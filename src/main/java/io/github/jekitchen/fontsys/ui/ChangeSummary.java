package io.github.jekitchen.fontsys.ui;

import io.github.jekitchen.fontsys.config.FontSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a font change changes, for the "Keep this font?" dialog (PRD UI-11, D47): the font,
 * from and to, then every setting that differs. Plain data with lang keys, so it is tested
 * without the game; {@link KeepOrRevertScreen} turns it into text.
 *
 * <ul>
 *   <li>The font row is always listed, unchanged or not, so the player sees which font the
 *       other rows apply to.</li>
 *   <li>Switching to the vanilla font lists only the font: the other settings don't apply to it.</li>
 *   <li>Switching from the vanilla font lists the settings that differ from the defaults, as
 *       new values only, since the vanilla font had none of them.</li>
 *   <li>Sharpness and hinting aren't listed while Crisp edges is on at either end: crisp mode
 *       ignores them (D34, D53), and the Crisp edges row already says what changed.</li>
 * </ul>
 */
public final class ChangeSummary {
	/** A value as shown: a lang key (with {@code text} as its argument), or literal text when the key is null. */
	public record Value(@Nullable String key, String text) {
		static Value literal(String text) {
			return new Value(null, text);
		}

		static Value translated(String key) {
			return new Value(key, "");
		}
	}

	/** One changed item; {@code from} is null when the old value doesn't apply (it was the vanilla font). */
	public record Row(String labelKey, @Nullable Value from, Value to) {
		public boolean changed() {
			return from == null || !from.equals(to);
		}
	}

	private ChangeSummary() {
	}

	public static List<Row> rows(FontSettings before, FontSettings after) {
		List<Row> rows = new ArrayList<>();
		boolean wasActive = before.isActive();
		boolean isActive = after.isActive();
		rows.add(new Row("fontsys.confirm.change.font", font(before), font(after)));
		if (!isActive) {
			return rows;
		}
		FontSettings from = wasActive ? before : FontSettings.DEFAULTS;
		add(rows, wasActive, "fontsys.confirm.change.size", Value.literal(format(from.size())), Value.literal(format(after.size())));
		if (!from.crispEdges() && !after.crispEdges()) {
			add(rows, wasActive, "fontsys.confirm.change.sharpness", sharpness(from), sharpness(after));
		}
		add(rows, wasActive, "fontsys.confirm.change.shift_x", Value.literal(format(from.shiftX())), Value.literal(format(after.shiftX())));
		add(rows, wasActive, "fontsys.confirm.change.shift_y", Value.literal(format(from.shiftY())), Value.literal(format(after.shiftY())));
		add(rows, wasActive, "fontsys.screen.apply_to_uniform", onOff(from.applyToUniform()), onOff(after.applyToUniform()));
		if (from.hintingApplies() && after.hintingApplies()) {
			add(rows, wasActive, "fontsys.confirm.change.hinting", Value.translated(from.hinting().key()), Value.translated(after.hinting().key()));
		}
		add(rows, wasActive, "fontsys.screen.crisp", onOff(from.crispEdges()), onOff(after.crispEdges()));
		return rows;
	}

	/** Lists a setting if it changed; coming from the vanilla font, if it isn't the default, as the new value only. */
	private static void add(List<Row> rows, boolean wasActive, String labelKey, Value from, Value to) {
		if (!from.equals(to)) {
			rows.add(new Row(labelKey, wasActive ? from : null, to));
		}
	}

	private static Value font(FontSettings settings) {
		if (!settings.isActive()) {
			return Value.translated("fontsys.confirm.vanilla_font");
		}
		String family = Objects.requireNonNullElse(settings.familyName(), "");
		String style = Objects.requireNonNullElse(settings.styleName(), "");
		return Value.literal((family + " " + style).strip());
	}

	private static Value sharpness(FontSettings settings) {
		return settings.oversample() <= 0 ? Value.translated("fontsys.confirm.auto") : Value.literal(format(settings.oversample()) + "×");
	}

	private static Value onOff(boolean on) {
		return Value.translated(on ? "fontsys.confirm.on" : "fontsys.confirm.off");
	}

	/** Whole numbers without a decimal point, others to one place, as on the settings screen. */
	static String format(double v) {
		return String.format(Locale.ROOT, v == Math.rint(v) ? "%.0f" : "%.1f", v);
	}
}
