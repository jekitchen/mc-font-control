package io.github.jekitchen.fontsys.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * A lazy, capped cache of fonts for the rows of a list that are on screen (PRD D49). It has no
 * game code, so unit tests cover it; {@link FamilyNameFonts} supplies the font work.
 *
 * <p>A row's font goes through three steps:
 *
 * <ol>
 *   <li><b>Load</b>, on the executor: read the file and open the face ({@code P}). Only rows that
 *       are drawn are asked for, and only {@code maxInFlight} loads run at once, so fast
 *       scrolling doesn't queue up the whole list.</li>
 *   <li><b>Activate</b>, on the render thread, while a row is drawn: build the glyph atlas
 *       ({@code V}). At most {@code activationsPerFrame} per frame, so a screenful of new rows
 *       spreads over a few frames instead of one long one. The activator owns the loaded value
 *       from then on and frees what it no longer needs.</li>
 *   <li><b>Evict</b>: at most {@code capacity} fonts are active. To make room, the font least
 *       recently drawn is closed, but only one that wasn't drawn this frame or the last, so
 *       rows on screen never push each other out. If every active font is on screen, further
 *       rows stay in the normal font.</li>
 * </ol>
 *
 * <p>A key whose load returns null (the font can't draw its name) or fails is remembered and
 * not tried again until {@link #clear()}. Unexpected errors go to the reporter; a null load
 * does not. Loaded values for rows that left the screen before they were activated are freed
 * on the next frame.
 *
 * <p>Call {@link #beginFrame()} once per frame, then {@link #get} for each row drawn, all on
 * one thread. {@link #clear()} closes everything; loads still running are freed on their own
 * thread as they finish.
 *
 * @param <K> what identifies a row's font
 * @param <P> a loaded font, before activation (holds native memory)
 * @param <V> an active font (holds GPU memory)
 */
public final class LazyFontCache<K, P, V> {
	/** Loads a font off the render thread; returns null if it can't be used, which is not an error. */
	@FunctionalInterface
	public interface Loader<K, P> {
		@Nullable P load(K key) throws Exception;
	}

	/** Makes a loaded font drawable, on the render thread. Takes ownership of {@code loaded}, also when it fails. */
	@FunctionalInterface
	public interface Activator<P, V> {
		@Nullable V activate(P loaded) throws Exception;
	}

	@FunctionalInterface
	public interface ErrorReporter<K> {
		void report(K key, Throwable error);
	}

	/**
	 * What the cache has done since it was made.
	 *
	 * @param maxActivationNanos the longest a single frame spent activating fonts
	 */
	public record Stats(int size, int peakSize, int inFlight, int waiting, long loads, long activations, long evictions, int failures,
		int maxActivationsInAFrame, long maxActivationNanos, long totalActivationNanos) {
	}

	private record Arrival<K, P>(K key, @Nullable P loaded, @Nullable Throwable error) {
	}

	private static final class Waiting<P> {
		final P loaded;
		long lastAsked;

		Waiting(P loaded, long lastAsked) {
			this.loaded = loaded;
			this.lastAsked = lastAsked;
		}
	}

	private static final class Ready<V> {
		final V value;
		long lastUsed;

		Ready(V value, long lastUsed) {
			this.value = value;
			this.lastUsed = lastUsed;
		}
	}

	private final int capacity;
	private final int maxInFlight;
	private final int activationsPerFrame;
	private final Executor executor;
	private final Loader<K, P> loader;
	private final Activator<P, V> activator;
	private final Consumer<P> discard;
	private final Consumer<V> close;
	private final ErrorReporter<K> reporter;

	/** Guards {@link #generation}, {@link #arrivals} and {@link #inFlight}, which the loading thread touches. */
	private final Object lock = new Object();
	private int generation;
	private final Queue<Arrival<K, P>> arrivals = new ArrayDeque<>();
	private int inFlight;

	// Render thread only.
	private final Map<K, Ready<V>> ready = new HashMap<>();
	private final Map<K, Waiting<P>> waiting = new HashMap<>();
	/** Loads running for this generation, with the frame each row was last drawn. */
	private final Map<K, Long> requested = new HashMap<>();
	private final Set<K> failed = new HashSet<>();
	private long frame;
	private int activationsThisFrame;
	private long nanosThisFrame;

	private int peakSize;
	private long loads;
	private long activations;
	private long evictions;
	private int maxActivationsInAFrame;
	private long maxActivationNanos;
	private long totalActivationNanos;

	/**
	 * @param discard frees a loaded font that is never activated
	 * @param close   frees an active font
	 */
	public LazyFontCache(int capacity, int maxInFlight, int activationsPerFrame, Executor executor, Loader<K, P> loader,
		Activator<P, V> activator, Consumer<P> discard, Consumer<V> close, ErrorReporter<K> reporter) {
		if (capacity < 1 || maxInFlight < 1 || activationsPerFrame < 1) {
			throw new IllegalArgumentException("limits must be positive");
		}
		this.capacity = capacity;
		this.maxInFlight = maxInFlight;
		this.activationsPerFrame = activationsPerFrame;
		this.executor = executor;
		this.loader = loader;
		this.activator = activator;
		this.discard = discard;
		this.close = close;
		this.reporter = reporter;
	}

	/** Starts a frame: resets the activation budget and frees loaded fonts for rows no longer drawn. */
	public void beginFrame() {
		frame++;
		activationsThisFrame = 0;
		nanosThisFrame = 0;
		drain();
		for (Iterator<Waiting<P>> it = waiting.values().iterator(); it.hasNext(); ) {
			Waiting<P> w = it.next();
			if (w.lastAsked < frame - 1) {
				it.remove();
				discardQuietly(w.loaded);
			}
		}
	}

	/** The row's font if it is ready; otherwise starts loading it, if there is room, and returns null. */
	public @Nullable V get(K key) {
		drain();
		Ready<V> r = ready.get(key);
		if (r != null) {
			r.lastUsed = frame;
			return r.value;
		}
		if (failed.contains(key)) {
			return null;
		}
		Waiting<P> w = waiting.get(key);
		if (w != null) {
			w.lastAsked = frame;
			if (activationsThisFrame < activationsPerFrame && makeRoom()) {
				waiting.remove(key);
				return activate(key, w.loaded);
			}
			return null;
		}
		if (requested.containsKey(key)) {
			requested.put(key, frame);
		} else if (inFlight() < maxInFlight && hasRoom()) {
			submit(key);
		}
		return null;
	}

	/** Closes every font and forgets every failure; loads still running are freed when they finish. */
	public void clear() {
		List<Arrival<K, P>> late;
		synchronized (lock) {
			generation++;
			late = new ArrayList<>(arrivals);
			arrivals.clear();
		}
		late.forEach(a -> {
			if (a.loaded() != null) {
				discardQuietly(a.loaded());
			}
		});
		waiting.values().forEach(w -> discardQuietly(w.loaded));
		waiting.clear();
		ready.values().forEach(r -> closeQuietly(r.value));
		ready.clear();
		requested.clear();
		failed.clear();
	}

	/** Active fonts. */
	public int size() {
		return ready.size();
	}

	public boolean isFailed(K key) {
		return failed.contains(key);
	}

	public Stats stats() {
		return new Stats(ready.size(), peakSize, inFlight(), waiting.size(), loads, activations, evictions, failed.size(), maxActivationsInAFrame,
			maxActivationNanos, totalActivationNanos);
	}

	private int inFlight() {
		synchronized (lock) {
			return inFlight;
		}
	}

	/** Room for one more font: below the cap counting loads under way, or an active font that's off screen. */
	private boolean hasRoom() {
		int evictable = 0;
		for (Ready<V> r : ready.values()) {
			if (offScreen(r)) {
				evictable++;
			}
		}
		return ready.size() - evictable + requested.size() + waiting.size() < capacity;
	}

	private boolean offScreen(Ready<V> r) {
		return r.lastUsed < frame - 1;
	}

	/** Below the cap, or closes the least recently drawn font that is off screen. */
	private boolean makeRoom() {
		if (ready.size() < capacity) {
			return true;
		}
		K oldest = null;
		long oldestUse = Long.MAX_VALUE;
		for (Map.Entry<K, Ready<V>> e : ready.entrySet()) {
			if (offScreen(e.getValue()) && e.getValue().lastUsed < oldestUse) {
				oldest = e.getKey();
				oldestUse = e.getValue().lastUsed;
			}
		}
		if (oldest == null) {
			return false;
		}
		closeQuietly(ready.remove(oldest).value);
		evictions++;
		return true;
	}

	private void submit(K key) {
		int gen;
		synchronized (lock) {
			gen = generation;
			inFlight++;
		}
		requested.put(key, frame);
		loads++;
		try {
			executor.execute(() -> load(key, gen));
		} catch (RuntimeException e) {
			synchronized (lock) {
				inFlight--;
			}
			requested.remove(key);
			failed.add(key);
			reporter.report(key, e);
		}
	}

	/** On the loading thread. */
	private void load(K key, int gen) {
		P loaded = null;
		Throwable error = null;
		try {
			loaded = loader.load(key);
		} catch (Throwable t) {
			error = t;
		}
		synchronized (lock) {
			inFlight--;
			if (gen == generation) {
				arrivals.add(new Arrival<>(key, loaded, error));
				return;
			}
		}
		// The cache was cleared while this loaded: nobody will draw it.
		if (loaded != null) {
			discardQuietly(loaded);
		}
	}

	/** Moves finished loads to {@link #waiting}, or to {@link #failed}. */
	private void drain() {
		List<Arrival<K, P>> done;
		synchronized (lock) {
			if (arrivals.isEmpty()) {
				return;
			}
			done = new ArrayList<>(arrivals);
			arrivals.clear();
		}
		for (Arrival<K, P> a : done) {
			Long lastAsked = requested.remove(a.key());
			if (a.error() != null) {
				failed.add(a.key());
				reporter.report(a.key(), a.error());
			} else if (a.loaded() == null) {
				failed.add(a.key());
			} else {
				waiting.put(a.key(), new Waiting<>(a.loaded(), lastAsked == null ? frame : lastAsked));
			}
		}
	}

	private @Nullable V activate(K key, P loaded) {
		activationsThisFrame++;
		maxActivationsInAFrame = Math.max(maxActivationsInAFrame, activationsThisFrame);
		long start = System.nanoTime();
		try {
			V value = activator.activate(loaded);
			if (value == null) {
				failed.add(key);
				return null;
			}
			ready.put(key, new Ready<>(value, frame));
			peakSize = Math.max(peakSize, ready.size());
			activations++;
			return value;
		} catch (Throwable t) {
			failed.add(key);
			reporter.report(key, t);
			return null;
		} finally {
			long spent = System.nanoTime() - start;
			nanosThisFrame += spent;
			totalActivationNanos += spent;
			maxActivationNanos = Math.max(maxActivationNanos, nanosThisFrame);
		}
	}

	private void discardQuietly(P loaded) {
		try {
			discard.accept(loaded);
		} catch (RuntimeException e) {
			reporter.report(null, e);
		}
	}

	private void closeQuietly(V value) {
		try {
			close.accept(value);
		} catch (RuntimeException e) {
			reporter.report(null, e);
		}
	}
}
