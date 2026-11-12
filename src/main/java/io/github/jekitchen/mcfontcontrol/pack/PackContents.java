package io.github.jekitchen.mcfontcontrol.pack;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.discovery.FontIndex;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * Decides what the runtime pack contains for given settings, and remembers what was loaded
 * so mixins and the GUI-scale watcher can read it.
 */
public final class PackContents {
	/** Font id the selected face is served under; the file lives at {@code font/system/selected.ttf}. */
	public static final Identifier FONT_FILE = Identifier.fromNamespaceAndPath(McFontControl.MOD_ID, "system/selected.ttf");
	/** Always vanilla glyphs, whatever is applied (see {@link FontDefinitions#vanillaCopy()}). */
	public static final FontDescription VANILLA_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(McFontControl.MOD_ID, "vanilla"));

	private static final Identifier DEFAULT_JSON = Identifier.withDefaultNamespace("font/default.json");
	private static final Identifier UNIFORM_JSON = Identifier.withDefaultNamespace("font/uniform.json");
	private static final Identifier VANILLA_JSON = Identifier.fromNamespaceAndPath(McFontControl.MOD_ID, "font/vanilla.json");
	private static final Identifier FONT_FILE_RESOURCE = FONT_FILE.withPrefix("font/");

	/** What the last build put in the pack. */
	public record Loaded(boolean active, int faceIndex, int guiScale, boolean autoOversample) {
	}

	private static volatile Loaded loaded = new Loaded(false, 0, 0, false);
	private static final Set<String> warned = new HashSet<>();

	private PackContents() {
	}

	public static Loaded loaded() {
		return loaded;
	}

	/** True for the font files our pack serves; mixins only touch these (D1). */
	public static boolean isSystemFont(Identifier fileId) {
		return FONT_FILE.equals(fileId);
	}

	/** Face index to open for one of our font files. */
	public static int faceIndexFor(Identifier fileId) {
		return isSystemFont(fileId) ? loaded.faceIndex() : 0;
	}

	/**
	 * Fills the pack for these settings.
	 *
	 * @param stepAside true while another font mod is loaded (AP-12): serve no system font
	 * @param guiScale  current GUI scale, used when oversample is automatic
	 */
	public static void build(RuntimeFontPack.Builder pack, FontSettings settings, boolean stepAside, int guiScale, Supplier<FontIndex> index) {
		pack.json(VANILLA_JSON, FontDefinitions.vanillaCopy());
		if (stepAside || !settings.isActive()) {
			loaded = new Loaded(false, 0, guiScale, false);
			return;
		}
		FontResolver.Result result = FontResolver.resolve(settings, index);
		if (result.font().isEmpty()) {
			warnOnce(settings.fontPath() + "#" + settings.faceIndex(), result.problem());
			loaded = new Loaded(false, 0, guiScale, false);
			return;
		}
		FontResolver.Resolved font = result.font().get();
		if (font.moved()) {
			warnOnce("moved:" + font.path(), "The font '" + font.family() + " " + font.style() + "' moved; using it from its new location.");
		}
		boolean auto = settings.oversample() <= 0;
		float oversample = auto ? Math.max(1, guiScale) : settings.oversample();
		String json = FontDefinitions.ttf(FONT_FILE.toString(), settings.size(), oversample, settings.shiftX(), settings.shiftY());
		pack.file(FONT_FILE_RESOURCE, font.path());
		pack.json(DEFAULT_JSON, json);
		if (settings.applyToUniform()) {
			pack.json(UNIFORM_JSON, json);
		}
		loaded = new Loaded(true, font.faceIndex(), guiScale, auto);
	}

	/** One warning per problem per session, never log spam (AP-7). */
	private static void warnOnce(String key, String message) {
		if (message == null) {
			return;
		}
		synchronized (warned) {
			if (!warned.add(key)) {
				return;
			}
		}
		McFontControl.LOGGER.warn("[{}] {}", McFontControl.DISPLAY_NAME, message);
	}
}
