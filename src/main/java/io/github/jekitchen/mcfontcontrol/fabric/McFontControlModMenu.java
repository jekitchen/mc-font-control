package io.github.jekitchen.mcfontcontrol.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.jekitchen.mcfontcontrol.ui.FontConfigScreen;

/** Opens the config screen from Mod Menu (PRD UI-1). Loaded only when Mod Menu is installed. */
public final class McFontControlModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return FontConfigScreen::new;
	}
}
