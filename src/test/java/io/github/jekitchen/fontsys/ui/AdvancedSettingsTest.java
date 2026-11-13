package io.github.jekitchen.fontsys.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.config.Hinting;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which Advanced-screen settings differ from the defaults, and resetting them (PRD UI-19, D52). */
class AdvancedSettingsTest {
	private static final FontSettings INTER = FontSettings.DEFAULTS.withEnabled(true).withFont("/fonts/Inter.ttf", 0, "Inter", "Regular")
		.withLayout(12, 4, 0, 0).withCrispEdges(true);

	@Test
	void nothingChangedAtTheDefaults() {
		assertEquals(List.of(), AdvancedSettings.changedLabelKeys(FontSettings.DEFAULTS));
		// Size, sharpness and Crisp edges live on the main screen and don't count.
		assertEquals(List.of(), AdvancedSettings.changedLabelKeys(INTER));
	}

	@Test
	void listsEachChangedSettingInScreenOrder() {
		assertEquals(List.of("fontsys.confirm.change.shift_y"),
			AdvancedSettings.changedLabelKeys(INTER.withLayout(12, 4, 0, -1)));
		assertEquals(List.of("fontsys.confirm.change.shift_x", "fontsys.confirm.change.shift_y", "fontsys.screen.apply_to_uniform"),
			AdvancedSettings.changedLabelKeys(INTER.withLayout(12, 4, 0.5F, 1).withApplyToUniform(true)));
		assertEquals(List.of("fontsys.confirm.change.hinting"), AdvancedSettings.changedLabelKeys(INTER.withHinting(Hinting.LIGHT)));
		assertEquals(List.of("fontsys.confirm.change.shift_x", "fontsys.confirm.change.hinting"),
			AdvancedSettings.changedLabelKeys(INTER.withLayout(12, 4, 1, 0).withHinting(Hinting.NONE)));
	}

	@Test
	void resetKeepsEverythingElse() {
		FontSettings tuned = INTER.withLayout(12, 4, 0.5F, -1.5F).withApplyToUniform(true).withHinting(Hinting.NONE);
		assertEquals(INTER, AdvancedSettings.reset(tuned));
		assertEquals(INTER, AdvancedSettings.reset(INTER));
	}
}
