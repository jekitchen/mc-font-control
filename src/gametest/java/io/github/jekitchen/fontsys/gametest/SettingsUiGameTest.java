package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.InstanceFonts;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import io.github.jekitchen.fontsys.ui.KeepOrRevertScreen;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
import net.minecraft.network.chat.Component;

/**
 * The 1.1.0 settings-screen improvements, driven with the real mouse and keyboard:
 *
 * <ul>
 *   <li>every control with a tooltip shows it when hovered (UI-14);</li>
 *   <li>the size strip offers the neighboring sizes, and clicking one selects it (UI-15);</li>
 *   <li>the keep-or-revert dialog shows samples in the new font (UI-11);</li>
 *   <li>a kept font is listed first and tagged Recent (UI-17);</li>
 *   <li>font files dropped on the screen are copied into the instance's fonts folder (UI-16);</li>
 *   <li>Options > Language > Font Settings has a Fontsys button (UI-1), and F7 is the default key (UI-2, D39).</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public class SettingsUiGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		defaultKey(context);
		fontOptionsButton(context);
		tooltips(context);
		Optional<FaceInfo> pick = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (pick.isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No system fonts found; skipping the size strip, samples, recent and drop checks.");
			return;
		}
		FontFamily family = Fontsys.fontIndex().families(false).stream()
			.filter(f -> f.name().equals(pick.get().family())).findFirst().orElseThrow();
		try {
			sizeStrip(context, family);
			samplesAndRecent(context, family);
			dropFonts(context, pick.get());
		} finally {
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
			context.setScreen(TitleScreen::new);
		}
	}

	private static void defaultKey(ClientGameTestContext context) {
		InputConstants.Key key = context.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings)
			.filter(k -> k.getName().equals("key.fontsys.open")).findFirst().map(KeyMapping::getDefaultKey).orElseThrow());
		require(key.getValue() == InputConstants.KEY_F7, "the settings key defaults to F7, not " + key.getName());
	}

	/** Options > Language > Font Settings has a button that opens Fontsys and returns there. */
	private static void fontOptionsButton(ClientGameTestContext context) {
		context.setScreen(() -> new FontOptionsScreen(new TitleScreen(), net.minecraft.client.Minecraft.getInstance().options));
		context.waitTicks(5);
		context.takeScreenshot("ui1-font-options");
		ScreenRectangle button = context.computeOnClient(mc -> widgets(mc.gui.screen()).stream()
			.filter(w -> w instanceof Button && w.getMessage().getString().equals(Component.translatable("fontsys.options.button").getString()))
			.map(AbstractWidget::getRectangle).findFirst()
			.orElseThrow(() -> new AssertionError("no Fontsys button in Font Settings")));
		click(context, button.left() + button.width() / 2, button.top() + button.height() / 2);
		context.waitForScreen(FontConfigScreen.class);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(FontOptionsScreen.class);
		context.setScreen(TitleScreen::new);
	}

	/** Hovering each control with a tooltip draws a tooltip box: dark purple pixels that weren't there before. */
	private static void tooltips(ClientGameTestContext context) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(10);
		List<ScreenRectangle> widgets = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).tooltipWidgets().stream()
			.map(AbstractWidget::getRectangle).toList());
		require(widgets.size() == 10, "10 controls have tooltips, found " + widgets.size());
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(3);
		int[][] before = shoot(context, "ui14-no-tooltip");
		List<String> missing = new ArrayList<>();
		for (int i = 0; i < widgets.size(); i++) {
			ScreenRectangle r = widgets.get(i);
			hover(context, r.left() + r.width() / 2, r.top() + r.height() / 2);
			int appeared = tooltipPixels(before, shoot(context, "ui14-tooltip-" + i));
			if (appeared < 1000) {
				missing.add(r + " (" + appeared + " px)");
			}
		}
		context.getInput().setCursorPos(1, 1);
		require(missing.isEmpty(), "no tooltip shown for " + missing);
		context.setScreen(TitleScreen::new);
	}

	/** The strip shows the sizes either side; clicking the larger one selects it. */
	private static void sizeStrip(ClientGameTestContext context, FontFamily family) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(5);
		context.runOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).selectFamily(family));
		context.waitFor(mc -> !((FontConfigScreen) mc.gui.screen()).stripSizes().isEmpty());
		context.waitTicks(3);
		float size = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).draftSize());
		List<Float> sizes = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).stripSizes());
		require(sizes.equals(List.of(size - 1, size, size + 1)), "strip " + sizes + " around " + size);
		context.takeScreenshot("ui15-size-strip");
		ScreenPosition larger = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).stripCenter(size + 1));
		require(larger != null, "the strip drew size " + (size + 1));
		click(context, larger.x(), larger.y());
		float now = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).draftSize());
		require(now == size + 1, "clicking " + (size + 1) + " selected " + now);
		context.waitFor(mc -> ((FontConfigScreen) mc.gui.screen()).stripSizes().equals(List.of(size, size + 1, size + 2)));
		context.takeScreenshot("ui15-size-strip-clicked");
		context.setScreen(TitleScreen::new);
	}

	/** The keep-or-revert dialog draws samples (the sign's wood color is on screen); a kept font is listed first. */
	private static void samplesAndRecent(ClientGameTestContext context, FontFamily family) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(5);
		context.runOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).selectFamily(family));
		context.waitTicks(5);
		context.clickScreenButton("fontsys.screen.apply");
		context.waitForScreen(KeepOrRevertScreen.class);
		context.waitFor(mc -> mc.gui.overlay() == null);
		context.waitTicks(10);
		int[][] rgb = shootRgb(context, "ui11-keep-samples");
		require(count(rgb, 0xA2824E) > 200, "the sign sample is drawn on the keep-or-revert dialog");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(FontConfigScreen.class);
		require(!Fontsys.controller().recentFonts().isEmpty() && family.holds(Fontsys.controller().recentFonts().get(0).familyName(),
			Fontsys.controller().recentFonts().get(0).fontPath()), "the kept font is the most recent");

		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(5);
		Map.Entry<FontFamily, Boolean> first = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).listedFamilies().get(0));
		require(first.getKey().name().equals(family.name()) && first.getValue(), "the kept font is listed first and tagged Recent, not " + first);
		context.takeScreenshot("ui17-recent");
		context.getInput().typeChars("zzz-no-such-font");
		context.waitTicks(3);
		require(context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).listedFamilies().isEmpty()), "searching hides the recent entries too");
		context.setScreen(TitleScreen::new);
	}

	/** Dropping a font file and a text file: the font is copied into fonts/, the text file is skipped. */
	private static void dropFonts(ClientGameTestContext context, FaceInfo face) {
		Path dir = InstanceFonts.dir().orElseThrow();
		Path source = Path.of(face.path());
		Path copy = dir.resolve("dropped-" + source.getFileName());
		Path stagedFont;
		Path stagedText;
		try {
			Path staging = Files.createTempDirectory("fontsys-drop");
			stagedFont = Files.copy(source, staging.resolve(copy.getFileName()));
			stagedText = Files.writeString(staging.resolve("readme.txt"), "not a font");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		try {
			context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
			context.waitTicks(5);
			context.runOnClient(mc -> mc.gui.screen().onFilesDrop(List.of(stagedFont, stagedText)));
			require(Files.isRegularFile(copy), "the dropped font was copied to " + copy);
			require(!Files.exists(dir.resolve("readme.txt")), "the text file was skipped");
			context.waitTicks(5);
			context.takeScreenshot("ui16-dropped");
		} finally {
			context.setScreen(TitleScreen::new);
			try {
				Files.deleteIfExists(copy);
				Files.deleteIfExists(stagedFont);
				Files.deleteIfExists(stagedText);
				Fontsys.fontIndex().scan();
			} catch (IOException e) {
				Fontsys.LOGGER.warn("[gametest] Could not clean up {}: {}", copy, e.toString());
			}
		}
	}

	// ---- helpers -------------------------------------------------------------------------------

	/** Every widget on the screen, including those inside lists. */
	private static List<AbstractWidget> widgets(ContainerEventHandler parent) {
		List<AbstractWidget> result = new ArrayList<>();
		for (GuiEventListener child : parent.children()) {
			if (child instanceof AbstractWidget w) {
				result.add(w);
			}
			if (child instanceof ContainerEventHandler container) {
				result.addAll(widgets(container));
			}
		}
		return result;
	}

	private static void hover(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(guiX * scale + scale / 2, guiY * scale + scale / 2);
		context.waitTicks(3);
	}

	private static void click(ClientGameTestContext context, int guiX, int guiY) {
		hover(context, guiX, guiY);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	/** Pixels that look like a tooltip's dark purple background now but didn't before. */
	private static int tooltipPixels(int[][] before, int[][] after) {
		int count = 0;
		for (int y = 0; y < after.length; y++) {
			for (int x = 0; x < after[y].length; x++) {
				if (tooltipLike(after[y][x]) && !tooltipLike(before[y][x])) {
					count++;
				}
			}
		}
		return count;
	}

	/** The tooltip background, 0xF0100010, over anything. */
	private static boolean tooltipLike(int rgb) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		return r <= 40 && g <= 18 && b <= 40 && Math.abs(r - b) <= 12 && r >= g + 4;
	}

	private static int count(int[][] rgb, int color) {
		int n = 0;
		for (int[] row : rgb) {
			for (int c : row) {
				if (Math.abs((c >> 16 & 0xFF) - (color >> 16 & 0xFF)) <= 3 && Math.abs((c >> 8 & 0xFF) - (color >> 8 & 0xFF)) <= 3
					&& Math.abs((c & 0xFF) - (color & 0xFF)) <= 3) {
					n++;
				}
			}
		}
		return n;
	}

	private static int[][] shoot(ClientGameTestContext context, String name) {
		return shootRgb(context, name);
	}

	/** A screenshot as 0xRRGGBB values, [y][x]. */
	private static int[][] shootRgb(ClientGameTestContext context, String name) {
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

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
