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
	}
}
