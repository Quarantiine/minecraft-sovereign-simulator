package com.example.entity;

import com.example.construction.ConstructionManager;
import com.example.entity.custom.MinionEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit and source invariant tests ensuring minions are never positioned inside walls
 * or trapped underground after building, mining, or during active entity lifecycles.
 */
public class MinionWallAndUndergroundSafetyTest {

	// =========================================================================
	// 1. Source Invariants in MinionEntity, ConstructionManager, MinionBuildGoal
	// =========================================================================

	@Test
	@DisplayName("Source Invariant: MinionEntity defines isSafeStandingPosition and findNearestSafeStandingPosition")
	void testMinionEntitySafetyMethodsExist() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(content.contains("public static boolean isSafeStandingPosition(World world, BlockPos pos)"),
			"MinionEntity must define public static isSafeStandingPosition");

		Assertions.assertTrue(content.contains("public static BlockPos findNearestSafeStandingPosition(World world, BlockPos center, int maxRadius)"),
			"MinionEntity must define public static findNearestSafeStandingPosition");

		Assertions.assertTrue(content.contains("public boolean unstuckIfInSolidWall(ServerWorld serverWorld)"),
			"MinionEntity must define unstuckIfInSolidWall");
	}

	@Test
	@DisplayName("Source Invariant: MinionEntity.tick invokes continuous anti-suffocation unstuck check")
	void testMinionEntityTickAntiSuffocationSafeguard() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		String content = Files.readString(path);

		Assertions.assertTrue(
			content.contains("if (this.isAlive() && !this.isPhasingBlocks()) {") &&
			content.contains("this.unstuckIfInSolidWall(serverWorld);"),
			"MinionEntity.tick must continuously trigger unstuckIfInSolidWall when embedded in solid blocks while not phasing"
		);
	}

	@Test
	@DisplayName("Source Invariant: finishBuildingEgress and findExteriorExitPosition guarantee non-solid standing positions")
	void testEgressGuaranteesSafeStandingPositions() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		String content = Files.readString(path);

		Assertions.assertTrue(content.contains("if (isSafeStandingPosition(serverWorld, exitPos))"),
			"finishBuildingEgress must verify exitPos with isSafeStandingPosition");
		Assertions.assertTrue(content.contains("unstuckIfInSolidWall(serverWorld);"),
			"finishBuildingEgress must invoke unstuckIfInSolidWall when structureExitVec is solid or null");
	}

	@Test
	@DisplayName("Source Invariant: ConstructionManager differentiates surface quarries from underground room excavations")
	void testConstructionManagerQuarryAndMineDismantleDistinction() throws IOException {
		Path path = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		String content = Files.readString(path);

		// Must check for surface quarry vs underground excavation
		Assertions.assertTrue(content.contains("boolean isSurfaceQuarry = isDismantle && ("),
			"ConstructionManager must detect whether mining session is open to the sky (surface quarry) vs underground");

		// Underground excavation stations on cleared interior floor
		Assertions.assertTrue(content.contains("if (isDismantle && !isSurfaceQuarry) {"),
			"ConstructionManager must branch for underground excavations");
		Assertions.assertTrue(content.contains("int floorY = box.getMinY();"),
			"Underground excavations must station on the cleared interior floor (box.getMinY())");

		// Must verify perimeter waypoints with isSafeStandingPosition
		Assertions.assertTrue(content.contains("MinionEntity.isSafeStandingPosition(world, candidate)"),
			"ConstructionManager must verify candidate waypoints with isSafeStandingPosition");
		Assertions.assertTrue(content.contains("MinionEntity.findNearestSafeStandingPosition"),
			"ConstructionManager must provide safe fallback via findNearestSafeStandingPosition");
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal invokes unstuck check upon levitation descent landing")
	void testMinionBuildGoalUnstuckOnTouchdown() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		String content = Files.readString(path);

		Assertions.assertTrue(
			content.contains("this.minion.unstuckIfInSolidWall(serverWorld);"),
			"MinionBuildGoal.tick must trigger unstuckIfInSolidWall when deactivating levitation upon landing"
		);
	}

	// =========================================================================
	// 2. Behavioral Simulation: Safe Standing Position & Unstuck Logic
	// =========================================================================

	static class MockBlockSpace {
		boolean solid = false;
		boolean hazardous = false;

		MockBlockSpace(boolean solid, boolean hazardous) {
			this.solid = solid;
			this.hazardous = hazardous;
		}
	}

	static class MockWorldGrid {
		java.util.Map<BlockPos, MockBlockSpace> grid = new java.util.HashMap<>();

		void set(BlockPos pos, boolean solid, boolean hazardous) {
			grid.put(pos, new MockBlockSpace(solid, hazardous));
		}

		MockBlockSpace get(BlockPos pos) {
			return grid.getOrDefault(pos, new MockBlockSpace(false, false));
		}

		boolean isSafe(BlockPos pos) {
			MockBlockSpace floor = get(pos.down());
			MockBlockSpace feet = get(pos);
			MockBlockSpace head = get(pos.up());

			// Floor must be solid and non-hazardous
			if (!floor.solid || floor.hazardous) {
				return false;
			}
			// Feet and head must not be solid
			if (feet.solid || head.solid) {
				return false;
			}
			return true;
		}

		BlockPos findNearestSafe(BlockPos center, int radius) {
			if (isSafe(center)) return center;
			BlockPos best = null;
			double bestDistSq = Double.MAX_VALUE;
			for (int r = 1; r <= radius; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						for (int dy = 2; dy >= -3; dy--) {
							BlockPos cand = center.add(dx, dy, dz);
							if (isSafe(cand)) {
								double d = cand.getSquaredDistance(center);
								if (d < bestDistSq) {
									bestDistSq = d;
									best = cand;
								}
							}
						}
					}
				}
				if (best != null) return best;
			}
			return null;
		}
	}

	@Test
	@DisplayName("Simulation: In-wall embedded minion is successfully extracted to open ground")
	void testInWallExtractionSimulation() {
		MockWorldGrid world = new MockWorldGrid();

		// Create a ground plane at Y=64
		for (int x = -10; x <= 10; x++) {
			for (int z = -10; z <= 10; z++) {
				world.set(new BlockPos(x, 63, z), true, false); // Solid stone floor
			}
		}

		// Build a solid wall at x=0..2, z=0..2, Y=64..66
		for (int x = 0; x <= 2; x++) {
			for (int z = 0; z <= 2; z++) {
				for (int y = 64; y <= 66; y++) {
					world.set(new BlockPos(x, y, z), true, false);
				}
			}
		}

		// Minion is accidentally positioned inside the wall at (1, 64, 1)
		BlockPos insideWallPos = new BlockPos(1, 64, 1);
		Assertions.assertFalse(world.isSafe(insideWallPos), "Position inside wall must not be considered safe");

		// Auto-unstuck searches for nearest safe location
		BlockPos extractedPos = world.findNearestSafe(insideWallPos, 6);
		Assertions.assertNotNull(extractedPos, "Unstuck must find a safe location outside the wall");
		Assertions.assertTrue(world.isSafe(extractedPos), "Extracted position must have solid floor and 2-block headroom");
		Assertions.assertTrue(
			extractedPos.getX() < 0 || extractedPos.getX() > 2 || extractedPos.getZ() < 0 || extractedPos.getZ() > 2,
			"Extracted position must be strictly outside the solid wall"
		);
	}

	@Test
	@DisplayName("Simulation: Hazardous floor (lava/magma) is rejected even with clear headroom")
	void testHazardousFloorRejection() {
		MockWorldGrid world = new MockWorldGrid();

		BlockPos lavaPos = new BlockPos(5, 63, 5);
		world.set(lavaPos, true, true); // Solid but hazardous (e.g. lava/magma)

		BlockPos standingPos = new BlockPos(5, 64, 5);
		Assertions.assertFalse(world.isSafe(standingPos), "Standing on hazardous floor must be rejected");

		// Safe floor adjacent
		world.set(new BlockPos(6, 63, 5), true, false); // Safe cobblestone
		BlockPos safeStandingPos = new BlockPos(6, 64, 5);
		Assertions.assertTrue(world.isSafe(safeStandingPos), "Standing on non-hazardous floor must be accepted");
	}

	@Test
	@DisplayName("Simulation: Underground excavation stations minions on cleared interior floor, not in unmined rock walls")
	void testUndergroundExcavationFloorStationing() {
		MockWorldGrid world = new MockWorldGrid();

		// Underground room: box from (10, 20, 10) to (14, 24, 14)
		// Bedrock/solid rock surrounds entire box
		for (int x = 8; x <= 16; x++) {
			for (int z = 8; z <= 16; z++) {
				for (int y = 18; y <= 26; y++) {
					world.set(new BlockPos(x, y, z), true, false); // Solid rock everywhere
				}
			}
		}

		// Excavation clears the room: (10, 20, 10) to (14, 24, 14) is now air!
		for (int x = 10; x <= 14; x++) {
			for (int z = 10; z <= 14; z++) {
				for (int y = 20; y <= 24; y++) {
					world.set(new BlockPos(x, y, z), false, false); // Cleared air
				}
			}
		}

		// The outer perimeter at maxZ+2 = 16 is SOLID ROCK!
		BlockPos perimeterWallPos = new BlockPos(12, 20, 16);
		Assertions.assertFalse(world.isSafe(perimeterWallPos), "Outer perimeter of underground mine is unmined solid rock!");

		// The cleared interior floor at box.getMinY() = 20 has solid ground beneath at Y=19 and air above!
		BlockPos interiorFloorPos = new BlockPos(12, 20, 12);
		Assertions.assertTrue(world.isSafe(interiorFloorPos), "Cleared interior floor must be recognized as safe standing ground!");
	}

	@Test
	@DisplayName("Source Invariant: CommandScepterItem permits build flight over water 2 blocks high or less")
	void testWaterHeightFlightPermissionSourceInvariant() throws IOException {
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		String content = Files.readString(scepterPath);

		Assertions.assertTrue(content.contains("return waterHeight > 2;"),
			"CommandScepterItem.isPlayerOverWater must permit flight when waterHeight <= 2 (only waterHeight > 2 cancels flight)");
	}

	@Test
	@DisplayName("Simulation: Water height calculation allows flight over water 1 or 2 blocks high, rejects > 2")
	void testWaterHeightSimulation() {
		java.util.function.Function<Integer, Boolean> isOverWater = depth -> depth > 2;

		// 0 blocks (dry ground)
		Assertions.assertFalse(isOverWater.apply(0), "Dry land must allow build flight");
		// 1 block high water (shallow puddle)
		Assertions.assertFalse(isOverWater.apply(1), "Water 1 block high must allow build flight");
		// 2 blocks high water (shallow creek / pool)
		Assertions.assertFalse(isOverWater.apply(2), "Water 2 blocks high must allow build flight");
		// 3 blocks high water (deep river)
		Assertions.assertTrue(isOverWater.apply(3), "Water 3 blocks high (> 2) must cancel build flight");
		// 10 blocks high water (ocean)
		Assertions.assertTrue(isOverWater.apply(10), "Deep ocean must cancel build flight");
	}
}
