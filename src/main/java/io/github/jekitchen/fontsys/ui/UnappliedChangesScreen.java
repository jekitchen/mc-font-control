package io.github.jekitchen.fontsys.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * "Apply your changes?" (PRD UI-13), shown when the settings screen is closed with changes
 * that haven't been applied. Apply applies them (then the usual keep-or-revert dialog) and
 * leaves; Discard changes leaves without applying; Back, or Esc, returns to the settings.
 */
public final class UnappliedChangesScreen extends Screen {
	private final FontConfigScreen settings;

	/** @param settings the settings screen to return to or close */
	UnappliedChangesScreen(FontConfigScreen settings) {
		super(Component.translatable("fontsys.unapplied.title"));
		this.settings = settings;
	}

	@Override
	protected void init() {
		int buttonWidth = 100;
		int y = height / 2 + 10;
		int x = width / 2 - (3 * buttonWidth + 8) / 2;
		addRenderableWidget(Button.builder(Component.translatable("fontsys.unapplied.apply"), b -> settings.applyAndClose())
			.bounds(x, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("fontsys.unapplied.discard"), b -> settings.discardAndClose())
			.bounds(x + buttonWidth + 4, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
			.bounds(x + 2 * (buttonWidth + 4), y, buttonWidth, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, height / 2 - 30, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("fontsys.unapplied.message"), width / 2, height / 2 - 14, 0xFFA0A0A0);
	}

	/** Esc goes back to the settings, never discards. */
	@Override
	public void onClose() {
		minecraft.gui.setScreen(settings);
	}
}
