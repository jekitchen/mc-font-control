package io.github.jekitchen.mcfontcontrol.ui;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.apply.FontController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * "Keep this font?" (PRD 5.7, D13). Every string is drawn in the vanilla font so the dialog
 * stays readable whatever was applied. Enter or Keep confirms; Esc, Revert or the countdown
 * reverts. The countdown itself runs in {@link FontController#tick}, on the wall clock, so it
 * continues if this screen is replaced or the window loses focus.
 */
public final class KeepOrRevertScreen extends Screen {
	private final @Nullable Screen returnTo;

	public KeepOrRevertScreen(@Nullable Screen returnTo) {
		super(VanillaText.translatable("mcfontcontrol.confirm.title"));
		this.returnTo = returnTo;
	}

	public @Nullable Screen returnTo() {
		return returnTo;
	}

	@Override
	protected void init() {
		int buttonWidth = 120;
		int y = height / 2 + 10;
		addRenderableWidget(Button.builder(VanillaText.translatable("mcfontcontrol.confirm.keep"), b -> keep())
			.bounds(width / 2 - buttonWidth - 5, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(VanillaText.translatable("mcfontcontrol.confirm.revert"), b -> revert())
			.bounds(width / 2 + 5, y, buttonWidth, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, height / 2 - 30, 0xFFFFFFFF);
		int seconds = McFontControl.controller().secondsLeft();
		graphics.centeredText(font, VanillaText.translatable("mcfontcontrol.confirm.countdown", seconds), width / 2, height / 2 - 14, 0xFFFFFF55);
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
		McFontControl.controller().confirm();
		minecraft.gui.setScreen(returnTo);
	}

	private void revert() {
		minecraft.gui.setScreen(returnTo);
		McFontControl.controller().revert();
	}
}
