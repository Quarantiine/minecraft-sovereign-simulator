package com.example.targeting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.Identifier;

/**
 * Manages predefined and player-saved Target Filter presets.
 * Presets allow commanders to quickly configure which hostile mobs minions target on sight.
 * Saved presets persist to JSON on the client (`config/sovereign-sim/target_filter_presets.json`).
 */
public class TargetFilterPresetManager {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DEFAULT_STORAGE_PATH = Path.of("config", "sovereign-sim", "target_filter_presets.json");

	private static final TargetFilterPresetManager INSTANCE = new TargetFilterPresetManager();

	private Path storagePath = DEFAULT_STORAGE_PATH;
	private final Map<String, TargetFilterPreset> userPresets = new LinkedHashMap<>();

	public static TargetFilterPresetManager getInstance() {
		return INSTANCE;
	}

	public TargetFilterPresetManager() {
		load();
	}

	public synchronized void setStoragePath(Path path) {
		this.storagePath = path != null ? path : DEFAULT_STORAGE_PATH;
	}

	public synchronized Path getStoragePath() {
		return this.storagePath;
	}

	/**
	 * Returns standard built-in presets generated dynamically against the available candidates.
	 *
	 * @param candidateIds All hostile mob type candidates registered.
	 * @return List of immutable built-in presets.
	 */
	public static List<TargetFilterPreset> getBuiltInPresets(List<Identifier> candidateIds) {
		List<TargetFilterPreset> list = new ArrayList<>();
		Set<Identifier> all = new LinkedHashSet<>(candidateIds != null ? candidateIds : List.of());

		// 1. All Hostiles
		list.add(new TargetFilterPreset("All Hostiles", all));

		// 2. Creeper Safe (all except creeper)
		Set<Identifier> creeperSafe = new LinkedHashSet<>(all);
		creeperSafe.remove(Identifier.ofVanilla("creeper"));
		list.add(new TargetFilterPreset("Creeper Safe", creeperSafe));

		// 3. Undead Only
		Set<Identifier> undead = new LinkedHashSet<>();
		Set<String> undeadNames = Set.of(
			"zombie", "skeleton", "stray", "drowned", "husk", "wither_skeleton",
			"zombified_piglin", "phantom", "zombie_villager", "bogged", "zoglin"
		);
		for (Identifier id : all) {
			if (undeadNames.contains(id.getPath())) {
				undead.add(id);
			}
		}
		list.add(new TargetFilterPreset("Undead Only", undead));

		// 4. Illagers & Raiders
		Set<Identifier> raiders = new LinkedHashSet<>();
		Set<String> raiderNames = Set.of("pillager", "vindicator", "evoker", "ravager", "witch", "illusioner");
		for (Identifier id : all) {
			if (raiderNames.contains(id.getPath())) {
				raiders.add(id);
			}
		}
		list.add(new TargetFilterPreset("Raiders", raiders));

		// 5. None / Passive
		list.add(new TargetFilterPreset("None (Passive)", Set.of()));

		return Collections.unmodifiableList(list);
	}

	/**
	 * Saves a player preset with a title and the given allowed mob IDs.
	 *
	 * @param title      The title of the preset.
	 * @param allowedIds Allowed mob identifiers.
	 * @return The created preset.
	 */
	public synchronized TargetFilterPreset savePreset(String title, Set<Identifier> allowedIds) {
		if (title == null || title.trim().isEmpty()) {
			throw new IllegalArgumentException("Preset title must not be empty");
		}
		String cleanTitle = title.trim();
		TargetFilterPreset preset = new TargetFilterPreset(cleanTitle, allowedIds);
		this.userPresets.put(cleanTitle, preset);
		save();
		return preset;
	}

	/**
	 * Deletes a user-saved preset by title.
	 *
	 * @param title Title of the preset to delete.
	 * @return True if a user preset was deleted.
	 */
	public synchronized boolean deletePreset(String title) {
		if (title == null) return false;
		boolean removed = this.userPresets.remove(title.trim()) != null;
		if (removed) {
			save();
		}
		return removed;
	}

	/**
	 * Looks up a user-saved preset by title.
	 */
	public synchronized Optional<TargetFilterPreset> getUserPreset(String title) {
		if (title == null) return Optional.empty();
		return Optional.ofNullable(this.userPresets.get(title.trim()));
	}

	/**
	 * Returns all user-saved presets.
	 */
	public synchronized List<TargetFilterPreset> getUserPresets() {
		return new ArrayList<>(this.userPresets.values());
	}

	/**
	 * Returns all available presets for a given set of candidates: user-saved presets first,
	 * followed by any built-in presets whose titles don't collide with user presets.
	 */
	public synchronized List<TargetFilterPreset> getAllPresets(List<Identifier> candidateIds) {
		List<TargetFilterPreset> combined = new ArrayList<>(this.userPresets.values());
		Set<String> userTitles = new LinkedHashSet<>(this.userPresets.keySet());

		for (TargetFilterPreset builtIn : getBuiltInPresets(candidateIds)) {
			if (!userTitles.contains(builtIn.title())) {
				combined.add(builtIn);
			}
		}
		return Collections.unmodifiableList(combined);
	}

	/**
	 * Clears in-memory user presets (useful for tests).
	 */
	public synchronized void clear() {
		this.userPresets.clear();
	}

	/**
	 * Loads user presets from disk.
	 */
	public synchronized void load() {
		this.userPresets.clear();
		if (this.storagePath == null || !Files.exists(this.storagePath)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(this.storagePath)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root != null && root.isJsonObject()) {
				JsonObject obj = root.getAsJsonObject();
				for (String key : obj.keySet()) {
					JsonElement el = obj.get(key);
					if (el != null && el.isJsonArray()) {
						Set<Identifier> ids = new LinkedHashSet<>();
						for (JsonElement item : el.getAsJsonArray()) {
							if (item.isJsonPrimitive()) {
								Identifier id = Identifier.tryParse(item.getAsString());
								if (id != null) {
									ids.add(id);
								}
							}
						}
						this.userPresets.put(key, new TargetFilterPreset(key, ids));
					}
				}
			}
		} catch (Exception ignored) {
			// Fail-safe fallback to empty presets on disk error or corruption
		}
	}

	/**
	 * Persists user presets to disk.
	 */
	public synchronized void save() {
		if (this.storagePath == null) return;

		try {
			Path parent = this.storagePath.getParent();
			if (parent != null && !Files.exists(parent)) {
				Files.createDirectories(parent);
			}

			JsonObject root = new JsonObject();
			for (Map.Entry<String, TargetFilterPreset> entry : this.userPresets.entrySet()) {
				JsonArray array = new JsonArray();
				for (Identifier id : entry.getValue().allowedIds()) {
					array.add(id.toString());
				}
				root.add(entry.getKey(), array);
			}

			try (Writer writer = Files.newBufferedWriter(this.storagePath)) {
				GSON.toJson(root, writer);
			}
		} catch (Exception ignored) {
			// Non-blocking disk failure suppression
		}
	}
}
