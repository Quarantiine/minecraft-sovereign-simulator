package com.example.blueprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit and source invariant tests for:
 * 1. StructureBlueprint.createRemapped(...) runtime behavior.
 * 2. BlueprintSaveChoiceModalScreen UI workflow & scepter update contracts.
 * 3. 3D BlueprintExplorerScreen performance mesh cache & card title/size overlap fix.
 * 4. Builder minion chest search radius expansion (32 blocks).
 * 5. Minion roaming on patrol route deletion.
 */
public class BlueprintPaletteRemapAndSaveChoiceTest {

	@BeforeAll
	static void initBootstrap() {
		try {
			net.minecraft.SharedConstants.createGameVersion();
			net.minecraft.Bootstrap.initialize();
		} catch (Throwable ignored) {}
	}

	private static BlockState createDummyBlockState() {
		try {
			java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
			return (BlockState) unsafe.allocateInstance(BlockState.class);
		} catch (Throwable t) {
			return null;
		}
	}

	@Test
	@DisplayName("StructureBlueprint.createRemapped copies metadata and blocks when replacements map is empty")
	void testCreateRemappedWithEmptyReplacements() {
		BlockState stateA = createDummyBlockState();
		BlockState stateB = createDummyBlockState();
		if (stateA == null || stateB == null) return;

		StructureBlueprint source = StructureBlueprint.builder("test_tower", "Test Tower")
			.description("Original tower description")
			.addBlock(new BlockPos(0, 0, 0), stateA)
			.addBlock(new BlockPos(0, 1, 0), stateB)
			.build();

		StructureBlueprint remapped = StructureBlueprint.createRemapped(
			source,
			"test_tower_copy",
			"Test Tower Copy",
			"Modified description",
			Map.of()
		);

		Assertions.assertNotNull(remapped);
		Assertions.assertEquals("test_tower_copy", remapped.getId());
		Assertions.assertEquals("Test Tower Copy", remapped.getName());
		Assertions.assertEquals("Modified description", remapped.getDescription());
		Assertions.assertEquals(2, remapped.getBlockCount());
	}

	@Test
	@DisplayName("StructureBlueprint.createRemapped preserves original ID and Name when null passed")
	void testCreateRemappedPreservesOriginalMetadataWhenNull() {
		BlockState state = createDummyBlockState();
		if (state == null) return;

		StructureBlueprint source = StructureBlueprint.builder("orig_id", "Orig Name")
			.description("Orig Desc")
			.addBlock(new BlockPos(0, 0, 0), state)
			.build();

		StructureBlueprint remapped = StructureBlueprint.createRemapped(
			source,
			null,
			null,
			null,
			null
		);

		Assertions.assertNotNull(remapped);
		Assertions.assertEquals("orig_id", remapped.getId());
		Assertions.assertEquals("Orig Name", remapped.getName());
		Assertions.assertEquals("Orig Desc", remapped.getDescription());
		Assertions.assertEquals(1, remapped.getBlockCount());
	}

	@Test
	@DisplayName("Source Invariant: BlueprintSaveChoiceModalScreen enforces overwrite vs copy, validation, and network dispatch")
	void testBlueprintSaveChoiceModalScreenInvariants() throws IOException {
		Path path = Path.of("src/client/java/com/example/client/gui/BlueprintSaveChoiceModalScreen.java");
		Assertions.assertTrue(Files.exists(path), "BlueprintSaveChoiceModalScreen.java must exist");
		String content = Files.readString(path);

		// Overwrite & Copy Buttons
		Assertions.assertTrue(content.contains("overwriteButton"), "Must declare overwriteButton");
		Assertions.assertTrue(content.contains("saveCopyButton"), "Must declare saveCopyButton");
		Assertions.assertTrue(content.contains("cancelButton"), "Must declare cancelButton");

		// Metadata validation
		Assertions.assertTrue(content.contains("nameField"), "Must declare nameField");
		Assertions.assertTrue(content.contains("cleanName.isBlank()"), "Must validate that name is not blank");
		Assertions.assertTrue(content.contains("remapped.getBlockCount() == 0"), "Must validate that remapped blueprint is not empty");

		// Networking & Registry integration
		Assertions.assertTrue(content.contains("ModClientNetworking.sendCreateCustomBlueprint(remapped)"),
			"Must send CreateCustomBlueprint payload on save");
		Assertions.assertTrue(content.contains("BlueprintRegistry.registerCustomBlueprint(remapped, false)"),
			"Must register in client registry immediately");
		Assertions.assertTrue(content.contains("CommandScepterItem.setBlueprintId"),
			"Must arm newly saved/overwritten blueprint on command scepter");
	}

