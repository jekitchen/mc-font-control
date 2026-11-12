package io.github.jekitchen.fontsys.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.fontsys.config.ConfigStore;
import io.github.jekitchen.fontsys.config.FontConfig;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.config.SystemFingerprint;
import io.github.jekitchen.fontsys.discovery.OsFamily;
import io.github.jekitchen.fontsys.platform.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Startup behaviour of the controller: new-system detection (5.6) and crash recovery (5.7). */
class FontControllerStartupTest {
	private static final String HERE = SystemFingerprint.of(OsFamily.WINDOWS, "C:\\Users\\alex");

	private static Platform platform(String... loadedMods) {
		return new Platform() {
			@Override
			public Path configDir() {
				return Path.of(".");
			}

			@Override
			public boolean isModLoaded(String id) {
				for (String m : loadedMods) {
					if (m.equals(id)) {
						return true;
					}
				}
				return false;
			}
		};
	}

	private static ConfigStore storeWith(Path dir, String fingerprint, FontSettings settings, FontSettings pending) throws IOException {
		ConfigStore store = new ConfigStore(dir.resolve("fontsys.json"));
		FontConfig config = FontConfig.defaults();
		config.systemFingerprint = fingerprint;
		config.setSettings(settings);
		config.pending = pending;
		store.save(config);
		return store;
	}

	private static final FontSettings AVENIR = FontSettings.DEFAULTS.withEnabled(true).withFont("/f/Avenir.ttc", 3, "Avenir Next", "Medium");

	@Test
	void fingerprintFormatAndMismatchMessages() {
		String mac = SystemFingerprint.of(OsFamily.MACOS, "/Users/alex");
		assertTrue(mac.startsWith("MACOS:"));
		assertFalse(mac.contains("alex"), "home path is only stored hashed");
		assertEquals(Optional.empty(), SystemFingerprint.mismatch(HERE, HERE));
		assertEquals("OS family or user profile changed: macOS -> Windows", SystemFingerprint.mismatch(mac, HERE).orElseThrow());
		assertTrue(SystemFingerprint.mismatch(SystemFingerprint.of(OsFamily.WINDOWS, "C:\\Users\\sam"), HERE).orElseThrow().contains("user profile"));
		assertTrue(SystemFingerprint.mismatch(null, HERE).isPresent());
	}

	@Test
	void sameSystemKeepsSettings(@TempDir Path dir) throws IOException {
		FontController c = new FontController(storeWith(dir, HERE, AVENIR, null), platform(), () -> HERE);
		assertEquals(AVENIR, c.appliedSettings());
	}

	@Test
	void copiedConfigIsBackedUpAndReset(@TempDir Path dir) throws IOException {
		String mac = SystemFingerprint.of(OsFamily.MACOS, "/Users/alex");
		ConfigStore store = storeWith(dir, mac, AVENIR, null);
		FontController c = new FontController(store, platform(), () -> HERE);
		assertFalse(c.appliedSettings().isActive());
		assertTrue(Files.exists(store.backupFile()));
		assertTrue(Files.readString(store.backupFile()).contains("Avenir Next"));
		// The fresh config records this system, so the next launch does not reset again.
		assertEquals(HERE, new ConfigStore(store.file()).load().config().systemFingerprint);
	}

	@Test
	void unconfirmedChangeIsRevertedAtStartup(@TempDir Path dir) throws IOException {
		FontSettings bigger = AVENIR.withLayout(16, 0, 0, 0);
		ConfigStore store = storeWith(dir, HERE, bigger, AVENIR);
		FontController c = new FontController(store, platform(), () -> HERE);
		assertEquals(AVENIR, c.appliedSettings());
		assertNull(c.config().pending);
		assertNull(store.load().config().pending, "recovery is saved");
	}

	@Test
	void otherFontModMakesUsStepAside(@TempDir Path dir) throws IOException {
		FontController c = new FontController(storeWith(dir, HERE, AVENIR, null), platform("caxton"), () -> HERE);
		assertTrue(c.stepAside());
		assertEquals("Caxton", c.conflict().orElseThrow().name());
	}

	@Test
	void firstRunWritesFingerprint(@TempDir Path dir) {
		ConfigStore store = new ConfigStore(dir.resolve("fontsys.json"));
		new FontController(store, platform(), () -> HERE);
		assertEquals(HERE, store.load().config().systemFingerprint);
	}

	private static Platform platformIn(Path gameDir) {
		return new Platform() {
			@Override
			public Path configDir() {
				return gameDir.resolve("config");
			}

			@Override
			public Path gameDir() {
				return gameDir;
			}

			@Override
			public boolean isModLoaded(String id) {
				return false;
			}
		};
	}

	@Test
	void copiedInstanceKeepsItsInstanceFont(@TempDir Path game) throws IOException {
		Path font = game.resolve("fonts").resolve("Pixel.ttf");
		Files.createDirectories(font.getParent());
		Files.writeString(font, "not really a font; only its presence matters here");
		FontSettings pixel = FontSettings.DEFAULTS.withEnabled(true).withFont(font.toString(), 0, "Pixel", "Regular");
		String mac = SystemFingerprint.of(OsFamily.MACOS, "/Users/alex");
		ConfigStore store = new ConfigStore(game.resolve("config").resolve("fontsys.json"), game);
		FontConfig config = FontConfig.defaults();
		config.systemFingerprint = mac;
		config.setSettings(pixel);
		store.save(config);

		FontController c = new FontController(store, platformIn(game), () -> HERE);
		assertEquals(pixel, c.appliedSettings());
		assertFalse(Files.exists(store.backupFile()));
		assertEquals(HERE, store.load().config().systemFingerprint, "adopted by this system");
	}

	@Test
	void copiedInstanceWithoutItsFontStillResets(@TempDir Path game) throws IOException {
		FontSettings missing = FontSettings.DEFAULTS.withEnabled(true).withFont(game.resolve("fonts").resolve("Gone.ttf").toString(), 0, "Gone", "Regular");
		ConfigStore store = new ConfigStore(game.resolve("config").resolve("fontsys.json"), game);
		FontConfig config = FontConfig.defaults();
		config.systemFingerprint = SystemFingerprint.of(OsFamily.MACOS, "/Users/alex");
		config.setSettings(missing);
		store.save(config);

		FontController c = new FontController(store, platformIn(game), () -> HERE);
		assertFalse(c.appliedSettings().isActive());
		assertTrue(Files.exists(store.backupFile()));
	}
}
