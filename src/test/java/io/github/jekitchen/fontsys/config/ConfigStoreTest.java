package io.github.jekitchen.fontsys.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Config load, save, migration and backup (PRD 5.3, D16). */
class ConfigStoreTest {
	@Test
	void missingFileGivesDisabledDefaults(@TempDir Path dir) {
		ConfigStore.LoadResult r = new ConfigStore(dir.resolve("fontsys.json")).load();
		assertFalse(r.config().enabled);
		assertFalse(r.config().settings().isActive());
		assertTrue(r.warnings().isEmpty());
		assertFalse(r.reset());
	}

	@Test
	void roundTrip(@TempDir Path dir) throws IOException {
		ConfigStore store = new ConfigStore(dir.resolve("fontsys.json"));
		FontConfig config = FontConfig.defaults();
		config.setSettings(FontSettings.DEFAULTS.withEnabled(true).withFont("/fonts/a.ttc", (2 << 16) | 1, "Avenir Next", "Medium").withLayout(11, 3, 0.5F, -1));
		config.showHiddenFonts = true;
		config.pending = FontSettings.DEFAULTS;
		config.systemFingerprint = "abc";
		store.save(config);

		FontConfig loaded = store.load().config();
		assertEquals(config.settings(), loaded.settings());
		assertTrue(loaded.showHiddenFonts);
		assertEquals(FontSettings.DEFAULTS, loaded.pending);
		assertEquals("abc", loaded.systemFingerprint);
		assertEquals(FontConfig.CURRENT_VERSION, loaded.configVersion);
	}

