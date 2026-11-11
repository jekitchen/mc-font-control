package io.github.jekitchen.mcfontcontrol.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A font family with its selectable styles, ordered upright-first then by weight, and the
 * four classic slots (PRD FD-4).
 */
public record FontFamily(String name, List<FaceInfo> styles) {
	private static final Comparator<FaceInfo> STYLE_ORDER = Comparator
		.comparing((FaceInfo f) -> f.italic())
		.thenComparingInt(FaceInfo::weight)
		.thenComparing(f -> f.style().toLowerCase(Locale.ROOT));

	public Optional<FaceInfo> regular() {
		return closest(false, 400);
	}

	public Optional<FaceInfo> bold() {
		return exact(false, 700);
	}

	public Optional<FaceInfo> italic() {
		return exact(true, 400);
	}

	public Optional<FaceInfo> boldItalic() {
		return exact(true, 700);
	}

	/** The style to select when the player picks this family. */
	public FaceInfo defaultStyle() {
		return regular().orElse(styles.getFirst());
	}

	/** The most restrictive reason any style is hidden, used to mark revealed families (UI-10). */
	public Visibility visibility() {
		return styles.stream().map(FaceInfo::visibility).max(Comparator.naturalOrder()).orElse(Visibility.VISIBLE);
	}

	public Optional<FaceInfo> findStyle(String style) {
		return styles.stream().filter(f -> f.style().equalsIgnoreCase(style)).findFirst();
	}

	private Optional<FaceInfo> exact(boolean italic, int weight) {
		return styles.stream().filter(f -> f.italic() == italic && f.weight() == weight).findFirst();
	}

	private Optional<FaceInfo> closest(boolean italic, int weight) {
		return styles.stream()
			.filter(f -> f.italic() == italic)
			.min(Comparator.comparingInt((FaceInfo f) -> Math.abs(f.weight() - weight)).thenComparing(STYLE_ORDER));
	}

	/**
	 * Groups faces into families, skipping faces that should not be shown. Duplicate
	 * family/style pairs (the same font installed twice) keep the first one in path order.
	 */
	public static List<FontFamily> group(List<FaceInfo> faces, boolean showHidden) {
		Map<String, Map<String, FaceInfo>> byFamily = new LinkedHashMap<>();
		Map<String, String> displayNames = new LinkedHashMap<>();
		for (FaceInfo face : faces) {
			if (!face.visibility().isShown(showHidden)) {
				continue;
			}
			String key = face.family().toLowerCase(Locale.ROOT);
			displayNames.putIfAbsent(key, face.family());
			byFamily.computeIfAbsent(key, k -> new LinkedHashMap<>()).putIfAbsent(face.style().toLowerCase(Locale.ROOT), face);
		}
		List<FontFamily> result = new ArrayList<>();
		byFamily.forEach((key, styles) -> {
			List<FaceInfo> sorted = new ArrayList<>(styles.values());
			sorted.sort(STYLE_ORDER);
			result.add(new FontFamily(displayNames.get(key), List.copyOf(sorted)));
		});
		result.sort(Comparator.comparing(f -> f.name().toLowerCase(Locale.ROOT)));
		return List.copyOf(result);
	}
}
