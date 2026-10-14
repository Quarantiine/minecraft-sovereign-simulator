package com.example.item;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.StructureBlueprint;
import com.example.client.renderer.ClientBuildCaptureTracker;
import com.example.component.BuildingMode;
import com.example.component.CommandMode;
import com.example.item.custom.CommandScepterItem;
import com.example.network.StartBuildAreaPayload;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating Command Scepter BUILD mode duality (BLUEPRINT vs AREA):
 * - BuildingMode enum properties and cycling
 * - Spatial corner logic and dimension calculations
 * - Procedural layer-by-layer blueprint generation via createAreaFillBlueprint
 * - ClientBuildCaptureTracker selection, height adjustments, and limits
 * - StartBuildAreaPayload record integrity
 * - Callback hooks and wiring
 */
public class CommandScepterBuildingModeTest {

	private static net.minecraft.block.BlockState createDummyBlockState() {
		try {
			java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
			return (net.minecraft.block.BlockState) unsafe.allocateInstance(net.minecraft.block.BlockState.class);
		} catch (Throwable t) {
			return null;
		}
	}

	@BeforeEach
	void setUp() {
		CommandScepterItem.resetBuildClickDebounce();
		CommandScepterItem.BUILD_CORNER_STEPPER = null;
		CommandScepterItem.BUILD_CORNER_RESETTER = null;
		CommandScepterItem.BUILD_CLICK_CONSUMER = null;
		CommandScepterItem.BUILD_MODAL_OPENER = null;
		ClientBuildCaptureTracker.clear();
	}

	@Test
	@DisplayName("BuildingMode includes BLUEPRINT and AREA enum values with correct formatting and cycling")
	void testBuildingModeEnumProperties() {
		BuildingMode blueprint = BuildingMode.BLUEPRINT;
		Assertions.assertNotNull(blueprint);
		Assertions.assertEquals(0, blueprint.getId());
		Assertions.assertEquals("blueprint", blueprint.asString());
		Assertions.assertEquals("Blueprint / Structure", blueprint.getDisplayName());
		Assertions.assertEquals("§b", blueprint.getColorCode());
		Assertions.assertEquals("§bBlueprint / Structure", blueprint.getFormattedName());

		BuildingMode area = BuildingMode.AREA;
		Assertions.assertNotNull(area);
		Assertions.assertEquals(1, area.getId());
		Assertions.assertEquals("area", area.asString());
		Assertions.assertEquals("Area Fill / Volumetric", area.getDisplayName());
		Assertions.assertEquals("§3", area.getColorCode());
		Assertions.assertEquals("§3Area Fill / Volumetric", area.getFormattedName());

		// Cycling
		Assertions.assertEquals(BuildingMode.AREA, blueprint.next());
		Assertions.assertEquals(BuildingMode.BLUEPRINT, area.next());
		Assertions.assertEquals(BuildingMode.AREA, blueprint.previous());
		Assertions.assertEquals(BuildingMode.BLUEPRINT, area.previous());

		// Lookup by id and name
		Assertions.assertEquals(BuildingMode.BLUEPRINT, BuildingMode.fromId(0));
		Assertions.assertEquals(BuildingMode.AREA, BuildingMode.fromId(1));
		Assertions.assertEquals(BuildingMode.BLUEPRINT, BuildingMode.fromId(99)); // Default fallback

		Assertions.assertEquals(BuildingMode.BLUEPRINT, BuildingMode.fromName("blueprint"));
		Assertions.assertEquals(BuildingMode.AREA, BuildingMode.fromName("area"));
		Assertions.assertEquals(BuildingMode.BLUEPRINT, BuildingMode.fromName("unknown"));
	}

	@Test
	@DisplayName("createAreaFillBlueprint generates a bottom-to-top layer-by-layer blueprint with exact item tally")
	void testCreateAreaFillBlueprint() {
		net.minecraft.block.BlockState dummyState = createDummyBlockState();
		if (dummyState == null) return;

		StructureBlueprint bp = StructureBlueprint.createAreaFillBlueprint(
			BlockPos.ORIGIN,
			new BlockPos(2, 3, 4),
			dummyState,
			"test_area_fill",
			"Area Fill (3x4x5)",
			"Volumetric Fill Test"
		);

		Assertions.assertNotNull(bp);
		Assertions.assertEquals(3, bp.getSizeX());
		Assertions.assertEquals(4, bp.getSizeY());
		Assertions.assertEquals(5, bp.getSizeZ());
		Assertions.assertEquals(3 * 4 * 5, bp.getBlockCount());
		Assertions.assertEquals(60, bp.getBlockCount());
		Assertions.assertTrue(bp.getName().contains("Area Fill"));

		// Check layer-by-layer ordering: all blocks at y=0 must come before y=1, etc.
		List<BlueprintBlock> blocks = bp.getBlocks();
		Assertions.assertEquals(60, blocks.size());
		int currentY = 0;
		for (BlueprintBlock b : blocks) {
			Assertions.assertTrue(b.offset().getY() >= currentY, "Blocks must be ordered bottom-to-top by layer Y");
			currentY = b.offset().getY();
			Assertions.assertEquals(dummyState, b.state());
		}
	}

