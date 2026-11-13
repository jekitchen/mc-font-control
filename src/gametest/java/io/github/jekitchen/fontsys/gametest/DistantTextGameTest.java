package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.pack.GlyphMipmaps;
import io.github.jekitchen.fontsys.pack.MipmappedGlyphPages;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.joml.Matrix3x2f;

/**
 * Clearer distant in-world text (PRD D50): system-font glyphs on mipmapped atlas pages.
 *
 * <p>Renders the {@link InWorldTextGameTest} scene (signs, a text display and a name tag) at
 * 1, 8 and 20 blocks, looking straight at the oak sign from the height of its text, once with mipmapped pages switched off ("before", vanilla's atlas) and
 * once on ("after"), each from four camera positions a third of a screen pixel apart. A third
 * set of shots with the text removed isolates the text: its "ink" is the difference. Checks:
 *
 * <ul>
 *   <li>Up close (magnified), the sign text is pixel-identical before and after.</li>
 *   <li>At 20 blocks, the text shimmers less between the sub-pixel camera positions (pixels
 *       flipping on and off) and its total ink varies less: it fades evenly instead of
 *       breaking up.</li>
 *   <li>GUI text, at 1:1 and scaled down like a smaller chat, is pixel-identical before and
 *       after: GUI text always samples the full-size level.</li>
 *   <li>The atlas pages are what D50 says: 4 mip levels and a trilinear sampler for system
 *       glyphs, vanilla's single level for vanilla's own glyphs, and a GUI sampler with no
 *       mipmaps.</li>
 * </ul>
 *
 * <p>Writes the measurements and atlas memory to {@code build/reports/measurements/distant-text.txt}.
 */
