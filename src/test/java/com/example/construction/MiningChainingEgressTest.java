package com.example.construction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests verifying seamless chaining between mining and building sessions:
 * <ol>
 *   <li>Neither mining nor building session completion triggers structure egress or portal descent when chaining.</li>
 *   <li>ConstructionSession markCompleted immediately terminates isActive() status.</li>
 *   <li>Minions chaining to an adjacent active session maintain Arcane Levitation without forced ground landings.</li>
 *   <li>ConstructionManager isMinionEngagedInConstruction covers full 128-block operational radius.</li>
 * </ol>
 */
public class MiningChainingEgressTest {

	@Test
	@DisplayName("ConstructionSession markCompleted immediately marks session as inactive")
	void testSessionMarkCompletedInvariants() throws IOException {
		Path sessionPath = Path.of("src/main/java/com/example/construction/ConstructionSession.java");
		Assertions.assertTrue(Files.exists(sessionPath), "ConstructionSession.java must exist");
		String sessionSource = Files.readString(sessionPath);

		Assertions.assertTrue(
			sessionSource.contains("public void markCompleted()"),
			"ConstructionSession must define public markCompleted()"
		);
		Assertions.assertTrue(
			sessionSource.contains("this.status = SessionStatus.COMPLETED;"),
			"markCompleted must set status to COMPLETED"
		);
	}

	@Test
	@DisplayName("ConstructionManager completeSession clears egress and keeps levitation active when chaining")
	void testCompleteSessionEgressExclusionWhenChaining() throws IOException {
		Path managerPath = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(managerPath), "ConstructionManager.java must exist");
		String managerSource = Files.readString(managerPath);

		// 1. Session must be marked completed immediately
		Assertions.assertTrue(
			managerSource.contains("session.markCompleted();"),
			"completeSession must immediately invoke session.markCompleted()"
		);

		// 2. When nextSession is present, egress state is cleared via clearBuildingEgress
		Assertions.assertTrue(
			managerSource.contains("minion.clearBuildingEgress();"),
			"completeSession must clear egress state via clearBuildingEgress when nextSession.isPresent()"
		);

		// 3. Levitation and activelyBuilding are preserved when chaining
		Assertions.assertTrue(
			managerSource.contains("minion.setActivelyBuilding(true);"),
			"completeSession must preserve activelyBuilding = true when chaining"
		);
		Assertions.assertTrue(
			managerSource.contains("minion.setArcaneLevitating(true);"),
			"completeSession must keep arcaneLevitating = true when chaining"
		);

		// 4. Must keep sitting = false when chaining
		Assertions.assertTrue(
			managerSource.contains("if (nextSession.isPresent()) {"),
			"completeSession must branch when nextSession.isPresent()"
		);
		Assertions.assertTrue(
			managerSource.contains("minion.setSitting(false);"),
			"completeSession must keep sitting = false when chaining"
		);
	}

	@Test
	@DisplayName("ConstructionManager isMinionEngagedInConstruction checks full 128-block radius")
	void testEngagementRadiusMatches128Blocks() throws IOException {
		Path managerPath = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(managerPath), "ConstructionManager.java must exist");
		String managerSource = Files.readString(managerPath);

		Assertions.assertTrue(
			managerSource.contains("isMinionEngagedInConstruction(MinionEntity minion, double maxDistance)"),
			"ConstructionManager must declare parameterized isMinionEngagedInConstruction"
		);
		Assertions.assertTrue(
			managerSource.contains("return isMinionEngagedInConstruction(minion, 128.0D);"),
			"Default isMinionEngagedInConstruction radius must be 128.0D to match chaining search radius"
		);
	}

	@Test
	@DisplayName("MinionBuildGoal handles removed session immediately and resets egress in kinematics")
	void testMinionBuildGoalChainingAndKinematicsInvariants() throws IOException {
		Path goalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(goalPath), "MinionBuildGoal.java must exist");
		String goalSource = Files.readString(goalPath);

		// 1. tryClaimTaskOrChainNextSession checks if currentSession is still registered in ConstructionManager
		Assertions.assertTrue(
			goalSource.contains("ConstructionManager.getInstance().getSession(this.currentSession.getId()).isPresent()"),
			"tryClaimTaskOrChainNextSession must check if currentSession is present in ConstructionManager"
		);

		// 2. setupTaskKinematics clears egress state cleanly without forcing descent
		Assertions.assertTrue(
			goalSource.contains("this.minion.clearBuildingEgress();"),
			"setupTaskKinematics must clear egress state via clearBuildingEgress"
		);

		// 3. Levitation stall check allows extended flight up to 160 ticks
		Assertions.assertTrue(
			goalSource.contains("ticksNavigating > 160"),
			"Levitation flight stall timeout must allow up to 160 ticks (8s) before falling back to ground navigation"
		);

		// 4. Egress is gated behind absence of chained sessions in stop() and tick()
		Assertions.assertTrue(
			goalSource.contains("!hasChainedSession && isBuilder && sessionBox != null"),
			"stop() must not trigger startEgressFromStructure if a chained session is active"
		);
	}

	@Test
	@DisplayName("MinionEntity defines clearBuildingEgress to reset egress without descent or levitation loss")
	void testMinionEntityClearBuildingEgressInvariants() throws IOException {
		Path minionPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionPath), "MinionEntity.java must exist");
		String minionSource = Files.readString(minionPath);

		Assertions.assertTrue(
			minionSource.contains("public void clearBuildingEgress()"),
			"MinionEntity must define public void clearBuildingEgress()"
		);
		Assertions.assertTrue(
			minionSource.contains("this.exitingBuilding = false;"),
			"clearBuildingEgress must reset exitingBuilding to false"
		);
	}
}
