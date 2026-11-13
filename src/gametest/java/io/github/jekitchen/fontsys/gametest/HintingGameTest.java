package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.config.Hinting;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.ui.AdvancedFontScreen;
import io.github.jekitchen.fontsys.ui.ChangeSummary;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import io.github.jekitchen.fontsys.ui.KeepOrRevertScreen;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

/**
 * Hinting: Full / Light / None (PRD D53), with a well-hinted font at GUI scale 2, where one glyph
 * pixel is one screen pixel:
 *
 * <ul>
 *   <li>for each mode, every sample glyph on screen is pixel for pixel the bitmap FreeType draws
 *       with that mode's flags, opened separately here: right shape, right place, not clipped;
 *       and its advance is FreeType's advance for that mode (whole pixels for Full and Light,
 *       fractional for None);</li>
 *   <li>the three modes really draw differently;</li>
 *   <li>the Advanced screen's control changes the preview before anything is applied, on both
 *       screens, and Apply lists the change in "Keep this font?" (D47);</li>
 *   <li>with Crisp edges on, the control is grayed out, reads "Crisp" and still explains why.</li>
 * </ul>
 * Screenshots {@code d53-hinting-full}, {@code -light} and {@code -none} show the same text in
 * each mode for comparing by eye.
 */
@SuppressWarnings("UnstableApiUsage")
public class HintingGameTest implements FabricClientGameTest {
	private static final String LINE_1 = "The quick brown fox jumps over the lazy dog";
	private static final String LINE_2 = "Hamburgefonstiv 0123456789 Il1 rn m @%&";
	/** Each drawn alone in a cell, to compare with FreeType's bitmap. */
	private static final String GLYPHS = "Hamburgefonstiv0@";
	private static final int CELL = 20;
	private static final int CELL_Y = 64;
	private static final int SCALE = 2;
	/** Coverage the text shader discards: alpha under 0.1. */
	private static final int FAINT = 26;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> pick = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (pick.isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No system fonts found; skipping the hinting test.");
			return;
		}
		FaceInfo face = pick.get();
		FontSettings full = GameTestFonts.settingsFor(face);
		Map<Hinting, int[][]> shots = new EnumMap<>(Hinting.class);
		try {
			guiScale(context, SCALE);
			GameTestFonts.apply(context, full);
			shots.put(Hinting.FULL, probe(context, face, Hinting.FULL));

			chooseLightOnTheAdvancedScreen(context);
			shots.put(Hinting.LIGHT, probe(context, face, Hinting.LIGHT));

			GameTestFonts.apply(context, full.withHinting(Hinting.NONE));
			shots.put(Hinting.NONE, probe(context, face, Hinting.NONE));

			int fullLight = differingPixels(shots.get(Hinting.FULL), shots.get(Hinting.LIGHT));
			int lightNone = differingPixels(shots.get(Hinting.LIGHT), shots.get(Hinting.NONE));
			int fullNone = differingPixels(shots.get(Hinting.FULL), shots.get(Hinting.NONE));
			Fontsys.LOGGER.info("[gametest] {}: pixels differing Full/Light {}, Light/None {}, Full/None {}.",
				face.family(), fullLight, lightNone, fullNone);
			require(fullLight > 0 && lightNone > 0 && fullNone > 0,
				"the modes draw differently: Full/Light " + fullLight + ", Light/None " + lightNone + ", Full/None " + fullNone);

			grayedOutInCrispMode(context);
		} finally {
			context.setScreen(TitleScreen::new);
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
			guiScale(context, 0);
		}
	}

	/**
	 * On the Advanced screen, Hinting: Full → Light: the preview changes on that screen and on the
	 * settings screen before anything is applied, then Apply lists it in "Keep this font?", and
	 * Keep applies it.
	 */
	private static void chooseLightOnTheAdvancedScreen(ClientGameTestContext context) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		settle(context);
		int[][] settingsFull = shoot(context, "d53-settings-full");
		context.clickScreenButton("fontsys.screen.advanced");
		context.waitForScreen(AdvancedFontScreen.class);
		settle(context);
		require(hintingLabel(context).equals(label(Hinting.FULL)), "Hinting starts at Full: " + hintingLabel(context));
		int[][] advancedFull = shoot(context, "d53-advanced-full");

		ScreenRectangle button = context.computeOnClient(mc -> hintingButton(mc).getRectangle());
		click(context, button.left() + button.width() / 2, button.top() + button.height() / 2);
		require(hintingLabel(context).equals(label(Hinting.LIGHT)), "one click cycles to Light: " + hintingLabel(context));
		require(context.computeOnClient(mc -> ((AdvancedFontScreen) mc.gui.screen()).tooltipWidgets().size()) == 5,
			"Hinting has a tooltip like the other Advanced controls");
		settle(context);
		int[][] advancedLight = shoot(context, "d53-advanced-light");
		int advancedPreview = differingPixels(advancedFull, advancedLight, 0.45, 1.0, 24, 28);
		require(advancedPreview > 0, "the Advanced screen's preview shows the draft's hinting");
		require(Fontsys.controller().appliedSettings().hinting() == Hinting.FULL, "nothing is applied yet");

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(FontConfigScreen.class);
		settle(context);
		require(context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).draft().hinting()) == Hinting.LIGHT,
			"the draft keeps Light");
		require(context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).advancedLabel().getString())
			.equals(Component.translatable("fontsys.screen.advanced.changed", 1).getString()), "Advanced... counts hinting");
		int[][] settingsLight = shoot(context, "d53-settings-light");
		// Below the sliders and the Advanced button, whose label now has a count.
		int settingsPreview = differingPixels(settingsFull, settingsLight, 0.45, 1.0, 100, 30);
		require(settingsPreview > 0, "the settings screen's preview shows the draft's hinting");
		Fontsys.LOGGER.info("[gametest] Preview pixels changed by Light: Advanced {}, settings {}.", advancedPreview, settingsPreview);

		context.clickScreenButton("fontsys.screen.apply");
		context.waitForScreen(KeepOrRevertScreen.class);
		context.waitFor(mc -> mc.gui.overlay() == null);
		context.waitTicks(10);
		List<ChangeSummary.Row> changes = context.computeOnClient(mc -> ((KeepOrRevertScreen) mc.gui.screen()).changes());
		require(changes.size() == 2 && changes.get(1).equals(new ChangeSummary.Row("fontsys.confirm.change.hinting",
				new ChangeSummary.Value("fontsys.advanced.hinting.full", ""), new ChangeSummary.Value("fontsys.advanced.hinting.light", ""))),
			"Keep this font? lists Hinting: Full → Light: " + changes);
		context.takeScreenshot("d53-keep-hinting");
		context.clickScreenButton("fontsys.confirm.keep");
		context.waitFor(mc -> mc.gui.overlay() == null && !(mc.gui.screen() instanceof KeepOrRevertScreen));
		require(Fontsys.controller().appliedSettings().hinting() == Hinting.LIGHT, "Keep applies Light");
		require(PackContents.loaded().hinting() == Hinting.LIGHT, "the font was rebuilt with Light");
		context.setScreen(TitleScreen::new);
		context.waitTicks(10);
	}

	/** Crisp edges hints for monochrome output its own way, so the control steps aside and says why. */
	private static void grayedOutInCrispMode(ClientGameTestContext context) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		context.waitTicks(10);
		ScreenRectangle box = widget(context, Component.translatable("fontsys.screen.crisp").getString());
		click(context, box.left() + 5, box.top() + box.height() / 2);
		require(context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).draft().crispEdges()), "Crisp edges is on");
		// None is applied, so the button counts it: Advanced (1)...
		ScreenRectangle advanced = widget(context, Component.translatable("fontsys.screen.advanced.changed", 1).getString());
		click(context, advanced.left() + advanced.width() / 2, advanced.top() + advanced.height() / 2);
		context.waitForScreen(AdvancedFontScreen.class);
		settle(context);
		String crisp = Component.translatable("options.generic_value", Component.translatable("fontsys.advanced.hinting"),
			Component.translatable("fontsys.advanced.hinting.crisp")).getString();
		require(hintingLabel(context).equals(crisp), "Hinting reads Crisp: " + hintingLabel(context));
		require(!context.computeOnClient(mc -> hintingButton(mc).active), "Hinting is grayed out in crisp mode");
		int[][] before = shoot(context, "d53-advanced-crisp");
		ScreenRectangle button = context.computeOnClient(mc -> hintingButton(mc).getRectangle());
		context.getInput().setCursorPos((button.left() + button.width() / 2.0) * SCALE, (button.top() + button.height() / 2.0) * SCALE);
		context.waitTicks(15);
		int[][] hovered = shoot(context, "d53-advanced-crisp-tooltip");
		require(tooltipPixels(before, hovered) > 100, "the grayed-out control still explains itself in a tooltip");
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);
	}

	// ---- the probe -----------------------------------------------------------------------------

	/**
	 * Draws the sample in the applied font, screenshots it, and checks every glyph cell against
	 * FreeType's own rendering of the same font with this mode's flags.
	 */
	private static int[][] probe(ClientGameTestContext context, FaceInfo face, Hinting mode) {
		require(PackContents.providerLoaded() && PackContents.loaded().hinting() == mode, mode + " is applied");
		context.setScreen(() -> new ProbeScreen(mode));
		context.waitTicks(10);
		require(context.computeOnClient(mc -> mc.getWindow().getGuiScale()) == SCALE, "GUI scale " + SCALE);
		String name = "d53-hinting-" + mode.name().toLowerCase(Locale.ROOT);
		Path shot = context.takeScreenshot(name);
		keep(shot, name);
		int[][] pixels = read(shot);
		FontSettings applied = Fontsys.controller().appliedSettings();
		int ppem = Math.round(applied.size() * applied.renderOversample(SCALE));
		float oversample = applied.renderOversample(SCALE);
		require(oversample == SCALE, "one glyph pixel per screen pixel, oversample " + oversample);
		List<float[]> advances = context.computeOnClient(mc -> {
			List<float[]> list = new ArrayList<>();
			for (char c : GLYPHS.toCharArray()) {
				list.add(new float[] {mc.font.getSplitter().stringWidth(String.valueOf(c))});
			}
			return list;
		});
		withFace(face, ppem, ft -> {
			int[] offset = null;
			boolean fractional = false;
			for (int i = 0; i < GLYPHS.length(); i++) {
				char c = GLYPHS.charAt(i);
				Expected glyph = Expected.render(ft, c, flags(mode));
				float expectedAdvance = glyph.advance / oversample;
				require(Math.abs(advances.get(i)[0] - expectedAdvance) < 1e-4,
					mode + ": advance of '" + c + "' is " + advances.get(i)[0] + ", FreeType says " + expectedAdvance);
				fractional |= glyph.advance != Math.rint(glyph.advance);
				int penX = (8 + i * CELL) * SCALE;
				if (offset == null) {
					offset = bestOffset(pixels, glyph, penX);
				}
				int error = mismatch(pixels, glyph, penX, offset, i);
				require(error <= 6, mode + ": '" + c + "' on screen differs from FreeType's " + mode + " bitmap by up to " + error
					+ " (offset " + offset[0] + "," + offset[1] + ")");
			}
			require(offset[0] == 0, mode + ": glyphs are placed exactly at their bearing, offset " + offset[0]);
			require(fractional == (mode == Hinting.NONE), mode + (mode == Hinting.NONE ? " has fractional advances" : " has whole-pixel advances"));
		});
		context.setScreen(TitleScreen::new);
		return pixels;
	}

	/**
	 * Copies a screenshot to {@code build/reports/hinting/}, which outlives the worktree (the game
	 * runs in {@code build/run/clientGameTest}); with Iris or Sodium loaded the name says so.
	 */
	private static void keep(Path shot, String name) {
		String suffix = Fontsys.platform().isModLoaded("iris") ? "-iris" : Fontsys.platform().isModLoaded("sodium") ? "-sodium" : "";
		Path dir = Path.of(System.getProperty("user.dir")).resolve("../../reports/hinting").normalize();
		try {
			Files.createDirectories(dir);
			Files.copy(shot, dir.resolve(name + suffix + ".png"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Fontsys.LOGGER.warn("[gametest] Could not keep {}: {}", name, e.toString());
		}
	}

	/** {@code FT_Load_Glyph} flags for each mode, written out here rather than taken from the mod. */
	private static int flags(Hinting mode) {
		return switch (mode) {
			case FULL -> 0;
			case LIGHT -> 1 << 16; // FT_LOAD_TARGET_LIGHT
			case NONE -> FreeType.FT_LOAD_NO_HINTING;
		};
	}

	/** One glyph as FreeType renders it: coverage rows, bearings and advance in pixels. */
	private record Expected(int[][] coverage, int left, int top, float advance) {
		static Expected render(FT_Face face, char c, int flags) {
			int index = (int) FreeType.FT_Get_Char_Index(face, c);
			require(FreeType.FT_Load_Glyph(face, index, FreeType.FT_LOAD_RENDER | FreeType.FT_LOAD_NO_BITMAP | flags) == 0, "FreeType loads '" + c + "'");
			FT_GlyphSlot slot = face.glyph();
			FT_Bitmap bitmap = slot.bitmap();
			int rows = bitmap.rows();
			int width = bitmap.width();
			int pitch = bitmap.pitch();
			int stride = Math.abs(pitch);
			ByteBuffer buffer = rows > 0 && stride > 0 ? bitmap.buffer(stride * rows) : null;
			int[][] coverage = new int[rows][width];
			for (int y = 0; buffer != null && y < rows; y++) {
				int row = (pitch >= 0 ? y : rows - 1 - y) * stride;
				for (int x = 0; x < width; x++) {
					coverage[y][x] = buffer.get(row + x) & 0xFF;
				}
			}
			return new Expected(coverage, slot.bitmap_left(), slot.bitmap_top(), slot.advance().x() / 64.0F);
		}
	}

	/**
	 * Where the first glyph's bitmap sits on screen relative to its pen position and
	 * {@code -bitmap_top}: x should be 0; y is the line's baseline, the same for every glyph.
	 */
	private static int[] bestOffset(int[][] pixels, Expected glyph, int penX) {
		int[] best = null;
		long bestError = Long.MAX_VALUE;
		for (int dy = CELL_Y * SCALE - 20; dy <= CELL_Y * SCALE + 60; dy++) {
			for (int dx = -3; dx <= 3; dx++) {
				long error = 0;
				for (int y = 0; y < glyph.coverage.length; y++) {
					for (int x = 0; x < glyph.coverage[y].length; x++) {
						error += Math.abs(at(pixels, penX + glyph.left + x + dx, dy - glyph.top + y) - glyph.coverage[y][x]);
					}
				}
				if (error < bestError) {
					bestError = error;
					best = new int[] {dx, dy};
				}
			}
		}
		return best;
	}

	/**
	 * The largest difference between the screen and the glyph's bitmap at that offset, over the
	 * glyph's own cell: inside the bitmap the coverage must match, outside it nothing is drawn.
	 */
	private static int mismatch(int[][] pixels, Expected glyph, int penX, int[] offset, int cell) {
		int x0 = penX + glyph.left + offset[0];
		int y0 = offset[1] - glyph.top;
		int worst = 0;
		int cellLeft = penX - 4;
		int cellRight = penX + CELL * SCALE - 4;
		for (int y = offset[1] - 24 * SCALE; y < offset[1] + 10 * SCALE; y++) {
			for (int x = cellLeft; x < cellRight; x++) {
				int gy = y - y0;
				int gx = x - x0;
				boolean inside = gy >= 0 && gy < glyph.coverage.length && gx >= 0 && gx < glyph.coverage[gy].length;
				int expected = inside ? glyph.coverage[gy][gx] : 0;
				int actual = at(pixels, x, y);
				int error = Math.abs(actual - expected);
				// The text shader discards texels under 10% alpha (core/text.fsh), so faint edge pixels may be missing.
				if (expected < FAINT && actual == 0) {
					error = 0;
				}
				worst = Math.max(worst, error);
			}
		}
		return worst;
	}

	private static int at(int[][] pixels, int x, int y) {
		return y >= 0 && y < pixels.length && x >= 0 && x < pixels[y].length ? pixels[y][x] & 0xFF : 0;
	}

	/** White text on black, without a shadow, at whole GUI units. */
	private static final class ProbeScreen extends Screen {
		private final Hinting mode;

		ProbeScreen(Hinting mode) {
			super(Component.empty());
			this.mode = mode;
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			graphics.fill(0, 0, width, height, 0xFF000000);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			super.extractRenderState(graphics, mouseX, mouseY, a);
			graphics.text(font, LINE_1, 8, 8, 0xFFFFFFFF, false);
			graphics.text(font, LINE_2, 8, 24, 0xFFFFFFFF, false);
			for (int i = 0; i < GLYPHS.length(); i++) {
				graphics.text(font, String.valueOf(GLYPHS.charAt(i)), 8 + i * CELL, CELL_Y, 0xFFFFFFFF, false);
			}
			Component name = Component.translatable("options.generic_value", Component.translatable("fontsys.advanced.hinting"),
				Component.translatable(mode.key())).withStyle(s -> s.withFont(PackContents.VANILLA_FONT));
			graphics.text(font, name, 8, height - 16, 0xFF808080, false);
		}
	}

	// ---- FreeType ------------------------------------------------------------------------------

	private interface FaceBody {
		void accept(FT_Face face);
	}

	/** Opens the font file with its own FreeType library, at the size the game rasterizes it. */
	private static void withFace(FaceInfo info, int ppem, FaceBody body) {
		ByteBuffer data;
		try {
			byte[] bytes = Files.readAllBytes(Path.of(info.path()));
			data = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		long library = 0;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer ptr = stack.mallocPointer(1);
			require(FreeType.FT_Init_FreeType(ptr) == 0, "FreeType starts");
			library = ptr.get(0);
			require(FreeType.FT_New_Memory_Face(library, data, info.faceIndex(), ptr) == 0, "FreeType opens " + info.path());
			FT_Face face = FT_Face.create(ptr.get(0));
			FreeType.FT_Set_Pixel_Sizes(face, ppem, ppem);
			body.accept(face);
		} finally {
			if (library != 0) {
				FreeType.FT_Done_FreeType(library);
			}
			MemoryUtil.memFree(data);
		}
	}

	// ---- helpers -------------------------------------------------------------------------------

	private static String label(Hinting mode) {
		return Component.translatable("options.generic_value", Component.translatable("fontsys.advanced.hinting"),
			Component.translatable(mode.key())).getString();
	}

	private static AbstractWidget hintingButton(Minecraft mc) {
		String prefix = Component.translatable("fontsys.advanced.hinting").getString();
		return ((AdvancedFontScreen) mc.gui.screen()).tooltipWidgets().stream()
			.filter(w -> w.getMessage().getString().startsWith(prefix)).findFirst()
			.orElseThrow(() -> new AssertionError("no Hinting control on the Advanced screen"));
	}

	/** The current screen's control with this label. */
	private static ScreenRectangle widget(ClientGameTestContext context, String label) {
		return context.computeOnClient(mc -> mc.gui.screen().children().stream()
			.filter(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals(label))
			.map(c -> ((AbstractWidget) c).getRectangle()).findFirst()
			.orElseThrow(() -> new AssertionError("no '" + label + "' on " + mc.gui.screen())));
	}

	private static String hintingLabel(ClientGameTestContext context) {
		return context.computeOnClient(mc -> hintingButton(mc).getMessage().getString());
	}

	/** Lets the preview rebuild, with the mouse out of the way of tooltips. */
	private static void settle(ClientGameTestContext context) {
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(20);
	}

	private static void click(ClientGameTestContext context, int guiX, int guiY) {
		context.getInput().setCursorPos(guiX * SCALE + SCALE / 2.0, guiY * SCALE + SCALE / 2.0);
		context.waitTicks(3);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	/** 0 is Auto. A new GUI scale reloads an automatic-sharpness font; wait for it. */
	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(40);
		context.waitFor(mc -> mc.gui.overlay() == null, 20 * 60);
		context.waitTicks(10);
	}

	private static int[][] shoot(ClientGameTestContext context, String name) {
		return read(context.takeScreenshot(name));
	}

	/** The screenshot's pixels as 0xRRGGBB; for white-on-black text, the low byte is the coverage. */
	private static int[][] read(Path shot) {
		try (InputStream in = Files.newInputStream(shot); NativeImage image = NativeImage.read(in)) {
			int[][] pixels = new int[image.getHeight()][image.getWidth()];
			for (int y = 0; y < pixels.length; y++) {
				for (int x = 0; x < pixels[y].length; x++) {
					pixels[y][x] = image.getPixel(x, y) & 0xFFFFFF;
				}
			}
			return pixels;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static int differingPixels(int[][] a, int[][] b) {
		return differingPixels(a, b, 0, 1, 0, 0);
	}

	/**
	 * Pixels that differ between two screenshots, from {@code left} to {@code right} (fractions
	 * of the width), and from {@code top} GUI units below the top to {@code bottom} above the bottom.
	 */
	private static int differingPixels(int[][] a, int[][] b, double left, double right, int top, int bottom) {
		int count = 0;
		int height = Math.min(a.length, b.length);
		for (int y = top * SCALE; y < height - bottom * SCALE; y++) {
			int width = Math.min(a[y].length, b[y].length);
			for (int x = (int) (width * left); x < (int) (width * right); x++) {
				if (a[y][x] != b[y][x]) {
					count++;
				}
			}
		}
		return count;
	}

	/** Pixels that look like a tooltip's dark purple background now but didn't before. */
	private static int tooltipPixels(int[][] before, int[][] after) {
		int count = 0;
		for (int y = 0; y < Math.min(before.length, after.length); y++) {
			for (int x = 0; x < Math.min(before[y].length, after[y].length); x++) {
				if (tooltipLike(after[y][x]) && !tooltipLike(before[y][x])) {
					count++;
				}
			}
		}
		return count;
	}

	/** The tooltip background, 0xF0100010, over anything. */
	private static boolean tooltipLike(int rgb) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		return r <= 40 && g <= 18 && b <= 40 && Math.abs(r - b) <= 12 && r >= g + 4;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
