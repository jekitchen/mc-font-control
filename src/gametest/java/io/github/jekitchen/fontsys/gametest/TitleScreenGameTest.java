package io.github.jekitchen.fontsys.gametest;

import io.github.jekitchen.fontsys.Fontsys;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * M1 smoke test: the client boots with the mod loaded and reaches the title screen.
 * The screenshot is kept as a CI artifact so later milestones can compare text rendering.
 */
@SuppressWarnings("UnstableApiUsage")
public class TitleScreenGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded(Fontsys.MOD_ID)) {
			throw new AssertionError(Fontsys.MOD_ID + " is not loaded");
		}
		context.waitForScreen(TitleScreen.class);
		// The title screen fades in; wait 2 s so the text is fully drawn in the screenshot.
		context.waitTicks(40);
		context.takeScreenshot("m1-title-screen");
	}
}
