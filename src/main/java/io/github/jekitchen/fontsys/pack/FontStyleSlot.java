package io.github.jekitchen.fontsys.pack;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.discovery.StyledFaces;
import java.util.Optional;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The styled fonts the runtime pack can serve next to the main one (PRD AP-8, D24). Each slot
 * is a font of its own, {@code fontsys:<key>}, holding the styled face first and then
 * vanilla's providers, and {@link StyleRouting} sends bold and italic text to it.
 */
public enum FontStyleSlot {
	BOLD("bold", true, false),
	ITALIC("italic", false, true),
	BOLD_ITALIC("bold_italic", true, true);

	private final boolean bold;
	private final boolean italic;
	private final FontDescription.Resource font;
	private final Identifier file;
	private final Identifier fileResource;
	private final Identifier jsonResource;

	FontStyleSlot(String key, boolean bold, boolean italic) {
		this.bold = bold;
		this.italic = italic;
		this.font = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, key));
		this.file = Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, "system/" + key + ".ttf");
		this.fileResource = file.withPrefix("font/");
		this.jsonResource = Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, "font/" + key + ".json");
	}

	/** The slot for text with these style flags, or null for plain text. */
	public static @Nullable FontStyleSlot of(boolean bold, boolean italic) {
		if (bold) {
			return italic ? BOLD_ITALIC : BOLD;
		}
		return italic ? ITALIC : null;
	}

	public boolean bold() {
		return bold;
	}

	public boolean italic() {
		return italic;
	}

	/** The font text in this style is drawn with. */
	public FontDescription.Resource font() {
		return font;
	}

	/** Font file id as referenced from the font JSON (relative to {@code font/}). */
	public Identifier file() {
		return file;
	}

	/** Pack path of the font file. */
	Identifier fileResource() {
		return fileResource;
	}

	/** Pack path of the font definition. */
	Identifier jsonResource() {
		return jsonResource;
	}

	/** The face this slot uses, or empty when vanilla's own styling is kept. */
	public Optional<StyledFaces.Variant> variant(StyledFaces faces) {
		return switch (this) {
			case BOLD -> Optional.of(faces.bold());
			case ITALIC -> faces.italic();
			case BOLD_ITALIC -> Optional.of(faces.boldItalic());
		};
	}

	/** How glyphs of {@code variant} are loaded and drawn in this slot. */
	public FaceSpec spec(StyledFaces.Variant variant) {
		return new FaceSpec(variant.face().faceIndex(), variant.syntheticBold(), bold, variant.italic());
	}
}
