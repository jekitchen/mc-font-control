package io.github.jekitchen.mcfontcontrol.apply;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.compat.KnownFontMods;
import io.github.jekitchen.mcfontcontrol.config.ConfigStore;
import io.github.jekitchen.mcfontcontrol.config.FontConfig;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.config.SystemFingerprint;
import io.github.jekitchen.mcfontcontrol.pack.PackContents;
import io.github.jekitchen.mcfontcontrol.pack.RuntimeFontPackSource;
import io.github.jekitchen.mcfontcontrol.platform.Platform;
import io.github.jekitchen.mcfontcontrol.ui.KeepOrRevertScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Owns the config and every change to the applied font: Apply and Reset with the
 * keep-or-revert safety valve (PRD 5.7, D13), crash recovery, new-system detection (5.6,
 * D12) and reloads after GUI-scale changes.
 */
public final class FontController {
	/** Seconds the player has to confirm a change (D13). */
	public static final int CONFIRM_SECONDS = 30;
	private static final int GUI_SCALE_SETTLE_TICKS = 20;

	private final ConfigStore store;
	private final Optional<KnownFontMods.KnownMod> conflict;
	private final Supplier<String> fingerprint;
	private FontConfig config;
	private volatile long lastApplyMillis = -1;
	/** System.nanoTime() when an unconfirmed change reverts; 0 when nothing is pending. */
	private long confirmDeadline;
	private int guiScaleChangedTicks;
	private boolean reloading;

	public FontController(ConfigStore store, Platform platform) {
		this(store, platform, SystemFingerprint::current);
	}

	FontController(ConfigStore store, Platform platform, Supplier<String> fingerprint) {
		this.store = store;
		this.fingerprint = fingerprint;
		this.conflict = KnownFontMods.firstLoaded(platform);
		boolean existed = Files.exists(store.file());
		ConfigStore.LoadResult result = store.load();
		this.config = result.config();
		result.warnings().forEach(FontController::warn);
		if (existed && !result.reset()) {
			checkSystem();
			recoverUnconfirmed();
		}
		if (config.systemFingerprint == null || !config.systemFingerprint.equals(fingerprint.get())) {
			config.systemFingerprint = fingerprint.get();
			save();
		}
		conflict.ifPresent(m -> warn(m.name() + " is installed and also replaces the game font; MC Font Control is leaving the font alone. Remove one of the two mods to choose."));
	}

	/** A config written on another OS or user account starts over from vanilla (5.6). */
	private void checkSystem() {
		Optional<String> mismatch = SystemFingerprint.mismatch(config.systemFingerprint, fingerprint.get());
		if (mismatch.isPresent()) {
			String backup = store.backupFile().getFileName().toString();
			store.backupAndReset(mismatch.get());
			config = FontConfig.defaults();
			warn("Config was written on a different system (" + mismatch.get() + "); backed up to " + backup + " and reset to defaults.");
			if (McFontControl.fontIndex() != null) {
				McFontControl.fontIndex().scan();
			}
		}
	}

	/** The game closed or crashed during a keep-or-revert countdown: revert before the title screen (5.7). */
	private void recoverUnconfirmed() {
		if (config.pending != null) {
			config.setSettings(config.pending);
			config.pending = null;
			save();
			McFontControl.LOGGER.info("[{}] The last font change was never confirmed; restored the previous font.", McFontControl.DISPLAY_NAME);
		}
	}

	public synchronized FontConfig config() {
		return config;
	}

	/** Settings the runtime pack serves. */
	public synchronized FontSettings appliedSettings() {
		return config.settings();
	}

	public Optional<KnownFontMods.KnownMod> conflict() {
		return conflict;
	}

	/** Another font mod is loaded, so we serve no system font (AP-12). */
	public boolean stepAside() {
		return conflict.isPresent();
	}

	public boolean isAwaitingConfirmation() {
		return confirmDeadline != 0;
	}

	/** Whole seconds left before an unconfirmed change reverts. */
	public int secondsLeft() {
		if (confirmDeadline == 0) {
			return 0;
		}
		return (int) Math.max(0, Math.ceil((confirmDeadline - System.nanoTime()) / 1e9));
	}

