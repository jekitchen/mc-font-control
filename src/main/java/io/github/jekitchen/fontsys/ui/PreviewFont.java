package io.github.jekitchen.fontsys.ui;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.PixelGrid;
import io.github.jekitchen.fontsys.discovery.StyledFaces;
import io.github.jekitchen.fontsys.mixin.FontManagerAccessor;
import io.github.jekitchen.fontsys.mixin.FontSetAccessor;
import io.github.jekitchen.fontsys.mixin.MinecraftAccessor;
import io.github.jekitchen.fontsys.pack.FaceSpec;
import io.github.jekitchen.fontsys.pack.FontStyleSlot;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.pack.PrivateUseFilteringProvider;
import io.github.jekitchen.fontsys.pack.StyleRouting;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

/**
 * A private {@link Font} for the live preview (PRD UI-6, D4). It is backed by its own
 * {@link FontSet}s: the selected face first, then vanilla's providers as the glyph fallback,
 * plus one set per bold or italic face (AP-8, D24), so the preview matches what Apply will
 * show. The game's own fonts are never touched. Render thread only; {@link #close()} frees
 * the faces and atlases.
 *
 * <p>For the size strip (UI-15) it also builds plain fonts at the neighboring sizes. They open
 * their own FreeType faces on the selected face's file data rather than reading the file again,
 * so the strip costs glyph atlases, not another copy of the font (D14).
 */
public final class PreviewFont implements AutoCloseable {
	private static final AtomicInteger COUNTER = new AtomicInteger();

	private final FontSet fontSet;
	private final Map<FontStyleSlot, FontSet> styledSets;
	private final List<GlyphProvider> owned;
	private final Optional<StyledFaces> styles;
	private final OptionalInt pixelGrid;
	private final Font font;
	private final float size;
	/** The size strip's other sizes; closed before {@link #owned}, whose face data they share. */
	private final Map<Float, Sized> others;

	private PreviewFont(FontSet fontSet, Map<FontStyleSlot, FontSet> styledSets, List<GlyphProvider> owned, Optional<StyledFaces> styles,
		OptionalInt pixelGrid, float size, Map<Float, Sized> others) {
		this.fontSet = fontSet;
		this.styledSets = styledSets;
		this.owned = owned;
		this.styles = styles;
		this.pixelGrid = pixelGrid;
		this.font = new Font(new Provider());
		this.size = size;
		this.others = others;
	}

	public Font font() {
		return font;
	}

	/** The sizes in the size strip, ascending; always includes the selected size. */
	public List<Float> stripSizes() {
		List<Float> sizes = new ArrayList<>(others.keySet());
		sizes.add(size);
		sizes.sort(null);
		return sizes;
	}

	/** The plain font at one of {@link #stripSizes()}, or the main font for the selected size. */
	public Font fontAt(float stripSize) {
		Sized other = others.get(stripSize);
		return other == null ? font : other.font();
	}

	/** A plain font at another size: its own face and atlas, no bold or italic faces. */
	private record Sized(FontSet set, GlyphProvider provider, Font font) {
		static Sized of(FontSet set, GlyphProvider provider) {
			return new Sized(set, provider, plainFont(set));
		}
	}

	/** A font drawn from one set, without bold or italic faces; also used for the font list's names (D49). */
	static Font plainFont(FontSet set) {
		return new Font(new Font.Provider() {
			@Override
			public GlyphSource glyphs(FontDescription description) {
				return set.source(false);
			}

			@Override
			public EffectGlyph effect() {
				return set.whiteGlyph();
			}
		});
	}

	/** The faces bold and italic text use, if the selected face is in the font index. */
	public Optional<StyledFaces> styles() {
		return styles;
	}

	/** Cells per em if the selected face looks like a pixel font ({@link PixelGrid}). */
	public OptionalInt pixelGrid() {
		return pixelGrid;
	}

	/** True if text in this slot is drawn with its own face rather than vanilla's simulated style. */
	public boolean hasStyledFace(FontStyleSlot slot) {
		return styledSets.containsKey(slot);
	}

	/** Glyphs for the preview's text; bold and italic text goes to the styled sets. */
	private final class Provider implements Font.Provider, StyleRouting.Router {
		@Override
		public GlyphSource glyphs(FontDescription description) {
			for (Map.Entry<FontStyleSlot, FontSet> styled : styledSets.entrySet()) {
				if (styled.getKey().font().equals(description)) {
					return styled.getValue().source(false);
				}
			}
			return fontSet.source(false);
		}

		@Override
		public EffectGlyph effect() {
			return fontSet.whiteGlyph();
		}

		@Override
		public FontDescription route(FontDescription font, boolean bold, boolean italic) {
			FontStyleSlot slot = FontStyleSlot.of(bold, italic);
			return slot != null && FontDescription.DEFAULT.equals(font) && styledSets.containsKey(slot) ? slot.font() : font;
		}
	}

