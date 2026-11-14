package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.config.Hinting;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.ui.AdvancedFontScreen;
import io.github.jekitchen.fontsys.ui.FamilyNameFonts;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Reload stress (PRD D56): everything that opens, closes or reloads FreeType faces and glyph
 * pages, many times over, so a native memory or threading fault shows up as a crash here
 * rather than in a player's game. Each round, on the title screen:
 *
 * <ul>
 *   <li>Apply a different installed font, alternating crisp edges and hinting;</li>
 *   <li>a plain resource reload (F3+T) and a GUI-scale change and back;</li>
 *   <li>the settings screen: a fast scroll through the list with the font names drawn in
 *       their own fonts (D49), another family selected so the preview loads, the Advanced
 *       screen, and back;</li>
 *   <li>Reset to vanilla.</li>
 * </ul>
 *
 * <p>Then, in a world with signs, a text display and name tags, close up and far away (the
 * mipmapped glyph pages, D50): Apply, F3+T, a GUI-scale change, Reset and Apply again. Run
 * against the built jar by {@code runProductionClientGameTest}, it is the regression check for
 * the Windows production-launch crash (D54, D56).
 */
@SuppressWarnings("UnstableApiUsage")
public class ReloadStressGameTest implements FabricClientGameTest {
	private static final int ROUNDS = 6;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		// Every launcher passes Mojang's -XX:StackShadowPages=32; on Windows the client crashes
		// without it, Fontsys or not (D56). A test launch without it tests nothing a player runs.
		require(ManagementFactory.getRuntimeMXBean().getInputArguments().stream().anyMatch(a -> a.startsWith("-XX:StackShadowPages=")),
			"the game was launched without Mojang's -XX:StackShadowPages JVM argument");
		if (GameTestFonts.pick(context).isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No system fonts found; skipping the reload stress test.");
			return;
		}
		List<FaceInfo> faces = new ArrayList<>();
		GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans").ifPresent(faces::add);
		faces.addAll(faces(ROUNDS - 1));
		int reloads = 0;
		try {
			for (int round = 0; round < ROUNDS; round++) {
				FaceInfo face = faces.get(round % faces.size());
				FontSettings settings = GameTestFonts.settingsFor(face)
					.withCrispEdges(round % 2 == 1)
					.withHinting(Hinting.values()[round % Hinting.values().length]);
				GameTestFonts.apply(context, settings);
				// Any installed font may be one Fontsys can't use; that falls back to vanilla (and is fine here).
				boolean applied = PackContents.providerLoaded();
				reload(context);
				guiScaleRoundTrip(context, 1 + round % 3);
				require(!applied || PackContents.providerLoaded(), "round " + round + ": " + face.family() + " kept after reloads");
				settingsScreen(context, faces.get((round + 1) % faces.size()).family());
				GameTestFonts.apply(context, FontSettings.DEFAULTS);
				reloads += 5;
			}
			context.waitFor(mc -> FamilyNameFonts.openFaces() == 0 && FamilyNameFonts.openAtlases() == 0, 20 * 10);
			reloads += inWorld(context, faces.getFirst());
		} finally {
			FamilyNameFonts.enabled = true;
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
			context.setScreen(TitleScreen::new);
		}
		Fontsys.LOGGER.info("[gametest] reload stress: {} rounds, {} fonts, about {} reloads, no crash", ROUNDS, faces.size(), reloads);
	}

	/** In a world: text close up and far away, through Apply, F3+T, a GUI-scale change and Reset. */
	private static int inWorld(ClientGameTestContext context, FaceInfo face) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			InWorldTextGameTest.buildScene(world);
			world.getConnection().waitForChunksRender();
			for (int i = 0; i < 2; i++) {
				GameTestFonts.apply(context, GameTestFonts.settingsFor(face).withCrispEdges(i == 1));
				require(PackContents.providerLoaded(), "in world: " + face.family() + " applied");
				world.getConnection().waitForChunksRender();
				// Far away: the signs' text samples the smaller mipmap levels.
				world.getServer().runCommand("tp @a 0.5 -60 -24 0 2");
				context.waitTicks(20);
				reload(context);
				world.getConnection().waitForChunksRender();
				world.getServer().runCommand("tp @a 0.5 -60 -1.5 0 5");
				context.waitTicks(20);
				guiScaleRoundTrip(context, 2 + i);
				GameTestFonts.apply(context, FontSettings.DEFAULTS);
				world.getConnection().waitForChunksRender();
				context.waitTicks(10);
			}
		}
		context.waitForScreen(TitleScreen.class);
		return 8;
	}

	/** The settings screen with font names, a fast scroll, the preview of another family and the Advanced screen. */
	private static void settingsScreen(ClientGameTestContext context, String otherFamily) {
		FamilyNameFonts.enabled = true;
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(5);
		int max = context.computeOnClient(mc -> list(mc).maxScrollAmount());
		for (int y = 0; y <= max; y += Math.max(1, max / 12)) {
			int at = y;
			context.runOnClient(mc -> list(mc).setScrollAmount(at));
			context.waitTick();
		}
		context.runOnClient(mc -> list(mc).setScrollAmount(0));
		context.runOnClient(mc -> {
			FontConfigScreen screen = (FontConfigScreen) mc.gui.screen();
			for (FontFamily family : Fontsys.fontIndex().families(false)) {
				if (family.name().equals(otherFamily)) {
					screen.selectFamily(family);
				}
			}
		});
		context.waitTicks(10);
		// "Advanced..." or, with hinting changed from the default, "Advanced (1)...": focus it and press Enter.
		context.runOnClient(mc -> {
			FontConfigScreen screen = (FontConfigScreen) mc.gui.screen();
			screen.setFocused(screen.children().stream()
				.filter(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals(screen.advancedLabel().getString()))
				.findFirst().orElseThrow(() -> new AssertionError("no Advanced button")));
		});
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(AdvancedFontScreen.class);
		context.waitTicks(5);
		context.setScreen(TitleScreen::new);
		context.waitTicks(2);
	}

	/** F3+T: a plain resource reload. */
	private static void reload(ClientGameTestContext context) {
		CompletableFuture<Void> reload = context.computeOnClient(mc -> mc.reloadResourcePacks());
		context.waitFor(mc -> reload.isDone() && mc.gui.overlay() == null, 20 * 60);
	}

	/** A GUI-scale change, which reloads the font at the new scale (automatic oversample), and back to Auto. */
	private static void guiScaleRoundTrip(ClientGameTestContext context, int scale) {
		for (int value : new int[] {scale, 0}) {
			context.runOnClient(mc -> {
				mc.options.guiScale().set(value);
				mc.resizeGui();
			});
			context.waitTicks(2);
			context.waitFor(mc -> mc.gui.overlay() == null, 20 * 30);
			context.waitTicks(5);
		}
	}

	/** Up to {@code n} installed fonts, each from a different family. */
	private static List<FaceInfo> faces(int n) {
		List<FontFamily> families = Fontsys.fontIndex().families(false);
		List<FaceInfo> out = new ArrayList<>();
		int step = Math.max(1, families.size() / n);
		for (int i = 0; i < families.size() && out.size() < n; i += step) {
			out.add(families.get(i).defaultStyle());
		}
		return out;
	}

	private static AbstractScrollArea list(net.minecraft.client.Minecraft mc) {
		return mc.gui.screen().children().stream().filter(c -> c instanceof AbstractScrollArea).map(c -> (AbstractScrollArea) c).findFirst()
			.orElseThrow(() -> new AssertionError("no font list"));
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
