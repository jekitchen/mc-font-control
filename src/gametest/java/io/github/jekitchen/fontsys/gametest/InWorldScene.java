package io.github.jekitchen.fontsys.gametest;

import java.util.List;

/**
 * The shared in-world text scene (M6): a sign, a glowing sign, a text display and three named
 * dogs in front of the player at (0.5, -60, -2.5), in a superflat world. Plain server commands
 * with no loader API, so the Fabric game tests ({@link InWorldTextGameTest#buildScene}) and the
 * NeoForge smoke test (PRD D57, which compiles this one file) build the same scene.
 */
public final class InWorldScene {
	public static final List<String> COMMANDS = List.of(
		"time set noon",
		"weather clear",
		"gamerule advance_time false",
		"fill -4 -61 0 4 -58 6 air",
		"fill -4 -61 6 4 -57 6 stone_bricks",
		"setblock -1 -60 3 oak_sign[rotation=8]{front_text:{messages:[\"Welcome home!\",\"Signs use\",\"the system font\",\"0123456789\"]}}",
		"setblock 1 -60 3 dark_oak_sign[rotation=8]{front_text:{color:\"light_blue\",has_glowing_text:1b,messages:[\"Glow ink\",\"on a\",\"dark oak sign\",\"\"]}}",
		"summon text_display 0.5 -58.2 5.9 {text:\"Text display: The quick brown fox\",background:1073741824,Rotation:[180f,0f]}",
		// Three dogs sitting side by side, facing the player, about three blocks apart so their name
		// tags (about three blocks wide each) don't overlap. Left to right: Asher (woods: gray, white
		// and brown), Ozzie (ashen, the closest coat to black and white) and Ellie (spotted: black,
		// white and brown).
		"summon wolf 3.6 -61 4.5 {CustomName:\"Asher the Aussie\",variant:\"minecraft:woods\",CustomNameVisible:1b,Sitting:1b,NoAI:1b,Silent:1b,Rotation:[180f,0f]}",
		"summon wolf 0.4 -61 4.5 {CustomName:\"Ozzie the Aussie\",variant:\"minecraft:ashen\",CustomNameVisible:1b,Sitting:1b,NoAI:1b,Silent:1b,Rotation:[180f,0f]}",
		"summon wolf -2.8 -61 4.5 {CustomName:\"Ellie the Aussie\",variant:\"minecraft:spotted\",CustomNameVisible:1b,Sitting:1b,NoAI:1b,Silent:1b,Rotation:[180f,0f]}",
		// Turn each dog toward the camera. Facing straight north, the outer dogs look side-on:
		// at Minecraft's wide field of view, a snout near the edge of the picture is stretched outward.
		"execute as @e[type=wolf] at @s run tp @s ~ ~ ~ facing 0.5 -60.4 -0.6",
		"tp @a 0.5 -60 -1.5 0 5"
	);

	private InWorldScene() {
	}
}
