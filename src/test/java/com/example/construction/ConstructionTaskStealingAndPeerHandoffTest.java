package com.example.construction;

import com.example.blueprint.BlueprintBlock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating multi-minion construction task stealing, absentee handoff,
 * world pre-completion, and lease-safe releases:
 * 1. ConstructionTask lease reassignments and ownership checks.
 * 2. Un-owned lease release protection (releaseIfOwnedBy).
 * 3. Immediate peer handoff detection when a task is completed or taken over.
 * 4. Architectural source invariants in ConstructionSession and MinionBuildGoal.
 */
public class ConstructionTaskStealingAndPeerHandoffTest {

	@Test
	@DisplayName("ConstructionTask reassign transfers lease and resets claim timestamp")
	void testTaskReassignLeaseTransfer() {
		ConstructionTask task = new ConstructionTask(
			1,
			new BlueprintBlock(new BlockPos(10, 64, 10)),
			BlockPos.ORIGIN
		);

		UUID minionA = UUID.randomUUID();
		UUID minionB = UUID.randomUUID();

		Assertions.assertTrue(task.claim(minionA, 100L));
		Assertions.assertTrue(task.isClaimed());
		Assertions.assertTrue(task.isClaimedBy(minionA));
		Assertions.assertFalse(task.isClaimedBy(minionB));
		Assertions.assertEquals(100L, task.getClaimTick());

		// Reassign from minionA to minionB
		Assertions.assertTrue(task.reassign(minionB, 180L));
		Assertions.assertTrue(task.isClaimedBy(minionB));
		Assertions.assertFalse(task.isClaimedBy(minionA));
		Assertions.assertEquals(180L, task.getClaimTick());
	}

	@Test
	@DisplayName("releaseIfOwnedBy protects against former claimants revoking peer leases")
	void testReleaseIfOwnedByLeaseSafety() {
		ConstructionTask task = new ConstructionTask(
			2,
			new BlueprintBlock(new BlockPos(0, 70, 0)),
			BlockPos.ORIGIN
		);

		UUID minionA = UUID.randomUUID();
		UUID minionB = UUID.randomUUID();

		task.claim(minionA, 50L);
		task.reassign(minionB, 150L);

		// Former claimant minionA attempts to release its old task
		boolean aReleased = task.releaseIfOwnedBy(minionA);
		Assertions.assertFalse(aReleased, "Former claimant must NOT be allowed to release minionB's lease");
		Assertions.assertTrue(task.isClaimedBy(minionB), "Task must remain claimed by minionB");

		// Actual owner minionB releases it
		boolean bReleased = task.releaseIfOwnedBy(minionB);
		Assertions.assertTrue(bReleased, "Active lease holder minionB must successfully release task");
		Assertions.assertEquals(ConstructionTask.Status.PENDING, task.getStatus());
	}

	@Test
	@DisplayName("Completed tasks are no longer recognized as claimed by any minion")
	void testCompletedTaskNoLongerClaimed() {
		ConstructionTask task = new ConstructionTask(
			3,
			new BlueprintBlock(new BlockPos(5, 64, 5)),
			BlockPos.ORIGIN
		);

		UUID minion = UUID.randomUUID();
		task.claim(minion, 20L);
		Assertions.assertTrue(task.isClaimedBy(minion));

		task.complete();
		Assertions.assertTrue(task.isCompleted());
		Assertions.assertFalse(task.isClaimedBy(minion), "Completed task must return false for isClaimedBy");
	}

	@Test
	@DisplayName("ConstructionSession defines absentee thresholds and safe release")
	void testConstructionSessionStealingConstantsAndMethods() {
		Assertions.assertEquals(60L, ConstructionSession.STEAL_GRACE_TICKS, "Must have 3-second grace period");
		Assertions.assertEquals(200L, ConstructionSession.STALLED_LEASE_TICKS, "Must have 10-second stalled lease threshold");
		Assertions.assertEquals(24.0D, ConstructionSession.ABSENT_DISTANCE, "Absent distance threshold must be 24 blocks");
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal detects lost lease and forgets task immediately")
	void testMinionBuildGoalForgetsLostTaskOnPeerTakeover() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// Must check isClaimedBy
		Assertions.assertTrue(source.contains("!this.currentTask.isClaimedBy(this.minion.getUuid())"),
			"MinionBuildGoal must check if task is still claimed by this minion");

		// Must call forgetLostTask
		Assertions.assertTrue(source.contains("forgetLostTask(serverWorld);"),
			"MinionBuildGoal must invoke forgetLostTask when lease is lost or finished by peer");

		// Must release with minion UUID guard in stop()
		Assertions.assertTrue(source.contains("this.currentSession.releaseTask(this.currentTask, this.minion.getUuid());"),
			"MinionBuildGoal.stop() must release task safely using releaseTask(task, uuid)");
	}

	@Test
	@DisplayName("Source Invariant: ConstructionSession claimNextTask validates already placed blocks")
	void testConstructionSessionPreCompletionInvariant() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));

		// Must inspect existing block state in world
		Assertions.assertTrue(source.contains("world.getBlockState(task.getWorldPos()).equals(task.getBlueprintBlock().state())"),
			"ConstructionSession.claimNextTask must pre-complete blocks already matching target state");
	}

	@Test
	@DisplayName("Source Invariant: ConstructionSession checks stalled lease before distance comparison to prevent deadlocks")
	void testIsClaimantAbsentStalledLeaseInvariant() throws IOException {
		String sessionSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));

		// Must check age > STALLED_LEASE_TICKS
		int stalledCheck = sessionSource.indexOf("age > STALLED_LEASE_TICKS");
		int distCheck = sessionSource.indexOf("requesterDistSq >= claimantDistSq");

		Assertions.assertTrue(stalledCheck > 0, "ConstructionSession must check age > STALLED_LEASE_TICKS");
		Assertions.assertTrue(distCheck > 0, "ConstructionSession must compare distance to target");
		Assertions.assertTrue(stalledCheck < distCheck,
			"Stalled lease check must precede distance check to allow peers to unblock builds when claimant is stuck near block");

		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));
		// Verify there are no unguarded releaseTask calls left in MinionBuildGoal
		Assertions.assertFalse(goalSource.contains("this.currentSession.releaseTask(this.currentTask);"),
			"MinionBuildGoal must never call releaseTask without passing minion UUID");
	}
}
