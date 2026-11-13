package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.InstanceFonts;
import io.github.jekitchen.fontsys.ui.ChangeSummary;
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
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The 1.1.0 settings-screen improvements, driven with the real mouse and keyboard:
 *
 * <ul>
 *   <li>every control with a tooltip shows it when hovered (UI-14);</li>
 *   <li>the size strip offers the neighboring sizes, and clicking one selects it (UI-15);</li>
 *   <li>the keep-or-revert dialog shows samples in the new font (UI-11);</li>
 *   <li>a kept font is listed first and tagged Recent (UI-17);</li>
 *   <li>font files dropped on the screen are copied into the instance's fonts folder (UI-16);</li>
 *   <li>Options > Language > Font Settings has a Fontsys button (UI-1), and F7 is the default key (UI-2, D39);</li>
 *   <li>the screen's title draws "sys" in the logo's green, and still reads "Fontsys" (D48).</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public class SettingsUiGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		defaultKey(context);
		greenTitle(context);
		tooltips(context);
		clearSearch(context);
		searchFocus(context);
		Optional<FaceInfo> pick = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (pick.isEmpty()) {
			fontOptionsButton(context, null);
			Fontsys.LOGGER.warn("[gametest] No system fonts found; skipping the font-change, size strip, samples, recent and drop checks.");
			return;
		}
		FontFamily family = Fontsys.fontIndex().families(false).stream()
			.filter(f -> f.name().equals(pick.get().family())).findFirst().orElseThrow();
		try {
			fontOptionsButton(context, pick.get());
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

	/**
	 * Options > Language > Font Settings has one Fontsys button, which opens Fontsys and returns
	 * there (UI-1). Coming back, the screen still has one button, and after a font change while
	 * Fontsys was open it is built again in the new font: its title is not cut off (UI-18).
	 */
	private static void fontOptionsButton(ClientGameTestContext context, @Nullable FaceInfo face) {
		context.setScreen(() -> new FontOptionsScreen(new TitleScreen(), net.minecraft.client.Minecraft.getInstance().options));
		context.waitTicks(5);
		context.takeScreenshot("ui1-font-options");
		openFontsysFromFontOptions(context);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(FontOptionsScreen.class);
		require(fontsysButtons(context) == 1, "one Fontsys button after coming back, not " + fontsysButtons(context));

		if (face != null) {
			// A larger font applied while Fontsys is open over Font Settings.
			openFontsysFromFontOptions(context);
			GameTestFonts.apply(context, GameTestFonts.settingsFor(face).withLayout(14, FontSettings.AUTO_OVERSAMPLE, 0, 0));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(FontOptionsScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("ui18-font-options-after-change");
			require(fontsysButtons(context) == 1, "one Fontsys button after a font change, not " + fontsysButtons(context));
			String cut = context.computeOnClient(mc -> widgets(mc.gui.screen()).stream()
				.filter(w -> w instanceof StringWidget && w.getMessage().getString().equals(mc.gui.screen().getTitle().getString()))
				.filter(w -> w.getWidth() < mc.font.width(w.getMessage()))
				.map(w -> w.getWidth() + " < " + mc.font.width(w.getMessage())).findFirst().orElse(null));
			require(cut == null, "the Font Settings title is cut off after the font change: " + cut);
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
		}
		context.setScreen(TitleScreen::new);
	}

	private static void openFontsysFromFontOptions(ClientGameTestContext context) {
		ScreenRectangle button = context.computeOnClient(mc -> widgets(mc.gui.screen()).stream()
			.filter(SettingsUiGameTest::isFontsysButton)
			.map(AbstractWidget::getRectangle).findFirst()
			.orElseThrow(() -> new AssertionError("no Fontsys button in Font Settings")));
		click(context, button.left() + button.width() / 2, button.top() + button.height() / 2);
		context.waitForScreen(FontConfigScreen.class);
	}

	private static int fontsysButtons(ClientGameTestContext context) {
		return context.computeOnClient(mc -> (int) widgets(mc.gui.screen()).stream().filter(SettingsUiGameTest::isFontsysButton).count());
	}

	private static boolean isFontsysButton(AbstractWidget w) {
		return w instanceof Button && w.getMessage().getString().equals(Component.translatable("fontsys.options.button").getString());
	}

	/**
	 * The title draws "Font" in white and "sys" in the logo's green, #56D9A8 (D48). Both colors
	 * must appear in the band at the top of the screen, and the title still reads "Fontsys" for
	 * narration.
	 */
	private static void greenTitle(ClientGameTestContext context) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(5);
		String title = context.computeOnClient(mc -> mc.gui.screen().getTitle().getString());
		require(title.equals("Fontsys"), "the title reads Fontsys, not " + title);
		int scale = context.computeOnClient(mc -> (int) mc.getWindow().getGuiScale());
		int[][] rgb = shootRgb(context, "d48-green-title");
		int[][] band = Arrays.copyOfRange(rgb, 0, Math.min(rgb.length, (8 + 10) * scale));
		int green = count(band, 0x56D9A8);
		int white = count(band, 0xFFFFFF);
		require(green > 10 * scale * scale, "\"sys\" is drawn in green in the title: " + green + " px");
		require(white > 10 * scale * scale, "\"Font\" is drawn in white in the title: " + white + " px");
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
		FontSettings before = Fontsys.controller().appliedSettings();
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(5);
		context.runOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).selectFamily(family));
		context.waitFor(mc -> !((FontConfigScreen) mc.gui.screen()).stripSizes().isEmpty());
		context.waitTicks(3);
		// One size up, so the change summary has a setting row as well as the font.
		float size = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).draftSize()) + 1;
		ScreenPosition larger = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).stripCenter(size));
		require(larger != null, "the strip drew size " + size);
		click(context, larger.x(), larger.y());
		context.waitTicks(3);
		context.clickScreenButton("fontsys.screen.apply");
		context.waitForScreen(KeepOrRevertScreen.class);
		context.waitFor(mc -> mc.gui.overlay() == null);
		context.waitTicks(10);
		int[][] rgb = shootRgb(context, "ui11-keep-samples");
		require(count(rgb, 0xA2824E) > 200, "the sign sample is drawn on the keep-or-revert dialog");
		// The change summary above the samples (D47): the font row ends at the new font, and its
		// gray labels are on screen and in the narration.
		List<ChangeSummary.Row> changes = context.computeOnClient(mc -> ((KeepOrRevertScreen) mc.gui.screen()).changes());
		FontSettings applied = Fontsys.controller().appliedSettings();
		require(!changes.isEmpty() && changes.get(0).to().text().equals(applied.familyName() + " " + applied.styleName()),
			"the summary's first row is the new font, not " + changes);
		require(count(rgb, 0xA0A0A0) > 30, "the summary's labels are drawn");
		if (!before.isActive() || before.size() != size) {
			String expected = size == Math.rint(size) ? String.valueOf((int) size) : String.valueOf(size);
			require(changes.stream().anyMatch(r -> r.labelKey().equals("fontsys.confirm.change.size") && r.to().text().equals(expected)),
				"the summary lists the new size " + expected + ": " + changes);
		}
		String narration = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
		require(narration.contains(applied.familyName()), "the narration reads the changes: " + narration);
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

	/**
	 * Dropping two fonts and a text file while the search box filters both out (UI-16): the fonts
	 * are copied into fonts/, the text file is skipped, and the first font is selected, with the
	 * search cleared so it is in view.
	 */
	private static void dropFonts(ClientGameTestContext context, FaceInfo face) {
		Path dir = InstanceFonts.dir().orElseThrow();
		FaceInfo second = Fontsys.fontIndex().families(false).stream()
			.filter(f -> !f.name().equals(face.family()))
			.map(FontFamily::defaultStyle)
			.filter(f -> !f.path().equals(face.path()) && (f.path().toLowerCase(java.util.Locale.ROOT).endsWith(".ttf") || f.path().toLowerCase(java.util.Locale.ROOT).endsWith(".otf")))
			.findFirst().orElseThrow();
		List<Path> copies = List.of(dir.resolve("dropped-a-" + Path.of(face.path()).getFileName()),
			dir.resolve("dropped-b-" + Path.of(second.path()).getFileName()));
		List<Path> staged = new ArrayList<>();
		try {
			Path staging = Files.createTempDirectory("fontsys-drop");
			staged.add(Files.copy(Path.of(face.path()), staging.resolve(copies.get(0).getFileName())));
			staged.add(Files.copy(Path.of(second.path()), staging.resolve(copies.get(1).getFileName())));
			staged.add(Files.writeString(staging.resolve("readme.txt"), "not a font"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		try {
			context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
			context.waitTicks(5);
			context.getInput().typeChars("zzz-no-such-font");
			context.waitTicks(2);
			context.runOnClient(mc -> mc.gui.screen().onFilesDrop(List.copyOf(staged)));
			require(Files.isRegularFile(copies.get(0)) && Files.isRegularFile(copies.get(1)), "both dropped fonts were copied to " + dir);
			require(!Files.exists(dir.resolve("readme.txt")), "the text file was skipped");
			context.waitFor(mc -> {
				FontFamily selected = ((FontConfigScreen) mc.gui.screen()).listSelection();
				return selected != null && selected.instance() && selected.name().equals(face.family());
			}, 20 * 30);
			require(context.computeOnClient(mc -> !((FontConfigScreen) mc.gui.screen()).listedFamilies().isEmpty()),
				"the search was cleared so the dropped font is in view");
			context.waitTicks(5);
			context.takeScreenshot("ui16-dropped-selected");
		} finally {
			context.setScreen(TitleScreen::new);
			try {
				for (Path path : copies) {
					Files.deleteIfExists(path);
				}
				for (Path path : staged) {
					Files.deleteIfExists(path);
				}
				Fontsys.fontIndex().scan();
			} catch (IOException e) {
				Fontsys.LOGGER.warn("[gametest] Could not clean up the dropped fonts: {}", e.toString());
			}
		}
	}

	/**
	 * Typing straight after the screen opens goes into the search box (UI-3), whether it was
	 * opened with the mouse (a button) or the keyboard (F7, or Enter on a focused button).
	 */
	private static void searchFocus(ClientGameTestContext context) {
		// Mouse: the Fontsys Settings button in Font Settings.
		context.setScreen(() -> new FontOptionsScreen(new TitleScreen(), net.minecraft.client.Minecraft.getInstance().options));
		context.waitTicks(3);
		openFontsysFromFontOptions(context);
		context.waitTicks(3);
		context.getInput().typeChars("ari");
		context.waitTicks(2);
		String typed = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).searchText());
		require(typed.equals("ari"), "opened with the mouse, typing went to the search box: '" + typed + "'");

		// Keyboard: Tab makes the keyboard the last input, as F7 does, then the screen opens.
		context.setScreen(TitleScreen::new);
		context.waitTicks(3);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		context.waitTicks(2);
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(3);
		context.getInput().typeChars("ari");
		context.waitTicks(2);
		typed = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).searchText());
		context.takeScreenshot("ui3-search-focus-keyboard");
		require(typed.equals("ari"), "opened from the keyboard, typing went to the search box: '" + typed + "'");
		context.setScreen(TitleScreen::new);
	}

	/** The search box's × button clears it and shows the whole list again; it hides while the box is empty (UI-3). */
	private static void clearSearch(ClientGameTestContext context) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(5);
		require(clearButton(context) == null, "no clear button while the search box is empty");
		context.getInput().typeChars("zzz-no-such-font");
		context.waitTicks(2);
		require(context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).listedFamilies().isEmpty()), "the search filters the list");
		ScreenRectangle clear = clearButton(context);
		require(clear != null, "a clear button while the search box has text");
		context.takeScreenshot("ui3-search-clear");
		click(context, clear.left() + clear.width() / 2, clear.top() + clear.height() / 2);
		require(context.computeOnClient(mc -> !((FontConfigScreen) mc.gui.screen()).listedFamilies().isEmpty()), "clearing shows the list again");
		require(clearButton(context) == null, "the clear button hides again");
		context.setScreen(TitleScreen::new);
	}

	private static @Nullable ScreenRectangle clearButton(ClientGameTestContext context) {
		return context.computeOnClient(mc -> widgets(mc.gui.screen()).stream()
			.filter(w -> w instanceof Button && w.visible && w.getMessage().getString().equals("×"))
			.map(AbstractWidget::getRectangle).findFirst().orElse(null));
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
