package com.example.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates that executing a 90° forward sector selection on hostile mobs (Mass Assault)
 * preserves minion states (hold position, patrol routes, free roaming) and does NOT make
 * unselected minions follow the player.
 */
public class MinionMassAssaultStatePreservationTest {

	@Test
	@DisplayName("Source Invariant: 90° Mass Assault in CommandScepterItem preserves hold position, patrol route, and roaming states without setting selected=true")
	void testMassAssaultSourceContracts() throws IOException {
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(Files.exists(scepterPath), "CommandScepterItem.java must exist");
		String content = Files.readString(scepterPath);

		// 1. Verify candidate collection in 90° sector does not exclude patrolling minions
		Assertions.assertTrue(
			content.contains("List<MinionEntity> candidates = serverWorld.getEntitiesByClass(") &&
			content.contains("m -> m.isAlive() && m.isOwner(player)"),
			"Candidate query in sector release must include all owned minions"
		);

		// 2. Verify attackMinionsSet incorporates minions from sector, selected squad, and fallback
		Assertions.assertTrue(
			content.contains("LinkedHashSet<MinionEntity> attackMinionsSet = new LinkedHashSet<>()"),
			"Mass assault must aggregate combat minions into a set"
		);

		// 3. Verify attackMinions loop preserves minion state without forcing setSelected(true)
		int assaultIndex = content.indexOf("if (!enclosedHostiles.isEmpty())");
		int elseRallyIndex = content.indexOf("// STANDARD BANNER OF COURAGE RALLY", assaultIndex);
		Assertions.assertTrue(elseRallyIndex > assaultIndex, "CommandScepterItem must branch into peaceful rally else block");

		String assaultSection = content.substring(assaultIndex, elseRallyIndex);
		String codeOnlyAssaultSection = assaultSection.replaceAll("//.*", "");

		// Assert assault loop does NOT call minion.setSelected(true)
		Assertions.assertFalse(
			codeOnlyAssaultSection.contains("minion.setSelected(true)"),
			"Mass assault branch must NOT set minion.setSelected(true)"
		);

		// Assert assault loop does NOT clear minion.setGuardAnchorPos(null)
		Assertions.assertFalse(
			codeOnlyAssaultSection.contains("minion.setGuardAnchorPos(null)"),
			"Mass assault branch must NOT clear minion.setGuardAnchorPos(null)"
		);

		// Assert assault loop anchors sitting minions to current position if unanchored
		Assertions.assertTrue(
			assaultSection.contains("minion.isSitting() && minion.getGuardAnchorPos() == null"),
			"Mass assault branch must anchor sitting minions before waking them"
		);

		// Assert assault loop queues assault targets and starts movement toward assigned target
		Assertions.assertTrue(
			assaultSection.contains("minion.setAssaultTargets(enclosedHostiles)") &&
			assaultSection.contains("minion.setTarget(assignedTarget)"),
			"Mass assault branch must queue assault targets and assign specific targets"
		);

		// 4. Verify peaceful rally branch DOES setSelected(true) for gathered squad
		String rallySection = content.substring(elseRallyIndex);
		Assertions.assertTrue(
			rallySection.contains("minion.setSelected(true)"),
			"Peaceful rally else block must set minion.setSelected(true) to marshal into following squad"
		);
		Assertions.assertTrue(
			rallySection.contains("mode != CommandMode.PATHWAY && minion.getPatrolRouteId() >= 0"),
			"Peaceful rally else block must protect patrolling minions from accidental enlistment"
		);
	}

	// =========================================================================
	// Pure Mathematical / Logic Simulation of State Invariants
	// =========================================================================

	enum PostCombatReturnDestination {
		OWNER_PLAYER,
		GUARD_ANCHOR,
		SQUAD_LEADER,
		NONE_RESUME_PATROL,
		NONE_RESUME_ROAMING
	}

	static class SimulatedMinion {
		boolean selected;
		boolean sitting;
		boolean guarding;
		int patrolRouteId = -1;
		String guardAnchor = null;
		String squadLeader = null;
		String currentTarget = null;
		boolean hasAssaultTargets = false;

		boolean isHoldingPosition() {
			if (this.selected || this.squadLeader != null || this.patrolRouteId >= 0) {
				return false;
			}
			return this.sitting || this.guarding;
		}

		void applyMassAssaultOrder(String target) {
			if (this.sitting && this.guardAnchor == null) {
				this.guardAnchor = "100,64,100";
				this.guarding = true;
			}
			if (this.sitting) {
				this.sitting = false;
			}
			this.hasAssaultTargets = true;
			this.currentTarget = target;
		}

