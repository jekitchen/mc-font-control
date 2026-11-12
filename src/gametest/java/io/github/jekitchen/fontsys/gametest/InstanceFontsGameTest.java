package io.github.jekitchen.fontsys.gametest;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.InstanceFonts;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Fonts in the instance's {@code fonts/} folder (PRD FD-14, D33): an installed font is copied
 * into the test instance's folder (locally, never committed), found by a rescan, listed as its
 * own "Instance" family next to the system one, applied, and saved as a relative path.
 */
@SuppressWarnings("UnstableApiUsage")
public class InstanceFontsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> pick = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (pick.isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No system fonts found; skipping the instance fonts test.");
			return;
		}
		FaceInfo system = pick.get();
		Path dir = InstanceFonts.dir().orElseThrow(() -> new AssertionError("no instance fonts folder"));
		Path copy = dir.resolve(Path.of(system.path()).getFileName());
		try {
			Files.createDirectories(dir);
			Files.copy(Path.of(system.path()), copy, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		try {
			rescan(context);
			List<FontFamily> same = Fontsys.fontIndex().families(false).stream()
				.filter(f -> f.name().equalsIgnoreCase(system.family())).toList();
			require(same.size() == 2, "expected the system and the instance " + system.family() + ", got " + same);
			require(!same.get(0).instance() && same.get(1).instance(), "system family first, then the instance one");
			FaceInfo instanceFace = same.get(1).styles().stream()
				.filter(f -> f.faceIndex() == system.faceIndex()).findFirst().orElseThrow();
			require(InstanceFonts.contains(instanceFace.path()), "the instance face's file is in fonts/");

			GameTestFonts.apply(context, GameTestFonts.settingsFor(instanceFace));
			require(PackContents.providerLoaded(), "the instance font is applied");
			require(Fontsys.controller().appliedSettings().fontPath().equals(instanceFace.path()), "applied from the instance folder");
			String json = Files.readString(Fontsys.platform().configDir().resolve(Fontsys.MOD_ID + ".json"));
			require(json.contains("\"fontPath\": \"fonts/" + copy.getFileName() + "\""), "saved as a relative path: " + json);
			context.takeScreenshot("fd14-instance-font-title");

			// Reopened, the screen highlights the instance family, not the system one of the same name.
			context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
			context.waitTicks(10);
			context.getInput().typeChars(system.family());
			context.waitTicks(10);
			boolean instanceSelected = context.computeOnClient(mc -> {
				FontFamily selected = ((FontConfigScreen) mc.gui.screen()).listSelection();
				return selected != null && selected.instance();
			});
			require(instanceSelected, "the applied instance family is the selected list entry");
			context.takeScreenshot("fd14-instance-font-list");
			context.setScreen(TitleScreen::new);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
			try {
				Files.deleteIfExists(copy);
			} catch (IOException e) {
				Fontsys.LOGGER.warn("[gametest] Could not delete {}: {}", copy, e.toString());
			}
			rescan(context);
		}
	}

	private static void rescan(ClientGameTestContext context) {
		CompletableFuture<?> scan = context.computeOnClient(mc -> Fontsys.fontIndex().scan());
		context.waitFor(mc -> scan.isDone(), 20 * 60);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
