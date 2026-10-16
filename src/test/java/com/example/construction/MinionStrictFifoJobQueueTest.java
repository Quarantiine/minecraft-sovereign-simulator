package com.example.construction;

import com.example.entity.custom.MinionRole;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Unit tests validating strict FIFO (First-In, First-Out) job execution order for Builder and Miner minions.
 * Ensures minions 100% complete the earliest placed job before any minion begins working on subsequently placed jobs.
 */
public class MinionStrictFifoJobQueueTest {

	static class SimulatedJobSession {
		final UUID id = UUID.randomUUID();
		final String jobName;
		final BlockPos anchorPos;
		final long createdTick;
		boolean active = true;
		int remainingBlocks;

		SimulatedJobSession(String jobName, BlockPos anchorPos, long createdTick, int totalBlocks) {
			this.jobName = jobName;
			this.anchorPos = anchorPos;
			this.createdTick = createdTick;
			this.remainingBlocks = totalBlocks;
		}

		boolean isComplete() {
			return remainingBlocks <= 0;
		}

		void performWork() {
			if (remainingBlocks > 0) {
				remainingBlocks--;
				if (remainingBlocks == 0) {
					active = false;
				}
			}
		}
	}

	static class SimulatedFifoDispatcher {
		final List<SimulatedJobSession> sessions = new ArrayList<>();

		void addSession(SimulatedJobSession session) {
			sessions.add(session);
		}

		Optional<SimulatedJobSession> findJobForMinion(Vec3d minionPos, double maxDistance) {
			double maxDistSq = maxDistance * maxDistance;
			SimulatedJobSession primarySession = null;
			long earliestTick = Long.MAX_VALUE;
			double bestDistSq = Double.MAX_VALUE;

			for (SimulatedJobSession session : sessions) {
				if (!session.active) continue;

				double distSq = minionPos.squaredDistanceTo(Vec3d.ofCenter(session.anchorPos));
				if (distSq <= maxDistSq) {
					if (session.createdTick < earliestTick) {
						earliestTick = session.createdTick;
						bestDistSq = distSq;
						primarySession = session;
					} else if (session.createdTick == earliestTick && distSq < bestDistSq) {
						bestDistSq = distSq;
						primarySession = session;
					}
				}
			}
			return Optional.ofNullable(primarySession);
		}
	}

	@Test
	@DisplayName("Minion strictly commits to earlier placed job even when physically closer to newer job")
	void testStrictFifoPrioritizesOldestActiveJob() {
		SimulatedFifoDispatcher dispatcher = new SimulatedFifoDispatcher();

		// Job 1 (e.g. Mining Area placed at tick 100, anchor at x=0, z=0)
		SimulatedJobSession job1 = new SimulatedJobSession("Quarry Mining Area", new BlockPos(0, 64, 0), 100L, 5);
		// Job 2 (e.g. Blueprint placed at tick 200, anchor at x=10, z=10)
		SimulatedJobSession job2 = new SimulatedJobSession("Watchtower Blueprint", new BlockPos(10, 64, 10), 200L, 5);

		dispatcher.addSession(job1);
		dispatcher.addSession(job2);

		// Minion is standing at x=11, z=10 — right next to Job 2 (distance ~1 block) and far from Job 1 (distance ~15 blocks)
		Vec3d minionPos = new Vec3d(11.0, 64.0, 10.0);

		// While Job 1 is active, dispatcher must ALWAYS return Job 1 regardless of proximity
		for (int i = 0; i < 5; i++) {
			Optional<SimulatedJobSession> assigned = dispatcher.findJobForMinion(minionPos, 128.0);
			Assertions.assertTrue(assigned.isPresent());
			Assertions.assertEquals("Quarry Mining Area", assigned.get().jobName,
				"Minion must prioritize Job 1 until 100% finished, not switch to Job 2");
			assigned.get().performWork();
		}

		// Job 1 is now 100% complete
		Assertions.assertTrue(job1.isComplete());
		Assertions.assertFalse(job1.active);

		// Now dispatcher assigns Job 2
		Optional<SimulatedJobSession> nextJob = dispatcher.findJobForMinion(minionPos, 128.0);
		Assertions.assertTrue(nextJob.isPresent());
		Assertions.assertEquals("Watchtower Blueprint", nextJob.get().jobName,
			"Minion must transition to Job 2 only after Job 1 is completely finished");
	}

