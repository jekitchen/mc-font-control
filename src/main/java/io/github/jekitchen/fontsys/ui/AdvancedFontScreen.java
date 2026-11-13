package io.github.jekitchen.fontsys.ui;

import io.github.jekitchen.fontsys.config.FontSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Advanced Font Settings (PRD UI-19, D52): the fine-tuning settings most fonts don't need,
 * opened from the settings screen's Advanced... button.
 *
 * <p>It edits the settings screen's draft directly and applies nothing itself: Done and Esc go
 * back with the changes kept, and Apply there (or Done's "Apply your changes?" prompt, UI-13)
 * applies them, so nothing done here can be lost or applied by surprise. The preview is the
 * settings screen's own, laid out to show the shifts against a vanilla button.
 *
 * <p><b>Adding a setting:</b> add one control in {@link #init()} with {@link #row(AbstractWidget)}
 * (full column width, 20 high; give a {@code Checkbox} {@code .maxWidth(column)}), edit the draft
 * with {@link #change(FontSettings)}, give it a tooltip with {@link #tip}, and add a line to
 * {@link AdvancedSettings} so the Advanced... button counts it and Reset to defaults resets it.
 * Lang keys go in the {@code fontsys.advanced.*} block; also list the setting in
 * {@link ChangeSummary} so the keep-or-revert dialog shows it.
 */
public final class AdvancedFontScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int TOP = 22;
	private static final int ROW = 24;

	private final FontConfigScreen settings;
	/** Widgets given a tooltip in {@link #init()}; read by the game tests. */
	private final List<AbstractWidget> tooltipped = new ArrayList<>();
	/** Set by {@link #onClose()}: going back to the settings, which keep showing the preview. */
	private boolean returning;
	private int column;
	private int nextY;
	private int noteY;
	private int previewX0;
	private int previewX1;
	private int previewY1;
	private Button resetButton;
	private Button doneButton;

	AdvancedFontScreen(FontConfigScreen settings) {
		super(Component.translatable("fontsys.advanced.title"));
		this.settings = settings;
	}

	@Override
	protected void init() {
		// The same columns as the settings screen, so the preview stays where it was.
		column = Math.max(120, width * 2 / 5 - MARGIN);
		int bottom = height - 28;
		nextY = TOP;
		tooltipped.clear();
		FontSettings draft = settings.draft();

		row(tip(new ValueSlider(MARGIN, 0, column, -FontSettings.MAX_SHIFT, FontSettings.MAX_SHIFT, 0.5, draft.shiftX(),
			v -> Component.translatable("fontsys.screen.shift_x", format(v)),
			v -> setShift((float) v, settings.draft().shiftY())), "fontsys.screen.shift_x.tooltip"));
		row(tip(new ValueSlider(MARGIN, 0, column, -FontSettings.MAX_SHIFT, FontSettings.MAX_SHIFT, 0.5, draft.shiftY(),
			v -> Component.translatable("fontsys.screen.shift_y", format(v)),
			v -> setShift(settings.draft().shiftX(), (float) v)), "fontsys.screen.shift_y.tooltip"));
		row(tip(Checkbox.builder(Component.translatable("fontsys.screen.apply_to_uniform"), font)
			.maxWidth(column)
			.selected(draft.applyToUniform())
			.onValueChange((box, value) -> change(settings.draft().withApplyToUniform(value)))
			.build(), "fontsys.screen.apply_to_uniform.tooltip"));
		noteY = nextY + 2;

		previewX0 = MARGIN + column + MARGIN;
		previewX1 = width - MARGIN;
		previewY1 = bottom;

		// [Reset to defaults]                [Done]
		Component resetLabel = Component.translatable("fontsys.advanced.reset");
		resetButton = addRenderableWidget(tip(Button.builder(resetLabel, b -> resetToDefaults())
			.bounds(MARGIN, bottom + 4, Math.max(column, font.width(resetLabel) + 12), 20).build(), "fontsys.advanced.reset.tooltip"));
		int doneWidth = Math.max(80, font.width(CommonComponents.GUI_DONE) + 12);
		doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(width - MARGIN - doneWidth, bottom + 4, doneWidth, 20).build());
		updateButtons();
	}

	/** Puts a control on the next row of the left column; a taller one (a wrapped checkbox label) gets more room. */
	private <T extends AbstractWidget> T row(T widget) {
		widget.setPosition(MARGIN, nextY + Math.max(0, (20 - widget.getHeight()) / 2));
		nextY += Math.max(ROW, widget.getHeight() + 4);
		return addRenderableWidget(widget);
	}

	/** Sets the tooltip after the widget is built: 26.3's {@code Checkbox.Builder} drops it for one-line labels (D40). */
	private <T extends AbstractWidget> T tip(T widget, String key) {
		widget.setTooltip(Tooltip.create(Component.translatable(key)));
		tooltipped.add(widget);
		return widget;
	}

	/** The widgets that have a tooltip; read by the game tests. */
	public List<AbstractWidget> tooltipWidgets() {
		return List.copyOf(tooltipped);
	}

	private void setShift(float shiftX, float shiftY) {
		FontSettings draft = settings.draft();
		change(draft.withLayout(draft.size(), draft.oversample(), shiftX, shiftY));
	}

	/** Every control edits the settings screen's draft through here. */
	private void change(FontSettings draft) {
		settings.setDraft(draft);
		updateButtons();
	}

	private void resetToDefaults() {
		settings.setDraft(AdvancedSettings.reset(settings.draft()));
		// Rebuilt so every control shows its default; Reset is now disabled, so keyboard focus goes to Done.
		rebuildWidgets();
		if (minecraft.getLastInputType().isKeyboard()) {
			setInitialFocus(doneButton);
		}
	}

	private void updateButtons() {
		if (resetButton != null) {
			resetButton.active = !AdvancedSettings.changedLabelKeys(settings.draft()).isEmpty();
		}
	}

	@Override
	public void tick() {
		settings.preview().tick(settings.draft());
	}

	/** Done and Esc: back to the settings, with the changes kept there, not yet applied. */
	@Override
	public void onClose() {
		returning = true;
		minecraft.gui.setScreen(settings);
	}

	@Override
	public void removed() {
		// Left some other way than back to the settings: free the preview and name fonts (D14).
		if (!returning) {
			settings.releaseFonts();
		}
		returning = false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
		if (noteY + font.lineHeight <= previewY1) {
			graphics.textWithWordWrap(font, Component.translatable("fontsys.advanced.note"), MARGIN, noteY, column, 0xFFA0A0A0);
		}
		settings.preview().extract(graphics, font, settings.draft(), PreviewPanel.Layout.ADVANCED,
			previewX0, TOP, previewX1, previewY1, mouseX, mouseY);
	}

	private static String format(double v) {
		return String.format(Locale.ROOT, v == Math.rint(v) ? "%.0f" : "%.1f", v);
	}
}
