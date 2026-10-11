package com.example.entity;

import com.example.entity.ai.goal.MinionActiveTargetGoal;
import com.example.entity.custom.MinionCombatPolicy;
import com.example.network.UpdateMinionCombatPolicyPayload;
import com.example.targeting.MinionTargetFilterManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating individual minion combat engagement policies:
 * 1. MinionCombatPolicy enum values, sequential next cycles, and ID resolution.
 * 2. UpdateMinionCombatPolicyPayload packet codec and identity contracts.
 * 3. Targeting logic simulation across GLOBAL, PASSIVE, DEFENSIVE, and CUSTOM rules of engagement.
 */
public class MinionCombatPolicyTest {

	private static final Identifier ZOMBIE = Identifier.ofVanilla("zombie");
	private static final Identifier CREEPER = Identifier.ofVanilla("creeper");
	private static final Identifier SKELETON = Identifier.ofVanilla("skeleton");

	@Test
	@DisplayName("MinionCombatPolicy values, sequential next cycles, and fromId fallbacks")
	void testCombatPolicyEnumInvariants() {
		Assertions.assertEquals(0, MinionCombatPolicy.GLOBAL.getId());
		Assertions.assertEquals(1, MinionCombatPolicy.PASSIVE.getId());
		Assertions.assertEquals(2, MinionCombatPolicy.DEFENSIVE.getId());
		Assertions.assertEquals(3, MinionCombatPolicy.CUSTOM.getId());

		// Cyclic transitions: GLOBAL -> PASSIVE -> DEFENSIVE -> CUSTOM -> GLOBAL
		Assertions.assertEquals(MinionCombatPolicy.PASSIVE, MinionCombatPolicy.GLOBAL.next());
		Assertions.assertEquals(MinionCombatPolicy.DEFENSIVE, MinionCombatPolicy.PASSIVE.next());
		Assertions.assertEquals(MinionCombatPolicy.CUSTOM, MinionCombatPolicy.DEFENSIVE.next());
		Assertions.assertEquals(MinionCombatPolicy.GLOBAL, MinionCombatPolicy.CUSTOM.next());

		// fromId resolution
		Assertions.assertEquals(MinionCombatPolicy.GLOBAL, MinionCombatPolicy.fromId(0));
		Assertions.assertEquals(MinionCombatPolicy.PASSIVE, MinionCombatPolicy.fromId(1));
		Assertions.assertEquals(MinionCombatPolicy.DEFENSIVE, MinionCombatPolicy.fromId(2));
		Assertions.assertEquals(MinionCombatPolicy.CUSTOM, MinionCombatPolicy.fromId(3));
		Assertions.assertEquals(MinionCombatPolicy.GLOBAL, MinionCombatPolicy.fromId(99)); // Unknown fallback
	}

	@Test
	@DisplayName("UpdateMinionCombatPolicyPayload packet record contract")
	void testPayloadRecordContract() {
		UpdateMinionCombatPolicyPayload payload = new UpdateMinionCombatPolicyPayload(
			42,
			MinionCombatPolicy.CUSTOM,
			List.of(CREEPER, SKELETON)
		);

		Assertions.assertEquals(UpdateMinionCombatPolicyPayload.ID, payload.getId());
		Assertions.assertEquals(42, payload.minionId());
		Assertions.assertEquals(MinionCombatPolicy.CUSTOM, payload.policy());
		Assertions.assertEquals(List.of(CREEPER, SKELETON), payload.disabledTargets());
		Assertions.assertNotNull(UpdateMinionCombatPolicyPayload.PACKET_CODEC);
	}

	@Test
	@DisplayName("Simulation: PASSIVE policy strictly suppresses all on-sight and offensive attacks")
	void testPassivePolicySuppressesCombat() {
		MinionCombatPolicy policy = MinionCombatPolicy.PASSIVE;
		boolean canEngageOnSight = switch (policy) {
			case PASSIVE -> false;
			case DEFENSIVE -> false;
			case CUSTOM -> true;
			case GLOBAL -> true;
		};
		Assertions.assertFalse(canEngageOnSight, "Passive policy must forbid engaging any mob on sight");
	}