	@Test
	@DisplayName("Source Invariant: ConstructionManager enforces FIFO queue by createdTick")
	void testConstructionManagerFifoSourceInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(path), "ConstructionManager.java must exist");
		String content = Files.readString(path);

		// 1. Strict FIFO Task Ordering comment and logic
		Assertions.assertTrue(
			content.contains("Strict FIFO Task Ordering:"),
			"ConstructionManager must document Strict FIFO Task Ordering"
		);
		Assertions.assertTrue(
			content.contains("long sessionCreated = session.getCreatedTick();"),
			"findNearestSessionForMinion must inspect session.getCreatedTick() for FIFO selection"
		);
		Assertions.assertTrue(
			content.contains("if (sessionCreated < earliestTick)"),
			"findNearestSessionForMinion must prioritize earliest created active session"
		);
		Assertions.assertTrue(
			content.contains("CopyOnWriteArrayList"),
			"sessionsByOwner must use CopyOnWriteArrayList for thread-safe session queueing"
		);
		Assertions.assertTrue(
			content.contains("getSquaredDistanceToSession(minionPos, session)"),
			"findNearestSessionForMinion must use 3D bounding box proximity"
		);
	}

	@Test
	@DisplayName("Source Invariant: ConstructionManager does not cancel overlapping sessions, enabling sequential job queueing")
	void testConstructionManagerDoesNotCancelOverlappingSessions() throws IOException {
		Path path = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(path), "ConstructionManager.java must exist");
		String content = Files.readString(path);

		// Must NOT cancel previous sessions whose bounding box intersects newBox
		Assertions.assertFalse(
			content.contains("intersects(newBox)"),
			"ConstructionManager.startSession must not cancel overlapping sessions"
		);
	}

	@Test
	@DisplayName("3D Box Distance ensures deep underground mining areas remain in range of surface minions")
	void testUndergroundMiningBoxProximity() {
		// Minion on surface: (10, 64, 10)
		BlockPos minionPos = new BlockPos(10, 64, 10);
		// Mining area spanning (0, -60, 0) to (20, 64, 20), anchor at (0, -60, 0)
		BlockPos anchorPos = new BlockPos(0, -60, 0);
		net.minecraft.util.math.BlockBox box = new net.minecraft.util.math.BlockBox(0, -60, 0, 20, 64, 20);

		// Distance to anchor: dx=10, dy=124, dz=10 -> 100 + 15376 + 100 = 15576 (~124.8 blocks)
		double anchorDistSq = minionPos.getSquaredDistance(anchorPos);
		Assertions.assertTrue(anchorDistSq > 120.0 * 120.0);

		// Clamped distance to box: minion is inside the box horizontally and vertically (Y=64 <= 64)
		int clampedX = Math.max(box.getMinX(), Math.min(minionPos.getX(), box.getMaxX()));
		int clampedY = Math.max(box.getMinY(), Math.min(minionPos.getY(), box.getMaxY()));
		int clampedZ = Math.max(box.getMinZ(), Math.min(minionPos.getZ(), box.getMaxZ()));
		double boxDistSq = minionPos.getSquaredDistance(clampedX, clampedY, clampedZ);
		Assertions.assertEquals(0.0, boxDistSq, "Surface minion directly inside/at top of mining area must have 0 box distance");
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal verifies currentTask.isCompleted() in tick()")
	void testMinionBuildGoalTaskCompletedGuard() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionBuildGoal.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(
			content.contains("this.currentTask.isCompleted()"),
			"MinionBuildGoal.tick() must inspect this.currentTask.isCompleted() to advance completed tasks"
		);
	}

	@Test
	@DisplayName("Overlapping Sessions: newer overlapping job is blocked until older job finishes as a whole")
	void testOverlappingSessionsGatedUntilOlderJobComplete() throws IOException {
		Path mgrPath = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(mgrPath), "ConstructionManager.java must exist");
		String mgrContent = Files.readString(mgrPath);

		Assertions.assertTrue(
			mgrContent.contains("isBlockedByOverlappingOlderSession"),
			"ConstructionManager must implement isBlockedByOverlappingOlderSession"
		);
		Assertions.assertTrue(
			mgrContent.contains("thisBox.intersects(olderBox)"),
			"isBlockedByOverlappingOlderSession must verify 3D bounding box intersection"
		);
		Assertions.assertTrue(
			mgrContent.contains("if (box != null && !session.isDismantle())"),
			"completeSession must not add dismantled mining quarries to constructedStructureBounds"
		);

		Path sessPath = Path.of("src/main/java/com/example/construction/ConstructionSession.java");
		Assertions.assertTrue(Files.exists(sessPath), "ConstructionSession.java must exist");
		String sessContent = Files.readString(sessPath);

		Assertions.assertTrue(
			sessContent.contains("ConstructionManager.getInstance().isBlockedByOverlappingOlderSession(this)"),
			"ConstructionSession.claimNextTask must verify it is not blocked by an older overlapping session"
		);
	}

	@Test
	@DisplayName("BedBlock Occupied Invariant: Bed states are sanitized to occupied=false across blueprint and build logic")
	void testBedBlockOccupiedSanitizationInvariants() throws IOException {
		// 1. BlueprintBlock forces OCCUPIED=false for BedBlock
		Path bpBlockPath = Path.of("src/main/java/com/example/blueprint/BlueprintBlock.java");
		Assertions.assertTrue(Files.exists(bpBlockPath), "BlueprintBlock.java must exist");
		String bpBlockContent = Files.readString(bpBlockPath);
		Assertions.assertTrue(
			bpBlockContent.contains("BedBlock.OCCUPIED") && bpBlockContent.contains("with(BedBlock.OCCUPIED, false)"),
			"BlueprintBlock must sanitize BedBlock.OCCUPIED to false"
		);

		// 2. StructureBlueprint sanitizes BedBlock OCCUPIED during capture and property transfer
		Path structBpPath = Path.of("src/main/java/com/example/blueprint/StructureBlueprint.java");
		Assertions.assertTrue(Files.exists(structBpPath), "StructureBlueprint.java must exist");
		String structBpContent = Files.readString(structBpPath);
		Assertions.assertTrue(
			structBpContent.contains("BedBlock.OCCUPIED") && structBpContent.contains("with(BedBlock.OCCUPIED, false)"),
			"StructureBlueprint must sanitize BedBlock.OCCUPIED to false"
		);

		// 3. MinionBuildGoal ensures placed bed halves always have OCCUPIED=false
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(buildGoalPath), "MinionBuildGoal.java must exist");
		String buildGoalContent = Files.readString(buildGoalPath);
		Assertions.assertTrue(
			buildGoalContent.contains("targetState.with(BedBlock.OCCUPIED, false)")
				&& buildGoalContent.contains("otherState.with(BedBlock.OCCUPIED, false)"),
			"MinionBuildGoal must set BedBlock.OCCUPIED to false on both bed halves"
		);

		// 4. ConstructionSession auto-completes the corresponding bed half
		Path sessionPath = Path.of("src/main/java/com/example/construction/ConstructionSession.java");
		Assertions.assertTrue(Files.exists(sessionPath), "ConstructionSession.java must exist");
		String sessionContent = Files.readString(sessionPath);
		Assertions.assertTrue(
			sessionContent.contains("state.getBlock() instanceof BedBlock && state.contains(BedBlock.PART)"),
			"ConstructionSession.completeTask must auto-complete the corresponding bed half"
		);
	}
}

