package io.github.jekitchen.fontsys.discovery;

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
		.thenComparingInt(f -> StyleNames.plainness(f.style()) == 2 ? 1 : 0)
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
		return styles.stream().filter(f -> f.italic() == italic && f.weight() == weight)
			.min(Comparator.comparingInt(f -> StyleNames.plainness(f.style())));
	}

	private Optional<FaceInfo> closest(boolean italic, int weight) {
		return styles.stream()
			.filter(f -> f.italic() == italic)
			.min(Comparator.comparingInt((FaceInfo f) -> Math.abs(f.weight() - weight))
				.thenComparingInt(f -> StyleNames.plainness(f.style()))
				.thenComparing(STYLE_ORDER));
	}

	/**
	 * Groups faces into families, skipping faces that should not be shown. Duplicate
	 * family/style pairs (the same font installed twice) keep the first one in path order.
	 * Aliases ({@link FamilyAliases}) are added as extra families unless a real family already
	 * has the alias's name.
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
		for (FamilyAliases.Alias alias : FamilyAliases.ALIASES) {
			String key = alias.alias().toLowerCase(Locale.ROOT);
			Map<String, FaceInfo> source = byFamily.get(alias.family().toLowerCase(Locale.ROOT));
			if (source == null || byFamily.containsKey(key)) {
				continue;
			}
			Map<String, FaceInfo> aliased = new LinkedHashMap<>();
			source.forEach((style, face) -> {
				if (alias.matches(face)) {
					aliased.put(style, new FaceInfo(face.path(), face.faceIndex(), alias.alias(), face.style(), face.bold(), face.italic(), face.format(), face.visibility()));
				}
			});
			if (!aliased.isEmpty()) {
				byFamily.put(key, aliased);
				displayNames.put(key, alias.alias());
			}
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