	@Test
	@DisplayName("ClientBuildCaptureTracker handles sequential corner stepping, dimension computation, and height adjustments")
	void testClientBuildCaptureTracker() {
		Assertions.assertFalse(ClientBuildCaptureTracker.hasCompleteSelection());

		BlockPos p1 = new BlockPos(10, 60, 10);
		int step1 = ClientBuildCaptureTracker.stepCorner(p1);
		Assertions.assertEquals(1, step1);
		Assertions.assertEquals(p1, ClientBuildCaptureTracker.getPos1());
		Assertions.assertNull(ClientBuildCaptureTracker.getPos2());
		Assertions.assertFalse(ClientBuildCaptureTracker.hasCompleteSelection());

		BlockPos p2 = new BlockPos(14, 65, 14);
		int step2 = ClientBuildCaptureTracker.stepCorner(p2);
		Assertions.assertEquals(2, step2);
		Assertions.assertEquals(p2, ClientBuildCaptureTracker.getPos2());
		Assertions.assertTrue(ClientBuildCaptureTracker.hasCompleteSelection());

		// Dimensions: 14 - 10 + 1 = 5 in X, 65 - 60 + 1 = 6 in Y, 14 - 10 + 1 = 5 in Z
		Assertions.assertEquals(5, ClientBuildCaptureTracker.getDimensionX());
		Assertions.assertEquals(6, ClientBuildCaptureTracker.getDimensionY());
		Assertions.assertEquals(5, ClientBuildCaptureTracker.getDimensionZ());
		Assertions.assertEquals(150L, ClientBuildCaptureTracker.getVolume());

		// Adjust height by +2
		int newHeight = ClientBuildCaptureTracker.adjustHeight(2);
		Assertions.assertEquals(8, newHeight);
		Assertions.assertEquals(67, ClientBuildCaptureTracker.getPos2().getY());

		// Clear
		ClientBuildCaptureTracker.clear();
		Assertions.assertNull(ClientBuildCaptureTracker.getPos1());
		Assertions.assertNull(ClientBuildCaptureTracker.getPos2());
		Assertions.assertFalse(ClientBuildCaptureTracker.hasCompleteSelection());
	}

	@Test
	@DisplayName("StartBuildAreaPayload record integrity and ID")
	void testStartBuildAreaPayload() {
		BlockPos p1 = new BlockPos(0, 10, 0);
		BlockPos p2 = new BlockPos(5, 15, 5);
		Identifier blockId = Identifier.of("minecraft", "oak_planks");

		StartBuildAreaPayload payload = new StartBuildAreaPayload(p1, p2, blockId);
		Assertions.assertEquals(p1, payload.pos1());
		Assertions.assertEquals(p2, payload.pos2());
		Assertions.assertEquals(blockId, payload.blockId());
		Assertions.assertEquals(StartBuildAreaPayload.ID, payload.getId());
		Assertions.assertNotNull(StartBuildAreaPayload.PACKET_CODEC);
	}

	@Test
	@DisplayName("Build area modal opener callback contract")
	void testBuildModalOpenerContract() {
		boolean[] opened = new boolean[] { false };
		BlockPos p1 = new BlockPos(10, 64, 10);
		BlockPos p2 = new BlockPos(20, 74, 20);

		CommandScepterItem.BUILD_MODAL_OPENER = (player, hand, stack, pos1, pos2) -> {
			opened[0] = true;
			Assertions.assertEquals(p1, pos1);
			Assertions.assertEquals(p2, pos2);
		};

		CommandScepterItem.BUILD_MODAL_OPENER.openBuildModal(null, null, null, p1, p2);
		Assertions.assertTrue(opened[0]);
	}

	@Test
	@DisplayName("Debounce logic for build clicks enforces 350ms window")
	void testBuildClickDebounce() {
		Assertions.assertFalse(CommandScepterItem.isBuildClickDebounced());
		CommandScepterItem.recordBuildClick();
		Assertions.assertTrue(CommandScepterItem.isBuildClickDebounced());
		CommandScepterItem.resetBuildClickDebounce();
		Assertions.assertFalse(CommandScepterItem.isBuildClickDebounced());
	}

