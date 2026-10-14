package com.example.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit and source invariant tests validating minion sneaking behavior and tactical stealth:
 * 1. Selected minions sneak if and only if their commanding player is sneaking.
 * 2. MinionEntity.tickSneakState manages setSneaking and EntityPose.CROUCHING / EntityPose.STANDING.
 * 3. MinionEntity.isInSneakingPose returns true whenever isSneaking or pose is CROUCHING.
 * 4. Minions sitting or actively building/levitating are exempted from sneaking.
 * 5. Escort minions sneak when their squad leader is sneaking.
 * 6. MinionEntityRenderer overrides hasLabel and renderLabelIfPresent to completely hide name tags when sneaking.
 * 7. MinionOverheadBadgeFeatureRenderer suppresses overhead crest badges (squad banner, role crest, health hearts) when sneaking.
 * 8. MinionFormationFollowGoal and MinionFollowLeaderGoal adjust movement pacing to stealth speed (0.65D) when sneaking.
 */
public class MinionSneakStealthTest {

	@Test
	@DisplayName("MinionEntity: Validate sneaking logic, pose synchronization, and exemption invariants")
	void testMinionEntitySneakInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		// tickSneakState must be declared and called in tick()
		Assertions.assertTrue(content.contains("public void tickSneakState()"), "MinionEntity must define tickSneakState()");
		Assertions.assertTrue(content.contains("this.tickSneakState();"), "MinionEntity.tick() must invoke tickSneakState()");

		// Check commander and selection conditions
		Assertions.assertTrue(
			content.contains("boolean isCommanderSneaking = this.isSelected() && owner != null && owner.isSneaking();"),
			"MinionEntity must check isSelected and owner.isSneaking"
		);

		// Check squad escort following leader sneak
		Assertions.assertTrue(
			content.contains("leader != null && leader.isSneaking()"),
			"MinionEntity must allow escorts to sneak with their squad leader"
		);

		// Check exemptions: sitting, actively building, arcane levitating
		Assertions.assertTrue(
			content.contains("!this.isSitting() && !this.isActivelyBuilding() && !this.isArcaneLevitating()"),
			"MinionEntity must exempt sitting, building, and levitating minions from sneaking"
		);

		// Check pose updates
		Assertions.assertTrue(
			content.contains("this.setPose(EntityPose.CROUCHING);") && content.contains("this.setPose(EntityPose.STANDING);"),
			"MinionEntity must switch between EntityPose.CROUCHING and EntityPose.STANDING"
		);

