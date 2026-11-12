package io.github.jekitchen.mcfontcontrol.ui;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.mixin.FontManagerAccessor;
import io.github.jekitchen.mcfontcontrol.mixin.FontSetAccessor;
import io.github.jekitchen.mcfontcontrol.mixin.MinecraftAccessor;
import io.github.jekitchen.mcfontcontrol.pack.PackContents;
import io.github.jekitchen.mcfontcontrol.pack.PrivateUseFilteringProvider;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
 * {@link FontSet}: the selected face first, then vanilla's providers as the glyph fallback,
 * so the preview matches what Apply will show. The game's own fonts are never touched.
 * Render thread only; {@link #close()} frees the face and atlas.
 */
public final class PreviewFont implements AutoCloseable {
	private static final AtomicInteger COUNTER = new AtomicInteger();

	private final FontSet fontSet;
	private final GlyphProvider provider;
	private final Font font;

	private PreviewFont(FontSet fontSet, GlyphProvider provider) {
		this.fontSet = fontSet;
		this.provider = provider;
		this.font = new Font(new Font.Provider() {
			@Override
			public GlyphSource glyphs(FontDescription description) {
				return fontSet.source(false);
			}

			@Override
			public EffectGlyph effect() {
				return fontSet.whiteGlyph();
			}
		});
	}

	public Font font() {
		return font;
	}

	/**
	 * Opens the face and builds the font set.
	 *
	 * @throws IOException if the file cannot be read or FreeType rejects it
	 */
	public static PreviewFont create(FontSettings settings, int guiScale) throws IOException {
		Minecraft mc = Minecraft.getInstance();
		float oversample = settings.oversample() <= 0 ? Math.max(1, guiScale) : settings.oversample();
		GlyphProvider ttf = openTrueType(Path.of(settings.fontPath()), settings.faceIndex(), settings.size(), oversample, settings.shiftX(), settings.shiftY());

		List<GlyphProvider.Conditional> providers = new ArrayList<>();
		providers.add(new GlyphProvider.Conditional(ttf, FontOption.Filter.ALWAYS_PASS));
		FontSet vanilla = ((FontManagerAccessor) ((MinecraftAccessor) mc).mcfontcontrol$fontManager()).mcfontcontrol$fontSets()
			.get(((FontDescription.Resource) PackContents.VANILLA_FONT).id());
		if (vanilla != null) {
			// Shared, not owned: the game closes these providers on its next reload.
			providers.addAll(((FontSetAccessor) vanilla).mcfontcontrol$allProviders());
		}
		Identifier atlas = Identifier.fromNamespaceAndPath(McFontControl.MOD_ID, "preview_" + COUNTER.incrementAndGet());
		FontSet set = new FontSet(new GlyphStitcher(mc.getTextureManager(), atlas));
		set.reload(providers, fontOptions(mc));
		return new PreviewFont(set, ttf);
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

	/** Same steps as vanilla's {@code TrueTypeGlyphProviderDefinition.load}, but with our face index. */
	private static GlyphProvider openTrueType(Path path, int faceIndex, float size, float oversample, float shiftX, float shiftY) throws IOException {
		ByteBuffer data = readDirect(path);
		FT_Face face = null;
		try {
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer facePtr = stack.mallocPointer(1);
					int err = FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), data, faceIndex, facePtr);
					if (err != 0) {
						throw new IOException("FreeType error " + err);
					}
					face = FT_Face.create(facePtr.get(0));
				}
				if (FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE) != 0) {
					throw new IOException("No Unicode character map");
				}
				TrueTypeGlyphProvider ttf = new TrueTypeGlyphProvider(data, face, size, oversample, shiftX, shiftY, "");
				return new PrivateUseFilteringProvider(ttf, face);
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
		provider.close();
	}
}
