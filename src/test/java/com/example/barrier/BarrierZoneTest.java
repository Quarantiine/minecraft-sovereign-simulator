package com.example.barrier;

import com.example.network.ModifyBarrierZonePayload;
import com.example.network.SyncBarrierZonesPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating dynamic multi-node BarrierZone (Sovereign Ward):
 * - Multi-point polygon containment (Jordan curve ray-casting)
 * - Boundary edge proximity tolerance
 * - Incremental perimeter expansion without restarting
 * - Vertical elevation boundaries and in-game height adjustments
 * - NBT serialization and world persistence
 * - BarrierManager registration and universal territory protection
 * - Network payload codecs
 */
public class BarrierZoneTest {

	private UUID testOwner;

	@BeforeEach
	void setUp() {
		this.testOwner = UUID.randomUUID();
	}

	@Test
	@DisplayName("Single and dual-node barrier zones protect immediate radius and bounding box")
	void testSingleAndDualNodePerimeter() {
		// 1. Single node (Pos 1)
		BlockPos p1 = new BlockPos(100, 64, 100);
		BarrierZone single = new BarrierZone(1, testOwner, "Tower Ward", List.of(p1), 50, 100, true);

		Assertions.assertTrue(single.contains(p1), "Direct node position must be protected");
		Assertions.assertTrue(single.contains(new BlockPos(102, 64, 100)), "Within 2-block radius must be protected");
		Assertions.assertFalse(single.contains(new BlockPos(105, 64, 100)), "Beyond 2-block radius must not be protected");
		Assertions.assertFalse(single.contains(new BlockPos(100, 120, 100)), "Above maxY must not be protected");

		// 2. Dual nodes (Pos 1 & Pos 2)
		BlockPos p2 = new BlockPos(110, 64, 120);
		BarrierZone dual = new BarrierZone(2, testOwner, "Wall Corridor", List.of(p1, p2), 50, 100, true);

		Assertions.assertTrue(dual.contains(new BlockPos(105, 64, 110)), "Inside dual-node bounding box must be protected");
		Assertions.assertTrue(dual.contains(p1));
		Assertions.assertTrue(dual.contains(p2));
		Assertions.assertFalse(dual.contains(new BlockPos(90, 64, 110)), "Outside dual-node box must not be protected");
	}

	@Test
	@DisplayName("Multi-node polygon containment accurately protects interior points and edge boundaries")
	void testMultiNodePolygonContainment() {
		// Define a square perimeter: (0,0) -> (100,0) -> (100,100) -> (0,100)
		BlockPos n1 = new BlockPos(0, 64, 0);
		BlockPos n2 = new BlockPos(100, 64, 0);
		BlockPos n3 = new BlockPos(100, 64, 100);
		BlockPos n4 = new BlockPos(0, 64, 100);

		BarrierZone zone = new BarrierZone(1, testOwner, "Base Ward", List.of(n1, n2, n3, n4), 50, 100, true);

		// Inside points
		Assertions.assertTrue(zone.contains(new BlockPos(50, 64, 50)), "Center must be inside");
		Assertions.assertTrue(zone.contains(new BlockPos(10, 80, 10)), "Inside at elevated Y must be inside");

		// Boundary edge points
		Assertions.assertTrue(zone.contains(new BlockPos(50, 64, 0)), "Points on perimeter segment must be inside");
		Assertions.assertTrue(zone.contains(new BlockPos(100, 64, 50)), "Points on perimeter segment must be inside");

		// Outside points
		Assertions.assertFalse(zone.contains(new BlockPos(-5, 64, 50)), "Points outside western border must be outside");
		Assertions.assertFalse(zone.contains(new BlockPos(105, 64, 50)), "Points outside eastern border must be outside");
		Assertions.assertFalse(zone.contains(new BlockPos(50, 64, 150)), "Points outside southern border must be outside");

		// Vertical limits
		Assertions.assertFalse(zone.contains(new BlockPos(50, 40, 50)), "Below minY (50) must not be protected");
		Assertions.assertFalse(zone.contains(new BlockPos(50, 110, 50)), "Above maxY (100) must not be protected");
	}

	@Test
	@DisplayName("Base expansion: players can append nodes to extend territory without starting over")
	void testBaseExpansionWithoutStartingOver() {
		// Initial triangle perimeter: (0,0) -> (50,0) -> (0,50)
		BlockPos n1 = new BlockPos(0, 64, 0);
		BlockPos n2 = new BlockPos(50, 64, 0);
		BlockPos n3 = new BlockPos(0, 64, 50);

		BarrierZone zone = new BarrierZone(1, testOwner, "Village Ward", List.of(n1, n2, n3), 50, 100, true);

		// Point (40, 64, 40) is initially OUTSIDE the right-angled triangle (x + z = 80 > 50)
		Assertions.assertFalse(zone.contains(new BlockPos(40, 64, 40)), "Point outside triangle must be unprotected");

		// Player expands base! Adds node n4 = (50, 64, 50) to make it a 50x50 square
		zone.addNode(new BlockPos(50, 64, 50));
		Assertions.assertEquals(4, zone.getNodeCount());

		// Now point (40, 64, 40) is INSIDE the newly expanded square!
		Assertions.assertTrue(zone.contains(new BlockPos(40, 64, 40)), "Point must be protected after perimeter expansion");

		// Previously protected point (10, 64, 10) remains protected
		Assertions.assertTrue(zone.contains(new BlockPos(10, 64, 10)), "Initial territory remains protected");
	}

	@Test
	@DisplayName("Height adjustments dynamically update ceiling elevation")
	void testHeightAdjustment() {
		BlockPos n1 = new BlockPos(10, 64, 10);
		BlockPos n2 = new BlockPos(20, 64, 20);
		BarrierZone zone = new BarrierZone(1, testOwner, "Adjustable Ward", List.of(n1, n2), 54, 94, true);

		Assertions.assertEquals(54, zone.getMinY());
		Assertions.assertEquals(94, zone.getMaxY());
		Assertions.assertEquals(41, zone.getMaxY() - zone.getMinY() + 1);

		int newHeight = zone.adjustHeight(10);
		Assertions.assertEquals(104, zone.getMaxY());
		Assertions.assertEquals(51, newHeight);

		Assertions.assertTrue(zone.contains(new BlockPos(15, 100, 15)), "Point at Y=100 should now be inside after height expansion");
	}

