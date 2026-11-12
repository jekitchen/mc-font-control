package io.github.jekitchen.mcfontcontrol.gametest;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.discovery.FaceInfo;
import io.github.jekitchen.mcfontcontrol.discovery.FontFamily;
import io.github.jekitchen.mcfontcontrol.discovery.SystemFontIndex;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Helpers shared by the game tests. */
final class GameTestFonts {
	private GameTestFonts() {
	}

	/** Waits for the background scan, then picks the first installed family from {@code preferred}. */
	static Optional<FaceInfo> pick(ClientGameTestContext context, String... preferred) {
		context.waitFor(mc -> McFontControl.fontIndex().state() == SystemFontIndex.State.READY
			|| McFontControl.fontIndex().state() == SystemFontIndex.State.FAILED, 20 * 60);
		List<FontFamily> families = McFontControl.fontIndex().families(false);
		for (String name : preferred) {
			for (FontFamily family : families) {
				if (family.name().equalsIgnoreCase(name)) {
					return Optional.of(family.defaultStyle());
				}
			}
		}
		return families.isEmpty() ? Optional.empty() : Optional.of(families.getFirst().defaultStyle());
	}

	static FontSettings settingsFor(FaceInfo face) {
		return FontSettings.DEFAULTS.withEnabled(true).withFont(face.path(), face.faceIndex(), face.family(), face.style());
	}

	/** Applies settings through the controller and waits for the resource reload and fade-out. */
	static void apply(ClientGameTestContext context, FontSettings settings) {
		CompletableFuture<Void> done = context.computeOnClient(mc -> McFontControl.controller().apply(settings));
		context.waitFor(mc -> done.isDone(), 20 * 60);
		context.waitFor(mc -> mc.gui.overlay() == null, 20 * 30);
		context.waitTicks(40);
	}
}
