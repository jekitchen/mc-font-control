package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.apply.FontController;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import io.github.jekitchen.fontsys.ui.KeepOrRevertScreen;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * M5: config screen with live preview, Apply with keep-or-revert (Enter keeps, Esc reverts,
 * 30 s timeout reverts), and Reset to vanilla (PRD 5.4, 5.7).
 */
@SuppressWarnings("UnstableApiUsage")
public class ConfigScreenGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> pick = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (pick.isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No system fonts found; skipping the config screen test.");
			return;
		}
		FontFamily family = Fontsys.fontIndex().families(false).stream()
			.filter(f -> f.name().equals(pick.get().family())).findFirst().orElseThrow();

		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(10);
		context.getInput().typeChars(family.name());
		context.waitTicks(5);
		context.takeScreenshot("m5-config-search");

		// Preview only: the game font must not change before Apply (UI-6).
		context.runOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).selectFamily(family));
		context.waitTicks(10);
		require(!PackContents.loaded().active(), "preview must not change the game font");
		context.takeScreenshot("m5-config-preview");

		// Apply, then keep with Enter.
		context.clickScreenButton("fontsys.screen.apply");
		context.waitForScreen(KeepOrRevertScreen.class);
		context.waitFor(mc -> mc.gui.overlay() == null);
		context.waitTicks(30);
		require(PackContents.providerLoaded(), "font should be applied while the dialog is open");
		context.takeScreenshot("m5-keep-or-revert");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(FontConfigScreen.class);
		require(Fontsys.controller().config().pending == null, "Enter keeps the font");
		require(Fontsys.controller().appliedSettings().isActive(), "font stays applied after Keep");
		context.waitTicks(10);
		context.takeScreenshot("m5-config-applied");

		// A bigger size, then Esc: back to the previous settings.
		FontSettings kept = Fontsys.controller().appliedSettings();
		applyThroughController(context, kept.withLayout(14, kept.oversample(), kept.shiftX(), kept.shiftY()));
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.overlay() == null && !(mc.gui.screen() instanceof KeepOrRevertScreen));
		require(Fontsys.controller().appliedSettings().size() == kept.size(), "Esc reverts to the kept size");

		// Do nothing: the countdown reverts on its own.
		applyThroughController(context, kept.withLayout(16, kept.oversample(), kept.shiftX(), kept.shiftY()));
		context.waitFor(mc -> !(mc.gui.screen() instanceof KeepOrRevertScreen), (FontController.CONFIRM_SECONDS + 10) * 20);
		context.waitFor(mc -> mc.gui.overlay() == null);
		require(Fontsys.controller().appliedSettings().size() == kept.size(), "timeout reverts to the kept size");
		require(Fontsys.controller().config().pending == null, "pending cleared after timeout");

		// Reset to vanilla, confirmed.
		context.waitForScreen(FontConfigScreen.class);
		context.clickScreenButton("fontsys.screen.reset");
		context.waitForScreen(KeepOrRevertScreen.class);
		context.clickScreenButton("fontsys.confirm.keep");
		context.waitFor(mc -> mc.gui.overlay() == null);
		require(!Fontsys.controller().appliedSettings().isActive(), "reset to vanilla");
		context.setScreen(TitleScreen::new);
	}

	private static void applyThroughController(ClientGameTestContext context, FontSettings settings) {
		context.runOnClient(mc -> Fontsys.controller().applyWithConfirmation(settings, mc.gui.screen()));
		context.waitForScreen(KeepOrRevertScreen.class);
		context.waitFor(mc -> mc.gui.overlay() == null);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