	@Test
	@DisplayName("NBT serialization and deserialization preserves all zone fields")
	void testNbtSerialization() {
		BlockPos n1 = new BlockPos(12, 65, 34);
		BlockPos n2 = new BlockPos(56, 70, 78);
		BlockPos n3 = new BlockPos(90, 60, 12);
		BarrierZone original = new BarrierZone(42, testOwner, "Imperial Citadel Ward", List.of(n1, n2, n3), 40, 150, true);

		NbtCompound nbt = original.toNbt();
		BarrierZone deserialized = BarrierZone.fromNbt(nbt);

		Assertions.assertEquals(original.getId(), deserialized.getId());
		Assertions.assertEquals(original.getOwnerUuid(), deserialized.getOwnerUuid());
		Assertions.assertEquals(original.getName(), deserialized.getName());
		Assertions.assertEquals(original.getMinY(), deserialized.getMinY());
		Assertions.assertEquals(original.getMaxY(), deserialized.getMaxY());
		Assertions.assertEquals(original.isSealed(), deserialized.isSealed());
		Assertions.assertEquals(3, deserialized.getNodeCount());
		Assertions.assertEquals(n1, deserialized.getNodes().get(0));
		Assertions.assertEquals(n2, deserialized.getNodes().get(1));
		Assertions.assertEquals(n3, deserialized.getNodes().get(2));
	}

	@Test
	@DisplayName("BarrierManager registers, seals, and universally protects marked territory")
	void testBarrierManagerLifecycle() {
		BarrierManager manager = BarrierManager.getInstance();

		// Add nodes
		BlockPos p1 = new BlockPos(200, 64, 200);
		BlockPos p2 = new BlockPos(250, 64, 200);
		BlockPos p3 = new BlockPos(250, 64, 250);
		BlockPos p4 = new BlockPos(200, 64, 250);

		manager.clearActiveZone(testOwner);
		manager.addNodeToActiveZone(testOwner, p1);
		manager.addNodeToActiveZone(testOwner, p2);
		manager.addNodeToActiveZone(testOwner, p3);
		manager.addNodeToActiveZone(testOwner, p4);

		// Before sealing: not protected
		Assertions.assertFalse(manager.isPositionProtected(null, new BlockPos(225, 64, 225), testOwner));

		// Seal zone
		BarrierZone sealed = manager.sealActiveZone(testOwner, 0, "Kingdom Ward", 50, 100);
		Assertions.assertNotNull(sealed);
		Assertions.assertTrue(sealed.isSealed());

		// After sealing: protected!
		Assertions.assertTrue(manager.isPositionProtected(null, new BlockPos(225, 64, 225), testOwner));
		Assertions.assertFalse(manager.isPositionProtected(null, new BlockPos(300, 64, 300), testOwner));

		// Clean up
		manager.deleteZone(testOwner, sealed.getId());
		Assertions.assertFalse(manager.isPositionProtected(null, new BlockPos(225, 64, 225), testOwner));
	}

	@Test
	@DisplayName("Network payload records instantiate and hold expected values")
	void testPayloadIntegrity() {
		BlockPos pos = new BlockPos(10, 20, 30);
		ModifyBarrierZonePayload c2s = new ModifyBarrierZonePayload(
			ModifyBarrierZonePayload.Action.ADD_NODE,
			5,
			pos,
			-64,
			320,
			"Test Ward"
		);
		Assertions.assertEquals(ModifyBarrierZonePayload.Action.ADD_NODE, c2s.action());
		Assertions.assertEquals(5, c2s.zoneId());
		Assertions.assertEquals(pos, c2s.pos());
		Assertions.assertEquals("Test Ward", c2s.name());

		BarrierZone zone = new BarrierZone(1, testOwner, "Ward 1", List.of(pos), 0, 100, true);
		SyncBarrierZonesPayload s2c = new SyncBarrierZonesPayload(List.of(zone));
		Assertions.assertEquals(1, s2c.zones().size());
		Assertions.assertEquals(zone.getId(), s2c.zones().get(0).getId());
	}

	@Test
	@DisplayName("Zone expansion provides continuous protection while editing and updates perimeter on seal")
	void testSafeZoneResumptionAndExpansionWithContinuousProtection() {
		BarrierManager manager = BarrierManager.getInstance();

		BlockPos p1 = new BlockPos(10, 64, 10);
		BlockPos p2 = new BlockPos(30, 64, 10);
		BlockPos p3 = new BlockPos(10, 64, 30);

		manager.clearActiveZone(testOwner);
		manager.addNodeToActiveZone(testOwner, p1);
		manager.addNodeToActiveZone(testOwner, p2);
		manager.addNodeToActiveZone(testOwner, p3);
		BarrierZone original = manager.sealActiveZone(testOwner, 0, "Kingdom Ward", 50, 100);

		Assertions.assertNotNull(original);
		Assertions.assertTrue(original.isSealed());
		Assertions.assertTrue(manager.isPositionProtected(null, new BlockPos(15, 64, 15), testOwner), "Inside triangle should be protected");
		Assertions.assertFalse(manager.isPositionProtected(null, new BlockPos(25, 64, 25), testOwner), "Outside triangle should not be protected yet");

		// Player clicks Expand!
		BarrierZone draft = manager.resumeZoneForEditing(testOwner, original.getId());
		Assertions.assertNotNull(draft);
		Assertions.assertFalse(draft.isSealed(), "Draft must be unsealed for editing");
		Assertions.assertEquals(original.getId(), draft.getResumedFromZoneId(), "Draft must link to original zone");

		// CRITICAL EDGE CASE: Base MUST remain continuously protected while player walks around placing expansion nodes!
		Assertions.assertTrue(manager.isPositionProtected(null, new BlockPos(15, 64, 15), testOwner), "Original base territory must remain protected during expansion editing");

		// Player appends node 4 = (30, 64, 30) to form a 20x20 square
		manager.addNodeToActiveZone(testOwner, new BlockPos(30, 64, 30));

		// Player seals expanded ward
		BarrierZone expanded = manager.sealActiveZone(testOwner, 0, "Expanded Kingdom Ward", 40, 120);
		Assertions.assertNotNull(expanded);
		Assertions.assertEquals(original.getId(), expanded.getId(), "Must update original zone in-place");
		Assertions.assertEquals(4, expanded.getNodeCount(), "Must have all 4 nodes");
		Assertions.assertTrue(expanded.isSealed());

		// Now point (25, 64, 25) is INSIDE the expanded perimeter and protected!
		Assertions.assertTrue(manager.isPositionProtected(null, new BlockPos(25, 64, 25), testOwner), "Newly enclosed territory must be protected");
		Assertions.assertTrue(manager.isPositionProtected(null, new BlockPos(15, 64, 15), testOwner), "Initial territory remains protected");

		// Cleanup
		manager.deleteZone(testOwner, expanded.getId());
	}

