package io.github.jekitchen.fontsys.neoforge.smoketest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.SystemFontIndex;
import io.github.jekitchen.fontsys.gametest.InWorldScene;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.pack.RuntimeFontPackSource;
import io.github.jekitchen.fontsys.platform.ClientHooks;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge smoke test (PRD D51). Fabric's client game-test API has no NeoForge counterpart, so
 * this test-only mod drives the real client itself: once the title screen is up it checks
 * every NeoForge-specific part of Fontsys, applies an installed font, checks it is drawn,
 * resets to vanilla, renders the Fabric game tests' in-world scene in both fonts (D57) and
 * quits. The result goes to {@code fontsys-smoketest.txt} in the run
 * folder, which {@code ./gradlew :neoforge:neoforgeSmokeTest} checks. It does nothing unless
 * the run sets {@code -Dfontsys.smokeTest=true}.
 */
@Mod(value = "fontsys_smoketest", dist = Dist.CLIENT)
public final class FontsysSmokeTest {
	private static final Logger LOG = LoggerFactory.getLogger("Fontsys smoke test");
	/** Narrow and wide letters: a proportional font won't match vanilla's widths for both. */
	private static final List<String> SAMPLES = List.of("iiiiiiiiiillllllllll", "WWWWWWWWWWMMMMMMMMMM");
	/** A client that never reaches the title screen must not hang CI. */
	private static final long TIMEOUT_MINUTES = 10;

	/** Chunks that never report rendered (another renderer, a slow CI machine) must not hang the test. */
	private static final int CHUNK_TIMEOUT_TICKS = 20 * 60;

	private enum Step {
		TITLE, F7_OPENED, FONT_OPTIONS, SCAN, APPLYING, RETURNED, RESETTING,
		WORLD_LOADING, WORLD_VANILLA, WORLD_APPLYING, WORLD_RESETTING, WORLD_LEAVING, DONE
	}

	private final List<String> lines = new ArrayList<>();
	private Step step = Step.TITLE;
	private int ticksInStep;
	private int settledTicks;
	private List<Integer> vanillaWidths;
	private CompletableFuture<Void> pending;
	private FontOptionsScreen fontOptions;
	private FaceInfo face;
	private volatile boolean finished;