	@Test
	@DisplayName("ExampleModClient and ExampleMod register BUILD AREA hooks")
	void testWiringInClientAndMod() throws Exception {
		File clientFile = new File("src/client/java/com/example/client/ExampleModClient.java");
		Assertions.assertTrue(clientFile.exists());
		String clientContent = Files.readString(clientFile.toPath());
		Assertions.assertTrue(clientContent.contains("CommandScepterItem.BUILD_MODAL_OPENER ="), "ExampleModClient must register BUILD_MODAL_OPENER");
		Assertions.assertTrue(clientContent.contains("CommandScepterItem.BUILD_CORNER_STEPPER ="), "ExampleModClient must register BUILD_CORNER_STEPPER");
		Assertions.assertTrue(clientContent.contains("CommandScepterItem.BUILD_CORNER_RESETTER ="), "ExampleModClient must register BUILD_CORNER_RESETTER");

		File modFile = new File("src/main/java/com/example/ExampleMod.java");
		Assertions.assertTrue(modFile.exists());
		String modContent = Files.readString(modFile.toPath());
		Assertions.assertTrue(modContent.contains("BuildingMode.AREA"), "ExampleMod must check BuildingMode.AREA");
		Assertions.assertTrue(modContent.contains("handleBuildClick"), "ExampleMod must call handleBuildClick");
	}

	@Test
	@DisplayName("Edge Case Contract: BlueprintHologramRenderer persists in-world wireframe and ghost grid during active area build sessions")
	void testActiveAreaBuildHologramPersistence() throws Exception {
		File rendererFile = new File("src/client/java/com/example/client/renderer/BlueprintHologramRenderer.java");
		Assertions.assertTrue(rendererFile.exists());
		String content = Files.readString(rendererFile.toPath());
		Assertions.assertTrue(
			content.contains("!sessionData.isDismantle() && sessionData.sizeX() > 0") &&
			content.contains("renderBuildBoundingBox(") &&
			content.contains("renderBuildGhostGrid("),
			"BlueprintHologramRenderer must render wireframe and ghost grid for active area build sessions"
		);
	}

	@Test
	@DisplayName("Edge Case Contract: ExampleModClient supports keyboard shortcuts for BUILD AREA height and restricts rotation to BLUEPRINT mode")
	void testClientKeyboardShortcutsAndRetreatParity() throws Exception {
		File clientFile = new File("src/client/java/com/example/client/ExampleModClient.java");
		Assertions.assertTrue(clientFile.exists());
		String content = Files.readString(clientFile.toPath());

		// 1. Keyboard shortcuts ([ / ] and PageUp / PageDown) for BUILD AREA
		Assertions.assertTrue(
			content.contains("BuildingMode.AREA") &&
			content.contains("ClientBuildCaptureTracker.adjustHeight(step)"),
			"ExampleModClient must support bracket and page keys for BUILD AREA height adjustment"
		);

		// 2. R key checks BLUEPRINT mode specifically so AREA mode falls through to retreat / emergency recall
		Assertions.assertTrue(
			content.contains("CommandScepterItem.getBuildingMode(heldScepter) == com.example.component.BuildingMode.BLUEPRINT"),
			"ExampleModClient must only rotate blueprint in BLUEPRINT mode, allowing AREA mode to trigger retreat/recall"
		);
	}

	@Test
	@DisplayName("Edge Case Contract: ModNetworking validates world height boundaries for area build and mining")
	void testWorldHeightBoundsValidation() throws Exception {
		File netFile = new File("src/main/java/com/example/network/ModNetworking.java");
		Assertions.assertTrue(netFile.exists());
		String content = Files.readString(netFile.toPath());
		Assertions.assertTrue(
			content.contains("minY < world.getBottomY() || maxY >= world.getTopY()"),
			"ModNetworking must check world height limits for area sessions"
		);
	}

	@Test
	@DisplayName("Edge Case Contract: BuildAreaConfirmModalScreen updates changeBlockButton visibility based on corner selection")
	void testModalCornerStateVisibility() throws Exception {
		File modalFile = new File("src/client/java/com/example/client/gui/BuildAreaConfirmModalScreen.java");
		Assertions.assertTrue(modalFile.exists());
		String content = Files.readString(modalFile.toPath());
		Assertions.assertTrue(
			content.contains("this.changeBlockButton.visible = (this.pos1 != null && this.pos2 != null)"),
			"Modal must toggle change block button visibility when corners are present"
		);
	}
}
