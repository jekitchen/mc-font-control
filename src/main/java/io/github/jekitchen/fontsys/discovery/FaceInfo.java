package io.github.jekitchen.fontsys.discovery;

/**
 * One selectable face: a face inside a font file, or a named instance of a variable font.
 *
 * @param path       absolute path of the font file
 * @param faceIndex  FreeType face index: face number in the low 16 bits, named instance in the
 *                   upper bits ({@code (instance << 16) | face}), as {@code FT_New_Memory_Face} expects
 * @param family     family name as FreeType reports it
 * @param style      style name, or the named-instance name for variable fonts
 * @param bold       FreeType's bold style flag
 * @param italic     FreeType's italic style flag
 * @param format     FreeType font format, e.g. {@code TrueType} or {@code CFF}
 * @param visibility listing rule (FD-13)
 */
public record FaceInfo(String path, int faceIndex, String family, String style, boolean bold, boolean italic, String format, Visibility visibility) {
	public boolean isNamedInstance() {
		return (faceIndex >>> 16) != 0;
	}

	/** Rough weight from the style name (100 Thin .. 900 Black); used for ordering and slots. */
	public int weight() {
		return StyleNames.weight(style, bold);
	}
}
