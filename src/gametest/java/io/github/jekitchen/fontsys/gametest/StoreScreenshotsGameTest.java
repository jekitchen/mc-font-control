package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import io.github.jekitchen.fontsys.ui.KeepOrRevertScreen;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * Gallery screenshots for the Modrinth and CurseForge pages (docs/store/). Real game renders at
 * 1920x1080, each with the system font and, where a comparison helps, vanilla. Skipped unless
 * the environment variable {@code FONTSYS_STORE_SCREENSHOTS} is set, so CI doesn't pay for it:
 * <pre>FONTSYS_STORE_SCREENSHOTS=1 ./gradlew runClientGameTest</pre>
 * Output: {@code build/store-screenshots/}.
 */
@SuppressWarnings("UnstableApiUsage")
public class StoreScreenshotsGameTest implements FabricClientGameTest {
	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("FONTSYS_STORE_SCREENSHOTS") == null) {
			return;
		}
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> main = GameTestFonts.pick(context, "Inter", "Segoe UI", "Helvetica Neue", "DejaVu Sans");
		Optional<FaceInfo> serif = GameTestFonts.pick(context, "Georgia", "Times New Roman", "DejaVu Serif");
		if (main.isEmpty() || serif.isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] Store screenshots need installed fonts; skipped.");
			return;
		}
		context.getInput().resizeWindow(WIDTH, HEIGHT);
		context.waitTicks(20);

		// Title screen: vanilla, then the system font.
		GameTestFonts.apply(context, FontSettings.DEFAULTS);
		shot(context, "02-title-vanilla");
		GameTestFonts.apply(context, GameTestFonts.settingsFor(main.get()));
		shot(context, "01-title-" + slug(main.get().family()));

		// Config screen: previewing a serif font while the UI uses the main font. The search box
		// stays empty, so the list shows the fonts around the selection, not just one.
		FontFamily serifFamily = Fontsys.fontIndex().families(false).stream()
			.filter(f -> f.name().equals(serif.get().family())).findFirst().orElseThrow();
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(10);
		context.runOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).selectFamily(serifFamily));
		// Typing and deleting a letter refreshes the list, which highlights and centers the selection.
		context.getInput().typeChars("x");
		context.waitTicks(2);
		context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
		context.waitTicks(20);
		shot(context, "03-config-preview");

		// The "Keep this font?" dialog with its samples in the serif font (UI-11); kept, so the
		// title screen behind it is in that font too.
		context.clickScreenButton("fontsys.screen.apply");
		context.waitForScreen(KeepOrRevertScreen.class);
		context.waitFor(mc -> mc.gui.overlay() == null, 20 * 30);
		context.waitTicks(20);
		shot(context, "08-keep-" + slug(serif.get().family()));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.setScreen(TitleScreen::new);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			world.getConnection().waitForChunksRender();
			server.runCommand("difficulty peaceful");
			server.runCommand("gamemode survival @a");
			server.runCommand("give @a diamond_sword[enchantments={sharpness:5,unbreaking:3},lore=[\"Forged for the Ender Dragon\"]]");
			server.runCommand("give @a diamond_pickaxe");
			server.runCommand("give @a torch 64");
			server.runCommand("give @a bread 32");
			server.runCommand("give @a oak_sign 16");
			InWorldTextGameTest.buildScene(world);
			// Closer to the signs, and GUI scale 3 so chat and tooltips leave room for the world.
			server.runCommand("tp @a 0.5 -60 0.3 0 12");
			guiScale(context, 3);
			world.getConnection().waitForChunksRender();
			context.waitTicks(40);

			// HUD and chat with server-style rank tags (bold) and a whisper (italic): vanilla, then the system font.
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
			chat(context);
			shot(context, "05-world-chat-vanilla");
			GameTestFonts.apply(context, GameTestFonts.settingsFor(main.get()));
			chat(context);
			shot(context, "04-world-chat-" + slug(main.get().family()));

			// Signs, glow ink, a text display and a name tag in a serif font.
			GameTestFonts.apply(context, GameTestFonts.settingsFor(serif.get()));
			context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
			context.waitTicks(40);
			shot(context, "06-signs-" + slug(serif.get().family()));

			// An enchanted item's tooltip: name, enchantments, lore (italic) and attributes.
			GameTestFonts.apply(context, GameTestFonts.settingsFor(main.get()));
			context.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
			context.waitTicks(10);
			hoverHotbarSlot(context, 0);
			context.waitTicks(10);
			shot(context, "07-tooltip-" + slug(main.get().family()));
			context.setScreen(() -> null);
			guiScale(context, 0);
		}
		context.waitForScreen(TitleScreen.class);
		GameTestFonts.apply(context, FontSettings.DEFAULTS);
	}

	/** 0 is Auto. */
	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(5);
	}

	private static void chat(ClientGameTestContext context) {
		List<Component> lines = List.of(
			Component.empty()
				.append(Component.literal("[ADMIN] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
				.append(Component.literal("Welcome to the server! Spawn shops are open.").withStyle(ChatFormatting.WHITE)),
			Component.empty()
				.append(Component.literal("[DONATOR] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.append(Component.literal("Steve: anyone up for a mining trip?").withStyle(ChatFormatting.WHITE)),
			Component.literal("<Alex> Sure! Meet at the portal in 5 minutes."),
			Component.literal("Steve whispers to you: did you change your font?").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
			Component.literal("Diamonds found: 12 · Depth: -58 · Biome: Plains").withStyle(ChatFormatting.AQUA));
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			lines.forEach(mc.gui.hud.getChat()::addClientSystemMessage);
		});
		context.waitTicks(20);
	}

	/** Moves the cursor over a hotbar slot of the survival inventory (176x166, hotbar row at y = 142). */
	private static void hoverHotbarSlot(ClientGameTestContext context, int slot) {
		double[] pos = context.computeOnClient(mc -> {
			int scale = mc.getWindow().getGuiScale();
			int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			return new double[] {(left + 8 + slot * 18 + 8) * scale, (top + 142 + 8) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.waitTicks(10);
		Path out = Path.of(System.getProperty("user.dir")).resolve("../../store-screenshots").normalize();
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
	}

	private static String slug(String name) {
		return name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}
}
