package io.github.jekitchen.fontsys.pack;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4fc;

/**
 * A system-font glyph that lives on two atlas pages (PRD D50): vanilla's page, exactly where
 * 1.1.0 put it, for GUI text, and a mipmapped page for in-world text.
 *
 * <p>Why two: vanilla draws a glyph's quad with texture coordinates inset by 0.01 texel, so
 * where a glyph sits at a half-pixel position (common with system fonts' fractional advances)
 * or is scaled to half size, some screen pixels sample exactly on the border between two
 * texels. Which texel wins then depends on float rounding of the texture coordinates, and so on
 * where the glyph sits in its atlas. Moving glyphs to other pages therefore flipped a few GUI
 * pixels (6 on Mesa at 1:1 and 3/4 scale, 25 of 721 on Windows at half scale). Keeping every
 * glyph on vanilla's pages, in the same order, keeps GUI text bit-identical on every GPU.
 *
 * <p>Vanilla's two callers keep the halves apart:
 *
 * <ul>
 *   <li>GUI text ({@code GlyphRenderState}) binds {@link TextRenderable#textureView()} and
 *       {@link TextRenderable#guiPipeline()}, then calls {@code render(..., flat = true)}: all
 *       from the vanilla-page glyph.</li>
 *   <li>In-world text ({@code TextFeatureRenderer}, {@code GizmoFeatureRenderer}) binds
 *       {@link TextRenderable#renderType}, whose texture is the mipmapped page, then calls
 *       {@code render(..., flat = false)}: all from this glyph's own fields.</li>
 * </ul>
 *
 * <p>Underlines and strikethroughs ({@link #createEffect}) stay on vanilla's page; they are drawn
 * from vanilla's white glyph anyway.
 */
final class MipmappedSheetGlyph extends BakedSheetGlyph {
	private final BakedSheetGlyph gui;

	MipmappedSheetGlyph(BakedSheetGlyph gui, GlyphInfo info, GlyphRenderTypes renderTypes, GpuTextureView textureView,
		float u0, float u1, float v0, float v1, float left, float right, float up, float down) {
		super(info, renderTypes, textureView, u0, u1, v0, v1, left, right, up, down);
		this.gui = gui;
	}

	@Override
	public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
		return new Instance(
			gui.createGlyph(x, y, color, shadowColor, style, boldOffset, shadowOffset),
			super.createGlyph(x, y, color, shadowColor, style, boldOffset, shadowOffset));
	}

	@Override
	public TextRenderable createEffect(float x0, float y0, float x1, float y1, float depth, int color, int shadowColor, float shadowOffset) {
		return gui.createEffect(x0, y0, x1, y1, depth, color, shadowColor, shadowOffset);
	}

	/** One drawn glyph: the GUI half and the in-world half. */
	record Instance(TextRenderable.Styled gui, TextRenderable.Styled world) implements TextRenderable.Styled {
		@Override
		public void render(Matrix4fc pose, VertexConsumer buffer, int packedLightCoords, boolean flat) {
			(flat ? gui : world).render(pose, buffer, packedLightCoords, flat);
		}

		@Override
		public RenderType renderType(Font.DisplayMode displayMode) {
			return world.renderType(displayMode);
		}

		@Override
		public GpuTextureView textureView() {
			return gui.textureView();
		}

		@Override
		public RenderPipeline guiPipeline() {
			return gui.guiPipeline();
		}

		@Override
		public float left() {
			return gui.left();
		}

		@Override
		public float top() {
			return gui.top();
		}

		@Override
		public float right() {
			return gui.right();
		}

		@Override
		public float bottom() {
			return gui.bottom();
		}

		@Override
		public float activeLeft() {
			return gui.activeLeft();
		}

		@Override
		public float activeTop() {
			return gui.activeTop();
		}

		@Override
		public float activeRight() {
			return gui.activeRight();
		}

		@Override
		public float activeBottom() {
			return gui.activeBottom();
		}

		@Override
		public Style style() {
			return gui.style();
		}
	}
}
