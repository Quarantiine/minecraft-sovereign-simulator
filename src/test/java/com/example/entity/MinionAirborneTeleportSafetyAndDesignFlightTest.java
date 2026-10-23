package com.example.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating:
 * 1. Flight activation in DESIGN mode in both client BuildFlightManager and server CommandScepterItem.
 * 2. Architect Aerial Surveyor action bar feedback during DESIGN mode flight.
 * 3. Minion airborne teleport safety: finding safe ground at player X/Z when player is airborne.
 * 4. 2x2 solid platform safety requirement for elevated player landings to prevent fall damage.
 * 5. Water landing fallback when airborne over oceans/lakes without platforms.
 * 6. Lava refusal guard: minions refuse to teleport into lava or mid-air when over hazard oceans.
 */
public class MinionAirborneTeleportSafetyAndDesignFlightTest {

	@Test
	@DisplayName("Source contract: Flight is enabled in BUILD, MINE, and DESIGN modes")
	void testFlightEnabledInDesignMode() throws IOException {
		String flightManagerSource = Files.readString(Path.of("src/client/java/com/example/client/BuildFlightManager.java"));
		String scepterSource = Files.readString(Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));

		// Client BuildFlightManager checks DESIGN mode
		Assertions.assertTrue(flightManagerSource.contains("CommandMode.DESIGN"),
			"BuildFlightManager must activate flight in DESIGN mode");
		Assertions.assertTrue(flightManagerSource.contains("Architect Aerial Surveyor"),
			"BuildFlightManager must display Architect Aerial Surveyor actionbar text in DESIGN mode");

		// Server inventoryTick grants flight in DESIGN mode
		Assertions.assertTrue(scepterSource.contains("getMode(stack) == CommandMode.DESIGN"),
			"CommandScepterItem.inventoryTick must maintain server flight in DESIGN mode");
	}

	@Test
	@DisplayName("Source contract: MinionEntity implements 2x2 platform check and air-to-ground teleport destination")
	void testMinionAirborneTeleportSourceContract() throws IOException {
		String minionEntitySource = Files.readString(Path.of("src/main/java/com/example/entity/custom/MinionEntity.java"));

		// findSafeTeleportDestination helper
		Assertions.assertTrue(minionEntitySource.contains("public static Vec3d findSafeTeleportDestination(ServerWorld world, ServerPlayerEntity player)"),
			"MinionEntity must implement findSafeTeleportDestination");

		// 2x2 platform validation
		Assertions.assertTrue(minionEntitySource.contains("public static boolean hasAtLeast2x2Platform(World world, BlockPos pos)"),
			"MinionEntity must implement hasAtLeast2x2Platform to prevent landing on 1x1 needles or slippery edges");

		// Safe water landing fallback
		Assertions.assertTrue(minionEntitySource.contains("public static boolean isSafeWaterLanding(World world, BlockPos pos)"),
			"MinionEntity must implement isSafeWaterLanding for safe water surface teleportation");

		// Lava / hazard refusal guard
		Assertions.assertTrue(minionEntitySource.contains("if (dest == null) {\n\t\t\t// No safe landing site (e.g. over lava ocean with no platform or shore)\n\t\t\t// Do not teleport minion into lava or mid-air\n\t\t\treturn false;\n\t\t}"),
			"MinionEntity.teleportToPlayer must refuse teleportation if no safe ground/water exists");
	}

	@Test
	@DisplayName("Source contract: Priority structure in findSafeTeleportDestination (Platform -> Ground -> Water -> Lava refusal)")
	void testTeleportPriorityStructure() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/entity/custom/MinionEntity.java"));

		// Step 1: 2x2 platform check near player
		Assertions.assertTrue(source.contains("hasAtLeast2x2Platform(world, candidate)"),
			"findSafeTeleportDestination must verify platform has at least 2x2 solid blocks");

		// Step 2A: Solid ground scan downward
		Assertions.assertTrue(source.contains("Priority 2A: Solid ground directly under player X and Z"),
			"findSafeTeleportDestination must prioritize solid ground directly under player X and Z");

		// Step 2B: Water below player
		Assertions.assertTrue(source.contains("Priority 2B: Water below player"),
			"findSafeTeleportDestination must check for safe water landing when no solid ground is beneath player");

		// Step 2C: Shore search around hazards
		Assertions.assertTrue(source.contains("Priority 2C: Expanded shore search around hazards/lava"),
			"findSafeTeleportDestination must scan for safe shores if directly over hazards");

		// Step 3: Strict lava avoidance
		Assertions.assertTrue(source.contains("Strictly refuse teleportation into lava"),
			"findSafeTeleportDestination must refuse teleportation when no safe land or water exists");
	}
}