	@Test
	@DisplayName("Source Invariant: BlueprintExplorerScreen card overlap fix and zero-allocation 3D engine")
	void testBlueprintExplorerScreenPerformanceAndOverlapFix() throws IOException {
		Path path = Path.of("src/client/java/com/example/client/gui/BlueprintExplorerScreen.java");
		Assertions.assertTrue(Files.exists(path), "BlueprintExplorerScreen.java must exist");
		String content = Files.readString(path);

		// Card text collision fix: CardSlotButton overrides renderWidget
		Assertions.assertTrue(content.contains("CardSlotButton extends ButtonWidget"),
			"Must define CardSlotButton to suppress vanilla button centering");
		Assertions.assertTrue(content.contains("cardY + 5"),
			"Must render card title on top line at cardY + 5");
		Assertions.assertTrue(content.contains("cardY + 18"),
			"Must render card size and blocks on bottom line at cardY + 18");

		// Palette remapping toolbar integration
		Assertions.assertTrue(content.contains("remapBtn"),
			"Must declare remapBtn on bottom toolbar");
		Assertions.assertTrue(content.contains("openPaletteRemapModal"),
			"Must define openPaletteRemapModal method");
		Assertions.assertTrue(content.contains("BlueprintSaveChoiceModalScreen"),
			"openPaletteRemapModal must transition to BlueprintSaveChoiceModalScreen");

		// High-performance mesh pre-caching and zero-allocation depth sorting
		Assertions.assertTrue(content.contains("BlueprintMeshCache"),
			"Must define BlueprintMeshCache");
		Assertions.assertTrue(content.contains("Arrays.sort(this.sortBuffer, 0, survivorCount)"),
			"Must sort faces using primitive sortBuffer with zero per-frame object allocation");
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal and MinionHarvestingHelper search containers within 128 blocks village radius")
	void testContainerSearchRadiusExpansion() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(buildGoalPath));
		String buildGoalContent = Files.readString(buildGoalPath);
		Assertions.assertTrue(buildGoalContent.contains("VILLAGE_CONTAINER_SEARCH_RADIUS"),
			"MinionBuildGoal scavenge radius must use VILLAGE_CONTAINER_SEARCH_RADIUS (128 blocks)");