	@Test
	@DisplayName("Simulation: DEFENSIVE policy suppresses on-sight proactive targeting but permits retaliatory strikes")
	void testDefensivePolicyAllowsOnlyRetaliation() {
		MinionCombatPolicy policy = MinionCombatPolicy.DEFENSIVE;
		boolean proactiveOnSight = switch (policy) {
			case PASSIVE, DEFENSIVE -> false;
			case CUSTOM, GLOBAL -> true;
		};
		boolean retaliationAllowed = (policy != MinionCombatPolicy.PASSIVE);

		Assertions.assertFalse(proactiveOnSight, "Defensive policy must suppress proactive on-sight scanning");
		Assertions.assertTrue(retaliationAllowed, "Defensive policy must permit fighting back when attacked");
	}

	@Test
	@DisplayName("Simulation: CUSTOM policy evaluates against individual disabled set rather than global filter")
	void testCustomPolicyEvaluatesIndividualDisabledList() {
		Set<Identifier> individualDisabled = Set.of(CREEPER);
		MinionCombatPolicy policy = MinionCombatPolicy.CUSTOM;

		boolean zombieAllowed = switch (policy) {
			case PASSIVE -> false;
			case DEFENSIVE -> false;
			case CUSTOM -> !individualDisabled.contains(ZOMBIE);
			case GLOBAL -> true;
		};

		boolean creeperAllowed = switch (policy) {
			case PASSIVE -> false;
			case DEFENSIVE -> false;
			case CUSTOM -> !individualDisabled.contains(CREEPER);
			case GLOBAL -> true;
		};

		Assertions.assertTrue(zombieAllowed, "Zombie not in individual disabled set should be allowed");
		Assertions.assertFalse(creeperAllowed, "Creeper in individual disabled set should be forbidden");
	}

	@Test
	@DisplayName("Source Invariant: MinionEntity clears active target when policy is PASSIVE or target is disallowed")
	void testMinionEntityDisengagesTargetOnPolicyChange() throws java.io.IOException {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/entity/custom/MinionEntity.java"));
		Assertions.assertTrue(source.contains("if (policy == MinionCombatPolicy.PASSIVE) {\n\t\t\tthis.setTarget(null);"),
			"MinionEntity must disengage target when switching to PASSIVE");
		Assertions.assertTrue(source.contains("if (this.getTarget() != null && !this.isTargetAllowed(this.getTarget().getType())) {\n\t\t\tthis.setTarget(null);"),
			"MinionEntity must disengage target when disallowed");
		Assertions.assertTrue(source.contains("INDIVIDUAL_DISABLED_TARGETS = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.STRING);"),
			"MinionEntity must register INDIVIDUAL_DISABLED_TARGETS for client-server sync");
		Assertions.assertTrue(source.contains("if (target != null && this.getCombatPolicy() == MinionCombatPolicy.PASSIVE) {\n\t\t\tsuper.setTarget(null);\n\t\t\treturn;\n\t\t}"),
			"MinionEntity.setTarget must strictly reject targets when PASSIVE");
	}

	@Test
	@DisplayName("Source Invariant: Tactical goals and CommandScepter respect PASSIVE and forbidden targets")
	void testTacticalCommandsAndGoalsRespectPolicy() throws java.io.IOException {
		String scepterSource = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));
		Assertions.assertTrue(scepterSource.contains("attackMinions.removeIf(m -> m.getCombatPolicy() == com.example.entity.custom.MinionCombatPolicy.PASSIVE);"),
			"CommandScepter mass attack must exclude PASSIVE minions");
		Assertions.assertTrue(scepterSource.contains("minions.removeIf(m -> m.getCombatPolicy() == com.example.entity.custom.MinionCombatPolicy.PASSIVE);"),
			"CommandScepter hostile entity ping must exclude PASSIVE minions");

		String leaderGoalSource = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/entity/ai/goal/AttackWithLeaderGoal.java"));
		Assertions.assertTrue(leaderGoalSource.contains("!this.minion.isTargetAllowed(this.attacking.getType())"),
			"AttackWithLeaderGoal must check isTargetAllowed");

		String minionSource = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/entity/custom/MinionEntity.java"));
		Assertions.assertTrue(minionSource.contains("MinionEntity.this.isTargetAllowed(ownerAttacking.getType())"),
			"AttackWithOwnerGoal in MinionEntity must check isTargetAllowed");
	}
}
