package com.example.construction;

import com.example.entity.ai.goal.MinionBuildGoal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.WallBlock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating connectable block clipping, bidirectional neighbor updates,
 * and seamless fence/pane snapping:
 * 1. Verification of connectable block class hierarchy (FenceBlock, PaneBlock, WallBlock, etc.).
 * 2. Null-safety validation for connectable evaluation and neighbor updates.
 * 3. Architectural source invariants in MinionBuildGoal and ConstructionSession.
 */
public class ConnectableBlockClippingAndNeighborUpdateTest {

	@Test
	@DisplayName("Connectable block hierarchy: Fences and Panes inherit from HorizontalConnectingBlock")
	void testConnectableBlockTypeHierarchy() {
		// Verify that fences, glass panes, and iron bars all inherit from HorizontalConnectingBlock
		Assertions.assertTrue(HorizontalConnectingBlock.class.isAssignableFrom(FenceBlock.class),
			"FenceBlock must inherit from HorizontalConnectingBlock");
		Assertions.assertTrue(HorizontalConnectingBlock.class.isAssignableFrom(PaneBlock.class),
			"PaneBlock (glass panes and iron bars) must inherit from HorizontalConnectingBlock");

		// Verify standalone connectable block types
		Assertions.assertTrue(WallBlock.class.isAssignableFrom(WallBlock.class),
			"WallBlock must be recognized");
		Assertions.assertTrue(StairsBlock.class.isAssignableFrom(StairsBlock.class),
			"StairsBlock must be recognized");
		Assertions.assertTrue(FenceGateBlock.class.isAssignableFrom(FenceGateBlock.class),
			"FenceGateBlock must be recognized");
		Assertions.assertTrue(ConnectingBlock.class.isAssignableFrom(ConnectingBlock.class),
			"ConnectingBlock must be recognized");
		Assertions.assertTrue(RedstoneWireBlock.class.isAssignableFrom(RedstoneWireBlock.class),
			"RedstoneWireBlock must be recognized");
		Assertions.assertTrue(TripwireBlock.class.isAssignableFrom(TripwireBlock.class),
			"TripwireBlock must be recognized");
	}

	@Test
	@DisplayName("Null-safety: Connectable evaluation and neighbor updates return false or handle null cleanly")
	void testConnectableNullSafety() {
		Assertions.assertFalse(ConstructionSession.isConnectableBlock(null),
			"Null state must return false for isConnectableBlock");
		Assertions.assertFalse(ConstructionSession.isConnectableMatching(null, null),
			"Null states must return false for isConnectableMatching");

		// Neighbor update helper must safely no-op when passed nulls
		Assertions.assertDoesNotThrow(() -> MinionBuildGoal.updateConnectableNeighbors(null, null),
			"updateConnectableNeighbors must safely handle null world and pos");
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal applies postProcessState before placement and updates connectable neighbors")
	void testMinionBuildGoalConnectableClippingInvariants() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// Must post-process connectable target block state before placing
		Assertions.assertTrue(source.contains("if (ConstructionSession.isConnectableBlock(targetState))"),
			"MinionBuildGoal must check isConnectableBlock before placing");
		Assertions.assertTrue(source.contains("targetState = Block.postProcessState(targetState, serverWorld, targetPos);"),
			"MinionBuildGoal must call Block.postProcessState on connectable target block before placing");

		// Must trigger bidirectional neighbor clipping updates after placing
		Assertions.assertTrue(source.contains("updateConnectableNeighbors(serverWorld, targetPos);"),
			"MinionBuildGoal must invoke updateConnectableNeighbors after block placement");

		// Must handle alreadyPlaced for connectables
		Assertions.assertTrue(source.contains("alreadyPlaced = ConstructionSession.isConnectableMatching(serverWorld.getBlockState(targetPos), targetState);"),
			"MinionBuildGoal must evaluate alreadyPlaced using isConnectableMatching for connectables");

		// Must update connectable neighbors after dismantling to prevent orphaned connections
		Assertions.assertTrue(source.contains("updateConnectableNeighbors(serverWorld, targetPos);"),
			"MinionBuildGoal must invoke updateConnectableNeighbors in executeDismantleWork");
	}

	@Test
	@DisplayName("Source Invariant: ConstructionSession integrates connectable block matching in claimNextTask")
	void testConstructionSessionConnectableInvariants() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));

		// Must preserve exact string invariant required by ConstructionTaskStealingAndPeerHandoffTest
		Assertions.assertTrue(source.contains("world.getBlockState(task.getWorldPos()).equals(task.getBlueprintBlock().state())"),
			"ConstructionSession must retain exact source string for peer handoff test compatibility");

		// Must also check connectable matching
		Assertions.assertTrue(source.contains("isConnectableBlock(task.getBlueprintBlock().state()) && isConnectableMatching(world.getBlockState(task.getWorldPos()), task.getBlueprintBlock().state())"),
			"ConstructionSession must evaluate isConnectableMatching in claimNextTask already-placed check");

		// Must verify isConnectableBlock checks HorizontalConnectingBlock and WallBlock
		Assertions.assertTrue(source.contains("block instanceof HorizontalConnectingBlock"),
			"isConnectableBlock must check HorizontalConnectingBlock");
		Assertions.assertTrue(source.contains("block instanceof WallBlock"),
			"isConnectableBlock must check WallBlock");
		Assertions.assertTrue(source.contains("block instanceof StairsBlock"),
			"isConnectableBlock must check StairsBlock");
		Assertions.assertTrue(source.contains("block instanceof FenceGateBlock"),
			"isConnectableBlock must check FenceGateBlock");

		// Must verify isConnectableMatching checks Stairs and Gate properties
		Assertions.assertTrue(source.contains("worldState.get(StairsBlock.FACING) == targetState.get(StairsBlock.FACING)"),
			"isConnectableMatching must check stairs facing");
		Assertions.assertTrue(source.contains("worldState.get(StairsBlock.HALF) == targetState.get(StairsBlock.HALF)"),
			"isConnectableMatching must check stairs half");
		Assertions.assertTrue(source.contains("worldState.get(FenceGateBlock.FACING) == targetState.get(FenceGateBlock.FACING)"),
			"isConnectableMatching must check fence gate facing");
	}
}