	@Test
	void corruptFileIsBackedUpAndReset(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("fontsys.json"), "{ \"enabled\": tru");
		ConfigStore store = new ConfigStore(file);
		ConfigStore.LoadResult r = store.load();
		assertTrue(r.reset());
		assertEquals(1, r.warnings().size());
		assertFalse(r.config().enabled);
		assertFalse(Files.exists(file));
		assertEquals("{ \"enabled\": tru", Files.readString(store.backupFile()));
	}

	@Test
	void newerVersionIsTreatedAsUnreadable(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("fontsys.json"), "{\"configVersion\": 99, \"enabled\": true, \"fontPath\": \"/f.ttf\"}");
		ConfigStore.LoadResult r = new ConfigStore(file).load();
		assertTrue(r.reset());
		assertTrue(r.warnings().getFirst().contains("newer"));
		assertFalse(r.config().enabled);
	}

	@Test
	void unknownFieldsIgnoredAndMissingFieldsDefault(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("fontsys.json"), "{\"enabled\": true, \"fontPath\": \"/f.ttf\", \"futureThing\": [1,2]}");
		ConfigStore.LoadResult r = new ConfigStore(file).load();
		assertFalse(r.reset());
		FontSettings s = r.config().settings();
		assertTrue(s.isActive());
		assertEquals(FontSettings.DEFAULT_SIZE, s.size());
		assertEquals(FontSettings.AUTO_OVERSAMPLE, s.oversample());
		assertNull(r.config().pending);
		assertEquals(FontConfig.CURRENT_VERSION, r.config().configVersion);
	}

	@Test
	void wrongTypesAreUnreadable(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("fontsys.json"), "[1, 2, 3]");
		assertTrue(new ConfigStore(file).load().reset());
		Path file2 = Files.writeString(dir.resolve("b.json"), "{\"size\": \"big\"}");
		assertTrue(new ConfigStore(file2).load().reset());
	}

	@Test
	void settingsAreClamped() {
		FontSettings s = new FontSettings(true, "/f.ttf", -5, "F", "R", 99, 50, Float.NaN, -100, false, false).sanitized();
		assertEquals(0, s.faceIndex());
		assertEquals(FontSettings.MAX_SIZE, s.size());
		assertEquals(FontSettings.MAX_OVERSAMPLE, s.oversample());
		assertEquals(0, s.shiftX());
		assertEquals(-FontSettings.MAX_SHIFT, s.shiftY());
		assertEquals(FontSettings.AUTO_OVERSAMPLE, new FontSettings(true, "/f", 0, null, null, 10, -3, 0, 0, false, false).sanitized().oversample());
	}

	@Test
	void sameRendering() {
		FontSettings a = FontSettings.DEFAULTS.withEnabled(true).withFont("/a.ttf", 0, "A", "Regular");
		assertTrue(a.sameRendering(a.withFont("/a.ttf", 0, "Renamed", "Style")));
		assertFalse(a.sameRendering(a.withLayout(12, 0, 0, 0)));
		assertFalse(a.sameRendering(a.withEnabled(false)));
		// Two disabled configurations both render vanilla.
		assertTrue(FontSettings.DEFAULTS.sameRendering(a.withEnabled(false)));
	}

	@Test
	void instanceFontsAreSavedRelativeAndFollowTheInstance(@TempDir Path game, @TempDir Path moved) throws IOException {
		String font = game.resolve("fonts").resolve("pixel").resolve("Pixel.otf").toString();
		String previous = game.resolve("fonts").resolve("Comic.ttf").toString();
		ConfigStore store = new ConfigStore(game.resolve("config").resolve("fontsys.json"), game);
		FontConfig config = FontConfig.defaults();
		config.setSettings(FontSettings.DEFAULTS.withEnabled(true).withFont(font, 0, "Pixel", "Regular"));
		config.pending = FontSettings.DEFAULTS.withEnabled(true).withFont(previous, 0, "Comic", "Regular");
		store.save(config);

		String json = Files.readString(store.file());
		assertTrue(json.contains("\"fontPath\": \"fonts/pixel/Pixel.otf\""), json);
		assertTrue(json.contains("\"fontPath\": \"fonts/Comic.ttf\""), json);
		assertFalse(json.contains(game.toString().replace("\\", "\\\\")), "no absolute instance path in the file");

		// The same file read from a copy of the instance elsewhere.
		Path copy = moved.resolve("config").resolve("fontsys.json");
		Files.createDirectories(copy.getParent());
		Files.copy(store.file(), copy);
		FontConfig loaded = new ConfigStore(copy, moved).load().config();
		assertEquals(moved.resolve("fonts").resolve("pixel").resolve("Pixel.otf").toString(), loaded.settings().fontPath());
		assertEquals(moved.resolve("fonts").resolve("Comic.ttf").toString(), loaded.pending.fontPath());
	}

	@Test
	void recentFontsRoundTripWithInstancePathsRelative(@TempDir Path game, @TempDir Path moved) throws IOException {
		String instance = game.resolve("fonts").resolve("Pixel.otf").toString();
		String system = game.getRoot().resolve("Windows").resolve("Fonts").resolve("arial.ttf").toString();
		ConfigStore store = new ConfigStore(game.resolve("config").resolve("fontsys.json"), game);
		FontConfig config = FontConfig.defaults();
		config.recentFonts = List.of(new RecentFont("Pixel", instance), new RecentFont("Arial", system));
		store.save(config);
		String json = Files.readString(store.file());
		assertTrue(json.contains("\"fontPath\": \"fonts/Pixel.otf\""), json);

		Path copy = moved.resolve("config").resolve("fontsys.json");
		Files.createDirectories(copy.getParent());
		Files.copy(store.file(), copy);
		List<RecentFont> loaded = new ConfigStore(copy, moved).load().config().recentFonts;
		assertEquals(List.of(new RecentFont("Pixel", moved.resolve("fonts").resolve("Pixel.otf").toString()), new RecentFont("Arial", system)), loaded);
	}

	@Test
	void missingOrNullRecentFontsLoadAsEmpty(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("fontsys.json");
		Files.writeString(file, "{\"configVersion\":1}");
		assertEquals(List.of(), new ConfigStore(file).load().config().recentFonts);
		Files.writeString(file, "{\"configVersion\":1,\"recentFonts\":null}");
		assertEquals(List.of(), new ConfigStore(file).load().config().recentFonts);
		Files.writeString(file, "{\"configVersion\":1,\"recentFonts\":[null,{\"fontPath\":\"/a.ttf\"},{\"familyName\":\"Inter\"}]}");
		assertEquals(List.of(new RecentFont("Inter", null)), new ConfigStore(file).load().config().recentFonts);
	}

	@Test
	void pushingARecentFontMovesItFirstAndKeepsFive() {
		List<RecentFont> recent = List.of();
		for (String name : List.of("A", "B", "C", "D", "E", "F")) {
			recent = RecentFont.push(recent, new RecentFont(name, "/sys/" + name + ".ttf"));
		}
		assertEquals(List.of("F", "E", "D", "C", "B"), recent.stream().map(RecentFont::familyName).toList());
		recent = RecentFont.push(recent, new RecentFont("c", "/sys/C-Bold.ttf"));
		assertEquals(List.of("c", "F", "E", "D", "B"), recent.stream().map(RecentFont::familyName).toList(), "same family, any face, moves to the top");
	}

	@Test
	void systemFontsStayAbsolute(@TempDir Path game) throws IOException {
		String system = game.getRoot().resolve("Windows").resolve("Fonts").resolve("arial.ttf").toString();
		ConfigStore store = new ConfigStore(game.resolve("fontsys.json"), game);
		FontConfig config = FontConfig.defaults();
		config.setSettings(FontSettings.DEFAULTS.withEnabled(true).withFont(system, 0, "Arial", "Regular"));
		store.save(config);
		assertEquals(system, store.load().config().settings().fontPath());
	}
}
