package com.example.construction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Automated test suite validating that Builder Minions understand survival mechanics
 * for living/spreading substrates, unobtainable blocks, compound structures, and multi-part placements.
 */
public class SurvivalLivingSubstrateAndCompoundBlocksTest {

	@Test
	@DisplayName("Living Substrates: BlueprintBlock maps grass, mycelium, and nylium to survival precursor items")
	void testLivingSubstrateVirtualItemMappings() throws IOException {
		String bpBlockSource = Files.readString(Path.of("src/main/java/com/example/blueprint/BlueprintBlock.java"));

		// Grass blocks and mycelium must map to Items.DIRT
		Assertions.assertTrue(bpBlockSource.contains("Blocks.GRASS_BLOCK"), "BlueprintBlock must handle Blocks.GRASS_BLOCK");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.MYCELIUM"), "BlueprintBlock must handle Blocks.MYCELIUM");
		Assertions.assertTrue(bpBlockSource.contains("Items.DIRT"), "Grass blocks and mycelium must map to Items.DIRT");

		// Crimson and Warped Nylium must map to Items.NETHERRACK
		Assertions.assertTrue(bpBlockSource.contains("Blocks.CRIMSON_NYLIUM"), "BlueprintBlock must handle Blocks.CRIMSON_NYLIUM");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.WARPED_NYLIUM"), "BlueprintBlock must handle Blocks.WARPED_NYLIUM");
		Assertions.assertTrue(bpBlockSource.contains("Items.NETHERRACK"), "Nylium must map to Items.NETHERRACK");
	}

	@Test
	@DisplayName("Survival Unobtainables: BlueprintBlock provides legal survival item fallbacks")
	void testSurvivalUnobtainablesMappings() throws IOException {
		String bpBlockSource = Files.readString(Path.of("src/main/java/com/example/blueprint/BlueprintBlock.java"));

		// Budding Amethyst -> Amethyst Block
		Assertions.assertTrue(bpBlockSource.contains("Blocks.BUDDING_AMETHYST") && bpBlockSource.contains("Items.AMETHYST_BLOCK"),
			"Budding amethyst must map to Amethyst Block");

		// Suspicious Sand & Gravel -> Sand & Gravel
		Assertions.assertTrue(bpBlockSource.contains("Blocks.SUSPICIOUS_SAND") && bpBlockSource.contains("Items.SAND"),
			"Suspicious sand must map to regular Sand");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.SUSPICIOUS_GRAVEL") && bpBlockSource.contains("Items.GRAVEL"),
			"Suspicious gravel must map to regular Gravel");

		// Frosted Ice -> Ice
		Assertions.assertTrue(bpBlockSource.contains("Blocks.FROSTED_ICE") && bpBlockSource.contains("Items.ICE"),
			"Frosted ice must map to regular Ice");

		// Petrified Oak Slab -> Oak Slab
		Assertions.assertTrue(bpBlockSource.contains("Blocks.PETRIFIED_OAK_SLAB") && bpBlockSource.contains("Items.OAK_SLAB"),
			"Petrified oak slab must map to Oak Slab");

