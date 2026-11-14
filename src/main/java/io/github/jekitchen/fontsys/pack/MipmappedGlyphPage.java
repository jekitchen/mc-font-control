package io.github.jekitchen.fontsys.pack;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.nio.file.Path;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Dumpable;
import net.minecraft.client.renderer.texture.DynamicAtlasTree;
import net.minecraft.client.renderer.texture.DynamicAtlasTreeSlot;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * One mipmapped glyph atlas page for system-font glyphs (PRD D50), which in-world text draws
 * from; GUI text keeps drawing the same glyphs from vanilla's pages ({@link MipmappedSheetGlyph}).
 * It mirrors vanilla's {@code FontTexture} (a 256x256 RGBA page, drawn with the same
 * {@code text*} render types, D30), with three differences:
 *
 * <ul>
 *   <li>The texture has {@link GlyphMipmaps#LEVELS} mip levels, filled on the CPU per glyph
 *       through Blaze3D's {@code CommandEncoder.writeToTexture(texture, image, mipLevel, ...)},
 *       the same call vanilla's block atlas uses. No OpenGL or Vulkan calls of our own.</li>
 *   <li>Glyphs sit in blocks aligned to the smallest level, with transparent margins
 *       ({@link GlyphMipmaps}), instead of 1 px apart.</li>
 *   <li>The texture's own sampler, which in-world text uses (signs, name tags, text displays),
 *       minifies trilinearly ({@code LINEAR} with mipmaps) and magnifies {@code NEAREST} as
 *       vanilla does, so close-up text is unchanged.</li>
 * </ul>
 */
final class MipmappedGlyphPage extends AbstractTexture implements Dumpable {
	static final int SIZE = 256;
	/** Vanilla's {@code FontTexture} inset, which keeps the sample inside the glyph's texels. */
	private static final float INSET = 0.01F;

	private final GlyphRenderTypes renderTypes;
	/** Free space, in blocks of {@link GlyphMipmaps#BLOCK} texels. */
	private final DynamicAtlasTree blocks = new DynamicAtlasTree(0, 0, SIZE / GlyphMipmaps.BLOCK, SIZE / GlyphMipmaps.BLOCK);

	MipmappedGlyphPage(Identifier name) {
		GpuDevice device = RenderSystem.getDevice();
		int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING;
		this.texture = device.createTexture(name::toString, usage, GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, GlyphMipmaps.LEVELS);
		this.textureView = device.createTextureView(this.texture);
		this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.NEAREST, true);
		this.renderTypes = GlyphRenderTypes.createForColorTexture(name);
	}

	/**
	 * Places, renders and uploads a glyph; null if this page has no room for it.
	 *
	 * @param gui the same glyph on vanilla's page, which GUI text keeps drawing from
	 */
	@Nullable BakedSheetGlyph add(GlyphInfo info, MipmappableBitmap bitmap, BakedSheetGlyph gui) {
		int width = bitmap.getPixelWidth();
		int height = bitmap.getPixelHeight();
		int blockWidth = GlyphMipmaps.blockSize(width);
		int blockHeight = GlyphMipmaps.blockSize(height);
		DynamicAtlasTreeSlot slot = blocks.insert(blockWidth / GlyphMipmaps.BLOCK, blockHeight / GlyphMipmaps.BLOCK, 0);
		if (slot == null) {
			return null;
		}
		int blockX = slot.x() * GlyphMipmaps.BLOCK;
		int blockY = slot.y() * GlyphMipmaps.BLOCK;
		upload(bitmap, blockX, blockY, blockWidth, blockHeight);

		float x = blockX + GlyphMipmaps.MARGIN;
		float y = blockY + GlyphMipmaps.MARGIN;
		return new MipmappedSheetGlyph(
			gui, info, renderTypes, getTextureView(),
			(x + INSET) / SIZE, (x - INSET + width) / SIZE, (y + INSET) / SIZE, (y - INSET + height) / SIZE,
			bitmap.getLeft(), bitmap.getRight(), bitmap.getTop(), bitmap.getBottom());
	}

	/**
	 * Renders the glyph into its block and writes every mip level. The whole block is written,
	 * margins included, as white with alpha = coverage (D36): white everywhere means averaging
	 * texels never darkens an edge, so no premultiplication is needed.
	 */
	private void upload(MipmappableBitmap bitmap, int blockX, int blockY, int blockWidth, int blockHeight) {
		int width = bitmap.getPixelWidth();
		int height = bitmap.getPixelHeight();
		int[] coverage = new int[blockWidth * blockHeight];
		bitmap.render((px, py, c) -> {
			if (px >= 0 && px < width && py >= 0 && py < height) {
				coverage[(py + GlyphMipmaps.MARGIN) * blockWidth + px + GlyphMipmaps.MARGIN] = c;
			}
		});
		int[][] levels = GlyphMipmaps.chain(coverage, blockWidth, blockHeight, MipmappedGlyphPages.contrastBoost());
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		for (int level = 0; level < levels.length; level++) {
			int w = blockWidth >> level;
			int h = blockHeight >> level;
			try (NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false)) {
				int[] texels = levels[level];
				for (int py = 0; py < h; py++) {
					for (int px = 0; px < w; px++) {
						image.setPixelABGR(px, py, ColorAtlasGlyph.texel(texels[py * w + px]));
					}
				}
				encoder.writeToTexture(getTexture(), image, level, 0, blockX >> level, blockY >> level);
			}
		}
	}

	/** F3 + S texture dump, every level, as vanilla's {@code FontTexture} does for its one. */
	@Override
	public void dumpContents(Identifier id, Path dir) {
		if (this.texture != null) {
			TextureUtil.writeAsPNG(dir, id.toDebugFileName(), this.texture, GlyphMipmaps.LEVELS - 1, argb -> ARGB.alpha(argb) == 0 ? 0xFF000000 : argb);
		}
	}
}
