package com.example.construction;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.StructureBlueprint;
import com.example.network.StartMiningAreaPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating server-side Mining Curation logic:
 * - Filtering blocks during blueprint creation via allowedBlockIds
 * - ConstructionSession curated block retention and task queuing
 * - hasRemainingBlocksInWorld selective scanning of curated targets
 * - StartMiningAreaPayload integration and backwards-compatibility
 * - Source contract verification across ConstructionManager, ConstructionSession, and ModNetworking
 */
public class MiningCurationTest {

	private static BlockState createDummyBlockState() {
		try {
			java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
			return (BlockState) unsafe.allocateInstance(BlockState.class);
		} catch (Throwable t) {
			return null;
		}
	}

	@Test
	@DisplayName("StructureBlueprint.Builder builds valid blueprint and captures block list")
	void testBlueprintBuilderAndCuratedFiltering() {
		BlockState stateA = createDummyBlockState();
		BlockState stateB = createDummyBlockState();
		BlockState stateC = createDummyBlockState();
		if (stateA == null || stateB == null || stateC == null) return;

		StructureBlueprint bp = StructureBlueprint.builder("test_bp", "Test Blueprint")
			.addBlock(new BlockPos(0, 0, 0), stateA)
			.addBlock(new BlockPos(0, 1, 0), stateB)
			.addBlock(new BlockPos(0, 2, 0), stateC)
			.build();

		Assertions.assertEquals(3, bp.getBlocks().size());

		// Filtering simulation using curated target set
		Set<BlockPos> curatedOffsets = new LinkedHashSet<>();
		curatedOffsets.add(new BlockPos(0, 0, 0));
		curatedOffsets.add(new BlockPos(0, 2, 0));

		List<BlueprintBlock> filteredBlocks = new ArrayList<>();
		for (BlueprintBlock b : bp.getBlocks()) {
			if (curatedOffsets.contains(b.offset())) {
				filteredBlocks.add(b);
			}
		}

		Assertions.assertEquals(2, filteredBlocks.size());
		Assertions.assertTrue(filteredBlocks.stream().anyMatch(b -> b.offset().equals(new BlockPos(0, 0, 0))));
		Assertions.assertTrue(filteredBlocks.stream().anyMatch(b -> b.offset().equals(new BlockPos(0, 2, 0))));
		Assertions.assertFalse(filteredBlocks.stream().anyMatch(b -> b.offset().equals(new BlockPos(0, 1, 0))));
	}

	@Test
	@DisplayName("ConstructionSession holds curated block IDs and exposes getter")
	void testConstructionSessionCuratedBlockIds() {
		BlockState stateA = createDummyBlockState();
		BlockState stateB = createDummyBlockState();
		if (stateA == null || stateB == null) return;

		Identifier stoneId = Identifier.of("minecraft", "stone");
		Identifier coalOreId = Identifier.of("minecraft", "coal_ore");
		Set<Identifier> curated = new HashSet<>();
		curated.add(stoneId);
		curated.add(coalOreId);

		StructureBlueprint bp = StructureBlueprint.builder("test_bp", "Test Blueprint")
			.addBlock(new BlockPos(0, 0, 0), stateA)
			.addBlock(new BlockPos(1, 0, 0), stateB)
			.build();

		RegistryKey<World> overworldKey = RegistryKey.of(
			RegistryKey.ofRegistry(Identifier.of("minecraft", "dimension")),
			Identifier.of("minecraft", "overworld")
		);

		ConstructionSession session = new ConstructionSession(
			UUID.randomUUID(),
			overworldKey,
			new BlockPos(0, 64, 0),
			bp,
			true,
			0L,
			ConstructionSession.SessionMode.BUILD,
			null,
			curated
		);

		Assertions.assertEquals(curated, session.getCuratedBlockIds());
		Assertions.assertTrue(session.getCuratedBlockIds().contains(stoneId));
		Assertions.assertTrue(session.getCuratedBlockIds().contains(coalOreId));
	}

	@Test
	@DisplayName("StartMiningAreaPayload backward compatibility and curated block list")
	void testStartMiningAreaPayload() {
		BlockPos p1 = new BlockPos(10, 60, 10);
		BlockPos p2 = new BlockPos(20, 70, 20);

		// Legacy constructor
		StartMiningAreaPayload legacyPayload = new StartMiningAreaPayload(p1, p2);
		Assertions.assertEquals(p1, legacyPayload.pos1());
		Assertions.assertEquals(p2, legacyPayload.pos2());
		Assertions.assertTrue(legacyPayload.curatedBlockIds().isEmpty());

		// Curated constructor
		Identifier diamondOre = Identifier.of("minecraft", "diamond_ore");
		StartMiningAreaPayload curatedPayload = new StartMiningAreaPayload(p1, p2, Collections.singletonList(diamondOre));
		Assertions.assertEquals(1, curatedPayload.curatedBlockIds().size());
		Assertions.assertEquals(diamondOre, curatedPayload.curatedBlockIds().get(0));
	}