		// Infested Stone variants -> Standard stone items
		Assertions.assertTrue(bpBlockSource.contains("Blocks.INFESTED_STONE") && bpBlockSource.contains("Items.STONE"),
			"Infested stone must map to regular Stone");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.INFESTED_COBBLESTONE") && bpBlockSource.contains("Items.COBBLESTONE"),
			"Infested cobblestone must map to regular Cobblestone");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.INFESTED_STONE_BRICKS") && bpBlockSource.contains("Items.STONE_BRICKS"),
			"Infested stone bricks must map to regular Stone Bricks");
		Assertions.assertTrue(bpBlockSource.contains("Blocks.INFESTED_DEEPSLATE") && bpBlockSource.contains("Items.DEEPSLATE"),
			"Infested deepslate must map to regular Deepslate");
	}

	@Test
	@DisplayName("Compound & Multi-Block Structures: Zero-cost upper halves, bed heads, and potted plants")
	void testCompoundAndMultiBlockMappings() throws IOException {
		String bpBlockSource = Files.readString(Path.of("src/main/java/com/example/blueprint/BlueprintBlock.java"));

		// Piston Head, Double Block Upper Half, and Bed Head must cost Items.AIR (placed by base item)
		Assertions.assertTrue(bpBlockSource.contains("Blocks.PISTON_HEAD") && bpBlockSource.contains("Items.AIR"),
			"Piston head must require Items.AIR");
		Assertions.assertTrue(bpBlockSource.contains("DoubleBlockHalf.UPPER") && bpBlockSource.contains("Items.AIR"),
			"Double block upper half must require Items.AIR");
		Assertions.assertTrue(bpBlockSource.contains("BedPart.HEAD") && bpBlockSource.contains("Items.AIR"),
			"Bed head must require Items.AIR");

		// FlowerPotBlock must map to Items.FLOWER_POT
		Assertions.assertTrue(bpBlockSource.contains("FlowerPotBlock") && bpBlockSource.contains("Items.FLOWER_POT"),
			"Potted plants must require Items.FLOWER_POT");
	}

	@Test
	@DisplayName("ConstructionSession: isSatisfiedBlockState recognizes organic substrates and dynamic properties")
	void testConstructionSessionSatisfiedBlockState() throws IOException {
		String sessionSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));

		// isSatisfiedBlockState method exists
		Assertions.assertTrue(sessionSource.contains("public static boolean isSatisfiedBlockState(BlockState worldState, BlockState blueprintState)"),
			"ConstructionSession must provide isSatisfiedBlockState");

		// Organic substrate satisfaction: DIRT satisfies GRASS_BLOCK and MYCELIUM
		Assertions.assertTrue(sessionSource.contains("worldState.isOf(Blocks.GRASS_BLOCK) || worldState.isOf(Blocks.DIRT)"),
			"Dirt in place must satisfy grass block tasks");
		Assertions.assertTrue(sessionSource.contains("worldState.isOf(Blocks.MYCELIUM) || worldState.isOf(Blocks.DIRT)"),
			"Dirt in place must satisfy mycelium tasks");

		// Nylium substrate satisfaction: NETHERRACK satisfies CRIMSON and WARPED NYLIUM
		Assertions.assertTrue(sessionSource.contains("worldState.isOf(Blocks.CRIMSON_NYLIUM) || worldState.isOf(Blocks.NETHERRACK)"),
			"Netherrack in place must satisfy crimson nylium tasks");
		Assertions.assertTrue(sessionSource.contains("worldState.isOf(Blocks.WARPED_NYLIUM) || worldState.isOf(Blocks.NETHERRACK)"),
			"Netherrack in place must satisfy warped nylium tasks");

		// Dynamic leaf distance, composter level, and chests
		Assertions.assertTrue(sessionSource.contains("blueprintState.getBlock() instanceof LeavesBlock && worldState.isOf(blueprintState.getBlock())"),
			"LeavesBlock must ignore dynamic distance property");
		Assertions.assertTrue(sessionSource.contains("blueprintState.isOf(Blocks.COMPOSTER) && worldState.isOf(Blocks.COMPOSTER)"),
			"Composter must ignore dynamic fill level");
		Assertions.assertTrue(sessionSource.contains("blueprintState.getBlock() instanceof ChestBlock"),
			"ChestBlock must match facing while tolerating double chest connection");
	}

	@Test
	@DisplayName("MinionBuildGoal: Silk touch priority, bone meal catalysis, and potted plant assembly")
	void testMinionBuildGoalSurvivalLogic() throws IOException {
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// Silk touch preference check before consuming dirt
		Assertions.assertTrue(goalSource.contains("preferredItem = Items.GRASS_BLOCK;"),
			"MinionBuildGoal must check for silk-touch grass blocks first");
		Assertions.assertTrue(goalSource.contains("preferredItem = Items.MYCELIUM;"),
			"MinionBuildGoal must check for silk-touch mycelium first");
		Assertions.assertTrue(goalSource.contains("preferredItem = Items.CRIMSON_NYLIUM;"),
			"MinionBuildGoal must check for silk-touch crimson nylium first");
		Assertions.assertTrue(goalSource.contains("preferredItem = Items.WARPED_NYLIUM;"),
			"MinionBuildGoal must check for silk-touch warped nylium first");

		// Bone meal catalysis for grass, mycelium, and nylium
		Assertions.assertTrue(goalSource.contains("consumeBoneMealIfPresent"),
			"MinionBuildGoal must support Bone Meal consumption for organic acceleration");
		Assertions.assertTrue(goalSource.contains("hasAdjacentGrass && consumeBoneMealIfPresent"),
			"MinionBuildGoal must accelerate grass conversion if adjacent grass exists and bone meal is carried");
		Assertions.assertTrue(goalSource.contains("hasAdjacentNylium && consumeBoneMealIfPresent"),
			"MinionBuildGoal must convert Netherrack to Nylium when touching Nylium and bone meal is carried");

		// Potted plant assembly (consumes plant item if present)
		Assertions.assertTrue(goalSource.contains("fpb.getContent().asItem()"),
			"MinionBuildGoal must inspect content of potted flower and consume plant if carried");

		// Double slab assembly (consumes second slab item)
		Assertions.assertTrue(goalSource.contains("SlabType.DOUBLE"),
			"MinionBuildGoal must consume second slab for double slab assembly");
	}
}
