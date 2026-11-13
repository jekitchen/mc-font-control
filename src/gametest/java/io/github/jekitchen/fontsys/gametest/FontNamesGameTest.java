package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.Visibility;
import io.github.jekitchen.fontsys.ui.FamilyNameFonts;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import io.github.jekitchen.fontsys.ui.LazyFontCache;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Font names in their own font in the font list (PRD D49):
 *
 * <ul>
 *   <li>with an empty search, rows on screen are drawn in their own fonts: their pixels differ
 *       from the same rows with the feature off, and rows that fall back don't change;</li>
 *   <li>scrolling the whole list keeps at most {@link FamilyNameFonts#CAPACITY} fonts open and
 *       activates at most two per frame;</li>
 *   <li>leaving the screen closes every face and atlas and frees every font file.</li>
 * </ul>
 *
 * <p>Also measures memory and frame time while scrolling, with the feature off and on (D14),
 * and writes them to {@code build/reports/measurements/font-names.txt}.
 */
@SuppressWarnings("UnstableApiUsage")
public class FontNamesGameTest implements FabricClientGameTest {
	private static final double MB = 1024 * 1024;
	/** One 256×256 RGBA atlas page per name font. */
	private static final long ATLAS_BYTES = 256 * 256 * 4;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		if (GameTestFonts.pick(context).isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No system fonts found; skipping the font-name checks.");
			return;
		}
		FamilyNameFonts.enabled = true;
		long heapBefore = usedHeapAfterGc();
		try {
			context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
			context.getInput().setCursorPos(1, 1); // no row hovered
			context.waitTicks(5);
			require(context.computeOnClient(mc -> screen(mc).searchText().isEmpty()), "the search starts empty");
			settle(context);
			ownFontRows(context);

			Measure off = scrollThrough(context, false, 14);
			Measure on = scrollThrough(context, true, 14);
			// A fast fling, five rows a frame: rows pass before their fonts load.
			Measure fling = scrollThrough(context, true, 70);
			LazyFontCache.Stats stats = context.computeOnClient(mc -> screen(mc).nameFonts().stats());
			int listed = context.computeOnClient(mc -> screen(mc).listedFamilies().size());
			require(stats.peakSize() <= FamilyNameFonts.CAPACITY && FamilyNameFonts.openAtlases() <= FamilyNameFonts.CAPACITY,
				"at most " + FamilyNameFonts.CAPACITY + " name fonts open: peak " + stats.peakSize() + ", open " + FamilyNameFonts.openAtlases());
			require(stats.activations() <= FamilyNameFonts.CAPACITY || stats.evictions() > 0, "fonts were evicted once the cap was reached: " + stats);
			require(stats.maxActivationsInAFrame() <= 2, "at most two fonts activated per frame: " + stats.maxActivationsInAFrame());

			// Halfway down, settled, for the screenshot.
			context.runOnClient(mc -> list(mc).setScrollAmount(list(mc).maxScrollAmount() / 2.0));
			settle(context);
			context.takeScreenshot("d49-names-scrolled");
			int openAtlases = FamilyNameFonts.openAtlases();
			long heapOpen = usedHeapAfterGc();
			// The same screen with every name font closed: the difference is what the open ones cost.
			FamilyNameFonts.enabled = false;
			context.waitFor(mc -> FamilyNameFonts.openFaces() == 0 && FamilyNameFonts.openAtlases() == 0, 20 * 10);
			long heapClosed = usedHeapAfterGc();
			FamilyNameFonts.enabled = true;

			context.setScreen(TitleScreen::new);
			context.waitFor(mc -> FamilyNameFonts.openFaces() == 0 && FamilyNameFonts.openAtlases() == 0 && FamilyNameFonts.heldBytes() == 0, 20 * 10);
			long heapAfter = usedHeapAfterGc();

			String report = String.format(Locale.ROOT,
				"families=%d visibleRows=%d cap=%d%n"
					+ "activations=%d evictions=%d fallbacks=%d peakOpen=%d maxActivationsPerFrame=%d%n"
					+ "activationMs: mean=%.2f maxPerFrame=%.2f%n"
					+ "frameMs off: %s%nframeMs on:  %s%nframeMs on, fast fling: %s%n"
					+ "openAtlasesHalfway=%d gpu=%.1fMB peakFontFileBytesHeld=%.1fMB%n"
					+ "heap: before=%.1fMB screenWithNames=%.1fMB screenWithoutNames=%.1fMB afterClose=%.1fMB namesCost=%.1fMB%n",
				listed, on.rows, FamilyNameFonts.CAPACITY,
				stats.activations(), stats.evictions(), stats.failures(), stats.peakSize(), stats.maxActivationsInAFrame(),
				stats.activations() == 0 ? 0 : stats.totalActivationNanos() / 1e6 / stats.activations(), stats.maxActivationNanos() / 1e6,
				off.summary(), on.summary(), fling.summary(),
				openAtlases, openAtlases * ATLAS_BYTES / MB, FamilyNameFonts.peakHeldBytes() / MB,
				heapBefore / MB, heapOpen / MB, heapClosed / MB, heapAfter / MB, (heapOpen - heapClosed) / MB);
			Fontsys.LOGGER.info("[measurement] font names:\n{}", report.strip());
			write(report);
			hiddenFonts(context);
		} finally {
			FamilyNameFonts.enabled = true;
			context.setScreen(TitleScreen::new);
		}
		// Faces close on the loading thread, after any load still running.
		for (int i = 0; i < 200 && (FamilyNameFonts.openFaces() != 0 || FamilyNameFonts.openAtlases() != 0 || FamilyNameFonts.heldBytes() != 0); i++) {
			context.waitTick();
		}
		require(FamilyNameFonts.openFaces() == 0, "every name-font face is closed after leaving the screen: " + FamilyNameFonts.openFaces());
		require(FamilyNameFonts.openAtlases() == 0, "every name-font atlas is closed after leaving the screen: " + FamilyNameFonts.openAtlases());
		require(FamilyNameFonts.heldBytes() == 0, "every font file is freed after leaving the screen: " + FamilyNameFonts.heldBytes());
	}

	/**
	 * Rows drawn in their own font look different from the same rows with the feature off; rows
	 * that fall back look the same. Red before D49: every row looked the same.
	 */
	private static void ownFontRows(ClientGameTestContext context) {
		List<FamilyNameFonts.DrawnName> on = context.computeOnClient(mc -> screen(mc).nameFonts().drawnNames());
		long own = on.stream().filter(FamilyNameFonts.DrawnName::ownFont).count();
		require(own > 0, "some rows are drawn in their own font: " + on);
		int[][] withNames = shoot(context, "d49-names-own-font");
		FamilyNameFonts.enabled = false;
		context.waitTicks(3);
		List<FamilyNameFonts.DrawnName> off = context.computeOnClient(mc -> screen(mc).nameFonts().drawnNames());
		require(off.stream().noneMatch(FamilyNameFonts.DrawnName::ownFont), "switched off, every row uses the normal font");
		int[][] without = shoot(context, "d49-names-normal-font");
		FamilyNameFonts.enabled = true;

		double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		int top = context.computeOnClient(mc -> list(mc).getY());
		int bottom = context.computeOnClient(mc -> list(mc).getBottom());
		Map<String, Integer> offWidth = new HashMap<>();
		off.forEach(d -> offWidth.put(d.name(), d.width()));
		List<String> unchanged = new ArrayList<>();
		List<String> changed = new ArrayList<>();
		int checkedOwn = 0;
		for (FamilyNameFonts.DrawnName d : on) {
			if (d.y() - 2 < top || d.y() + 11 > bottom) {
				continue; // the list draws rows cut by its edges, but clips them
			}
			int width = Math.max(d.width(), offWidth.getOrDefault(d.name(), d.width()));
			int diff = differingPixels(withNames, without, (int) (d.x() * scale), (int) ((d.y() - 2) * scale),
				(int) ((d.x() + width) * scale), (int) ((d.y() + 11) * scale));
			if (d.ownFont()) {
				checkedOwn++;
			}
			if (d.ownFont() && diff < 10) {
				unchanged.add(d.name() + " (" + diff + " px)");
			} else if (!d.ownFont() && diff > 0) {
				changed.add(d.name() + " (" + diff + " px)");
			}
		}
		require(checkedOwn > 0, "some rows fully in view are in their own font");
		require(unchanged.isEmpty(), "rows in their own font look different from the normal font: " + unchanged);
		require(changed.isEmpty(), "rows that fall back look as before: " + changed);
		context.waitTicks(3);
	}


	/** Fonts the index hides (symbol, emoji and color fonts), listed with Show hidden fonts, keep the normal font. */
	private static void hiddenFonts(ClientGameTestContext context) {
		boolean was = Fontsys.controller().config().showHiddenFonts;
		Fontsys.controller().setShowHiddenFonts(true);
		try {
			context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
			context.waitTicks(5);
			List<String> listed = context.computeOnClient(mc -> screen(mc).listedFamilies().stream().map(e -> e.getKey().name()).toList());
			List<String> hidden = context.computeOnClient(mc -> screen(mc).listedFamilies().stream().map(Map.Entry::getKey)
				.filter(f -> f.visibility() != Visibility.VISIBLE).map(FontFamily::name).toList());
			if (hidden.isEmpty()) {
				Fontsys.LOGGER.warn("[gametest] No hidden fonts installed; skipping the hidden-font check.");
				return;
			}
			int index = listed.indexOf(hidden.get(0));
			context.runOnClient(mc -> list(mc).setScrollAmount(Math.max(0, index * 14 - 28)));
			settle(context);
			context.takeScreenshot("d49-names-hidden");
			List<FamilyNameFonts.DrawnName> drawn = context.computeOnClient(mc -> screen(mc).nameFonts().drawnNames()).stream()
				.filter(d -> hidden.contains(d.name())).toList();
			require(!drawn.isEmpty(), "a hidden font is on screen: " + hidden.get(0));
			require(drawn.stream().noneMatch(FamilyNameFonts.DrawnName::ownFont), "hidden fonts keep the normal font: " + drawn);
		} finally {
			Fontsys.controller().setShowHiddenFonts(was);
			context.setScreen(TitleScreen::new);
		}
	}
	private record Measure(int rows, long[] frameNanos) {
		String summary() {
			long[] sorted = frameNanos.clone();
			Arrays.sort(sorted);
			if (sorted.length == 0) {
				return "no frames";
			}
			return String.format(Locale.ROOT, "frames=%d p50=%.2f p95=%.2f max=%.2f", sorted.length, sorted[sorted.length / 2] / 1e6,
				sorted[(int) (sorted.length * 0.95)] / 1e6, sorted[sorted.length - 1] / 1e6);
		}
	}

	/** Scrolls from the top to the bottom, {@code step} GUI pixels (a row is 14) per tick, recording each frame's time. */
	private static Measure scrollThrough(ClientGameTestContext context, boolean enabled, int step) {
		FamilyNameFonts.enabled = enabled;
		context.runOnClient(mc -> list(mc).setScrollAmount(0));
		context.waitTicks(enabled ? 3 : 10);
		int max = context.computeOnClient(mc -> list(mc).maxScrollAmount());
		List<Long> frames = new ArrayList<>();
		int rows = 0;
		for (int y = 0; y <= max + step; y += step) {
			int at = Math.min(y, max);
			context.runOnClient(mc -> list(mc).setScrollAmount(at));
			context.waitTick();
			frames.add(context.computeOnClient(mc -> mc.getFrameTimeNs()));
			rows = Math.max(rows, context.computeOnClient(mc -> screen(mc).nameFonts().drawnNames().size()));
		}
		return new Measure(rows, frames.stream().mapToLong(Long::longValue).toArray());
	}

	/** Waits until the rows on screen have their fonts, or have fallen back. */
	private static void settle(ClientGameTestContext context) {
		context.waitFor(mc -> {
			LazyFontCache.Stats s = screen(mc).nameFonts().stats();
			return s.inFlight() == 0 && s.waiting() == 0 && !screen(mc).nameFonts().drawnNames().isEmpty();
		}, 20 * 20);
		context.waitTicks(5);
	}

	private static FontConfigScreen screen(net.minecraft.client.Minecraft mc) {
		return (FontConfigScreen) mc.gui.screen();
	}

	private static AbstractScrollArea list(net.minecraft.client.Minecraft mc) {
		return screen(mc).children().stream().filter(c -> c instanceof AbstractScrollArea).map(c -> (AbstractScrollArea) c).findFirst()
			.orElseThrow(() -> new AssertionError("no font list"));
	}

	private static int differingPixels(int[][] a, int[][] b, int x0, int y0, int x1, int y1) {
		int n = 0;
		for (int y = Math.max(0, y0); y < Math.min(a.length, y1); y++) {
			for (int x = Math.max(0, x0); x < Math.min(a[y].length, x1); x++) {
				if (a[y][x] != b[y][x]) {
					n++;
				}
			}
		}
		return n;
	}

	private static int[][] shoot(ClientGameTestContext context, String name) {
		Path shot = context.takeScreenshot(name);
		try (InputStream in = Files.newInputStream(shot); NativeImage image = NativeImage.read(in)) {
			int[][] rgb = new int[image.getHeight()][image.getWidth()];
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					rgb[y][x] = image.getPixel(x, y) & 0xFFFFFF;
				}
			}
			return rgb;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static long usedHeapAfterGc() {
		Runtime rt = Runtime.getRuntime();
		for (int i = 0; i < 3; i++) {
			System.gc();
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		return rt.totalMemory() - rt.freeMemory();
	}

	private static void write(String report) {
		try {
			Path out = Path.of(System.getProperty("user.dir")).resolve("../../reports/measurements").normalize();
			Files.createDirectories(out);
			Files.writeString(out.resolve("font-names.txt"), report);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void require(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError(what);
		}
	}
}
