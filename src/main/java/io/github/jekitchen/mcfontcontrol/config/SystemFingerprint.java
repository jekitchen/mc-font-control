package io.github.jekitchen.mcfontcontrol.config;

import io.github.jekitchen.mcfontcontrol.discovery.OsFamily;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Detects a config copied from another system (PRD 5.6, D12).
 *
 * <p>Stored as {@code <OS family>:<SHA-256 of OS family + user home path>}. The OS family is
 * kept in clear so the log can say which part changed; the home path is only ever hashed, and
 * never logged. Host name is deliberately not used.
 */
public final class SystemFingerprint {
	private SystemFingerprint() {
	}

	public static String of(OsFamily os, String userHome) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			byte[] hash = sha.digest((os.name() + "\n" + userHome).getBytes(StandardCharsets.UTF_8));
			return os.name() + ":" + HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}

	public static String current() {
		return of(OsFamily.current(), System.getProperty("user.home", ""));
	}

	/**
	 * Empty if the stored fingerprint matches; otherwise the reason for the log line, e.g.
	 * "OS family or user profile changed: macOS -> Windows".
	 */
	public static Optional<String> mismatch(String stored, String current) {
		if (current.equals(stored)) {
			return Optional.empty();
		}
		String now = osOf(current).map(OsFamily::displayName).orElse("unknown");
		if (stored == null || stored.isBlank()) {
			return Optional.of("no system fingerprint in the file; now " + now);
		}
		Optional<OsFamily> before = osOf(stored);
		if (before.isPresent() && osOf(current).isPresent() && before.get() != osOf(current).get()) {
			return Optional.of("OS family or user profile changed: " + before.get().displayName() + " -> " + now);
		}
		return Optional.of("OS family or user profile changed: user profile differs (" + now + ")");
	}

	private static Optional<OsFamily> osOf(String fingerprint) {
		if (fingerprint == null) {
			return Optional.empty();
		}
		int colon = fingerprint.indexOf(':');
		if (colon <= 0) {
			return Optional.empty();
		}
		try {
			return Optional.of(OsFamily.valueOf(fingerprint.substring(0, colon)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
