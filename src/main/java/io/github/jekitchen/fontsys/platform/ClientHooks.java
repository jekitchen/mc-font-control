package io.github.jekitchen.fontsys.platform;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The client wiring both loaders share (PRD D8, D51): what happens on each tick and when a
 * screen is built. Each loader's entry point only connects its own events to these methods,
 * so Fabric and NeoForge behave the same.
 */
public final class ClientHooks {
	/** The Fontsys category in Options > Controls > Key Binds. */
	public static final Identifier KEY_CATEGORY = Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, "main");

	/** Option lists that already have the Fontsys button. Weak: lists go when their screen does. */
	private static final Set<OptionsList> WITH_BUTTON = Collections.newSetFromMap(new WeakHashMap<>());

	private static boolean conflictToastShown;

	private ClientHooks() {
	}

	/** Opens the settings from anywhere in game; F7 by default, which vanilla 26.3 leaves free (UI-2, D39). */
	public static KeyMapping createOpenKey(KeyMapping.Category category) {
		return new KeyMapping("key.fontsys.open", InputConstants.KEY_F7, category);
	}

	/** At the end of every client tick. */
	public static void endClientTick(Minecraft mc, KeyMapping openKey) {
		Fontsys.controller().tick(mc);
		while (openKey.consumeClick()) {
			if (!(mc.gui.screen() instanceof FontConfigScreen)) {
				mc.gui.setScreen(new FontConfigScreen(mc.gui.screen()));
			}
		}
		// One notice per session when another font mod is loaded (AP-12).
		if (!conflictToastShown && mc.gui.screen() instanceof TitleScreen) {
			conflictToastShown = true;
			Fontsys.controller().conflict().ifPresent(mod -> SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.translatable("fontsys.toast.conflict.title"),
				Component.translatable("fontsys.toast.conflict.message", mod.name())));
		}
	}

	/**
	 * A Fontsys button in Options > Language > Font Settings, under Force Unicode Font, so the
	 * settings can be found without Mod Menu or the mods list (UI-1). It goes into the screen's
	 * own option list, which lays it out and scrolls it with the vanilla options. Call it after
	 * the screen's widgets are built; it may be called again when the player comes back to the
	 * screen, which keeps its list, so each list gets the button once (D42).
	 */
	public static void addToFontOptions(Screen screen) {
		if (!(screen instanceof FontOptionsScreen)) {
			return;
		}
		for (GuiEventListener child : screen.children()) {
			if (child instanceof OptionsList options) {
				if (!WITH_BUTTON.add(options)) {
					return;
				}
				options.addBig(Button.builder(Component.translatable("fontsys.options.button"), b -> Minecraft.getInstance().gui.setScreen(new FontConfigScreen(screen)))
					.tooltip(Tooltip.create(Component.translatable("fontsys.options.button.tooltip")))
					.width(Button.BIG_WIDTH)
					.build());
				return;
			}
		}
	}
}
