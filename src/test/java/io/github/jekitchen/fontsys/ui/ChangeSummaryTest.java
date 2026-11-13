package io.github.jekitchen.fontsys.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.ui.ChangeSummary.Row;
import io.github.jekitchen.fontsys.ui.ChangeSummary.Value;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the "Keep this font?" dialog lists as changed (PRD UI-11, D47). */
class ChangeSummaryTest {
	private static final FontSettings INTER = FontSettings.DEFAULTS.withEnabled(true).withFont("/fonts/Inter.ttf", 0, "Inter", "Regular");
	private static final FontSettings GEORGIA = FontSettings.DEFAULTS.withEnabled(true).withFont("/fonts/georgia.ttf", 0, "Georgia", "Bold");

	private static Value literal(String text) {
		return new Value(null, text);
	}

	private static Value key(String key) {
		return new Value(key, "");
	}

	@Test
	void fontOnly() {
		assertEquals(List.of(new Row("fontsys.confirm.change.font", literal("Inter Regular"), literal("Georgia Bold"))),
			ChangeSummary.rows(INTER, GEORGIA));
	}

	@Test
	void settingsWithTheSameFont() {
		FontSettings after = INTER.withLayout(12, 4, 0.5F, -1).withApplyToUniform(true);
		List<Row> rows = ChangeSummary.rows(INTER, after);
		assertEquals(List.of(
			new Row("fontsys.confirm.change.font", literal("Inter Regular"), literal("Inter Regular")),
			new Row("fontsys.confirm.change.size", literal("10"), literal("12")),
			new Row("fontsys.confirm.change.sharpness", key("fontsys.confirm.auto"), literal("4×")),
			new Row("fontsys.confirm.change.shift_x", literal("0"), literal("0.5")),
			new Row("fontsys.confirm.change.shift_y", literal("0"), literal("-1")),
			new Row("fontsys.screen.apply_to_uniform", key("fontsys.confirm.off"), key("fontsys.confirm.on"))), rows);
		assertFalse(rows.get(0).changed(), "the font row is listed, but as unchanged");
		assertTrue(rows.get(1).changed());
	}

	@Test
	void crispEdgesHidesSharpness() {
		FontSettings before = INTER.withLayout(10, 4, 0, 0);
		FontSettings after = INTER.withLayout(10, 2, 0, 0).withCrispEdges(true);
		assertEquals(List.of(
			new Row("fontsys.confirm.change.font", literal("Inter Regular"), literal("Inter Regular")),
			new Row("fontsys.screen.crisp", key("fontsys.confirm.off"), key("fontsys.confirm.on"))),
			ChangeSummary.rows(before, after), "crisp mode ignores sharpness, so it isn't listed");
	}

	@Test
	void toVanillaListsOnlyTheFont() {
		FontSettings before = GEORGIA.withLayout(14, 4, 1, 1).withCrispEdges(true);
		assertEquals(List.of(new Row("fontsys.confirm.change.font", literal("Georgia Bold"), key("fontsys.confirm.vanilla_font"))),
			ChangeSummary.rows(before, before.withEnabled(false)));
	}

	@Test
	void fromVanillaListsSettingsThatAreNotTheDefault() {
		FontSettings vanilla = INTER.withLayout(14, 0, 0, 0).withEnabled(false);
		List<Row> rows = ChangeSummary.rows(vanilla, GEORGIA.withLayout(12, 0, 0, 0).withCrispEdges(true));
		assertEquals(List.of(
			new Row("fontsys.confirm.change.font", key("fontsys.confirm.vanilla_font"), literal("Georgia Bold")),
			new Row("fontsys.confirm.change.size", null, literal("12")),
			new Row("fontsys.screen.crisp", null, key("fontsys.confirm.on"))), rows,
			"compared with the defaults, not the old font's leftover settings; new values only");
		assertTrue(rows.get(1).changed());
	}

	@Test
	void numbers() {
		assertEquals("10", ChangeSummary.format(10));
		assertEquals("10.5", ChangeSummary.format(10.5));
		assertEquals("-0.5", ChangeSummary.format(-0.5));
	}
}
