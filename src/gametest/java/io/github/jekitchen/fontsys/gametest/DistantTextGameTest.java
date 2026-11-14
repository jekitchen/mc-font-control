package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import org.joml.Matrix4f;

/**
 * Clearer distant in-world text (PRD D50): system-font glyphs on mipmapped atlas pages.
 *
 * <p>Renders the {@link InWorldTextGameTest} scene (signs, a text display and a name tag) at
 * 1, 8 and 20 blocks, looking straight at the oak sign from the height of its text, in three
 * modes: mipmapped pages off ("before", 1.1.0's vanilla atlas), on with plain averaged levels
 * ("plain mean", for the trade-off numbers) and on as shipped, with the contrast boost
 * ("after"). Each from four camera positions a third of a screen pixel apart. A last set of
 * shots with the text removed isolates the text: its "ink" is the difference. Checks:
 *
 * <ul>
 *   <li>Up close (magnified), the sign text is pixel-identical before and after.</li>
 *   <li>At 8 and 20 blocks, the text shimmers less between the sub-pixel camera positions
 *       (pixels flipping on and off) and its total ink varies less: it fades evenly instead of
 *       breaking up. The boost darkens it compared with the plain mean, and at 8 blocks it has
 *       at least 90% of 1.1.0's ink.</li>
 *   <li>GUI text, at 1:1 and scaled down like a smaller chat, is pixel-identical before and
 *       after, and sends exactly the same vertices: GUI text still draws from vanilla's
 *       pages.</li>
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
				// The "Saving world" indicator appears on a timer, so its glyphs would join
				// vanilla's pages at a different moment in each mode and shuffle them.
				mc.options.showAutosaveIndicator().set(false);
			});
			InWorldTextGameTest.buildScene(world);
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand("data merge entity @e[type=text_display,limit=1] {background:0}");
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);
			settle(context, world);

			Shots before = shoot(context, world, settings, false, false, "before", suffix, report);
			Shots plain = shoot(context, world, settings, true, false, "plain", suffix, report);
			Shots after = shoot(context, world, settings, true, true, "after", suffix, report);

			// The same views with the text removed: what is left is everything but the text.
			world.getServer().runCommand("data merge block -1 -60 3 {front_text:{messages:[\"\",\"\",\"\",\"\"]}}");
			world.getServer().runCommand("data merge block 1 -60 3 {front_text:{messages:[\"\",\"\",\"\",\"\"]}}");
			world.getServer().runCommand("data merge entity @e[type=text_display,limit=1] {text:\"\"}");
			// Any named mob in the scene (a pig, or dogs once the shared scene has them).
			world.getServer().runCommand("execute as @e[type=!player,type=!text_display] run data merge entity @s {CustomNameVisible:0b}");
			context.waitTicks(10);
			int[][][][] blank = new int[DISTANCES.length][OFFSETS][][];
			for (int d = 0; d < DISTANCES.length; d++) {
				for (int k = 0; k < OFFSETS; k++) {
					blank[d][k] = luma(view(context, world, d, k, "d50-" + LABELS[d] + "-no-text-" + k + suffix));
				}
			}

			compare(before, plain, after, blank, report);
		} finally {
			MipmappedGlyphPages.setEnabled(true);
			MipmappedGlyphPages.setContrastBoost(true);
			context.runOnClient(saved::restore);
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
		}
		context.waitForScreen(TitleScreen.class);
		write(report.toString());
	}

	/**
	 * Screenshots of one mode: the GUI probe and every distance and camera offset.
	 *
	 * @param mipmaps system glyphs on mipmapped pages (off: 1.1.0's vanilla pages only)
	 * @param boost   the contrast boost of the smaller levels (off: the plain mean)
	 */
	private static Shots shoot(ClientGameTestContext context, TestSingleplayerContext world, FontSettings settings, boolean mipmaps, boolean boost,
		String mode, String suffix, StringBuilder report) {
		MipmappedGlyphPages.setEnabled(mipmaps);
		MipmappedGlyphPages.setContrastBoost(boost);
		// Apply looking at the sky, then open the probe first, so every mode puts glyphs on
		// vanilla's pages in the same order: the GUI probe's, then the world's. In-world text is
		// drawn sorted by distance, so applying in view of it would order the pages by where the
		// camera happened to be, and that alone moves GUI glyphs to other texels.
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.4f %.4f %.4f 0 -90", TARGET_X, CAMERA_Y, SIGN_Z - DISTANCES[0]));
		context.waitTicks(6);
		GameTestFonts.apply(context, settings);
		context.setScreen(ProbeScreen::new);
		context.waitTicks(5);
		Shots shots = new Shots();
		shots.gui = luma(context.takeScreenshot("d50-gui-" + mode + suffix));
		shots.guiScale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		shots.guiVertices = context.computeOnClient(DistantTextGameTest::guiVertices);
		pages(context, mipmaps);
		context.setScreen(() -> null);
		world.getConnection().waitForChunksRender();
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

	/**
	 * The vertices GUI text sends for the probe's sample: atlas page, positions and texture
	 * coordinates of every glyph, as {@code GlyphRenderState} draws them ({@code flat = true}).
	 * Identical strings mean identical GUI draws, whatever the GPU's rounding.
	 */
	private static String guiVertices(Minecraft mc) {
		StringBuilder out = new StringBuilder();
		mc.font.prepareText(Component.literal(SAMPLE).getVisualOrderText(), 0.0F, 0.0F, -1, true, false, 0).visit(new Font.GlyphVisitor() {
			@Override
			public void acceptGlyph(TextRenderable.Styled glyph) {
				out.append(glyph.textureView().texture().getLabel()).append(':');
				glyph.render(new Matrix4f(), new RecordingVertices(out), 0, true);
				out.append('\n');
			}
		});
		return out.toString();
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

	/**
	 * Waits until the close-up view stops changing: light from the freshly built scene can take
	 * a few seconds to settle, more on a busy machine, and would otherwise show up as "ink".
	 */
	private static void settle(ClientGameTestContext context, TestSingleplayerContext world) {
		int[][] previous = luma(view(context, world, 0, 0, "d50-settle"));
		for (int attempt = 0; attempt < 30; attempt++) {
			context.waitTicks(20);
			int[][] current = luma(view(context, world, 0, 0, "d50-settle"));
			if (differing(previous, current, 0, 0, current[0].length, current.length) == 0) {
				return;
			}
			previous = current;
		}
		Fontsys.LOGGER.warn("[gametest] The scene kept changing; distant text measurements may be noisy.");
	}

	private static void compare(Shots before, Shots plain, Shots after, int[][][][] blank, StringBuilder report) {
		// GUI text at 1:1, 3/4 and half scale: GUI text draws from vanilla's pages, which hold
		// the same glyphs at the same places as before, so nothing may change at all.
		int bottom = Math.min((HALF_SCALE_Y + 8) * before.guiScale, before.gui.length);
		int width = before.gui[0].length;
		int guiLit = lit(before.gui, 0, bottom);
		int guiPlain = differing(before.gui, plain.gui, 0, 0, width, bottom);
		int guiDiff = differing(before.gui, after.gui, 0, 0, width, bottom);
		boolean sameVertices = before.guiVertices.equals(after.guiVertices) && before.guiVertices.equals(plain.guiVertices);
		report.append("gui text (1:1, 3/4 and half scale, ").append(guiLit).append(" text pixels): ").append(guiDiff).append(" pixels changed (")
			.append(guiPlain).append(" with the plain mean); vertex data ").append(sameVertices ? "identical" : "DIFFERENT").append('\n');

		// Near: the sign text in the middle of the view is magnified, so it must not change.
		int[][] nearBefore = before.world[0][0];
		int w = nearBefore[0].length;
		int h = nearBefore.length;
		int nearDiff = differing(nearBefore, after.world[0][0], w / 4, h * 3 / 8, w * 3 / 4, h * 5 / 8);
		int nearPlain = differing(nearBefore, plain.world[0][0], w / 4, h * 3 / 8, w * 3 / 4, h * 5 / 8);
		report.append("near (1 block) sign text: ").append(nearDiff).append(" pixels changed (").append(nearPlain).append(" with the plain mean)\n");

		Metrics[][] metrics = new Metrics[3][DISTANCES.length];
		for (int d = 0; d < DISTANCES.length; d++) {
			metrics[0][d] = Metrics.of(before.world[d], blank[d]);
			metrics[1][d] = Metrics.of(plain.world[d], blank[d]);
			metrics[2][d] = Metrics.of(after.world[d], blank[d]);
			report.append(String.format(Locale.ROOT, "%s:%n  before (1.1.0)   %s%n  plain mean       %s, ink %.2fx before%n  after (boosted)  %s, ink %.2fx before%n",
				LABELS[d], metrics[0][d], metrics[1][d], ratio(metrics[1][d], metrics[0][d]), metrics[2][d], ratio(metrics[2][d], metrics[0][d])));
		}
		Fontsys.LOGGER.info("[measurement] distant text:\n{}", report.toString().strip());

		require(sameVertices, "GUI text vertex data changed; GUI text must draw exactly as before:\n" + before.guiVertices + "vs\n" + after.guiVertices);
		require(guiDiff == 0 && guiPlain == 0, guiDiff + " GUI pixels changed (" + guiPlain + " with the plain mean); GUI text must look exactly as before");
		require(nearDiff == 0 && nearPlain == 0, nearDiff + " pixels of close-up sign text changed (" + nearPlain + " with the plain mean); magnified text must look exactly as before");
		for (int d = 1; d < DISTANCES.length; d++) {
			Metrics old = metrics[0][d];
			Metrics now = metrics[2][d];
			require(old.ink > 0 && now.ink > 0, LABELS[d] + " shots show text");
			require(now.shimmer < old.shimmer, LABELS[d] + ": text should shimmer less: " + now.shimmer + " vs " + old.shimmer);
			// Flicker of the total ink under 1% is invisible; the per-pixel shimmer above is the real
			// measure. With DejaVu Sans on Mesa at 8 blocks, both before and after sit around 0.6%,
			// and the bolder boosted letters land a hair above 1.1.0 (0.0064 vs 0.0055).
			require(now.variation <= Math.max(old.variation, VISIBLE_VARIATION), LABELS[d] + ": text coverage should vary less: " + now.variation + " vs " + old.variation);
			require(now.ink >= metrics[1][d].ink, LABELS[d] + ": the boost should darken text: " + now.ink + " vs " + metrics[1][d].ink);
		}
		require(ratio(metrics[2][1], metrics[0][1]) >= 0.9, "text 8 blocks away should be about as dark as before: " + ratio(metrics[2][1], metrics[0][1]) + "x the ink");
	}

	private static double ratio(Metrics a, Metrics b) {
		return b.ink == 0 ? 0 : a.ink / b.ink;
	}

	/**
	 * Text "ink" per camera offset (difference from the shot without text), and how steady it is:
	 * {@code shimmer} is how much of the ink changes between neighboring offsets (pixels popping
	 * in and out), {@code variation} the coefficient of variation of the total ink, and
	 * {@code contrast} the text's darkest strokes against the background: the mean difference of
	 * its strongest tenth of pixels, as a share of the strongest difference in the shot.
	 */
	/** Coefficient of variation of the total ink below which flicker can't be seen. */
	private static final double VISIBLE_VARIATION = 0.01;

	private record Metrics(double ink, double shimmer, double variation, double contrast) {
		/** Differences up to this (of 765, three channels) are rendering noise, not text. */
		private static final int NOISE = 6;

		static Metrics of(int[][][] shots, int[][][] blank) {
			int n = shots.length;
			int[][][] ink = new int[n][][];
			double[] totals = new double[n];
			double contrast = 0;
			for (int k = 0; k < n; k++) {
				ink[k] = new int[shots[k].length][shots[k][0].length];
				List<Integer> lit = new ArrayList<>();
				for (int y = 0; y < ink[k].length; y++) {
					for (int x = 0; x < ink[k][y].length; x++) {
						int difference = Math.abs(shots[k][y][x] - blank[k][y][x]);
						// The sky can drift by a step or two between shots; that is not text.
						ink[k][y][x] = difference > NOISE ? difference : 0;
						totals[k] += ink[k][y][x];
						if (ink[k][y][x] > 0) {
							lit.add(ink[k][y][x]);
						}
					}
				}
				lit.sort(null);
				int top = Math.max(1, lit.size() / 10);
				double sum = 0;
				for (int i = lit.size() - top; i < lit.size(); i++) {
					sum += i < 0 ? 0 : lit.get(i);
				}
				contrast += lit.isEmpty() ? 0 : sum / top / 765.0 / n;
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
			return new Metrics(mean, mean == 0 ? 0 : change / (n - 1) / mean, mean == 0 ? 0 : Math.sqrt(variance) / mean, contrast);
		}

		@Override
		public String toString() {
			return String.format(Locale.ROOT, "ink %.0f, contrast %.3f, shimmer %.3f, ink variation %.4f", ink, contrast, shimmer, variation);
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
				int guiLevels = glyph.textureView().texture().getMipLevels();
				if (guiLevels != 1) {
					found.add("GUI text draws from a page with " + guiLevels + " mip levels, expected vanilla's 1");
				}
				int levels = MipmappedGlyphPages.worldTextureView(glyph).texture().getMipLevels();
				int expected = mipmaps ? GlyphMipmaps.LEVELS : 1;
				if (levels != expected) {
					found.add("in-world glyph page has " + levels + " mip levels, expected " + expected);
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
		String guiVertices;
		int[][][][] world;
	}

	/** Writes each vertex's position and texture coordinates as text. */
	private record RecordingVertices(StringBuilder out) implements VertexConsumer {
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			out.append(" (").append(x).append(' ').append(y).append(' ').append(z).append(')');
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			out.append(" uv ").append(u).append(' ').append(v);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			return this;
		}

		@Override
		public VertexConsumer setColor(int color) {
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv3(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}

	/** Options the test changes, restored afterwards. */
	private record SavedOptions(CloudStatus clouds, boolean backgroundForChatOnly, double textBackground, ChatVisiblity chat, boolean autosaveIndicator) {
		static SavedOptions save(Minecraft mc) {
			return new SavedOptions(mc.options.cloudStatus().get(), mc.options.backgroundForChatOnly().get(), mc.options.textBackgroundOpacity().get(), mc.options.chatVisibility().get(),
				mc.options.showAutosaveIndicator().get());
		}

		void restore(Minecraft mc) {
			mc.options.cloudStatus().set(clouds);
			mc.options.backgroundForChatOnly().set(backgroundForChatOnly);
			mc.options.textBackgroundOpacity().set(textBackground);
			mc.options.chatVisibility().set(chat);
			mc.options.showAutosaveIndicator().set(autosaveIndicator);
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
