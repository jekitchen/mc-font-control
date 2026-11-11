package io.github.jekitchen.mcfontcontrol.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Builds the font definition JSON our pack serves.
 *
 * <p>Vanilla merges {@code font/default.json} from every selected pack, with providers from
 * higher packs taking priority ({@code FontManager.prepare} / {@code apply}). So our
 * {@code default.json} only lists the system font: vanilla's own providers stay underneath
 * as the fallback for missing glyphs (AP-2), and server packs above ours still win (D11).
 */
public final class FontDefinitions {
	private FontDefinitions() {
	}

	/** One {@code ttf} provider. {@code fileId} is relative to {@code font/}. */
	public static String ttf(String fileId, float size, float oversample, float shiftX, float shiftY) {
		JsonObject provider = new JsonObject();
		provider.addProperty("type", "ttf");
		provider.addProperty("file", fileId);
		provider.addProperty("size", size);
		provider.addProperty("oversample", oversample);
		JsonArray shift = new JsonArray();
		shift.add(shiftX);
		shift.add(shiftY);
		provider.add("shift", shift);
		return wrap(provider);
	}

	/**
	 * {@code mcfontcontrol:vanilla}: a copy of vanilla's {@code default.json}. It references
	 * vanilla's glyph sets directly, so text styled with this font is always drawn in the
	 * vanilla font whatever is applied (used by the keep-or-revert dialog, D13).
	 */
	public static String vanillaCopy() {
		JsonObject space = reference("minecraft:include/space");
		JsonObject bitmap = reference("minecraft:include/default");
		JsonObject filter = new JsonObject();
		filter.addProperty("uniform", false);
		bitmap.add("filter", filter);
		JsonObject unifont = reference("minecraft:include/unifont");
		return wrap(space, bitmap, unifont);
	}

	private static JsonObject reference(String id) {
		JsonObject ref = new JsonObject();
		ref.addProperty("type", "reference");
		ref.addProperty("id", id);
		return ref;
	}

	private static String wrap(JsonObject... providers) {
		JsonArray list = new JsonArray();
		for (JsonObject p : providers) {
			list.add(p);
		}
		JsonObject root = new JsonObject();
		root.add("providers", list);
		return root.toString();
	}
}
