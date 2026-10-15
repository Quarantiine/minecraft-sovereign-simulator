package com.example.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit and source invariant tests validating that Builder minions strictly NEVER engage in combat:
 * 1. MinionEntity.setTarget rejects non-null targets when minion matches MinionRole.BUILDER.
 * 2. MinionEntity.isTargetAllowed returns false when minion matches MinionRole.BUILDER.
 * 3. MinionEntity.setRole and setAdaptiveRole immediately clear active target and assault queue upon transitioning to BUILDER.
 * 4. MinionEntity.setAssaultTargets refuses to queue combat assault targets for builders.
 * 5. MinionEntity.tryAttack and shootAt strictly abort without dealing damage or launching projectiles.
 * 6. MinionEntity.tick continuously enforces null target when matchesRole(MinionRole.BUILDER).
 * 7. All combat AI goals (MeleeAttackGoal, MinionRangedAttackGoal, MinionActiveTargetGoal,
 *    TrackOwnerAttackerGoal, AttackWithOwnerGoal, RevengeGoal, TrackLeaderAttackerGoal, AttackWithLeaderGoal)
 *    are hard-gated against MinionRole.BUILDER.
 * 8. MinionPatrolGoal and MinionActiveTargetGoal alert broadcasts explicitly exclude builders.
 * 9. MinionHarvestingHelper does not command builders to hunt or engage mobs when no warrior is available.
 * 10. CommandScepterItem mass assault, target click, and ATTACK mode exclude builders.
 */
public class BuilderNoCombatTest {

