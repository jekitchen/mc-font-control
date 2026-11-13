package io.github.jekitchen.fontsys.ui;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.Visibility;
import io.github.jekitchen.fontsys.mixin.TrueTypeGlyphProviderAccessor;
import io.github.jekitchen.fontsys.pack.FaceSpec;
import io.github.jekitchen.fontsys.pack.PrivateUseFilteringProvider;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.FontSet;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_Glyph_Metrics;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

/**
 * Draws each family's name in the font list in that family's own font, as word processors'
 * font menus do (PRD UI-3, D49). Names fall back to the normal font until their font is ready,
 * and for good when it can't draw them.
 *
 * <p><b>Only rows on screen.</b> A name's font is asked for only while its row is drawn, and
 * {@link LazyFontCache} keeps at most {@link #CAPACITY} open, evicting rows that left the screen.
 *
 * <p><b>Cheap to keep.</b> On a background thread the font file is read and opened with the
 * same FreeType-backed {@code ttf} provider as the preview ({@link PreviewFont}), restricted to
 * the characters of the name. On the render thread a small {@link FontSet} is built and the
 * name measured once, which bakes its glyphs into the set's atlas; then the face is closed and
 * the file's bytes freed. A listed name costs one 256×256 glyph atlas (256 KB of GPU memory)
 * and no font data, however large the font file.
 *
 * <p><b>No stutter.</b> Reading and opening happen off the render thread, at most
 * {@link #MAX_IN_FLIGHT} at a time; at most {@link #ACTIVATIONS_PER_FRAME} atlases are built
 * per frame.
 *
 * <p><b>Fallbacks</b>, all drawn in the normal font as before: fonts the index hides (symbol,
 * color and emoji fonts, shown with Show hidden fonts), names with a character the font lacks,
 * fonts that can't fit the name in a row even at {@link #MIN_SIZE}, and fonts that fail to load
 * (one warning per game session; the rest go to the debug log).
 */
public final class FamilyNameFonts {
	/** Most name fonts open at once: more than a full-height list shows at GUI scale 1 on a 1080p screen. */
	public static final int CAPACITY = 64;
	static final int MAX_IN_FLIGHT = 4;
	static final int ACTIVATIONS_PER_FRAME = 2;
	/**
	 * Names are drawn at Fontsys's default size: the em is 10 GUI pixels, so capital letters are
	 * about 7 pixels tall, like vanilla's, and names match the vanilla-font tags beside them.
	 */
	static final float SIZE = FontSettings.DEFAULT_SIZE;
	/** Below this a name would be too small to read; such fonts stay in the normal font. */
	static final float MIN_SIZE = 6;
	/**
	 * GUI pixels a name may reach above and below its baseline. Rows are 14 pixels tall, and the
	 * name shares the vanilla-font baseline with the tags, 10 pixels below the row's top: 9 up
	 * keeps clear of the selection outline, 4 down keeps clear of the next row's text.
	 */
	static final int ROOM_ABOVE = 9;
	static final int ROOM_BELOW = 4;

	/** Test hook: when off, every name is drawn in the normal font, as before D49. */
	public static volatile boolean enabled = true;

	private static final AtomicInteger OPEN_FACES = new AtomicInteger();
	private static final AtomicInteger OPEN_ATLASES = new AtomicInteger();
	private static final AtomicLong HELD_BYTES = new AtomicLong();
	private static final AtomicLong PEAK_HELD_BYTES = new AtomicLong();
	/** One warning per game session; the rest go to the debug log. */
	private static final AtomicBoolean WARNED = new AtomicBoolean();

