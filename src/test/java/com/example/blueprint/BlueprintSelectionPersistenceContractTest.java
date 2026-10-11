package com.example.blueprint;

import com.example.item.custom.CommandScepterItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating source contracts and invariants for persistent blueprint selection:
 * - Scepter never cycles or switches blueprints on right-click release (onStoppedUsing).
 * - Client UseItemCallback never cycles blueprints on air/sky clicks.
 * - Blueprint selection is preserved persistently across repeated placements.
 */
public class BlueprintSelectionPersistenceContractTest {

	@Test
	@DisplayName("Source Invariant: onStoppedUsing in CommandScepterItem must never call cycleBlueprint in BUILD mode")
	void testOnStoppedUsingDoesNotCycleBlueprint() throws IOException {
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(Files.exists(scepterPath), "CommandScepterItem.java must exist");
		String content = Files.readString(scepterPath);

		// Ensure onStoppedUsing quick-tap does NOT invoke cycleBlueprint
		int onStoppedIndex = content.indexOf("public void onStoppedUsing");
		Assertions.assertTrue(onStoppedIndex != -1, "onStoppedUsing must exist in CommandScepterItem.java");
		int quickTapIndex = content.indexOf("if (useTicks < 8)", onStoppedIndex);
		Assertions.assertTrue(quickTapIndex != -1, "quick tap block must exist in onStoppedUsing");
		int nextSectionIndex = content.indexOf("float chargeProgress =", quickTapIndex);
		Assertions.assertTrue(nextSectionIndex != -1, "chargeProgress section must exist");

		String quickTapSnippet = content.substring(quickTapIndex, nextSectionIndex);
		Assertions.assertFalse(
			quickTapSnippet.contains("cycleBlueprint"),
			"onStoppedUsing quick-tap block must NOT invoke cycleBlueprint in BUILD mode (blueprint selection must be persistent)"
		);
	}

	@Test
	@DisplayName("Source Invariant: ExampleModClient UseItemCallback must never call cycleBlueprint on air clicks")
	void testClientRightClickAirDoesNotCycleBlueprint() throws IOException {
		Path clientPath = Path.of("src/client/java/com/example/client/ExampleModClient.java");
		Assertions.assertTrue(Files.exists(clientPath), "ExampleModClient.java must exist");
		String content = Files.readString(clientPath);

		int callbackIndex = content.indexOf("UseItemCallback.EVENT.register");
		Assertions.assertTrue(callbackIndex != -1, "UseItemCallback must be registered");
		int commandHubKeyIndex = content.indexOf("commandHubKey =", callbackIndex);
		Assertions.assertTrue(commandHubKeyIndex != -1, "commandHubKey registration must follow callback");

		String callbackSnippet = content.substring(callbackIndex, commandHubKeyIndex);
		Assertions.assertFalse(
			callbackSnippet.contains("cycleBlueprint"),
			"ExampleModClient UseItemCallback must NOT call cycleBlueprint on air/sky clicks (blueprint selection must be persistent)"
		);
	}

	@Test
	@DisplayName("Source Invariant: CommandScepterItem tooltip must not instruct right-clicking air to cycle blueprint")
	void testTooltipGuidance() throws IOException {
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(Files.exists(scepterPath), "CommandScepterItem.java must exist");
		String content = Files.readString(scepterPath);

		Assertions.assertFalse(
			content.contains("Right-Click Air (Build): Cycle Blueprint"),
			"Tooltip must not advertise Right-Click Air cycling blueprints in BUILD mode"
		);
		Assertions.assertTrue(
			content.contains("Anchor Construction (Retains Blueprint)"),
			"Tooltip must confirm that ground placement retains active blueprint selection"
		);
	}
}
