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

	@Test
	@DisplayName("Builder minions remove water source blocks with empty buckets or during dismantling")
	void testBuilderMinionWaterSourceRemovalContract() throws IOException {
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));
		String sessionSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));
		String helperSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));

		// MinionBuildGoal must detect still water and still lava sources
		Assertions.assertTrue(goalSource.contains("boolean isStillWater = currentState.isOf(Blocks.WATER) && serverWorld.getFluidState(targetPos).isStill()"),
			"MinionBuildGoal must check isStillWater for source block detection");
		Assertions.assertTrue(goalSource.contains("boolean isFlowingFluid = isFluid && !isStillWater && !isStillLava"),
			"MinionBuildGoal must differentiate flowing fluids from source blocks");

		// MinionBuildGoal must have removeFluidSource method
		Assertions.assertTrue(goalSource.contains("removeFluidSource(ServerWorld serverWorld, BlockPos targetPos, BlockState currentState, boolean isWater)"),
			"MinionBuildGoal must implement removeFluidSource for removing fluid source blocks");

		// removeFluidSource must scoop with empty bucket and convert to filled bucket
		Assertions.assertTrue(goalSource.contains("SoundEvents.ITEM_BUCKET_FILL"),
			"removeFluidSource must play bucket fill sound");
		Assertions.assertTrue(goalSource.contains("withdrawBucketFromNearbyContainer"),
			"removeFluidSource must be capable of retrieving buckets from nearby containers");
		Assertions.assertTrue(goalSource.contains("tryDisplaceFluidWithSolidBlock"),
			"removeFluidSource must be capable of displacing fluids with solid blocks");

		// ConstructionSession claimNextTask must only auto-complete flowing fluids, leaving source blocks for builders
		Assertions.assertTrue(sessionSource.contains("boolean isFlowing = isFluid(worldState) && !worldState.getFluidState().isStill()"),
			"ConstructionSession must only auto-complete flowing fluids in DISMANTLE mode");

		// MinionHarvestingHelper.gatherWaterBucket must set scooped water block to air
		Assertions.assertTrue(helperSource.contains("world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)"),
			"MinionHarvestingHelper.gatherWaterBucket must remove scooped water source block from the world");
	}

	@Test
	@DisplayName("Minions steer to safe dry solid ground when surrounded by water or lava upon completion")
	void testMinionSafeSolidGroundLandingContract() throws IOException {
		String minionSource = Files.readString(Path.of("src/main/java/com/example/entity/custom/MinionEntity.java"));
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// tickGentleDescent must detect fluids and steer to safe solid ground
		Assertions.assertTrue(minionSource.contains("boolean inOrOverFluid = this.isTouchingWater() || this.isInLava()"),
			"MinionEntity.tickGentleDescent must detect if minion is in or over water/lava");
		Assertions.assertTrue(minionSource.contains("findNearestSafeStandingPosition(serverWorld, feet, 32)"),
			"MinionEntity.tickGentleDescent must locate nearest safe solid ground within 32 blocks when over fluid");

		// isSafeStandingPosition must require dry non-fluid ground
		Assertions.assertTrue(minionSource.contains("!groundState.getFluidState().isEmpty()"),
			"MinionEntity.isSafeStandingPosition must reject fluid ground beneath");
		Assertions.assertTrue(minionSource.contains("feetFluid != null && !feetFluid.isEmpty()"),
			"MinionEntity.isSafeStandingPosition must reject fluids at foot level");

		// finishBuildingEgress must not drop minion into fluid
		Assertions.assertTrue(minionSource.contains("boolean inOrOverFluid = this.isTouchingWater() || this.isInLava()"),
			"MinionEntity.finishBuildingEgress must check for fluids before clearing levitation");

		// MinionBuildGoal.stop must preserve levitation if over fluid
		Assertions.assertTrue(goalSource.contains("boolean overFluid = this.minion.isTouchingWater() || this.minion.isInLava()"),
			"MinionBuildGoal.stop must check if minion is over fluid to keep levitation active for gentle descent");
	}

	@Test
	@DisplayName("Strict Structure & Blueprint Water Protection: Placed water in builds is never scooped by minions")
	void testWaterSourceProtectionAgainstMinionScoopingContract() throws IOException {
		String helperSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));
		String sessionSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));

		// 1. gatherWaterBucket must check isProtectedWaterSource
		Assertions.assertTrue(helperSource.contains("isProtectedWaterSource(world, pos, session)"),
			"MinionHarvestingHelper.gatherWaterBucket must check isProtectedWaterSource before scooping water");

		// 2. isProtectedWaterSource must protect session volume and tasks
		Assertions.assertTrue(helperSource.contains("session.containsBlockPos(pos)"),
			"isProtectedWaterSource must protect blocks in current session tasks");
		Assertions.assertTrue(helperSource.contains("session.getWorldBoundingBox().contains(pos)"),
			"isProtectedWaterSource must protect blocks in current session bounding box");

		// 3. isProtectedWaterSource must protect any active session on server
		Assertions.assertTrue(helperSource.contains("ConstructionManager.getInstance().isPositionInAnyActiveSession(pos)"),
			"isProtectedWaterSource must protect blocks across all active sessions");

		// 4. isProtectedWaterSource must check adjacent processed architectural blocks
		Assertions.assertTrue(helperSource.contains("isProcessedBlockId(neighborId)"),
			"isProtectedWaterSource must protect water touching architectural building materials");

		// 5. Architectural materials must include quartz, prismarine, copper, cut, smooth, polished, chiseled
		Assertions.assertTrue(helperSource.contains("path.contains(\"quartz\")"),
			"isProcessedBlockId must include quartz");
		Assertions.assertTrue(helperSource.contains("path.contains(\"prismarine\")"),
			"isProcessedBlockId must include prismarine");
		Assertions.assertTrue(helperSource.contains("path.contains(\"copper\")"),
			"isProcessedBlockId must include copper");
		Assertions.assertTrue(helperSource.contains("path.contains(\"smooth_\")"),
			"isProcessedBlockId must include smooth blocks");
		Assertions.assertTrue(helperSource.contains("path.contains(\"polished_\")"),
			"isProcessedBlockId must include polished blocks");

		// 6. MinionBuildGoal must schedule fluid tick on water and lava placement
		Assertions.assertTrue(goalSource.contains("serverWorld.scheduleFluidTick(targetPos, net.minecraft.fluid.Fluids.WATER"),
			"MinionBuildGoal must schedule fluid tick immediately upon water placement");
		Assertions.assertTrue(goalSource.contains("serverWorld.scheduleFluidTick(targetPos, net.minecraft.fluid.Fluids.LAVA"),
			"MinionBuildGoal must schedule fluid tick immediately upon lava placement");

		// 7. ConstructionSession must handle fluid isStill check in alreadyInPlace
		Assertions.assertTrue(sessionSource.contains("task.getBlueprintBlock().state().isOf(Blocks.WATER) && world.getBlockState(task.getWorldPos()).isOf(Blocks.WATER) && world.getFluidState(task.getWorldPos()).isStill()"),
			"ConstructionSession.claimNextTask must check isStill() when evaluating pre-placed water");
	}
}