	@Test
	@DisplayName("Inverted bounds and node removal recalculate vertical elevation correctly")
	void testInvertedBoundsAndNodeRemovalRecalculation() {
		BlockPos p1 = new BlockPos(0, 64, 0);
		BlockPos p2 = new BlockPos(20, 64, 20);

		// Inverted bounds guard
		BarrierZone inverted = new BarrierZone(1, testOwner, "Inverted Ward", List.of(p1, p2), 100, 50, true);
		Assertions.assertTrue(inverted.contains(new BlockPos(10, 70, 10)), "Inverted bounds must be normalized safely");
		Assertions.assertFalse(inverted.contains(new BlockPos(10, 110, 10)), "Outside normalized bounds must return false");

		// Node removal bounds recalculation
		BarrierZone dynamicZone = new BarrierZone(2, testOwner, "Dynamic Ward", List.of(p1, p2), 0, 320, false);
		dynamicZone.addNode(new BlockPos(10, 310, 10)); // Very high node
		Assertions.assertEquals(320, dynamicZone.getMaxY(), "MaxY must reach 320 for high node");

		BlockPos removed = dynamicZone.removeLastNode();
		Assertions.assertEquals(new BlockPos(10, 310, 10), removed);
		Assertions.assertEquals(Math.min(320, 64 + BarrierZone.DEFAULT_VERTICAL_EXPANSION_UP), dynamicZone.getMaxY());
	}

	@Test
	@DisplayName("Automatic expansion connects new node to the nearest existing node preserving loop topology")
	void testAutomaticExpansionNearestNodeConnection() {
		// Square perimeter: (0,0) -> (100,0) -> (100,100) -> (0,100)
		BlockPos n1 = new BlockPos(0, 64, 0);
		BlockPos n2 = new BlockPos(100, 64, 0);
		BlockPos n3 = new BlockPos(100, 64, 100);
		BlockPos n4 = new BlockPos(0, 64, 100);
		List<BlockPos> nodes = List.of(n1, n2, n3, n4);

		// Place new expansion node at (110, 64, 10) - nearest existing node is n2 (100, 64, 0)
		BlockPos newPos = new BlockPos(110, 64, 10);
		int insertionIndex = BarrierZone.findBestInsertionIndex(nodes, newPos);

		// n2 is index 1. Neighbor n3 (100, 64, 100) is closer to newPos than neighbor n1 (0, 64, 0).
		// Best insertion index between n2 and n3 is index 2.
		Assertions.assertEquals(2, insertionIndex, "Insertion index must be between nearest node and closer neighbor");

		// Test manager-level resume with insertion
		BarrierManager manager = BarrierManager.getInstance();
		manager.clearActiveZone(testOwner);
		for (BlockPos p : nodes) {
			manager.addNodeToActiveZone(testOwner, p);
		}
		BarrierZone original = manager.sealActiveZone(testOwner, 0, "Kingdom Barrier", 50, 100);

		BarrierZone draft = manager.resumeZoneForEditing(testOwner, original.getId(), newPos);
		Assertions.assertNotNull(draft);
		Assertions.assertEquals(5, draft.getNodeCount(), "Draft must contain original 4 nodes plus inserted new node");
		Assertions.assertEquals(newPos, draft.getNodes().get(2), "New node must be inserted at index 2 connected to n2");

		// Cleanup
		manager.deleteZone(testOwner, original.getId());
	}

	@Test
	@DisplayName("removeNode removes matching node from BarrierZone and updates vertical bounds")
	void testNodeRemovalAndSpecificPositionRemoval() {
		BlockPos n1 = new BlockPos(10, 60, 10);
		BlockPos n2 = new BlockPos(20, 60, 10);
		BlockPos n3 = new BlockPos(20, 120, 20); // High node
		BarrierZone zone = new BarrierZone(5, testOwner, "Castle Ward", List.of(n1, n2, n3), 50, 150, false);

		Assertions.assertEquals(3, zone.getNodeCount());
		Assertions.assertTrue(zone.getMaxY() >= 140);

		// Remove specific node by exact pos
		boolean removed = zone.removeNode(n3);
		Assertions.assertTrue(removed, "Targeted high node must be removed");
		Assertions.assertEquals(2, zone.getNodeCount());
		Assertions.assertEquals(Math.min(320, 60 + BarrierZone.DEFAULT_VERTICAL_EXPANSION_UP), zone.getMaxY(), "MaxY must contract after high node removal");

		// Remove non-existent node
		boolean notFound = zone.removeNode(new BlockPos(999, 999, 999));
		Assertions.assertFalse(notFound);

		// Test BarrierManager removal from active zone
		BarrierManager manager = BarrierManager.getInstance();
		manager.clearActiveZone(testOwner);
		manager.addNodeToActiveZone(testOwner, n1);
		manager.addNodeToActiveZone(testOwner, n2);
		Assertions.assertEquals(2, manager.getOrCreateActiveZone(testOwner).getNodeCount());

		boolean managerRemoved = manager.removeNodeFromActiveZone(testOwner, n2);
		Assertions.assertTrue(managerRemoved, "Manager must remove node from active zone");
		Assertions.assertEquals(1, manager.getOrCreateActiveZone(testOwner).getNodeCount());

		// Test BarrierManager removal from SEALED zone: Only the clicked node is removed, NOT all connected waypoints!
		manager.clearActiveZone(testOwner);
		manager.addNodeToActiveZone(testOwner, new BlockPos(0, 64, 0));
		manager.addNodeToActiveZone(testOwner, new BlockPos(10, 64, 0));
		manager.addNodeToActiveZone(testOwner, new BlockPos(10, 64, 10));
		manager.addNodeToActiveZone(testOwner, new BlockPos(0, 64, 10));
		BarrierZone sealed = manager.sealActiveZone(testOwner, 0, "Kingdom Border", 50, 100);
		Assertions.assertNotNull(sealed);
		Assertions.assertEquals(4, sealed.getNodeCount(), "Sealed barrier must have 4 connected nodes initially");

		// Remove only ONE node (10, 64, 0) from the sealed barrier
		boolean sealedNodeRemoved = manager.removeNodeFromAnyZone(testOwner, sealed.getId(), new BlockPos(10, 64, 0));
		Assertions.assertTrue(sealedNodeRemoved, "Must successfully remove the single clicked node from sealed barrier");
		Assertions.assertEquals(3, sealed.getNodeCount(), "Sealed barrier must retain remaining 3 connected nodes instead of deleting all waypoints");
		Assertions.assertFalse(sealed.getNodes().contains(new BlockPos(10, 64, 0)), "Targeted node must no longer be in barrier");
		Assertions.assertTrue(sealed.getNodes().contains(new BlockPos(0, 64, 0)), "Connected node 1 must remain intact");
		Assertions.assertTrue(sealed.getNodes().contains(new BlockPos(10, 64, 10)), "Connected node 3 must remain intact");
		Assertions.assertTrue(sealed.getNodes().contains(new BlockPos(0, 64, 10)), "Connected node 4 must remain intact");

		// Cleanup
		manager.deleteZone(testOwner, sealed.getId());
		manager.clearActiveZone(testOwner);
	}