	/**
	 * Applies new settings. If the rendered font changes, the previous settings are saved as
	 * {@code pending} first and, once the reload finishes, the keep-or-revert dialog opens over
	 * {@code returnTo}.
	 *
	 * @return completes when the reload finishes (immediately if nothing visible changed)
	 */
	public CompletableFuture<Void> applyWithConfirmation(FontSettings next, Screen returnTo) {
		FontSettings previous = appliedSettings();
		if (previous.sameRendering(next)) {
			synchronized (this) {
				config.setSettings(next);
			}
			save();
			return CompletableFuture.completedFuture(null);
		}
		synchronized (this) {
			config.pending = previous;
		}
		return apply(next).thenRun(() -> Minecraft.getInstance().execute(() -> {
			confirmDeadline = System.nanoTime() + CONFIRM_SECONDS * 1_000_000_000L;
			Minecraft.getInstance().gui.setScreen(new KeepOrRevertScreen(returnTo));
		}));
	}

	/** Keep the new font (Enter / Keep changes). */
	public void confirm() {
		synchronized (this) {
			config.pending = null;
		}
		confirmDeadline = 0;
		save();
	}

	/** Restore the settings from before the unconfirmed change (Esc / Revert / timeout). */
	public CompletableFuture<Void> revert() {
		FontSettings previous;
		synchronized (this) {
			previous = config.pending;
			config.pending = null;
		}
		confirmDeadline = 0;
		if (previous == null) {
			save();
			return CompletableFuture.completedFuture(null);
		}
		return apply(previous);
	}

	/**
	 * Saves settings and reloads resources so they take effect (AP-6, D2: full reload). Logs
	 * how long the reload took, for the D14 Apply target.
	 */
	public CompletableFuture<Void> apply(FontSettings settings) {
		synchronized (this) {
			config.setSettings(settings);
		}
		save();
		return reload("Applied " + (settings.isActive() ? "'" + settings.familyName() + " " + settings.styleName() + "'" : "the vanilla font"));
	}

	private CompletableFuture<Void> reload(String what) {
		long start = System.nanoTime();
		reloading = true;
		return Minecraft.getInstance().reloadResourcePacks().whenComplete((ok, error) -> {
			reloading = false;
			long ms = (System.nanoTime() - start) / 1_000_000;
			lastApplyMillis = ms;
			McFontControl.LOGGER.info("[{}] {} in {} ms.", McFontControl.DISPLAY_NAME, what, ms);
		});
	}

	/** Duration of the last reload we triggered, or -1. */
	public long lastApplyMillis() {
		return lastApplyMillis;
	}

	/**
	 * Called every client tick. Runs the confirmation countdown on the wall clock, so it keeps
	 * going when the window loses focus (5.7), and reloads after a GUI-scale change when
	 * oversample is automatic (risk: pixel grid and GUI scale).
	 */
	public void tick(Minecraft mc) {
		if (confirmDeadline != 0 && System.nanoTime() >= confirmDeadline) {
			McFontControl.LOGGER.info("[{}] Font change not confirmed within {} s; reverting.", McFontControl.DISPLAY_NAME, CONFIRM_SECONDS);
			Screen open = mc.gui.screen();
			revert();
			if (open instanceof KeepOrRevertScreen keep) {
				mc.gui.setScreen(keep.returnTo());
			}
		}
		PackContents.Loaded loaded = PackContents.loaded();
		if (loaded.active() && loaded.autoOversample() && !reloading && mc.gui.overlay() == null
			&& RuntimeFontPackSource.currentGuiScale() != loaded.guiScale()) {
			if (++guiScaleChangedTicks >= GUI_SCALE_SETTLE_TICKS) {
				guiScaleChangedTicks = 0;
				reload("Reloaded the font for GUI scale " + RuntimeFontPackSource.currentGuiScale());
			}
		} else {
			guiScaleChangedTicks = 0;
		}
	}

	public synchronized void setShowHiddenFonts(boolean value) {
		config.showHiddenFonts = value;
		save();
	}

	public synchronized void save() {
		try {
			store.save(config);
		} catch (IOException e) {
			warn("Could not save " + store.file().getFileName() + ": " + e.getMessage());
		}
	}

	private static void warn(String message) {
		McFontControl.LOGGER.warn("[{}] {}", McFontControl.DISPLAY_NAME, message);
	}
}
