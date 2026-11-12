package io.github.jekitchen.fontsys.discovery;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.CLongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_CharMap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

/**
 * Reads face metadata with the LWJGL FreeType bindings the game bundles (never AWT; PRD
 * section 4). Owns a private FreeType library so scanning never contends with the game's
 * glyph loading. Not thread-safe: use one reader per thread, and close it.
 */
public final class FreeTypeFaceReader implements AutoCloseable {
	private static final int MAX_FACES_PER_FILE = 256;
	// FreeType counts named instances in 15 bits. macOS's SF Pro (SFNS.ttf) has 369 (widths x
	// weights), so this cap only guards against corrupt files.
	private static final int MAX_INSTANCES_PER_FACE = 1024;
	/** How many mapped characters to sample when checking a colour font for real outlines. */
	private static final int OUTLINE_SAMPLE = 128;

	private final long library;

	public FreeTypeFaceReader() throws IOException {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer lib = stack.mallocPointer(1);
			int err = FreeType.FT_Init_FreeType(lib);
			if (err != 0) {
				throw new IOException("FreeType init failed: " + err);
			}
			this.library = lib.get(0);
		}
	}

	/** Reads every face (and variable-font named instance) in a font file. */
	public List<FaceInfo> read(Path path) throws IOException {
		try (Arena arena = Arena.ofConfined(); FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
			long size = channel.size();
			if (size <= 0 || size > Integer.MAX_VALUE) {
				throw new IOException("Unsupported file size " + size);
			}
			// Mapping through an Arena unmaps deterministically when it closes, so Windows does
			// not keep the font file locked after the scan.
			MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
			return readFaces(path, segment.asByteBuffer());
		}
	}

	private List<FaceInfo> readFaces(Path path, ByteBuffer data) throws IOException {
		List<FaceInfo> result = new ArrayList<>();
		long numFaces;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FT_Face face = open(stack, data, 0);
			if (face == null) {
				throw new IOException("FreeType could not open the font");
			}
			try {
				numFaces = face.num_faces();
				addFaceOrInstances(path, data, face, 0, result);
			} finally {
				FreeType.FT_Done_Face(face);
			}
		}
		for (int i = 1; i < Math.min(numFaces, MAX_FACES_PER_FILE); i++) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				FT_Face face = open(stack, data, i);
				if (face == null) {
					continue;
				}
				try {
					addFaceOrInstances(path, data, face, i, result);
				} finally {
					FreeType.FT_Done_Face(face);
				}
			}
		}
		return result;
	}

	private void addFaceOrInstances(Path path, ByteBuffer data, FT_Face face, int faceNumber, List<FaceInfo> out) {
		int instances = (int) ((face.style_flags() >>> 16) & 0x7FFF);
		boolean variable = (face.face_flags() & FreeType.FT_FACE_FLAG_VARIATION) != 0
			|| (face.face_flags() & FreeType.FT_FACE_FLAG_MULTIPLE_MASTERS) != 0;
		if (instances == 0 || !variable) {
			FaceInfo info = describe(path, face, faceNumber);
			if (info != null) {
				out.add(info);
			}
			return;
		}
		// Variable font: list each named instance (FD-12). Instance n opens as (n << 16) | face.
		for (int n = 1; n <= Math.min(instances, MAX_INSTANCES_PER_FACE); n++) {
			int index = (n << 16) | faceNumber;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				FT_Face instance = open(stack, data, index);
				if (instance == null) {
					continue;
				}
				try {
					FaceInfo info = describe(path, instance, index);
					if (info != null) {
						out.add(info);
					}
				} finally {
					FreeType.FT_Done_Face(instance);
				}
			}
		}
	}

	private FT_Face open(MemoryStack stack, ByteBuffer data, long index) {
		PointerBuffer facePtr = stack.mallocPointer(1);
		if (FreeType.FT_New_Memory_Face(library, data, index, facePtr) != 0) {
			return null;
		}
		return FT_Face.create(facePtr.get(0));
	}

	private static FaceInfo describe(Path path, FT_Face face, int faceIndex) {
		String family = trim(face.family_nameString());
		if (family == null) {
			return null;
		}
		String style = trim(face.style_nameString());
		if (style == null) {
			style = "Regular";
		}
		long faceFlags = face.face_flags();
		long styleFlags = face.style_flags();
		boolean bold = (styleFlags & FreeType.FT_STYLE_FLAG_BOLD) != 0;
		boolean italic = StyleNames.isItalic(style, (styleFlags & FreeType.FT_STYLE_FLAG_ITALIC) != 0);
		boolean color = (faceFlags & FreeType.FT_FACE_FLAG_COLOR) != 0;
		boolean outlines = hasTable(face, "glyf") || hasTable(face, "CFF ") || hasTable(face, "CFF2");
		if (outlines && color && (hasTable(face, "sbix") || hasTable(face, "CBDT"))) {
			// Bitmap colour fonts can carry a glyf table of empty placeholders (Apple Color Emoji's
			// have two points each), so the table alone doesn't prove the font draws in grayscale.
			outlines = hasDrawableOutlines(face);
		}
		String format = FreeType.FT_Get_Font_Format(face);
		boolean renderable = "TrueType".equals(format) || "CFF".equals(format);

		boolean unicode = false;
		int maps = face.num_charmaps();
		PointerBuffer charmaps = face.charmaps();
		for (int i = 0; charmaps != null && i < maps; i++) {
			if (FT_CharMap.create(charmaps.get(i)).encoding() == FreeType.FT_ENCODING_UNICODE) {
				unicode = true;
			}
		}
		boolean latin = unicode
			&& FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE) == 0
			&& FreeType.FT_Get_Char_Index(face, 'A') != 0
			&& FreeType.FT_Get_Char_Index(face, 'a') != 0
			&& FreeType.FT_Get_Char_Index(face, '0') != 0;

		// macOS marks fonts meant only for the system with a leading dot (".SF Arabic",
		// ".LastResort"); Font Book and the font menus never list them, and neither do we.
		Visibility visibility = family.startsWith(".")
			? Visibility.SYSTEM_PRIVATE
			: Visibility.classify(outlines, unicode, latin, color, renderable);
		return new FaceInfo(path.toString(), faceIndex, family, style, bold, italic, format == null ? "" : format, visibility);
	}

	/**
	 * True if most of the first {@link #OUTLINE_SAMPLE} mapped, non-blank characters have an
	 * outline that can enclose area (three or more points), ignoring embedded bitmaps. A majority
	 * rather than any: Apple Color Emoji has real outlines only for its keycap bases (#, *, 0-9).
	 */
	private static boolean hasDrawableOutlines(FT_Face face) {
		if (FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE) != 0) {
			return false;
		}
		int drawn = 0;
		int real = 0;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer glyph = stack.mallocInt(1);
			long c = FreeType.FT_Get_First_Char(face, glyph);
			for (int i = 0; i < OUTLINE_SAMPLE && glyph.get(0) != 0; i++) {
				if (FreeType.FT_Load_Glyph(face, glyph.get(0), FreeType.FT_LOAD_NO_BITMAP | FreeType.FT_LOAD_NO_SCALE) == 0
					&& face.glyph().format() == FreeType.FT_GLYPH_FORMAT_OUTLINE) {
					int points = face.glyph().outline().n_points();
					if (points > 0) {
						drawn++;
						if (points >= 3) {
							real++;
						}
					}
				}
				c = FreeType.FT_Get_Next_Char(face, c, glyph);
			}
		}
		return real * 2 > drawn;
	}

	private static boolean hasTable(FT_Face face, String tag) {
		long t = ((long) tag.charAt(0) << 24) | ((long) tag.charAt(1) << 16) | ((long) tag.charAt(2) << 8) | tag.charAt(3);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			CLongBuffer length = stack.callocCLong(1);
			return FreeType.FT_Load_Sfnt_Table(face, t, 0, null, length) == 0 && length.get(0) > 0;
		}
	}

	private static String trim(String s) {
		if (s == null) {
			return null;
		}
		String t = s.strip();
		return t.isEmpty() ? null : t;
	}

	@Override
	public void close() {
		FreeType.FT_Done_FreeType(library);
	}
}
