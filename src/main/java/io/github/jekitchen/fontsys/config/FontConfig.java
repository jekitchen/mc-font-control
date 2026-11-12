package io.github.jekitchen.fontsys.config;

/**
 * Contents of {@code config/fontsys.json} (PRD 5.3). Field names are the JSON keys.
 * Defaults: disabled, vanilla font.
 */
public final class FontConfig {
	/** Current schema version (D16). */
	public static final int CURRENT_VERSION = 1;

	public int configVersion = CURRENT_VERSION;
	public String systemFingerprint;
	public boolean enabled;
	public String fontPath;
	public int faceIndex;
	public String familyName;
	public String styleName;
	public float size = FontSettings.DEFAULT_SIZE;
	public float oversample = FontSettings.AUTO_OVERSAMPLE;
	public float shiftX;
	public float shiftY;
	public boolean applyToUniform;
	public boolean showHiddenFonts;
	/** Previous settings while a keep-or-revert confirmation is open; set means "not confirmed". */
	public FontSettings pending;
	/** Hard-edged glyphs for pixel-style fonts (PRD AP-10, D34). */
	public boolean crispEdges;

	public FontSettings settings() {
		return new FontSettings(enabled, fontPath, faceIndex, familyName, styleName, size, oversample, shiftX, shiftY, applyToUniform, crispEdges).sanitized();
	}

	public void setSettings(FontSettings s) {
		FontSettings v = s.sanitized();
		enabled = v.enabled();
		fontPath = v.fontPath();
		faceIndex = v.faceIndex();
		familyName = v.familyName();
		styleName = v.styleName();
		size = v.size();
		oversample = v.oversample();
		shiftX = v.shiftX();
		shiftY = v.shiftY();
		applyToUniform = v.applyToUniform();
		crispEdges = v.crispEdges();
	}

	public static FontConfig defaults() {
		return new FontConfig();
	}
}
