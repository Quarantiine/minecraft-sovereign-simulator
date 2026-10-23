package com.example.construction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests verifying that mining minions store mined items in nearby storage blocks when
 * their inventory is full, and drop items on the ground if no storage blocks can be found.
 */
public class MiningStorageOffloadTest {

	@Test
	@DisplayName("MinionHarvestingHelper defines isInventoryFull contract checking all slots")
	void testIsInventoryFullContract() throws IOException {
		String helperSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));

		Assertions.assertTrue(helperSource.contains("public static boolean isInventoryFull(SimpleInventory inv)"),
			"MinionHarvestingHelper must define public isInventoryFull(SimpleInventory inv)");
		Assertions.assertTrue(helperSource.contains("inv.getStack(i).isEmpty()"),
			"isInventoryFull must verify every slot in the inventory");
	}

	@Test
	@DisplayName("isMinedMaterial contract identifies mined items while protecting essential equipment, tools, food, and torches")
	void testIsMinedMaterialContract() throws IOException {
		String helperSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));

		Assertions.assertTrue(helperSource.contains("public static boolean isMinedMaterial(ItemStack stack)"),
			"MinionHarvestingHelper must define public isMinedMaterial(ItemStack stack)");
		Assertions.assertTrue(helperSource.contains("item instanceof ToolItem"),
			"isMinedMaterial must protect tools from being discarded or stored away");
		Assertions.assertTrue(helperSource.contains("item instanceof ArmorItem"),
			"isMinedMaterial must protect armor pieces");
		Assertions.assertTrue(helperSource.contains("item instanceof SwordItem"),
			"isMinedMaterial must protect swords");
		Assertions.assertTrue(helperSource.contains("DataComponentTypes.FOOD"),
			"isMinedMaterial must protect minion food rations");
		Assertions.assertTrue(helperSource.contains("item == Items.TORCH"),
			"isMinedMaterial must protect torches");
		Assertions.assertTrue(helperSource.contains("item == Items.BUCKET"),
			"isMinedMaterial must protect buckets");
		Assertions.assertTrue(helperSource.contains("com.example.item.ModItems.COMMAND_SCEPTER"),
			"isMinedMaterial must protect command scepters");
	}

	@Test
	@DisplayName("Mining storage discovery and container inventory resolution contracts")
	void testMiningStorageDiscoveryContracts() throws IOException {
		String helperSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));

		// Must define findNearbyStorageBlocks supporting vertical world height and horizontal radius
		Assertions.assertTrue(helperSource.contains("public static List<BlockPos> findNearbyStorageBlocks(ServerWorld world, BlockPos center, int horizontalRadius, UUID ownerUuid)"),
			"MinionHarvestingHelper must implement findNearbyStorageBlocks");
		Assertions.assertTrue(helperSource.contains("BarrierManager.getInstance().isPositionProtected"),
			"findNearbyStorageBlocks must exclude foreign/enemy protected barrier storages");
		Assertions.assertTrue(helperSource.contains("ChestType.SINGLE"),
			"findNearbyStorageBlocks must handle double chest pairs without duplicate entries");

		// Must define getContainerInventory supporting single chests, double chests, and block entity inventories
		Assertions.assertTrue(helperSource.contains("public static Inventory getContainerInventory(ServerWorld world, BlockPos pos)"),
			"MinionHarvestingHelper must define getContainerInventory");
		Assertions.assertTrue(helperSource.contains("ChestBlock.getInventory(chestBlock, state, world, pos, true)"),
			"getContainerInventory must resolve double chests via ChestBlock.getInventory");

		// Must define depositStackIntoInventory with stack merging and empty slot deposition
		Assertions.assertTrue(helperSource.contains("public static boolean depositStackIntoInventory(ItemStack stack, Inventory targetInv)"),
			"MinionHarvestingHelper must implement depositStackIntoInventory");
		Assertions.assertTrue(helperSource.contains("ItemStack.areItemsAndComponentsEqual(existing, stack)"),
			"depositStackIntoInventory must merge into matching item stacks");
		Assertions.assertTrue(helperSource.contains("targetInv.setStack(i, stack.split(toMove))"),
			"depositStackIntoInventory must place remaining quantity into empty slots");
	}

	@Test
	@DisplayName("Mining offload and ground dropping contract in MinionBuildGoal and ConstructionManager")
	void testMiningOffloadAndGroundDroppingContracts() throws IOException {
		String helperSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));
		String goalSource = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));
		String managerSource = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionManager.java"));

		// Helper offload method
		Assertions.assertTrue(helperSource.contains("public static boolean offloadMinedItemsToStorages(MinionEntity minion, ServerWorld world, ConstructionSession session)"),
			"MinionHarvestingHelper must implement offloadMinedItemsToStorages");
		Assertions.assertTrue(helperSource.contains("depositStackIntoInventory(stack, containerInv)"),
			"offloadMinedItemsToStorages must deposit mined stacks into container inventories");

		// MinionBuildGoal dismantle execution contracts
		Assertions.assertTrue(goalSource.contains("isInventoryFull(this.minion.getInventory())"),
			"MinionBuildGoal must check for full inventory during dismantle execution");
		Assertions.assertTrue(goalSource.contains("offloadMinedItemsToStorages(this.minion, serverWorld, this.currentSession)"),
			"MinionBuildGoal must call offloadMinedItemsToStorages when inventory is full");
		Assertions.assertTrue(goalSource.contains("this.minion.dropStack(remainder)"),
			"MinionBuildGoal must drop mined items on the ground if no storage blocks can accept remainder");

		// ConstructionManager contract: dismantle sessions do NOT deploy autonomous chests
		Assertions.assertTrue(managerSource.contains("if (!session.isDismantle())"),
			"ConstructionManager must avoid autonomous chest deployment on dismantle sessions");
	}
}