	public FontsysSmokeTest() {
		if (!Boolean.getBoolean("fontsys.smokeTest")) {
			return;
		}
		Thread watchdog = new Thread(() -> {
			try {
				TimeUnit.MINUTES.sleep(TIMEOUT_MINUTES);
			} catch (InterruptedException e) {
				return;
			}
			if (!finished) {
				lines.add("FAIL timed out after " + TIMEOUT_MINUTES + " minutes in step " + step);
				writeResult(false);
				Runtime.getRuntime().halt(1);
			}
		}, "Fontsys smoke test watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		LOG.info("Smoke test enabled; it quits the game when done.");
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
			if (finished) {
				return;
			}
			try {
				tick(Minecraft.getInstance());
			} catch (RuntimeException | AssertionError e) {
				LOG.error("Smoke test failed in step {}", step, e);
				fail(step + ": " + e);
			}
		});
	}

	private void tick(Minecraft mc) {
		ticksInStep++;
		switch (step) {
			case TITLE -> {
				if (mc.gui.overlay() != null) {
					return;
				}
				// A fresh game folder starts on vanilla's accessibility onboarding; skip it as a player would.
				if (mc.gui.screen() instanceof AccessibilityOnboardingScreen) {
					mc.options.onboardingAccessibilityFinished();
					mc.gui.setScreen(new TitleScreen());
					return;
				}
				// NeoForge stops on a screen when mods load with warnings (Iris 1.11.7's deprecated
				// logoFile property, for one) until the player clicks "Proceed to main menu". List the
				// warnings and proceed, as a player would; any loading error fails the run (D57).
				if (mc.gui.screen() instanceof LoadingErrorScreen screen) {
					List<ModLoadingIssue> errors = ModLoader.getLoadingErrors();
					check(errors.isEmpty(), "mods load without errors", errors.stream().map(FMLTranslations::translateIssueEnglish).toList().toString());
					ModLoader.getLoadingWarnings().forEach(w -> lines.add("INFO NeoForge loading warning: " + FMLTranslations.translateIssueEnglish(w)));
					check(press(screen, "fml.button.continue.launch"), "the loading-warnings screen can be passed", "no Proceed button");
					lines.add("INFO proceeded past NeoForge's loading-warnings screen");
					return;
				}
				if (!(mc.gui.screen() instanceof TitleScreen)) {
					within(20 * 60, "the title screen shows", "stuck on " + mc.gui.screen());
					return;
				}
				if (ticksInStep < 20) {
					return;
				}
				check(!PackContents.loaded().active(), "the game starts in the vanilla font", "a font is already applied");
				checkStatic(mc);
				vanillaWidths = widths(mc);
				// F7 as the player would press it: the key mapping's click, consumed on the next tick.
				KeyMapping.click(InputConstants.getKey("key.keyboard.f7"));
				next(Step.F7_OPENED);
			}
			case F7_OPENED -> {
				if (ticksInStep < 5) {
					return;
				}
				check(mc.gui.screen() instanceof FontConfigScreen, "F7 opens the Fontsys settings", "screen is " + mc.gui.screen());
				fontOptions = new FontOptionsScreen(new TitleScreen(), mc.options);
				mc.gui.setScreen(fontOptions);
				next(Step.FONT_OPTIONS);
			}
			case FONT_OPTIONS -> {
				if (ticksInStep < 5) {
					return;
				}
				check(fontsysButtons(fontOptions) == 1, "Font Settings has one Fontsys Settings button", "found " + fontsysButtons(fontOptions));
				mc.gui.setScreen(new FontConfigScreen(fontOptions));
				next(Step.SCAN);
			}
			case SCAN -> {
				SystemFontIndex.State state = Fontsys.fontIndex().state();
				if (state != SystemFontIndex.State.READY && state != SystemFontIndex.State.FAILED) {
					return;
				}
				Optional<FaceInfo> face = pick("Arial", "Segoe UI", "Helvetica", "DejaVu Sans", "Liberation Sans");
				check(face.isPresent(), "the font scan finds installed fonts", "scan state " + state + ", no families");
				this.face = face.orElseThrow();
				lines.add("INFO applying " + this.face.family() + " " + this.face.style());
				// Larger than vanilla, so labels laid out before the change would be cut off (D42).
				pending = Fontsys.controller().apply(settings(14));
				next(Step.APPLYING);
			}
			case APPLYING -> {
				if (!pending.isDone() || mc.gui.overlay() != null || ticksInStep < 40) {
					return;
				}
				check(!pending.isCompletedExceptionally(), "the resource reload finishes", "it failed");
				check(PackContents.loaded().active(), "the runtime pack serves the system font", "pack is inactive");
				check(PackContents.providerLoaded(), "vanilla's ttf provider loads the system font (mixins applied)", "provider not loaded");
				check(!widths(mc).equals(vanillaWidths), "text is measured in the new font", "widths " + widths(mc) + " equal vanilla's");
				screenshot(mc, "neoforge-settings-system-font.png");
				// Back to the screen underneath, laid out in the old font (UI-18, D42).
				mc.gui.setScreen(fontOptions);
				next(Step.RETURNED);
			}
			case RETURNED -> {
				if (ticksInStep < 5) {
					return;
				}
				check(fontsysButtons(fontOptions) == 1, "returning to Font Settings keeps one Fontsys button", "found " + fontsysButtons(fontOptions));
				StringWidget title = findLabel(fontOptions, fontOptions.getTitle());
				check(title != null && title.getWidth() >= mc.font.width(title.getMessage()),
					"the Font Settings title is refit to the new font", title == null ? "no title label" : "width " + title.getWidth() + " < " + mc.font.width(title.getMessage()));
				screenshot(mc, "neoforge-font-settings-system-font.png");
				mc.gui.setScreen(new TitleScreen());
				pending = Fontsys.controller().apply(FontSettings.DEFAULTS);
				next(Step.RESETTING);
			}
			case RESETTING -> {
				if (!pending.isDone() || mc.gui.overlay() != null || ticksInStep < 40) {
					return;
				}
				check(!PackContents.loaded().active(), "reset returns to the vanilla font", "pack still active");
				check(widths(mc).equals(vanillaWidths), "text is measured in the vanilla font again", "widths " + widths(mc) + " != " + vanillaWidths);
				screenshot(mc, "neoforge-title-vanilla.png");
				// The shared in-world scene in a new creative world on the Superflat preset, as Fabric's
				// game-test world builder makes it (D57).
				mc.createWorldOpenFlows().createFreshLevel("fontsys-smoke-test",
					new LevelSettings("fontsys-smoke-test", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
					new WorldOptions(1L, false, false),
					registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
					new TitleScreen());
				next(Step.WORLD_LOADING);
			}
			case WORLD_LOADING -> {
				IntegratedServer server = mc.getSingleplayerServer();
				if (mc.level == null || mc.player == null || server == null || mc.gui.screen() != null) {
					within(20 * 120, "a new world loads", "still loading, screen " + mc.gui.screen());
					return;
				}
				lines.add("PASS a new world loads");
				// Without command feedback, which would cover the scene in chat.
				server.execute(() -> InWorldScene.COMMANDS.forEach(command ->
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command)));
				next(Step.WORLD_VANILLA);
			}
			case WORLD_VANILLA -> {
				if (!chunksRendered(mc)) {
					return;
				}
				check(mc.level.getBlockState(new BlockPos(-1, -60, 3)).getBlock() == Blocks.OAK_SIGN,
					"the in-world scene is built (signs, text display, name tags)", "no sign at (-1, -60, 3)");
				screenshot(mc, "neoforge-world-vanilla.png");
				// Vanilla's layout size, as in the Fabric in-world test, so the two loaders' pictures compare.
				pending = Fontsys.controller().apply(settings(FontSettings.DEFAULTS.size()));
				next(Step.WORLD_APPLYING);
			}
			case WORLD_APPLYING -> {
				if (!pending.isDone() || mc.gui.overlay() != null || !chunksRendered(mc)) {
					return;
				}
				check(!pending.isCompletedExceptionally() && PackContents.loaded().active(), "a font applies in a world", "reload failed or pack inactive");
				check(!widths(mc).equals(vanillaWidths), "text in a world is measured in the new font", "widths " + widths(mc) + " equal vanilla's");
				screenshot(mc, "neoforge-world-system-font.png");
				pending = Fontsys.controller().apply(FontSettings.DEFAULTS);
				next(Step.WORLD_RESETTING);
			}
			case WORLD_RESETTING -> {
				if (!pending.isDone() || mc.gui.overlay() != null || ticksInStep < 40) {
					return;
				}
				check(!PackContents.loaded().active() && widths(mc).equals(vanillaWidths), "reset in a world returns to the vanilla font", "widths " + widths(mc));
				mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
				next(Step.WORLD_LEAVING);
			}
			case WORLD_LEAVING -> {
				if (mc.level != null || !(mc.gui.screen() instanceof TitleScreen)) {
					within(20 * 60, "the world saves and closes", "still in the world, screen " + mc.gui.screen());
					return;
				}
				lines.add("PASS the world saves and closes");
				next(Step.DONE);
			}
			case DONE -> {
				if (ticksInStep < 20) {
					return;
				}
				finish(mc, true);
			}
		}
	}

	/** The picked installed font at the given size, everything else at Fontsys's defaults. */
	private FontSettings settings(float size) {
		return FontSettings.DEFAULTS.withEnabled(true).withFont(face.path(), face.faceIndex(), face.family(), face.style())
			.withLayout(size, FontSettings.AUTO_OVERSAMPLE, 0, 0);
	}

	/**
	 * True once the chunks around the player are loaded, nothing waits for light and every
	 * section is drawn (as Fabric's {@code waitForChunksRender} checks), and that has held for
	 * two seconds. Waits at most
	 * {@link #CHUNK_TIMEOUT_TICKS}, then goes on with a note: a renderer that never reports
	 * finished still gets its screenshot.
	 */
	private boolean chunksRendered(Minecraft mc) {
		if (ticksInStep >= CHUNK_TIMEOUT_TICKS) {
			lines.add("INFO chunks not reported drawn after " + CHUNK_TIMEOUT_TICKS / 20 + " s in step " + step + "; continuing ("
				+ "loaded " + chunksLoaded(mc) + ", light work " + mc.level.getChunkSource().getLightEngine().hasLightWork()
				+ ", sections drawn " + mc.levelRenderer.hasRenderedAllSections() + ", player at " + mc.player.position()
				+ ", eye block " + mc.level.getBlockState(BlockPos.containing(mc.player.getEyePosition())) + ")");
			return true;
		}
		if (!chunksLoaded(mc) || mc.level.getChunkSource().getLightEngine().hasLightWork() || !mc.levelRenderer.hasRenderedAllSections()) {
			settledTicks = 0;
			return false;
		}
		return ++settledTicks >= 40;
	}

	/** The chunks around the player, which hold the whole scene. */
	private static boolean chunksLoaded(Minecraft mc) {
		int radius = Math.min(2, mc.options.getEffectiveRenderDistance());
		ChunkPos center = mc.player.chunkPosition();
		for (int x = center.x() - radius; x <= center.x() + radius; x++) {
			for (int z = center.z() - radius; z <= center.z() + radius; z++) {
				if (mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) == null) {
					return false;
				}
			}
		}
		return true;
	}

	/** Checks that need no font change: the NeoForge wiring of the pack, key, mods list and command. */
	private void checkStatic(Minecraft mc) {
		// Other mods the run was asked to include (-PwithSodiumIris, D57), so a missing jar can't pass.
		for (String id : System.getProperty("fontsys.smokeTest.expectMods", "").split(",")) {
			if (id.isBlank()) {
				continue;
			}
			check(ModList.get().isLoaded(id), id + " is loaded alongside Fontsys", "not in the mods list");
			lines.add("INFO " + id + " " + ModList.get().getModContainerById(id).orElseThrow().getModInfo().getVersion());
		}

		// The run loads Fontsys from the single jar players get, Fabric metadata and all (D54).
		if (Boolean.getBoolean("fontsys.smokeTest.expectJar")) {
			IModFile file = ModList.get().getModFileById(Fontsys.MOD_ID).getFile();
			Path path = file.getFilePath();
			lines.add("INFO Fontsys loaded from " + path.getFileName());
			check(Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"), "Fontsys loads from the built jar", "loaded from " + path);
			check(file.getContents().containsFile("fabric.mod.json"), "the jar is the single jar for both loaders (it carries fabric.mod.json)", "no fabric.mod.json in " + path);
		}

		List<String> selected = mc.getResourcePackRepository().getSelectedPacks().stream().map(Pack::getId).toList();
		check(selected.contains(RuntimeFontPackSource.PACK_ID), "the runtime font pack is selected", "selected: " + selected);

		KeyMapping key = null;
		for (KeyMapping mapping : mc.options.keyMappings) {
			if (mapping.getName().equals("key.fontsys.open")) {
				key = mapping;
			}
		}
		check(key != null, "the Fontsys key mapping is registered", "not in Options > Controls");
		check(key.getDefaultKey().getValue() == InputConstants.KEY_F7, "the key defaults to F7", "default is " + key.getDefaultKey());
		check(key.getCategory().id().equals(ClientHooks.KEY_CATEGORY), "the key is in the Fontsys category", "category " + key.getCategory().id());

		Optional<IConfigScreenFactory> factory = ModList.get().getModContainerById(Fontsys.MOD_ID).flatMap(c -> c.getCustomExtension(IConfigScreenFactory.class));
		check(factory.isPresent(), "the mods list has a Config button for Fontsys", "no IConfigScreenFactory");
		Screen config = factory.get().createScreen(ModList.get().getModContainerById(Fontsys.MOD_ID).orElseThrow(), mc.gui.screen());
		check(config instanceof FontConfigScreen, "the mods list's Config button opens the Fontsys settings", "opens " + config);

		// NeoForge registers client commands on joining a world; register them into a fresh tree here.
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		NeoForge.EVENT_BUS.post(new RegisterClientCommandsEvent(dispatcher, Commands.createValidationContext(VanillaRegistries.createWorldLookup())));
		CommandNode<CommandSourceStack> command = dispatcher.getRoot().getChild(Fontsys.MOD_ID);
		check(command != null && command.getChild("fonts") != null && command.getChild("rescan") != null,
			"/fontsys fonts and /fontsys rescan are registered as client commands", "command tree: " + dispatcher.getRoot().getChildren());
	}

	private static List<Integer> widths(Minecraft mc) {
		return SAMPLES.stream().map(mc.font::width).toList();
	}

	private static Optional<FaceInfo> pick(String... preferred) {
		List<FontFamily> families = Fontsys.fontIndex().families(false);
		for (String name : preferred) {
			for (FontFamily family : families) {
				if (family.name().equalsIgnoreCase(name)) {
					return Optional.of(family.defaultStyle());
				}
			}
		}
		return families.isEmpty() ? Optional.empty() : Optional.of(families.getFirst().defaultStyle());
	}

	private static int fontsysButtons(Screen screen) {
		String label = Component.translatable("fontsys.options.button").getString();
		int count = 0;
		for (GuiEventListener child : screen.children()) {
			if (child instanceof OptionsList list) {
				for (GuiEventListener entry : list.children()) {
					if (entry instanceof ContainerEventHandler row) {
						for (GuiEventListener widget : row.children()) {
							if (widget instanceof AbstractWidget w && w.getMessage().getString().equals(label)) {
								count++;
							}
						}
					}
				}
			}
		}
		return count;
	}

	/** Presses the screen's button with that label, if it has one. */
	private static boolean press(Screen screen, String translationKey) {
		String label = Component.translatable(translationKey).getString();
		for (GuiEventListener child : screen.children()) {
			if (child instanceof Button button && button.getMessage().getString().equals(label)) {
				button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
				return true;
			}
		}
		return false;
	}

	private static StringWidget findLabel(ContainerEventHandler parent, Component text) {
		for (GuiEventListener child : parent.children()) {
			if (child instanceof StringWidget label && label.getMessage().getString().equals(text.getString())) {
				return label;
			}
			if (child instanceof ContainerEventHandler container) {
				StringWidget found = findLabel(container, text);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private void screenshot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> { });
	}

	private void check(boolean ok, String what, String otherwise) {
		if (!ok) {
			throw new AssertionError(what + ": " + otherwise);
		}
		lines.add("PASS " + what);
		LOG.info("PASS {}", what);
	}

	/** While waiting: fails once the step has taken longer than {@code ticks}, and logs nothing otherwise. */
	private void within(int ticks, String what, String otherwise) {
		if (ticksInStep >= ticks) {
			throw new AssertionError(what + ": " + otherwise);
		}
	}

	private void next(Step to) {
		step = to;
		ticksInStep = 0;
		settledTicks = 0;
	}

	private void fail(String reason) {
		lines.add("FAIL " + reason);
		finish(Minecraft.getInstance(), false);
	}

	private void finish(Minecraft mc, boolean passed) {
		finished = true;
		writeResult(passed);
		LOG.info("Smoke test {}; quitting.", passed ? "passed" : "FAILED");
		mc.stop();
	}

	private synchronized void writeResult(boolean passed) {
		List<String> out = new ArrayList<>(lines);
		out.add("RESULT " + (passed ? "PASS" : "FAIL"));
		try {
			Files.write(Path.of("fontsys-smoketest.txt").toAbsolutePath(), out, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.error("Could not write the smoke test result", e);
		}
		LOG.info("Smoke test result:{}{}", System.lineSeparator(), String.join(System.lineSeparator(), out));
	}
}