@SuppressWarnings("UnstableApiUsage")
public class DistantTextGameTest implements FabricClientGameTest {
	private static final double[] DISTANCES = {1, 8, 20};
	private static final String[] LABELS = {"near", "8-blocks", "20-blocks"};
	private static final int OFFSETS = 4;
	/** Oak sign text center, aimed at from every distance. */
	private static final double TARGET_X = -0.5;
	private static final double TARGET_Y = -59.2;
	private static final double SIGN_Z = 3.5;
	/** Spectator feet height that puts the eye (1.62 above) level with the sign text. */
	private static final double CAMERA_Y = TARGET_Y - 1.62;
	/** Half-scale GUI line, in GUI units from the top. */
	private static final int HALF_SCALE_Y = 56;
	private static final String SAMPLE = "The quick brown fox 0123456789 @%&";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> pick = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (pick.isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No fonts found; skipping the distant text test.");
			return;
		}
		FontSettings settings = GameTestFonts.settingsFor(pick.get());
		String suffix = Fontsys.platform().isModLoaded("iris") ? "-iris" : Fontsys.platform().isModLoaded("sodium") ? "-sodium" : "";
		SavedOptions saved = context.computeOnClient(SavedOptions::save);
		StringBuilder report = new StringBuilder();
		report.append("font=").append(pick.get().family()).append(' ').append(pick.get().style()).append(suffix.isEmpty() ? "" : " (" + suffix.substring(1) + ")").append('\n');

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				mc.options.cloudStatus().set(CloudStatus.OFF);
				mc.options.backgroundForChatOnly().set(false);
				mc.options.textBackgroundOpacity().set(0.0);
				// Command feedback in chat would differ between shots.
				mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
			});
			InWorldTextGameTest.buildScene(world);
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand("data merge entity @e[type=text_display,limit=1] {background:0}");
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);

			Shots before = shoot(context, world, settings, false, suffix, report);
			Shots after = shoot(context, world, settings, true, suffix, report);

			// The same views with the text removed: what is left is everything but the text.
			world.getServer().runCommand("data merge block -1 -60 3 {front_text:{messages:[\"\",\"\",\"\",\"\"]}}");
			world.getServer().runCommand("data merge block 1 -60 3 {front_text:{messages:[\"\",\"\",\"\",\"\"]}}");
			world.getServer().runCommand("data merge entity @e[type=text_display,limit=1] {text:\"\"}");
			world.getServer().runCommand("data merge entity @e[type=pig,limit=1] {CustomNameVisible:0b}");
			context.waitTicks(10);
			int[][][][] blank = new int[DISTANCES.length][OFFSETS][][];
			for (int d = 0; d < DISTANCES.length; d++) {
				for (int k = 0; k < OFFSETS; k++) {
					blank[d][k] = luma(view(context, world, d, k, "d50-" + LABELS[d] + "-no-text-" + k + suffix));
				}
			}

			compare(before, after, blank, report);
		} finally {
			MipmappedGlyphPages.setEnabled(true);
			context.runOnClient(saved::restore);
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
		}
		context.waitForScreen(TitleScreen.class);
		write(report.toString());
	}

	/** Screenshots of one mode: the GUI probe and every distance and camera offset. */
	private static Shots shoot(ClientGameTestContext context, TestSingleplayerContext world, FontSettings settings, boolean mipmaps, String suffix, StringBuilder report) {
		String mode = mipmaps ? "after" : "before";
		MipmappedGlyphPages.setEnabled(mipmaps);
		GameTestFonts.apply(context, settings);
		world.getConnection().waitForChunksRender();

		Shots shots = new Shots();
		context.setScreen(ProbeScreen::new);
		context.waitTicks(5);
		shots.gui = luma(context.takeScreenshot("d50-gui-" + mode + suffix));
		shots.guiScale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		pages(context, mipmaps);
		context.setScreen(() -> null);
		context.waitTicks(10);

		shots.world = new int[DISTANCES.length][OFFSETS][][];
		for (int d = 0; d < DISTANCES.length; d++) {
			for (int k = 0; k < OFFSETS; k++) {
				String name = "d50-" + LABELS[d] + "-" + mode + (k == 0 ? "" : "-" + k) + suffix;
				shots.world[d][k] = luma(view(context, world, d, k, name));
			}
		}
		long[] atlas = atlasBytes(context);
		report.append(String.format(Locale.ROOT, "atlas %s: %.2f MB in %d pages (%.2f MB on mipmapped pages)%n", mode, atlas[0] / 1048576.0, atlas[2], atlas[1] / 1048576.0));
		return shots;
	}

	/** Moves the camera to distance {@code d}, offset {@code k} thirds of a pixel sideways, and screenshots. */
	private static Path view(ClientGameTestContext context, TestSingleplayerContext world, int d, int k, String name) {
		double distance = DISTANCES[d];
		// ~343 px per block at 1 block in a 480 px tall window with a 70 degree field of view,
		// so distance / 1000 is a third of a pixel at the target, whatever the distance.
		double x = TARGET_X + k * distance / 1000.0;
		double z = SIGN_Z - distance;
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.4f %.4f %.4f 0 0", x, CAMERA_Y, z));
		context.waitTicks(6);
		return context.takeScreenshot(name);
	}

	private static void compare(Shots before, Shots after, int[][][][] blank, StringBuilder report) {
		// GUI at 1:1 and 3/4 scale: identical. At exactly half scale every sample lands on a
		// texel boundary, so float rounding (which depends on where the glyph sits in the atlas)
		// can pick the neighboring texel: a few pixels flip, but nothing blurs.
		int split = HALF_SCALE_Y * before.guiScale;
		int width = before.gui[0].length;
		int guiDiff = differing(before.gui, after.gui, 0, 0, width, split);
		// Only the half-scale line's rows: the "Saving world" indicator fades in the bottom corner.
		int halfDiff = differing(before.gui, after.gui, 0, split, width, Math.min(split + 8 * before.guiScale, before.gui.length));
		int halfLit = lit(before.gui, split, split + 8 * before.guiScale);
		// Blur (a lower mip level or a linear filter) would turn fully lit pixels gray.
		int fullBefore = full(before.gui, split, split + 8 * before.guiScale);
		int fullAfter = full(after.gui, split, split + 8 * before.guiScale);
		report.append("gui differing pixels before vs after: ").append(guiDiff).append(" at 1:1 and 3/4 scale; ")
			.append(halfDiff).append(" of ").append(halfLit).append(" text pixels at half scale (texel-boundary rounding; fully lit ").append(fullBefore).append(" -> ").append(fullAfter).append(")\n");

		// Near: the sign text in the middle of the view is magnified, so it must not change.
		int[][] nearBefore = before.world[0][0];
		int w = nearBefore[0].length;
		int h = nearBefore.length;
		int nearDiff = differing(nearBefore, after.world[0][0], w / 4, h * 3 / 8, w * 3 / 4, h * 5 / 8);
		report.append("near (1 block) sign text differing pixels before vs after: ").append(nearDiff).append('\n');

		Metrics[] metricsBefore = new Metrics[DISTANCES.length];
		Metrics[] metricsAfter = new Metrics[DISTANCES.length];
		for (int d = 0; d < DISTANCES.length; d++) {
			metricsBefore[d] = Metrics.of(before.world[d], blank[d]);
			metricsAfter[d] = Metrics.of(after.world[d], blank[d]);
			report.append(String.format(Locale.ROOT, "%s: before %s | after %s%n", LABELS[d], metricsBefore[d], metricsAfter[d]));
		}
		Fontsys.LOGGER.info("[measurement] distant text:\n{}", report.toString().strip());

		require(guiDiff == 0, guiDiff + " GUI pixels changed; GUI text must look exactly as before");
		require(halfDiff * 20 <= halfLit, halfDiff + " of " + halfLit + " half-scale GUI text pixels changed");
		require(Math.abs(fullAfter - fullBefore) * 20 <= fullBefore, "half-scale GUI text blurred: " + fullBefore + " fully lit pixels before, " + fullAfter + " after");
		require(nearDiff == 0, nearDiff + " pixels of close-up sign text changed; magnified text must look exactly as before");
		Metrics far0 = metricsBefore[2];
		Metrics far1 = metricsAfter[2];
		require(far0.ink > 0 && far1.ink > 0, "the 20-block shots show text");
		require(far1.shimmer < far0.shimmer, "distant text should shimmer less: " + far1.shimmer + " vs " + far0.shimmer);
		require(far1.variation <= far0.variation, "distant text coverage should vary less: " + far1.variation + " vs " + far0.variation);
	}

	/**
	 * Text "ink" per camera offset (difference from the shot without text), and how steady it is:
	 * {@code shimmer} is how much of the ink changes between neighboring offsets (pixels popping
	 * in and out), {@code variation} the coefficient of variation of the total ink.
	 */
	private record Metrics(double ink, double shimmer, double variation) {
		/** Differences up to this (of 765, three channels) are rendering noise, not text. */
		private static final int NOISE = 6;

		static Metrics of(int[][][] shots, int[][][] blank) {
			int n = shots.length;
			int[][][] ink = new int[n][][];
			double[] totals = new double[n];
			for (int k = 0; k < n; k++) {
				ink[k] = new int[shots[k].length][shots[k][0].length];
				for (int y = 0; y < ink[k].length; y++) {
					for (int x = 0; x < ink[k][y].length; x++) {
						int difference = Math.abs(shots[k][y][x] - blank[k][y][x]);
						// The sky can drift by a step or two between shots; that is not text.
						ink[k][y][x] = difference > NOISE ? difference : 0;
						totals[k] += ink[k][y][x];
					}
				}
			}
			double change = 0;
			for (int k = 0; k + 1 < n; k++) {
				for (int y = 0; y < ink[k].length; y++) {
					for (int x = 0; x < ink[k][y].length; x++) {
						change += Math.abs(ink[k][y][x] - ink[k + 1][y][x]);
					}
				}
			}
			double mean = 0;
			for (double t : totals) {
				mean += t / n;
			}
			double variance = 0;
			for (double t : totals) {
				variance += (t - mean) * (t - mean) / n;
			}
			return new Metrics(mean, mean == 0 ? 0 : change / (n - 1) / mean, mean == 0 ? 0 : Math.sqrt(variance) / mean);
		}

		@Override
		public String toString() {
			return String.format(Locale.ROOT, "ink %.0f, shimmer %.3f, ink variation %.4f", ink, shimmer, variation);
		}
	}

	/**
	 * System glyphs sit on 4-level pages with a trilinear sampler when mipmaps are on, on
	 * vanilla's 1-level pages when off; GUI text binds a sampler without mipmaps either way.
	 */
	private static void pages(ClientGameTestContext context, boolean mipmaps) {
		List<String> problems = context.computeOnClient(mc -> {
			List<String> found = new ArrayList<>();
			List<TextRenderable.Styled> glyphs = new ArrayList<>();
			mc.font.prepareText(Component.literal("Ag").getVisualOrderText(), 0.0F, 0.0F, -1, false, false, 0).visit(new Font.GlyphVisitor() {
				@Override
				public void acceptGlyph(TextRenderable.Styled glyph) {
					glyphs.add(glyph);
				}
			});
			if (glyphs.isEmpty()) {
				found.add("no glyphs prepared");
			}
			for (TextRenderable.Styled glyph : glyphs) {
				int levels = glyph.textureView().texture().getMipLevels();
				int expected = mipmaps ? GlyphMipmaps.LEVELS : 1;
				if (levels != expected) {
					found.add("glyph page has " + levels + " mip levels, expected " + expected);
				}
				TextureSetup gui = new GlyphRenderState(new Matrix3x2f(), glyph, null).textureSetup();
				GpuSampler guiSampler = gui.sampler0();
				if (guiSampler == null || guiSampler.getMaxLod().orElse(1000) != 0 || guiSampler.getMinFilter() != FilterMode.NEAREST) {
					found.add("GUI text sampler " + describe(guiSampler) + " could read lower mip levels");
				}
			}
			for (Map.Entry<Identifier, AbstractTexture> entry : textures(mc.getTextureManager()).entrySet()) {
				if (isMipmappedPage(entry.getKey())) {
					GpuSampler sampler = entry.getValue().getSampler();
					if (sampler.getMinFilter() != FilterMode.LINEAR || sampler.getMagFilter() != FilterMode.NEAREST || sampler.getMaxLod().isPresent()) {
						found.add(entry.getKey() + " sampler " + describe(sampler));
					}
				} else if (entry.getValue() instanceof FontTexture font && font.getTexture().getMipLevels() != 1) {
					found.add(entry.getKey() + " (vanilla) has " + font.getTexture().getMipLevels() + " mip levels");
				}
			}
			return found.stream().distinct().toList();
		});
		require(problems.isEmpty(), "atlas pages (mipmaps " + (mipmaps ? "on" : "off") + "): " + problems);
	}

	private static String describe(GpuSampler sampler) {
		return sampler == null ? "null" : "min " + sampler.getMinFilter() + ", mag " + sampler.getMagFilter() + ", max LOD " + sampler.getMaxLod();
	}

	/** {total bytes of glyph atlas pages, of which on mipmapped pages, page count}. */
	private static long[] atlasBytes(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			long total = 0;
			long mipmapped = 0;
			long pages = 0;
			for (Map.Entry<Identifier, AbstractTexture> entry : textures(mc.getTextureManager()).entrySet()) {
				boolean ours = isMipmappedPage(entry.getKey());
				if (!ours && !(entry.getValue() instanceof FontTexture)) {
					continue;
				}
				GpuTexture texture = entry.getValue().getTexture();
				long bytes = 0;
				for (int level = 0; level < texture.getMipLevels(); level++) {
					bytes += (long) texture.getWidth(level) * texture.getHeight(level) * texture.getFormat().componentCount();
				}
				total += bytes;
				mipmapped += ours ? bytes : 0;
				pages++;
			}
			return new long[] {total, mipmapped, pages};
		});
	}

	private static boolean isMipmappedPage(Identifier id) {
		return id.getNamespace().equals(Fontsys.MOD_ID) && id.getPath().startsWith("font_mip/");
	}

	/** Every registered texture; the game ships unobfuscated, so the field name is stable. */
	@SuppressWarnings("unchecked")
	private static Map<Identifier, AbstractTexture> textures(TextureManager manager) {
		try {
			Field field = TextureManager.class.getDeclaredField("byPath");
			field.setAccessible(true);
			return Map.copyOf((Map<Identifier, AbstractTexture>) field.get(manager));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("TextureManager.byPath not found", e);
		}
	}

	/** Pixels that differ in the rectangle. */
	private static int differing(int[][] a, int[][] b, int x0, int y0, int x1, int y1) {
		int count = 0;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				if (a[y][x] != b[y][x]) {
					count++;
				}
			}
		}
		return count;
	}

	/** Pixels in rows {@code y0} to {@code y1} that differ from the probe's background. */
	private static int lit(int[][] image, int y0, int y1) {
		int count = 0;
		int background = image[image.length - 1][image[0].length - 1];
		for (int y = y0; y < Math.min(y1, image.length); y++) {
			for (int value : image[y]) {
				if (value != background) {
					count++;
				}
			}
		}
		return count;
	}

	/** Pixels in rows {@code y0} to {@code y1} at full white (the probe's half-scale text is white). */
	private static int full(int[][] image, int y0, int y1) {
		int count = 0;
		for (int y = y0; y < Math.min(y1, image.length); y++) {
			for (int value : image[y]) {
				if (value == 3 * 255) {
					count++;
				}
			}
		}
		return count;
	}

	/** Sum of the three color channels of every pixel. */
	private static int[][] luma(Path shot) {
		try (InputStream in = Files.newInputStream(shot); NativeImage image = NativeImage.read(in)) {
			int[][] out = new int[image.getHeight()][image.getWidth()];
			for (int y = 0; y < out.length; y++) {
				for (int x = 0; x < out[y].length; x++) {
					int p = image.getPixel(x, y);
					out[y][x] = (p & 0xFF) + (p >> 8 & 0xFF) + (p >> 16 & 0xFF);
				}
			}
			return out;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(String report) {
		try {
			Path out = Path.of(System.getProperty("user.dir")).resolve("../../reports/measurements").normalize();
			Files.createDirectories(out);
			Files.writeString(out.resolve("distant-text.txt"), report);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class Shots {
		int[][] gui;
		int guiScale;
		int[][][][] world;
	}

	/** Options the test changes, restored afterwards. */
	private record SavedOptions(CloudStatus clouds, boolean backgroundForChatOnly, double textBackground, ChatVisiblity chat) {
		static SavedOptions save(Minecraft mc) {
			return new SavedOptions(mc.options.cloudStatus().get(), mc.options.backgroundForChatOnly().get(), mc.options.textBackgroundOpacity().get(), mc.options.chatVisibility().get());
		}

		void restore(Minecraft mc) {
			mc.options.cloudStatus().set(clouds);
			mc.options.backgroundForChatOnly().set(backgroundForChatOnly);
			mc.options.textBackgroundOpacity().set(textBackground);
			mc.options.chatVisibility().set(chat);
		}
	}

	/** GUI text at 1:1 and scaled down, as menus, the HUD and a smaller chat draw it. */
	private static final class ProbeScreen extends Screen {
		ProbeScreen() {
			super(Component.empty());
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			graphics.fill(0, 0, width, height, 0xFF202020);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			super.extractRenderState(graphics, mouseX, mouseY, a);
			graphics.text(font, SAMPLE, 8, 8, 0xFFFFFFFF, true);
			graphics.text(font, SAMPLE, 8, 24, 0xFF000000, false);
			float[] scales = {0.75F, 0.5F};
			int y = HALF_SCALE_Y - 16;
			for (float scale : scales) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(8, y);
				graphics.pose().scale(scale, scale);
				graphics.text(font, SAMPLE, 0, 0, 0xFFFFFFFF, true);
				graphics.pose().popMatrix();
				y += 16;
			}
		}
	}
}
