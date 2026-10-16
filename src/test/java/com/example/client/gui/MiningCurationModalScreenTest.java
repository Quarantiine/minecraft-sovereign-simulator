package com.example.client.gui;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating MiningCurationModalScreen:
 * - Window geometry and modal pause behavior
 * - Real-world block entry ingestion and counting
 * - Live search query filtering (name, namespace, path)
 * - Toggling selection states per block
 * - Bulk [✔ All] and [✖ Clear] operations
 * - Fail-safe apply behavior (empty selection reverts to all detected blocks)
 * - Callback invocation and close state transitions
 */
public class MiningCurationModalScreenTest {

	@Test
	@DisplayName("Modal dimensions, pause state, and close transitions")
	void testModalBasics() {
		Assertions.assertEquals(360, MiningCurationModalScreen.WINDOW_WIDTH);
		Assertions.assertEquals(240, MiningCurationModalScreen.WINDOW_HEIGHT);

		MiningCurationModalScreen modal = new MiningCurationModalScreen(
			new BlockPos(0, 0, 0),
			new BlockPos(10, 10, 10),
			null,
			null
		);

		Assertions.assertFalse(modal.shouldPause(), "Curation modal must not pause game in singleplayer");
		Assertions.assertFalse(modal.isClosed());
		modal.close();
		Assertions.assertTrue(modal.isClosed());
	}

