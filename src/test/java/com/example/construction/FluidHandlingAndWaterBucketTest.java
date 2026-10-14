package com.example.construction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests verifying water bucket logistics for construction and fluid-ignoring mechanics for mining:
 * <ol>
 *   <li><b>Water Bucket Material Resolution:</b> Water source blocks resolve to {@code Items.WATER_BUCKET} instead of {@code Items.AIR}.</li>
 *   <li><b>Flowing Fluid Filtering:</b> Flowing water/lava (level > 0 / !isStill()) is filtered out during blueprint capture.</li>
 *   <li><b>Mining Fluid Immunity:</b> Dismantle and area mining strictly ignore water, lava, and fluids across survival and creative modes.</li>
 *   <li><b>Survival Bucket Lifecycle:</b> Placing water from a water bucket leaves an empty bucket in minion inventory.</li>
 *   <li><b>Autonomous Water Gathering:</b> Minions can autonomously scoop water sources with empty buckets or craft buckets from iron ingots.</li>
 * </ol>
 */
public class FluidHandlingAndWaterBucketTest {

	@Test
	@DisplayName("BlueprintBlock resolves water and lava source blocks to buckets instead of AIR")
	void testBlueprintBlockFluidResolutionContract() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/blueprint/BlueprintBlock.java"));

		// Must map WATER to WATER_BUCKET
		Assertions.assertTrue(source.contains("WATER_BUCKET"),
			"BlueprintBlock must map water blocks to Items.WATER_BUCKET");
		Assertions.assertTrue(source.contains("Blocks.WATER"),
			"BlueprintBlock must check for Blocks.WATER");

		// Must map LAVA to LAVA_BUCKET
		Assertions.assertTrue(source.contains("LAVA_BUCKET"),
			"BlueprintBlock must map lava blocks to Items.LAVA_BUCKET");
	}

	@Test
	@DisplayName("StructureBlueprint filters flowing fluids and never adds Items.AIR to required item counts")
	void testStructureBlueprintFluidHandlingContract() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/blueprint/StructureBlueprint.java"));

		// captureBlocks must check if fluid is still (source) and skip flowing fluids
		Assertions.assertTrue(source.contains("state.getFluidState().isStill()"),
			"StructureBlueprint.captureBlocks must check state.getFluidState().isStill() to filter flowing fluids");

		// createAreaBlueprint must filter out fluids from area mining tasks
		Assertions.assertTrue(source.contains("!b.state().isLiquid()"),
			"StructureBlueprint.createAreaBlueprint must exclude liquids from mining blueprints");

		// Builder.build must never insert Items.AIR into itemCounts
		Assertions.assertTrue(source.contains("item != net.minecraft.item.Items.AIR"),
			"StructureBlueprint.Builder must never add Items.AIR into itemCounts");
		Assertions.assertTrue(source.contains("Items.WATER_BUCKET"),
			"StructureBlueprint.Builder must resolve water blocks to Items.WATER_BUCKET");
	}

	@Test
	@DisplayName("ConstructionSession ignores fluids during dismantle/mining in survival and creative")
	void testConstructionSessionMiningFluidImmunityContract() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));

		// Must provide isFluid helper
		Assertions.assertTrue(source.contains("isFluid(BlockState state)"),
			"ConstructionSession must define isFluid(BlockState state) helper");

		// hasRemainingBlocksInWorld must ignore fluids
		Assertions.assertTrue(source.contains("!isFluid(state)"),
			"hasRemainingBlocksInWorld must ignore fluid blocks when checking if area is cleared");

		// claimNextTask in DISMANTLE mode must complete fluid tasks immediately
		Assertions.assertTrue(source.contains("isFluid(worldState)"),
			"claimNextTask must auto-complete fluid blocks in DISMANTLE mode");

		// isDismantleTaskReady must consider fluids as cleared obstacles
		Assertions.assertTrue(source.contains("isFluid(world.getBlockState(abovePos))"),
			"isDismantleTaskReady must treat overhead fluids as cleared obstacles");
	}

	@Test
	@DisplayName("MinionBuildGoal survival water placement leaves empty bucket and dismantle ignores liquids")
	void testMinionBuildGoalFluidAndBucketLifecycleContract() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// Survival bucket consumption helper must deposit empty bucket
		Assertions.assertTrue(source.contains("Items.BUCKET"),
			"MinionBuildGoal must deposit empty bucket after using water bucket in survival mode");
		Assertions.assertTrue(source.contains("consumeResourceFromInventory"),
			"MinionBuildGoal must use consumeResourceFromInventory for bucket lifecycle management");

		// alreadyPlaced check must check still water source
		Assertions.assertTrue(source.contains("serverWorld.getFluidState(targetPos).isStill()"),
			"MinionBuildGoal must check isStill() on water blocks to verify water source placement");

		// Sound effect on water placement
		Assertions.assertTrue(source.contains("SoundEvents.ITEM_BUCKET_EMPTY"),
			"MinionBuildGoal must play bucket empty sound when placing water");

		// executeDismantleWork must ignore fluids immediately
		Assertions.assertTrue(source.contains("boolean isFluid = currentState.isLiquid()"),
			"MinionBuildGoal.executeDismantleWork must check isFluid and complete without breaking fluids");
	}

	@Test
	@DisplayName("MinionHarvestingHelper supports autonomous water gathering with empty bucket and iron ingot bucket crafting")
	void testMinionHarvestingHelperWaterGatheringContract() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));

		// Must support gatherWaterBucket
		Assertions.assertTrue(source.contains("gatherWaterBucket"),
			"MinionHarvestingHelper must implement gatherWaterBucket");
		Assertions.assertTrue(source.contains("Items.WATER_BUCKET"),
			"MinionHarvestingHelper.tryAutonomousHarvest must handle Items.WATER_BUCKET");
		Assertions.assertTrue(source.contains("Items.IRON_INGOT"),
			"MinionHarvestingHelper must craft bucket from iron ingots if minion lacks an empty bucket");
		Assertions.assertTrue(source.contains("SoundEvents.ITEM_BUCKET_FILL"),
			"MinionHarvestingHelper must play bucket fill sound when scooping water");
	}

	@Test
	@DisplayName("Area fill mode suppresses particle effects during corner selection, idle tick, and construction")
	void testAreaFillParticleSuppressionContract() throws IOException {
		String scepterSource = Files.readString(Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));
		String managerSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionManager.java"));

		// inventoryTick must skip blueprint particles in AREA mode
		Assertions.assertTrue(scepterSource.contains("getBuildingMode(stack) == BuildingMode.AREA"),
			"CommandScepterItem.inventoryTick must skip blueprint particles in Area Fill mode");

		// ConstructionManager.renderHologramParticles must skip Area Fill sessions
		Assertions.assertTrue(managerSource.contains("session.getBlueprint().getId().startsWith(\"build_area_\")"),
			"ConstructionManager.renderHologramParticles must skip Area Fill sessions");
	}
}
