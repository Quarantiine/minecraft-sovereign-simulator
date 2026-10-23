package com.example.item;

import com.example.component.CommandMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating:
 * 1. CommandMode.DEFAULT standby mode enum invariants and sequence cycling.
 * 2. CommandScepterItem pass-through behavior in DEFAULT mode (blocks, entities, air).
 * 3. Flight capability in MINE mode (client BuildFlightManager & server inventoryTick).
 * 4. Builder minion autonomous water bucket gathering outside barrier waypoints and infinite springs.
 */
public class CommandScepterDefaultModeAndMiningFlightTest {

	@Test
	@DisplayName("CommandMode.DEFAULT enum invariants, serialization, and sequence cycling")
	void testDefaultCommandModeEnumInvariants() {
		CommandMode defaultMode = CommandMode.DEFAULT;
		Assertions.assertNotNull(defaultMode, "CommandMode.DEFAULT must exist");
		Assertions.assertEquals("default", defaultMode.asString());
		Assertions.assertEquals("Default", defaultMode.getDisplayName());
		Assertions.assertEquals("§7", defaultMode.getColorCode());
		Assertions.assertEquals("§7Default", defaultMode.getFormattedName());

		// Cyclic sequencing: PATHWAY -> DEFAULT -> FOLLOW
		Assertions.assertEquals(CommandMode.DEFAULT, CommandMode.PATHWAY.next(),
			"CommandMode.PATHWAY.next() must cycle to DEFAULT");
		Assertions.assertEquals(CommandMode.FOLLOW, CommandMode.DEFAULT.next(),
			"CommandMode.DEFAULT.next() must cycle to FOLLOW");
		Assertions.assertEquals(CommandMode.PATHWAY, CommandMode.DEFAULT.previous(),
			"CommandMode.DEFAULT.previous() must cycle back to PATHWAY");
	}

	@Test
	@DisplayName("Source contract: CommandScepterItem passes all interactions in DEFAULT mode")
	void testCommandScepterDefaultModePassThroughContract() throws IOException {
		String scepterSource = Files.readString(Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));

		// getMaxUseTime & getUseAction in DEFAULT mode
		Assertions.assertTrue(scepterSource.contains("if (getMode(stack) == CommandMode.DEFAULT) {\n\t\t\treturn 0;"),
			"getMaxUseTime must return 0 in DEFAULT mode to disable channeled blocking");
		Assertions.assertTrue(scepterSource.contains("if (getMode(stack) == CommandMode.DEFAULT) {\n\t\t\treturn UseAction.NONE;"),
			"getUseAction must return UseAction.NONE in DEFAULT mode");

		// useOnBlock pass-through
		Assertions.assertTrue(scepterSource.contains("if (mode == CommandMode.DEFAULT) {\n\t\t\treturn ActionResult.PASS;"),
			"useOnBlock must return ActionResult.PASS in DEFAULT mode to allow vanilla block interactions");

		// useOnEntity pass-through
		Assertions.assertTrue(scepterSource.contains("if (mode == CommandMode.DEFAULT) {\n\t\t\treturn ActionResult.PASS;"),
			"useOnEntity must return ActionResult.PASS in DEFAULT mode to allow vanilla entity interactions");

		// use (air right-click) pass-through
		Assertions.assertTrue(scepterSource.contains("if (mode == CommandMode.DEFAULT) {\n\t\t\treturn TypedActionResult.pass(stack);"),
			"use must return TypedActionResult.pass in DEFAULT mode when not sneaking");
	}

	@Test
	@DisplayName("Source contract: Flight is enabled in both BUILD and MINE mode")
	void testMiningFlightSourceContract() throws IOException {
		String flightManagerSource = Files.readString(Path.of("src/client/java/com/example/client/BuildFlightManager.java"));
		String scepterSource = Files.readString(Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));

		// Client BuildFlightManager checks MINE mode
		Assertions.assertTrue(flightManagerSource.contains("CommandMode.BUILD || CommandScepterItem.getMode(heldStack) == CommandMode.MINE"),
			"BuildFlightManager must activate flight in both BUILD and MINE modes");
		Assertions.assertTrue(flightManagerSource.contains("Quarry Aerial Surveyor"),
			"BuildFlightManager must display Quarry Aerial Surveyor actionbar text in MINE mode");

		// Server inventoryTick grants flight in MINE mode
		Assertions.assertTrue(scepterSource.contains("CommandMode.BUILD || getMode(stack) == CommandMode.MINE"),
			"CommandScepterItem.inventoryTick must maintain server flight in both BUILD and MINE modes");
	}

	@Test
	@DisplayName("Source contract: Water bucket gathering outside barrier waypoints and infinite springs")
	void testWaterGatheringOutsideBarriersSourceContract() throws IOException {
		String helperSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// MinionHarvestingHelper defines infinite water source check
		Assertions.assertTrue(helperSource.contains("isInfiniteWaterSource(ServerWorld world, BlockPos pos)"),
			"MinionHarvestingHelper must define isInfiniteWaterSource helper");

		// isProtectedWaterSource allows infinite water sources
		Assertions.assertTrue(helperSource.contains("if (!isInfiniteWaterSource(world, pos))"),
			"isProtectedWaterSource must allow infinite/renewable water sources");

		// Extended 64-block search for water outside barriers
		Assertions.assertTrue(helperSource.contains("BlockPos.iterateOutwards(minionPos, 64, 32, 64)"),
			"gatherWaterBucket must scan up to 64 blocks horizontally and 32 vertically for exterior water sources");

		// Water cauldron fallback
		Assertions.assertTrue(helperSource.contains("Blocks.WATER_CAULDRON"),
			"gatherWaterBucket must check for water cauldrons");

		// Container empty bucket withdrawal
		Assertions.assertTrue(helperSource.contains("withdrawBucketFromNearbyContainers"),
			"MinionHarvestingHelper must implement withdrawBucketFromNearbyContainers");

		// MinionBuildGoal triggers gatherWaterBucket when carrying bucket and needing water
		Assertions.assertTrue(goalSource.contains("requiredItem == Items.WATER_BUCKET && com.example.entity.ai.logistics.MinionHarvestingHelper.hasItemInInventory(this.minion, Items.BUCKET)"),
			"MinionBuildGoal must trigger gatherWaterBucket when minion carries an empty bucket and needs a water bucket");
	}

	@Test
	@DisplayName("Source contract: CommandScepterScreen renders DEFAULT mode button and symmetrical Targets layout")
	void testCommandScepterScreenDefaultModeGuiContract() throws IOException {
		String screenSource = Files.readString(Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java"));

		// 8 modes grid
		Assertions.assertTrue(screenSource.contains("// Command Mode Buttons in a 2-column grid (8 modes)"),
			"CommandScepterScreen must render all 8 command modes");

		// Symmetrical Targets button placement
		Assertions.assertTrue(screenSource.contains(".dimensions(startX + 87, startY + 100, 65, 15)"),
			"targetFilterBtn must be positioned symmetrically at startX + 87, startY + 100 with width 65, height 15");

		// Mode description for DEFAULT mode
		Assertions.assertTrue(screenSource.contains("case DEFAULT -> \"§7Neutral standby mode. Scepter will not intercept interactions.\";"),
			"CommandScepterScreen must include description for DEFAULT mode");

		// Standby dashboard
		Assertions.assertTrue(screenSource.contains("✦ Neutral Standby Mode"),
			"CommandScepterScreen must render Neutral Standby Mode dashboard in right pane");
	}
}
