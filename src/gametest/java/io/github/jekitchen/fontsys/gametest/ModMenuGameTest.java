package io.github.jekitchen.fontsys.gametest;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * UI-1: when Mod Menu is installed, its config button for Fontsys opens our screen, and the
 * Fontsys page lists the Modrinth, CurseForge and Buy Me a Coffee links (D45).
 * Skipped unless Mod Menu is present (run with -PwithSodiumIris).
 */
@SuppressWarnings("UnstableApiUsage")
public class ModMenuGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
			return;
		}
		context.waitForScreen(TitleScreen.class);
		Screen screen = context.computeOnClient(mc -> FabricLoader.getInstance()
			.getEntrypointContainers("modmenu", com.terraformersmc.modmenu.api.ModMenuApi.class).stream()
			.filter(c -> c.getProvider().getMetadata().getId().equals(Fontsys.MOD_ID))
			.findFirst().orElseThrow(() -> new AssertionError("no modmenu entrypoint"))
			.getEntrypoint().getModConfigScreenFactory().create(mc.gui.screen()));
		if (!(screen instanceof FontConfigScreen)) {
			throw new AssertionError("Mod Menu opened " + screen);
		}
		context.setScreen(() -> screen);
		context.waitTicks(10);
		context.takeScreenshot("m7-modmenu-config");
		context.setScreen(TitleScreen::new);

		// Links under the description (D45): Modrinth, CurseForge and Buy Me a Coffee.
		var links = FabricLoader.getInstance().getModContainer(Fontsys.MOD_ID).orElseThrow().getMetadata()
			.getCustomValue("modmenu").getAsObject().get("links").getAsObject();
		for (String key : java.util.List.of("modmenu.modrinth", "modmenu.curseforge", "modmenu.buymeacoffee")) {
			if (links.get(key) == null || !links.get(key).getAsString().startsWith("https://")) {
				throw new AssertionError("no Mod Menu link " + key);
			}
		}

		// The mod list: Fontsys shows its own icon (assets/fontsys/icon.png, PRD D28).
		context.waitForScreen(TitleScreen.class);
		context.clickScreenButton("modmenu.title");
		context.waitTicks(10);
		context.getInput().typeChars("Fontsys");
		context.waitTicks(10);
		context.takeScreenshot("m7-modmenu-list");
		context.setScreen(TitleScreen::new);
	}
}
