package io.github.jekitchen.fontsys.config;

import org.jspecify.annotations.Nullable;

/**
 * Everything in {@link FontSettings} that tunes how a font is drawn, as opposed to which font:
 * size, sharpness, shifts, Replace Unicode font, Crisp edges and hinting. Each recent font
 * remembers the tuning it was kept with, and picking that font again restores it (PRD UI-17,
 * D55), since a shift or size that suits one font rarely suits another.
 */
public record FontTuning(
	float size,
	float oversample,
	float shiftX,
	float shiftY,
	boolean applyToUniform,
	boolean crispEdges,
	@Nullable Hinting hinting) {

	/** The tuning of a font picked for the first time. */
	public static final FontTuning DEFAULTS = of(FontSettings.DEFAULTS);

	public static FontTuning of(FontSettings settings) {
		return new FontTuning(settings.size(), settings.oversample(), settings.shiftX(), settings.shiftY(),
			settings.applyToUniform(), settings.crispEdges(), settings.hinting());
	}

	/** {@code settings} with this tuning: the same font and on/off state, clamped (D16). */
	public FontSettings applyTo(FontSettings settings) {
		return new FontSettings(settings.enabled(), settings.fontPath(), settings.faceIndex(), settings.familyName(), settings.styleName(),
			size, oversample, shiftX, shiftY, applyToUniform, crispEdges, Hinting.orDefault(hinting)).sanitized();
	}

	/** Clamped and defaulted, as read from a config file that may be hand-edited or old (D16). */
	public FontTuning sanitized() {
		return of(applyTo(FontSettings.DEFAULTS));
	}
}
