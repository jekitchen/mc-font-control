package io.github.jekitchen.fontsys.neoforge.smoketest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.SystemFontIndex;
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
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge smoke test (PRD D51). Fabric's client game-test API has no NeoForge counterpart, so
 * this test-only mod drives the real client itself: once the title screen is up it checks
 * every NeoForge-specific part of Fontsys, applies an installed font, checks it is drawn,
 * resets to vanilla and quits. The result goes to {@code fontsys-smoketest.txt} in the run
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

	private enum Step { TITLE, F7_OPENED, FONT_OPTIONS, SCAN, APPLYING, RETURNED, RESETTING, DONE }

	private final List<String> lines = new ArrayList<>();
	private Step step = Step.TITLE;
	private int ticksInStep;
	private List<Integer> vanillaWidths;
	private CompletableFuture<Void> pending;
	private FontOptionsScreen fontOptions;
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
				if (!(mc.gui.screen() instanceof TitleScreen)) {
					check(ticksInStep < 20 * 60, "the title screen shows", "stuck on " + mc.gui.screen());
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
				FaceInfo f = face.orElseThrow();
				lines.add("INFO applying " + f.family() + " " + f.style());
				// Larger than vanilla, so labels laid out before the change would be cut off (D42).
				pending = Fontsys.controller().apply(FontSettings.DEFAULTS.withEnabled(true).withFont(f.path(), f.faceIndex(), f.family(), f.style())
					.withLayout(14, FontSettings.AUTO_OVERSAMPLE, 0, 0));
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

	/** Checks that need no font change: the NeoForge wiring of the pack, key, mods list and command. */
	private void checkStatic(Minecraft mc) {
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

	private void next(Step to) {
		step = to;
		ticksInStep = 0;
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
