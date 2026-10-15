package com.example.client.renderer;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating ClientBuildCaptureTracker state management,
 * unified height/depth adjustments, underground transitions, and orientation controls.
 */
public class ClientBuildCaptureTrackerTest {

	@BeforeEach
	void setUp() {
		ClientBuildCaptureTracker.clear();
	}

	@Test
	@DisplayName("Tracker initializes empty with incomplete selection")
	void testInitialState() {
		Assertions.assertNull(ClientBuildCaptureTracker.getPos1());
		Assertions.assertNull(ClientBuildCaptureTracker.getPos2());
		Assertions.assertFalse(ClientBuildCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientBuildCaptureTracker.hasPos2());
		Assertions.assertFalse(ClientBuildCaptureTracker.hasCompleteSelection());
		Assertions.assertEquals(0, ClientBuildCaptureTracker.getSizeX());
		Assertions.assertEquals(0, ClientBuildCaptureTracker.getSizeY());
		Assertions.assertEquals(0, ClientBuildCaptureTracker.getSizeZ());
		Assertions.assertEquals(0L, ClientBuildCaptureTracker.getVolume());
		Assertions.assertNull(ClientBuildCaptureTracker.getBlockBox());
		Assertions.assertNull(ClientBuildCaptureTracker.getBox());
		Assertions.assertFalse(ClientBuildCaptureTracker.isUnderground());
		Assertions.assertEquals(0, ClientBuildCaptureTracker.getUndergroundDepth());
		Assertions.assertEquals("Incomplete Selection", ClientBuildCaptureTracker.getDimensionString());
	}

	@Test
	@DisplayName("Unified height adjustment expands above ground and transitions underground when decreasing past 1")
	void testUnifiedHeightAndUndergroundAdjustment() {
		BlockPos p1 = new BlockPos(10, 64, 10);
		BlockPos p2 = new BlockPos(20, 64, 20); // Height = 1 at baseline 64

		ClientBuildCaptureTracker.setPos1(p1);
		ClientBuildCaptureTracker.setPos2(p2);

		Assertions.assertEquals(1, ClientBuildCaptureTracker.getSizeY());
		Assertions.assertFalse(ClientBuildCaptureTracker.isUnderground());
		Assertions.assertEquals(0, ClientBuildCaptureTracker.getUndergroundDepth());

		// Expand upward by 5 -> height = 6
		int hAbove = ClientBuildCaptureTracker.adjustHeight(5);
		Assertions.assertEquals(6, hAbove);
		Assertions.assertEquals(6, ClientBuildCaptureTracker.getSizeY());
		Assertions.assertFalse(ClientBuildCaptureTracker.isUnderground());
		Assertions.assertEquals(0, ClientBuildCaptureTracker.getUndergroundDepth());

		// Shrink down by 5 -> back to baseline height 1
		int hBase = ClientBuildCaptureTracker.adjustHeight(-5);
		Assertions.assertEquals(1, hBase);
		Assertions.assertEquals(1, ClientBuildCaptureTracker.getSizeY());
		Assertions.assertFalse(ClientBuildCaptureTracker.isUnderground());

		// Decrease past 1 by 1 block -> expands downward into ground (depth 1, height 2)
		int hUnder1 = ClientBuildCaptureTracker.adjustHeight(-1);
		Assertions.assertEquals(2, hUnder1);
		Assertions.assertEquals(2, ClientBuildCaptureTracker.getSizeY());
		Assertions.assertTrue(ClientBuildCaptureTracker.isUnderground());
		Assertions.assertEquals(1, ClientBuildCaptureTracker.getUndergroundDepth());

		// Decrease deeper underground by 4 blocks -> depth = 5, height = 6
		int hUnder5 = ClientBuildCaptureTracker.adjustHeight(-4);
		Assertions.assertEquals(6, hUnder5);
		Assertions.assertEquals(6, ClientBuildCaptureTracker.getSizeY());
		Assertions.assertTrue(ClientBuildCaptureTracker.isUnderground());
		Assertions.assertEquals(5, ClientBuildCaptureTracker.getUndergroundDepth());

		// Pull back up out of ground by 5 blocks -> reaches baseline height 1
		int hPullBase = ClientBuildCaptureTracker.adjustHeight(5);
		Assertions.assertEquals(1, hPullBase);
		Assertions.assertEquals(1, ClientBuildCaptureTracker.getSizeY());
		Assertions.assertFalse(ClientBuildCaptureTracker.isUnderground());
		Assertions.assertEquals(0, ClientBuildCaptureTracker.getUndergroundDepth());

		// Pull up further by 3 blocks -> expands above ground (height = 4)
		int hAboveCross = ClientBuildCaptureTracker.adjustHeight(3);
		Assertions.assertEquals(4, hAboveCross);
		Assertions.assertEquals(4, ClientBuildCaptureTracker.getSizeY());
		Assertions.assertFalse(ClientBuildCaptureTracker.isUnderground());
	}

	@Test
	@DisplayName("Underground depth clamps to MAX_HEIGHT (96) and transitions cleanly")
	void testUndergroundClampingAndCrossover() {
		BlockPos p1 = new BlockPos(0, 64, 0);
		BlockPos p2 = new BlockPos(10, 64, 10);
		ClientBuildCaptureTracker.setCorners(p1, p2);

		// Dig deeply underground by 500 blocks -> clamped to MAX_HEIGHT (96)
		int deepClamped = ClientBuildCaptureTracker.adjustHeight(-500);
		Assertions.assertEquals(ClientBuildCaptureTracker.MAX_HEIGHT, deepClamped);
		Assertions.assertTrue(ClientBuildCaptureTracker.isUnderground());
		Assertions.assertEquals(95, ClientBuildCaptureTracker.getUndergroundDepth());

		// Pull back up by 100 -> pulls up 95 to surface and expands 5 above ground -> height 6
		int pulledUp = ClientBuildCaptureTracker.adjustHeight(100);
		Assertions.assertEquals(6, pulledUp);
		Assertions.assertFalse(ClientBuildCaptureTracker.isUnderground());
		Assertions.assertEquals(0, ClientBuildCaptureTracker.getUndergroundDepth());

		// Explicit setHeight clamps to 1 minimum
		int clampedMin = ClientBuildCaptureTracker.setHeight(0);
		Assertions.assertEquals(1, clampedMin);
		Assertions.assertEquals(1, ClientBuildCaptureTracker.getSizeY());
	}

	@Test
	@DisplayName("Orientation and half cycle functionality operates correctly")
	void testOrientationAndHalfSettings() {
		ClientBuildCaptureTracker.setFacing(Direction.NORTH);
		Assertions.assertEquals(Direction.NORTH, ClientBuildCaptureTracker.getFacing());

		ClientBuildCaptureTracker.cycleFacing();
		Assertions.assertEquals(Direction.EAST, ClientBuildCaptureTracker.getFacing());

		Assertions.assertFalse(ClientBuildCaptureTracker.isInvertedHalf());
		ClientBuildCaptureTracker.toggleInvertedHalf();
		Assertions.assertTrue(ClientBuildCaptureTracker.isInvertedHalf());
	}
}
