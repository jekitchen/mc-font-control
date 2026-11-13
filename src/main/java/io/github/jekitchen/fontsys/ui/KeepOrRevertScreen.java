package io.github.jekitchen.fontsys.ui;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.apply.FontController;
import io.github.jekitchen.fontsys.config.FontSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * "Keep this font?" (PRD 5.7, D13). The question, countdown and buttons are drawn in the
 * vanilla font so the dialog stays readable whatever was applied. Between them, samples in the
 * new font, with every setting applied, on the backgrounds text appears on in the game: a menu,
 * chat, a sign and a tooltip (UI-11). Enter or Keep confirms; Esc, Revert or the countdown
 * reverts. The countdown itself runs in {@link FontController#tick}, on the wall clock, so it
 * continues if this screen is replaced or the window loses focus.
 */
public final class KeepOrRevertScreen extends Screen {
	private static final int PAD = 5;
	private static final int GAP = 6;

	private final @Nullable Screen returnTo;

	public KeepOrRevertScreen(@Nullable Screen returnTo) {
		super(VanillaText.translatable("fontsys.confirm.title"));
		this.returnTo = returnTo;
	}

	public @Nullable Screen returnTo() {
		return returnTo;
	}

	@Override
	protected void init() {
		int buttonWidth = 120;
		int y = height - 30;
		addRenderableWidget(Button.builder(VanillaText.translatable("fontsys.confirm.keep"), b -> keep())
			.bounds(width / 2 - buttonWidth - 5, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(VanillaText.translatable("fontsys.confirm.revert"), b -> revert())
			.bounds(width / 2 + 5, y, buttonWidth, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
		int seconds = Fontsys.controller().secondsLeft();
		graphics.centeredText(font, VanillaText.translatable("fontsys.confirm.countdown", seconds), width / 2, height - 44, 0xFFFFFF55);
		extractSamples(graphics, 26, height - 50);
	}

	/**
	 * The samples, top to bottom in {@code [top, bottom)}: a menu panel with a sentence and the
	 * characters that look alike in poor fonts, then chat, sign and tooltip swatches, wrapped
	 * into rows. {@link #font} is the game font, so this is the applied font.
	 */
	private void extractSamples(GuiGraphicsExtractor graphics, int top, int bottom) {
		FontSettings applied = Fontsys.controller().appliedSettings();
		int line = applied.isActive() ? Math.max(10, (int) Math.ceil(applied.size()) + 3) : 10;
		int boxWidth = Math.min(width - 32, 400);
		int left = (width - boxWidth) / 2;

		// Lay out first, so the samples can be centered in the space they have.
		List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.translatable("fontsys.confirm.sample"), boxWidth - 2 * PAD));
		lines.addAll(font.split(characters(), boxWidth - 2 * PAD));
		int panelHeight = lines.size() * line + 2 * PAD - 3;
		List<Swatch> swatches = List.of(
			new Swatch(Component.translatable("fontsys.preview.chat"), 0x80000000, 0, 0xFFFFFFFF, true),
			new Swatch(Component.translatable("fontsys.preview.sign"), 0xFFA2824E, 0, 0xFF000000, false),
			new Swatch(Component.translatable("fontsys.preview.tooltip"), 0xF0100010, 0xFF5000FF, 0xFFFFFF55, true));
		List<List<Swatch>> rows = new ArrayList<>();
		List<Swatch> row = new ArrayList<>();
		int rowWidth = 0;
		for (Swatch swatch : swatches) {
			int w = swatchWidth(swatch, boxWidth);
			if (!row.isEmpty() && rowWidth + GAP + w > boxWidth) {
				rows.add(row);
				row = new ArrayList<>();
				rowWidth = 0;
			}
			rowWidth += (row.isEmpty() ? 0 : GAP) + w;
			row.add(swatch);
		}
		rows.add(row);
		int swatchHeight = line + 2 * PAD - 3;
		int total = panelHeight + rows.size() * (GAP + swatchHeight);
		int y = top + Math.max(0, (bottom - top - total) / 2);

		graphics.enableScissor(0, top, width, bottom);
		// Menu text: white with a shadow on a dark panel.
		graphics.fill(left, y, left + boxWidth, y + panelHeight, 0xE0181818);
		graphics.outline(left, y, boxWidth, panelHeight, 0xFF555555);
		int textY = y + PAD;
		for (FormattedCharSequence text : lines) {
			graphics.text(font, text, left + PAD, textY, 0xFFFFFFFF, true);
			textY += line;
		}
		// Chat, sign and tooltip, as many to a row as fit.
		y += panelHeight + GAP;
		for (List<Swatch> r : rows) {
			extractRow(graphics, r, y, swatchHeight, boxWidth);
			y += swatchHeight + GAP;
		}
		graphics.disableScissor();
	}

	/** "Il1 O0 rn m · 0123456789 · Bold · Italic": look-alike letters, digits, and the bold and italic faces. */
	private static Component characters() {
		return Component.translatable("fontsys.confirm.characters")
			.append(Component.translatable("fontsys.confirm.separator"))
			.append(Component.translatable("fontsys.confirm.bold").withStyle(ChatFormatting.BOLD))
			.append(Component.translatable("fontsys.confirm.separator"))
			.append(Component.translatable("fontsys.confirm.italic").withStyle(ChatFormatting.ITALIC));
	}

	private int swatchWidth(Swatch swatch, int max) {
		return Math.min(max, font.width(swatch.text()) + 2 * PAD);
	}

	private void extractRow(GuiGraphicsExtractor graphics, List<Swatch> row, int y, int h, int boxWidth) {
		int rowWidth = (row.size() - 1) * GAP;
		for (Swatch swatch : row) {
			rowWidth += swatchWidth(swatch, boxWidth);
		}
		int x = (width - rowWidth) / 2;
		for (Swatch swatch : row) {
			int w = swatchWidth(swatch, boxWidth);
			graphics.fill(x, y, x + w, y + h, swatch.background());
			if (swatch.border() != 0) {
				graphics.outline(x, y, w, h, swatch.border());
			}
			graphics.enableScissor(x, y, x + w, y + h);
			graphics.text(font, swatch.text(), x + PAD, y + PAD, swatch.color(), swatch.shadow());
			graphics.disableScissor();
			x += w + GAP;
		}
	}

	private record Swatch(Component text, int background, int border, int color, boolean shadow) {
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			keep();
			return true;
		}
		return super.keyPressed(event);
	}

	/** Esc reverts (5.7). */
	@Override
	public void onClose() {
		revert();
	}

	private void keep() {
		Fontsys.controller().confirm();
		minecraft.gui.setScreen(returnTo);
	}

	private void revert() {
		minecraft.gui.setScreen(returnTo);
		Fontsys.controller().revert();
	}
}
