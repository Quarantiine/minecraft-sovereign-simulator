package com.example.entity;

import com.example.entity.ai.goal.MinionActiveTargetGoal;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit and source invariant tests validating Sentinel Wall-Penetrating "Hearing" and Sentry Senses:
 * 1. Target predicate discrimination (ignoreVisibility on Sentinels vs line-of-sight on Warriors).
 * 2. Echolocation alert audio & VFX triggers (sculk click, amethyst resonance, sculk soul particles).
 * 3. Expanded vertical search volume (12 blocks vertical range for multi-floor/bastion sentry detection).
 * 4. Ally outpost mobilization (alerting nearby non-sentinel allies when threats are heard through walls).
 * 5. Persistent target retention without visibility surrender for Sentinels.
 * 6. MinionEntity.hasSentrySense contract.
 */
public class SentinelSentrySenseTest {

	@Test
	@DisplayName("MinionActiveTargetGoal source invariants: Wall-penetrating sentry sense on Sentinels")
	void testMinionActiveTargetGoalSentryInvariants() throws IOException {
		Path targetGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionActiveTargetGoal.java");
		Assertions.assertTrue(Files.exists(targetGoalPath), "MinionActiveTargetGoal.java must exist");
		String content = Files.readString(targetGoalPath);

		// 1. Dual target predicates: standard vision vs wall-penetrating sentry sense
		Assertions.assertTrue(
			content.contains("TargetPredicate warriorPredicate") && content.contains("TargetPredicate sentinelSentryPredicate"),
			"MinionActiveTargetGoal must define dual target predicates for Warriors and Sentinels"
		);
		Assertions.assertTrue(
			content.contains(".ignoreVisibility()"),
			"sentinelSentryPredicate must call ignoreVisibility() to detect hostiles through walls"
		);

		// 2. Dynamic predicate selection in findClosestTarget
		Assertions.assertTrue(
			content.contains("boolean isSentinel = this.minion.matchesRole(MinionRole.SENTINEL);"),
			"findClosestTarget must check if minion is a Sentinel"
		);
		Assertions.assertTrue(
			content.contains("this.targetPredicate = isSentinel ? this.sentinelSentryPredicate : this.warriorPredicate;"),
			"findClosestTarget must assign sentinelSentryPredicate for Sentinels and warriorPredicate for Warriors"
		);

		// 3. Expanded vertical search box for Sentinels (12 blocks up/down)
		Assertions.assertTrue(
			content.contains("double yExpand = this.minion.matchesRole(MinionRole.SENTINEL) ? 12.0D : 4.0D;"),
			"getSearchBox must expand vertical range to 12.0D for Sentinels for multi-floor detection"
		);

		// 4. Alert trigger on detecting hostile behind wall
		Assertions.assertTrue(
			content.contains("boolean behindWall = !this.minion.getVisibilityCache().canSee(this.targetEntity);")
				&& content.contains("triggerSentinelSentrySenseAlert(serverWorld, this.targetEntity);"),
			"start must trigger sentinel sentry sense alert when hostile is behind a wall"
		);

		// 5. Sound and particle effects for sentry hearing
		Assertions.assertTrue(
			content.contains("SoundEvents.BLOCK_SCULK_SENSOR_CLICKING")
				&& content.contains("SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE"),
			"triggerSentinelSentrySenseAlert must play sculk clicking and amethyst resonance sound effects"
		);
		Assertions.assertTrue(
			content.contains("ParticleTypes.SCULK_SOUL"),
			"triggerSentinelSentrySenseAlert must spawn SCULK_SOUL vibration particles at minion's head"
		);

		// 6. Ally outpost notification
		Assertions.assertTrue(
			content.contains("List<MinionEntity> nearbyAllies = serverWorld.getEntitiesByClass(")
				&& content.contains("ally.setTarget(target);"),
			"triggerSentinelSentrySenseAlert must alert nearby allied minions to intercept the threat"
		);

		// 7. Sentry target persistence in shouldContinue (Warriors timeout at 60 ticks without visibility, Sentinels never drop)
		Assertions.assertTrue(
			content.contains("!this.minion.matchesRole(MinionRole.SENTINEL)")
				&& content.contains("this.warriorUnseenTicks > 60"),
			"shouldContinue must enforce 60-tick visibility timeout exclusively for non-Sentinels"
		);
	}

	@Test
	@DisplayName("MinionEntity source invariants: hasSentrySense helper method")
	void testMinionEntitySentrySenseHelperInvariant() throws IOException {
		Path minionPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionPath), "MinionEntity.java must exist");
		String content = Files.readString(minionPath);

		Assertions.assertTrue(
			content.contains("public boolean hasSentrySense() {"),
			"MinionEntity must declare hasSentrySense method"
		);
		Assertions.assertTrue(
			content.contains("return this.matchesRole(MinionRole.SENTINEL);"),
			"hasSentrySense must return true exclusively for Sentinel archetype"
		);
	}
}