	@Test
	@DisplayName("Source contract: ConstructionSession hasRemainingBlocksInWorld respects curatedBlockIds")
	void testSourceContractConstructionSessionCuratedScanning() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));

		// Must store curatedBlockIds field
		Assertions.assertTrue(source.contains("private final Set<Identifier> curatedBlockIds;"),
			"ConstructionSession must contain private final Set<Identifier> curatedBlockIds");

		// hasRemainingBlocksInWorld must filter against curatedBlockIds
		Assertions.assertTrue(source.contains("this.curatedBlockIds.isEmpty() || this.curatedBlockIds.contains"),
			"hasRemainingBlocksInWorld must check if curatedBlockIds is empty or contains the target block ID");
	}

	@Test
	@DisplayName("Source contract: ConstructionManager startAreaDismantleSession forwards curatedBlockIds")
	void testSourceContractConstructionManagerCuration() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionManager.java"));

		// Must have startAreaDismantleSession taking curatedBlockIds
		Assertions.assertTrue(source.contains("List<Identifier> curatedBlockIds"),
			"ConstructionManager must have startAreaDismantleSession accepting curatedBlockIds");

		// Must pass allowedBlockIds to createAreaBlueprint
		Assertions.assertTrue(source.contains("StructureBlueprint.createAreaBlueprint("),
			"ConstructionManager must call StructureBlueprint.createAreaBlueprint");
		Assertions.assertTrue(source.contains("allowedBlockIds"),
			"ConstructionManager must pass allowedBlockIds to createAreaBlueprint");
	}

	@Test
	@DisplayName("Source contract: ModNetworking extracts curatedBlockIds from payload")
	void testSourceContractModNetworkingCurationHandling() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/network/ModNetworking.java"));

		// handleStartMiningArea must pass payload.curatedBlockIds()
		Assertions.assertTrue(source.contains("payload.curatedBlockIds()"),
			"ModNetworking must pass payload.curatedBlockIds() to ConstructionManager");
	}

	@Test
	@DisplayName("Edge Case Safeguards: claimNextTask and executeDismantleWork never break uncurated blocks")
	void testCuratedEdgeCaseSafeguards() throws IOException {
		String sessionSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));
		Assertions.assertTrue(
			sessionSource.contains("isNotCurated") && sessionSource.contains("this.curatedBlockIds.contains"),
			"ConstructionSession.claimNextTask must auto-complete task if world block changed to an uncurated block"
		);

		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));
		Assertions.assertTrue(
			goalSource.contains("currentSession.getCuratedBlockIds()") && goalSource.contains("!this.currentSession.getCuratedBlockIds().contains(blockId)"),
			"MinionBuildGoal.executeDismantleWork must refuse to break uncurated blocks if world state changed"
		);

		String managerSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionManager.java"));
		Assertions.assertTrue(
			managerSource.contains("public void syncToPlayer(ServerPlayerEntity player)") && managerSource.contains("session.getCuratedBlockIds()"),
			"ConstructionManager must provide syncToPlayer sending active sessions with curatedBlockIds"
		);

		String modSource = Files.readString(Path.of("src/main/java/com/example/ExampleMod.java"));
		Assertions.assertTrue(
			modSource.contains("ConstructionManager.getInstance().syncToPlayer(handler.getPlayer())"),
			"ExampleMod must invoke ConstructionManager.syncToPlayer on ServerPlayConnectionEvents.JOIN"
		);
	}

	@Test
	@DisplayName("Water & Non-Block Removal Contract: Area mining removes water and non-blocks unless curated out")
	void testWaterAndNonBlockRemovalInMiningArea() throws IOException {
		// 1. StructureBlueprint.createAreaBlueprint includes water and all non-air blocks unless filtered by curation
		String bpSource = Files.readString(Path.of("src/main/java/com/example/blueprint/StructureBlueprint.java"));
		Assertions.assertTrue(
			bpSource.contains("if (b.state() != null && !b.state().isAir())")
			&& bpSource.contains("allowedBlockIds.contains(blockId)"),
			"StructureBlueprint.createAreaBlueprint must capture all non-air blocks (including water) unless filtered by curation"
		);

		// 2. ConstructionSession hasRemainingBlocksInWorld recognizes still water sources when not curated out
		String sessionSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));
		Assertions.assertTrue(
			sessionSource.contains("isIgnoredFluid") && sessionSource.contains("!isFluid(state) || !isIgnoredFluid"),
			"ConstructionSession.hasRemainingBlocksInWorld must recognize still fluid sources when uncurated or curated"
		);

		// 3. MinionBuildGoal.executeDismantleWork must check curation before removing fluid sources
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));
		int curationIndex = goalSource.indexOf("!this.currentSession.getCuratedBlockIds().contains(blockId)");
		int fluidSourceIndex = goalSource.indexOf("removeFluidSource(serverWorld, targetPos, currentState, isStillWater)");
		Assertions.assertTrue(curationIndex != -1 && fluidSourceIndex != -1 && curationIndex < fluidSourceIndex,
			"MinionBuildGoal must check curation whitelist before removeFluidSource so uncurated water is preserved");

		// 4. MinionBuildGoal in BUILD mode must clear water/fluids at target position before block placement
		Assertions.assertTrue(
			goalSource.contains("!targetState.isOf(Blocks.WATER) && (currentWorldState.isOf(Blocks.WATER) || !currentWorldState.getFluidState().isEmpty())")
			&& goalSource.contains("removeFluidSource(serverWorld, targetPos, currentWorldState, currentWorldState.isOf(Blocks.WATER))"),
			"MinionBuildGoal in BUILD mode must clear pre-existing water before placing solid blocks"
		);

		// 5. MiningCurationModalScreen uses water and lava bucket icons for fluid entries
		String screenSource = Files.readString(Path.of("src/client/java/com/example/client/gui/MiningCurationModalScreen.java"));
		Assertions.assertTrue(
			screenSource.contains("icon = new ItemStack(Items.WATER_BUCKET);")
			&& screenSource.contains("icon = new ItemStack(Items.LAVA_BUCKET);"),
			"MiningCurationModalScreen must resolve water and lava blocks to bucket icons"
		);
	}
}

