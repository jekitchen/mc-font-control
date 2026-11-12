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
 */
public final class PreviewFont implements AutoCloseable {
	private static final AtomicInteger COUNTER = new AtomicInteger();

	private final FontSet fontSet;
	private final Map<FontStyleSlot, FontSet> styledSets;
	private final List<GlyphProvider> owned;
	private final Optional<StyledFaces> styles;
	private final OptionalInt pixelGrid;
	private final Font font;

	private PreviewFont(FontSet fontSet, Map<FontStyleSlot, FontSet> styledSets, List<GlyphProvider> owned, Optional<StyledFaces> styles,
		OptionalInt pixelGrid) {
		this.fontSet = fontSet;
		this.styledSets = styledSets;
		this.owned = owned;
		this.styles = styles;
		this.pixelGrid = pixelGrid;
		this.font = new Font(new Provider());
	}

	public Font font() {
		return font;
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
		FontSet set = null;
		try {
			Opened plain = openTrueType(Path.of(settings.fontPath()), FaceSpec.plain(settings.faceIndex()), settings, guiScale, true);
			GlyphProvider ttf = plain.provider();
			owned.add(ttf);
			set = fontSet(mc, ttf, vanilla);

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
						GlyphProvider styled = openTrueType(Path.of(variant.get().face().path()), slot.spec(variant.get()), settings, guiScale, false).provider();
						owned.add(styled);
						styledSets.put(slot, fontSet(mc, styled, vanilla));
					} catch (IOException e) {
						// Vanilla's simulated style shows for this slot, as it would after Apply.
						Fontsys.LOGGER.debug("[{}] Preview of the {} face failed: {}", Fontsys.DISPLAY_NAME, slot, e.toString());
					}
				}
			}
			return new PreviewFont(set, styledSets, owned, styles, plain.pixelGrid());
		} catch (IOException | RuntimeException e) {
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

	private static FontSet fontSet(Minecraft mc, GlyphProvider first, List<GlyphProvider.Conditional> vanilla) {
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

	private record Opened(GlyphProvider provider, OptionalInt pixelGrid) {
	}

	/**
	 * Same steps as vanilla's {@code TrueTypeGlyphProviderDefinition.load}, but with our face
	 * index, style handling and crisp mode, laid out as Apply would at this GUI scale.
	 *
	 * @param detectGrid also check whether the face looks like a pixel font
	 */
	private static Opened openTrueType(Path path, FaceSpec spec, FontSettings settings, int guiScale, boolean detectGrid) throws IOException {
		ByteBuffer data = readDirect(path);
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
				// Before the provider sets a size and transform: the check loads unscaled outlines.
				OptionalInt grid = detectGrid ? PixelGrid.detect(face) : OptionalInt.empty();
				TrueTypeGlyphProvider ttf = new TrueTypeGlyphProvider(data, face, settings.size(), settings.renderOversample(guiScale),
					settings.renderShift(settings.shiftX(), guiScale), settings.renderShift(settings.shiftY(), guiScale), "");
				return new Opened(new PrivateUseFilteringProvider(ttf, face, spec, settings.crispEdges()), grid);
			}
		} catch (IOException | RuntimeException e) {
			if (face != null) {
				synchronized (FreeTypeUtil.LIBRARY_LOCK) {
					FreeType.FT_Done_Face(face);
				}
			}
			MemoryUtil.memFree(data);
			throw e instanceof IOException io ? io : new IOException(e);
		}
	}

	/** Reads a file into native memory; {@code TrueTypeGlyphProvider.close} frees it. */
	private static ByteBuffer readDirect(Path path) throws IOException {
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

	@Override
	public void close() {
		fontSet.close();
		styledSets.values().stream().distinct().forEach(FontSet::close);
		owned.forEach(GlyphProvider::close);
	}
}
