package io.github.jekitchen.mcfontcontrol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Guards the identity and client-only rules from the PRD (D18, section 1). */
class ModMetadataTest {
	private static String read(String resource) throws IOException {
		try (InputStream in = ModMetadataTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, "missing resource " + resource);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void modIdMatchesMetadata() throws IOException {
		String json = read("/fabric.mod.json");
		assertTrue(json.contains("\"id\": \"" + McFontControl.MOD_ID + "\""), "mod id must be " + McFontControl.MOD_ID);
	}

	@Test
	void modIsClientOnly() throws IOException {
		assertTrue(read("/fabric.mod.json").contains("\"environment\": \"client\""), "mod must be client-only");
	}

	@Test
	void licenseIsMit() throws IOException {
		assertTrue(read("/fabric.mod.json").contains("\"license\": \"MIT\""));
	}

	@Test
	void langFileExists() throws IOException {
		assertTrue(read("/assets/mcfontcontrol/lang/en_us.json").contains("MC Font Control"));
	}
}