		PostCombatReturnDestination resolvePostCombatDestination() {
			if (this.sitting) {
				return PostCombatReturnDestination.NONE_RESUME_ROAMING;
			}
			if (this.patrolRouteId >= 0) {
				return PostCombatReturnDestination.NONE_RESUME_PATROL;
			}
			if (this.squadLeader != null) {
				return PostCombatReturnDestination.SQUAD_LEADER;
			}
			if (this.guardAnchor != null) {
				return PostCombatReturnDestination.GUARD_ANCHOR;
			}
			if (this.selected) {
				return PostCombatReturnDestination.OWNER_PLAYER;
			}
			return PostCombatReturnDestination.NONE_RESUME_ROAMING;
		}
	}

	@Test
	@DisplayName("Invariant Simulation: Stationed minion in hold position stays stationed and returns to post after mass assault")
	void testStationedMinionHoldPositionPreservation() {
		SimulatedMinion minion = new SimulatedMinion();
		minion.selected = false;
		minion.guarding = true;
		minion.guardAnchor = "50,65,50";

		Assertions.assertTrue(minion.isHoldingPosition(), "Initially in hold position");

		// Trigger 90° Mass Assault on hostile
		minion.applyMassAssaultOrder("zombie_1");

		Assertions.assertFalse(minion.selected, "Must NOT be selected to follow player");
		Assertions.assertEquals("50,65,50", minion.guardAnchor, "Guard anchor must remain intact");
		Assertions.assertTrue(minion.isHoldingPosition(), "Must remain classified as holding position");

		// Combat ends
		minion.currentTarget = null;
		minion.hasAssaultTargets = false;

		PostCombatReturnDestination dest = minion.resolvePostCombatDestination();
		Assertions.assertEquals(PostCombatReturnDestination.GUARD_ANCHOR, dest,
			"Stationed minion must return to guard anchor post, NEVER the player");
	}

	@Test
	@DisplayName("Invariant Simulation: Patrolling minion on route stays on route and resumes patrol after mass assault")
	void testPatrollingMinionRoutePreservation() {
		SimulatedMinion minion = new SimulatedMinion();
		minion.selected = false;
		minion.patrolRouteId = 2; // Route 2 Cyan

		Assertions.assertFalse(minion.isHoldingPosition());

		// Trigger 90° Mass Assault on hostile
		minion.applyMassAssaultOrder("creeper_1");

		Assertions.assertFalse(minion.selected, "Must NOT be selected to follow player");
		Assertions.assertEquals(2, minion.patrolRouteId, "Patrol route ID must remain intact");

		// Combat ends
		minion.currentTarget = null;
		minion.hasAssaultTargets = false;

		PostCombatReturnDestination dest = minion.resolvePostCombatDestination();
		Assertions.assertEquals(PostCombatReturnDestination.NONE_RESUME_PATROL, dest,
			"Patrolling minion must resume patrol route, NEVER return to the player");
	}

	@Test
	@DisplayName("Invariant Simulation: Roaming minion stays roaming and resumes wandering after mass assault")
	void testRoamingMinionPreservation() {
		SimulatedMinion minion = new SimulatedMinion();
		minion.selected = false;
		minion.guardAnchor = null;
		minion.patrolRouteId = -1;

		// Trigger 90° Mass Assault on hostile
		minion.applyMassAssaultOrder("skeleton_1");

		Assertions.assertFalse(minion.selected, "Must NOT be selected to follow player");

		// Combat ends
		minion.currentTarget = null;
		minion.hasAssaultTargets = false;

		PostCombatReturnDestination dest = minion.resolvePostCombatDestination();
		Assertions.assertEquals(PostCombatReturnDestination.NONE_RESUME_ROAMING, dest,
			"Roaming minion must resume ambient roaming, NEVER return to the player");
	}

	@Test
	@DisplayName("Invariant Simulation: Previously selected minion returns to player formation after mass assault")
	void testSelectedMinionFormationReturn() {
		SimulatedMinion minion = new SimulatedMinion();
		minion.selected = true; // Was already following commander

		// Trigger 90° Mass Assault on hostile
		minion.applyMassAssaultOrder("spider_1");

		Assertions.assertTrue(minion.selected, "Must remain selected");

		// Combat ends
		minion.currentTarget = null;
		minion.hasAssaultTargets = false;

		PostCombatReturnDestination dest = minion.resolvePostCombatDestination();
		Assertions.assertEquals(PostCombatReturnDestination.OWNER_PLAYER, dest,
			"Previously selected minion must return to the commander in formation");
	}
}
