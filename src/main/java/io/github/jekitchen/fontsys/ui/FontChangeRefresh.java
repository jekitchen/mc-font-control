package io.github.jekitchen.fontsys.ui;

import io.github.jekitchen.fontsys.Fontsys;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Refits text labels on screens the player returns to after the font changed (PRD UI-18, D42).
 *
 * <p>When the player applies a font from a screen opened over another one (Fontsys Settings in
 * Options > Language > Font Settings, say), the screens underneath were laid out in the old
 * font. Since 26.x, coming back to a screen only repositions its widgets ({@code init()} runs
 * once per screen), so a {@link StringWidget} sized to its text in a narrower font keeps that
 * width and cuts the text short ("Font Setti..."). Rebuilding such screens isn't safe: their
 * layouts are built once and would gain a second copy of every widget. Instead, a label that
 * was sized to fit its text when the screen was built is resized to fit it in the new font
 * just before vanilla repositions the layout, which then centers it again.
 */
public final class FontChangeRefresh {
	/** The font generation each screen was last laid out in. Weak: screens come and go. */
	private static final Map<Screen, Integer> LAID_OUT = Collections.synchronizedMap(new WeakHashMap<>());
	/** Whether each label was exactly as wide as its text when its screen was first built. */
	private static final Map<StringWidget, Boolean> FITTED = Collections.synchronizedMap(new WeakHashMap<>());

	private FontChangeRefresh() {
	}

	/** At the start of {@code Screen.init(int, int)}: refits the labels if the font changed since the last layout. */
	public static void beforeInit(Screen screen) {
		if (Fontsys.controller() == null) {
			return;
		}
		int now = Fontsys.controller().fontGeneration();
		Integer before = LAID_OUT.put(screen, now);
		if (before == null || before == now) {
			return;
		}
		var font = Minecraft.getInstance().font;
		for (StringWidget label : labels(screen)) {
			// Labels with a maximum width measure themselves again when their text is set.
			label.setMessage(label.getMessage());
			if (Boolean.TRUE.equals(FITTED.get(label))) {
				label.setWidth(font.width(label.getMessage()));
			}
		}
	}

	/** After {@code Screen.init(int, int)}: notes which new labels are sized to their text. */
	public static void afterInit(Screen screen) {
		var font = Minecraft.getInstance().font;
		for (StringWidget label : labels(screen)) {
			FITTED.computeIfAbsent(label, l -> l.getWidth() == font.width(l.getMessage()));
		}
	}

	private static List<StringWidget> labels(ContainerEventHandler parent) {
		List<StringWidget> result = new ArrayList<>();
		for (GuiEventListener child : parent.children()) {
			if (child instanceof StringWidget label) {
				result.add(label);
			} else if (child instanceof ContainerEventHandler container) {
				result.addAll(labels(container));
			}
		}
		return result;
	}
}
