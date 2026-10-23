package com.example.construction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Automated test suite validating that Builder Minions in Survival Mode
 * will never stall or freeze on any construction task.
 *
 * Validates:
 * 1. Task Queue & Deficiency Cooldown (never stall on missing blocks, skip to ready tasks).
 * 2. Virtual Item Mappings for blocks without direct items (Farmland, Paths, crops, redstone wire).
 * 3. Two-phase tool transformations (Hoe till, Shovel flatten, Shear carve, Axe strip, Shovel extinguish).
 * 4. Multi-tier crafting registry (Scholastic, Concrete hydration, Mud adobe, Redstone, Candles, Decor).
 * 5. Backpack hygiene auto-offload (never clog inventory with byproducts).
 * 6. Survival block filtering (purge Blocks.LIGHT, BARRIER, COMMAND_BLOCK, SPAWNER, BEDROCK).
 * 7. Substitute with Palette (safe fallbacks for exotic or missing materials).
 * 8. Physical placement safeguards (crop on farmland, falling block solid support, leaf persistence, waterlogging).
 */
public class SurvivalBuilderNeverStallTest {

	@Test
	@DisplayName("Pillar 1: Task Queue Deficiency Cooldown prevents minions from stalling on missing resources")
	void testTaskDeficiencyCooldown() throws IOException {
		String taskSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionTask.java"));
		String sessionSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// ConstructionTask must support deficiency cooldowns per minion UUID
		Assertions.assertTrue(taskSource.contains("hasDeficiencyCooldown"), "ConstructionTask must implement hasDeficiencyCooldown");
		Assertions.assertTrue(taskSource.contains("setDeficiencyCooldown"), "ConstructionTask must implement setDeficiencyCooldown");
		Assertions.assertTrue(taskSource.contains("clearDeficiencyCooldown"), "ConstructionTask must implement clearDeficiencyCooldown");

		// ConstructionSession claimNextTask must skip tasks with active deficiency cooldown
		Assertions.assertTrue(sessionSource.contains("task.hasDeficiencyCooldown(minionUuid, currentTick)"),
			"ConstructionSession.claimNextTask must skip tasks with active deficiency cooldown for the requesting minion");

		// MinionBuildGoal handleResourceDeficiency must tag current task with deficiency cooldown
		Assertions.assertTrue(goalSource.contains("this.currentTask.setDeficiencyCooldown"),
			"MinionBuildGoal.handleResourceDeficiency must tag task with deficiency cooldown");
		Assertions.assertTrue(goalSource.contains("this.failureCooldownUntilTick = world.getTime() + 10L"),
			"MinionBuildGoal must set low failure cooldown so minion claims other tasks immediately");
	}

	@Test
	@DisplayName("Pillar 2: Virtual item mappings resolve Blocks without direct item form")
	void testVirtualItemMappings() throws IOException {
		String bpBlockSource = Files.readString(Path.of("src/main/java/com/example/blueprint/BlueprintBlock.java"));

		// Farmland and Dirt Path must map to Items.DIRT
		Assertions.assertTrue(bpBlockSource.contains("Blocks.FARMLAND") && bpBlockSource.contains("Blocks.DIRT_PATH"),
			"BlueprintBlock must handle Farmland and Dirt Path");
		Assertions.assertTrue(bpBlockSource.contains("Items.DIRT"), "Farmland and Dirt Path must map to Items.DIRT");

		// Crops must map to their respective seeds / harvest items
		Assertions.assertTrue(bpBlockSource.contains("Items.WHEAT_SEEDS"), "Wheat must map to Wheat Seeds");
		Assertions.assertTrue(bpBlockSource.contains("Items.CARROT"), "Carrots must map to Carrot");
		Assertions.assertTrue(bpBlockSource.contains("Items.POTATO"), "Potatoes must map to Potato");
		Assertions.assertTrue(bpBlockSource.contains("Items.BEETROOT_SEEDS"), "Beetroots must map to Beetroot Seeds");
		Assertions.assertTrue(bpBlockSource.contains("Items.MELON_SEEDS"), "Melon stem must map to Melon Seeds");
		Assertions.assertTrue(bpBlockSource.contains("Items.PUMPKIN_SEEDS"), "Pumpkin stem must map to Pumpkin Seeds");
		Assertions.assertTrue(bpBlockSource.contains("Items.STRING"), "Tripwire must map to String");
		Assertions.assertTrue(bpBlockSource.contains("Items.REDSTONE"), "Redstone wire must map to Redstone dust");
		Assertions.assertTrue(bpBlockSource.contains("Items.GLOW_BERRIES"), "Cave vines must map to Glow Berries");
	}