	/**
	 * Opens the faces and builds the font sets.
	 *
	 * @throws IOException if the selected face cannot be read or FreeType rejects it
	 */
	public static PreviewFont create(FontSettings settings, int guiScale) throws IOException {
		Minecraft mc = Minecraft.getInstance();
		List<GlyphProvider.Conditional> vanilla = vanillaProviders(mc);
		List<GlyphProvider> owned = new ArrayList<>();
		Map<FontStyleSlot, FontSet> styledSets = new EnumMap<>(FontStyleSlot.class);
		Map<Float, Sized> others = new LinkedHashMap<>();
		FontSet set = null;
		try {
			FaceSpec plainSpec = FaceSpec.plain(settings.faceIndex());
			Opened plain = openTrueType(readDirect(Path.of(settings.fontPath())), true, plainSpec, settings, guiScale, true);
			GlyphProvider ttf = plain.provider();
			owned.add(ttf);
			set = fontSet(mc, ttf, vanilla);

			for (float other : SizeStrip.sizes(settings.size(), plain.pixelGrid(), settings.crispEdges(), FontSettings.MIN_SIZE, FontSettings.MAX_SIZE)) {
				if (other == settings.size()) {
					continue;
				}
				FontSettings resized = settings.withLayout(other, settings.oversample(), settings.shiftX(), settings.shiftY());
				GlyphProvider provider;
				try {
					provider = openTrueType(plain.data(), false, plainSpec, resized, guiScale, false).provider();
				} catch (IOException e) {
					// The strip just leaves this size out.
					Fontsys.LOGGER.debug("[{}] Preview at size {} failed: {}", Fontsys.DISPLAY_NAME, other, e.toString());
					continue;
				}
				try {
					others.put(other, Sized.of(fontSet(mc, provider, vanilla), provider));
				} catch (RuntimeException e) {
					provider.close();
					throw e;
				}
			}

			Optional<StyledFaces> styles = selectedFace(settings)
				.map(face -> PackContents.styledFaces(face, Fontsys.fontIndex()::bestAvailable));
			if (styles.isPresent()) {
				for (FontStyleSlot slot : FontStyleSlot.values()) {
					Optional<StyledFaces.Variant> variant = slot.variant(styles.get());
					if (variant.isEmpty()) {
						continue;
					}
					FontStyleSlot same = PackContents.sharedSlot(slot, variant.get(), Map.of(FontStyleSlot.BOLD, styles.get().bold()));
					if (same != null && styledSets.containsKey(same)) {
						styledSets.put(slot, styledSets.get(same));
						continue;
					}
					try {
						GlyphProvider styled = openTrueType(readDirect(Path.of(variant.get().face().path())), true, slot.spec(variant.get()), settings, guiScale, false)
							.provider();
						owned.add(styled);
						styledSets.put(slot, fontSet(mc, styled, vanilla));
					} catch (IOException e) {
						// Vanilla's simulated style shows for this slot, as it would after Apply.
						Fontsys.LOGGER.debug("[{}] Preview of the {} face failed: {}", Fontsys.DISPLAY_NAME, slot, e.toString());
					}
				}
			}
			return new PreviewFont(set, styledSets, owned, styles, plain.pixelGrid(), settings.size(), others);
		} catch (IOException | RuntimeException e) {
			closeOthers(others);
			styledSets.values().stream().distinct().forEach(FontSet::close);
			if (set != null) {
				set.close();
			}
			owned.forEach(GlyphProvider::close);
			throw e;
		}
	}

	/** Vanilla's providers from {@code fontsys:vanilla}. Shared, not owned: the game closes them on its next reload. */
	private static List<GlyphProvider.Conditional> vanillaProviders(Minecraft mc) {
		FontSet vanilla = ((FontManagerAccessor) ((MinecraftAccessor) mc).fontsys$fontManager()).fontsys$fontSets()
			.get(((FontDescription.Resource) PackContents.VANILLA_FONT).id());
		return vanilla == null ? List.of() : ((FontSetAccessor) vanilla).fontsys$allProviders();
	}

