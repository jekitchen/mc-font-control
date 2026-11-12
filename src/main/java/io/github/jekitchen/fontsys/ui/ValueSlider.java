package io.github.jekitchen.fontsys.ui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** A slider over a clamped, stepped range (PRD UI-4). */
final class ValueSlider extends AbstractSliderButton {
	private final double min;
	private final double max;
	private final double step;
	private final DoubleFunction<Component> label;
	private final DoubleConsumer onChange;

	ValueSlider(int x, int y, int width, double min, double max, double step, double initial, DoubleFunction<Component> label, DoubleConsumer onChange) {
		super(x, y, width, 20, Component.empty(), (Mth.clamp(initial, min, max) - min) / (max - min));
		this.min = min;
		this.max = max;
		this.step = step;
		this.label = label;
		this.onChange = onChange;
		updateMessage();
	}

	double current() {
		double raw = min + value * (max - min);
		return Mth.clamp(Math.round(raw / step) * step, min, max);
	}

	/** Redraws the label, for when it depends on more than this slider's value. */
	void refreshMessage() {
		updateMessage();
	}

	@Override
	protected void updateMessage() {
		setMessage(label.apply(current()));
	}

	@Override
	protected void applyValue() {
		onChange.accept(current());
	}
}
