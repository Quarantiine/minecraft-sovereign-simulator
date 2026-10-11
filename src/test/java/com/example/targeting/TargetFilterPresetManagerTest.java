package com.example.targeting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests validating TargetFilterPreset and TargetFilterPresetManager:
 * - Predefined built-in presets (All Hostiles, Creeper Safe, Undead Only, Raiders, None)
 * - Saving custom presets with title and allowed mob set
 * - Loading and saving to JSON file
 * - Deletion of custom presets
 * - Preset list aggregation without duplicating user presets
 */
public class TargetFilterPresetManagerTest {

	private static final Identifier ZOMBIE = Identifier.ofVanilla("zombie");
	private static final Identifier CREEPER = Identifier.ofVanilla("creeper");
	private static final Identifier SKELETON = Identifier.ofVanilla("skeleton");
	private static final Identifier PILLAGER = Identifier.ofVanilla("pillager");
	private static final Identifier SPIDER = Identifier.ofVanilla("spider");
	private static final List<Identifier> CANDIDATES = List.of(ZOMBIE, CREEPER, SKELETON, PILLAGER, SPIDER);

	@TempDir
	Path tempDir;

	private TargetFilterPresetManager manager;
	private Path testJsonPath;

	@BeforeEach
	void setUp() {
		this.testJsonPath = tempDir.resolve("target_presets.json");
		this.manager = new TargetFilterPresetManager();
		this.manager.setStoragePath(this.testJsonPath);
		this.manager.clear();
	}

	@Test
	@DisplayName("Built-in presets generate valid subsets of candidates")
	void testBuiltInPresets() {
		List<TargetFilterPreset> builtIns = TargetFilterPresetManager.getBuiltInPresets(CANDIDATES);
		Assertions.assertFalse(builtIns.isEmpty());

		// 1. All Hostiles contains every candidate
		TargetFilterPreset all = builtIns.stream().filter(p -> p.title().equals("All Hostiles")).findFirst().orElseThrow();
		Assertions.assertEquals(CANDIDATES.size(), all.size());
		Assertions.assertTrue(all.contains(CREEPER));

		// 2. Creeper Safe contains everything except creeper
		TargetFilterPreset creeperSafe = builtIns.stream().filter(p -> p.title().equals("Creeper Safe")).findFirst().orElseThrow();
		Assertions.assertFalse(creeperSafe.contains(CREEPER));
		Assertions.assertTrue(creeperSafe.contains(ZOMBIE));
		Assertions.assertTrue(creeperSafe.contains(SKELETON));

		// 3. Undead Only contains zombie and skeleton, not creeper or pillager
		TargetFilterPreset undead = builtIns.stream().filter(p -> p.title().equals("Undead Only")).findFirst().orElseThrow();
		Assertions.assertTrue(undead.contains(ZOMBIE));
		Assertions.assertTrue(undead.contains(SKELETON));
		Assertions.assertFalse(undead.contains(CREEPER));
		Assertions.assertFalse(undead.contains(PILLAGER));

		// 4. Raiders contains pillager
		TargetFilterPreset raiders = builtIns.stream().filter(p -> p.title().equals("Raiders")).findFirst().orElseThrow();
		Assertions.assertTrue(raiders.contains(PILLAGER));
		Assertions.assertFalse(raiders.contains(ZOMBIE));

		// 5. None / Passive is empty
		TargetFilterPreset none = builtIns.stream().filter(p -> p.title().equals("None (Passive)")).findFirst().orElseThrow();
		Assertions.assertEquals(0, none.size());
	}

	@Test
	@DisplayName("Save and load custom presets persists accurately to JSON file")
	void testSaveAndLoadPreset() {
		TargetFilterPreset saved = this.manager.savePreset("Night Patrol", Set.of(ZOMBIE, SKELETON));
		Assertions.assertEquals("Night Patrol", saved.title());
		Assertions.assertEquals(2, saved.size());
		Assertions.assertTrue(Files.exists(this.testJsonPath), "JSON file must be created on disk");

		// Read back via a fresh manager instance
		TargetFilterPresetManager fresh = new TargetFilterPresetManager();
		fresh.setStoragePath(this.testJsonPath);
		fresh.load();

		Optional<TargetFilterPreset> loaded = fresh.getUserPreset("Night Patrol");
		Assertions.assertTrue(loaded.isPresent());
		Assertions.assertEquals(Set.of(ZOMBIE, SKELETON), loaded.get().allowedIds());
	}

	@Test
	@DisplayName("Delete preset removes user preset and updates disk storage")
	void testDeletePreset() {
		this.manager.savePreset("Temp Preset", Set.of(ZOMBIE));
		Assertions.assertTrue(this.manager.getUserPreset("Temp Preset").isPresent());

		boolean deleted = this.manager.deletePreset("Temp Preset");
		Assertions.assertTrue(deleted);
		Assertions.assertFalse(this.manager.getUserPreset("Temp Preset").isPresent());

		TargetFilterPresetManager fresh = new TargetFilterPresetManager();
		fresh.setStoragePath(this.testJsonPath);
		fresh.load();
		Assertions.assertFalse(fresh.getUserPreset("Temp Preset").isPresent());
	}

	@Test
	@DisplayName("User presets take precedence over built-in presets with identical titles")
	void testUserPresetOverridesBuiltInTitle() {
		// User overrides "Creeper Safe" with only zombie allowed
		this.manager.savePreset("Creeper Safe", Set.of(ZOMBIE));

		List<TargetFilterPreset> all = this.manager.getAllPresets(CANDIDATES);
		long count = all.stream().filter(p -> p.title().equals("Creeper Safe")).count();
		Assertions.assertEquals(1, count, "Must not duplicate titles");

		TargetFilterPreset effective = all.stream().filter(p -> p.title().equals("Creeper Safe")).findFirst().orElseThrow();
		Assertions.assertEquals(Set.of(ZOMBIE), effective.allowedIds());
	}
}
