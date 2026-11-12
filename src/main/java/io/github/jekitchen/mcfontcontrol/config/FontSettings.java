package io.github.jekitchen.mcfontcontrol.config;

import java.util.Objects;

/**
 * The settings that decide which font is applied and how. Saved flat in the config file, and
 * nested under {@code pending} while a keep-or-revert confirmation is open (PRD 5.3, 5.7).
 *
 * @param oversample rasterization oversampling; {@code 0} means automatic (the current GUI scale)
 */
public record FontSettings(
	boolean enabled,
	String fontPath,
	int faceIndex,
	String familyName,
	String styleName,
	float size,
	float oversample,
	float shiftX,
	float shiftY,
	boolean applyToUniform,
	boolean crispEdges) {

	public static final float DEFAULT_SIZE = 10.0F;
	public static final float MIN_SIZE = 5.0F;
	public static final float MAX_SIZE = 20.0F;
	public static final float AUTO_OVERSAMPLE = 0.0F;
	public static final float MAX_OVERSAMPLE = 8.0F;
	public static final float MAX_SHIFT = 4.0F;

	public static final FontSettings DEFAULTS = new FontSettings(false, null, 0, null, null, DEFAULT_SIZE, AUTO_OVERSAMPLE, 0, 0, false, false);

	/** A font is selected and the master switch is on. */
	public boolean isActive() {
		return enabled && fontPath != null && !fontPath.isBlank();
	}

	/** Clamps every number to its usable range and replaces NaN with defaults (PRD UI-4, D16). */
	public FontSettings sanitized() {
		// A missing size reads as 0; treat it as "use the default".
		float s = size > 0 ? finiteOr(size, DEFAULT_SIZE) : DEFAULT_SIZE;
		float o = finiteOr(oversample, AUTO_OVERSAMPLE);
		return new FontSettings(
			enabled,
			fontPath,
			Math.max(0, faceIndex),
			familyName,
			styleName,
			clamp(s, MIN_SIZE, MAX_SIZE),
			o <= 0 ? AUTO_OVERSAMPLE : clamp(o, 1, MAX_OVERSAMPLE),
			clamp(finiteOr(shiftX, 0), -MAX_SHIFT, MAX_SHIFT),
			clamp(finiteOr(shiftY, 0), -MAX_SHIFT, MAX_SHIFT),
			applyToUniform,
			crispEdges);
	}

	/** The same font choice, with a new on/off state. */
	public FontSettings withEnabled(boolean value) {
		return new FontSettings(value, fontPath, faceIndex, familyName, styleName, size, oversample, shiftX, shiftY, applyToUniform, crispEdges);
	}

	public FontSettings withFont(String path, int index, String family, String style) {
		return new FontSettings(enabled, path, index, family, style, size, oversample, shiftX, shiftY, applyToUniform, crispEdges);
	}

	public FontSettings withLayout(float newSize, float newOversample, float newShiftX, float newShiftY) {
		return new FontSettings(enabled, fontPath, faceIndex, familyName, styleName, newSize, newOversample, newShiftX, newShiftY, applyToUniform, crispEdges);
	}

	public FontSettings withApplyToUniform(boolean value) {
		return new FontSettings(enabled, fontPath, faceIndex, familyName, styleName, size, oversample, shiftX, shiftY, value, crispEdges);
	}

	/** Whether two settings render the same text: anything that changes the applied font. */
	public boolean sameRendering(FontSettings other) {
		if (!isActive() && !other.isActive()) {
			return true;
		}
		return isActive() == other.isActive()
			&& Objects.equals(fontPath, other.fontPath)
			&& faceIndex == other.faceIndex
			&& size == other.size
			&& oversample == other.oversample
			&& shiftX == other.shiftX
			&& shiftY == other.shiftY
			&& applyToUniform == other.applyToUniform
			&& crispEdges == other.crispEdges;
	}

	private static float finiteOr(float value, float fallback) {
		return Float.isFinite(value) ? value : fallback;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
