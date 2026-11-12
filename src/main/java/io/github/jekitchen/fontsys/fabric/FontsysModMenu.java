package io.github.jekitchen.fontsys.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;

/** Opens the config screen from Mod Menu (PRD UI-1). Loaded only when Mod Menu is installed. */
public final class FontsysModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return FontConfigScreen::new;
	}
}
