package io.github.jekitchen.fontsys.discovery;

import io.github.jekitchen.fontsys.Fontsys;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * The system font list. Scans on a background thread (PRD FD-5), never the render thread,
 * and caches results to {@code config/fontsys/font-index.json} (FD-6).
 */
public final class SystemFontIndex {
	public enum State { NOT_STARTED, SCANNING, READY, FAILED }

	private final Path cacheFile;
	private final Supplier<List<Path>> directories;
	private final OsFamily os;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Fontsys font scan");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private volatile State state = State.NOT_STARTED;
	private volatile FontIndex index = FontIndex.empty();
	private volatile int generation;
	private volatile FontScanner.Stats lastStats;
	private CompletableFuture<FontIndex> running;
	private List<FontFamily> familiesCache;
	private boolean familiesCacheHidden;
	private int familiesCacheGeneration = -1;

	public SystemFontIndex(Path cacheFile, OsFamily os, Supplier<List<Path>> directories) {
		this.cacheFile = cacheFile;
		this.os = os;
		this.directories = directories;
	}

	public State state() {
		return state;
	}

	public OsFamily os() {
		return os;
	}

	/** Bumped whenever the index changes, so screens know to refresh. */
	public int generation() {
		return generation;
	}

	public Optional<FontScanner.Stats> lastStats() {
		return Optional.ofNullable(lastStats);
	}

	public FontIndex index() {
		return index;
	}

	/** Families to list. Cached per generation and hidden-font setting. */
	public synchronized List<FontFamily> families(boolean showHidden) {
		if (familiesCache == null || familiesCacheGeneration != generation || familiesCacheHidden != showHidden) {
			familiesCache = FontFamily.group(index.allFaces(), showHidden);
			familiesCacheGeneration = generation;
			familiesCacheHidden = showHidden;
		}
		return familiesCache;
	}

	/**
	 * Index to use before the background scan has finished: the in-memory index if ready,
	 * otherwise the cache file. Reading the cache is fast; it never touches font files.
	 */
	public FontIndex bestAvailable() {
		FontIndex current = index;
		return current.files().isEmpty() ? FontIndex.load(cacheFile) : current;
	}

	/** Starts a scan unless one is running. Unchanged files are taken from the cache. */
	public synchronized CompletableFuture<FontIndex> scan() {
		if (running != null && !running.isDone()) {
			return running;
		}
		state = State.SCANNING;
		running = CompletableFuture.supplyAsync(this::runScan, executor);
		return running;
	}

	private FontIndex runScan() {
		FontIndex previous = index.files().isEmpty() ? FontIndex.load(cacheFile) : index;
		if (index.files().isEmpty() && !previous.files().isEmpty()) {
			// Show cached fonts straight away while the scan checks for changes.
			publish(previous);
		}
		FontScanner scanner = new FontScanner();
		try (FreeTypeFaceReader reader = new FreeTypeFaceReader()) {
			FontIndex result = scanner.scan(directories.get(), previous, reader::read);
			lastStats = scanner.lastStats();
			publish(result);
			state = State.READY;
			FontScanner.Stats s = scanner.lastStats();
			Fontsys.LOGGER.info("[{}] Font scan: {} files ({} cached, {} read, {} unreadable) in {} ms; {} families.",
				Fontsys.DISPLAY_NAME, s.files(), s.reused(), s.read(), s.failed(), s.millis(), families(false).size());
			try {
				result.save(cacheFile);
			} catch (IOException e) {
				Fontsys.LOGGER.warn("[{}] Could not write the font cache {}: {}", Fontsys.DISPLAY_NAME, cacheFile, e.getMessage());
			}
			return result;
		} catch (IOException | RuntimeException | LinkageError e) {
			state = State.FAILED;
			Fontsys.LOGGER.warn("[{}] Font scan failed: {}", Fontsys.DISPLAY_NAME, e.toString());
			return index;
		}
	}

	private void publish(FontIndex newIndex) {
		index = newIndex;
		generation++;
	}
}