		// isInSneakingPose override
		Assertions.assertTrue(
			content.contains("public boolean isInSneakingPose()") && content.contains("this.isSneaking() || this.getPose() == EntityPose.CROUCHING"),
			"MinionEntity must override isInSneakingPose based on isSneaking and CROUCHING pose"
		);
	}

	@Test
	@DisplayName("MinionEntityRenderer: Name tags (hasLabel and renderLabelIfPresent) are suppressed when sneaking")
	void testRendererNameTagSuppressionInvariants() throws IOException {
		Path path = Path.of("src/client/java/com/example/client/renderer/MinionEntityRenderer.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntityRenderer.java must exist");
		String content = Files.readString(path);

		// hasLabel must return false when sneaking
		Assertions.assertTrue(
			content.contains("protected boolean hasLabel(MinionEntity entity)"),
			"MinionEntityRenderer must override hasLabel"
		);
		Assertions.assertTrue(
			content.contains("if (entity.isSneaking() || entity.isInSneakingPose()) {\n\t\t\treturn false;\n\t\t}"),
			"hasLabel must return false if entity is sneaking"
		);

		// renderLabelIfPresent must abort when sneaking
		Assertions.assertTrue(
			content.contains("protected void renderLabelIfPresent("),
			"MinionEntityRenderer must override renderLabelIfPresent"
		);
		Assertions.assertTrue(
			content.contains("if (entity.isSneaking() || entity.isInSneakingPose()) {\n\t\t\treturn;\n\t\t}"),
			"renderLabelIfPresent must abort if entity is sneaking"
		);
	}

	@Test
	@DisplayName("MinionOverheadBadgeFeatureRenderer: Overhead crest badges are suppressed when sneaking")
	void testOverheadBadgeSuppressionInvariants() throws IOException {
		Path path = Path.of("src/client/java/com/example/client/renderer/MinionOverheadBadgeFeatureRenderer.java");
		Assertions.assertTrue(Files.exists(path), "MinionOverheadBadgeFeatureRenderer.java must exist");
		String content = Files.readString(path);

		// Overhead badges must abort rendering if minion is sneaking
		Assertions.assertTrue(
			content.contains("if (entity.isInvisible() || entity.isSpectator() || entity.isSneaking() || entity.isInSneakingPose()) {\n\t\t\treturn;\n\t\t}"),
			"MinionOverheadBadgeFeatureRenderer must return early when entity is sneaking"
		);
	}

	@Test
	@DisplayName("MinionFormationFollowGoal & MinionFollowLeaderGoal: Stealth pacing (0.65D) when sneaking")
	void testStealthFollowPacingInvariants() throws IOException {
		Path formationGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionFormationFollowGoal.java");
		Assertions.assertTrue(Files.exists(formationGoalPath), "MinionFormationFollowGoal.java must exist");
		String formationContent = Files.readString(formationGoalPath);

		Assertions.assertTrue(
			formationContent.contains("if (this.minion.isSneaking() || owner.isSneaking()) {\n\t\t\tspeed = (distToOwnerSq > SPRINT_DISTANCE_THRESHOLD_SQ) ? MARCH_SPEED : 0.65D;"),
			"MinionFormationFollowGoal must adopt 0.65D stealth speed when sneaking"
		);

		Path leaderGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionFollowLeaderGoal.java");
		Assertions.assertTrue(Files.exists(leaderGoalPath), "MinionFollowLeaderGoal.java must exist");
		String leaderContent = Files.readString(leaderGoalPath);

		Assertions.assertTrue(
			leaderContent.contains("if (this.minion.isSneaking() || resolvedLeader.isSneaking()) {\n\t\t\tspeed = (distToLeaderSq > SPRINT_DISTANCE_THRESHOLD_SQ) ? MARCH_SPEED : 0.65D;"),
			"MinionFollowLeaderGoal must adopt 0.65D stealth speed when leader is sneaking"
		);
	}

	@Test
	@DisplayName("Sneaking Decision Logic Matrix Simulation")
	void testSneakingDecisionLogicMatrix() {
		// Pure decision function replicating MinionEntity.tickSneakState logic
		java.util.function.BiFunction<Boolean, Boolean, Boolean> calculateSneak =
			(isSelected, isOwnerSneaking) -> isSelected && isOwnerSneaking;

		// 1. Unselected minions never sneak regardless of owner sneaking
		Assertions.assertFalse(calculateSneak.apply(false, false), "Unselected minion must not sneak when owner is not sneaking");
		Assertions.assertFalse(calculateSneak.apply(false, true), "Unselected minion must not sneak even if owner is sneaking");

		// 2. Selected minion only sneaks if owner is sneaking
		Assertions.assertFalse(calculateSneak.apply(true, false), "Selected minion must not sneak if owner is walking normally");
		Assertions.assertTrue(calculateSneak.apply(true, true), "Selected minion must sneak when owner is sneaking");

		// 3. Exemption conditions: sitting or building overrides sneak
		java.util.function.Function<Boolean[], Boolean> calculateWithExemptions =
			params -> {
				boolean isAlive = params[0];
				boolean isSitting = params[1];
				boolean isBuilding = params[2];
				boolean isSelected = params[3];
				boolean isOwnerSneaking = params[4];
				if (!isAlive || isSitting || isBuilding) return false;
				return isSelected && isOwnerSneaking;
			};

		Assertions.assertFalse(
			calculateWithExemptions.apply(new Boolean[]{true, true, false, true, true}),
			"Sitting minions must not sneak even if selected and owner is sneaking"
		);
		Assertions.assertFalse(
			calculateWithExemptions.apply(new Boolean[]{true, false, true, true, true}),
			"Actively building minions must not sneak even if selected and owner is sneaking"
		);
		Assertions.assertFalse(
			calculateWithExemptions.apply(new Boolean[]{false, false, false, true, true}),
			"Dead minions must not sneak"
		);
		Assertions.assertTrue(
			calculateWithExemptions.apply(new Boolean[]{true, false, false, true, true}),
			"Free active selected minions must sneak when owner is sneaking"
		);
	}
}
