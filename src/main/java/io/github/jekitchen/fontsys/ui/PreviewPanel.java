package io.github.jekitchen.fontsys.ui;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.apply.FontController;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.PixelGrid;
import io.github.jekitchen.fontsys.discovery.StyledFaces;
import io.github.jekitchen.fontsys.pack.FontStyleSlot;
import io.github.jekitchen.fontsys.pack.RuntimeFontPackSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The live preview of the draft settings (PRD UI-5, UI-6, D4): owns the private
 * {@link PreviewFont}, rebuilds it a few ticks after the draft changes, and draws it into a
 * box. Shared by the settings screen and its Advanced screen (D52), so going between the two
 * keeps the preview font instead of loading it again.
 *
 * <ul>
 *   <li>{@link Layout#FULL}, the settings screen: pixel-font hint, size strip (UI-15), samples,
 *       and which faces bold and italic use.</li>
 *   <li>{@link Layout#ADVANCED}: text on a vanilla button, then the samples, so the shifts can
 *       be judged against a fixed frame.</li>
 * </ul>
 */
final class PreviewPanel {
	private static final int DEBOUNCE_TICKS = 3;
	/** Vanilla's button background, as {@code AbstractButton} draws it. */
	private static final Identifier BUTTON_SPRITE = Identifier.withDefaultNamespace("widget/button");

	enum Layout {
		FULL("fontsys.preview.empty"),
		ADVANCED("fontsys.advanced.preview.empty");

		private final String emptyKey;

		Layout(String emptyKey) {
			this.emptyKey = emptyKey;
		}
	}

	private final FontController controller = Fontsys.controller();
	private @Nullable PreviewFont preview;
	private @Nullable Component error;
	private boolean dirty = true;
	private int wait;
	/** Where the size strip drew each size last frame, for clicks. */
	private final List<StripHit> stripHits = new ArrayList<>();

	/** Rebuilds the preview font a few ticks from now, so dragging a slider doesn't reload it every step. */
	void markDirty() {
		dirty = true;
	}

	/** Call every tick from the screen showing the panel. */
	void tick(FontSettings draft) {
		// A resource reload closes the vanilla providers the preview shares; drop the preview
		// while the loading overlay is up and rebuild it afterwards.
		if (Minecraft.getInstance().gui.overlay() != null) {
			close();
			return;
		}
		if (dirty && ++wait >= DEBOUNCE_TICKS) {
			wait = 0;
			dirty = false;
			rebuild(draft);
		}
	}

	private void rebuild(FontSettings draft) {
		closeFont();
		error = null;
		if (draft.fontPath() == null) {
			return;
		}
		try {
			preview = PreviewFont.create(draft.sanitized(), RuntimeFontPackSource.currentGuiScale());
		} catch (IOException | RuntimeException | LinkageError e) {
			error = Component.translatable("fontsys.preview.error");
			Fontsys.LOGGER.warn("[{}] Preview of '{} {}' failed: {}", Fontsys.DISPLAY_NAME, draft.familyName(), draft.styleName(), e.toString());
		}
	}

	/** Frees the preview font (D14); it is rebuilt on the next tick that wants it. */
	void close() {
		closeFont();
		dirty = true;
	}

	private void closeFont() {
		if (preview != null) {
			preview.close();
			preview = null;
		}
	}

	/** The sizes the size strip shows now, ascending; empty before the preview is ready. */
	List<Float> stripSizes() {
		return preview == null ? List.of() : preview.stripSizes();
	}

	/** The size in the size strip at this point as last drawn, if any. */
	@Nullable Float stripSizeAt(double mouseX, double mouseY) {
		StripHit hit = stripHitAt(mouseX, mouseY);
		return hit == null ? null : hit.size();
	}

	/** The middle of a size in the size strip as last drawn, in GUI coordinates; for the game tests. */
	@Nullable ScreenPosition stripCenter(float size) {
		return stripHits.stream().filter(h -> h.size() == size).findFirst()
			.map(h -> new ScreenPosition((h.x0() + h.x1()) / 2, (h.y0() + h.y1()) / 2)).orElse(null);
	}

	private @Nullable StripHit stripHitAt(double mouseX, double mouseY) {
		for (StripHit hit : stripHits) {
			if (mouseX >= hit.x0() && mouseX < hit.x1() && mouseY >= hit.y0() && mouseY < hit.y1()) {
				return hit;
			}
		}
		return null;
	}

	private record StripHit(float size, int x0, int y0, int x1, int y1) {
	}

	// ---- Drawing -------------------------------------------------------------------------------

	/** Draws the panel in the box from (x0, y0) to (x1, y1); {@code font} is the screen's own font, for labels. */
	void extract(GuiGraphicsExtractor graphics, Font font, FontSettings draft, Layout layout,
		int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
		stripHits.clear();
		graphics.fill(x0, y0, x1, y1, 0xA0000000);
		graphics.outline(x0, y0, x1 - x0, y1 - y0, 0xFF555555);
		int x = x0 + 6;
		int w = x1 - x0 - 12;
		int top = y0 + 5;
		if (controller.conflict().isPresent()) {
			// AP-12: say plainly why nothing happens, above everything else.
			Component why = Component.translatable("fontsys.conflict.banner", controller.conflict().get().name());
			int h = font.wordWrapHeight(why, w - 8) + 8;
			graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 1 + h, 0xD0702020);
			graphics.textWithWordWrap(font, why, x + 2, y0 + 5, w - 8, 0xFFFFFFFF);
			top = y0 + h + 6;
		}
		PreviewFont current = preview;
		if (current == null || Minecraft.getInstance().gui.overlay() != null) {
			Component message = error != null ? error
				: draft.fontPath() == null ? Component.translatable(layout.emptyKey)
				: Component.translatable("fontsys.preview.loading");
			graphics.textWithWordWrap(font, message, x, top + 1, w, 0xFFA0A0A0);
			return;
		}
		Font pf = current.font();
		int line = Math.max(10, (int) Math.ceil(draft.size()) + 3);
		int y = top;
		graphics.enableScissor(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
		if (layout == Layout.FULL) {
			Component hint = pixelFontHint(current, draft);
			if (hint != null) {
				// First, so it stays visible however little room the samples leave.
				graphics.text(font, hint, x, y, 0xFFFFE080, false);
				y += font.lineHeight + 3;
			}
			y = extractSizeStrip(graphics, font, current, draft, x, y, w, mouseX, mouseY);
		} else {
			y = extractButtonSample(graphics, font, pf, x, y, w);
		}
		graphics.text(pf, Component.translatable("fontsys.preview.pangram"), x, y, 0xFFFFFFFF, true);
		y += line;
		graphics.text(pf, Component.translatable("fontsys.preview.digits"), x, y, 0xFFD0D0D0, true);
		y += line;
		graphics.text(pf, styleSample(), x, y, 0xFFFFFFFF, true);
		y += line + 3;
		// Chat line
		graphics.fill(x - 2, y - 2, x + w, y + line - 2, 0x80000000);
		graphics.text(pf, Component.translatable("fontsys.preview.chat"), x, y, 0xFFFFFFFF, true);
		y += line + 4;
		// Tooltip and sign samples side by side
		Component tooltip = Component.translatable("fontsys.preview.tooltip");
		int tw = Math.min(w / 2 - 4, pf.width(tooltip) + 8);
		graphics.fill(x - 2, y - 3, x - 2 + tw, y + line - 1, 0xF0100010);
		graphics.outline(x - 2, y - 3, tw, line + 2, 0xFF5000FF);
		graphics.text(pf, tooltip, x + 2, y, 0xFFFFFF55, true);
		Component sign = Component.translatable("fontsys.preview.sign");
		int sx = x + tw + 6;
		int sw = Math.min(x + w - sx, pf.width(sign) + 12);
		graphics.fill(sx, y - 3, sx + sw, y + line - 1, 0xFFA2824E);
		graphics.text(pf, sign, sx + 6, y, 0xFF000000, false);
		// Which faces bold and italic use, when the panel has room below the samples.
		int labelY = y + line + 3;
		if (layout == Layout.FULL && labelY + font.lineHeight <= y1 - 2) {
			current.styles().ifPresent(styles -> graphics.text(font, facesLabel(current, styles), x, labelY, 0xFFA0A0A0, false));
		}
		graphics.disableScissor();
	}

	/**
	 * A vanilla button with its label in the preview font, placed as {@code AbstractButton}
	 * places its own (centered, top at {@code (top + bottom - 9) / 2 + 1}), so Shift X and Y
	 * show against a frame whose position never changes. Beside it, when there is room, the same
	 * button in Minecraft's font, to line the two up against.
	 *
	 * @return the y below the buttons
	 */
	private static int extractButtonSample(GuiGraphicsExtractor graphics, Font font, Font pf, int x, int y, int w) {
		Component label = Component.translatable("fontsys.advanced.preview.button");
		Component vanilla = VanillaText.of(label);
		int textWidth = Math.max(pf.width(label), font.width(vanilla));
		int bw = Math.min((w - 6) / 2, Math.max(100, textWidth + 16));
		boolean both = bw >= textWidth + 8;
		if (!both) {
			bw = Math.min(w, Math.max(100, pf.width(label) + 16));
		}
		extractButton(graphics, pf, label, x, y, bw);
		if (!both) {
			return y + 26;
		}
		extractButton(graphics, font, vanilla, x + bw + 6, y, bw);
		graphics.text(font, Component.translatable("fontsys.advanced.preview.compare"), x, y + 23, 0xFFA0A0A0, false);
		return y + 23 + font.lineHeight + 4;
	}

	private static void extractButton(GuiGraphicsExtractor graphics, Font font, Component label, int x, int y, int width) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON_SPRITE, x, y, width, 20);
		int textY = (y + y + 20 - 9) / 2 + 1;
		graphics.text(font, label, x + (width - font.width(label)) / 2, textY, 0xFFFFFFFF, true);
	}

	/**
	 * The size strip (UI-15): a short sample at the next size down, the current size and the next
	 * size up, each after its size in the vanilla font. Clicking one selects that size.
	 *
	 * @return the y below the strip
	 */
	private int extractSizeStrip(GuiGraphicsExtractor graphics, Font font, PreviewFont current, FontSettings draft,
		int x, int y, int w, int mouseX, int mouseY) {
		List<Float> sizes = current.stripSizes();
		if (sizes.size() < 2) {
			return y;
		}
		Component sample = Component.translatable("fontsys.preview.size_sample");
		if (stripWidth(font, current, sizes, sample) > w) {
			sample = Component.translatable("fontsys.preview.size_sample.short");
		}
		// Vanilla places every glyph from a baseline at a fixed distance below the text's y, at any
		// size, so drawing all sizes at one y lines them up. Start low enough for the largest
		// size's ascenders (about 3/4 of the size above the baseline, which sits 7 below y).
		float largest = sizes.get(sizes.size() - 1);
		int textY = y + 2 + Math.max(0, Math.round(largest * 0.75F) - 7);
		int rowHeight = textY - y + 7 + Math.round(largest * 0.25F) + 4;
		int sx = x;
		for (float size : sizes) {
			Component label = Component.literal(ChangeSummary.format(size));
			boolean selected = size == draft.size();
			int sampleWidth = current.fontAt(size).width(sample);
			int segment = font.width(label) + 3 + sampleWidth;
			StripHit hit = new StripHit(size, sx - 2, y - 2, sx + segment + 2, y + rowHeight - 1);
			stripHits.add(hit);
			boolean hovered = !selected && stripHitAt(mouseX, mouseY) == hit;
			if (selected || hovered) {
				graphics.outline(hit.x0(), hit.y0(), hit.x1() - hit.x0(), hit.y1() - hit.y0(), selected ? 0xFF808080 : 0xFF505050);
			}
			graphics.text(font, label, sx, textY, selected ? 0xFFFFFFFF : 0xFF909090, false);
			graphics.text(current.fontAt(size), sample, sx + font.width(label) + 3, textY, 0xFFFFFFFF, true);
			if (hovered) {
				graphics.setTooltipForNextFrame(font, Component.translatable("fontsys.preview.size_strip.tooltip", ChangeSummary.format(size)), mouseX, mouseY);
			}
			sx += segment + 12;
		}
		return y + rowHeight + 3;
	}

	private static int stripWidth(Font font, PreviewFont current, List<Float> sizes, Component sample) {
		int total = 0;
		for (float size : sizes) {
			total += font.width(ChangeSummary.format(size)) + 3 + current.fontAt(size).width(sample) + 12;
		}
		return total - 12;
	}

	/**
	 * For a pixel-style font (PRD 5.5): suggests crisp mode while it's off, then a size on the
	 * font's pixel grid if the current one isn't. Never changes anything by itself.
	 */
	private static @Nullable Component pixelFontHint(PreviewFont current, FontSettings draft) {
		if (current.pixelGrid().isEmpty()) {
			return null;
		}
		int cells = current.pixelGrid().getAsInt();
		OptionalInt size = PixelGrid.suggestedSize(cells, draft.size(), FontSettings.MIN_SIZE, FontSettings.MAX_SIZE);
		boolean onGrid = size.isPresent() && size.getAsInt() == draft.size();
		if (!draft.crispEdges()) {
			return size.isPresent() && !onGrid
				? Component.translatable("fontsys.preview.pixel_font.crisp_size", size.getAsInt())
				: Component.translatable("fontsys.preview.pixel_font.crisp");
		}
		return size.isPresent() && !onGrid ? Component.translatable("fontsys.preview.pixel_font.size", size.getAsInt()) : null;
	}

	/** "Bold text  Italic text  Bold italic", each in its style (AP-8). */
	private static Component styleSample() {
		return Component.empty()
			.append(Component.translatable("fontsys.preview.bold").withStyle(ChatFormatting.BOLD))
			.append(Component.translatable("fontsys.preview.separator"))
			.append(Component.translatable("fontsys.preview.italic").withStyle(ChatFormatting.ITALIC))
			.append(Component.translatable("fontsys.preview.separator"))
			.append(Component.translatable("fontsys.preview.bold_italic").withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC));
	}

	/** Which faces bold and italic text use, e.g. "Bold: Bold · Italic: Italic" (AP-8, D24). */
	private static Component facesLabel(PreviewFont preview, StyledFaces styles) {
		Component bold = preview.hasStyledFace(FontStyleSlot.BOLD) ? faceName(styles.bold()) : Component.translatable("fontsys.preview.face.simulated");
		Component italic = preview.hasStyledFace(FontStyleSlot.ITALIC) && styles.italic().isPresent()
			? faceName(styles.italic().get())
			: Component.translatable("fontsys.preview.face.slanted");
		return Component.translatable("fontsys.preview.faces", bold, italic);
	}

	private static Component faceName(StyledFaces.Variant variant) {
		Component style = Component.literal(variant.face().style());
		return variant.syntheticBold() ? Component.translatable("fontsys.preview.face.emboldened", style) : style;
	}
}
