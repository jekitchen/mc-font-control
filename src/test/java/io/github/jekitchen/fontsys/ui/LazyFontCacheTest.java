package io.github.jekitchen.fontsys.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

/** The font list's lazy, capped cache of name fonts (PRD D49), without the game. */
class LazyFontCacheTest {
	/** Runs submitted loads only when the test says so, like a background thread that hasn't got to them yet. */
	private static final class ManualExecutor implements Executor {
		final Queue<Runnable> tasks = new ArrayDeque<>();

		@Override
		public void execute(Runnable task) {
			tasks.add(task);
		}

		void runAll() {
			while (!tasks.isEmpty()) {
				tasks.poll().run();
			}
		}
	}

	/** A prepared font: native memory that must be freed exactly once. */
	private static final class Prepared {
		final String key;
		boolean freed;

		Prepared(String key) {
			this.key = key;
		}
	}

	/** An active font: GPU memory that must be freed exactly once. */
	private static final class Active {
		final String key;
		boolean closed;

		Active(String key) {
			this.key = key;
		}
	}

	private final ManualExecutor executor = new ManualExecutor();
	private final List<String> loaded = new ArrayList<>();
	private final List<Prepared> prepared = new ArrayList<>();
	private final List<Active> active = new ArrayList<>();
	private final List<String> errors = new ArrayList<>();
	/** Keys whose font can't draw the name: the loader returns null. */
	private final Set<String> undrawable = new HashSet<>();
	/** Keys whose file can't be read: the loader throws. */
	private final Set<String> broken = new HashSet<>();

	private LazyFontCache<String, Prepared, Active> cache(int capacity, int maxInFlight, int perFrame) {
		return new LazyFontCache<>(capacity, maxInFlight, perFrame, executor,
			key -> {
				loaded.add(key);
				if (broken.contains(key)) {
					throw new IOException("unreadable " + key);
				}
				if (undrawable.contains(key)) {
					return null;
				}
				Prepared p = new Prepared(key);
				prepared.add(p);
				return p;
			},
			p -> {
				p.freed = true; // activation bakes the glyphs and frees the file
				Active a = new Active(p.key);
				active.add(a);
				return a;
			},
			p -> p.freed = true,
			a -> a.closed = true,
			(key, error) -> errors.add(key));
	}

	/** One frame: begin, then draw each visible row. */
	private static List<Active> frame(LazyFontCache<String, Prepared, Active> cache, String... rows) {
		cache.beginFrame();
		List<Active> drawn = new ArrayList<>();
		for (String row : rows) {
			drawn.add(cache.get(row));
		}
		return drawn;
	}

	private long open() {
		return active.stream().filter(a -> !a.closed).count();
	}

	private long held() {
		return prepared.stream().filter(p -> !p.freed).count();
	}

	@Test
	void loadsInTheBackgroundAndActivatesOnALaterFrame() {
		var cache = cache(8, 4, 2);
		assertNull(frame(cache, "a").get(0), "nothing until the font is ready");
		assertTrue(loaded.isEmpty(), "the file is read on the executor, not while drawing");
		executor.runAll();
		assertEquals(List.of("a"), loaded);
		Active a = frame(cache, "a").get(0);
		assertNotNull(a, "ready on the next frame");
		assertEquals(0, held(), "activation frees the font file");
		assertTrue(frame(cache, "a").get(0) == a, "cached: not loaded again");
		assertEquals(1, loaded.size());
	}

	@Test
	void requestsOnlyRowsThatAreDrawn() {
		var cache = cache(8, 4, 2);
		frame(cache, "b", "c");
		executor.runAll();
		assertEquals(List.of("b", "c"), loaded, "only the rows asked for, in order");
	}

	@Test
	void limitsActivationsPerFrame() {
		var cache = cache(8, 8, 2);
		frame(cache, "a", "b", "c", "d", "e");
		executor.runAll();
		assertEquals(2, frame(cache, "a", "b", "c", "d", "e").stream().filter(x -> x != null).count(), "two per frame");
		assertEquals(4, frame(cache, "a", "b", "c", "d", "e").stream().filter(x -> x != null).count());
		assertEquals(5, frame(cache, "a", "b", "c", "d", "e").stream().filter(x -> x != null).count());
		assertEquals(2, cache.stats().maxActivationsInAFrame(), "never more than two in one frame");
	}

	@Test
	void limitsLoadsInFlight() {
		var cache = cache(8, 2, 8);
		frame(cache, "a", "b", "c", "d");
		assertEquals(2, executor.tasks.size(), "two loads at a time");
		executor.runAll();
		frame(cache, "a", "b", "c", "d");
		assertEquals(2, executor.tasks.size(), "the next two once the first are in");
	}

