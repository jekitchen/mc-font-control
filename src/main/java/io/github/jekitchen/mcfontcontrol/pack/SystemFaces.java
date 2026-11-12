package io.github.jekitchen.mcfontcontrol.pack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.util.freetype.FT_Face;

/**
 * FreeType faces opened for system fonts (applied or preview), so render-time fixes stay
 * scoped to ours, and the subset FreeType emboldens because the family has no heavier face
 * (PRD AP-8).
 */
public final class SystemFaces {
	private static final Set<Long> FACES = ConcurrentHashMap.newKeySet();
	private static final Set<Long> SYNTHETIC_BOLD = ConcurrentHashMap.newKeySet();

	private SystemFaces() {
	}

	public static void add(FT_Face face, boolean syntheticBold) {
		if (face != null) {
			FACES.add(face.address());
			if (syntheticBold) {
				SYNTHETIC_BOLD.add(face.address());
			}
		}
	}

	public static void remove(long address) {
		FACES.remove(address);
		SYNTHETIC_BOLD.remove(address);
	}

	public static boolean contains(FT_Face face) {
		return face != null && FACES.contains(face.address());
	}

	public static boolean isSyntheticBold(FT_Face face) {
		return face != null && SYNTHETIC_BOLD.contains(face.address());
	}
}
