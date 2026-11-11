package io.github.jekitchen.mcfontcontrol.gametest;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.discovery.FaceInfo;
import io.github.jekitchen.mcfontcontrol.pack.PackContents;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * M4: a face inside a collection, variable-font named instances and a CFF {@code .otf} each
 * load through vanilla's provider (D1). Each case is skipped if no such font is installed.
 */
@SuppressWarnings("UnstableApiUsage")
public class FaceSelectionGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		GameTestFonts.pick(context);
		List<FaceInfo> faces = McFontControl.fontIndex().families(false).stream().flatMap(f -> f.styles().stream()).toList();

		check(context, "collection-face", faces, f -> (f.faceIndex() & 0xFFFF) > 0 && f.weight() >= 400 && f.path().toLowerCase().matches(".*\\.(ttc|otc)$"),
			"MS PGothic", "Avenir Next", "Helvetica Neue");
		check(context, "variable-light", faces, f -> f.isNamedInstance() && f.weight() <= 300, "Inter", "Segoe UI Variable", "SF Pro", "System Font");
		check(context, "variable-bold", faces, f -> f.isNamedInstance() && f.weight() >= 700, "Inter", "Segoe UI Variable", "SF Pro", "System Font");
		check(context, "otf-cff", faces, f -> "CFF".equals(f.format()), "Source Han Sans JP", "Source Sans Pro");

		GameTestFonts.apply(context, FontSettings.DEFAULTS);
	}

	private static void check(ClientGameTestContext context, String name, List<FaceInfo> faces, Predicate<FaceInfo> kind, String... preferredFamilies) {
		Optional<FaceInfo> face = Optional.empty();
		for (String family : preferredFamilies) {
			face = faces.stream().filter(kind).filter(f -> f.family().equalsIgnoreCase(family) && !f.italic()).findFirst();
			if (face.isPresent()) {
				break;
			}
		}
		if (face.isEmpty()) {
			face = faces.stream().filter(kind).filter(f -> !f.italic()).findFirst();
		}
		if (face.isEmpty()) {
			McFontControl.LOGGER.warn("[gametest] No {} font installed; skipping.", name);
			return;
		}
		FaceInfo f = face.get();
		GameTestFonts.apply(context, GameTestFonts.settingsFor(f));
		if (!PackContents.providerLoaded() || PackContents.loaded().faceIndex() != f.faceIndex()) {
			throw new AssertionError(name + ": " + f.family() + " " + f.style() + " did not load");
		}
		McFontControl.LOGGER.info("[gametest] {}: {} {} (face index 0x{})", name, f.family(), f.style(), Integer.toHexString(f.faceIndex()));
		context.takeScreenshot("m4-" + name);
	}
}
