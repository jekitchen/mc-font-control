package io.github.jekitchen.fontsys.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code config/fontsys.json} (PRD 5.3, D16). Never throws on bad
 * input: unreadable, invalid or too-new files are renamed to {@code .bak} and replaced by
 * defaults, with one warning for the caller to log.
 */
public final class ConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

	/** Result of loading: the config plus at most a few warnings to log. */
	public record LoadResult(FontConfig config, List<String> warnings, boolean reset) {
	}

	private final Path file;

	public ConfigStore(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	public Path backupFile() {
		return file.resolveSibling(file.getFileName() + ".bak");
	}

	public LoadResult load() {
		List<String> warnings = new ArrayList<>();
		if (!Files.exists(file)) {
			return new LoadResult(FontConfig.defaults(), warnings, false);
		}
		String reason;
		try {
			JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!json.isJsonObject()) {
				reason = "not a JSON object";
			} else {
				JsonObject obj = json.getAsJsonObject();
				int version = obj.has("configVersion") && obj.get("configVersion").isJsonPrimitive() ? obj.get("configVersion").getAsInt() : 0;
				if (version > FontConfig.CURRENT_VERSION) {
					reason = "written by a newer version of the mod (configVersion " + version + ")";
				} else {
					FontConfig config = GSON.fromJson(migrate(obj, version), FontConfig.class);
					config.configVersion = FontConfig.CURRENT_VERSION;
					config.setSettings(config.settings());
					if (config.pending != null) {
						config.pending = config.pending.sanitized();
					}
					return new LoadResult(config, warnings, false);
				}
			}
		} catch (IOException | JsonParseException | IllegalStateException | NumberFormatException | UnsupportedOperationException e) {
			reason = "unreadable (" + e.getClass().getSimpleName() + ")";
		}
		warnings.add(backupAndReset(reason));
		return new LoadResult(FontConfig.defaults(), warnings, true);
	}

	/**
	 * Upgrades older JSON one version at a time. Version 0 means the field was missing; it has
	 * the same shape as version 1.
	 */
	static JsonObject migrate(JsonObject obj, int fromVersion) {
		return obj;
	}

	/** Renames the current file to {@code .bak}; returns the log message describing why. */
	public String backupAndReset(String reason) {
		try {
			Files.move(file, backupFile(), StandardCopyOption.REPLACE_EXISTING);
			return "Config file " + reason + "; backed up to " + backupFile().getFileName() + " and reset to defaults.";
		} catch (IOException e) {
			return "Config file " + reason + " and could not be backed up (" + e.getMessage() + "); using defaults.";
		}
	}

	/** Saves atomically: temp file, then move, so a crash never leaves half a file. */
	public void save(FontConfig config) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		}
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
