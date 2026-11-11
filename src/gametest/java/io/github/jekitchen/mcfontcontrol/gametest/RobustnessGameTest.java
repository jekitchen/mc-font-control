package io.github.jekitchen.mcfontcontrol.gametest;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.discovery.FaceInfo;
import io.github.jekitchen.mcfontcontrol.pack.PackContents;
import io.github.jekitchen.mcfontcontrol.pack.RuntimeFontPackSource;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Robustness (PRD section 6): the applied font survives a resource-pack reload (F3+T) and a
 * GUI-scale change, and automatic oversample follows the new GUI scale.
 */
@SuppressWarnings("UnstableApiUsage")
public class RobustnessGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> face = GameTestFonts.pick(context, "Arial", "Helvetica", "DejaVu Sans");
		if (face.isEmpty()) {
			return;
		}
		GameTestFonts.apply(context, GameTestFonts.settingsFor(face.get()));
		require(PackContents.providerLoaded(), "font applied");

		// F3+T: a plain resource reload keeps the font.
		CompletableFuture<Void> reload = context.computeOnClient(mc -> mc.reloadResourcePacks());
		context.waitFor(mc -> reload.isDone() && mc.gui.overlay() == null, 20 * 60);
		require(PackContents.providerLoaded(), "font survives a resource reload");

		// GUI scale change: automatic oversample reloads at the new scale.
		int before = context.computeOnClient(mc -> RuntimeFontPackSource.currentGuiScale());
		int target = before == 1 ? 2 : 1;
		context.runOnClient(mc -> {
			mc.options.guiScale().set(target);
			mc.resizeGui();
		});
		context.waitFor(mc -> PackContents.loaded().guiScale() == target && mc.gui.overlay() == null, 20 * 30);
		require(PackContents.providerLoaded(), "font reloaded after GUI scale change");
		context.waitTicks(20);
		context.takeScreenshot("m5-gui-scale-" + target);

		context.runOnClient(mc -> {
			mc.options.guiScale().set(0);
			mc.resizeGui();
		});
		context.waitFor(mc -> PackContents.loaded().guiScale() == before && mc.gui.overlay() == null, 20 * 30);
		GameTestFonts.apply(context, FontSettings.DEFAULTS);
		McFontControl.LOGGER.info("[gametest] robustness: reload and GUI scale {} -> {} -> {} ok", before, target, before);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
