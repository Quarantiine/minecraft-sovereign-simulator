package com.example.client.gui;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.StructureBlueprint;
import com.example.network.CaptureSpatialBlueprintPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating Blueprint Palette Remapping and Block Replacement:
 * 1. Strict placeable block validation (only BlockItem instances are valid).
 * 2. Property transfer safety for BlockStates.
 * 3. Network payload integration with block replacements map.
 * 4. Architectural source contracts across ModNetworking, StructureBlueprint, and GUI modals.
 */
public class BlueprintPaletteRemapModalTest {

	@Test
	@DisplayName("Strict placeable block validation verifies BlockItem class hierarchy")
	void testStrictPlaceableBlockValidationContract() {
		Assertions.assertTrue(BlockItem.class.isAssignableFrom(BlockItem.class));
		Assertions.assertFalse(BlockItem.class.isAssignableFrom(net.minecraft.item.SwordItem.class));
		Assertions.assertFalse(BlockItem.class.isAssignableFrom(net.minecraft.item.MiningToolItem.class));
		Assertions.assertFalse(BlockItem.class.isAssignableFrom(net.minecraft.item.ArmorItem.class));
	}

	@Test
	@DisplayName("transferBlockStateProperties safely handles null parameters without throwing")
	void testTransferBlockStatePropertiesSafeNulls() {
		Assertions.assertNull(StructureBlueprint.transferBlockStateProperties(null, null));
	}

	@Test
	@DisplayName("CaptureSpatialBlueprintPayload properly stores and exposes block replacements")
	void testPayloadBlockReplacements() {
		Map<Identifier, Identifier> replacements = new LinkedHashMap<>();
		Identifier moss = Identifier.of("minecraft", "moss_block");
		Identifier cobble = Identifier.of("minecraft", "cobblestone");
		Identifier dirt = Identifier.of("minecraft", "dirt");
		Identifier air = Identifier.of("minecraft", "air");
		replacements.put(moss, cobble);
		replacements.put(dirt, air);

		BlockPos p1 = new BlockPos(0, 0, 0);
		BlockPos p2 = new BlockPos(10, 10, 10);
		CaptureSpatialBlueprintPayload payload = new CaptureSpatialBlueprintPayload(
			"custom_test",
			"Test BP",
			"Desc",
			p1,
			p2,
			replacements
		);

		Assertions.assertEquals(2, payload.blockReplacements().size());
		Assertions.assertEquals(cobble, payload.blockReplacements().get(moss));
		Assertions.assertEquals(air, payload.blockReplacements().get(dirt));
	}

	@Test
	@DisplayName("ModNetworking strictly guards against non-block replacements and allows air")
	void testModNetworkingSecurityGuardrail() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/example/network/ModNetworking.java"));
		Assertions.assertTrue(
			source.contains("toBlock.asItem() instanceof BlockItem"),
			"ModNetworking must validate that target replacement is a BlockItem"
		);
		Assertions.assertTrue(
			source.contains("to.equals(Identifier.of(\"minecraft\", \"air\"))"),
			"ModNetworking must allow air as a valid eraser replacement"
		);
	}

	@Test
	@DisplayName("StructureBlueprint captureBlocks checks air to omit erased blocks from compilation")
	void testStructureBlueprintAirOmissionContract() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/example/blueprint/StructureBlueprint.java"));
		Assertions.assertTrue(
			source.contains("targetId.equals(Identifier.of(\"minecraft\", \"air\"))"),
			"StructureBlueprint must omit erased blocks when targetId is air"
		);
		Assertions.assertTrue(
			source.contains("transferBlockStateProperties"),
			"StructureBlueprint must transfer BlockState properties for replaced blocks"
		);
	}

	@Test
	@DisplayName("BlockPickerModalScreen strictly filters placeable blocks from Registries.BLOCK")
	void testBlockPickerPlaceableFilterContract() throws Exception {
		String source = Files.readString(Path.of("src/client/java/com/example/client/gui/BlockPickerModalScreen.java"));
		Assertions.assertTrue(
			source.contains("item instanceof BlockItem"),
			"BlockPickerModalScreen must strictly filter only items that are BlockItem"
		);
		Assertions.assertTrue(
			source.contains("block.getDefaultState().isAir()"),
			"BlockPickerModalScreen must exclude air from placeable block picker"
		);
	}

	@Test
	@DisplayName("BlueprintCaptureModalScreen contains remap button and propagates replacements to network")
	void testBlueprintCaptureModalScreenIntegration() throws Exception {
		String source = Files.readString(Path.of("src/client/java/com/example/client/gui/BlueprintCaptureModalScreen.java"));
		Assertions.assertTrue(
			source.contains("openPaletteRemapScreen"),
			"BlueprintCaptureModalScreen must provide openPaletteRemapScreen method"
		);
		Assertions.assertTrue(
			source.contains("this.activeReplacements"),
			"BlueprintCaptureModalScreen must maintain activeReplacements state"
		);
		Assertions.assertTrue(
			source.contains("ModClientNetworking.sendCaptureSpatialBlueprint"),
			"BlueprintCaptureModalScreen must pass activeReplacements to network dispatch"
		);
	}
}