	/** A set with {@code first}, then {@code vanilla} as the fallback, in its own atlas. Render thread: it creates the atlas texture. */
	static FontSet fontSet(Minecraft mc, GlyphProvider first, List<GlyphProvider.Conditional> vanilla) {
		List<GlyphProvider.Conditional> providers = new ArrayList<>();
		providers.add(new GlyphProvider.Conditional(first, FontOption.Filter.ALWAYS_PASS));
		providers.addAll(vanilla);
		Identifier atlas = Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, "preview_" + COUNTER.incrementAndGet());
		FontSet set = new FontSet(new GlyphStitcher(mc.getTextureManager(), atlas));
		set.reload(providers, fontOptions(mc));
		return set;
	}

	/** The selected face as the font index knows it. */
	private static Optional<FaceInfo> selectedFace(FontSettings settings) {
		return Fontsys.fontIndex().bestAvailable().allFaces().stream()
			.filter(f -> f.path().equals(settings.fontPath()) && f.faceIndex() == settings.faceIndex())
			.findFirst();
	}

	private static Set<FontOption> fontOptions(Minecraft mc) {
		Set<FontOption> options = EnumSet.noneOf(FontOption.class);
		if (mc.options.forceUnicodeFont().get()) {
			options.add(FontOption.UNIFORM);
		}
		if (mc.options.japaneseGlyphVariants().get()) {
			options.add(FontOption.JAPANESE_VARIANTS);
		}
		return options;
	}

	/** @param trueType the vanilla provider inside {@code provider} */
	record Opened(GlyphProvider provider, OptionalInt pixelGrid, ByteBuffer data, TrueTypeGlyphProvider trueType) {
	}

	/** Looks at a face before its provider is built, and may change the size it is built at, or reject it. */
	@FunctionalInterface
	interface FaceCheck {
		FontSettings check(FT_Face face, FontSettings settings) throws IOException;
	}

	/**
	 * Same steps as vanilla's {@code TrueTypeGlyphProviderDefinition.load}, but with our face
	 * index, style handling and crisp mode, laid out as Apply would at this GUI scale.
	 *
	 * @param data the font file in native memory
	 * @param ownsData the provider frees {@code data} when it closes; otherwise another provider
	 *     owns it and must close after this one
	 * @param detectGrid also check whether the face looks like a pixel font
	 */
	private static Opened openTrueType(ByteBuffer data, boolean ownsData, FaceSpec spec, FontSettings settings, int guiScale, boolean detectGrid)
		throws IOException {
		return openTrueType(data, ownsData, spec, settings, guiScale, detectGrid, null);
	}

	/**
	 * As above, with a check that runs once the face is open and its Unicode map selected, before
	 * the provider is built (the font list's names, D49). Safe off the render thread, as vanilla
	 * loads its own {@code ttf} providers there.
	 */
	static Opened openTrueType(ByteBuffer data, boolean ownsData, FaceSpec spec, FontSettings settings, int guiScale, boolean detectGrid,
		@Nullable FaceCheck check) throws IOException {
		FT_Face face = null;
		try {
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer facePtr = stack.mallocPointer(1);
					int err = FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), data, spec.faceIndex(), facePtr);
					if (err != 0) {
						throw new IOException("FreeType error " + err);
					}
					face = FT_Face.create(facePtr.get(0));
				}
				if (FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE) != 0) {
					throw new IOException("No Unicode character map");
				}
				// Before the provider sets a size and transform: the checks load unscaled outlines.
				OptionalInt grid = detectGrid ? PixelGrid.detect(face) : OptionalInt.empty();
				if (check != null) {
					settings = check.check(face, settings);
				}
				// The provider frees the buffer it is given and checks it is set before every glyph.
				// A provider that shares another's data gets a one-byte placeholder to own instead;
				// its face still reads the shared data, which the owner frees after it closes.
				TrueTypeGlyphProvider ttf = new TrueTypeGlyphProvider(ownsData ? data : MemoryUtil.memAlloc(1), face, settings.size(), settings.renderOversample(guiScale),
					settings.renderShift(settings.shiftX(), guiScale), settings.renderShift(settings.shiftY(), guiScale), "");
				return new Opened(new PrivateUseFilteringProvider(ttf, face, spec, settings.crispEdges()), grid, data, ttf);
			}
		} catch (IOException | RuntimeException e) {
			if (face != null) {
				synchronized (FreeTypeUtil.LIBRARY_LOCK) {
					FreeType.FT_Done_Face(face);
				}
			}
			if (ownsData) {
				MemoryUtil.memFree(data);
			}
			throw e instanceof IOException io ? io : new IOException(e);
		}
	}

	/** Reads a file into native memory; {@code TrueTypeGlyphProvider.close} frees it. */
	static ByteBuffer readDirect(Path path) throws IOException {
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
			long size = channel.size();
			if (size <= 0 || size > Integer.MAX_VALUE) {
				throw new IOException("Unsupported file size " + size);
			}
			ByteBuffer buffer = MemoryUtil.memAlloc((int) size);
			try {
				while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
					// keep reading
				}
				buffer.flip();
				return buffer;
			} catch (IOException e) {
				MemoryUtil.memFree(buffer);
				throw e;
			}
		}
	}

	/** The strip's fonts share the selected face's data, so they close first. */
	private static void closeOthers(Map<Float, Sized> others) {
		others.values().forEach(o -> {
			o.set().close();
			o.provider().close();
		});
		others.clear();
	}

	@Override
	public void close() {
		closeOthers(others);
		fontSet.close();
		styledSets.values().stream().distinct().forEach(FontSet::close);
		owned.forEach(GlyphProvider::close);
	}
}