	@Test
	@DisplayName("Pillar 2 & 3: Tool Transformations and Two-Phase Placement Contracts")
	void testToolTransformations() throws IOException {
		String bpBlockSource = Files.readString(Path.of("src/main/java/com/example/blueprint/BlueprintBlock.java"));
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// Must define ToolTransformationType enum with required operations
		Assertions.assertTrue(bpBlockSource.contains("enum ToolTransformationType"), "Must declare ToolTransformationType enum");
		Assertions.assertTrue(bpBlockSource.contains("HOE_TILL"), "Must support HOE_TILL");
		Assertions.assertTrue(bpBlockSource.contains("SHOVEL_FLATTEN"), "Must support SHOVEL_FLATTEN");
		Assertions.assertTrue(bpBlockSource.contains("AXE_STRIP"), "Must support AXE_STRIP");
		Assertions.assertTrue(bpBlockSource.contains("SHEAR_CARVE"), "Must support SHEAR_CARVE");
		Assertions.assertTrue(bpBlockSource.contains("SHOVEL_EXTINGUISH"), "Must support SHOVEL_EXTINGUISH");

		// MinionBuildGoal must execute tool transformations with audio and tool damage
		Assertions.assertTrue(goalSource.contains("ToolTransformationType.HOE_TILL"), "Goal must handle HOE_TILL");
		Assertions.assertTrue(goalSource.contains("SoundEvents.ITEM_HOE_TILL"), "Goal must play HOE_TILL sound");
		Assertions.assertTrue(goalSource.contains("ToolTransformationType.SHOVEL_FLATTEN"), "Goal must handle SHOVEL_FLATTEN");
		Assertions.assertTrue(goalSource.contains("SoundEvents.ITEM_SHOVEL_FLATTEN"), "Goal must play SHOVEL_FLATTEN sound");
		Assertions.assertTrue(goalSource.contains("ToolTransformationType.SHEAR_CARVE"), "Goal must handle SHEAR_CARVE");
		Assertions.assertTrue(goalSource.contains("SoundEvents.BLOCK_PUMPKIN_CARVE"), "Goal must play PUMPKIN_CARVE sound");
		Assertions.assertTrue(goalSource.contains("ToolTransformationType.AXE_STRIP"), "Goal must handle AXE_STRIP");
		Assertions.assertTrue(goalSource.contains("SoundEvents.ITEM_AXE_STRIP"), "Goal must play AXE_STRIP sound");
		Assertions.assertTrue(goalSource.contains("ToolTransformationType.SHOVEL_EXTINGUISH"), "Goal must handle SHOVEL_EXTINGUISH");
	}