	/** One low-priority thread for every screen: loads run one at a time, as do the faces' closes. */
	private static final class Worker {
		static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, Fontsys.DISPLAY_NAME + " font names");
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			return thread;
		});
	}

	/** A name's font: the family's default face, laid out for one GUI scale. */
	record Key(String path, int faceIndex, String name, int guiScale) {
	}

	/** Where a name was drawn last frame, and whether in its own font; for the game tests. */
	public record DrawnName(String name, int x, int y, int width, boolean ownFont) {
	}

	/** A face opened for one name, before activation. Holds the font file in native memory. */
	private record Loaded(Key key, NameGlyphs glyphs, float size) {
	}

	/** An active name font: its atlas holds the name's glyphs; the face is closed. */
	private record NameFont(FontSet set, Font font, float size) {
		void close() {
			set.close();
			OPEN_ATLASES.decrementAndGet();
		}
	}

	private final LazyFontCache<Key, Loaded, NameFont> cache = new LazyFontCache<>(CAPACITY, MAX_IN_FLIGHT, ACTIVATIONS_PER_FRAME,
		Worker.EXECUTOR, FamilyNameFonts::load, FamilyNameFonts::activate, loaded -> loaded.glyphs().release(), NameFont::close, this::report);
	private int guiScale = -1;
	private boolean active;
	private List<DrawnName> drawing = new ArrayList<>();
	private List<DrawnName> drawn = List.of();

	/**
	 * Call once per frame before the list draws.
	 *
	 * @param usable false while a resource reload is under way: names use the normal font and every
	 *     name font is closed, as the preview is
	 */
	public void beginFrame(int guiScale, boolean usable) {
		drawn = List.copyOf(drawing);
		drawing = new ArrayList<>();
		if (!usable || !enabled) {
			if (active) {
				cache.clear();
				active = false;
			}
			return;
		}
		if (guiScale != this.guiScale) {
			// Glyphs are rasterized for the GUI scale, as the applied font's are.
			cache.clear();
			this.guiScale = guiScale;
		}
		active = true;
		cache.beginFrame();
	}

	/** Closes every name font; call when the screen closes. Loads still running are freed as they finish. */
	public void clear() {
		cache.clear();
		active = false;
		drawing = new ArrayList<>();
		drawn = List.of();
	}

	/**
	 * Draws a family's name at {@code x, y} (the normal font's text position), in its own font if
	 * that is ready, cut short with "…" to fit {@code room}.
	 */
	public void drawName(GuiGraphicsExtractor graphics, Font normal, FontFamily family, int x, int y, int room, int color) {
		String name = family.name();
		Font own = fontFor(family);
		Font font = own != null ? own : normal;
		int width;
		if (font.width(name) <= room) {
			graphics.text(font, name, x, y, color, false);
			width = font.width(name);
		} else {
			String cut = font.plainSubstrByWidth(name, Math.max(0, room - normal.width("…")));
			graphics.text(font, cut, x, y, color, false);
			graphics.text(normal, "…", x + font.width(cut), y, color, false);
			width = font.width(cut) + normal.width("…");
		}
		drawing.add(new DrawnName(name, x, y, width, own != null));
	}

	private @Nullable Font fontFor(FontFamily family) {
		if (!active || family.visibility() != Visibility.VISIBLE) {
			return null;
		}
		FaceInfo face = family.defaultStyle();
		NameFont font = cache.get(new Key(face.path(), face.faceIndex(), family.name(), guiScale));
		return font == null ? null : font.font();
	}

	// ---- Test hooks ------------------------------------------------------------------------------

	/** The names drawn last frame, top to bottom. */
	public List<DrawnName> drawnNames() {
		return drawn;
	}

	public LazyFontCache.Stats stats() {
		return cache.stats();
	}

	/** Name-font faces open now, in any screen: each holds its font file in native memory. */
	public static int openFaces() {
		return OPEN_FACES.get();
	}

	/** Name-font atlases open now, in any screen. */
	public static int openAtlases() {
		return OPEN_ATLASES.get();
	}

	/** Font-file bytes held in native memory now, and the most ever held at once. */
	public static long heldBytes() {
		return HELD_BYTES.get();
	}

	public static long peakHeldBytes() {
		return PEAK_HELD_BYTES.get();
	}

	// ---- Loading ---------------------------------------------------------------------------------

	/** Thrown when a font can't draw a name: an expected fallback, not an error. */
	private static final class CannotDraw extends IOException {
		CannotDraw(String message) {
			super(message);
		}
	}

	/** On the worker thread: reads the file and opens the face, or returns null if it can't draw the name. */
	private static @Nullable Loaded load(Key key) throws IOException {
		ByteBuffer data = PreviewFont.readDirect(Path.of(key.path()));
		long bytes = data.capacity();
		hold(bytes);
		float[] size = new float[1];
		FontSettings settings = FontSettings.DEFAULTS.withFont(key.path(), key.faceIndex(), key.name(), null)
			.withLayout(SIZE, FontSettings.AUTO_OVERSAMPLE, 0, 0);
		PreviewFont.Opened opened;
		try {
			// The provider owns the data from here: it frees it when it closes, or now if opening fails.
			opened = PreviewFont.openTrueType(data, true, FaceSpec.plain(key.faceIndex()), settings, key.guiScale(), false, (face, s) -> {
				size[0] = fit(face, key.name());
				return s.withLayout(size[0], s.oversample(), 0, 0);
			});
		} catch (CannotDraw e) {
			hold(-bytes);
			Fontsys.LOGGER.debug("[{}] '{}' stays in the normal font in the list: {}", Fontsys.DISPLAY_NAME, key.name(), e.getMessage());
			return null;
		} catch (IOException e) {
			hold(-bytes);
			throw e;
		}
		OPEN_FACES.incrementAndGet();
		NameGlyphs glyphs = new NameGlyphs(opened.provider(), opened.trueType(), key.name(), bytes);
		try {
			// Loads the glyphs' metrics here rather than on the render thread.
			key.name().codePoints().forEach(glyphs::getGlyph);
		} catch (RuntimeException e) {
			glyphs.closeNow();
			throw e;
		}
		return new Loaded(key, glyphs, size[0]);
	}

	/**
	 * The size a name is drawn at, from its glyphs' unscaled outlines.
	 *
	 * @throws CannotDraw if the font lacks a character of the name, has no visible glyphs for it,
	 *     or can't fit it in a row
	 */
	private static float fit(FT_Face face, String name) throws CannotDraw {
		int unitsPerEm = Short.toUnsignedInt(face.units_per_EM());
		if (unitsPerEm == 0) {
			throw new CannotDraw("no outlines");
		}
		long top = 0;
		long bottom = 0;
		boolean ink = false;
		for (int codepoint : name.codePoints().toArray()) {
			int index = PrivateUseFilteringProvider.isPrivateUse(codepoint) ? 0 : FreeType.FT_Get_Char_Index(face, codepoint);
			if (index == 0) {
				throw new CannotDraw(String.format(Locale.ROOT, "no glyph for U+%04X", codepoint));
			}
			if (FreeType.FT_Load_Glyph(face, index, FreeType.FT_LOAD_NO_SCALE | FreeType.FT_LOAD_NO_BITMAP) != 0) {
				throw new CannotDraw(String.format(Locale.ROOT, "glyph for U+%04X does not load", codepoint));
			}
			FT_GlyphSlot slot = face.glyph();
			if (slot == null) {
				throw new CannotDraw("no glyph slot");
			}
			FT_Glyph_Metrics metrics = slot.metrics();
			if (metrics.width() > 0 && metrics.height() > 0) {
				ink = true;
				top = Math.max(top, metrics.horiBearingY());
				bottom = Math.min(bottom, metrics.horiBearingY() - metrics.height());
			}
		}
		if (!ink) {
			throw new CannotDraw("no visible glyphs");
		}
		float size = fitSize(unitsPerEm, top, bottom);
		if (size < MIN_SIZE) {
			throw new CannotDraw("too tall for a row at size " + size);
		}
		return size;
	}

	/**
	 * {@link #SIZE}, or smaller if the name's glyphs (top and bottom in font units, relative to
	 * the baseline) would reach past {@link #ROOM_ABOVE} or {@link #ROOM_BELOW}. Half sizes, like
	 * the size slider.
	 */
	static float fitSize(int unitsPerEm, long top, long bottom) {
		float size = SIZE;
		if (top > 0) {
			size = Math.min(size, ROOM_ABOVE * (float) unitsPerEm / top);
		}
		if (bottom < 0) {
			size = Math.min(size, ROOM_BELOW * (float) unitsPerEm / -bottom);
		}
		return (float) Math.floor(size * 2) / 2;
	}

	/** On the render thread: builds the atlas and bakes the name into it, then lets the face go. */
	private static NameFont activate(Loaded loaded) {
		FontSet set = null;
		try {
			set = PreviewFont.fontSet(Minecraft.getInstance(), loaded.glyphs(), List.of());
			OPEN_ATLASES.incrementAndGet();
			Font font = PreviewFont.plainFont(set);
			// Measuring looks up, and so bakes, every glyph of the name while the face is open.
			font.width(loaded.key().name());
			return new NameFont(set, font, loaded.size());
		} catch (RuntimeException e) {
			if (set != null) {
				set.close();
				OPEN_ATLASES.decrementAndGet();
			}
			throw e;
		} finally {
			loaded.glyphs().release();
		}
	}

	private void report(@Nullable Key key, Throwable error) {
		String name = key == null ? "?" : key.name();
		if (WARNED.compareAndSet(false, true)) {
			Fontsys.LOGGER.warn("[{}] Could not show '{}' in its own font in the font list; it and any others that fail are shown in the normal font: {}",
				Fontsys.DISPLAY_NAME, name, error.toString());
		} else {
			Fontsys.LOGGER.debug("[{}] Could not show '{}' in its own font in the font list: {}", Fontsys.DISPLAY_NAME, name, error.toString());
		}
	}

	private static void hold(long bytes) {
		long now = HELD_BYTES.addAndGet(bytes);
		PEAK_HELD_BYTES.accumulateAndGet(now, Math::max);
	}

	/**
	 * The {@code ttf} provider, limited to a name's characters so the font set looks up only
	 * those, not every glyph in the font (vanilla's {@code FontSet.reload} loads every supported
	 * glyph's metrics, which takes long for a large CJK font). Once the name is baked,
	 * {@link #release()} closes the face on the worker thread, so the render thread never waits
	 * for the FreeType lock a load holds; the font set then finds nothing new here.
	 */
	private static final class NameGlyphs implements GlyphProvider {
		private final GlyphProvider delegate;
		private final TrueTypeGlyphProvider trueType;
		private final IntSet codepoints;
		private final long bytes;
		private volatile boolean released;
		private boolean closed;

		NameGlyphs(GlyphProvider delegate, TrueTypeGlyphProvider trueType, String name, long bytes) {
			this.delegate = delegate;
			this.trueType = trueType;
			this.codepoints = IntSets.unmodifiable(new IntOpenHashSet(name.codePoints().toArray()));
			this.bytes = bytes;
		}

		@Override
		public @Nullable UnbakedGlyph getGlyph(int codepoint) {
			return released || !codepoints.contains(codepoint) ? null : delegate.getGlyph(codepoint);
		}

		@Override
		public IntSet getSupportedGlyphs() {
			return codepoints;
		}

		/** No more glyphs from here; the face closes on the worker thread. Safe to call twice. */
		void release() {
			if (released) {
				return;
			}
			released = true;
			try {
				Worker.EXECUTOR.execute(this::closeNow);
			} catch (RuntimeException e) {
				closeNow();
			}
		}

		synchronized void closeNow() {
			released = true;
			if (closed) {
				return;
			}
			closed = true;
			try {
				delegate.close();
				// The baked glyphs keep the provider reachable; let its entry for every character go.
				((TrueTypeGlyphProviderAccessor) trueType).fontsys$glyphs().clear();
			} finally {
				OPEN_FACES.decrementAndGet();
				hold(-bytes);
			}
		}

		@Override
		public void close() {
			release();
		}
	}
}
