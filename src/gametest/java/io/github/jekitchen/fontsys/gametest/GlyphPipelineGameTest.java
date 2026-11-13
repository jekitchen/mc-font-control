package io.github.jekitchen.fontsys.gametest;

import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * System-font glyphs draw with the same pipelines as vanilla's own font (PRD D30).
 *
 * <p>Vanilla puts FreeType glyphs in a grayscale atlas and draws them with the
 * {@code text_grayscale*} pipelines. Iris 1.11.7 has no shader-pack program for
 * {@code text_grayscale_polygon_offset}, the one used for sign text, so with a shader pack on,
 * signs drew as streaks across the screen. Fontsys glyphs go in the color atlas instead, which
 * every shader mod handles because vanilla's own font uses it.
 */
@SuppressWarnings("UnstableApiUsage")
public class GlyphPipelineGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> face = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (face.isEmpty()) {
			return;
		}
		GameTestFonts.apply(context, GameTestFonts.settingsFor(face.get()));
		try {
			Component text = Component.literal("Sign text Ag ")
				.append(Component.literal("bold ").withStyle(ChatFormatting.BOLD))
				.append(Component.literal("italic").withStyle(ChatFormatting.ITALIC));
			List<String> wrong = context.computeOnClient(mc -> {
				List<TextRenderable.Styled> glyphs = new ArrayList<>();
				mc.font.prepareText(text.getVisualOrderText(), 0.0F, 0.0F, -1, false, false, 0)
					.visit(new Font.GlyphVisitor() {
						@Override
						public void acceptGlyph(TextRenderable.Styled glyph) {
							glyphs.add(glyph);
						}
					});
				List<String> problems = new ArrayList<>();
				if (glyphs.isEmpty()) {
					problems.add("no glyphs prepared");
				}
				for (TextRenderable.Styled glyph : glyphs) {
					check(problems, glyph.renderType(Font.DisplayMode.NORMAL).pipeline(), RenderPipelines.TEXT);
					check(problems, glyph.renderType(Font.DisplayMode.SEE_THROUGH).pipeline(), RenderPipelines.TEXT_SEE_THROUGH);
					check(problems, glyph.renderType(Font.DisplayMode.POLYGON_OFFSET).pipeline(), RenderPipelines.TEXT_POLYGON_OFFSET);
					check(problems, glyph.guiPipeline(), RenderPipelines.GUI_TEXT);
				}
				return problems.stream().distinct().toList();
			});
			if (!wrong.isEmpty()) {
				throw new AssertionError("System-font glyphs use unexpected pipelines: " + wrong);
			}
		} finally {
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
		}
	}

	private static void check(List<String> problems, Object actual, Object expected) {
		if (actual != expected) {
			problems.add(actual + " instead of " + expected);
		}
	}
}