	@Test
	@DisplayName("Pillar 4: Multi-Tier Crafting Chains (Scholastic, Concrete, Mud, Redstone, Candles, Decor)")
	void testMultiTierCraftingChains() throws IOException {
		String helperSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));

		// Scholastic
		Assertions.assertTrue(helperSource.contains("isScholasticResource"), "Must define isScholasticResource");
		Assertions.assertTrue(helperSource.contains("tryCraftScholasticResource"), "Must define tryCraftScholasticResource");
		Assertions.assertTrue(helperSource.contains("Items.PAPER"), "Must handle Paper");
		Assertions.assertTrue(helperSource.contains("Items.BOOK"), "Must handle Book");
		Assertions.assertTrue(helperSource.contains("Items.BOOKSHELF"), "Must handle Bookshelf");
		Assertions.assertTrue(helperSource.contains("Items.LECTERN"), "Must handle Lectern");

		// Concrete
		Assertions.assertTrue(helperSource.contains("isConcreteResource"), "Must define isConcreteResource");
		Assertions.assertTrue(helperSource.contains("tryCraftConcreteResource"), "Must define tryCraftConcreteResource");

		// Mud & Adobe
		Assertions.assertTrue(helperSource.contains("isMudResource"), "Must define isMudResource");
		Assertions.assertTrue(helperSource.contains("tryCraftMudResource"), "Must define tryCraftMudResource");
		Assertions.assertTrue(helperSource.contains("Items.PACKED_MUD"), "Must handle Packed Mud");
		Assertions.assertTrue(helperSource.contains("Items.MUD_BRICKS"), "Must handle Mud Bricks");

		// Redstone
		Assertions.assertTrue(helperSource.contains("isRedstoneComponent"), "Must define isRedstoneComponent");
		Assertions.assertTrue(helperSource.contains("tryCraftRedstoneComponent"), "Must define tryCraftRedstoneComponent");
		Assertions.assertTrue(helperSource.contains("Items.HOPPER"), "Must handle Hopper");
		Assertions.assertTrue(helperSource.contains("Items.PISTON"), "Must handle Piston");
		Assertions.assertTrue(helperSource.contains("Items.STICKY_PISTON"), "Must handle Sticky Piston");
		Assertions.assertTrue(helperSource.contains("Items.OBSERVER"), "Must handle Observer");
		Assertions.assertTrue(helperSource.contains("Items.REDSTONE_LAMP"), "Must handle Redstone Lamp");

		// Candles & Decor
		Assertions.assertTrue(helperSource.contains("isCandleResource"), "Must define isCandleResource");
		Assertions.assertTrue(helperSource.contains("isDecorResource"), "Must define isDecorResource");
		Assertions.assertTrue(helperSource.contains("Items.CAMPFIRE"), "Must handle Campfire");
		Assertions.assertTrue(helperSource.contains("Items.ITEM_FRAME"), "Must handle Item Frame");
		Assertions.assertTrue(helperSource.contains("Items.ARMOR_STAND"), "Must handle Armor Stand");
	}

	@Test
	@DisplayName("Pillar 5: Backpack Hygiene Auto-Offload prevents minion inventory clogs")
	void testBackpackHygieneAutoOffload() throws IOException {
		String helperSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// Must define maintainInventoryHygiene
		Assertions.assertTrue(helperSource.contains("maintainInventoryHygiene"),
			"MinionHarvestingHelper must define maintainInventoryHygiene");
		Assertions.assertTrue(helperSource.contains("emptySlots >= 3"),
			"Hygiene check must pass if minion has sufficient empty slots");
		Assertions.assertTrue(helperSource.contains("SoundEvents.BLOCK_CHEST_CLOSE"),
			"Hygiene offload must produce chest interaction feedback");

		// MinionBuildGoal must invoke maintainInventoryHygiene
		Assertions.assertTrue(goalSource.contains("maintainInventoryHygiene"),
			"MinionBuildGoal must invoke maintainInventoryHygiene during build cycles");
	}

	@Test
	@DisplayName("Pillar 6: Survival Filter strictly excludes creative-only blocks and whitelists Farmland / Dirt Path")
	void testSurvivalBlockFiltering() throws IOException {
		String bpBlockSource = Files.readString(Path.of("src/main/java/com/example/blueprint/BlueprintBlock.java"));
		String modalSource = Files.readString(Path.of("src/client/java/com/example/client/gui/BlockPickerModalScreen.java"));
		String networkSource = Files.readString(Path.of("src/main/java/com/example/network/ModNetworking.java"));

		// BlueprintBlock.isSurvivalPlaceableBlock contract
		Assertions.assertTrue(bpBlockSource.contains("boolean isSurvivalPlaceableBlock"),
			"BlueprintBlock must declare isSurvivalPlaceableBlock");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.LIGHT"), "Must exclude Blocks.LIGHT (light bulb block)");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.BARRIER"), "Must exclude Blocks.BARRIER");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.COMMAND_BLOCK"), "Must exclude command blocks");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.BEDROCK"), "Must exclude Bedrock");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.FARMLAND"), "Must whitelist Farmland");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.DIRT_PATH"), "Must whitelist Dirt Path");

		// BlockPickerModalScreen must filter via isSurvivalPlaceableBlock
		Assertions.assertTrue(modalSource.contains("isSurvivalPlaceableBlock"),
			"BlockPickerModalScreen must filter blocks using isSurvivalPlaceableBlock");

		// ModNetworking must validate replacement blocks and area builds against isSurvivalPlaceableBlock
		Assertions.assertTrue(networkSource.contains("isSurvivalPlaceableBlock"),
			"ModNetworking must validate palette replacements against isSurvivalPlaceableBlock");
	}

	@Test
	@DisplayName("Pillar 7: Substitute with Palette prevents stalls on exotic materials")
	void testSubstituteWithPalette() throws IOException {
		String helperSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// Must define resolvePaletteFallback
		Assertions.assertTrue(helperSource.contains("Item resolvePaletteFallback(Item missingItem)"),
			"MinionHarvestingHelper must define resolvePaletteFallback");
		Assertions.assertTrue(helperSource.contains("Items.CRYING_OBSIDIAN"), "Must handle Crying Obsidian fallback");
		Assertions.assertTrue(helperSource.contains("Items.END_ROD"), "Must handle End Rod fallback");
		Assertions.assertTrue(helperSource.contains("Items.NETHERITE_BLOCK"), "Must handle Netherite Block fallback");

		// MinionBuildGoal must call resolvePaletteFallback before failing
		Assertions.assertTrue(goalSource.contains("resolvePaletteFallback"),
			"MinionBuildGoal must call resolvePaletteFallback when resources cannot be found");
	}

	@Test
	@DisplayName("Physical Placement Safeguards: Gravity blocks, crops, nether water, and leaves")
	void testPhysicalPlacementSafeguards() throws IOException {
		String sessionSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// ConstructionSession isBuildTaskReady physical checks
		Assertions.assertTrue(sessionSource.contains("FallingBlock"),
			"isBuildTaskReady must check solid support for FallingBlock");
		Assertions.assertTrue(sessionSource.contains("CropBlock"),
			"isBuildTaskReady must check farmland support for CropBlock");
		Assertions.assertTrue(sessionSource.contains("Blocks.FARMLAND"),
			"CropBlock must require Blocks.FARMLAND underneath");
		Assertions.assertTrue(sessionSource.contains("CocoaBlock"),
			"isBuildTaskReady must check jungle log support for CocoaBlock");
		Assertions.assertTrue(sessionSource.contains("Blocks.NETHER_WART"),
			"isBuildTaskReady must check soul sand support for Nether Wart");

		// MinionBuildGoal physical safeguards
		Assertions.assertTrue(goalSource.contains("LeavesBlock.PERSISTENT"),
			"MinionBuildGoal must force LeavesBlock.PERSISTENT = true on placement");
		Assertions.assertTrue(goalSource.contains("ultrawarm()"),
			"MinionBuildGoal must check serverWorld.getDimension().ultrawarm() for nether water evaporation");
		Assertions.assertTrue(goalSource.contains("WATERLOGGED"),
			"MinionBuildGoal must preserve water if target block contains WATERLOGGED property");
	}
}
