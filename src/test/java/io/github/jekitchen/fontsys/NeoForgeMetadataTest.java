package io.github.jekitchen.fontsys;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The NeoForge jar keeps the same identity as the Fabric one (PRD D18, D51): mod ID, name,
 * license, links and the shared mixin config.
 */
class NeoForgeMetadataTest {
	private static final Path TOML = Path.of("neoforge/src/main/resources/META-INF/neoforge.mods.toml");
	private static final Path ENTRY = Path.of("neoforge/src/main/java/io/github/jekitchen/fontsys/neoforge/FontsysNeoForge.java");

	private static String fabric() throws IOException {
		try (InputStream in = NeoForgeMetadataTest.class.getResourceAsStream("/fabric.mod.json")) {
			assertNotNull(in, "missing fabric.mod.json");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void identityMatchesFabric() throws IOException {
		String toml = Files.readString(TOML);
		String fabric = fabric();
		assertTrue(toml.contains("modId=\"" + Fontsys.MOD_ID + "\""), "mod id must be " + Fontsys.MOD_ID);
		assertTrue(toml.contains("displayName=\"" + Fontsys.DISPLAY_NAME + "\""), "display name must be " + Fontsys.DISPLAY_NAME);
		assertTrue(toml.contains("license=\"MIT\""), "license must be MIT");
		for (String link : new String[] {"https://modrinth.com/mod/fontsys", "https://github.com/jekitchen/mc-fontsys/issues"}) {
			assertTrue(fabric.contains(link) && toml.contains(link), "both loaders link to " + link);
		}
	}

	@Test
	void sharesTheMixinConfig() throws IOException {
		assertTrue(Files.readString(TOML).contains("config=\"fontsys.mixins.json\""), "NeoForge must load the shared mixin config");
		assertTrue(fabric().contains("\"fontsys.mixins.json\""), "Fabric must load the shared mixin config");
	}

	@Test
	void entryPointIsClientOnly() throws IOException {
		assertTrue(Files.readString(ENTRY).contains("dist = Dist.CLIENT"), "the NeoForge entry point must be client-only");
	}
}
