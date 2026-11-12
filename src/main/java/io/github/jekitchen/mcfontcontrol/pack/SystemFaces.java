package io.github.jekitchen.mcfontcontrol.pack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.util.freetype.FT_Face;

/** FreeType faces opened for system fonts (applied or preview), so render-time fixes stay scoped to ours. */
public final class SystemFaces {
	private static final Set<Long> FACES = ConcurrentHashMap.newKeySet();

	private SystemFaces() {
	}

	public static void add(FT_Face face) {
		if (face != null) {
			FACES.add(face.address());
		}
	}

	public static void remove(long address) {
		FACES.remove(address);
	}

	public static boolean contains(FT_Face face) {
		return face != null && FACES.contains(face.address());
	}
}
