package io.github.jekitchen.fontsys.discovery;

import java.util.Locale;

/** Operating-system families the mod knows how to scan for fonts. */
public enum OsFamily {
	WINDOWS("Windows"),
	MACOS("macOS"),
	LINUX("Linux"),
	OTHER("Other");

	private final String displayName;

	OsFamily(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	/** Classifies a {@code os.name} system property value. */
	public static OsFamily fromOsName(String osName) {
		String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
		if (name.startsWith("windows")) {
			return WINDOWS;
		}
		if (name.startsWith("mac") || name.contains("darwin")) {
			return MACOS;
		}
		if (name.contains("linux")) {
			return LINUX;
		}
		return OTHER;
	}

	public static OsFamily current() {
		return fromOsName(System.getProperty("os.name"));
	}
}
