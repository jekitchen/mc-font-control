package io.github.jekitchen.fontsys;

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
		assertTrue(json.contains("\"id\": \"" + Fontsys.MOD_ID + "\""), "mod id must be " + Fontsys.MOD_ID);
	}

	@Test
	void modIsClientOnly() throws IOException {
		assertTrue(read("/fabric.mod.json").contains("\"environment\": \"client\""), "mod must be client-only");
	}

	@Test
	void licenseIsMit() throws IOException {
		assertTrue(read("/fabric.mod.json").contains("\"license\": \"MIT\""));
	}

	/**
	 * Quilt Loader tells Fabric mods it is Fabric Loader 0.19.5, so a higher floor stops Fontsys
	 * loading on Quilt, as 0.20.1 did (D57). Raise it only for a Fabric Loader feature Fontsys uses.
	 */
	@Test
	void fabricLoaderFloorAllowsQuilt() throws IOException {
		assertTrue(read("/fabric.mod.json").contains("\"fabricloader\": \">=0.19.5\""), "fabricloader must stay >=0.19.5 (PRD D57)");
	}

	@Test
	void langFileExists() throws IOException {
		assertTrue(read("/assets/fontsys/lang/en_us.json").contains("Fontsys"));
	}
}
