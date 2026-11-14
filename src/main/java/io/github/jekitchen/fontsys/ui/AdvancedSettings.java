package io.github.jekitchen.fontsys.ui;

import io.github.jekitchen.fontsys.config.FontSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * The fine-tuning settings on the Advanced screen (PRD UI-19, D52): which of them differ from
 * the defaults, and putting them back. No game dependencies, so unit tests cover it.
 *
 * <p>Adding a setting to the Advanced screen means a line in each method here, next to the
 * control in {@link AdvancedFontScreen#init()}.
 */
public final class AdvancedSettings {
	private AdvancedSettings() {
	}

	/** Lang keys naming each advanced setting that isn't at its default, in screen order. */
	public static List<String> changedLabelKeys(FontSettings settings) {
		FontSettings defaults = FontSettings.DEFAULTS;
		List<String> keys = new ArrayList<>();
		if (settings.shiftX() != defaults.shiftX()) {
			keys.add("fontsys.confirm.change.shift_x");
		}
		if (settings.shiftY() != defaults.shiftY()) {
			keys.add("fontsys.confirm.change.shift_y");
		}
		// In the Advanced screen's order: hinting above Replace Unicode font.
		if (settings.hinting() != defaults.hinting()) {
			keys.add("fontsys.confirm.change.hinting");
		}
		if (settings.applyToUniform() != defaults.applyToUniform()) {
			keys.add("fontsys.screen.apply_to_uniform");
		}
		return keys;
	}

	/** {@code settings} with every advanced setting at its default and everything else kept. */
	public static FontSettings reset(FontSettings settings) {
		FontSettings defaults = FontSettings.DEFAULTS;
		return settings
			.withLayout(settings.size(), settings.oversample(), defaults.shiftX(), defaults.shiftY())
			.withApplyToUniform(defaults.applyToUniform())
			.withHinting(defaults.hinting());
	}
}
