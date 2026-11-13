package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.InstanceFonts;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Crisp mode (PRD AP-10, D34), acceptance criteria 1 and 4: white text on black, screenshotted.
 * Crisp, every text pixel is fully on or off; turned off, antialiasing (gray edge pixels) is back.
 *
 * <p>Uses the pixel font named by {@code FONTSYS_PIXEL_FONT} when set (copied into the test
 * instance's fonts folder, never committed), otherwise an installed system font.
 */
@SuppressWarnings("UnstableApiUsage")
public class CrispModeGameTest implements FabricClientGameTest {
	private static final String SAMPLE = "The quick brown fox 0123456789 @%&";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Path copied = copyPixelFont();
		try {
			Optional<FaceInfo> pick = copied != null
				? instanceFace(context, copied)
				: GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
			if (pick.isEmpty()) {
				Fontsys.LOGGER.warn("[gametest] No fonts found; skipping the crisp mode test.");
				return;
			}
			FontSettings smooth = GameTestFonts.settingsFor(pick.get());

			GameTestFonts.apply(context, smooth.withCrispEdges(true));
			require(PackContents.providerLoaded() && PackContents.loaded().crisp(), "the crisp font is applied");
			long[] crisp = probe(context, "ap10-crisp");
			require(crisp[1] > 0, "the probe found the text");
			require(crisp[0] == 0, crisp[0] + " gray pixels in crisp text");
			// The screen with crisp on: the slider steps aside, and a pixel font gets a size hint.
			context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
			context.waitTicks(10);
			context.takeScreenshot("ap10-config-screen");

			GameTestFonts.apply(context, smooth);
			long[] antialiased = probe(context, "ap10-smooth");
			require(antialiased[0] > 0, "antialiasing is back when crisp mode is off");
			Fontsys.LOGGER.info("[gametest] Crisp text: {} lit, 0 gray. Smooth text: {} gray pixels.", crisp[1], antialiased[0]);
		} finally {
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
			context.setScreen(TitleScreen::new);
			if (copied != null) {
				try {
					Files.deleteIfExists(copied);
				} catch (IOException e) {
					Fontsys.LOGGER.warn("[gametest] Could not delete {}: {}", copied, e.toString());
				}
				rescan(context);
			}
		}
	}

	/** Draws the sample, screenshots it, and counts {gray, lit} pixels in the text area. */
	private static long[] probe(ClientGameTestContext context, String name) {
		context.setScreen(ProbeScreen::new);
		context.waitTicks(5);
		int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		Path shot = context.takeScreenshot(name);
		try (InputStream in = Files.newInputStream(shot); NativeImage image = NativeImage.read(in)) {
			long gray = 0;
			long lit = 0;
			int x1 = Math.min(image.getWidth(), 400 * scale);
			int y1 = Math.min(image.getHeight(), 40 * scale);
			for (int y = 0; y < y1; y++) {
				for (int x = 0; x < x1; x++) {
					int argb = image.getPixel(x, y);
					int r = argb >> 16 & 0xFF;
					int g = argb >> 8 & 0xFF;
					int b = argb & 0xFF;
					if (r == 255 && g == 255 && b == 255) {
						lit++;
					} else if (r != 0 || g != 0 || b != 0) {
						gray++;
					}
				}
			}
			return new long[] {gray, lit};
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** White text on plain black, at whole GUI units, without a shadow. */
	private static final class ProbeScreen extends Screen {
		ProbeScreen() {
			super(Component.empty());
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			graphics.fill(0, 0, width, height, 0xFF000000);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			super.extractRenderState(graphics, mouseX, mouseY, a);
			graphics.text(font, SAMPLE, 8, 8, 0xFFFFFFFF, false);
			graphics.text(font, Component.literal(SAMPLE).withStyle(net.minecraft.ChatFormatting.BOLD), 8, 22, 0xFFFFFFFF, false);
		}
	}

	private static Path copyPixelFont() {
		String env = System.getenv("FONTSYS_PIXEL_FONT");
		if (env == null || env.isBlank() || !Files.isRegularFile(Path.of(env)) || InstanceFonts.dir().isEmpty()) {
			return null;
		}
		Path source = Path.of(env);
		Path target = InstanceFonts.dir().get().resolve(source.getFileName());
		try {
			Files.createDirectories(target.getParent());
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			return target;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Optional<FaceInfo> instanceFace(ClientGameTestContext context, Path file) {
		rescan(context);
		return Fontsys.fontIndex().bestAvailable().allFaces().stream()
			.filter(f -> Path.of(f.path()).equals(file.toAbsolutePath().normalize()))
			.findFirst();
	}

	private static void rescan(ClientGameTestContext context) {
		CompletableFuture<?> scan = context.computeOnClient(mc -> Fontsys.fontIndex().scan());
		context.waitFor(mc -> scan.isDone(), 20 * 60);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
