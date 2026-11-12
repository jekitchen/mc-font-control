package io.github.jekitchen.mcfontcontrol.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.discovery.FaceInfo;
import io.github.jekitchen.mcfontcontrol.discovery.FontIndex;
import io.github.jekitchen.mcfontcontrol.discovery.Visibility;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runtime pack logic that runs without the game (PRD AP-2, AP-7, AP-11, D11). */
class PackTest {
	@Test
	void privateUseRanges() {
		assertTrue(PrivateUseFilteringProvider.isPrivateUse(0xE000));
		assertTrue(PrivateUseFilteringProvider.isPrivateUse(0xF8FF));
		assertTrue(PrivateUseFilteringProvider.isPrivateUse(0xF0000));
		assertTrue(PrivateUseFilteringProvider.isPrivateUse(0x10FFFD));
		assertFalse(PrivateUseFilteringProvider.isPrivateUse(0xDFFF));
		assertFalse(PrivateUseFilteringProvider.isPrivateUse(0xF900));
		assertFalse(PrivateUseFilteringProvider.isPrivateUse('A'));
		assertFalse(PrivateUseFilteringProvider.isPrivateUse(0x1F600));
	}

	@Test
	void ttfDefinition() {
		JsonObject root = JsonParser.parseString(FontDefinitions.ttf("mcfontcontrol:system/selected.ttf", 10.5F, 2, 0.5F, -1)).getAsJsonObject();
		JsonObject provider = root.getAsJsonArray("providers").get(0).getAsJsonObject();
		assertEquals(1, root.getAsJsonArray("providers").size());
		assertEquals("ttf", provider.get("type").getAsString());
		assertEquals("mcfontcontrol:system/selected.ttf", provider.get("file").getAsString());
		assertEquals(10.5F, provider.get("size").getAsFloat());
		assertEquals(2F, provider.get("oversample").getAsFloat());
		assertEquals(-1F, provider.getAsJsonArray("shift").get(1).getAsFloat());
		assertFalse(provider.has("skip"), "private-use filtering happens in the provider wrapper, not skip");
	}

	@Test
	void vanillaCopyMatchesVanillaDefault() {
		JsonObject root = JsonParser.parseString(FontDefinitions.vanillaCopy()).getAsJsonObject();
		assertEquals(3, root.getAsJsonArray("providers").size());
		assertEquals("minecraft:include/default", root.getAsJsonArray("providers").get(1).getAsJsonObject().get("id").getAsString());
		assertFalse(root.getAsJsonArray("providers").get(1).getAsJsonObject().getAsJsonObject("filter").get("uniform").getAsBoolean());
	}

	/** Uses vanilla's own insertion rule to check where our pack lands (D11). */
	@Test
	void packSitsAbovePlayerPacksAndBelowServerPacks() {
		PackSelectionConfig vanilla = new PackSelectionConfig(true, Pack.Position.BOTTOM, true);
		PackSelectionConfig playerPack = new PackSelectionConfig(false, Pack.Position.TOP, false);
		PackSelectionConfig serverPack = new PackSelectionConfig(true, Pack.Position.TOP, true);
		PackSelectionConfig ours = new PackSelectionConfig(true, Pack.Position.TOP, false);

		List<PackSelectionConfig> list = new ArrayList<>(List.of(vanilla, playerPack, serverPack));
		Pack.Position.TOP.insert(list, ours, c -> c, false);
		assertEquals(List.of(vanilla, playerPack, ours, serverPack), list);

		// A server pack added later also goes above ours.
		List<PackSelectionConfig> later = new ArrayList<>(List.of(vanilla, playerPack, ours));
		PackSelectionConfig server2 = new PackSelectionConfig(true, Pack.Position.TOP, true);
		Pack.Position.TOP.insert(later, server2, c -> c, false);
		assertEquals(server2, later.getLast());
	}

	@Test
	void resolverUsesFileWhenPresent(@TempDir Path dir) throws IOException {
		Path font = Files.writeString(dir.resolve("a.ttf"), "x");
		FontSettings s = FontSettings.DEFAULTS.withEnabled(true).withFont(font.toString(), 0, "A", "Regular");
		FontResolver.Result r = FontResolver.resolve(s, FontIndex::empty, file -> List.of(face(file, 0, "A", "Regular", Visibility.VISIBLE)));
		assertTrue(r.font().isPresent());
		assertFalse(r.font().get().moved());
	}

	@Test
	void resolverRefindsMovedFont(@TempDir Path dir) throws IOException {
		Path moved = Files.writeString(dir.resolve("moved.ttc"), "x");
		FaceInfo newHome = face(moved, 2, "Avenir Next", "Medium", Visibility.VISIBLE);
		FontIndex index = new FontIndex(FontIndex.VERSION, List.of(new FontIndex.FileEntry(moved.toString(), 0, 1, List.of(newHome), null)));
		FontSettings s = FontSettings.DEFAULTS.withEnabled(true).withFont(dir.resolve("gone.ttc").toString(), 5, "Avenir Next", "Medium");
		FontResolver.Result r = FontResolver.resolve(s, () -> index, file -> List.of(newHome));
		assertTrue(r.font().isPresent());
		assertTrue(r.font().get().moved());
		assertEquals(2, r.font().get().faceIndex());
	}

	@Test
	void resolverFailsCleanly(@TempDir Path dir) throws IOException {
		FontSettings gone = FontSettings.DEFAULTS.withEnabled(true).withFont(dir.resolve("gone.ttf").toString(), 0, "Nope", "Regular");
		FontResolver.Result r = FontResolver.resolve(gone, FontIndex::empty, file -> List.of());
		assertTrue(r.font().isEmpty());
		assertTrue(r.problem().contains("no longer installed"));
		assertFalse(r.problem().contains(dir.toString()), "log text must not contain the folder path");

		Path corrupt = Files.writeString(dir.resolve("bad.ttf"), "x");
		FontSettings bad = FontSettings.DEFAULTS.withEnabled(true).withFont(corrupt.toString(), 0, "Bad", "Regular");
		FontResolver.Result r2 = FontResolver.resolve(bad, FontIndex::empty, file -> {
			throw new IOException("boom");
		});
		assertTrue(r2.problem().contains("could not be read"));

		FontResolver.Result r3 = FontResolver.resolve(bad, FontIndex::empty, file -> List.of(face(file, 0, "Bad", "Regular", Visibility.UNSUPPORTED)));
		assertTrue(r3.problem().contains("cannot be drawn"));

		assertTrue(FontResolver.resolve(FontSettings.DEFAULTS, FontIndex::empty).font().isEmpty());
	}

	private static FaceInfo face(Path path, int index, String family, String style, Visibility v) {
		return new FaceInfo(path.toString(), index, family, style, false, false, "TrueType", v);
	}
}
