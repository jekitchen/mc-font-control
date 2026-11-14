package io.github.jekitchen.fontsys.config;

import io.github.jekitchen.fontsys.discovery.InstanceFonts;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A font family the player applied and kept, for the "recent" entries at the top of the font
 * list (PRD UI-17), with the tuning it was kept with, which picking it again restores (D55).
 * The path tells an instance family from a system family of the same name (FD-14); like
 * {@code fontPath}, it is saved relative to the game folder for instance fonts. {@code tuning}
 * is null in configs written before 1.2.0.
 */
public record RecentFont(String familyName, @Nullable String fontPath, @Nullable FontTuning tuning) {
	/** How many recent fonts the config keeps. */
	public static final int MAX = 5;

	/** A recent font without remembered tuning. */
	public RecentFont(String familyName, @Nullable String fontPath) {
		this(familyName, fontPath, null);
	}

	/** {@code recent} with {@code added} moved or put first, at most {@link #MAX}, nulls dropped. */
	public static List<RecentFont> push(@Nullable List<RecentFont> recent, RecentFont added) {
		List<RecentFont> result = new ArrayList<>(MAX);
		result.add(added);
		if (recent != null) {
			for (RecentFont r : recent) {
				if (result.size() == MAX) {
					break;
				}
				if (r != null && r.familyName() != null && !r.sameFamily(added)) {
					result.add(r);
				}
			}
		}
		return result;
	}

	/** The tuning remembered for a family in {@code recent}, or null if it has none (D55). */
	public static @Nullable FontTuning tuningFor(@Nullable List<RecentFont> recent, String familyName, @Nullable String fontPath) {
		if (recent == null) {
			return null;
		}
		RecentFont wanted = new RecentFont(familyName, fontPath);
		for (RecentFont r : recent) {
			if (r != null && r.familyName() != null && r.tuning() != null && r.sameFamily(wanted)) {
				return r.tuning();
			}
		}
		return null;
	}

	/** Same family name and both system or both instance fonts: an instance copy of a system family counts separately (FD-14). */
	public boolean sameFamily(RecentFont other) {
		return familyName.equalsIgnoreCase(other.familyName) && InstanceFonts.contains(fontPath) == InstanceFonts.contains(other.fontPath);
	}
}
