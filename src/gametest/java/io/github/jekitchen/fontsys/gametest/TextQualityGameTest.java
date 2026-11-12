package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Text quality for smooth fonts, from screenshots:
 *
 * <ul>
 *   <li>Linear coverage (PRD D36): an edge pixel gets exactly its coverage of the text colour.
 *       So white text on black and black text on white, drawn in the same place, add up to
 *       white at every pixel. With coverage applied twice, as vanilla's grayscale glyphs do,
 *       the light text's edges come out much darker and the sums fall short.</li>
 *   <li>Thinner shadow (D37): the shadow sits half a GUI unit away, in whole screen pixels;
 *       crisp text keeps vanilla's one GUI unit.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public class TextQualityGameTest implements FabricClientGameTest {
	private static final String SAMPLE = "The quick brown fox 0123456789 @%&";
	/** rounding in the blend and the screenshot */
	private static final int TOLERANCE = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> pick = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (pick.isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No fonts found; skipping the text quality test.");
			return;
		}
		FontSettings smooth = GameTestFonts.settingsFor(pick.get());
		try {
			GameTestFonts.apply(context, smooth);
			int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
			linearCoverage(context);

			int thin = shadowPixels(context, "d37-shadow");
			int expected = Math.max(1, Math.round(scale / 2.0F));
			require(thin == expected, "shadow " + thin + " px at GUI scale " + scale + ", expected " + expected);

			GameTestFonts.apply(context, smooth.withCrispEdges(true));
			int classic = shadowPixels(context, "d37-shadow-crisp");
			require(classic == scale, "crisp shadow " + classic + " px at GUI scale " + scale + ", expected " + scale);
			Fontsys.LOGGER.info("[gametest] Shadow at GUI scale {}: {} px smooth, {} px crisp.", scale, thin, classic);
		} finally {
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
			context.setScreen(TitleScreen::new);
		}
	}

	private static void linearCoverage(ClientGameTestContext context) {
		int[][] light = shoot(context, "d36-light-on-dark", 0xFF000000, 0xFFFFFFFF, false);
		int[][] dark = shoot(context, "d36-dark-on-light", 0xFFFFFFFF, 0xFF000000, false);
		long edges = 0;
		long wrong = 0;
		int worst = 0;
		for (int y = 0; y < light.length; y++) {
			for (int x = 0; x < light[y].length; x++) {
				int l = light[y][x];
				int d = dark[y][x];
				if (l == 0 && d == 255) {
					continue; // background in both
				}
				if (l > 0 && l < 255) {
					edges++;
				}
				int error = Math.abs(l + d - 255);
				worst = Math.max(worst, error);
				if (error > TOLERANCE) {
					wrong++;
				}
			}
		}
		require(edges > 0, "the probe found antialiased text");
		require(wrong == 0, wrong + " of " + edges + " edge pixels don't add up (worst off by " + worst + ")");
		Fontsys.LOGGER.info("[gametest] Linear coverage: {} edge pixels, worst error {}.", edges, worst);
	}

	/** How far the shadow reaches past the text, in screen pixels, measured to the right and down. */
	private static int shadowPixels(ClientGameTestContext context, String name) {
		int[] plain = bounds(shoot(context, name + "-off", 0xFF000000, 0xFFFFFFFF, false));
		int[] shadowed = bounds(shoot(context, name, 0xFF000000, 0xFFFFFFFF, true));
		int right = shadowed[0] - plain[0];
		int down = shadowed[1] - plain[1];
		require(right == down, "shadow offset " + right + " px right but " + down + " px down");
		return right;
	}

	/** {max x, max y} of the lit pixels. */
	private static int[] bounds(int[][] image) {
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < image.length; y++) {
			for (int x = 0; x < image[y].length; x++) {
				if (image[y][x] != 0) {
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		require(maxX >= 0, "the probe found the text");
		return new int[] {maxX, maxY};
	}

	/** Draws the sample, screenshots it, and returns the red channel of the text area. */
	private static int[][] shoot(ClientGameTestContext context, String name, int background, int color, boolean shadow) {
		context.setScreen(() -> new ProbeScreen(background, color, shadow));
		context.waitTicks(5);
		int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		Path shot = context.takeScreenshot(name);
		try (InputStream in = Files.newInputStream(shot); NativeImage image = NativeImage.read(in)) {
			int width = Math.min(image.getWidth(), 400 * scale);
			int height = Math.min(image.getHeight(), 30 * scale);
			int[][] red = new int[height][width];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					red[y][x] = image.getPixel(x, y) >> 16 & 0xFF;
				}
			}
			return red;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The sample on a plain background, at whole GUI units. */
	private static final class ProbeScreen extends Screen {
		private final int background;
		private final int color;
		private final boolean shadow;

		ProbeScreen(int background, int color, boolean shadow) {
			super(Component.empty());
			this.background = background;
			this.color = color;
			this.shadow = shadow;
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			graphics.fill(0, 0, width, height, background);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			super.extractRenderState(graphics, mouseX, mouseY, a);
			graphics.text(font, SAMPLE, 8, 8, color, shadow);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
