package io.github.jekitchen.fontsys.gametest;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * In-world text with an Iris shader pack (PRD D30). Off unless {@code FONTSYS_SHADERPACK} names
 * a shader pack zip or folder and Iris is installed:
 *
 * <pre>FONTSYS_SHADERPACK=/path/to/pack.zip ./gradlew runClientGameTest -PwithSodiumIris</pre>
 *
 * <p>The game-test run folder is wiped at launch, so the test copies the pack in and selects it
 * through Iris's config. It fails if Iris reports a render pipeline it has no shader-pack program
 * for ("Missing program ... in override list"), which is how unsupported text pipelines broke
 * sign text in 1.0.0, and saves screenshots for review. Shader packs can't be redistributed, so
 * CI doesn't run it.
 */
@SuppressWarnings("UnstableApiUsage")
public class ShaderPackGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		String pack = System.getenv("FONTSYS_SHADERPACK");
		if (pack == null || pack.isBlank() || !Fontsys.platform().isModLoaded("iris")) {
			return;
		}
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> face = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		Path source = Path.of(pack);
		context.runOnClient(mc -> {
			Path target = mc.gameDirectory.toPath().resolve("shaderpacks").resolve(source.getFileName());
			try {
				Files.createDirectories(target.getParent());
				if (!Files.exists(target)) {
					Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			selectShaderPack(source.getFileName().toString(), true);
		});

		List<String> missing = new CopyOnWriteArrayList<>();
		Logger root = (Logger) LogManager.getRootLogger();
		AbstractAppender watcher = new AbstractAppender("fontsys-shaderpack-watch", null, null, true, Property.EMPTY_ARRAY) {
			@Override
			public void append(LogEvent event) {
				String message = event.getMessage().getFormattedMessage();
				if (message.contains("Missing program")) {
					missing.add(message);
				}
			}
		};
		watcher.start();
		root.addAppender(watcher);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			InWorldTextGameTest.buildScene(world);
			world.getConnection().waitForChunksRender();
			context.waitTicks(40);
			context.takeScreenshot("shaderpack-world-vanilla");
			if (face.isPresent()) {
				GameTestFonts.apply(context, GameTestFonts.settingsFor(face.get()));
				world.getConnection().waitForChunksRender();
				context.waitTicks(40);
				context.takeScreenshot("shaderpack-world-system-font");
				world.getServer().runCommand("tp @a 0.5 -60 1.2 -25 10");
				context.waitTicks(20);
				context.takeScreenshot("shaderpack-world-closeup");
				GameTestFonts.apply(context, FontSettings.DEFAULTS);
			}
		} finally {
			root.removeAppender(watcher);
			watcher.stop();
		}
		context.waitForScreen(TitleScreen.class);
		context.runOnClient(mc -> selectShaderPack(null, false));
		if (!missing.isEmpty()) {
			throw new AssertionError("Iris drew with pipelines it has no shader-pack program for: " + missing.stream().distinct().toList());
		}
	}

	/** Iris has no public API for choosing a pack, so this goes through its config by reflection. */
	private static void selectShaderPack(String name, boolean enabled) {
		try {
			Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
			Object config = iris.getMethod("getIrisConfig").invoke(null);
			config.getClass().getMethod("setShaderPackName", String.class).invoke(config, name);
			config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, enabled);
			config.getClass().getMethod("save").invoke(config);
			iris.getMethod("reload").invoke(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Couldn't select the shader pack through Iris", e);
		}
	}
}
