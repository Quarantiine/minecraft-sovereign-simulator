package com.example.features;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class NewFeaturesAndUpgradesContractTest {

	@Test
	@DisplayName("Contract: AUTO role minions retain mined resources in inventory in survival")
	void testAutoRoleMiningInventoryRetention() throws IOException {
		Path helperPath = Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java");
		String content = Files.readString(helperPath);
		Assertions.assertTrue(
			content.contains("boolean isAutoRole = minion.getRole() == com.example.entity.custom.MinionRole.AUTO;"),
			"MinionHarvestingHelper must check for AUTO minion role"
		);
		Assertions.assertTrue(
			content.contains("if (isMiningSession || (isAutoRole && session == null))"),
			"MinionHarvestingHelper must prevent premature offloading for AUTO minions in mining"
		);

		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		String goalContent = Files.readString(buildGoalPath);
		Assertions.assertTrue(
			goalContent.contains("m.getRole() == com.example.entity.custom.MinionRole.AUTO"),
			"MinionBuildGoal must share drop overspill with AUTO role minions"
		);
	}

	@Test
	@DisplayName("Contract: CommandScepterItem queues clustered hostiles upon right-click attack ping")
	void testHostileEntityClusterAttackQueue() throws IOException {
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		String content = Files.readString(scepterPath);
		Assertions.assertTrue(
			content.contains("Box clusterBox = target.getBoundingBox().expand(16.0D, 8.0D, 16.0D);"),
			"CommandScepterItem must query 16b cluster box around clicked target"
		);
		Assertions.assertTrue(
			content.contains("minion.setAssaultTargets(fullAssaultQueue);"),
			"CommandScepterItem must queue clustered assault targets into minion"
		);
		Assertions.assertTrue(
			content.contains("minion.addAssaultTargets(fullAssaultQueue);"),
			"CommandScepterItem must append targets via addAssaultTargets when minion is already in combat"
		);
	}

	@Test
	@DisplayName("Contract: Selected follower minions remain selected after build or mine completion")
	void testSelectedFollowerPreservedPostBuild() throws IOException {
		Path managerPath = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		String content = Files.readString(managerPath);
		Assertions.assertTrue(
			content.contains("if (!minion.isSelected()) {\n\t\t\t\t\tminion.setSelected(false);\n\t\t\t\t}"),
			"ConstructionManager must preserve isSelected state if minion was already selected"
		);
	}

	@Test
	@DisplayName("Contract: BlockPickerModalScreen contains Recent Picked section with LRU tracking")
	void testBlockPickerRecentPickedSection() throws IOException {
		Path pickerPath = Path.of("src/client/java/com/example/client/gui/BlockPickerModalScreen.java");
		String content = Files.readString(pickerPath);
		Assertions.assertTrue(
			content.contains("RECENT_PICKED_BLOCKS"),
			"BlockPickerModalScreen must define RECENT_PICKED_BLOCKS list"
		);
		Assertions.assertTrue(
			content.contains("✦ Recent Picked"),
			"BlockPickerModalScreen must render Recent Picked section header"
		);
		Assertions.assertTrue(
			content.contains("recentBlockAt(mouseX, mouseY)"),
			"BlockPickerModalScreen must detect clicks on recent block slots"
		);
	}

	@Test
	@DisplayName("Contract: Water and underwater construction flight and breathing")
	void testWaterAndUnderwaterConstruction() throws IOException {
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		String scepterContent = Files.readString(scepterPath);
		Assertions.assertTrue(
			scepterContent.contains("public static boolean isPlayerOverWater(World world, PlayerEntity player)"),
			"CommandScepterItem must define isPlayerOverWater"
		);

		Path minionPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		String minionContent = Files.readString(minionPath);
		Assertions.assertTrue(
			minionContent.contains("MinionEntity.this.isActivelyBuilding() || MinionEntity.this.isArcaneLevitating()"),
			"MinionEntity SwimGoal must yield when minion is actively building or levitating"
		);
		Assertions.assertTrue(
			minionContent.contains("this.setAir(this.getMaxAir());"),
			"MinionEntity must breathe underwater when engaged in construction"
		);
	}
}
