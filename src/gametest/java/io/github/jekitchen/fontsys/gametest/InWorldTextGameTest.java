package io.github.jekitchen.fontsys.gametest;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * M6: in-world text with a system font: a sign, a glowing sign, a text display and a name
 * tag, screenshotted in vanilla and with a system font for side-by-side review, then up close
 * in crisp mode (M8). Run with
 * {@code -PwithSodiumIris} to repeat it with Sodium and Iris installed.
 */
@SuppressWarnings("UnstableApiUsage")
public class InWorldTextGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> face = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		String suffix = Fontsys.platform().isModLoaded("iris") ? "-iris" : Fontsys.platform().isModLoaded("sodium") ? "-sodium" : "";

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			buildScene(world);
			world.getConnection().waitForChunksRender();
			context.waitTicks(40);
			context.takeScreenshot("m6-world-vanilla" + suffix);

			if (face.isPresent()) {
				GameTestFonts.apply(context, GameTestFonts.settingsFor(face.get()));
				world.getConnection().waitForChunksRender();
				context.waitTicks(40);
				context.takeScreenshot("m6-world-system-font" + suffix);
				// Close-up: z-fighting shows most at a shallow angle and short distance.
				world.getServer().runCommand("tp @a 0.5 -60 1.2 -25 10");
				context.waitTicks(20);
				context.takeScreenshot("m6-world-closeup" + suffix);
				// Crisp mode (AP-10) on signs, glow ink, text displays and name tags.
				GameTestFonts.apply(context, GameTestFonts.settingsFor(face.get()).withCrispEdges(true));
				world.getConnection().waitForChunksRender();
				context.waitTicks(20);
				context.takeScreenshot("m8-world-crisp-closeup" + suffix);
				GameTestFonts.apply(context, FontSettings.DEFAULTS);
			}
		}
		context.waitForScreen(TitleScreen.class);
	}

	/** A sign, a glowing sign, a text display and three named dogs in front of the player at (0.5, -60, -2.5). */
	static void buildScene(TestSingleplayerContext world) {
		var server = world.getServer();
		InWorldScene.COMMANDS.forEach(server::runCommand);
	}
}
