package io.github.jekitchen.mcfontcontrol.gametest;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.discovery.FaceInfo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * D14 memory check: heap growth with a large applied font must stay within the font's file
 * size plus 10 MB. The font file itself is held in native memory by vanilla's provider (one
 * copy), so the Java heap should barely move. Writes {@code build/reports/measurements/memory.txt}.
 */
@SuppressWarnings("UnstableApiUsage")
public class MemoryGameTest implements FabricClientGameTest {
	private static final long MB = 1024 * 1024;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		GameTestFonts.pick(context);
		// The largest visible font installed: the worst case for memory.
		Optional<FaceInfo> largest = McFontControl.fontIndex().families(false).stream()
			.map(f -> f.defaultStyle())
			.max(Comparator.comparingLong(f -> size(f.path())));
		if (largest.isEmpty()) {
			return;
		}
		FaceInfo face = largest.get();
		long fontBytes = size(face.path());

		GameTestFonts.apply(context, FontSettings.DEFAULTS);
		long vanillaHeap = usedHeapAfterGc();
		GameTestFonts.apply(context, GameTestFonts.settingsFor(face));
		// Draw some text so glyphs are rasterized and cached, as in normal play.
		context.waitTicks(40);
		long appliedHeap = usedHeapAfterGc();
		long delta = appliedHeap - vanillaHeap;
		GameTestFonts.apply(context, FontSettings.DEFAULTS);

		String report = String.format("font=%s %s file=%.1fMB heapVanilla=%.1fMB heapApplied=%.1fMB heapDelta=%.1fMB limit=%.1fMB%n",
			face.family(), face.style(), fontBytes / (double) MB, vanillaHeap / (double) MB, appliedHeap / (double) MB, delta / (double) MB, (fontBytes + 10 * MB) / (double) MB);
		McFontControl.LOGGER.info("[measurement] memory: {}", report.strip());
		try {
			Path out = Path.of(System.getProperty("user.dir")).resolve("../../reports/measurements").normalize();
			Files.createDirectories(out);
			Files.writeString(out.resolve("memory.txt"), report);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (delta > fontBytes + 10 * MB) {
			throw new AssertionError("heap grew " + delta / MB + " MB with a " + fontBytes / MB + " MB font");
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

	private static long size(String path) {
		try {
			return Files.size(Path.of(path));
		} catch (IOException e) {
			return 0;
		}
	}
}
