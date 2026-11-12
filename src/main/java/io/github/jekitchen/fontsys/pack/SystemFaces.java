package io.github.jekitchen.fontsys.pack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.util.freetype.FT_Face;

/**
 * FreeType faces opened for system fonts (applied or preview), so render-time fixes stay
 * scoped to ours, and the subset FreeType emboldens because the family has no heavier face
 * (PRD AP-8), and the subset rendered with hard edges in crisp mode (AP-10, D34).
 */
public final class SystemFaces {
	private static final Set<Long> FACES = ConcurrentHashMap.newKeySet();
	private static final Set<Long> SYNTHETIC_BOLD = ConcurrentHashMap.newKeySet();
	private static final Set<Long> CRISP = ConcurrentHashMap.newKeySet();

	private SystemFaces() {
	}

	public static void add(FT_Face face, boolean syntheticBold, boolean crisp) {
		if (face != null) {
			FACES.add(face.address());
			if (syntheticBold) {
				SYNTHETIC_BOLD.add(face.address());
			}
			if (crisp) {
				CRISP.add(face.address());
			}
		}
	}

	public static void remove(long address) {
		FACES.remove(address);
		SYNTHETIC_BOLD.remove(address);
		CRISP.remove(address);
	}

	public static boolean contains(FT_Face face) {
		return face != null && FACES.contains(face.address());
	}

	public static boolean isSyntheticBold(FT_Face face) {
		return face != null && SYNTHETIC_BOLD.contains(face.address());
	}

	public static boolean isCrisp(FT_Face face) {
		return face != null && CRISP.contains(face.address());
	}
}