	@Test
	void capHoldsAndEvictsLeastRecentlyDrawnRows() {
		var cache = cache(3, 8, 8);
		frame(cache, "a", "b", "c");
		executor.runAll();
		frame(cache, "a", "b", "c");
		assertEquals(3, open());
		// Scroll: a and b leave the screen, d and e come in. A row counts as off screen once it
		// wasn't drawn this frame or the last, so d and e load a frame later.
		for (int i = 0; i < 4; i++) {
			frame(cache, "c", "d", "e");
			executor.runAll();
		}
		assertEquals(3, cache.size());
		assertTrue(cache.stats().peakSize() <= 3, "never more than the cap open");
		assertEquals(3, open(), "evicted fonts are closed");
		assertTrue(active.stream().filter(a -> a.key.equals("a") || a.key.equals("b")).allMatch(a -> a.closed), "a and b were evicted");
		assertEquals(3, frame(cache, "c", "d", "e").stream().filter(x -> x != null).count());
	}

	@Test
	void rowsBeyondTheCapStayInTheNormalFontWithoutThrashing() {
		var cache = cache(2, 8, 8);
		for (int i = 0; i < 5; i++) {
			frame(cache, "a", "b", "c");
			executor.runAll();
		}
		List<Active> drawn = frame(cache, "a", "b", "c");
		assertEquals(2, drawn.stream().filter(x -> x != null).count(), "two rows in their own font");
		assertNull(drawn.get(2), "the third row stays in the normal font");
		assertEquals(0, cache.stats().evictions(), "rows on screen are never evicted for each other");
		assertEquals(2, loaded.size(), "and no loads are wasted on it");
	}

	@Test
	void failuresFallBackOnceAndAreNotRetried() {
		var cache = cache(8, 8, 8);
		undrawable.add("symbols");
		broken.add("corrupt");
		frame(cache, "symbols", "corrupt", "ok");
		executor.runAll();
		for (int i = 0; i < 3; i++) {
			List<Active> drawn = frame(cache, "symbols", "corrupt", "ok");
			assertNull(drawn.get(0));
			assertNull(drawn.get(1));
			assertNotNull(drawn.get(2));
			executor.runAll();
		}
		assertEquals(List.of("symbols", "corrupt", "ok"), loaded, "each tried once");
		assertEquals(List.of("corrupt"), errors, "only the unexpected failure is reported; a font that can't draw its name is not an error");
		assertTrue(cache.isFailed("symbols") && cache.isFailed("corrupt"));
	}

	@Test
	void failedActivationFallsBack() {
		var cache = new LazyFontCache<String, Prepared, Active>(8, 8, 8, executor, Prepared::new,
			p -> {
				p.freed = true;
				throw new IllegalStateException("atlas");
			},
			p -> p.freed = true, a -> a.closed = true, (key, error) -> errors.add(key));
		frame(cache, "a");
		executor.runAll();
		assertNull(frame(cache, "a").get(0));
		assertTrue(cache.isFailed("a"));
		assertEquals(List.of("a"), errors);
	}

	@Test
	void clearClosesEverythingAndDropsLateResults() {
		var cache = cache(8, 8, 8);
		frame(cache, "a", "b");
		executor.runAll();
		frame(cache, "a", "b");
		frame(cache, "c"); // c is loading when the screen closes
		cache.clear();
		assertEquals(0, open(), "every active font is closed");
		assertEquals(0, cache.size());
		executor.runAll(); // c finishes after the screen closed
		assertEquals(0, held(), "a late result is freed at once, on the loading thread");
		assertEquals(0, cache.stats().inFlight());
	}

	@Test
	void resultsForRowsNoLongerOnScreenAreFreed() {
		var cache = cache(8, 8, 1);
		frame(cache, "a", "b", "c");
		executor.runAll();
		frame(cache, "a", "b", "c"); // one activation this frame
		frame(cache, "x"); // scrolled far away: b and c were never drawn again
		frame(cache, "x");
		assertEquals(0, held(), "their font files are freed, not kept waiting");
		assertFalse(cache.isFailed("b"), "they load again if they come back");
	}

	@Test
	void reusableAfterClear() {
		var cache = cache(8, 8, 8);
		frame(cache, "a");
		executor.runAll();
		frame(cache, "a");
		cache.clear();
		frame(cache, "a");
		executor.runAll();
		assertNotNull(frame(cache, "a").get(0), "loads again after the screen reopens");
		assertEquals(1, open());
	}
}
