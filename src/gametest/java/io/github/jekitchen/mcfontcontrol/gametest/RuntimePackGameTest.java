package io.github.jekitchen.mcfontcontrol.gametest;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.discovery.FaceInfo;
import io.github.jekitchen.mcfontcontrol.pack.PackContents;
import io.github.jekitchen.mcfontcontrol.pack.RuntimeFontPackSource;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.repository.Pack;

/**
 * M3: a system font applied through the runtime pack shows on the title screen and in a
 * world, a missing font file falls back to vanilla, and Reset returns to vanilla.
 */
@SuppressWarnings("UnstableApiUsage")
public class RuntimePackGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		List<String> selected = context.computeOnClient(mc -> mc.getResourcePackRepository().getSelectedPacks().stream().map(Pack::getId).toList());
		if (!selected.contains(RuntimeFontPackSource.PACK_ID)) {
			throw new AssertionError("runtime pack not selected: " + selected);
		}

		Optional<FaceInfo> face = GameTestFonts.pick(context, "Arial", "Segoe UI", "Helvetica", "DejaVu Sans");
		if (face.isEmpty()) {
			McFontControl.LOGGER.warn("[gametest] No system fonts found on this machine; skipping the apply checks.");
			return;
		}

		GameTestFonts.apply(context, GameTestFonts.settingsFor(face.get()));
		if (!PackContents.providerLoaded()) {
			throw new AssertionError("system font was not loaded");
		}
		context.takeScreenshot("m3-system-font-title");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.setScreen(() -> new ChatScreen("The quick brown fox jumps over the lazy dog 0123456789", false));
			context.waitTicks(10);
			context.takeScreenshot("m3-system-font-world-chat");
			context.setScreen(() -> null);
		}
		context.waitForScreen(TitleScreen.class);

		// A font file that has gone away: vanilla text and one warning, no crash (AP-7).
		FontSettings missing = GameTestFonts.settingsFor(face.get()).withFont(face.get().path() + ".missing", 0, "No Such Family", "Regular");
		GameTestFonts.apply(context, missing);
		if (PackContents.loaded().active()) {
			throw new AssertionError("missing font should fall back to vanilla");
		}

		GameTestFonts.apply(context, FontSettings.DEFAULTS);
		context.takeScreenshot("m3-vanilla-after-reset");
	}
}
