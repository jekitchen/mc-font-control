package io.github.jekitchen.fontsys.pack;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.FontIndex;
import io.github.jekitchen.fontsys.discovery.StyledFaces;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * Decides what the runtime pack contains for given settings, and remembers what was loaded
 * so mixins, style routing and the GUI-scale watcher can read it.
 */
public final class PackContents {
	/** Font id the selected face is served under; the file lives at {@code font/system/selected.ttf}. */
	public static final Identifier FONT_FILE = Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, "system/selected.ttf");
	/** Always vanilla glyphs, whatever is applied (see {@link FontDefinitions#vanillaCopy()}). */
	public static final FontDescription VANILLA_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, "vanilla"));
	/** Vanilla's Unicode font, which uses the system font too when {@code applyToUniform} is on (AP-3). */
	static final FontDescription UNIFORM_FONT = new FontDescription.Resource(Identifier.withDefaultNamespace("uniform"));

	private static final Identifier DEFAULT_JSON = Identifier.withDefaultNamespace("font/default.json");
	private static final Identifier UNIFORM_JSON = Identifier.withDefaultNamespace("font/uniform.json");
	private static final Identifier VANILLA_JSON = Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, "font/vanilla.json");
	private static final Identifier FONT_FILE_RESOURCE = FONT_FILE.withPrefix("font/");

	/**
	 * What the last build put in the pack.
	 *
	 * @param files    every font file served, with how it is opened and drawn
	 * @param styles   the face used for each styled slot (AP-8); a missing slot keeps vanilla's styling
	 * @param servedBy the font each styled slot is drawn with: itself, or {@link FontStyleSlot#BOLD}
	 *                 when bold italic uses the same face as bold (then vanilla adds the slant)
	 * @param uniform  true if {@code uniform.json} uses the system font too
	 * @param autoOversample the oversample follows the GUI scale, so a GUI-scale change reloads the font
	 * @param crisp    glyphs are rendered with hard edges (AP-10, D34)
	 */
	public record Loaded(boolean active, int faceIndex, int guiScale, boolean autoOversample, boolean uniform, boolean crisp,
		Map<Identifier, FaceSpec> files, Map<FontStyleSlot, StyledFaces.Variant> styles, Map<FontStyleSlot, FontStyleSlot> servedBy) {
		static Loaded inactive(int guiScale) {
			return new Loaded(false, 0, guiScale, false, false, false, Map.of(), Map.of(), Map.of());
		}
	}

	private static volatile Loaded loaded = Loaded.inactive(0);
	private static final Set<Identifier> loadedFiles = ConcurrentHashMap.newKeySet();
	private static final Set<String> warned = new HashSet<>();

	private PackContents() {
	}

	public static Loaded loaded() {
		return loaded;
	}

	/** True once vanilla has loaded the selected face since the last pack build. */
	public static boolean providerLoaded() {
		return loadedFiles.contains(FONT_FILE);
	}

	/** True once vanilla has loaded this font file since the last pack build. */
	public static boolean providerLoaded(Identifier fileId) {
		return loadedFiles.contains(fileId);
	}

	/** Called by the provider mixin when vanilla finishes loading one of our font files. */
	public static void markProviderLoaded(Identifier fileId) {
		loadedFiles.add(fileId);
	}

	/** True for the font files our pack serves; mixins only touch these (D1). */
	public static boolean isSystemFont(Identifier fileId) {
		return loaded.files().containsKey(fileId);
	}

	/** How one of our font files is opened; plain face 0 for anything else. */
	public static FaceSpec specFor(Identifier fileId) {
		return loaded.files().getOrDefault(fileId, FaceSpec.plain(0));
	}

	/**
	 * The font to draw text in {@code style} with: one of our styled fonts for bold or italic
	 * text in the system font, if it has been loaded, otherwise {@code font} unchanged (AP-8).
	 */
	static FontDescription route(FontDescription font, boolean bold, boolean italic) {
		Loaded current = loaded;
		if (!current.active() || !(FontDescription.DEFAULT.equals(font) || current.uniform() && UNIFORM_FONT.equals(font))) {
			return font;
		}
		FontStyleSlot slot = FontStyleSlot.of(bold, italic);
		FontStyleSlot served = slot == null ? null : current.servedBy().get(slot);
		if (served == null || !loadedFiles.contains(served.file())) {
			return font;
		}
		return served.font();
	}

	/**
	 * Fills the pack for these settings.
	 *
	 * @param stepAside true while another font mod is loaded (AP-12): serve no system font
	 * @param guiScale  current GUI scale, used when oversample is automatic
	 */
	public static void build(RuntimeFontPack.Builder pack, FontSettings settings, boolean stepAside, int guiScale, Supplier<FontIndex> index) {
		loadedFiles.clear();
		pack.json(VANILLA_JSON, FontDefinitions.vanillaCopy());
		if (stepAside || !settings.isActive()) {
			loaded = Loaded.inactive(guiScale);
			return;
		}
		FontResolver.Result result = FontResolver.resolve(settings, index);
		if (result.font().isEmpty()) {
			warnOnce(settings.fontPath() + "#" + settings.faceIndex(), result.problem());
			loaded = Loaded.inactive(guiScale);
			return;
		}
		FontResolver.Resolved font = result.font().get();
		if (font.moved()) {
			warnOnce("moved:" + font.path(), "The font '" + font.family() + " " + font.style() + "' moved; using it from its new location.");
		}
		boolean auto = settings.followsGuiScale();
		float oversample = settings.renderOversample(guiScale);
		float shiftX = settings.renderShift(settings.shiftX(), guiScale);
		float shiftY = settings.renderShift(settings.shiftY(), guiScale);
		String json = FontDefinitions.ttf(FONT_FILE.toString(), settings.size(), oversample, shiftX, shiftY);
		pack.file(FONT_FILE_RESOURCE, font.path());
		pack.json(DEFAULT_JSON, json);
		if (settings.applyToUniform()) {
			pack.json(UNIFORM_JSON, json);
		}

		Map<Identifier, FaceSpec> files = new HashMap<>();
		files.put(FONT_FILE, FaceSpec.plain(font.faceIndex()));
		Map<FontStyleSlot, StyledFaces.Variant> styles = new EnumMap<>(FontStyleSlot.class);
		Map<FontStyleSlot, FontStyleSlot> servedBy = new EnumMap<>(FontStyleSlot.class);
		StyledFaces styled = StyledFaces.of(family(font.face(), index), font.face());
		for (FontStyleSlot slot : FontStyleSlot.values()) {
			Optional<StyledFaces.Variant> variant = slot.variant(styled);
			if (variant.isEmpty() || !Files.isReadable(Path.of(variant.get().face().path()))) {
				continue;
			}
			styles.put(slot, variant.get());
			FontStyleSlot same = sharedSlot(slot, variant.get(), styles);
			if (same != null) {
				// Same face and drawing as bold: don't load the font file a second time.
				servedBy.put(slot, same);
				continue;
			}
			pack.file(slot.fileResource(), Path.of(variant.get().face().path()));
			pack.json(slot.jsonResource(), FontDefinitions.styled(slot.file().toString(), settings.size(), oversample, shiftX, shiftY));
			files.put(slot.file(), slot.spec(variant.get()));
			servedBy.put(slot, slot);
		}
		loaded = new Loaded(true, font.faceIndex(), guiScale, auto, settings.applyToUniform(), settings.crispEdges(), Map.copyOf(files), Map.copyOf(styles), Map.copyOf(servedBy));
	}

	/**
	 * {@link FontStyleSlot#BOLD} if {@code slot} is bold italic and would load exactly the bold
	 * font (same face, same emboldening, no italic of its own), otherwise null.
	 */
	public static FontStyleSlot sharedSlot(FontStyleSlot slot, StyledFaces.Variant variant, Map<FontStyleSlot, StyledFaces.Variant> chosen) {
		if (slot != FontStyleSlot.BOLD_ITALIC || !variant.equals(chosen.get(FontStyleSlot.BOLD))) {
			return null;
		}
		return FontStyleSlot.BOLD.spec(variant).equals(slot.spec(variant)) ? FontStyleSlot.BOLD : null;
	}

	/**
	 * The selected face's family from the font index, for finding its bold and italic faces.
	 * Same name and same source, so an instance font never borrows a system font's bold (FD-14).
	 */
	public static Optional<FontFamily> family(FaceInfo face, Supplier<FontIndex> index) {
		return FontFamily.group(index.get().allFaces(), true).stream()
			.filter(f -> f.holds(face.family(), face.path()))
			.findFirst();
	}

	/** The styled faces for a selected face, as {@link #build} chooses them; used by the preview. */
	public static StyledFaces styledFaces(FaceInfo face, Supplier<FontIndex> index) {
		return StyledFaces.of(family(face, index), face);
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
		Fontsys.LOGGER.warn("[{}] {}", Fontsys.DISPLAY_NAME, message);
	}
}