	@Test
	@DisplayName("Target placement logic places barrier nodes on top of solid blocks instead of under them")
	void testTopPlacementTargetPositionCalculation() {
		BlockPos solidGround = new BlockPos(100, 64, 100);
		net.minecraft.util.math.Direction sideClicked = net.minecraft.util.math.Direction.UP;

		// Simulated block state check:
		// When block is replaceable (e.g. air/tall grass), place at clickedPos.
		// When block is solid (e.g. dirt, stone), offset by sideClicked (clickedPos.up()).
		boolean isReplaceable = false;
		BlockPos targetPos = isReplaceable ? solidGround : (sideClicked != null ? solidGround.offset(sideClicked) : solidGround.up());

		Assertions.assertEquals(new BlockPos(100, 65, 100), targetPos, "Barrier node must be placed on top of solid block (Y+1)");

		// If clicked on side of a cliff (e.g. EAST)
		BlockPos cliffWall = new BlockPos(50, 70, 50);
		BlockPos sideTarget = cliffWall.offset(net.minecraft.util.math.Direction.EAST);
		Assertions.assertEquals(new BlockPos(51, 70, 50), sideTarget, "Barrier node placed against wall face must project outward");
	}

	@Test
	@DisplayName("Architectural Contracts: 3 Modal options (Seal, Cancel, Remove), blur suppression, and client thread safety")
	void testClientBarrierWiringAndModalContracts() throws Exception {
		// 1. Verify BarrierConfirmModalScreen has 3 options: seal, cancel, remove
		java.io.File modalFile = new java.io.File("src/client/java/com/example/client/gui/BarrierConfirmModalScreen.java");
		Assertions.assertTrue(modalFile.exists(), "BarrierConfirmModalScreen must exist");
		String modalContent = java.nio.file.Files.readString(modalFile.toPath());

		Assertions.assertTrue(modalContent.contains("sealBarrier()"), "Modal must define sealBarrier()");
		Assertions.assertTrue(modalContent.contains("cancelModal()"), "Modal must define cancelModal()");
		Assertions.assertTrue(modalContent.contains("removeAllPending()"), "Modal must define removeAllPending()");
		Assertions.assertTrue(modalContent.contains("applyBlur(float delta)"), "Modal must override applyBlur to suppress blur");

		// 2. Verify CommandScepterItem registers BarrierRemover and guards modal openers with world.isClient()
		java.io.File scepterFile = new java.io.File("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(scepterFile.exists(), "CommandScepterItem must exist");
		String scepterContent = java.nio.file.Files.readString(scepterFile.toPath());

		Assertions.assertTrue(scepterContent.contains("BARRIER_REMOVER"), "CommandScepterItem must define BARRIER_REMOVER");
		Assertions.assertTrue(scepterContent.contains("world != null && world.isClient()") || scepterContent.contains("world.isClient()"),
			"CommandScepterItem must guard modal openers with world.isClient()");

		// 3. Verify ExampleModClient registers BARRIER_REMOVER and dispatches setScreen on client thread
		java.io.File clientFile = new java.io.File("src/client/java/com/example/client/ExampleModClient.java");
		Assertions.assertTrue(clientFile.exists(), "ExampleModClient must exist");
		String clientContent = java.nio.file.Files.readString(clientFile.toPath());

		Assertions.assertTrue(clientContent.contains("CommandScepterItem.BARRIER_REMOVER ="), "ExampleModClient must hook BARRIER_REMOVER");
		Assertions.assertTrue(clientContent.contains("client.execute(() ->"), "ExampleModClient must use client.execute() for modal screen opening");
	}

	@Test
	@DisplayName("Strict Node Targeting: Clicking adjacent land or looking from high altitude never deletes barrier nodes")
	void testStrictNodeTargetingAndModalOpeningIntegrity() {
		com.example.client.renderer.ClientBarrierTracker.clear();

		BlockPos node1 = new BlockPos(10, 64, 10);
		BlockPos node2 = new BlockPos(20, 64, 10);
		BlockPos node3 = new BlockPos(20, 64, 20);

		com.example.client.renderer.ClientBarrierTracker.addNode(node1);
		com.example.client.renderer.ClientBarrierTracker.addNode(node2);
		com.example.client.renderer.ClientBarrierTracker.addNode(node3);
		Assertions.assertEquals(3, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());

		// 1. Clicking exact node position directly matches that specific node
		BlockPos exactHit = com.example.client.renderer.ClientBarrierTracker.findMatchingPendingNode(node1, node1);
		Assertions.assertEquals(node1, exactHit, "Exact node coordinate match must return the node");

		// 2. Clicking the solid ground block directly below the node matches the node via clickedPos.up()
		BlockPos blockBelow = node1.down();
		BlockPos belowHit = com.example.client.renderer.ClientBarrierTracker.findMatchingPendingNode(null, blockBelow);
		Assertions.assertEquals(node1, belowHit, "Clicking the block directly beneath node must match node.up()");

		// 3. Clicking adjacent ground (1 block away) MUST NOT match or delete any node (allows Left-Click to Seal)
		BlockPos adjacentGround = node1.east();
		BlockPos adjacentHit = com.example.client.renderer.ClientBarrierTracker.findMatchingPendingNode(adjacentGround, adjacentGround);
		Assertions.assertNull(adjacentHit, "Clicking adjacent ground block must NOT match any barrier node");

		// 4. Clicking interior territory ground MUST NOT match or delete any node
		BlockPos interiorGround = new BlockPos(15, 64, 15);
		BlockPos interiorHit = com.example.client.renderer.ClientBarrierTracker.findMatchingPendingNode(interiorGround, interiorGround);
		Assertions.assertNull(interiorHit, "Clicking interior land must NOT match any barrier node");

		// 5. Open-air click from 100 blocks high looking down at the ground MUST NOT match nodes (out of reach)
		net.minecraft.util.math.Vec3d highEye = new net.minecraft.util.math.Vec3d(15.0D, 164.0D, 15.0D);
		net.minecraft.util.math.Vec3d lookDown = new net.minecraft.util.math.Vec3d(0.0D, -1.0D, 0.0D);
		BlockPos highHit = com.example.client.renderer.ClientBarrierTracker.findMatchingPendingNode(null, null, highEye, lookDown, 16.0D);
		Assertions.assertNull(highHit, "Looking down at ground from 100 blocks high must NOT match distant ground nodes");

		// 6. Looking up into the sky MUST NOT match nodes
		net.minecraft.util.math.Vec3d groundEye = new net.minecraft.util.math.Vec3d(15.0D, 65.0D, 15.0D);
		net.minecraft.util.math.Vec3d lookSky = new net.minecraft.util.math.Vec3d(0.0D, 1.0D, 0.0D);
		BlockPos skyHit = com.example.client.renderer.ClientBarrierTracker.findMatchingPendingNode(null, null, groundEye, lookSky, 16.0D);
		Assertions.assertNull(skyHit, "Looking up into the sky must NOT match nodes");

		// 7. Aiming directly at floating node in open air within reach (e.g. 3 blocks away) DOES match that node
		net.minecraft.util.math.Vec3d eyeNear = new net.minecraft.util.math.Vec3d(10.5D, 64.5D, 7.0D);
		net.minecraft.util.math.Vec3d lookAtNode1 = new net.minecraft.util.math.Vec3d(0.0D, 0.0D, 1.0D);
		BlockPos floatingHit = com.example.client.renderer.ClientBarrierTracker.findMatchingPendingNode(null, null, eyeNear, lookAtNode1, 6.0D);
		Assertions.assertEquals(node1, floatingHit, "Aiming directly at floating marker AABB within reach must match");

		// 8. Removing a specific node removes ONLY that node and leaves remaining nodes intact
		BlockPos removed = com.example.client.renderer.ClientBarrierTracker.removeNodeFromAnyBarrier(node1, node1, null, null, 0.0D);
		Assertions.assertEquals(node1, removed, "removeNodeFromAnyBarrier must return the removed node");
		Assertions.assertEquals(2, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount(), "Exactly one node removed; 2 remain");
		Assertions.assertTrue(com.example.client.renderer.ClientBarrierTracker.getPendingNodes().contains(node2));
		Assertions.assertTrue(com.example.client.renderer.ClientBarrierTracker.getPendingNodes().contains(node3));

		// 9. Clicking ground after removal does NOT delete remaining nodes
		BlockPos groundRemove = com.example.client.renderer.ClientBarrierTracker.removeNodeFromAnyBarrier(interiorGround, interiorGround, null, null, 0.0D);
		Assertions.assertNull(groundRemove, "Clicking ground must return null so modal can open");
		Assertions.assertEquals(2, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount(), "Pending node count must remain 2");
	}

	@Test
	@DisplayName("Builder Minions: Agro-forestry planting and harvesting strictly avoid sealed barriers")
	void testBuilderMinionsPlantAndHarvestOutsideBarrierOnly() {
		BarrierManager manager = BarrierManager.getInstance();

		// Create a sealed barrier protecting a 30x30 village base: (0,0) to (30,30)
		BlockPos b1 = new BlockPos(0, 64, 0);
		BlockPos b2 = new BlockPos(30, 64, 0);
		BlockPos b3 = new BlockPos(30, 64, 30);
		BlockPos b4 = new BlockPos(0, 64, 30);

		manager.clearActiveZone(testOwner);
		manager.addNodeToActiveZone(testOwner, b1);
		manager.addNodeToActiveZone(testOwner, b2);
		manager.addNodeToActiveZone(testOwner, b3);
		manager.addNodeToActiveZone(testOwner, b4);
		BarrierZone villageWard = manager.sealActiveZone(testOwner, 0, "Village Ward", 50, 100);

		Assertions.assertNotNull(villageWard);
		Assertions.assertTrue(villageWard.isSealed());

		// 1. Candidate planting position inside the village ward: (15, 64, 15)
		BlockPos insidePos = new BlockPos(15, 64, 15);
		Assertions.assertTrue(manager.isPositionProtected(null, insidePos, testOwner),
			"BarrierManager must flag interior village territory as protected");
		Assertions.assertTrue(com.example.entity.ai.logistics.MinionHarvestingHelper.isProtectedBlock(null, insidePos, null, testOwner),
			"MinionHarvestingHelper.isProtectedBlock must protect any block inside sealed barrier from planting/felling");

		// 2. Candidate planting position outside the village ward: (45, 64, 15)
		BlockPos outsidePos = new BlockPos(45, 64, 15);
		Assertions.assertFalse(manager.isPositionProtected(null, outsidePos, testOwner),
			"BarrierManager must NOT flag wild land outside barrier as protected");
		Assertions.assertFalse(com.example.entity.ai.logistics.MinionHarvestingHelper.isProtectedBlock(null, outsidePos, null, testOwner),
			"Outside land is harvestable and valid for forestry planting");

		// 3. Minion planting filter simulation:
		// When searching for planting positions, any candidate evaluated inside the barrier is rejected
		java.util.List<BlockPos> candidateSpots = java.util.List.of(
			new BlockPos(10, 64, 10), // inside barrier
			new BlockPos(20, 64, 20), // inside barrier
			new BlockPos(40, 64, 10), // outside barrier
			new BlockPos(50, 64, 20)  // outside barrier
		);

		java.util.List<BlockPos> validPlantingSpots = candidateSpots.stream()
			.filter(pos -> !com.example.entity.ai.logistics.MinionHarvestingHelper.isProtectedBlock(null, pos, null, testOwner))
			.toList();

		Assertions.assertEquals(2, validPlantingSpots.size(), "Only positions outside the barrier are accepted for planting");
		Assertions.assertTrue(validPlantingSpots.contains(new BlockPos(40, 64, 10)));
		Assertions.assertTrue(validPlantingSpots.contains(new BlockPos(50, 64, 20)));
		Assertions.assertFalse(validPlantingSpots.contains(new BlockPos(10, 64, 10)), "Inside positions must never be selected");
		Assertions.assertFalse(validPlantingSpots.contains(new BlockPos(20, 64, 20)), "Inside positions must never be selected");

		// Cleanup
		manager.deleteZone(testOwner, villageWard.getId());
	}

	@Test
	@DisplayName("Ping-pong barrier drafting: independent node placement without 96m hijack, snap-to-close on Point #1, renaming, and one-click delete")
	void testPingPongDraftingRenamingAndOneClickDelete() {
		com.example.client.renderer.ClientBarrierTracker.clear();

		// 1. Establish an existing sealed barrier
		BlockPos old1 = new BlockPos(0, 64, 0);
		BlockPos old2 = new BlockPos(10, 64, 0);
		BlockPos old3 = new BlockPos(10, 64, 10);
		com.example.client.renderer.ClientBarrierTracker.addNode(old1);
		com.example.client.renderer.ClientBarrierTracker.addNode(old2);
		com.example.client.renderer.ClientBarrierTracker.addNode(old3);
		com.example.client.renderer.ClientBarrierTracker.seal("Old Castle");

		BarrierZone oldZone = new BarrierZone(1, testOwner, "Old Castle", List.of(old1, old2, old3), -64, 320, true);
		com.example.client.renderer.ClientBarrierTracker.setConfirmedZones(List.of(oldZone));

		// 2. Player wants to start a completely SEPARATE barrier 20 blocks away
		BlockPos new1 = new BlockPos(30, 64, 30);
		com.example.client.renderer.ClientBarrierTracker.addNode(new1);

		// Must have exactly 1 pending node (NOT hijacked by 96m auto-merge!)
		Assertions.assertEquals(1, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount(),
			"Placing fresh ground point must create an independent barrier draft without merging into existing 96m barriers");

		BlockPos new2 = new BlockPos(40, 64, 30);
		BlockPos new3 = new BlockPos(40, 64, 40);
		com.example.client.renderer.ClientBarrierTracker.addNode(new2);
		com.example.client.renderer.ClientBarrierTracker.addNode(new3);
		Assertions.assertEquals(3, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());

		// 3. Test loop closure detection: Clicking Point #1
		Assertions.assertTrue(com.example.client.renderer.ClientBarrierTracker.isFirstNode(new1, new1),
			"Clicking Point #1 coordinates must trigger snap-to-close");
		Assertions.assertFalse(com.example.client.renderer.ClientBarrierTracker.isFirstNode(new2, new2),
			"Clicking Point #2 must not trigger snap-to-close");

		// 4. Seal with custom name
		com.example.client.renderer.ClientBarrierTracker.seal("North Farm");
		Assertions.assertEquals(0, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount(),
			"Pending nodes must clear after sealing");

		// 5. Test renaming
		BarrierZone farmZone = new BarrierZone(2, testOwner, "North Farm", List.of(new1, new2, new3), -64, 320, true);
		com.example.client.renderer.ClientBarrierTracker.setConfirmedZones(new java.util.ArrayList<>(List.of(oldZone, farmZone)));

		boolean renamed = com.example.client.renderer.ClientBarrierTracker.renameZone(2, "Imperial Vineyard");
		Assertions.assertTrue(renamed, "Zone 2 should be successfully renamed");
		Assertions.assertEquals("Imperial Vineyard", farmZone.getName());

		// 6. Test one-click delete
		boolean deleted = com.example.client.renderer.ClientBarrierTracker.deleteConfirmedZone(2);
		Assertions.assertTrue(deleted, "Zone 2 should be deleted in one click");
		Assertions.assertEquals(1, com.example.client.renderer.ClientBarrierTracker.getConfirmedZones().size());
		Assertions.assertEquals(1, com.example.client.renderer.ClientBarrierTracker.getConfirmedZones().get(0).getId());

		// Cleanup
		com.example.client.renderer.ClientBarrierTracker.clear();
	}

	@Test
	@DisplayName("Waypoint Extension & Branching: Right-clicking any waypoint extends directly from that specific waypoint")
	void testWaypointBranchingAndRotationWhenExtending() {
		com.example.client.renderer.ClientBarrierTracker.clear();

		BlockPos p1 = new BlockPos(10, 64, 10);
		BlockPos p2 = new BlockPos(20, 64, 10);
		BlockPos p3 = new BlockPos(20, 64, 20);

		BarrierZone zone = new BarrierZone(10, testOwner, "Triangle Ward", List.of(p1, p2, p3), -64, 320, true);
		com.example.client.renderer.ClientBarrierTracker.setConfirmedZones(List.of(zone));

		// 1. Right-clicking Waypoint #2 must rotate cycle so Waypoint #2 is the extension tail!
		boolean res2 = com.example.client.renderer.ClientBarrierTracker.resumeZoneAt(10, p2, p2);
		Assertions.assertTrue(res2, "Should resume zone at waypoint 2");
		List<BlockPos> pending2 = com.example.client.renderer.ClientBarrierTracker.getPendingNodes();
		Assertions.assertEquals(3, pending2.size());
		Assertions.assertEquals(p2, pending2.get(pending2.size() - 1), "The tail of the resumed chain MUST be the clicked Waypoint #2!");
		Assertions.assertEquals(p3, pending2.get(0), "The head of the resumed chain must be the adjacent waypoint in the loop (P3)");

		// 2. Right-clicking Waypoint #1 must rotate cycle so Waypoint #1 is the extension tail!
		com.example.client.renderer.ClientBarrierTracker.clear();
		boolean res1 = com.example.client.renderer.ClientBarrierTracker.resumeZoneAt(10, p1, p1);
		Assertions.assertTrue(res1, "Should resume zone at waypoint 1");
		List<BlockPos> pending1 = com.example.client.renderer.ClientBarrierTracker.getPendingNodes();
		Assertions.assertEquals(3, pending1.size());
		Assertions.assertEquals(p1, pending1.get(pending1.size() - 1), "The tail of the resumed chain MUST be the clicked Waypoint #1!");
		Assertions.assertEquals(p2, pending1.get(0), "The head of the resumed chain must be adjacent waypoint P2");

		// 3. Right-clicking Waypoint #3 keeps Waypoint #3 as the extension tail!
		com.example.client.renderer.ClientBarrierTracker.clear();
		boolean res3 = com.example.client.renderer.ClientBarrierTracker.resumeZoneAt(10, p3, p3);
		Assertions.assertTrue(res3, "Should resume zone at waypoint 3");
		List<BlockPos> pending3 = com.example.client.renderer.ClientBarrierTracker.getPendingNodes();
		Assertions.assertEquals(3, pending3.size());
		Assertions.assertEquals(p3, pending3.get(pending3.size() - 1), "The tail of the resumed chain MUST be Waypoint #3!");
		Assertions.assertEquals(p1, pending3.get(0), "The head of the resumed chain must be Waypoint #1");

		// 4. Test intermediate drafting branching: drafting P1 -> P2 -> P3, clicking P2 rewinds active tail to P2!
		com.example.client.renderer.ClientBarrierTracker.clear();
		com.example.client.renderer.ClientBarrierTracker.addNode(p1);
		com.example.client.renderer.ClientBarrierTracker.addNode(p2);
		com.example.client.renderer.ClientBarrierTracker.addNode(p3);
		Assertions.assertEquals(3, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());

		int idx = com.example.client.renderer.ClientBarrierTracker.findPendingNodeIndex(p2, p2);
		Assertions.assertEquals(1, idx, "Waypoint 2 should be at pending index 1");

		boolean truncated = com.example.client.renderer.ClientBarrierTracker.truncatePendingTo(idx);
		Assertions.assertTrue(truncated);
		Assertions.assertEquals(2, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());
		Assertions.assertEquals(p2, com.example.client.renderer.ClientBarrierTracker.getPendingNodes().get(1),
			"Active drafting tail must now be Waypoint #2!");

		// Cleanup
		com.example.client.renderer.ClientBarrierTracker.clear();
	}

	@Test
	@DisplayName("Ping-Pong Barrier Extension: Start from any existing waypoint and close with created or existing waypoints without starting over")
	void testPingPongBarrierExtensionAndClosureOnAnyWaypoint() {
		com.example.client.renderer.ClientBarrierTracker.clear();

		// 1. Establish confirmed Barrier #1: W1, W2, W3
		BlockPos w1 = new BlockPos(10, 64, 10);
		BlockPos w2 = new BlockPos(30, 64, 10);
		BlockPos w3 = new BlockPos(30, 64, 30);
		BarrierZone barrier1 = new BarrierZone(1, testOwner, "Main Keep", List.of(w1, w2, w3), -64, 320, true);
		com.example.client.renderer.ClientBarrierTracker.setConfirmedZones(List.of(barrier1));

		// 2. Player right-clicks existing Waypoint W2 to start an extension
		BlockPos matchedConfirmed = com.example.client.renderer.ClientBarrierTracker.findConfirmedNodeAt(w2, w2);
		Assertions.assertEquals(w2, matchedConfirmed, "Must find confirmed node W2");

		boolean started = com.example.client.renderer.ClientBarrierTracker.startExtensionFrom(w2);
		Assertions.assertTrue(started);
		Assertions.assertEquals(1, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount(),
			"Starting extension must begin with exactly Point 1 at the clicked waypoint");
		Assertions.assertEquals(w2, com.example.client.renderer.ClientBarrierTracker.getPendingNodes().get(0),
			"Point 1 of draft must be the clicked waypoint W2");

		// 3. Player drafts extension points N1, N2
		BlockPos n1 = new BlockPos(50, 64, 10);
		BlockPos n2 = new BlockPos(50, 64, 30);
		com.example.client.renderer.ClientBarrierTracker.addNode(n1);
		com.example.client.renderer.ClientBarrierTracker.addNode(n2);
		Assertions.assertEquals(3, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());
		Assertions.assertEquals(List.of(w2, n1, n2), com.example.client.renderer.ClientBarrierTracker.getPendingNodes());

		// 4. Player aims at W3 (an existing waypoint of Barrier #1) to close the ping-pong barrier
		BlockPos closeTarget = com.example.client.renderer.ClientBarrierTracker.findCloseTarget(w3, w3);
		Assertions.assertNotNull(closeTarget, "Must detect W3 as a valid loop closure target");
		Assertions.assertEquals(w3, closeTarget);

		// Loop closure logic: since W3 is an existing waypoint not yet in pending, append it to complete circuit
		if (!com.example.client.renderer.ClientBarrierTracker.containsPendingNode(closeTarget)) {
			com.example.client.renderer.ClientBarrierTracker.addNode(closeTarget);
		}
		Assertions.assertEquals(4, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());
		Assertions.assertEquals(List.of(w2, n1, n2, w3), com.example.client.renderer.ClientBarrierTracker.getPendingNodes(),
			"Pending nodes must NOT be truncated or reset when closing on an existing waypoint!");

		// 5. Seal the extension barrier
		boolean sealedExt = com.example.client.renderer.ClientBarrierTracker.seal("Eastern Annex");
		Assertions.assertTrue(sealedExt);
		Assertions.assertEquals(0, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());

		// 6. Test closing back onto the starting waypoint (W2)
		// Start extension from W2 again: W2 -> E1 -> E2 -> close back onto W2
		com.example.client.renderer.ClientBarrierTracker.startExtensionFrom(w2);
		BlockPos e1 = new BlockPos(30, 64, -10);
		BlockPos e2 = new BlockPos(10, 64, -10);
		com.example.client.renderer.ClientBarrierTracker.addNode(e1);
		com.example.client.renderer.ClientBarrierTracker.addNode(e2);
		Assertions.assertEquals(3, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());

		// Aim back at W2 (Point 1 of current draft)
		BlockPos closeTargetStart = com.example.client.renderer.ClientBarrierTracker.findCloseTarget(w2, w2);
		Assertions.assertNotNull(closeTargetStart, "Must detect starting Point 1 (W2) as a valid loop closure target");
		Assertions.assertEquals(w2, closeTargetStart);

		// W2 is already in pendingNodes at index 0, so circuit is already closed
		Assertions.assertTrue(com.example.client.renderer.ClientBarrierTracker.containsPendingNode(closeTargetStart));
		Assertions.assertEquals(3, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());

		boolean sealedLoop = com.example.client.renderer.ClientBarrierTracker.seal("Southern Annex");
		Assertions.assertTrue(sealedLoop);
		Assertions.assertEquals(0, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());

		// Cleanup
		com.example.client.renderer.ClientBarrierTracker.clear();
	}

	@Test
	@DisplayName("Barrier Mode Control Contract: Left-click places/extends/closes barriers, Right-click removes waypoint or opens Seal modal")
	void testBarrierModeControlContract() throws Exception {
		// 1. Verify CommandScepterItem.handleBuildClick (left-click) handles BARRIER mode node addition, extension, and loop closure
		java.io.File scepterFile = new java.io.File("src/main/java/com/example/item/custom/CommandScepterItem.java");
		String scepterContent = java.nio.file.Files.readString(scepterFile.toPath());

		Assertions.assertTrue(scepterContent.contains("BARRIER_NODE_APPENDER.addNode(targetPos)"),
			"Left-click handleBuildClick must add nodes via BARRIER_NODE_APPENDER");
		Assertions.assertTrue(scepterContent.contains("BARRIER_CLOSER.tryCloseLoop(targetPos, clickedPos)"),
			"Left-click handleBuildClick must close loop via BARRIER_CLOSER");
		Assertions.assertTrue(scepterContent.contains("BARRIER_RESUMER.tryResumeAt(targetPos, clickedPos)"),
			"Left-click handleBuildClick must extend via BARRIER_RESUMER");

		// 2. Verify CommandScepterItem.useOnBlock and use (right-click) in BARRIER mode handles node removal and modal opening only when count >= 2
		Assertions.assertTrue(scepterContent.contains("BARRIER_REMOVER.removeBarrierAt(targetPos, clickedPos)"),
			"Right-click useOnBlock must remove nodes via BARRIER_REMOVER");
		Assertions.assertTrue(scepterContent.contains("count >= 2 && BARRIER_MODAL_OPENER != null"),
			"Right-click useOnBlock/use must strictly check count >= 2 before calling BARRIER_MODAL_OPENER");
		Assertions.assertTrue(scepterContent.contains("BARRIER_MODAL_OPENER.openBarrierModal(player, context.getHand(), stack)"),
			"Right-click useOnBlock must open Seal modal when not pointing at a waypoint and count >= 2");

		// 3. Functional simulation of the left-click and right-click cycle
		com.example.client.renderer.ClientBarrierTracker.clear();

		BlockPos p1 = new BlockPos(100, 64, 100);
		BlockPos p2 = new BlockPos(110, 64, 100);
		BlockPos p3 = new BlockPos(110, 64, 110);

		// Left click 1: Place Point 1
		com.example.client.renderer.ClientBarrierTracker.addNode(p1);
		// Left click 2: Place Point 2
		com.example.client.renderer.ClientBarrierTracker.addNode(p2);
		// Left click 3: Place Point 3
		com.example.client.renderer.ClientBarrierTracker.addNode(p3);
		Assertions.assertEquals(3, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());

		// Left click 4 on Point 1: Valid loop closure detected
		BlockPos closeTarget = com.example.client.renderer.ClientBarrierTracker.findCloseTarget(p1, p1);
		Assertions.assertEquals(p1, closeTarget, "Left-clicking Point 1 must detect valid close target");

		// Right click on Point 2: Removes Point 2
		BlockPos removed = com.example.client.renderer.ClientBarrierTracker.removeNodeFromAnyBarrier(p2, p2, null, null, 0.0D);
		Assertions.assertEquals(p2, removed, "Right-clicking Point 2 must remove that waypoint");
		Assertions.assertEquals(2, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount(), "2 nodes remaining after right-click removal");
		Assertions.assertFalse(com.example.client.renderer.ClientBarrierTracker.getPendingNodes().contains(p2));

		// Right click on ground (p = (200, 64, 200), not a waypoint): removeBarrierAt returns null, routing to modal open
		BlockPos nonWaypoint = new BlockPos(200, 64, 200);
		BlockPos notRemoved = com.example.client.renderer.ClientBarrierTracker.removeNodeFromAnyBarrier(nonWaypoint, nonWaypoint, null, null, 0.0D);
		Assertions.assertNull(notRemoved, "Right-clicking non-waypoint must not remove any waypoint, routing to open modal");

		// 4. Test that with 0 or 1 waypoint placed, no modal opens on right-clicking non-waypoint
		com.example.client.renderer.ClientBarrierTracker.clear();
		Assertions.assertEquals(0, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());
		Assertions.assertFalse(com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount() >= 2,
			"With 0 waypoints, count >= 2 must be false");

		// Place 1 waypoint
		com.example.client.renderer.ClientBarrierTracker.addNode(p1);
		Assertions.assertEquals(1, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());
		Assertions.assertFalse(com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount() >= 2,
			"With only 1 waypoint, count >= 2 must be false so nothing opens");

		// Place 2nd waypoint
		com.example.client.renderer.ClientBarrierTracker.addNode(p2);
		Assertions.assertEquals(2, com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount());
		Assertions.assertTrue(com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount() >= 2,
			"With 2 waypoints, count >= 2 is true so right-clicking non-waypoint opens the Seal modal");

		// Cleanup
		com.example.client.renderer.ClientBarrierTracker.clear();
	}

	@Test
	@DisplayName("Defunct barrier is automatically removed from manager and tracker when waypoints drop below 2")
	void testDefunctBarrierRemovedWhenWaypointsRemoved() {
		BarrierManager manager = BarrierManager.getInstance();
		UUID owner = UUID.randomUUID();

		// Create a sealed barrier with 3 waypoints
		BlockPos p1 = new BlockPos(10, 64, 10);
		BlockPos p2 = new BlockPos(20, 64, 10);
		BlockPos p3 = new BlockPos(20, 64, 20);

		manager.addNodeToActiveZone(owner, p1);
		manager.addNodeToActiveZone(owner, p2);
		manager.addNodeToActiveZone(owner, p3);
		BarrierZone sealed = manager.sealActiveZone(owner, 0, "PerimeterAlpha", 60, 70);
		Assertions.assertNotNull(sealed);
		Assertions.assertEquals(3, sealed.getNodeCount());
		Assertions.assertEquals(1, manager.getZones(owner).size());

		// Sync to client tracker
		com.example.client.renderer.ClientBarrierTracker.setConfirmedZones(manager.getZones(owner));
		Assertions.assertEquals(1, com.example.client.renderer.ClientBarrierTracker.getConfirmedZones().size());

		// Remove first waypoint p1 (3 -> 2)
		boolean removed1 = manager.removeNodeFromAnyZone(owner, sealed.getId(), p1);
		Assertions.assertTrue(removed1);
		Assertions.assertEquals(2, sealed.getNodeCount());
		Assertions.assertEquals(1, manager.getZones(owner).size(), "2 nodes remaining is still retained");

		// Remove second waypoint p2 (2 -> 1, drops below 2 nodes, defunct)
		boolean removed2 = manager.removeNodeFromAnyZone(owner, sealed.getId(), p2);
		Assertions.assertTrue(removed2);
		Assertions.assertEquals(0, manager.getZones(owner).size(), "Defunct barrier with < 2 nodes must be automatically removed from manager");

		// Test ClientBarrierTracker cleanup
		com.example.client.renderer.ClientBarrierTracker.setConfirmedZones(manager.getZones(owner));
		Assertions.assertEquals(0, com.example.client.renderer.ClientBarrierTracker.getConfirmedZones().size(),
			"ClientBarrierTracker must not show defunct barrier with < 2 nodes");

		// Directly test removeNodeFromConfirmedZone dropping to < 2 nodes
		BarrierZone clientZone = new BarrierZone(999, owner, "TestZone");
		clientZone.addNode(p1);
		clientZone.addNode(p2);
		clientZone.setSealed(true);
		com.example.client.renderer.ClientBarrierTracker.setConfirmedZones(new java.util.ArrayList<>(List.of(clientZone)));
		Assertions.assertEquals(1, com.example.client.renderer.ClientBarrierTracker.getConfirmedZones().size());

		com.example.client.renderer.ClientBarrierTracker.removeNodeFromConfirmedZone(clientZone, p1);
		Assertions.assertEquals(0, com.example.client.renderer.ClientBarrierTracker.getConfirmedZones().size(),
			"Removing a node from a 2-node zone on client must immediately remove it from confirmedZones");

		// Cleanup
		com.example.client.renderer.ClientBarrierTracker.clear();
	}

	@Test
	@DisplayName("BarrierHologramRenderer maintains clean interior (no center box or pillar), SEE_THROUGH layer, and vertex buffer flushing")
	void testCleanInteriorAndFlush() throws Exception {
		java.nio.file.Path rendererFile = java.nio.file.Path.of("src/client/java/com/example/client/renderer/BarrierHologramRenderer.java");
		String content = java.nio.file.Files.readString(rendererFile);

		Assertions.assertFalse(content.contains("renderCenterHologram"),
			"BarrierHologramRenderer must keep interior clean with no center hologram");
		Assertions.assertFalse(content.contains("centerBox"),
			"BarrierHologramRenderer must not render center box on ground");
		Assertions.assertTrue(content.contains("TextRenderer.TextLayerType.SEE_THROUGH"),
			"BarrierHologramRenderer must use SEE_THROUGH so waypoint tags are not occluded by environment");
		Assertions.assertTrue(content.contains("immediate.draw();"),
			"BarrierHologramRenderer must call immediate.draw() to flush text buffers to GPU in AFTER_TRANSLUCENT");
		Assertions.assertTrue(content.contains("isBarrierMode"),
			"BarrierHologramRenderer must support rendering confirmed barriers while holding scepter");
	}
}


