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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * "Keep this font?" (PRD 5.7, D13). The question, countdown and buttons are drawn in the
 * vanilla font so the dialog stays readable whatever was applied. Below the question, what the
 * change changes: the font, from and to, and each setting that differs (D47), also in the
 * vanilla font. Then samples in the new font, with every setting applied, on the backgrounds text appears on in the game: a menu,
 * chat, a sign and a tooltip (UI-11). Enter or Keep confirms; Esc, Revert or the countdown
 * reverts. The countdown itself runs in {@link FontController#tick}, on the wall clock, so it
 * continues if this screen is replaced or the window loses focus.
 */
public final class KeepOrRevertScreen extends Screen {
	private static final int PAD = 5;
	private static final int GAP = 6;
	/** Line height of the change summary, in the vanilla font. */
	private static final int SUMMARY_LINE = 10;
	private static final int LABEL_COLOR = 0xFFA0A0A0;
	/** Space between the summary's labels and values. */
	private static final int LABEL_GAP = 6;

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
		// The change summary and the samples, centered together between the title and the countdown.
		ChangesLayout changes = layoutChanges();
		int lead = changes == null ? 0 : changes.height() + GAP;
		int blockTop = extractSamples(graphics, 26, height - 50, lead);
		if (changes != null) {
			extractChanges(graphics, changes, blockTop);
		}
	}

	/** What this change changes (UI-11, D47); empty if the settings it replaced are gone. */
	public List<ChangeSummary.Row> changes() {
		FontSettings before = Fontsys.controller().pendingSettings();
		return before == null ? List.of() : ChangeSummary.rows(before, Fontsys.controller().appliedSettings());
	}

	/** The change summary laid out: a label per row, its value split into lines, and the panel height. */
	private record ChangesLayout(List<Component> labels, List<List<FormattedCharSequence>> values, int labelWidth, int height) {
	}

	/**
	 * Lays out the changes as "label: old → new" rows, in the vanilla font so they are readable
	 * whatever was applied. Values too long for one line wrap. Null when there is nothing to list.
	 */
	private @Nullable ChangesLayout layoutChanges() {
		List<ChangeSummary.Row> rows = changes();
		if (rows.isEmpty()) {
			return null;
		}
		List<Component> labels = rows.stream().map(KeepOrRevertScreen::label).toList();
		int labelWidth = labels.stream().mapToInt(font::width).max().orElse(0);
		int valueWidth = Math.max(40, boxWidth() - 2 * PAD - labelWidth - LABEL_GAP);
		List<List<FormattedCharSequence>> values = rows.stream().map(row -> font.split(value(row), valueWidth)).toList();
		int lines = values.stream().mapToInt(List::size).sum();
		return new ChangesLayout(labels, values, labelWidth, lines * SUMMARY_LINE + 2 * PAD - 1);
	}

	private void extractChanges(GuiGraphicsExtractor graphics, ChangesLayout layout, int top) {
		int boxWidth = boxWidth();
		int left = (width - boxWidth) / 2;
		int valueX = left + PAD + layout.labelWidth() + LABEL_GAP;
		graphics.fill(left, top, left + boxWidth, top + layout.height(), 0xE0181818);
		graphics.outline(left, top, boxWidth, layout.height(), 0xFF555555);
		int y = top + PAD;
		for (int i = 0; i < layout.labels().size(); i++) {
			graphics.text(font, layout.labels().get(i), left + PAD, y, LABEL_COLOR, false);
			for (FormattedCharSequence line : layout.values().get(i)) {
				graphics.text(font, line, valueX, y, 0xFFFFFFFF, true);
				y += SUMMARY_LINE;
			}
		}
	}

	private static Component label(ChangeSummary.Row row) {
		return VanillaText.translatable("fontsys.confirm.change.label", Component.translatable(row.labelKey()));
	}

	/** "old → new" with the old value gray, or just the value when it didn't change or had none. */
	private static Component value(ChangeSummary.Row row) {
		MutableComponent to = text(row.to());
		if (row.from() == null || !row.changed()) {
			return VanillaText.of(to);
		}
		return VanillaText.of(Component.empty()
			.append(text(row.from()).withStyle(ChatFormatting.GRAY))
			.append(Component.translatable("fontsys.confirm.change.arrow").withStyle(ChatFormatting.GRAY))
			.append(to));
	}

	private static MutableComponent text(ChangeSummary.Value value) {
		return value.key() == null ? Component.literal(value.text()) : Component.translatable(value.key(), value.text());
	}

	/** Narrators read the changes after the title. */
	@Override
	public Component getNarrationMessage() {
		List<Component> parts = new ArrayList<>();
		parts.add(title);
		for (ChangeSummary.Row row : changes()) {
			parts.add(Component.empty().append(label(row)).append(" ").append(value(row)));
		}
		return CommonComponents.joinForNarration(parts.toArray(Component[]::new));
	}

	private int boxWidth() {
		return Math.min(width - 32, 400);
	}

	/**
	 * The samples, top to bottom in {@code [top, bottom)}: a menu panel with a sentence and the
	 * characters that look alike in poor fonts, then chat, sign and tooltip swatches, wrapped
	 * into rows. {@link #font} is the game font, so this is the applied font. {@code lead} pixels
	 * are kept free above them for the change summary, and the two are centered together.
	 *
	 * @return the top of the centered block, where the change summary goes
	 */
	private int extractSamples(GuiGraphicsExtractor graphics, int top, int bottom, int lead) {
		FontSettings applied = Fontsys.controller().appliedSettings();
		int line = applied.isActive() ? Math.max(10, (int) Math.ceil(applied.size()) + 3) : 10;
		int boxWidth = boxWidth();
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
		int blockTop = top + Math.max(0, (bottom - top - lead - total) / 2);
		int y = blockTop + lead;

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
		return blockTop;
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
