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
 * tag, screenshotted in vanilla and with a system font for side-by-side review. Run with
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
				GameTestFonts.apply(context, FontSettings.DEFAULTS);
			}
		}
		context.waitForScreen(TitleScreen.class);
	}

	/** A sign, a glowing sign, a text display and a named pig in front of the player at (0.5, -60, -2.5). */
	static void buildScene(TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamerule advance_time false");
		server.runCommand("fill -4 -61 0 4 -58 6 air");
		server.runCommand("fill -4 -61 6 4 -57 6 stone_bricks");
		server.runCommand("setblock -1 -60 3 oak_sign[rotation=8]{front_text:{messages:[\"Welcome home!\",\"Signs use\",\"the system font\",\"0123456789\"]}}");
		server.runCommand("setblock 1 -60 3 dark_oak_sign[rotation=8]{front_text:{color:\"light_blue\",has_glowing_text:1b,messages:[\"Glow ink\",\"on a\",\"dark oak sign\",\"\"]}}");
		server.runCommand("summon text_display 0.5 -58.2 5.9 {text:\"Text display: The quick brown fox\",background:1073741824,Rotation:[180f,0f]}");
		server.runCommand("summon pig 2.5 -61 4.5 {CustomName:\"Named pig\",CustomNameVisible:1b,NoAI:1b,Silent:1b,Rotation:[180f,0f]}");
		server.runCommand("tp @a 0.5 -60 -1.5 0 5");
	}
}
