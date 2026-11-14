package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.ui.AdvancedFontScreen;
import io.github.jekitchen.fontsys.ui.ChangeSummary;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import io.github.jekitchen.fontsys.ui.KeepOrRevertScreen;
import io.github.jekitchen.fontsys.ui.UnappliedChangesScreen;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * The Advanced screen (PRD UI-19, D52), driven with the real mouse and keyboard. With a font
 * applied, so a shift is the only change:
 *
 * <ul>
 *   <li>Advanced... opens it; moving Shift Y from the keyboard and pressing Esc goes back with
 *       the change kept, unapplied, the button counting it and keyboard focus back on it;</li>
 *   <li>Done on the settings screen then asks "Apply your changes?" (UI-13, D35);</li>
 *   <li>Apply lists Shift Y in the keep-or-revert dialog's summary (D47);</li>
 *   <li>Reset to defaults puts the shifts back, and Done there returns with nothing to apply;</li>
 *   <li>each font keeps its own tuning (D55): a shift made on one font doesn't follow the player
 *       to another, comes back on returning, and is remembered once that font is kept;</li>
 *   <li>both screens lay out at the smallest GUI, GUI scale 1 and GUI scale 4.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public class AdvancedScreenGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> pick = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (pick.isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No system fonts found; skipping the Advanced screen test.");
			return;
		}
		try {
			GameTestFonts.apply(context, GameTestFonts.settingsFor(pick.get()));
			shiftKeptAndApplied(context);
			resetToDefaults(context);
			GameTestFonts.pick(context, "Georgia", "Times New Roman", "DejaVu Serif", "Arial")
				.filter(other -> !other.family().equals(pick.get().family()))
				.ifPresentOrElse(other -> eachFontKeepsItsTuning(context, pick.get(), other),
					() -> Fontsys.LOGGER.warn("[gametest] Only one font family found; skipping the per-font tuning check."));
			layout(context);
		} finally {
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
			context.setScreen(TitleScreen::new);
		}
	}

	/**
	 * Both screens at the smallest GUI Minecraft allows (320 × 240), GUI scale 1 and GUI scale 4:
	 * every control on screen, and none overlapping another.
	 */
	private static void layout(ClientGameTestContext context) {
		int[][] cases = {{640, 480, 2}, {854, 480, 1}, {1920, 1080, 4}};
		try {
			for (int[] c : cases) {
				context.getInput().resizeWindow(c[0], c[1]);
				guiScale(context, c[2]);
				String name = "ui19-layout-" + c[0] + "x" + c[1] + "-scale" + c[2];
				context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
				context.waitTicks(10);
				requireTidy(context, name + " settings");
				context.takeScreenshot(name + "-settings");
				context.clickScreenButton("fontsys.screen.advanced");
				context.waitForScreen(AdvancedFontScreen.class);
				context.waitTicks(10);
				requireTidy(context, name + " advanced");
				context.takeScreenshot(name + "-advanced");
				context.setScreen(TitleScreen::new);
			}
		} finally {
			context.getInput().resizeWindow(854, 480);
			guiScale(context, 0);
		}
	}

	/** On the settings screen only the Advanced button and Crisp edges are checked; the Advanced screen, everything. */
	private static void requireTidy(ClientGameTestContext context, String where) {
		String problem = context.computeOnClient(mc -> {
			int w = mc.gui.screen().width;
			int h = mc.gui.screen().height;
			List<AbstractWidget> all = mc.gui.screen().children().stream()
				.filter(c -> c instanceof AbstractWidget a && a.visible).map(c -> (AbstractWidget) c).toList();
			List<AbstractWidget> checked = mc.gui.screen() instanceof FontConfigScreen
				? all.stream().filter(a -> a.getMessage().getString().startsWith("Advanced")
					|| a.getMessage().getString().equals(Component.translatable("fontsys.screen.crisp").getString())).toList()
				: all;
			for (AbstractWidget a : checked) {
				if (a.getX() < 0 || a.getY() < 0 || a.getRight() > w || a.getBottom() > h) {
					return a.getMessage().getString() + " is off screen at " + a.getRectangle() + " in " + w + "x" + h;
				}
				for (AbstractWidget b : all) {
					if (a != b && a.getX() < b.getRight() && b.getX() < a.getRight() && a.getY() < b.getBottom() && b.getY() < a.getBottom()) {
						return a.getMessage().getString() + " " + a.getRectangle() + " overlaps " + b.getMessage().getString() + " " + b.getRectangle();
					}
				}
			}
			return null;
		});
		require(problem == null, where + ": " + problem);
	}

	/**
	 * 0 is Auto. With automatic sharpness a new GUI scale reloads the font once it has been
	 * stable for a second (D2), so this waits for that reload to finish.
	 */
	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(40);
		context.waitFor(mc -> mc.gui.overlay() == null, 20 * 60);
		context.waitTicks(10);
	}

	private static void shiftKeptAndApplied(ClientGameTestContext context) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(10);
		require(advancedLabel(context).equals(Component.translatable("fontsys.screen.advanced").getString()),
			"no count on Advanced... with every setting at its default: " + advancedLabel(context));
		context.clickScreenButton("fontsys.screen.advanced");
		context.waitForScreen(AdvancedFontScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("ui19-advanced");

		// Shift Y, from the keyboard as vanilla sliders work: Tab to it (Shift X comes first);
		// a slider reached with Tab takes the arrow keys straight away. Right until it reads 1.
		context.getInput().pressKey(InputConstants.KEY_TAB);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		require(focusedLabel(context).startsWith("Shift Y"), "Tab, Tab reaches Shift Y, not " + focusedLabel(context));
		String target = Component.translatable("fontsys.screen.shift_y", "1").getString();
		for (int i = 0; i < 400 && !sliderLabel(context, "Shift Y").equals(target); i++) {
			context.getInput().pressKey(InputConstants.KEY_RIGHT);
		}
		require(sliderLabel(context, "Shift Y").equals(target), "Right moves Shift Y to 1: " + sliderLabel(context, "Shift Y"));
		context.waitTicks(10);
		context.takeScreenshot("ui19-advanced-shift-y");

		// Esc: back to the settings with the change kept, not applied.
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(FontConfigScreen.class);
		float shiftY = context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).draft().shiftY());
		require(shiftY == 1, "Esc on the Advanced screen keeps the Shift Y change, got " + shiftY);
		require(Fontsys.controller().appliedSettings().shiftY() == 0, "nothing is applied before Apply");
		require(advancedLabel(context).equals(Component.translatable("fontsys.screen.advanced.changed", 1).getString()),
			"Advanced... counts the changed setting: " + advancedLabel(context));
		require(focusedLabel(context).equals(advancedLabel(context)), "back from the keyboard, focus is on Advanced..., not " + focusedLabel(context));
		context.waitTicks(5);
		context.takeScreenshot("ui19-settings-advanced-count");

		// Done asks about the advanced-only change (D35); Back keeps it.
		require(context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).hasUnappliedChanges()), "a shift is an unapplied change");
		context.clickScreenButton("gui.done");
		context.waitForScreen(UnappliedChangesScreen.class);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(FontConfigScreen.class);
		require(context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).draft().shiftY()) == shiftY, "Back keeps the shift");

		// Apply: the keep-or-revert summary lists Shift Y (D47), then Esc reverts.
		context.clickScreenButton("fontsys.screen.apply");
		context.waitForScreen(KeepOrRevertScreen.class);
		context.waitFor(mc -> mc.gui.overlay() == null);
		context.waitTicks(10);
		List<ChangeSummary.Row> changes = context.computeOnClient(mc -> ((KeepOrRevertScreen) mc.gui.screen()).changes());
		String expected = String.format(Locale.ROOT, shiftY == Math.rint(shiftY) ? "%.0f" : "%.1f", shiftY);
		require(changes.stream().anyMatch(r -> r.labelKey().equals("fontsys.confirm.change.shift_y")
			&& r.from() != null && r.from().text().equals("0") && r.to().text().equals(expected)),
			"the summary lists Shift Y 0 → " + expected + ": " + changes);
		require(changes.size() == 2 && !changes.get(0).changed(), "only the shift changed, under the same font: " + changes);
		context.takeScreenshot("ui19-keep-shift-y");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.overlay() == null && mc.gui.screen() instanceof FontConfigScreen);
		require(Fontsys.controller().appliedSettings().shiftY() == 0, "Esc reverted the shift");
		context.setScreen(TitleScreen::new);
	}

	/**
	 * Per-font tuning (D55). With {@code first} applied: Shift Y 1 on it, unapplied; picking
	 * {@code second} (never kept) shows the defaults; picking {@code first} again brings the shift
	 * back. Then {@code first} is kept with the shift and {@code second} applied, and in a new
	 * visit to the screen picking {@code first} restores the shift it was kept with.
	 */
	private static void eachFontKeepsItsTuning(ClientGameTestContext context, FaceInfo first, FaceInfo second) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(10);
		setShiftY(context, "1");
		require(draft(context).shiftY() == 1 && draft(context).familyName().equals(first.family()), "Shift Y 1 on " + first.family());

		selectFamily(context, second.family());
		require(draft(context).familyName().equals(second.family()), "picked " + second.family());
		require(draft(context).shiftY() == 0, "a font never kept starts at the defaults, not with the other font's shift: " + draft(context));
		require(advancedLabel(context).equals(Component.translatable("fontsys.screen.advanced").getString()),
			"no count on Advanced... for a font at its defaults: " + advancedLabel(context));

		selectFamily(context, first.family());
		require(draft(context).shiftY() == 1, "going back to " + first.family() + " brings back its unapplied shift: " + draft(context));

		// Keep the first font with its shift, then apply the second one.
		applyAndKeep(context);
		require(Fontsys.controller().appliedSettings().shiftY() == 1, "kept with the shift");
		selectFamily(context, second.family());
		require(draft(context).shiftY() == 0, "the second font is still at the defaults");
		applyAndKeep(context);
		require(Fontsys.controller().appliedSettings().familyName().equals(second.family()), second.family() + " is applied");

		// A new visit: the first font is no longer applied, so its shift comes from the recent list.
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(10);
		require(draft(context).shiftY() == 0, "the screen opens on " + second.family() + " at the defaults");
		selectFamily(context, first.family());
		require(draft(context).shiftY() == 1, first.family() + " comes back with the shift it was kept with: " + draft(context));
		require(advancedLabel(context).equals(Component.translatable("fontsys.screen.advanced.changed", 1).getString()),
			"Advanced... counts the remembered shift: " + advancedLabel(context));
		context.takeScreenshot("d55-remembered-tuning");
		context.setScreen(TitleScreen::new);
	}

	private static FontSettings draft(ClientGameTestContext context) {
		return context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).draft());
	}

	private static void selectFamily(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).selectFamily(Fontsys.fontIndex().families(false).stream()
			.filter(f -> f.name().equals(name)).findFirst().orElseThrow()));
		context.waitTicks(3);
	}

	/** Opens Advanced, moves Shift Y to {@code value} from the keyboard and goes back. */
	private static void setShiftY(ClientGameTestContext context, String value) {
		context.clickScreenButton("fontsys.screen.advanced");
		context.waitForScreen(AdvancedFontScreen.class);
		context.waitTicks(5);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		String target = Component.translatable("fontsys.screen.shift_y", value).getString();
		for (int i = 0; i < 400 && !sliderLabel(context, "Shift Y").equals(target); i++) {
			context.getInput().pressKey(InputConstants.KEY_RIGHT);
		}
		require(sliderLabel(context, "Shift Y").equals(target), "Shift Y reaches " + value + ": " + sliderLabel(context, "Shift Y"));
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(FontConfigScreen.class);
	}

	/** Apply, then Enter on Keep this font?, back on the settings screen. */
	private static void applyAndKeep(ClientGameTestContext context) {
		context.clickScreenButton("fontsys.screen.apply");
		context.waitForScreen(KeepOrRevertScreen.class);
		context.waitFor(mc -> mc.gui.overlay() == null);
		context.waitTicks(5);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitFor(mc -> mc.gui.overlay() == null && mc.gui.screen() instanceof FontConfigScreen);
		context.waitTicks(5);
	}

	private static void resetToDefaults(ClientGameTestContext context) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(5);
		context.clickScreenButton("fontsys.screen.advanced");
		context.waitForScreen(AdvancedFontScreen.class);
		context.waitTicks(3);
		require(!resetActive(context), "Reset to defaults is off while everything is at its default");
		ScreenRectangle slider = sliderRect(context, "Shift X");
		click(context, slider.left() + slider.width() / 8, slider.top() + slider.height() / 2);
		require(resetActive(context), "Reset to defaults turns on after a change");
		context.clickScreenButton("fontsys.advanced.reset");
		require(!resetActive(context), "Reset to defaults turns off again");
		require(sliderLabel(context, "Shift X").equals(Component.translatable("fontsys.screen.shift_x", "0").getString()),
			"the Shift X slider shows 0 again: " + sliderLabel(context, "Shift X"));
		context.clickScreenButton("gui.done");
		context.waitForScreen(FontConfigScreen.class);
		require(context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).draft().shiftX()) == 0, "the reset reached the draft");
		require(!context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).hasUnappliedChanges()), "nothing left to apply after the reset");
		require(advancedLabel(context).equals(Component.translatable("fontsys.screen.advanced").getString()),
			"no count after the reset: " + advancedLabel(context));
		context.clickScreenButton("gui.done");
		context.waitForScreen(TitleScreen.class);
	}

	// ---- helpers -------------------------------------------------------------------------------

	private static String focusedLabel(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			GuiEventListener f = mc.gui.screen().getFocused();
			return f instanceof AbstractWidget w ? w.getMessage().getString() : String.valueOf(f);
		});
	}

	private static String advancedLabel(ClientGameTestContext context) {
		return context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).advancedLabel().getString());
	}

	private static AbstractWidget advancedWidget(net.minecraft.client.Minecraft mc, String prefix) {
		return ((AdvancedFontScreen) mc.gui.screen()).tooltipWidgets().stream()
			.filter(w -> w.getMessage().getString().startsWith(prefix)).findFirst()
			.orElseThrow(() -> new AssertionError("no '" + prefix + "' control on the Advanced screen"));
	}

	private static ScreenRectangle sliderRect(ClientGameTestContext context, String prefix) {
		return context.computeOnClient(mc -> advancedWidget(mc, prefix).getRectangle());
	}

	private static String sliderLabel(ClientGameTestContext context, String prefix) {
		return context.computeOnClient(mc -> advancedWidget(mc, prefix).getMessage().getString());
	}

	private static boolean resetActive(ClientGameTestContext context) {
		String reset = Component.translatable("fontsys.advanced.reset").getString();
		return context.computeOnClient(mc -> advancedWidget(mc, reset).active);
	}

	private static void click(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(guiX * scale + scale / 2, guiY * scale + scale / 2);
		context.waitTicks(3);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
