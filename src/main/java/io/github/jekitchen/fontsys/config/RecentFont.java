package io.github.jekitchen.fontsys.config;

import io.github.jekitchen.fontsys.discovery.InstanceFonts;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A font family the player applied and kept, for the "recent" entries at the top of the font
 * list (PRD UI-17). The path tells an instance family from a system family of the same name
 * (FD-14); like {@code fontPath}, it is saved relative to the game folder for instance fonts.
 */
public record RecentFont(String familyName, @Nullable String fontPath) {
	/** How many recent fonts the config keeps. */
	public static final int MAX = 5;

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

	/** Same family name and both system or both instance fonts: an instance copy of a system family counts separately (FD-14). */
	boolean sameFamily(RecentFont other) {
		return familyName.equalsIgnoreCase(other.familyName) && InstanceFonts.contains(fontPath) == InstanceFonts.contains(other.fontPath);
	}
}