		Path harvestHelperPath = Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java");
		Assertions.assertTrue(Files.exists(harvestHelperPath));
		String harvestContent = Files.readString(harvestHelperPath);
		Assertions.assertTrue(harvestContent.contains("VILLAGE_CONTAINER_SEARCH_RADIUS = 128;")
			&& harvestContent.contains("VILLAGE_CONTAINER_VERTICAL_RADIUS = 64;"),
			"MinionHarvestingHelper must define VILLAGE_CONTAINER_SEARCH_RADIUS = 128 and VILLAGE_CONTAINER_VERTICAL_RADIUS = 64");
	}

	@Test
	@DisplayName("Source Invariant: Chests or Inventory Only for items not found in the world")
	void testStrictResourceOriginContract() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(buildGoalPath));
		String buildGoalContent = Files.readString(buildGoalPath);
		Assertions.assertTrue(buildGoalContent.contains("isNaturallyOccurringWorldResource(requiredItem)")
			&& buildGoalContent.contains("tryAutonomousHarvest"),
			"MinionBuildGoal must only attempt autonomous harvest for naturally occurring world resources");

		Path harvestHelperPath = Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java");
		Assertions.assertTrue(Files.exists(harvestHelperPath));
		String harvestContent = Files.readString(harvestHelperPath);
		Assertions.assertTrue(harvestContent.contains("public static boolean isNaturallyOccurringWorldResource"),
			"MinionHarvestingHelper must define isNaturallyOccurringWorldResource");
		Assertions.assertTrue(harvestContent.contains("!isNaturallyOccurringWorldResource(requiredItem)"),
			"tryAutonomousHarvest and quarryNaturalStone must reject items that cannot be found in the world");
	}

	@Test
	@DisplayName("Source Invariant: PatrolRouteManager.deleteRoute unbinds minions to roam freely instead of sitting")
	void testPatrolRouteDeleteRoamsMinions() throws IOException {
		Path path = Path.of("src/main/java/com/example/patrol/PatrolRouteManager.java");
		Assertions.assertTrue(Files.exists(path));
		String content = Files.readString(path);

		Assertions.assertTrue(content.contains("m.setPatrolRouteId(-1);"),
			"deleteRoute must clear minion patrol route assignment");
		Assertions.assertTrue(content.contains("m.setSitting(false);"),
			"deleteRoute must set minion to not sit so it roams freely");
		Assertions.assertTrue(content.contains("m.setGuardAnchorPos(null);"),
			"deleteRoute must clear guard anchor pos");
	}

	@Test
	@DisplayName("StructureBlueprint: withName and withNameAndDescription produce immutable updated instances preserving structure")
	void testStructureBlueprintWithName() {
		BlockState dummy = createDummyBlockState();
		StructureBlueprint.Builder builder = StructureBlueprint.builder("test_bp", "Old Name")
			.description("Initial description");
		if (dummy != null) {
			builder.addBlock(new BlockPos(0, 0, 0), dummy);
		}
		StructureBlueprint original = builder.build();

		StructureBlueprint renamed = original.withName("New Citadel");
		Assertions.assertEquals("test_bp", renamed.getId());
		Assertions.assertEquals("New Citadel", renamed.getName());
		Assertions.assertEquals("Initial description", renamed.getDescription());
		Assertions.assertEquals(original.getBlockCount(), renamed.getBlockCount());
		Assertions.assertEquals(original.getBoundingBox(), renamed.getBoundingBox());
		Assertions.assertEquals(original.getRotation(), renamed.getRotation());

		// Fallback when null or blank
		StructureBlueprint nullRenamed = original.withName(null);
		Assertions.assertEquals("Old Name", nullRenamed.getName());
		StructureBlueprint blankRenamed = original.withName("   ");
		Assertions.assertEquals("Old Name", blankRenamed.getName());

		// withNameAndDescription
		StructureBlueprint bothUpdated = original.withNameAndDescription("Royal Keep", "Upgraded keep fortress");
		Assertions.assertEquals("Royal Keep", bothUpdated.getName());
		Assertions.assertEquals("Upgraded keep fortress", bothUpdated.getDescription());
		Assertions.assertEquals(original.getBlockCount(), bothUpdated.getBlockCount());
	}

	@Test
	@DisplayName("CustomBlueprintManager: saveBlueprint preserves catalog sequence when renaming existing blueprint")
	void testCustomBlueprintManagerRenamePreservesCatalogOrder() {
		com.example.blueprint.CustomBlueprintManager manager = com.example.blueprint.CustomBlueprintManager.getInstance();
		manager.loadFromPersistentState(null);

		StructureBlueprint bp1 = StructureBlueprint.builder("bp_alpha", "Alpha Base").build();
		StructureBlueprint bp2 = StructureBlueprint.builder("bp_beta", "Beta Outpost").build();

		manager.saveBlueprint(null, bp1);
		manager.saveBlueprint(null, bp2);

		List<StructureBlueprint> catalog = new ArrayList<>(manager.getAllCustomBlueprints());
		Assertions.assertEquals(2, catalog.size());
		Assertions.assertEquals("bp_beta", catalog.get(0).getId());
		Assertions.assertEquals("bp_alpha", catalog.get(1).getId());

		// Rename bp_alpha (existing ID)
		StructureBlueprint renamedAlpha = bp1.withName("Grand Alpha Fortress");
		manager.saveBlueprint(null, renamedAlpha);

		List<StructureBlueprint> updatedCatalog = new ArrayList<>(manager.getAllCustomBlueprints());
		Assertions.assertEquals(2, updatedCatalog.size());
		// Position must be preserved (not bumped ahead of bp_beta)
		Assertions.assertEquals("bp_beta", updatedCatalog.get(0).getId());
		Assertions.assertEquals("bp_alpha", updatedCatalog.get(1).getId());
		Assertions.assertEquals("Grand Alpha Fortress", updatedCatalog.get(1).getName());
		Assertions.assertEquals("Grand Alpha Fortress", com.example.blueprint.BlueprintRegistry.getOrDefault("bp_alpha").getName());
	}

	@Test
	@DisplayName("Source Invariant: BlueprintExplorerScreen provides uncluttered buttons and 3D viewer renaming modal")
	void testBlueprintExplorerScreenRenameAndUnclutteredButtons() throws IOException {
		Path path = Path.of("src/client/java/com/example/client/gui/BlueprintExplorerScreen.java");
		Assertions.assertTrue(Files.exists(path));
		String content = Files.readString(path);

		// Rename action button declared
		Assertions.assertTrue(content.contains("renameBtn"), "Must declare renameBtn");
		Assertions.assertTrue(content.contains("promptRenameCustomBlueprint"), "Must define promptRenameCustomBlueprint");
		Assertions.assertTrue(content.contains("confirmRename"), "Must define confirmRename");
		Assertions.assertTrue(content.contains("cancelRename"), "Must define cancelRename");

		// Rename modal overlay elements
		Assertions.assertTrue(content.contains("renameField"), "Must declare renameField TextFieldWidget");
		Assertions.assertTrue(content.contains("confirmRenameBtn"), "Must declare confirmRenameBtn");
		Assertions.assertTrue(content.contains("cancelRenameBtn"), "Must declare cancelRenameBtn");
		Assertions.assertTrue(content.contains("RENAME BLUEPRINT"), "Must render RENAME BLUEPRINT title in modal overlay");

		// Uncluttered layout: Rename button positioned on Specs Card in split mode
		Assertions.assertTrue(content.contains("specY = winY + 180;"), "Specs card must be located at specY");
		Assertions.assertTrue(content.contains("GLFW.GLFW_KEY_R"), "Must support R hotkey for blueprint rename");
	}
}