	@Test
	@DisplayName("Curated counts and toggle operations")
	void testCurationTogglingAndCounts() {
		MiningCurationModalScreen modal = new MiningCurationModalScreen(
			new BlockPos(0, 0, 0),
			new BlockPos(5, 5, 5),
			null,
			null
		);

		Identifier oakLog = Identifier.of("minecraft", "oak_log");
		Identifier oakLeaves = Identifier.of("minecraft", "oak_leaves");
		Identifier dirt = Identifier.of("minecraft", "dirt");

		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(oakLog, null, null, null, "Oak Log", 24));
		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(oakLeaves, null, null, null, "Oak Leaves", 64));
		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(dirt, null, null, null, "Dirt", 120));

		Assertions.assertEquals(3, modal.getAllEntries().size());
		Assertions.assertEquals(3, modal.getSelectedBlockIds().size());
		Assertions.assertEquals(208, modal.getTotalBlockCount());
		Assertions.assertEquals(208, modal.getCuratedBlockCount());

		// Toggle off dirt
		modal.toggleEntry(dirt);
		Assertions.assertEquals(2, modal.getSelectedBlockIds().size());
		Assertions.assertFalse(modal.getSelectedBlockIds().contains(dirt));
		Assertions.assertEquals(88, modal.getCuratedBlockCount());

		// Toggle back on dirt
		modal.toggleEntry(dirt);
		Assertions.assertEquals(3, modal.getSelectedBlockIds().size());
		Assertions.assertTrue(modal.getSelectedBlockIds().contains(dirt));
		Assertions.assertEquals(208, modal.getCuratedBlockCount());
	}

	@Test
	@DisplayName("Bulk Select All and Clear All controls")
	void testBulkSelectAndClear() {
		MiningCurationModalScreen modal = new MiningCurationModalScreen(
			new BlockPos(0, 0, 0),
			new BlockPos(5, 5, 5),
			null,
			null
		);

		Identifier stone = Identifier.of("minecraft", "stone");
		Identifier ironOre = Identifier.of("minecraft", "iron_ore");

		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(stone, null, null, null, "Stone", 100));
		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(ironOre, null, null, null, "Iron Ore", 8));

		// Clear all
		modal.selectAll(false);
		Assertions.assertTrue(modal.getSelectedBlockIds().isEmpty());
		Assertions.assertEquals(0, modal.getCuratedBlockCount());
		Assertions.assertEquals(108, modal.getTotalBlockCount());

		// Select all
		modal.selectAll(true);
		Assertions.assertEquals(2, modal.getSelectedBlockIds().size());
		Assertions.assertEquals(108, modal.getCuratedBlockCount());
	}

	@Test
	@DisplayName("Search filter queries match names and identifiers")
	void testSearchFilter() {
		MiningCurationModalScreen modal = new MiningCurationModalScreen(
			new BlockPos(0, 0, 0),
			new BlockPos(5, 5, 5),
			null,
			null
		);

		Identifier oakLog = Identifier.of("minecraft", "oak_log");
		Identifier birchLog = Identifier.of("minecraft", "birch_log");
		Identifier stone = Identifier.of("minecraft", "stone");

		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(oakLog, null, null, null, "Oak Log", 10));
		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(birchLog, null, null, null, "Birch Log", 5));
		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(stone, null, null, null, "Stone", 50));

		Assertions.assertEquals(3, modal.getFilteredEntries().size());

		// Search for "log"
		modal.setSearchQuery("log");
		Assertions.assertEquals(2, modal.getFilteredEntries().size());

		// Search for "birch"
		modal.setSearchQuery("birch");
		Assertions.assertEquals(1, modal.getFilteredEntries().size());
		Assertions.assertEquals("Birch Log", modal.getFilteredEntries().get(0).name());

		// Search for non-existent term
		modal.setSearchQuery("diamond");
		Assertions.assertEquals(0, modal.getFilteredEntries().size());

		// Clear search
		modal.setSearchQuery("");
		Assertions.assertEquals(3, modal.getFilteredEntries().size());
	}

	@Test
	@DisplayName("Fail-safe apply logic: unchecking all blocks reverts to all detected blocks")
	void testFailSafeApplyLogic() {
		Identifier stone = Identifier.of("minecraft", "stone");
		Identifier dirt = Identifier.of("minecraft", "dirt");

		AtomicReference<Set<Identifier>> applied = new AtomicReference<>();

		MiningCurationModalScreen modal = new MiningCurationModalScreen(
			new BlockPos(0, 0, 0),
			new BlockPos(5, 5, 5),
			Collections.emptySet(),
			null,
			applied::set
		);

		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(stone, null, null, null, "Stone", 40));
		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(dirt, null, null, null, "Dirt", 20));

		// Uncheck all
		modal.selectAll(false);
		Assertions.assertTrue(modal.getSelectedBlockIds().isEmpty());

		// Apply should trigger fail-safe and yield all blocks
		modal.applyAndClose();
		Assertions.assertTrue(modal.isClosed());
		Assertions.assertNotNull(applied.get());
		Assertions.assertEquals(2, applied.get().size());
		Assertions.assertTrue(applied.get().contains(stone));
		Assertions.assertTrue(applied.get().contains(dirt));
	}

	@Test
	@DisplayName("Normal apply sends exact curated subset of blocks")
	void testSelectiveApply() {
		Identifier oakWood = Identifier.of("minecraft", "oak_wood");
		Identifier oakLeaves = Identifier.of("minecraft", "oak_leaves");
		Identifier grassBlock = Identifier.of("minecraft", "grass_block");

		AtomicReference<Set<Identifier>> applied = new AtomicReference<>();

		MiningCurationModalScreen modal = new MiningCurationModalScreen(
			new BlockPos(0, 0, 0),
			new BlockPos(5, 5, 5),
			Collections.emptySet(),
			null,
			applied::set
		);

		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(oakWood, null, null, null, "Oak Wood", 12));
		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(oakLeaves, null, null, null, "Oak Leaves", 30));
		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(grassBlock, null, null, null, "Grass Block", 50));

		// Uncheck grass block
		modal.toggleEntry(grassBlock);
		Assertions.assertEquals(2, modal.getSelectedBlockIds().size());

		modal.applyAndClose();
		Assertions.assertTrue(modal.isClosed());
		Assertions.assertNotNull(applied.get());
		Assertions.assertEquals(2, applied.get().size());
		Assertions.assertTrue(applied.get().contains(oakWood));
		Assertions.assertTrue(applied.get().contains(oakLeaves));
		Assertions.assertFalse(applied.get().contains(grassBlock));
	}

	@Test
	@DisplayName("Live ClientMiningCaptureTracker synchronization, isBlockCurated filtering, and cancel revert")
	void testCaptureTrackerSynchronizationAndRevert() {
		com.example.client.renderer.ClientMiningCaptureTracker.clear();

		Identifier oakLog = Identifier.of("minecraft", "oak_log");
		Identifier stone = Identifier.of("minecraft", "stone");

		// Initially without curation, all blocks are allowed
		Assertions.assertTrue(com.example.client.renderer.ClientMiningCaptureTracker.isBlockCurated(oakLog));
		Assertions.assertTrue(com.example.client.renderer.ClientMiningCaptureTracker.isBlockCurated(stone));

		MiningCurationModalScreen modal = new MiningCurationModalScreen(
			new BlockPos(0, 0, 0),
			new BlockPos(5, 5, 5),
			null,
			null
		);
		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(oakLog, null, null, null, "Oak Log", 10));
		modal.addEntryForTesting(new MiningCurationModalScreen.CurationEntry(stone, null, null, null, "Stone", 20));

		// Deselect stone
		modal.toggleEntry(stone);

		// ClientMiningCaptureTracker immediately updates live
		Assertions.assertTrue(com.example.client.renderer.ClientMiningCaptureTracker.isBlockCurated(oakLog));
		Assertions.assertFalse(com.example.client.renderer.ClientMiningCaptureTracker.isBlockCurated(stone));

		// Cancel without applying reverts to original state
		modal.close();
		Assertions.assertTrue(com.example.client.renderer.ClientMiningCaptureTracker.isBlockCurated(oakLog));
		Assertions.assertTrue(com.example.client.renderer.ClientMiningCaptureTracker.isBlockCurated(stone));
	}
}