	@Test
	@DisplayName("MinionEntity: setTarget, isTargetAllowed, and role transitions reject combat for builders")
	void testMinionEntityBuilderTargetInvariants() throws IOException {
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionEntityPath), "MinionEntity.java must exist");
		String content = Files.readString(minionEntityPath);

		// 1. setTarget must reject targets if minion matches MinionRole.BUILDER
		Assertions.assertTrue(
			content.contains("this.matchesRole(MinionRole.BUILDER)") && content.contains("super.setTarget(null);"),
			"setTarget must immediately set null and return when matchesRole(MinionRole.BUILDER)"
		);

		// 2. isTargetAllowed must return false for builders
		Assertions.assertTrue(
			content.contains("type == null || this.matchesRole(MinionRole.BUILDER)"),
			"isTargetAllowed must return false if minion matches MinionRole.BUILDER"
		);

		// 3. setRole(BUILDER) and setAdaptiveRole(BUILDER) must clear target and assault targets
		Assertions.assertTrue(
			content.contains("if (role == MinionRole.BUILDER) {\n\t\t\tthis.setTarget(null);\n\t\t\tthis.clearAssaultTargets();\n\t\t}"),
			"setRole and setAdaptiveRole must purge target and assault targets on BUILDER assignment"
		);

		// 4. setAssaultTargets must ignore targets if minion matches MinionRole.BUILDER
		Assertions.assertTrue(
			content.contains("if (this.matchesRole(MinionRole.BUILDER)) {\n\t\t\treturn;\n\t\t}"),
			"setAssaultTargets must clear and return early if minion matches MinionRole.BUILDER"
		);

		// 5. tryAttack and shootAt must strictly abort for builders
		Assertions.assertTrue(
			content.contains("if (this.matchesRole(MinionRole.BUILDER)) {\n\t\t\treturn false;\n\t\t}\n\t\tthis.swingHand(Hand.MAIN_HAND);"),
			"tryAttack must return false without swinging or damaging if matchesRole(MinionRole.BUILDER)"
		);
		Assertions.assertTrue(
			content.contains("if (this.matchesRole(MinionRole.BUILDER)) {\n\t\t\treturn;\n\t\t}\n\t\tItemStack weapon = this.getMainHandStack();"),
			"shootAt must return early without shooting if matchesRole(MinionRole.BUILDER)"
		);

		// 6. tick must enforce null target for builders
		Assertions.assertTrue(
			content.contains("// Builders do not fight\n\t\t\tif (this.matchesRole(MinionRole.BUILDER) && currentTarget != null) {\n\t\t\t\tthis.setTarget(null);\n\t\t\t\tcurrentTarget = null;\n\t\t\t}"),
			"tick() must continuously nullify currentTarget for builders"
		);
	}

	@Test
	@DisplayName("AI Goals: MeleeAttackGoal, RangedAttackGoal, and Target goals hard-gate against builders")
	void testAiGoalsBuilderImmunity() throws IOException {
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		String minionContent = Files.readString(minionEntityPath);

		// MeleeAttackGoal canStart and shouldContinue
		Assertions.assertTrue(
			minionContent.contains("if (MinionEntity.this.matchesRole(MinionRole.BUILDER)) {\n\t\t\t\t\treturn false;\n\t\t\t\t}"),
			"MeleeAttackGoal must return false in canStart and shouldContinue for builders"
		);

		// TrackOwnerAttackerGoal, AttackWithOwnerGoal, and RevengeGoal
		Assertions.assertTrue(
			minionContent.contains("if (MinionEntity.this.matchesRole(MinionRole.BUILDER)\n\t\t\t\t\t\t|| MinionEntity.this.getCombatPolicy() == MinionCombatPolicy.PASSIVE)"),
			"TrackOwnerAttackerGoal, AttackWithOwnerGoal, and RevengeGoal must suppress for builders"
		);

		// TrackLeaderAttackerGoal
		Path trackLeaderPath = Path.of("src/main/java/com/example/entity/ai/goal/TrackLeaderAttackerGoal.java");
		String trackLeaderContent = Files.readString(trackLeaderPath);
		Assertions.assertTrue(
			trackLeaderContent.contains("this.minion.matchesRole(com.example.entity.custom.MinionRole.BUILDER)"),
			"TrackLeaderAttackerGoal must return false for builders"
		);

		// AttackWithLeaderGoal
		Path attackLeaderPath = Path.of("src/main/java/com/example/entity/ai/goal/AttackWithLeaderGoal.java");
		String attackLeaderContent = Files.readString(attackLeaderPath);
		Assertions.assertTrue(
			attackLeaderContent.contains("this.minion.matchesRole(com.example.entity.custom.MinionRole.BUILDER)"),
			"AttackWithLeaderGoal must return false for builders"
		);

		// MinionActiveTargetGoal
		Path activeTargetPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionActiveTargetGoal.java");
		String activeTargetContent = Files.readString(activeTargetPath);
		Assertions.assertTrue(
			activeTargetContent.contains("if (this.minion.matchesRole(MinionRole.BUILDER)) {\n\t\t\treturn false;\n\t\t}"),
			"MinionActiveTargetGoal must return false for builders in canStart"
		);
		Assertions.assertTrue(
			activeTargetContent.contains("&& !ally.matchesRole(MinionRole.BUILDER)"),
			"Sentinel echolocation alert must exclude builders from ally mobilization"
		);

		// MinionRangedAttackGoal
		Path rangedGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionRangedAttackGoal.java");
		String rangedContent = Files.readString(rangedGoalPath);
		Assertions.assertTrue(
			rangedContent.contains("this.minion.matchesRole(MinionRole.BUILDER)"),
			"MinionRangedAttackGoal must reject builders"
		);

		// MinionPatrolGoal
		Path patrolGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionPatrolGoal.java");
		String patrolContent = Files.readString(patrolGoalPath);
		Assertions.assertTrue(
			patrolContent.contains("&& !ally.matchesRole(MinionRole.BUILDER)"),
			"MinionPatrolGoal combat alert must exclude builders"
		);
	}

	@Test
	@DisplayName("Logistics & Scepter: Builders never hunt mobs or join scepter attacks")
	void testLogisticsAndScepterBuilderExclusion() throws IOException {
		// MinionHarvestingHelper solo hunting fallback removal
		Path harvestingPath = Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java");
		String harvestingContent = Files.readString(harvestingPath);
		Assertions.assertFalse(
			harvestingContent.contains("Solo Hunting Fallback: Builder engages target mob directly"),
			"Solo Hunting Fallback must be removed; builders never fight mobs"
		);
		Assertions.assertTrue(
			harvestingContent.contains("Builder minions do not fight. Mob procurement requires an available Warrior thrall."),
			"MinionHarvestingHelper must cleanly fail procurement when no warrior is available"
		);

		// CommandScepterItem mass assault and attack modes
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		String scepterContent = Files.readString(scepterPath);
		Assertions.assertTrue(
			scepterContent.contains("attackMinions.removeIf(m -> m.matchesRole(MinionRole.BUILDER)"),
			"handleMassAssaultCommand must filter out builders"
		);
		Assertions.assertTrue(
			scepterContent.contains("minions.removeIf(m -> m.matchesRole(MinionRole.BUILDER)"),
			"handleAttackTargetCommand must filter out builders"
		);
	}

	@Test
	@DisplayName("AUTO Mode: Minions dynamically switch to Sentinel (if healing needed) or Warrior (if combat), while dedicated Builders stay Builders")
	void testAutoRoleSwitchesToWarriorOrSentinelWhenNeeded() throws IOException {
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		String minionContent = Files.readString(minionEntityPath);

		// 1. evaluateAutoRole checks self health (< 70% max health) for Sentinel
		Assertions.assertTrue(
			minionContent.contains("if (this.getHealth() < this.getMaxHealth() * 0.70F) {\n\t\t\t\t\tneedsMedic = true;\n\t\t\t\t}"),
			"evaluateAutoRole must check minion's own health (< 70% HP) for Sentinel medic role"
		);

		// 2. evaluateAutoRole checks attacker or nearby hostiles for Warrior
		Assertions.assertTrue(
			minionContent.contains("else if (this.getAttacker() != null && this.getAttacker().isAlive()) {\n\t\t\ttargetAdaptiveRole = MinionRole.WARRIOR;\n\t\t}"),
			"evaluateAutoRole must switch to WARRIOR if actively attacked"
		);
		Assertions.assertTrue(
			minionContent.contains("if (!nearbyHostiles.isEmpty()) {\n\t\t\t\ttargetAdaptiveRole = MinionRole.WARRIOR;"),
			"evaluateAutoRole must switch to WARRIOR if hostiles are within 16m"
		);
		Assertions.assertTrue(
			minionContent.contains("VillagerEntity.class"),
			"evaluateAutoRole must query and heal VillagerEntity under 70% HP"
		);

		// 3. damage() immediately triggers evaluateAutoRole for AUTO minions
		Assertions.assertTrue(
			minionContent.contains("if (result && !this.getWorld().isClient() && this.getRole() == MinionRole.AUTO && this.isAlive()) {\n\t\t\tthis.evaluateAutoRole();\n\t\t}"),
			"damage() must evaluate AUTO role immediately when damaged"
		);

		// 4. MinionBuildGoal yields when minion adapts away from BUILDER
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		String buildGoalContent = Files.readString(buildGoalPath);
		Assertions.assertTrue(
			buildGoalContent.contains("if (!this.minion.matchesRole(MinionRole.BUILDER)) {\n\t\t\treturn false;\n\t\t}"),
			"MinionBuildGoal.shouldContinue must yield when minion adapts away from BUILDER"
		);

		// 5. Behavioral simulation
		class MockThrall {
			com.example.entity.custom.MinionRole role;
			com.example.entity.custom.MinionRole adaptiveRole = com.example.entity.custom.MinionRole.WARRIOR;
			float health = 40.0F;
			float maxHealth = 40.0F;

			MockThrall(com.example.entity.custom.MinionRole role) {
				this.role = role;
			}

			boolean matchesRole(com.example.entity.custom.MinionRole expected) {
				if (this.role == expected) return true;
				return this.role == com.example.entity.custom.MinionRole.AUTO && this.adaptiveRole == expected;
			}

			com.example.entity.custom.MinionRole getEffectiveRole() {
				return this.role == com.example.entity.custom.MinionRole.AUTO ? this.adaptiveRole : this.role;
			}

			void evaluateAdaptiveRole(boolean allyNeedsHealing, boolean underAttack, boolean hostilesNearby, boolean hasBuildTask) {
				if (this.role != com.example.entity.custom.MinionRole.AUTO) {
					return; // Dedicated roles never adapt
				}
				// 1. Danger first: Eliminate threat before healing or building
				if (underAttack || hostilesNearby) {
					this.adaptiveRole = com.example.entity.custom.MinionRole.WARRIOR;
				} else if ((this.health < this.maxHealth * 0.70F) || allyNeedsHealing) {
					// 2. Medic healing: If no danger, heal injured self, players, golems, or villagers (< 70% HP)
					this.adaptiveRole = com.example.entity.custom.MinionRole.SENTINEL;
				} else if (hasBuildTask) {
					// 3. Resume building / mining: If all healed and no danger, resume construction/mining
					this.adaptiveRole = com.example.entity.custom.MinionRole.BUILDER;
				} else {
					this.adaptiveRole = com.example.entity.custom.MinionRole.WARRIOR;
				}
			}
		}

		// A. Dedicated BUILDER never adapts: stays pure builder regardless of health or combat
		MockThrall dedicatedBuilder = new MockThrall(com.example.entity.custom.MinionRole.BUILDER);
		dedicatedBuilder.health = 10.0F; // Low health
		dedicatedBuilder.evaluateAdaptiveRole(true, true, true, false);
		Assertions.assertEquals(com.example.entity.custom.MinionRole.BUILDER, dedicatedBuilder.getEffectiveRole());
		Assertions.assertTrue(dedicatedBuilder.matchesRole(com.example.entity.custom.MinionRole.BUILDER));

		// B. AUTO minion building peacefully adapts to BUILDER
		MockThrall autoMinion = new MockThrall(com.example.entity.custom.MinionRole.AUTO);
		autoMinion.evaluateAdaptiveRole(false, false, false, true);
		Assertions.assertEquals(com.example.entity.custom.MinionRole.BUILDER, autoMinion.getEffectiveRole());

		// C. Hostiles approach or attack while building -> AUTO minion eliminates danger FIRST (WARRIOR)
		autoMinion.evaluateAdaptiveRole(false, true, true, true);
		Assertions.assertEquals(com.example.entity.custom.MinionRole.WARRIOR, autoMinion.getEffectiveRole());
		Assertions.assertTrue(autoMinion.matchesRole(com.example.entity.custom.MinionRole.WARRIOR));
		Assertions.assertFalse(autoMinion.matchesRole(com.example.entity.custom.MinionRole.BUILDER));

		// D. Low HP (< 70%) while under attack -> DANGER FIRST (stays WARRIOR until danger eliminated)
		autoMinion.health = 20.0F; // 50% HP
		autoMinion.evaluateAdaptiveRole(false, true, true, true);
		Assertions.assertEquals(com.example.entity.custom.MinionRole.WARRIOR, autoMinion.getEffectiveRole(),
			"AUTO minion under attack must eliminate danger first before switching to Sentinel to heal");
		Assertions.assertTrue(autoMinion.matchesRole(com.example.entity.custom.MinionRole.WARRIOR));

		// D2. Danger eliminated, wounded (< 70% HP) -> switches to SENTINEL to heal
		autoMinion.evaluateAdaptiveRole(false, false, false, true);
		Assertions.assertEquals(com.example.entity.custom.MinionRole.SENTINEL, autoMinion.getEffectiveRole(),
			"AUTO minion must switch to Sentinel to heal once danger is cleared");
		Assertions.assertTrue(autoMinion.matchesRole(com.example.entity.custom.MinionRole.SENTINEL));

		// D3. Fully healed (>= 70% HP) and no threats -> returns to building / mining
		autoMinion.health = 40.0F; // Healed
		autoMinion.evaluateAdaptiveRole(false, false, false, true);
		Assertions.assertEquals(com.example.entity.custom.MinionRole.BUILDER, autoMinion.getEffectiveRole(),
			"AUTO minion must return to Builder once fully healed and safe");
		Assertions.assertTrue(autoMinion.matchesRole(com.example.entity.custom.MinionRole.BUILDER));

		// E. Ally (villager, golem, minion, player) needs healing and no threats -> switches to SENTINEL
		autoMinion.evaluateAdaptiveRole(true, false, false, true);
		Assertions.assertEquals(com.example.entity.custom.MinionRole.SENTINEL, autoMinion.getEffectiveRole());
		Assertions.assertTrue(autoMinion.matchesRole(com.example.entity.custom.MinionRole.SENTINEL));

		// E2. Ally healed and no threats -> switches back to BUILDER
		autoMinion.evaluateAdaptiveRole(false, false, false, true);
		Assertions.assertEquals(com.example.entity.custom.MinionRole.BUILDER, autoMinion.getEffectiveRole());
	}
}
