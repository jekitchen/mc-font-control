package io.github.jekitchen.fontsys.neoforge;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.platform.ClientHooks;
import io.github.jekitchen.fontsys.platform.FontsysCommand;
import io.github.jekitchen.fontsys.ui.FontChangeRefresh;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client entry point (PRD D51). Like the Fabric one, it only connects the loader's
 * events to the shared code in {@link ClientHooks}; {@code dist = CLIENT} keeps it off servers.
 *
 * <p>The runtime font pack and the glyph changes come from the shared mixins in
 * {@code fontsys.mixins.json}, which NeoForge loads from {@code neoforge.mods.toml}.
 */
@Mod(value = Fontsys.MOD_ID, dist = Dist.CLIENT)
public final class FontsysNeoForge {
	private static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(ClientHooks.KEY_CATEGORY);
	private static final KeyMapping OPEN_KEY = ClientHooks.createOpenKey(KEY_CATEGORY);

	public FontsysNeoForge(IEventBus modBus, ModContainer container) {
		Fontsys.init(new NeoForgePlatform());

		// The Config button for Fontsys in NeoForge's mods list (UI-1), as Mod Menu's is on Fabric.
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, modListScreen) -> new FontConfigScreen(modListScreen));

		modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
			event.registerCategory(KEY_CATEGORY);
			event.register(OPEN_KEY);
		});

		IEventBus game = NeoForge.EVENT_BUS;
		game.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher()
			.register(FontsysCommand.<CommandSourceStack>build((source, line) -> source.sendSuccess(() -> line, false))));
		game.addListener(ClientTickEvent.Post.class, event -> ClientHooks.endClientTick(Minecraft.getInstance(), OPEN_KEY));

		// Screens (UI-18, D42). NeoForge's Init events fire only when a screen builds its widgets,
		// not when the player comes back to it (vanilla then only repositions them), so labels are
		// refit when a screen is opened, after any other mod has had its say (LOWEST, not cancelled).
		game.addListener(EventPriority.LOWEST, ScreenEvent.Opening.class, event -> {
			if (event.getNewScreen() != null) {
				FontChangeRefresh.beforeInit(event.getNewScreen());
			}
		});
		game.addListener(ScreenEvent.Init.Pre.class, event -> FontChangeRefresh.beforeInit(event.getScreen()));
		game.addListener(ScreenEvent.Init.Post.class, event -> {
			FontChangeRefresh.afterInit(event.getScreen());
			ClientHooks.addToFontOptions(event.getScreen());
		});
	}
}
