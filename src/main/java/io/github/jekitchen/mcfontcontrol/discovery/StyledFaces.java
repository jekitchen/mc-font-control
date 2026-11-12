package io.github.jekitchen.mcfontcontrol.discovery;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The faces used for bold, italic and bold-italic text when one face is selected (PRD AP-8,
 * D24), chosen the way word processors do it:
 *
 * <ul>
 *   <li><b>Bold:</b> the family's next heavier face of the same slope and width (CSS
 *       "bolder": Regular to Bold, Light to Regular, Semibold to Black). If there is none,
 *       FreeType emboldens the selected face's outlines.</li>
 *   <li><b>Italic:</b> the family's italic face nearest in weight, of the same width. If there
 *       is none, vanilla's slant is kept.</li>
 *   <li><b>Bold italic:</b> the next heavier face after that italic; if there is none, the
 *       italic emboldened by FreeType; with no italic at all, the bold face slanted by vanilla.</li>
 * </ul>
 *
 * @param bold       always present: a real face or the selected face emboldened
 * @param italic     empty when the family has no italic of the right width
 * @param boldItalic always present
 */
public record StyledFaces(Variant bold, Optional<Variant> italic, Variant boldItalic) {
	/**
	 * One face to load for a style.
	 *
	 * @param face          the face
	 * @param syntheticBold FreeType emboldens the outlines because no heavier face exists
	 */
	public record Variant(FaceInfo face, boolean syntheticBold) {
		/** True if the face is itself italic, so vanilla must not slant it again. */
		public boolean italic() {
			return face.italic();
		}
	}

	/**
	 * @param family the selected face's family, if the font index knows it
	 * @param base   the selected face
	 */
	public static StyledFaces of(Optional<FontFamily> family, FaceInfo base) {
		List<FaceInfo> styles = family.map(FontFamily::styles).orElse(List.of());
		String width = StyleNames.residue(base.style());
		Variant bold = heavier(styles, base, width)
			.map(f -> new Variant(f, false))
			.orElseGet(() -> new Variant(base, true));
		Optional<FaceInfo> italicFace = base.italic() ? Optional.of(base) : nearestItalic(styles, base.weight(), width);
		Optional<Variant> italic = italicFace.map(f -> new Variant(f, false));
		Variant boldItalic = italicFace
			.map(it -> heavier(styles, it, width).map(f -> new Variant(f, false)).orElseGet(() -> new Variant(it, true)))
			.orElse(bold);
		return new StyledFaces(bold, italic, boldItalic);
	}

	/** CSS "bolder": the weight bold text should have when normal text has {@code weight}. */
	static int bolder(int weight) {
		if (weight < 350) {
			return 400;
		}
		return weight < 550 ? 700 : 900;
	}

	/** The face of the same slope and width that is heavier than {@code base}, closest to {@link #bolder}. */
	private static Optional<FaceInfo> heavier(List<FaceInfo> styles, FaceInfo base, String width) {
		int target = bolder(base.weight());
		return styles.stream()
			.filter(f -> f.italic() == base.italic() && f.weight() > base.weight() && StyleNames.residue(f.style()).equals(width))
			.min(Comparator.comparingInt((FaceInfo f) -> Math.abs(f.weight() - target))
				.thenComparingInt(f -> -f.weight())
				.thenComparingInt(f -> StyleNames.plainness(f.style())));
	}

	/** The italic of the same width nearest in weight; ties go to the lighter face. */
	private static Optional<FaceInfo> nearestItalic(List<FaceInfo> styles, int weight, String width) {
		return styles.stream()
			.filter(f -> f.italic() && StyleNames.residue(f.style()).equals(width))
			.min(Comparator.comparingInt((FaceInfo f) -> Math.abs(f.weight() - weight))
				.thenComparingInt(FaceInfo::weight)
				.thenComparingInt(f -> StyleNames.plainness(f.style())));
	}
}
