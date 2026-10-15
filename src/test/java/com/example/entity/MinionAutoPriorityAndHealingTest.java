package com.example.entity;

import com.example.entity.custom.MinionRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit and source invariant tests validating the AUTO mode priority hierarchy and medical triage:
 * 1. Danger Elimination First: If building, mining, or patrolling and a threat/danger appears
 *    (hostiles within 16m, minion/owner attacked, or assault targets assigned), the minion immediately
 *    adapts to WARRIOR, interrupts building/mining, releases tasks, and eliminates danger first.
 * 2. Medic Healing (< 70% Max HP): When no danger/threat is present, if the minion itself,
 *    a player commander/ally, an Iron Golem, or a Villager drops below 70% max health, the minion
 *    adapts to SENTINEL to channel Aegis of Restoration.
 * 3. Resume Building/Mining: Once all nearby allies (including villagers and golems) are healed (>= 70% HP)
 *    and no threat remains, the minion adapts back to BUILDER and resumes active construction/mining.
 * 4. Peacetime Standby: If no construction or mining session is active: adapts to SENTINEL if stationed /
 *    holding position, or WARRIOR for peacetime frontline escort.
 */
public class MinionAutoPriorityAndHealingTest {

	@Test
	@DisplayName("Source Invariants: evaluateAutoRole enforces Danger First -> Healing (Allies & Villagers) -> Building/Mining")
	void testEvaluateAutoRoleSourceInvariants() throws IOException {
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionEntityPath), "MinionEntity.java must exist");
		String minionContent = Files.readString(minionEntityPath);

		// 1. Danger First check: target, attacker, assault targets, owner attacker, or hostiles within 16m
		Assertions.assertTrue(
			minionContent.contains("if (this.getTarget() != null && this.getTarget().isAlive()) {\n\t\t\ttargetAdaptiveRole = MinionRole.WARRIOR;"),
			"evaluateAutoRole must check active target first"
		);
		Assertions.assertTrue(
			minionContent.contains("else if (this.getAttacker() != null && this.getAttacker().isAlive()) {\n\t\t\ttargetAdaptiveRole = MinionRole.WARRIOR;\n\t\t}"),
			"evaluateAutoRole must check active attacker for Warrior"
		);
		Assertions.assertTrue(
			minionContent.contains("else if (this.hasAssaultTargets()) {\n\t\t\ttargetAdaptiveRole = MinionRole.WARRIOR;"),
			"evaluateAutoRole must prioritize scepter assault targets for Warrior"
		);
		Assertions.assertTrue(
			minionContent.contains("if (!nearbyHostiles.isEmpty()) {\n\t\t\t\ttargetAdaptiveRole = MinionRole.WARRIOR;"),
			"evaluateAutoRole must switch to WARRIOR if hostiles are within 16m"
		);

		// 2. Healing check: Evaluates self, owner, fellow minions, Iron Golems, and Villagers (< 70% HP)
		Assertions.assertTrue(
			minionContent.contains("if (this.getHealth() < this.getMaxHealth() * 0.70F) {\n\t\t\t\t\tneedsMedic = true;\n\t\t\t\t}"),
			"evaluateAutoRole must check self health (< 70% HP) for Sentinel"
		);
		Assertions.assertTrue(
			minionContent.contains("VillagerEntity.class"),
			"evaluateAutoRole must query VillagerEntity for medical support"
		);
		Assertions.assertTrue(
			minionContent.contains("IronGolemEntity.class"),
			"evaluateAutoRole must query IronGolemEntity for medical support"
		);
		Assertions.assertTrue(
			minionContent.contains("needsMedic = true;\n\t\t\t\t}\n\t\t\t\tif (!needsMedic && this.getOwner()"),
			"evaluateAutoRole must prioritize healing commander if wounded"
		);

		// 3. Resume Building/Mining: Checks active construction/dismantle sessions when peaceful and healed
		Assertions.assertTrue(
			minionContent.contains("ConstructionManager.getInstance().findNearestSessionForMinion"),
			"evaluateAutoRole must query active sessions to resume building/mining"
		);

		// 4. Periodic auto evaluation runs every 5 ticks for responsive threat reaction
		Assertions.assertTrue(
			minionContent.contains("if (this.getRole() == MinionRole.AUTO && this.age % 5 == 0) {\n\t\t\t\tthis.evaluateAutoRole();\n\t\t\t}"),
			"MinionEntity.tick must evaluate AUTO role every 5 ticks"
		);

		// 5. isAlliedTeammate and hasNearbyAlliesNeedingHealing include VillagerEntity
		Assertions.assertTrue(
			minionContent.contains("if (other instanceof VillagerEntity) {\n\t\t\treturn true;\n\t\t}"),
			"isAlliedTeammate must recognize VillagerEntity as friendly teammate"
		);
		Assertions.assertTrue(
			minionContent.contains("List<VillagerEntity> nearbyVillagers"),
			"hasNearbyAlliesNeedingHealing must query nearby VillagerEntity"
		);
	}

	@Test
	@DisplayName("Source Invariants: SentinelHealAllyGoal and MinionBuildGoal support responsive AUTO adaptation")
	void testSentinelHealAndBuildGoalSourceInvariants() throws IOException {
		// SentinelHealAllyGoal queries and heals Villagers
		Path healGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/SentinelHealAllyGoal.java");
		Assertions.assertTrue(Files.exists(healGoalPath), "SentinelHealAllyGoal.java must exist");
		String healContent = Files.readString(healGoalPath);

		Assertions.assertTrue(
			healContent.contains("List<VillagerEntity> nearbyVillagers"),
			"SentinelHealAllyGoal.findMostWoundedAlly must query VillagerEntity"
		);
		Assertions.assertTrue(
			healContent.contains("float selfThreshold = this.minion.getRole() == MinionRole.AUTO ? ALLY_HEALTH_THRESHOLD : SELF_HEALTH_THRESHOLD;"),
			"SentinelHealAllyGoal must allow AUTO minions to self-heal at 70% threshold"
		);

		// MinionBuildGoal checks evaluateAutoRole and yields immediately on threat/medic transition
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(buildGoalPath), "MinionBuildGoal.java must exist");
		String buildContent = Files.readString(buildGoalPath);

		Assertions.assertTrue(
			buildContent.contains("if (this.minion.getRole() == MinionRole.AUTO && this.minion.age % 5 == 0) {\n\t\t\tthis.minion.evaluateAutoRole();\n\t\t}"),
			"MinionBuildGoal.shouldContinue must evaluate auto role periodically"
		);
		Assertions.assertTrue(
			buildContent.contains("if (!this.minion.matchesRole(MinionRole.BUILDER)) {\n\t\t\t\tthis.stop();\n\t\t\t\treturn;\n\t\t\t}"),
			"MinionBuildGoal.tick must immediately stop if adapted away from BUILDER"
		);
	}

	@Test
	@DisplayName("Behavioral Simulation: Full AUTO life-cycle (Building -> Danger Elimination -> Villager/Ally Healing -> Resuming Building)")
	void testAutoFullLifecycleStateMachine() {
		class FullAutoMinion {
			MinionRole role = MinionRole.AUTO;
			MinionRole adaptiveRole = MinionRole.WARRIOR;
			float health = 40.0F;
			float maxHealth = 40.0F;

			boolean matchesRole(MinionRole expected) {
				if (this.role == expected) return true;
				return this.role == MinionRole.AUTO && this.adaptiveRole == expected;
			}

			MinionRole getEffectiveRole() {
				return this.role == MinionRole.AUTO ? this.adaptiveRole : this.role;
			}

			void evaluateAdaptiveRole(boolean dangerPresent, boolean playerNeedsHeal, boolean golemNeedsHeal,
			                          boolean villagerNeedsHeal, boolean fellowMinionNeedsHeal,
			                          boolean activeConstructionSession, boolean holdingPosition) {
				if (this.role != MinionRole.AUTO) {
					return;
				}

				// Priority 1: Eliminate Danger First
				if (dangerPresent) {
					this.adaptiveRole = MinionRole.WARRIOR;
					return;
				}

				// Priority 2: Medic Healing (< 70% HP) for self, player, golem, villager, minion
				boolean selfNeedsHeal = this.health < this.maxHealth * 0.70F;
				boolean needsMedic = selfNeedsHeal || playerNeedsHeal || golemNeedsHeal || villagerNeedsHeal || fellowMinionNeedsHeal;
				if (needsMedic) {
					this.adaptiveRole = MinionRole.SENTINEL;
					return;
				}

				// Priority 3: Resume Building / Mining
				if (activeConstructionSession) {
					this.adaptiveRole = MinionRole.BUILDER;
					return;
				}

				// Priority 4: Peacetime Standby
				if (holdingPosition) {
					this.adaptiveRole = MinionRole.SENTINEL;
				} else {
					this.adaptiveRole = MinionRole.WARRIOR;
				}
			}
		}

		FullAutoMinion minion = new FullAutoMinion();

		// Step 1: Minion is assigned an active construction/mining blueprint session in peace
		minion.evaluateAdaptiveRole(false, false, false, false, false, true, false);
		Assertions.assertEquals(MinionRole.BUILDER, minion.getEffectiveRole(),
			"Step 1: Minion with active construction session must adapt to BUILDER");
		Assertions.assertTrue(minion.matchesRole(MinionRole.BUILDER));

		// Step 2: Hostile ambush occurs while building! (Villager also got hurt in the attack)
		// DANGER FIRST: Must eliminate danger first, not heal yet
		minion.evaluateAdaptiveRole(true, false, false, true, false, true, false);
		Assertions.assertEquals(MinionRole.WARRIOR, minion.getEffectiveRole(),
			"Step 2: When danger strikes during building, minion must adapt to WARRIOR to eliminate danger first");
		Assertions.assertTrue(minion.matchesRole(MinionRole.WARRIOR));
		Assertions.assertFalse(minion.matchesRole(MinionRole.BUILDER), "Must cease builder role immediately");

		// Step 3: Minion eliminated the danger! Now danger is 0, but Villager is at 50% HP (< 70%)
		// Minion adapts to SENTINEL to heal the villager
		minion.evaluateAdaptiveRole(false, false, false, true, false, true, false);
		Assertions.assertEquals(MinionRole.SENTINEL, minion.getEffectiveRole(),
			"Step 3: Once danger is eliminated, minion must adapt to SENTINEL to heal wounded Villager");
		Assertions.assertTrue(minion.matchesRole(MinionRole.SENTINEL));

		// Step 4: During healing, an Iron Golem also needs healing (40% HP)
		// Minion stays SENTINEL
		minion.evaluateAdaptiveRole(false, false, true, false, false, true, false);
		Assertions.assertEquals(MinionRole.SENTINEL, minion.getEffectiveRole(),
			"Step 4: Minion remains SENTINEL to heal wounded Iron Golem");

		// Step 5: Iron Golem and Villager healed! But the minion itself took damage (20 HP / 50% HP)
		// Minion stays SENTINEL to heal itself
		minion.health = 20.0F;
		minion.evaluateAdaptiveRole(false, false, false, false, false, true, false);
		Assertions.assertEquals(MinionRole.SENTINEL, minion.getEffectiveRole(),
			"Step 5: Minion remains SENTINEL to self-heal");

		// Step 6: All healed (self >= 70%, villager >= 70%, golem >= 70%, player >= 70%) and NO threat!
		// Minion returns to BUILDER to resume building/mining
		minion.health = 40.0F; // Full HP
		minion.evaluateAdaptiveRole(false, false, false, false, false, true, false);
		Assertions.assertEquals(MinionRole.BUILDER, minion.getEffectiveRole(),
			"Step 6: Once all healed and no threat, minion must return to BUILDER and resume building/mining");
		Assertions.assertTrue(minion.matchesRole(MinionRole.BUILDER));

		// Step 7: Construction session finished 100%! No more building tasks
		// Minion transitions to peacetime vanguard escort (WARRIOR)
		minion.evaluateAdaptiveRole(false, false, false, false, false, false, false);
		Assertions.assertEquals(MinionRole.WARRIOR, minion.getEffectiveRole(),
			"Step 7: When building finishes and peaceful, minion defaults to WARRIOR frontline vanguard");

		// Step 8: Commander stations minion at a post (hold position)
		minion.evaluateAdaptiveRole(false, false, false, false, false, false, true);
		Assertions.assertEquals(MinionRole.SENTINEL, minion.getEffectiveRole(),
			"Step 8: Holding position stationed minion adapts to SENTINEL guard");
	}
}
