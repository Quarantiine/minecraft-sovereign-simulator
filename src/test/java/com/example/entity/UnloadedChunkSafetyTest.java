package com.example.entity;

import com.example.construction.ConstructionSession;
import com.example.entity.ai.goal.MinionFormationFollowGoal;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating entity AI and construction session safeguards
 * when operating in or near unloaded chunks.
 */
public class UnloadedChunkSafetyTest {

	@Test
	@DisplayName("Formation offset calculation evaluates all roles without requiring anonymous synthetic class")
	void testFormationOffsetAllRoles() {
		for (MinionRole role : MinionRole.values()) {
			for (int rank = 0; rank < 12; rank++) {
				Vec3d offset = MinionFormationFollowGoal.calculateFormationOffset(role, rank);
				Assertions.assertNotNull(offset, "Offset for " + role + " rank " + rank + " must not be null");
			}
		}

		// Fallback for null
		Vec3d fallback = MinionFormationFollowGoal.calculateFormationOffset(null, 0);
		Assertions.assertNotNull(fallback);
		Assertions.assertEquals(-2.0D, fallback.x, 1e-5);
	}

	@Test
	@DisplayName("Bytecode verification: MinionFormationFollowGoal$1.class is not generated")
	void testNoAnonymousSyntheticClassGenerated() {
		Path syntheticClass = Path.of("build/classes/java/main/com/example/entity/ai/goal/MinionFormationFollowGoal$1.class");
		Assertions.assertFalse(
			Files.exists(syntheticClass),
			"MinionFormationFollowGoal$1.class must not exist; enum switch must use if-else to prevent classloader linkage crashes"
		);
	}

	@Test
	@DisplayName("Source contract: Formation and follow goals check isChunkLoaded")
	void testFormationAndFollowGoalsChunkChecks() throws IOException {
		Path formationGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionFormationFollowGoal.java");
		String formationContent = Files.readString(formationGoalPath);
		Assertions.assertTrue(
			formationContent.contains("!this.minion.getWorld().isChunkLoaded(this.minion.getBlockX() >> 4, this.minion.getBlockZ() >> 4)"),
			"MinionFormationFollowGoal must check isChunkLoaded for minion position"
		);
		Assertions.assertTrue(
			formationContent.contains("!world.isChunkLoaded(blockX >> 4, blockZ >> 4)"),
			"resolveWalkableY must check isChunkLoaded before reading block states"
		);

		Path followLeaderGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionFollowLeaderGoal.java");
		String followContent = Files.readString(followLeaderGoalPath);
		Assertions.assertTrue(
			followContent.contains("!this.minion.getWorld().isChunkLoaded(this.minion.getBlockX() >> 4, this.minion.getBlockZ() >> 4)"),
			"MinionFollowLeaderGoal must check isChunkLoaded for minion position"
		);
		Assertions.assertTrue(
			followContent.contains("!resolvedLeader.getWorld().isChunkLoaded(resolvedLeader.getBlockX() >> 4, resolvedLeader.getBlockZ() >> 4)"),
			"MinionFollowLeaderGoal must check isChunkLoaded for leader position"
		);
	}

	@Test
	@DisplayName("Source contract: ConstructionSession and MinionEntity check isChunkLoaded")
	void testConstructionAndEntityChunkChecks() throws IOException {
		Path sessionPath = Path.of("src/main/java/com/example/construction/ConstructionSession.java");
		String sessionContent = Files.readString(sessionPath);
		Assertions.assertTrue(
			sessionContent.contains("!world.isChunkLoaded(cx, cz)"),
			"hasRemainingBlocksInWorld must verify all bounding box chunks are loaded"
		);
		Assertions.assertTrue(
			sessionContent.contains("!world.isChunkLoaded(targetPos.getX() >> 4, targetPos.getZ() >> 4)"),
			"claimNextTask must verify target chunk is loaded before checking air"
		);
		Assertions.assertTrue(
			sessionContent.contains("isBuildTaskReady") && sessionContent.contains("!world.isChunkLoaded(targetPos.getX() >> 4, targetPos.getZ() >> 4)"),
			"isBuildTaskReady must verify target chunk is loaded"
		);

		Path entityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		String entityContent = Files.readString(entityPath);
		Assertions.assertTrue(
			entityContent.contains("!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)"),
			"isSafeStandingPosition must verify chunk is loaded before accessing block states"
		);
		Assertions.assertTrue(
			entityContent.contains("!world.isChunkLoaded(center.getX() >> 4, center.getZ() >> 4)"),
			"findNearestSafeStandingPosition must verify chunk is loaded"
		);

		Path managerPath = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		String managerContent = Files.readString(managerPath);
		Assertions.assertTrue(
			managerContent.contains("!world.isChunkLoaded(anchor.getX() >> 4, anchor.getZ() >> 4)"),
			"renderHologramParticles must skip sessions whose anchor is in an unloaded chunk"
		);

		Path targetGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionActiveTargetGoal.java");
		String targetGoalContent = Files.readString(targetGoalPath);
		Assertions.assertTrue(
			targetGoalContent.contains("!target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)"),
			"MinionActiveTargetGoal must drop target if target is in an unloaded chunk"
		);
	}
}
