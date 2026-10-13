package com.example.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating source contracts for blueprint rotation controls:
 * - Dedicated 'R' key rotation in BUILD mode
 * - Direct Left-Click rotation in BUILD mode without requiring Shift
 * - Arcane Build Flight altitude adjustment using Space (Jump) and Shift (Sneak)
 * - Server and client attack callback hooks
 */
public class BlueprintRotationInputContractTest {

	@Test
	@DisplayName("Source Invariant: ExampleModClient rotates blueprint on R key and leaves Left-Click standard in BUILD mode")
	void testClientRotationControls() throws IOException {
		Path clientPath = Path.of("src/client/java/com/example/client/ExampleModClient.java");
		Assertions.assertTrue(Files.exists(clientPath), "ExampleModClient.java must exist");
		String content = Files.readString(clientPath);

		// 1. R key cycles rotation in BUILD mode
		Assertions.assertTrue(
			content.contains("CommandScepterItem.getMode(heldScepter) == CommandMode.BUILD") &&
			content.contains("CommandScepterItem.cycleRotation(heldScepter, client.player)"),
			"R key in BUILD mode must cycle blueprint rotation"
		);

		// 2. Left-click / attackKey in BUILD mode is standard Minecraft hit/break (not intercepted for rotation)
		Assertions.assertFalse(
			content.contains("heldMode == CommandMode.BUILD") && content.contains("CommandScepterItem.cycleRotation(heldScepter, client.player)"),
			"Left-click attack key in BUILD mode must not hijack click for rotation"
		);

		// 3. Physical sneak detection for opening Command Hub GUI in flight
		Assertions.assertTrue(
			content.contains("MinecraftClient.getInstance().options.sneakKey.isPressed()"),
			"Sneak detection must check physical sneak key to work during physical flight"
		);
	}

	@Test
	@DisplayName("Source Invariant: BuildFlightManager supports altitude adjustment using Space and Shift keys and R for rotation")
	void testFlightAltitudeAdjustmentControls() throws IOException {
		Path flightPath = Path.of("src/client/java/com/example/client/BuildFlightManager.java");
		Assertions.assertTrue(Files.exists(flightPath), "BuildFlightManager.java must exist");
		String content = Files.readString(flightPath);

		// 1. Jump key ascends
		Assertions.assertTrue(
			content.contains("options.jumpKey.isPressed()"),
			"BuildFlightManager must check jumpKey.isPressed() to raise altitude"
		);

		// 2. Sneak key descends
		Assertions.assertTrue(
			content.contains("options.sneakKey.isPressed()"),
			"BuildFlightManager must check sneakKey.isPressed() to lower altitude"
		);

		// 3. Action bar feedback includes keys
		Assertions.assertTrue(
			content.contains("[Space/Shift] Alt") && content.contains("[R] Rotate"),
			"Action bar message must guide player on flight altitude and R rotation key"
		);
	}

	@Test
	@DisplayName("Source Invariant: ExampleMod Attack callbacks do not hijack BUILD mode for rotation")
	void testServerAttackCallbacks() throws IOException {
		Path modPath = Path.of("src/main/java/com/example/ExampleMod.java");
		Assertions.assertTrue(Files.exists(modPath), "ExampleMod.java must exist");
		String content = Files.readString(modPath);

		// 1. Attack block in BUILD mode is standard hit/break (not intercepted)
		Assertions.assertTrue(
			content.contains("AttackBlockCallback.EVENT.register"),
			"AttackBlockCallback must be registered"
		);
		Assertions.assertFalse(
			content.contains("mode == CommandMode.BUILD") &&
			content.contains("CommandScepterItem.cycleRotation(stack, player)"),
			"Attack block in BUILD mode must not hijack block hitting/mining for rotation"
		);

		// 2. Attack entity callback registered
		Assertions.assertTrue(
			content.contains("AttackEntityCallback.EVENT.register"),
			"AttackEntityCallback must be registered"
		);
	}
}
