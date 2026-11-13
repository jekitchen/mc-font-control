package io.github.jekitchen.fontsys.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * The sizes the preview shows side by side (PRD UI-15): the next size down, the current size
 * and the next size up, so players can compare before they move the slider.
 */
public final class SizeStrip {
	private SizeStrip() {
	}

	/**
	 * One size step either side of {@code size}, within {@code [min, max]}, in ascending order.
	 * With crisp edges on and a pixel font ({@code cells} present), the neighbors are the
	 * nearest sizes that keep every design pixel square, the same sizes the pixel-font hint
	 * suggests ({@code PixelGrid}): a multiple of the grid, or half of it.
	 */
	public static List<Float> sizes(float size, OptionalInt cells, boolean crisp, float min, float max) {
		Float below = null;
		Float above = null;
		if (crisp && cells.isPresent()) {
			int grid = cells.getAsInt();
			for (int s = (int) Math.ceil(min); s <= max; s++) {
				if (s % grid != 0 && s * 2 != grid) {
					continue;
				}
				if (s < size) {
					below = (float) s;
				} else if (s > size && above == null) {
					above = (float) s;
				}
			}
		} else {
			below = size - 1 >= min ? size - 1 : null;
			above = size + 1 <= max ? size + 1 : null;
		}
		List<Float> result = new ArrayList<>(3);
		if (below != null) {
			result.add(below);
		}
		result.add(size);
		if (above != null) {
			result.add(above);
		}
		return result;
	}
}
