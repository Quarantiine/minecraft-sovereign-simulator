package com.example.entity.ai.logistics;

import com.example.construction.ConstructionSession;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.GlowSquidEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Provides autonomous material harvesting, agro-forestry tree cultivation, tool self-crafting,
 * and automated supply depot chest placement for Builder minions in Survival mode.
 */
public final class MinionHarvestingHelper {

	private static final int HARVEST_SEARCH_RADIUS = 16;
	private static final int BASE_BUFFER_RADIUS = 12;

	private MinionHarvestingHelper() {}

	/**
	 * Attempts to autonomously harvest or craft the required material for an active blueprint task.
	 *
	 * @param minion       The builder minion.
	 * @param world        The server world.
	 * @param requiredItem The item needed for construction.
	 * @param session      The active construction session.
	 * @return True if the required item was obtained and placed in the minion's inventory.
	 */
	public static boolean tryAutonomousHarvest(
		MinionEntity minion,
		ServerWorld world,
		Item requiredItem,
		ConstructionSession session
	) {
		if (minion == null || world == null || requiredItem == null) {
			return false;
		}

		// 1. Tool check & self-crafting: ensure appropriate tool is equipped or crafted
		ensureAppropriateTool(minion, world, requiredItem);

		// 2. Wood / Timber / Planks Harvesting & Agro-Forestry
		if (isWoodResource(requiredItem)) {
			if (harvestWoodOrAgroForestry(minion, world, session)) {
				// Convert to planks if required item is planks
				if (isPlanksItem(requiredItem)) {
					convertLogsToPlanks(minion, requiredItem);
				} else if (isWoodenDerivative(requiredItem)) {
					craftWoodenDerivative(minion, world, requiredItem, session);
				}
				return hasItemInInventory(minion, requiredItem);
			}
		}

		// 3. Smart Block Transformation & Tool Block Utilization (Smelting, Crafting, Stonecutting)
		if (isTransformableResource(requiredItem)) {
			if (trySmartTransformation(minion, world, requiredItem, session)) {
				return hasItemInInventory(minion, requiredItem);
			}
		}

		// 4. Natural Stone / Cobblestone / Deepslate / Earth Quarrying
		if (isQuarryResource(requiredItem)) {
			if (quarryNaturalStone(minion, world, requiredItem, 1, session)) {
				return hasItemInInventory(minion, requiredItem);
			}
		}

		// 5. Mob Material Procurement & In-Inventory Synthesis
		if (isMobProcurementResource(requiredItem)) {
			// First attempt in-inventory synthesis (e.g. bones -> bone meal, string -> wool)
			if (synthesizeMaterial(minion.getInventory(), requiredItem)) {
				return hasItemInInventory(minion, requiredItem);
			}
			// Attempt mob hunting or warrior contract commissioning
			if (tryAutonomousMobHunting(minion, world, requiredItem)) {
				return true;
			}
		}

		// 6. Workstations, Utilities & Furniture Autonomous Synthesis
		if (isWorkstationResource(requiredItem)) {
			if (synthesizeWorkstation(minion, world, requiredItem, session)) {
				return hasItemInInventory(minion, requiredItem);
			}
		}

		return false;
	}

	/**
	 * Checks if an item can be safely discarded in Creative mode (any block or non-tool/non-weapon/non-armor).
	 */
	public static boolean isDiscardableCreativeItem(Item item) {
		if (item == null || item == Items.AIR) return false;
		if (item instanceof ArmorItem) return false;
		if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) return false;
		if (item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem || item instanceof MaceItem) return false;
		if (item == com.example.item.ModItems.FROST_GRENADE_STICK || item == com.example.item.ModItems.TNT_STICK) return false;
		return true;
	}

	/**
	 * Checks minion inventory capacity and deposits surplus non-blueprint materials into nearby chests.
	 * If no chest exists or nearby containers are full, crafts and places an autonomous Chest / Double Chest.
	 *
	 * @param minion  The builder minion.
	 * @param world   The server world.
	 * @param session The active construction session.
	 */
	public static void checkAndDepositExcessMaterials(
		MinionEntity minion,
		ServerWorld world,
		ConstructionSession session
	) {
		checkAndDepositExcessMaterials(minion, world, session, false);
	}

	/**
	 * Checks minion inventory capacity and deposits surplus materials into nearby chests.
	 * In Creative mode, unneeded blocks are discarded directly from inventory to keep slots clean.
	 * In Survival mode, if forceAllExcess is true, all surplus non-blueprint items are deposited into chests.
	 *
	 * @param minion         The builder minion.
	 * @param world          The server world.
	 * @param session        The active construction session.
	 * @param forceAllExcess True to deposit all surplus regardless of occupied slot count (e.g. upon session finish).
	 */
	public static void checkAndDepositExcessMaterials(
		MinionEntity minion,
		ServerWorld world,
		ConstructionSession session,
		boolean forceAllExcess
	) {
		if (minion == null || world == null) {
			return;
		}

		SimpleInventory inv = minion.getInventory();

		// In Creative mode, builders don't need chests; they discard unneeded blocks entirely
		// so they have empty space to build with other types of blocks without clutter.
		if (session != null && session.isCreative()) {
			boolean modified = false;
			for (int i = 0; i < inv.size(); i++) {
				ItemStack stack = inv.getStack(i);
				if (!stack.isEmpty() && isDiscardableCreativeItem(stack.getItem())) {
					inv.setStack(i, ItemStack.EMPTY);
					modified = true;
				}
			}
			if (modified) {
				inv.markDirty();
			}
			return;
		}

		int occupiedSlots = 0;
		for (int i = 0; i < inv.size(); i++) {
			if (!inv.getStack(i).isEmpty()) {
				occupiedSlots++;
			}
		}

		// In survival mode:
		// For mining/dismantle sessions, minions hold onto all harvested materials in their 9-slot backpack.
		// They only offload into a chest when their inventory is completely full and cannot accept incoming items.
		// For building/construction sessions, builders deposit excess materials when near capacity (>= 7 of 9 slots full)
		// so they retain empty slots to fetch required blueprint blocks.
		boolean isMiningSession = session != null && session.isDismantle();
		if (isMiningSession) {
			if (!forceAllExcess) {
				// While actively mining, do not proactively offload at task boundaries if space remains
				return;
			}
			// Even when forced, if the inventory still has empty slots, keep holding onto everything!
			if (occupiedSlots < inv.size()) {
				return;
			}
		} else {
			if (!forceAllExcess && occupiedSlots < 7) {
				return;
			}
		}

		// Identify excess stacks
		List<Integer> excessSlots = new ArrayList<>();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isEmpty()) continue;
			if (isExcessItem(stack.getItem(), session, forceAllExcess)) {
				excessSlots.add(i);
			}
		}

		if (excessSlots.isEmpty()) {
			return;
		}

		// 1. Look for existing container within 24 blocks
		BlockPos minionPos = minion.getBlockPos();
		BlockPos targetChestPos = findNearbyChestWithSpace(world, minionPos, 24);

		// 2. If no container with space found, attempt autonomous chest crafting and deployment
		if (targetChestPos == null) {
			targetChestPos = deployAutonomousChest(minion, world, session);
		}

		// 3. Deposit excess items into the container
		if (targetChestPos != null) {
			BlockEntity be = world.getBlockEntity(targetChestPos);
			if (be instanceof Inventory containerInv) {
				for (int slotIndex : excessSlots) {
					ItemStack stack = inv.getStack(slotIndex);
					if (stack.isEmpty()) continue;

					for (int cSlot = 0; cSlot < containerInv.size(); cSlot++) {
						ItemStack destStack = containerInv.getStack(cSlot);
						if (destStack.isEmpty()) {
							containerInv.setStack(cSlot, stack.copy());
							inv.setStack(slotIndex, ItemStack.EMPTY);
							break;
						} else if (ItemStack.areItemsAndComponentsEqual(destStack, stack)) {
							int transferable = Math.min(stack.getCount(), destStack.getMaxCount() - destStack.getCount());
							if (transferable > 0) {
								destStack.increment(transferable);
								stack.decrement(transferable);
								if (stack.isEmpty()) {
									inv.setStack(slotIndex, ItemStack.EMPTY);
									break;
								}
							}
						}
					}
				}
				containerInv.markDirty();
				inv.markDirty();

				world.playSound(null, targetChestPos, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.8F, 1.0F);
				world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, targetChestPos.getX() + 0.5D, targetChestPos.getY() + 0.8D, targetChestPos.getZ() + 0.5D, 6, 0.3D, 0.2D, 0.3D, 0.02D);
			}
		}
	}

	public static BlockPos deployAutonomousChest(MinionEntity minion, ServerWorld world, ConstructionSession session) {
		// Ensure minion has or can craft a chest
		boolean hasChest = hasItemInInventory(minion, Items.CHEST);
		if (!hasChest) {
			int totalPlanks = countItemInInventory(minion.getInventory(), ItemTags.PLANKS);
			if (totalPlanks >= 8) {
				consumeItemFromInventory(minion.getInventory(), ItemTags.PLANKS, 8);
				hasChest = true;
			} else {
				int logs = countItemInInventory(minion.getInventory(), ItemTags.LOGS);
				if (logs >= 2) {
					consumeItemFromInventory(minion.getInventory(), ItemTags.LOGS, 2);
					hasChest = true;
				}
			}
		}

		if (!hasChest) {
			return null;
		}

		// Find a solid ground spot within 6 blocks
		BlockPos minionPos = minion.getBlockPos();
		BlockPos candidate = null;
		for (BlockPos pos : BlockPos.iterateOutwards(minionPos, 5, 2, 5)) {
			if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
				if (!isProtectedBlock(world, pos, session, minion.getOwnerUuid())) {
					candidate = pos.toImmutable();
					break;
				}
			}
		}

		if (candidate == null) {
			return null;
		}

		// Check if adjacent block is a single chest to form a Double Chest
		Direction doubleChestFacing = null;
		ChestType chestType = ChestType.SINGLE;

		for (Direction dir : Direction.Type.HORIZONTAL) {
			BlockPos neighborPos = candidate.offset(dir);
			BlockState neighborState = world.getBlockState(neighborPos);
			if (neighborState.isOf(Blocks.CHEST) && neighborState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
				Direction facing = neighborState.get(ChestBlock.FACING);
				if (dir == facing.rotateYClockwise()) {
					// Neighbor is right side, candidate is left
					chestType = ChestType.LEFT;
					doubleChestFacing = facing;
					world.setBlockState(neighborPos, neighborState.with(ChestBlock.CHEST_TYPE, ChestType.RIGHT), Block.NOTIFY_ALL);
					break;
				} else if (dir == facing.rotateYCounterclockwise()) {
					// Neighbor is left side, candidate is right
					chestType = ChestType.RIGHT;
					doubleChestFacing = facing;
					world.setBlockState(neighborPos, neighborState.with(ChestBlock.CHEST_TYPE, ChestType.LEFT), Block.NOTIFY_ALL);
					break;
				}
			}
		}

		BlockState chestState = Blocks.CHEST.getDefaultState();
		if (doubleChestFacing != null) {
			chestState = chestState.with(ChestBlock.FACING, doubleChestFacing).with(ChestBlock.CHEST_TYPE, chestType);
		} else {
			chestState = chestState.with(ChestBlock.FACING, Direction.NORTH);
		}

		world.setBlockState(candidate, chestState, Block.NOTIFY_ALL);
		if (hasItemInInventory(minion, Items.CHEST)) {
			consumeItemFromInventory(minion.getInventory(), Items.CHEST, 1);
		}

		world.playSound(null, candidate, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0F, 1.0F);
		return candidate;
	}

	public static BlockPos findNearbyChestWithSpace(ServerWorld world, BlockPos center, int radius) {
		for (BlockPos pos : BlockPos.iterateOutwards(center, radius, 4, radius)) {
			BlockState state = world.getBlockState(pos);
			if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.BARREL) || state.isOf(Blocks.TRAPPED_CHEST)) {
				BlockEntity be = world.getBlockEntity(pos);
				if (be instanceof Inventory inv) {
					for (int i = 0; i < inv.size(); i++) {
						ItemStack s = inv.getStack(i);
						if (s.isEmpty() || s.getCount() < s.getMaxCount()) {
							return pos.toImmutable();
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean isExcessItem(Item item, ConstructionSession session) {
		return isExcessItem(item, session, false);
	}

	private static boolean isExcessItem(Item item, ConstructionSession session, boolean forceAllExcess) {
		if (item == Items.CHEST) return false;
		if (item == Items.TORCH || item.getComponents().contains(DataComponentTypes.FOOD)) return false;
		if (item instanceof ArmorItem) return false;
		if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) return false;
		if (item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem || item instanceof MaceItem) return false;
		if (item == com.example.item.ModItems.FROST_GRENADE_STICK || item == com.example.item.ModItems.TNT_STICK) return false;

		if (forceAllExcess) {
			return true;
		}

		// In mining/dismantle sessions, mined materials are not excess during regular task progress;
		// minions keep them in their backpack inventory.
		if (session != null && session.isDismantle()) {
			return false;
		}

		// If session requires this item or it is a raw ingredient for synthesis, it is not excess
		if (session != null) {
			for (var task : session.getTasks()) {
				if (!task.isCompleted()) {
					Item req = task.getBlueprintBlock().getRequiredItem();
					if (req == item || isSynthesisSource(item, req)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * Deposits a stack into a nearby container within range.
	 *
	 * @return The remaining stack that could not be deposited (or empty if fully stored).
	 */
	public static ItemStack depositStackIntoNearbyContainer(ItemStack stack, ServerWorld world, BlockPos searchCenter, ConstructionSession session, java.util.UUID ownerUuid) {
		if (stack.isEmpty() || world == null || searchCenter == null) {
			return stack;
		}

		BlockPos targetChestPos = findNearbyChestWithSpace(world, searchCenter, 24);
		if (targetChestPos != null) {
			BlockEntity be = world.getBlockEntity(targetChestPos);
			if (be instanceof Inventory containerInv) {
				for (int cSlot = 0; cSlot < containerInv.size(); cSlot++) {
					ItemStack destStack = containerInv.getStack(cSlot);
					if (destStack.isEmpty()) {
						containerInv.setStack(cSlot, stack.copy());
						stack.setCount(0);
						containerInv.markDirty();
						return ItemStack.EMPTY;
					} else if (ItemStack.areItemsAndComponentsEqual(destStack, stack)) {
						int transferable = Math.min(stack.getCount(), destStack.getMaxCount() - destStack.getCount());
						if (transferable > 0) {
							destStack.increment(transferable);
							stack.decrement(transferable);
							containerInv.markDirty();
							if (stack.isEmpty()) {
								return ItemStack.EMPTY;
							}
						}
					}
				}
			}
		}
		return stack;
	}

	private static boolean harvestWoodOrAgroForestry(
		MinionEntity minion,
		ServerWorld world,
		ConstructionSession session
	) {
		BlockPos minionPos = minion.getBlockPos();

		// 1. Scan for existing natural logs within 16 blocks
		for (BlockPos pos : BlockPos.iterateOutwards(minionPos, HARVEST_SEARCH_RADIUS, 8, HARVEST_SEARCH_RADIUS)) {
			BlockState state = world.getBlockState(pos);
			if (state.isIn(BlockTags.LOGS) && !isProtectedBlock(world, pos, session, minion.getOwnerUuid())) {
				if (!hasAdjacentLava(world, pos)) {
					fellLog(minion, world, pos.toImmutable(), state);
					return true;
				}
			}
		}

		// 2. Agro-Forestry: plant sapling and accelerate growth using bone meal
		Item saplingItem = findSaplingInInventory(minion.getInventory());
		if (saplingItem != null) {
			BlockPos plantPos = findPlantingPosition(world, minionPos, session, minion.getOwnerUuid());
			if (plantPos != null) {
				Block saplingBlock = Block.getBlockFromItem(saplingItem);
				if (saplingBlock != null && saplingBlock != Blocks.AIR) {
					world.setBlockState(plantPos, saplingBlock.getDefaultState(), Block.NOTIFY_ALL);
					consumeItemFromInventory(minion.getInventory(), saplingItem, 1);

					// Apply bone meal if minion has it
					if (hasItemInInventory(minion, Items.BONE_MEAL)) {
						BlockState currentSaplingState = world.getBlockState(plantPos);
						if (currentSaplingState.getBlock() instanceof Fertilizable fertilizable) {
							fertilizable.grow(world, world.random, plantPos, currentSaplingState);
							consumeItemFromInventory(minion.getInventory(), Items.BONE_MEAL, 1);

							world.playSound(null, plantPos, SoundEvents.ITEM_BONE_MEAL_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
							world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, plantPos.getX() + 0.5D, plantPos.getY() + 0.5D, plantPos.getZ() + 0.5D, 8, 0.3D, 0.3D, 0.3D, 0.05D);
						}
					}

					// Check if tree grew at or above plantPos
					BlockState resultState = world.getBlockState(plantPos);
					if (resultState.isIn(BlockTags.LOGS)) {
						fellLog(minion, world, plantPos, resultState);
						// Replenish a sapling for sustainable forestry
						minion.getInventory().addStack(new ItemStack(saplingItem, 1));
						return true;
					} else {
						// Even if not fully grown yet, minion gathers initial timber
						minion.getInventory().addStack(new ItemStack(Items.OAK_LOG, 2));
						return true;
					}
				}
			}
		}

		return false;
	}

	private static void fellLog(MinionEntity minion, ServerWorld world, BlockPos pos, BlockState state) {
		// Use loot-table-faithful drop computation so silk-touch axes and fortune yield correct items.
		BlockEntity be = world.getBlockEntity(pos);
		List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, be, minion, minion.getMainHandStack());
		world.breakBlock(pos, false, minion);

		if (drops.isEmpty()) {
			// Fallback: guaranteed at least one log unit for agro-forestry continuity
			Item fallback = state.getBlock().asItem();
			minion.getInventory().addStack(new ItemStack(fallback == Items.AIR ? Items.OAK_LOG : fallback, 1));
		} else {
			for (ItemStack drop : drops) {
				if (!drop.isEmpty()) {
					minion.getInventory().addStack(drop);
				}
			}
		}

		// Damage equipped axe if held
		ItemStack mainhand = minion.getMainHandStack();
		if (mainhand.getItem() instanceof AxeItem) {
			mainhand.damage(1, minion, EquipmentSlot.MAINHAND);
		}

		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 10, 0.2D, 0.2D, 0.2D, 0.1D);
		world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
	}

	private static boolean quarryNaturalStone(
		MinionEntity minion,
		ServerWorld world,
		Item requiredItem,
		ConstructionSession session
	) {
		return quarryNaturalStone(minion, world, requiredItem, 1, session);
	}

	private static boolean quarryNaturalStone(
		MinionEntity minion,
		ServerWorld world,
		Item requiredItem,
		int maxCount,
		ConstructionSession session
	) {
		BlockPos minionPos = minion.getBlockPos();
		int harvested = 0;

		for (BlockPos pos : BlockPos.iterateOutwards(minionPos, HARVEST_SEARCH_RADIUS, 10, HARVEST_SEARCH_RADIUS)) {
			BlockState state = world.getBlockState(pos);
			if (isNaturalStoneOrEarth(state) && matchesQuarryTarget(state, requiredItem) && !isProtectedBlock(world, pos, session, minion.getOwnerUuid())) {
				if (!hasAdjacentLava(world, pos)) {
					// Use loot-table-faithful drop computation (respects silk-touch pickaxes, fortune, etc.)
					BlockEntity be = world.getBlockEntity(pos);
					List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, be, minion, minion.getMainHandStack());
					world.breakBlock(pos, false, minion);

					if (drops.isEmpty()) {
						// Fallback to legacy resolution for no-loot-table edge cases
						Item dropItem = resolveQuarryDrop(state, requiredItem);
						minion.getInventory().addStack(new ItemStack(dropItem, 1));
					} else {
						for (ItemStack drop : drops) {
							if (!drop.isEmpty()) {
								minion.getInventory().addStack(drop);
							}
						}
					}

					// Damage pickaxe/shovel if held
					ItemStack mainhand = minion.getMainHandStack();
					if (mainhand.getItem() instanceof PickaxeItem || mainhand.getItem() instanceof ShovelItem) {
						mainhand.damage(1, minion, EquipmentSlot.MAINHAND);
					}

					world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.2D, 0.2D, 0.2D, 0.1D);
					world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
					harvested++;
					if (harvested >= maxCount) {
						return true;
					}
				}
			}
		}

		return harvested > 0;
	}

	private static boolean matchesQuarryTarget(BlockState state, Item requiredItem) {
		if (requiredItem == null) return isNaturalStoneOrEarth(state);

		if (requiredItem == Items.SAND || requiredItem == Items.SANDSTONE || requiredItem == Items.CUT_SANDSTONE
			|| requiredItem == Items.CHISELED_SANDSTONE || requiredItem == Items.GLASS || requiredItem == Items.GLASS_PANE) {
			return state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND) || state.isOf(Blocks.SANDSTONE) || state.isOf(Blocks.RED_SANDSTONE);
		}
		if (requiredItem == Items.RED_SAND || requiredItem == Items.RED_SANDSTONE) {
			return state.isOf(Blocks.RED_SAND) || state.isOf(Blocks.RED_SANDSTONE);
		}
		if (requiredItem == Items.GRAVEL || requiredItem == Items.FLINT) {
			return state.isOf(Blocks.GRAVEL);
		}
		if (requiredItem == Items.CLAY || requiredItem == Items.CLAY_BALL || requiredItem == Items.BRICK || requiredItem == Items.BRICKS
			|| requiredItem == Items.TERRACOTTA) {
			return state.isOf(Blocks.CLAY);
		}
		if (requiredItem == Items.DIRT) {
			return state.isOf(Blocks.DIRT);
		}
		if (requiredItem == Items.NETHERRACK || requiredItem == Items.NETHER_BRICK || requiredItem == Items.NETHER_BRICKS) {
			return state.isOf(Blocks.NETHERRACK);
		}
		if (requiredItem == Items.BASALT || requiredItem == Items.SMOOTH_BASALT || requiredItem == Items.POLISHED_BASALT) {
			return state.isOf(Blocks.BASALT);
		}
		if (requiredItem == Items.COAL) {
			return state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE);
		}
		if (requiredItem == Items.RAW_IRON || requiredItem == Items.IRON_INGOT || requiredItem == Items.IRON_BARS || requiredItem == Items.CHAIN || requiredItem == Items.IRON_BLOCK) {
			return state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE);
		}
		if (requiredItem == Items.RAW_COPPER || requiredItem == Items.COPPER_INGOT || requiredItem == Items.COPPER_BLOCK || requiredItem == Items.CUT_COPPER
			|| requiredItem == Items.CUT_COPPER_STAIRS || requiredItem == Items.CUT_COPPER_SLAB) {
			return state.isOf(Blocks.COPPER_ORE) || state.isOf(Blocks.DEEPSLATE_COPPER_ORE);
		}
		if (requiredItem == Items.RAW_GOLD || requiredItem == Items.GOLD_INGOT || requiredItem == Items.GOLD_BLOCK) {
			return state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE);
		}
		if (requiredItem == Items.QUARTZ || requiredItem == Items.QUARTZ_BLOCK) {
			return state.isOf(Blocks.NETHER_QUARTZ_ORE);
		}
		if (requiredItem == Items.DEEPSLATE || requiredItem == Items.COBBLED_DEEPSLATE || requiredItem == Items.POLISHED_DEEPSLATE
			|| requiredItem == Items.DEEPSLATE_BRICKS || requiredItem == Items.DEEPSLATE_TILES) {
			return state.isOf(Blocks.DEEPSLATE) || state.isOf(Blocks.COBBLED_DEEPSLATE);
		}
		if (requiredItem == Items.ANDESITE || requiredItem == Items.POLISHED_ANDESITE) {
			return state.isOf(Blocks.ANDESITE);
		}
		if (requiredItem == Items.DIORITE || requiredItem == Items.POLISHED_DIORITE) {
			return state.isOf(Blocks.DIORITE);
		}
		if (requiredItem == Items.GRANITE || requiredItem == Items.POLISHED_GRANITE) {
			return state.isOf(Blocks.GRANITE);
		}

		return state.isOf(Blocks.STONE) || state.isOf(Blocks.COBBLESTONE);
	}

	private static boolean isNaturalStoneOrEarth(BlockState state) {
		return state.isOf(Blocks.STONE)
			|| state.isOf(Blocks.COBBLESTONE)
			|| state.isOf(Blocks.DEEPSLATE)
			|| state.isOf(Blocks.COBBLED_DEEPSLATE)
			|| state.isOf(Blocks.ANDESITE)
			|| state.isOf(Blocks.DIORITE)
			|| state.isOf(Blocks.GRANITE)
			|| state.isOf(Blocks.DIRT)
			|| state.isOf(Blocks.SAND)
			|| state.isOf(Blocks.RED_SAND)
			|| state.isOf(Blocks.GRAVEL)
			|| state.isOf(Blocks.SANDSTONE)
			|| state.isOf(Blocks.RED_SANDSTONE)
			|| state.isOf(Blocks.CLAY)
			|| state.isOf(Blocks.NETHERRACK)
			|| state.isOf(Blocks.BASALT)
			|| state.isOf(Blocks.COAL_ORE)
			|| state.isOf(Blocks.DEEPSLATE_COAL_ORE)
			|| state.isOf(Blocks.IRON_ORE)
			|| state.isOf(Blocks.DEEPSLATE_IRON_ORE)
			|| state.isOf(Blocks.COPPER_ORE)
			|| state.isOf(Blocks.DEEPSLATE_COPPER_ORE)
			|| state.isOf(Blocks.GOLD_ORE)
			|| state.isOf(Blocks.DEEPSLATE_GOLD_ORE)
			|| state.isOf(Blocks.NETHER_QUARTZ_ORE);
	}

	private static Item resolveQuarryDrop(BlockState state, Item requiredItem) {
		if (state.isOf(Blocks.STONE)) {
			return requiredItem == Items.STONE ? Items.STONE : Items.COBBLESTONE;
		}
		if (state.isOf(Blocks.DEEPSLATE)) {
			return requiredItem == Items.DEEPSLATE ? Items.DEEPSLATE : Items.COBBLED_DEEPSLATE;
		}
		if (state.isOf(Blocks.CLAY)) {
			return Items.CLAY_BALL;
		}
		if (state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE)) {
			return Items.COAL;
		}
		if (state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE)) {
			return Items.RAW_IRON;
		}
		if (state.isOf(Blocks.COPPER_ORE) || state.isOf(Blocks.DEEPSLATE_COPPER_ORE)) {
			return Items.RAW_COPPER;
		}
		if (state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE)) {
			return Items.RAW_GOLD;
		}
		if (state.isOf(Blocks.NETHER_QUARTZ_ORE)) {
			return Items.QUARTZ;
		}
		return state.getBlock().asItem();
	}

	/**
	 * Comprehensive build protection rules:
	 * 1. Never break blocks inside the active blueprint or any other active session of the owner.
	 * 2. Never break human-crafted or processed architectural blocks (planks, bricks, slabs, glass, etc.).
	 * 3. Never break blocks within 12 blocks of beds, chests, or respawn anchors.
	 * 4. Never break indestructible blocks (bedrock, barriers).
	 */
	public static boolean isProtectedBlock(
		ServerWorld world,
		BlockPos pos,
		ConstructionSession session,
		UUID ownerUuid
	) {
		BlockState state = world.getBlockState(pos);

		// Bedrock and indestructible blocks
		if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.BARRIER) || state.getHardness(world, pos) < 0.0F) {
			return true;
		}

		// Inside current session bounding box or tasks
		if (session != null && session.getWorldBoundingBox().contains(pos)) {
			return true;
		}

		// Processed/crafted blocks check
		String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();
		if (isProcessedBlockId(blockId)) {
			return true;
		}

		// Within 12-block buffer of player beds, chests, or respawn anchors
		for (BlockPos neighbor : BlockPos.iterateOutwards(pos, BASE_BUFFER_RADIUS, 4, BASE_BUFFER_RADIUS)) {
			BlockState neighborState = world.getBlockState(neighbor);
			if (neighborState.isIn(BlockTags.BEDS)
				|| neighborState.isOf(Blocks.CHEST)
				|| neighborState.isOf(Blocks.TRAPPED_CHEST)
				|| neighborState.isOf(Blocks.BARREL)
				|| neighborState.isOf(Blocks.RESPAWN_ANCHOR)) {
				return true;
			}
		}

		return false;
	}

	private static boolean isProcessedBlockId(String path) {
		return path.contains("planks")
			|| path.contains("brick")
			|| path.contains("slab")
			|| path.contains("stair")
			|| path.contains("glass")
			|| path.contains("terracotta")
			|| path.contains("concrete")
			|| path.contains("wool")
			|| path.contains("carpet")
			|| path.contains("door")
			|| path.contains("trapdoor")
			|| path.contains("fence")
			|| path.contains("wall")
			|| path.contains("gate")
			|| path.contains("lantern")
			|| path.contains("torch")
			|| path.contains("chest")
			|| path.contains("barrel")
			|| path.contains("shulker")
			|| path.contains("furnace")
			|| path.contains("crafting_table")
			|| path.contains("stonecutter");
	}

	/**
	 * Hazard avoidance: skips any block whose 6 immediate orthogonal neighbors include lava.
	 */
	public static boolean hasAdjacentLava(ServerWorld world, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			BlockPos neighbor = pos.offset(dir);
			BlockState state = world.getBlockState(neighbor);
			if (state.isOf(Blocks.LAVA) || state.isOf(Blocks.LAVA_CAULDRON) || world.getFluidState(neighbor).isIn(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	private static void ensureAppropriateTool(MinionEntity minion, ServerWorld world, Item requiredItem) {
		ItemStack mainhand = minion.getMainHandStack();

		boolean needsPickaxe = isPickaxeResource(requiredItem);
		boolean needsShovel = isShovelResource(requiredItem);
		boolean needsAxe = isWoodResource(requiredItem);

		if (needsPickaxe && !(mainhand.getItem() instanceof PickaxeItem)) {
			// Check inventory for pickaxe
			int pickSlot = findToolSlot(minion.getInventory(), PickaxeItem.class);
			if (pickSlot != -1) {
				ItemStack pick = minion.getInventory().getStack(pickSlot);
				minion.equipStack(EquipmentSlot.MAINHAND, pick);
			} else {
				// Craft wooden or stone pickaxe
				craftPickaxe(minion, world);
			}
		} else if (needsShovel && !(mainhand.getItem() instanceof ShovelItem)) {
			int shovelSlot = findToolSlot(minion.getInventory(), ShovelItem.class);
			if (shovelSlot != -1) {
				ItemStack shovel = minion.getInventory().getStack(shovelSlot);
				minion.equipStack(EquipmentSlot.MAINHAND, shovel);
			} else {
				craftShovel(minion, world);
			}
		} else if (needsAxe && !(mainhand.getItem() instanceof AxeItem)) {
			int axeSlot = findToolSlot(minion.getInventory(), AxeItem.class);
			if (axeSlot != -1) {
				ItemStack axe = minion.getInventory().getStack(axeSlot);
				minion.equipStack(EquipmentSlot.MAINHAND, axe);
			} else {
				// Craft wooden axe
				craftAxe(minion, world);
			}
		}
	}

	public static boolean isShovelResource(Item item) {
		if (item == null) return false;
		return item == Items.DIRT
			|| item == Items.SAND
			|| item == Items.RED_SAND
			|| item == Items.GRAVEL
			|| item == Items.FLINT
			|| item == Items.CLAY
			|| item == Items.CLAY_BALL
			|| item == Items.SOUL_SAND
			|| item == Items.SOUL_SOIL
			|| item == Items.MUD;
	}

	public static boolean isPickaxeResource(Item item) {
		if (item == null) return false;
		if (isShovelResource(item) || isWoodResource(item)) return false;
		return isQuarryResource(item) || isTransformableResource(item) || isWorkstationResource(item);
	}

	private static void craftShovel(MinionEntity minion, ServerWorld world) {
		ensureSticksAndPlanks(minion);
		SimpleInventory inv = minion.getInventory();

		int cobble = countItemInInventory(inv, Items.COBBLESTONE);
		int sticks = countItemInInventory(inv, Items.STICK);
		if (cobble >= 1 && sticks >= 2) {
			consumeItemFromInventory(inv, Items.COBBLESTONE, 1);
			consumeItemFromInventory(inv, Items.STICK, 2);
			minion.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SHOVEL));
			playCraftingFeedback(minion, world, null);
			return;
		}

		int planks = countItemInInventory(inv, ItemTags.PLANKS);
		if (planks >= 1 && sticks >= 2) {
			consumeItemFromInventory(inv, ItemTags.PLANKS, 1);
			consumeItemFromInventory(inv, Items.STICK, 2);
			minion.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SHOVEL));
			playCraftingFeedback(minion, world, null);
		}
	}

	private static void craftPickaxe(MinionEntity minion, ServerWorld world) {
		ensureSticksAndPlanks(minion);
		SimpleInventory inv = minion.getInventory();

		// Prefer stone pickaxe if cobblestone available
		int cobble = countItemInInventory(inv, Items.COBBLESTONE);
		int sticks = countItemInInventory(inv, Items.STICK);
		if (cobble >= 3 && sticks >= 2) {
			consumeItemFromInventory(inv, Items.COBBLESTONE, 3);
			consumeItemFromInventory(inv, Items.STICK, 2);
			minion.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
			playCraftingFeedback(minion, world);
			return;
		}

		int planks = countItemInInventory(inv, ItemTags.PLANKS);
		if (planks >= 3 && sticks >= 2) {
			consumeItemFromInventory(inv, ItemTags.PLANKS, 3);
			consumeItemFromInventory(inv, Items.STICK, 2);
			minion.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
			playCraftingFeedback(minion, world);
		}
	}

	private static void craftAxe(MinionEntity minion, ServerWorld world) {
		ensureSticksAndPlanks(minion);
		SimpleInventory inv = minion.getInventory();

		int planks = countItemInInventory(inv, ItemTags.PLANKS);
		int sticks = countItemInInventory(inv, Items.STICK);
		if (planks >= 3 && sticks >= 2) {
			consumeItemFromInventory(inv, ItemTags.PLANKS, 3);
			consumeItemFromInventory(inv, Items.STICK, 2);
			minion.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
			playCraftingFeedback(minion, world);
		}
	}

	private static void ensureSticksAndPlanks(MinionEntity minion) {
		SimpleInventory inv = minion.getInventory();
		// If sticks < 2 and planks >= 2, craft 4 sticks
		if (countItemInInventory(inv, Items.STICK) < 2) {
			if (countItemInInventory(inv, ItemTags.PLANKS) >= 2) {
				consumeItemFromInventory(inv, ItemTags.PLANKS, 2);
				inv.addStack(new ItemStack(Items.STICK, 4));
			} else if (countItemInInventory(inv, ItemTags.LOGS) >= 1) {
				consumeItemFromInventory(inv, ItemTags.LOGS, 1);
				inv.addStack(new ItemStack(Items.OAK_PLANKS, 4));
				consumeItemFromInventory(inv, ItemTags.PLANKS, 2);
				inv.addStack(new ItemStack(Items.STICK, 4));
			}
		}

		// If planks < 3 and minion has logs, convert 1 log into 4 planks
		if (countItemInInventory(inv, ItemTags.PLANKS) < 3 && countItemInInventory(inv, ItemTags.LOGS) >= 1) {
			consumeItemFromInventory(inv, ItemTags.LOGS, 1);
			inv.addStack(new ItemStack(Items.OAK_PLANKS, 4));
		}
	}

	private static void playCraftingFeedback(MinionEntity minion, ServerWorld world) {
		playCraftingFeedback(minion, world, null);
	}

	private static void playCraftingFeedback(MinionEntity minion, ServerWorld world, BlockPos toolBlockPos) {
		BlockPos soundPos = toolBlockPos != null ? toolBlockPos : minion.getBlockPos();
		world.playSound(null, soundPos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 0.8F, 1.2F);
		world.spawnParticles(ParticleTypes.CRIT, soundPos.getX() + 0.5D, soundPos.getY() + 0.8D, soundPos.getZ() + 0.5D, 5, 0.2D, 0.2D, 0.2D, 0.05D);
	}

	private static void playSmeltingFeedback(MinionEntity minion, ServerWorld world, BlockPos toolBlockPos) {
		BlockPos soundPos = toolBlockPos != null ? toolBlockPos : minion.getBlockPos();
		world.playSound(null, soundPos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 0.9F, 1.0F);
		world.spawnParticles(ParticleTypes.FLAME, soundPos.getX() + 0.5D, soundPos.getY() + 0.6D, soundPos.getZ() + 0.5D, 6, 0.2D, 0.2D, 0.2D, 0.02D);
		world.spawnParticles(ParticleTypes.SMOKE, soundPos.getX() + 0.5D, soundPos.getY() + 0.9D, soundPos.getZ() + 0.5D, 4, 0.15D, 0.2D, 0.15D, 0.01D);
	}

	private static void playStonecutterFeedback(MinionEntity minion, ServerWorld world, BlockPos toolBlockPos) {
		BlockPos soundPos = toolBlockPos != null ? toolBlockPos : minion.getBlockPos();
		world.playSound(null, soundPos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundCategory.BLOCKS, 0.9F, 1.0F);
		world.spawnParticles(ParticleTypes.CRIT, soundPos.getX() + 0.5D, soundPos.getY() + 0.6D, soundPos.getZ() + 0.5D, 6, 0.2D, 0.2D, 0.2D, 0.05D);
	}

	private static void playSiftingFeedback(MinionEntity minion, ServerWorld world) {
		world.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.PLAYERS, 0.8F, 1.2F);
		world.spawnParticles(ParticleTypes.CRIT, minion.getX(), minion.getY() + 0.5D, minion.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.05D);
	}

	private static void convertLogsToPlanks(MinionEntity minion, Item requiredPlanks) {
		SimpleInventory inv = minion.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isIn(ItemTags.LOGS) && stack.getCount() > 0) {
				stack.decrement(1);
				inv.addStack(new ItemStack(requiredPlanks, 4));
				inv.markDirty();
				return;
			}
		}
	}

	private static BlockPos findPlantingPosition(ServerWorld world, BlockPos center, ConstructionSession session, UUID ownerUuid) {
		for (BlockPos pos : BlockPos.iterateOutwards(center, 8, 2, 8)) {
			BlockState below = world.getBlockState(pos.down());
			if ((below.isOf(Blocks.DIRT) || below.isOf(Blocks.GRASS_BLOCK)) && world.getBlockState(pos).isAir()) {
				// Ensure clearance above for tree canopy
				boolean clear = true;
				for (int y = 1; y <= 5; y++) {
					if (!world.getBlockState(pos.up(y)).isAir()) {
						clear = false;
						break;
					}
				}
				if (clear && !isProtectedBlock(world, pos, session, ownerUuid)) {
					return pos.toImmutable();
				}
			}
		}
		return null;
	}

	private static Item findSaplingInInventory(SimpleInventory inv) {
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isIn(ItemTags.SAPLINGS)) {
				return stack.getItem();
			}
		}
		return Items.OAK_SAPLING; // Default fallback sapling for sustainable forestry
	}

	private static int findToolSlot(SimpleInventory inv, Class<?> toolClass) {
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && toolClass.isInstance(stack.getItem())) {
				return i;
			}
		}
		return -1;
	}

	private static boolean hasItemInInventory(MinionEntity minion, Item item) {
		SimpleInventory inv = minion.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(item)) {
				return true;
			}
		}
		return false;
	}

	private static int countItemInInventory(SimpleInventory inv, Item item) {
		int count = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static int countItemInInventory(SimpleInventory inv, net.minecraft.registry.tag.TagKey<Item> tag) {
		int count = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isIn(tag)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void consumeItemFromInventory(SimpleInventory inv, Item item, int amount) {
		for (int i = 0; i < inv.size() && amount > 0; i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(item)) {
				int consumed = Math.min(stack.getCount(), amount);
				stack.decrement(consumed);
				amount -= consumed;
			}
		}
		inv.markDirty();
	}

	private static void consumeItemFromInventory(SimpleInventory inv, net.minecraft.registry.tag.TagKey<Item> tag, int amount) {
		for (int i = 0; i < inv.size() && amount > 0; i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isIn(tag)) {
				int consumed = Math.min(stack.getCount(), amount);
				stack.decrement(consumed);
				amount -= consumed;
			}
		}
		inv.markDirty();
	}

	public static boolean isWoodResource(Item item) {
		return item.getDefaultStack().isIn(ItemTags.LOGS)
			|| item.getDefaultStack().isIn(ItemTags.PLANKS)
			|| item.getDefaultStack().isIn(ItemTags.WOODEN_STAIRS)
			|| item.getDefaultStack().isIn(ItemTags.WOODEN_SLABS)
			|| item.getDefaultStack().isIn(ItemTags.WOODEN_DOORS);
	}

	/**
	 * Determines whether builder minions can autonomously quarry, harvest, smelt, synthesize,
	 * or craft the specified item in survival mode.
	 *
	 * @param item The item required for construction.
	 * @return True if builder minions have autonomous procurement routines for this resource.
	 */
	public static boolean isHarvestable(Item item) {
		if (item == null) return false;
		return isWoodResource(item)
			|| isQuarryResource(item)
			|| isTransformableResource(item)
			|| isMobProcurementResource(item)
			|| isWorkstationResource(item)
			|| isShovelResource(item)
			|| isPickaxeResource(item);
	}

	private static boolean isPlanksItem(Item item) {
		return item.getDefaultStack().isIn(ItemTags.PLANKS);
	}

	public static boolean isQuarryResource(Item item) {
		if (item == null) return false;
		return item == Items.COBBLESTONE
			|| item == Items.STONE
			|| item == Items.DEEPSLATE
			|| item == Items.COBBLED_DEEPSLATE
			|| item == Items.ANDESITE
			|| item == Items.DIORITE
			|| item == Items.GRANITE
			|| item == Items.DIRT
			|| item == Items.SAND
			|| item == Items.RED_SAND
			|| item == Items.GRAVEL
			|| item == Items.SANDSTONE
			|| item == Items.RED_SANDSTONE
			|| item == Items.CLAY
			|| item == Items.CLAY_BALL
			|| item == Items.COAL
			|| item == Items.RAW_IRON
			|| item == Items.RAW_COPPER
			|| item == Items.RAW_GOLD
			|| item == Items.NETHERRACK
			|| item == Items.BASALT
			|| item == Items.QUARTZ;
	}

	// =========================================================================
	// Smart Block Transformations & Tool Block Utilization
	// =========================================================================

	public static BlockPos findNearbyToolBlock(ServerWorld world, BlockPos center, int radius, Block... toolBlocks) {
		if (world == null || center == null || toolBlocks == null || toolBlocks.length == 0) {
			return null;
		}
		for (BlockPos pos : BlockPos.iterateOutwards(center, radius, 4, radius)) {
			BlockState state = world.getBlockState(pos);
			for (Block tb : toolBlocks) {
				if (state.isOf(tb)) {
					return pos.toImmutable();
				}
			}
		}
		return null;
	}

	private static boolean hasSmeltingFuel(SimpleInventory inv) {
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty()) {
				Item item = s.getItem();
				if (item == Items.COAL || item == Items.CHARCOAL || item == Items.LAVA_BUCKET || item == Items.STICK
					|| s.isIn(ItemTags.LOGS) || s.isIn(ItemTags.PLANKS)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean ensureSmeltingFuel(MinionEntity minion, ServerWorld world, ConstructionSession session) {
		SimpleInventory inv = minion.getInventory();
		if (hasSmeltingFuel(inv)) {
			return true;
		}

		if (countItemInInventory(inv, ItemTags.PLANKS) >= 1) {
			consumeItemFromInventory(inv, ItemTags.PLANKS, 1);
			inv.addStack(new ItemStack(Items.STICK, 4));
			return true;
		}

		if (harvestWoodOrAgroForestry(minion, world, session)) {
			if (countItemInInventory(inv, ItemTags.LOGS) >= 1) {
				consumeItemFromInventory(inv, ItemTags.LOGS, 1);
				inv.addStack(new ItemStack(Items.OAK_PLANKS, 4));
			}
			return true;
		}

		if (quarryNaturalStone(minion, world, Items.COAL, 1, session)) {
			return true;
		}

		return false;
	}

	private static void consumeSmeltingFuel(SimpleInventory inv) {
		if (countItemInInventory(inv, Items.STICK) >= 1) {
			consumeItemFromInventory(inv, Items.STICK, 1);
			return;
		}
		if (countItemInInventory(inv, ItemTags.PLANKS) >= 1) {
			consumeItemFromInventory(inv, ItemTags.PLANKS, 1);
			return;
		}
		if (countItemInInventory(inv, ItemTags.LOGS) >= 1) {
			consumeItemFromInventory(inv, ItemTags.LOGS, 1);
			return;
		}
		if (countItemInInventory(inv, Items.CHARCOAL) >= 1) {
			consumeItemFromInventory(inv, Items.CHARCOAL, 1);
			return;
		}
		if (countItemInInventory(inv, Items.COAL) >= 1) {
			consumeItemFromInventory(inv, Items.COAL, 1);
			return;
		}
		if (countItemInInventory(inv, Items.LAVA_BUCKET) >= 1) {
			consumeItemFromInventory(inv, Items.LAVA_BUCKET, 1);
			inv.addStack(new ItemStack(Items.BUCKET, 1));
		}
	}

	private static boolean executeSmelt(
		MinionEntity minion,
		ServerWorld world,
		Item inputItem,
		int inputCount,
		Item outputItem,
		int outputCount,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();
		if (countItemInInventory(inv, inputItem) < inputCount) {
			return false;
		}
		if (!ensureSmeltingFuel(minion, world, session)) {
			return false;
		}
		consumeSmeltingFuel(inv);

		BlockPos furnacePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER);
		if (furnacePos == null) {
			ensureFurnaceWorkstation(minion, world, session);
		}

		consumeItemFromInventory(inv, inputItem, inputCount);
		inv.addStack(new ItemStack(outputItem, outputCount));
		playSmeltingFeedback(minion, world, furnacePos);
		return true;
	}

	private static boolean ensureFurnaceWorkstation(MinionEntity minion, ServerWorld world, ConstructionSession session) {
		SimpleInventory inv = minion.getInventory();
		if (countItemInInventory(inv, Items.FURNACE) >= 1 || countItemInInventory(inv, Items.BLAST_FURNACE) >= 1 || countItemInInventory(inv, Items.SMOKER) >= 1) {
			return true;
		}
		if (countItemInInventory(inv, Items.COBBLESTONE) < 8) {
			quarryNaturalStone(minion, world, Items.COBBLESTONE, 8, session);
		}
		if (countItemInInventory(inv, Items.COBBLESTONE) >= 8) {
			consumeItemFromInventory(inv, Items.COBBLESTONE, 8);
			inv.addStack(new ItemStack(Items.FURNACE, 1));
			return true;
		}
		return false;
	}

	private static boolean executeStonecut(
		MinionEntity minion,
		ServerWorld world,
		Item inputItem,
		int inputCount,
		Item outputItem,
		int outputCount
	) {
		SimpleInventory inv = minion.getInventory();
		if (countItemInInventory(inv, inputItem) < inputCount) {
			return false;
		}
		BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
		consumeItemFromInventory(inv, inputItem, inputCount);
		inv.addStack(new ItemStack(outputItem, outputCount));
		playStonecutterFeedback(minion, world, cutterPos);
		return true;
	}

	private static Item findItemInInventory(SimpleInventory inv, net.minecraft.registry.tag.TagKey<Item> tag) {
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isIn(tag)) {
				return stack.getItem();
			}
		}
		return null;
	}

	public static boolean isTransformableResource(Item item) {
		if (item == null) return false;
		return isSmeltableBuildingResource(item)
			|| isSandstoneResource(item)
			|| isStoneBrickResource(item)
			|| isDeepslateDerivedResource(item)
			|| isBrickResource(item)
			|| isGlassResource(item)
			|| isNetherBrickResource(item)
			|| isQuartzDerivedResource(item)
			|| isBasaltDerivedResource(item)
			|| isPolishedStoneResource(item)
			|| isMetalDerivativeResource(item)
			|| isWoodenDerivative(item)
			|| item == Items.FLINT
			|| item == Items.CHARCOAL
			|| item == Items.TORCH
			|| item == Items.SOUL_TORCH
			|| item == Items.WHITE_CARPET
			|| item.getDefaultStack().isIn(ItemTags.WOOL_CARPETS);
	}

	public static boolean isSmeltableBuildingResource(Item item) {
		if (item == null) return false;
		return item == Items.SMOOTH_STONE
			|| item == Items.STONE
			|| item == Items.DEEPSLATE
			|| item == Items.GLASS
			|| item == Items.SMOOTH_SANDSTONE
			|| item == Items.SMOOTH_RED_SANDSTONE
			|| item == Items.CRACKED_STONE_BRICKS
			|| item == Items.CRACKED_DEEPSLATE_BRICKS
			|| item == Items.SMOOTH_BASALT
			|| item == Items.SMOOTH_QUARTZ
			|| item == Items.TERRACOTTA
			|| item == Items.BRICK
			|| item == Items.CHARCOAL
			|| item == Items.IRON_INGOT
			|| item == Items.COPPER_INGOT
			|| item == Items.GOLD_INGOT
			|| item == Items.NETHER_BRICK;
	}

	public static boolean isSandstoneResource(Item item) {
		if (item == null) return false;
		return item == Items.SANDSTONE || item == Items.RED_SANDSTONE || isSandstoneDerivative(item);
	}

	public static boolean isStoneBrickResource(Item item) {
		if (item == null) return false;
		return item == Items.STONE_BRICKS || item == Items.CRACKED_STONE_BRICKS || item == Items.CHISELED_STONE_BRICKS
			|| item == Items.STONE_BRICK_STAIRS || item == Items.STONE_BRICK_SLAB || item == Items.STONE_BRICK_WALL
			|| item == Items.MOSSY_STONE_BRICKS || item == Items.MOSSY_STONE_BRICK_STAIRS || item == Items.MOSSY_STONE_BRICK_SLAB || item == Items.MOSSY_STONE_BRICK_WALL
			|| item == Items.COBBLESTONE_STAIRS || item == Items.COBBLESTONE_SLAB || item == Items.COBBLESTONE_WALL || item == Items.MOSSY_COBBLESTONE
			|| item == Items.STONE_STAIRS || item == Items.STONE_SLAB;
	}

	public static boolean isDeepslateDerivedResource(Item item) {
		if (item == null) return false;
		return item == Items.DEEPSLATE || item == Items.POLISHED_DEEPSLATE || item == Items.DEEPSLATE_BRICKS || item == Items.DEEPSLATE_TILES
			|| item == Items.CRACKED_DEEPSLATE_BRICKS || item == Items.CRACKED_DEEPSLATE_TILES || item == Items.CHISELED_DEEPSLATE
			|| item == Items.POLISHED_DEEPSLATE_STAIRS || item == Items.POLISHED_DEEPSLATE_SLAB || item == Items.POLISHED_DEEPSLATE_WALL
			|| item == Items.DEEPSLATE_BRICK_STAIRS || item == Items.DEEPSLATE_BRICK_SLAB || item == Items.DEEPSLATE_BRICK_WALL
			|| item == Items.DEEPSLATE_TILE_STAIRS || item == Items.DEEPSLATE_TILE_SLAB || item == Items.DEEPSLATE_TILE_WALL
			|| item == Items.COBBLED_DEEPSLATE_STAIRS || item == Items.COBBLED_DEEPSLATE_SLAB || item == Items.COBBLED_DEEPSLATE_WALL;
	}

	public static boolean isBrickResource(Item item) {
		if (item == null) return false;
		return item == Items.BRICK || item == Items.BRICKS || item == Items.BRICK_STAIRS || item == Items.BRICK_SLAB || item == Items.BRICK_WALL
			|| item == Items.CLAY_BALL || item == Items.CLAY || item == Items.TERRACOTTA;
	}

	public static boolean isGlassResource(Item item) {
		if (item == null) return false;
		return item == Items.GLASS || item == Items.GLASS_PANE || item == Items.TINTED_GLASS;
	}

	public static boolean isNetherBrickResource(Item item) {
		if (item == null) return false;
		return item == Items.NETHER_BRICK || item == Items.NETHER_BRICKS || item == Items.NETHER_BRICK_STAIRS || item == Items.NETHER_BRICK_SLAB
			|| item == Items.NETHER_BRICK_WALL || item == Items.NETHER_BRICK_FENCE || item == Items.CHISELED_NETHER_BRICKS || item == Items.CRACKED_NETHER_BRICKS;
	}

	public static boolean isQuartzDerivedResource(Item item) {
		if (item == null) return false;
		return item == Items.QUARTZ_BLOCK || item == Items.SMOOTH_QUARTZ || item == Items.QUARTZ_STAIRS || item == Items.QUARTZ_SLAB
			|| item == Items.QUARTZ_BRICKS || item == Items.QUARTZ_PILLAR || item == Items.CHISELED_QUARTZ_BLOCK
			|| item == Items.SMOOTH_QUARTZ_STAIRS || item == Items.SMOOTH_QUARTZ_SLAB;
	}

	public static boolean isBasaltDerivedResource(Item item) {
		if (item == null) return false;
		return item == Items.POLISHED_BASALT || item == Items.SMOOTH_BASALT;
	}

	public static boolean isPolishedStoneResource(Item item) {
		if (item == null) return false;
		return item == Items.POLISHED_ANDESITE || item == Items.POLISHED_ANDESITE_STAIRS || item == Items.POLISHED_ANDESITE_SLAB
			|| item == Items.POLISHED_DIORITE || item == Items.POLISHED_DIORITE_STAIRS || item == Items.POLISHED_DIORITE_SLAB
			|| item == Items.POLISHED_GRANITE || item == Items.POLISHED_GRANITE_STAIRS || item == Items.POLISHED_GRANITE_SLAB;
	}

	public static boolean isMetalDerivativeResource(Item item) {
		if (item == null) return false;
		return item == Items.IRON_INGOT || item == Items.IRON_BARS || item == Items.CHAIN || item == Items.IRON_BLOCK
			|| item == Items.COPPER_INGOT || item == Items.COPPER_BLOCK || item == Items.CUT_COPPER || item == Items.CUT_COPPER_STAIRS || item == Items.CUT_COPPER_SLAB
			|| item == Items.GOLD_INGOT || item == Items.GOLD_BLOCK;
	}

	public static boolean isWoodenDerivative(Item item) {
		if (item == null) return false;
		ItemStack stack = item.getDefaultStack();
		return stack.isIn(ItemTags.WOODEN_STAIRS)
			|| stack.isIn(ItemTags.WOODEN_SLABS)
			|| stack.isIn(ItemTags.WOODEN_DOORS)
			|| stack.isIn(ItemTags.WOODEN_TRAPDOORS)
			|| stack.isIn(ItemTags.WOODEN_FENCES)
			|| stack.isIn(ItemTags.FENCE_GATES);
	}

	private static boolean isSandstoneDerivative(Item item) {
		return isNormalSandstoneDerivative(item) || isRedSandstoneDerivative(item);
	}

	private static boolean isNormalSandstoneDerivative(Item item) {
		return item == Items.CUT_SANDSTONE || item == Items.CHISELED_SANDSTONE || item == Items.SMOOTH_SANDSTONE
			|| item == Items.SANDSTONE_STAIRS || item == Items.SANDSTONE_SLAB || item == Items.SANDSTONE_WALL
			|| item == Items.CUT_SANDSTONE_SLAB || item == Items.SMOOTH_SANDSTONE_STAIRS || item == Items.SMOOTH_SANDSTONE_SLAB;
	}

	private static boolean isRedSandstoneDerivative(Item item) {
		return item == Items.CUT_RED_SANDSTONE || item == Items.CHISELED_RED_SANDSTONE || item == Items.SMOOTH_RED_SANDSTONE
			|| item == Items.RED_SANDSTONE_STAIRS || item == Items.RED_SANDSTONE_SLAB || item == Items.RED_SANDSTONE_WALL
			|| item == Items.CUT_RED_SANDSTONE_SLAB || item == Items.SMOOTH_RED_SANDSTONE_STAIRS || item == Items.SMOOTH_RED_SANDSTONE_SLAB;
	}

	public static boolean trySmartTransformation(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		if (minion == null || world == null || targetItem == null) return false;
		SimpleInventory inv = minion.getInventory();

		// 1. Flint: 3 Gravel -> 1 Flint
		if (targetItem == Items.FLINT) {
			if (countItemInInventory(inv, Items.GRAVEL) < 3) {
				quarryNaturalStone(minion, world, Items.GRAVEL, 3, session);
			}
			if (countItemInInventory(inv, Items.GRAVEL) >= 3) {
				consumeItemFromInventory(inv, Items.GRAVEL, 3);
				inv.addStack(new ItemStack(Items.FLINT, 1));
				playSiftingFeedback(minion, world);
				return true;
			}
			return false;
		}

		// 2. Charcoal: Smelt Log in Furnace
		if (targetItem == Items.CHARCOAL) {
			if (countItemInInventory(inv, ItemTags.LOGS) < 1) {
				harvestWoodOrAgroForestry(minion, world, session);
			}
			Item log = findItemInInventory(inv, ItemTags.LOGS);
			if (log != null) {
				return executeSmelt(minion, world, log, 1, Items.CHARCOAL, 1, session);
			}
			return false;
		}

		// 3. Torches: Coal/Charcoal + Stick -> 4 Torches
		if (targetItem == Items.TORCH) {
			ensureSticksAndPlanks(minion);
			if (countItemInInventory(inv, Items.COAL) < 1 && countItemInInventory(inv, Items.CHARCOAL) < 1) {
				if (!trySmartTransformation(minion, world, Items.CHARCOAL, session)) {
					quarryNaturalStone(minion, world, Items.COAL, 1, session);
				}
			}
			Item fuelItem = countItemInInventory(inv, Items.COAL) >= 1 ? Items.COAL : (countItemInInventory(inv, Items.CHARCOAL) >= 1 ? Items.CHARCOAL : null);
			if (fuelItem != null && countItemInInventory(inv, Items.STICK) >= 1) {
				consumeItemFromInventory(inv, fuelItem, 1);
				consumeItemFromInventory(inv, Items.STICK, 1);
				inv.addStack(new ItemStack(Items.TORCH, 4));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		// 4. Smooth Stone: Smelt Stone in Furnace
		if (targetItem == Items.SMOOTH_STONE) {
			if (countItemInInventory(inv, Items.STONE) < 1) {
				if (countItemInInventory(inv, Items.COBBLESTONE) < 1) {
					quarryNaturalStone(minion, world, Items.COBBLESTONE, 1, session);
				}
				if (countItemInInventory(inv, Items.COBBLESTONE) >= 1) {
					executeSmelt(minion, world, Items.COBBLESTONE, 1, Items.STONE, 1, session);
				}
			}
			if (countItemInInventory(inv, Items.STONE) >= 1) {
				return executeSmelt(minion, world, Items.STONE, 1, Items.SMOOTH_STONE, 1, session);
			}
			return false;
		}

		// 5. Stone: Smelt Cobblestone in Furnace
		if (targetItem == Items.STONE) {
			if (countItemInInventory(inv, Items.COBBLESTONE) < 1) {
				quarryNaturalStone(minion, world, Items.COBBLESTONE, 1, session);
			}
			if (countItemInInventory(inv, Items.COBBLESTONE) >= 1) {
				return executeSmelt(minion, world, Items.COBBLESTONE, 1, Items.STONE, 1, session);
			}
			return false;
		}

		// 6. Sandstone: 4 Sand -> 1 Sandstone
		if (targetItem == Items.SANDSTONE) {
			if (countItemInInventory(inv, Items.SAND) < 4) {
				quarryNaturalStone(minion, world, Items.SAND, 4 - countItemInInventory(inv, Items.SAND), session);
			}
			if (countItemInInventory(inv, Items.SAND) >= 4) {
				consumeItemFromInventory(inv, Items.SAND, 4);
				inv.addStack(new ItemStack(Items.SANDSTONE, 1));
				BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
				playCraftingFeedback(minion, world, tablePos);
				return true;
			}
			return false;
		}

		// 7. Red Sandstone: 4 Red Sand -> 1 Red Sandstone
		if (targetItem == Items.RED_SANDSTONE) {
			if (countItemInInventory(inv, Items.RED_SAND) < 4) {
				quarryNaturalStone(minion, world, Items.RED_SAND, 4 - countItemInInventory(inv, Items.RED_SAND), session);
			}
			if (countItemInInventory(inv, Items.RED_SAND) >= 4) {
				consumeItemFromInventory(inv, Items.RED_SAND, 4);
				inv.addStack(new ItemStack(Items.RED_SANDSTONE, 1));
				BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
				playCraftingFeedback(minion, world, tablePos);
				return true;
			}
			return false;
		}

		// 8. Sandstone Variants
		if (isSandstoneDerivative(targetItem)) {
			Item baseSandstone = isRedSandstoneDerivative(targetItem) ? Items.RED_SANDSTONE : Items.SANDSTONE;
			if (countItemInInventory(inv, baseSandstone) < 1) {
				trySmartTransformation(minion, world, baseSandstone, session);
			}
			if (countItemInInventory(inv, baseSandstone) >= 1) {
				return craftSandstoneDerivative(minion, world, targetItem, baseSandstone, session);
			}
			return false;
		}

		// 9. Glass: Smelt Sand in Furnace
		if (targetItem == Items.GLASS) {
			if (countItemInInventory(inv, Items.SAND) < 1 && countItemInInventory(inv, Items.RED_SAND) < 1) {
				quarryNaturalStone(minion, world, Items.SAND, 1, session);
			}
			Item sandItem = countItemInInventory(inv, Items.SAND) >= 1 ? Items.SAND : (countItemInInventory(inv, Items.RED_SAND) >= 1 ? Items.RED_SAND : null);
			if (sandItem != null) {
				return executeSmelt(minion, world, sandItem, 1, Items.GLASS, 1, session);
			}
			return false;
		}

		// 10. Glass Pane: 6 Glass -> 16 Glass Panes
		if (targetItem == Items.GLASS_PANE) {
			if (countItemInInventory(inv, Items.GLASS) < 6) {
				trySmartTransformation(minion, world, Items.GLASS, session);
			}
			if (countItemInInventory(inv, Items.GLASS) >= 1) {
				int glassToUse = Math.min(countItemInInventory(inv, Items.GLASS), 6);
				int panesProduced = glassToUse >= 6 ? 16 : glassToUse * 2;
				consumeItemFromInventory(inv, Items.GLASS, glassToUse);
				inv.addStack(new ItemStack(Items.GLASS_PANE, panesProduced));
				BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
				playCraftingFeedback(minion, world, tablePos);
				return true;
			}
			return false;
		}

		// 11. Stone Bricks & Derivatives
		if (isStoneBrickResource(targetItem)) {
			return craftStoneBrickDerivative(minion, world, targetItem, session);
		}

		// 12. Bricks & Clay
		if (isBrickResource(targetItem)) {
			return craftBrickDerivative(minion, world, targetItem, session);
		}

		// 13. Deepslate Derivatives
		if (isDeepslateDerivedResource(targetItem)) {
			return craftDeepslateDerivative(minion, world, targetItem, session);
		}

		// 14. Nether Bricks Derivatives
		if (isNetherBrickResource(targetItem)) {
			return craftNetherBrickDerivative(minion, world, targetItem, session);
		}

		// 15. Quartz Derivatives
		if (isQuartzDerivedResource(targetItem)) {
			return craftQuartzDerivative(minion, world, targetItem, session);
		}

		// 16. Basalt Derivatives
		if (isBasaltDerivedResource(targetItem)) {
			return craftBasaltDerivative(minion, world, targetItem, session);
		}

		// 17. Polished Andesite / Diorite / Granite
		if (isPolishedStoneResource(targetItem)) {
			return craftPolishedStoneDerivative(minion, world, targetItem, session);
		}

		// 18. Metals (Iron Ingot, Iron Bars, Chains, etc.)
		if (isMetalDerivativeResource(targetItem)) {
			return craftMetalDerivative(minion, world, targetItem, session);
		}

		// 19. Wooden Derivatives (Stairs, Slabs, Fences, Gates, Doors, Trapdoors)
		if (isWoodenDerivative(targetItem)) {
			return craftWoodenDerivative(minion, world, targetItem, session);
		}

		// 20. Wool to Carpet
		if (targetItem == Items.WHITE_CARPET || targetItem.getDefaultStack().isIn(ItemTags.WOOL_CARPETS)) {
			Item woolItem = findItemInInventory(inv, ItemTags.WOOL);
			if (woolItem == null) {
				synthesizeMaterial(inv, Items.WHITE_WOOL);
				woolItem = findItemInInventory(inv, ItemTags.WOOL);
			}
			if (woolItem != null && countItemInInventory(inv, woolItem) >= 2) {
				consumeItemFromInventory(inv, woolItem, 2);
				inv.addStack(new ItemStack(targetItem, 3));
				playCraftingFeedback(minion, world, null);
				return true;
			}
		}

		return false;
	}

	private static boolean craftSandstoneDerivative(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		Item baseSandstone,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();

		if (targetItem == Items.SMOOTH_SANDSTONE || targetItem == Items.SMOOTH_RED_SANDSTONE) {
			if (countItemInInventory(inv, baseSandstone) < 1) {
				trySmartTransformation(minion, world, baseSandstone, session);
			}
			if (countItemInInventory(inv, baseSandstone) >= 1) {
				return executeSmelt(minion, world, baseSandstone, 1, targetItem, 1, session);
			}
			return false;
		}

		if (targetItem == Items.CUT_SANDSTONE || targetItem == Items.CUT_RED_SANDSTONE) {
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, baseSandstone) >= 1) {
				return executeStonecut(minion, world, baseSandstone, 1, targetItem, 1);
			}
			if (countItemInInventory(inv, baseSandstone) >= 1) {
				consumeItemFromInventory(inv, baseSandstone, 1);
				inv.addStack(new ItemStack(targetItem, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.CHISELED_SANDSTONE || targetItem == Items.CHISELED_RED_SANDSTONE) {
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, baseSandstone) >= 1) {
				return executeStonecut(minion, world, baseSandstone, 1, targetItem, 1);
			}
			if (countItemInInventory(inv, baseSandstone) >= 1) {
				consumeItemFromInventory(inv, baseSandstone, 1);
				inv.addStack(new ItemStack(targetItem, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (countItemInInventory(inv, baseSandstone) >= 1) {
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			int yield = isSlabItem(targetItem) ? 2 : 1;
			if (cutterPos != null) {
				return executeStonecut(minion, world, baseSandstone, 1, targetItem, yield);
			}
			consumeItemFromInventory(inv, baseSandstone, 1);
			inv.addStack(new ItemStack(targetItem, yield));
			BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
			playCraftingFeedback(minion, world, tablePos);
			return true;
		}

		return false;
	}

	private static boolean craftStoneBrickDerivative(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();

		if (targetItem == Items.CRACKED_STONE_BRICKS) {
			if (countItemInInventory(inv, Items.STONE_BRICKS) < 1) {
				craftStoneBrickDerivative(minion, world, Items.STONE_BRICKS, session);
			}
			if (countItemInInventory(inv, Items.STONE_BRICKS) >= 1) {
				return executeSmelt(minion, world, Items.STONE_BRICKS, 1, Items.CRACKED_STONE_BRICKS, 1, session);
			}
			return false;
		}

		if (targetItem == Items.STONE_BRICKS) {
			if (countItemInInventory(inv, Items.STONE) < 4) {
				if (countItemInInventory(inv, Items.COBBLESTONE) < 4) {
					quarryNaturalStone(minion, world, Items.COBBLESTONE, 4, session);
				}
				while (countItemInInventory(inv, Items.COBBLESTONE) >= 1 && countItemInInventory(inv, Items.STONE) < 4) {
					if (!executeSmelt(minion, world, Items.COBBLESTONE, 1, Items.STONE, 1, session)) {
						break;
					}
				}
			}

			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, Items.STONE) >= 1) {
				return executeStonecut(minion, world, Items.STONE, 1, Items.STONE_BRICKS, 1);
			}

			if (countItemInInventory(inv, Items.STONE) >= 4) {
				consumeItemFromInventory(inv, Items.STONE, 4);
				inv.addStack(new ItemStack(Items.STONE_BRICKS, 4));
				BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
				playCraftingFeedback(minion, world, tablePos);
				return true;
			} else if (countItemInInventory(inv, Items.STONE) >= 1) {
				consumeItemFromInventory(inv, Items.STONE, 1);
				inv.addStack(new ItemStack(Items.STONE_BRICKS, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.CHISELED_STONE_BRICKS) {
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (countItemInInventory(inv, Items.STONE_BRICKS) < 1) {
				craftStoneBrickDerivative(minion, world, Items.STONE_BRICKS, session);
			}
			if (cutterPos != null && countItemInInventory(inv, Items.STONE_BRICKS) >= 1) {
				return executeStonecut(minion, world, Items.STONE_BRICKS, 1, Items.CHISELED_STONE_BRICKS, 1);
			}
			if (countItemInInventory(inv, Items.STONE_BRICKS) >= 1) {
				consumeItemFromInventory(inv, Items.STONE_BRICKS, 1);
				inv.addStack(new ItemStack(Items.CHISELED_STONE_BRICKS, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.STONE_BRICK_STAIRS || targetItem == Items.STONE_BRICK_SLAB || targetItem == Items.STONE_BRICK_WALL
			|| targetItem == Items.MOSSY_STONE_BRICK_STAIRS || targetItem == Items.MOSSY_STONE_BRICK_SLAB || targetItem == Items.MOSSY_STONE_BRICK_WALL) {
			if (countItemInInventory(inv, Items.STONE_BRICKS) < 1) {
				craftStoneBrickDerivative(minion, world, Items.STONE_BRICKS, session);
			}
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, Items.STONE_BRICKS) >= 1) {
				int yield = targetItem == Items.STONE_BRICK_SLAB || targetItem == Items.MOSSY_STONE_BRICK_SLAB ? 2 : 1;
				return executeStonecut(minion, world, Items.STONE_BRICKS, 1, targetItem, yield);
			}
			if (countItemInInventory(inv, Items.STONE_BRICKS) >= 1) {
				consumeItemFromInventory(inv, Items.STONE_BRICKS, 1);
				int yield = targetItem == Items.STONE_BRICK_SLAB || targetItem == Items.MOSSY_STONE_BRICK_SLAB ? 2 : 1;
				inv.addStack(new ItemStack(targetItem, yield));
				BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
				playCraftingFeedback(minion, world, tablePos);
				return true;
			}
		}

		if (targetItem == Items.COBBLESTONE_STAIRS || targetItem == Items.COBBLESTONE_SLAB || targetItem == Items.COBBLESTONE_WALL || targetItem == Items.MOSSY_COBBLESTONE) {
			if (countItemInInventory(inv, Items.COBBLESTONE) < 1) {
				quarryNaturalStone(minion, world, Items.COBBLESTONE, 1, session);
			}
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, Items.COBBLESTONE) >= 1) {
				int yield = targetItem == Items.COBBLESTONE_SLAB ? 2 : 1;
				return executeStonecut(minion, world, Items.COBBLESTONE, 1, targetItem, yield);
			}
			if (countItemInInventory(inv, Items.COBBLESTONE) >= 1) {
				consumeItemFromInventory(inv, Items.COBBLESTONE, 1);
				int yield = targetItem == Items.COBBLESTONE_SLAB ? 2 : 1;
				inv.addStack(new ItemStack(targetItem, yield));
				BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
				playCraftingFeedback(minion, world, tablePos);
				return true;
			}
		}

		if (targetItem == Items.STONE_STAIRS || targetItem == Items.STONE_SLAB) {
			if (countItemInInventory(inv, Items.STONE) < 1) {
				trySmartTransformation(minion, world, Items.STONE, session);
			}
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, Items.STONE) >= 1) {
				int yield = targetItem == Items.STONE_SLAB ? 2 : 1;
				return executeStonecut(minion, world, Items.STONE, 1, targetItem, yield);
			}
			if (countItemInInventory(inv, Items.STONE) >= 1) {
				consumeItemFromInventory(inv, Items.STONE, 1);
				int yield = targetItem == Items.STONE_SLAB ? 2 : 1;
				inv.addStack(new ItemStack(targetItem, yield));
				playCraftingFeedback(minion, world, null);
				return true;
			}
		}

		return false;
	}

	private static boolean craftBrickDerivative(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();

		if (targetItem == Items.CLAY_BALL) {
			return quarryNaturalStone(minion, world, Items.CLAY_BALL, 4, session);
		}

		if (targetItem == Items.CLAY) {
			if (countItemInInventory(inv, Items.CLAY_BALL) < 4) {
				quarryNaturalStone(minion, world, Items.CLAY_BALL, 4, session);
			}
			if (countItemInInventory(inv, Items.CLAY_BALL) >= 4) {
				consumeItemFromInventory(inv, Items.CLAY_BALL, 4);
				inv.addStack(new ItemStack(Items.CLAY, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.BRICK) {
			if (countItemInInventory(inv, Items.CLAY_BALL) < 1) {
				quarryNaturalStone(minion, world, Items.CLAY_BALL, 1, session);
			}
			if (countItemInInventory(inv, Items.CLAY_BALL) >= 1) {
				return executeSmelt(minion, world, Items.CLAY_BALL, 1, Items.BRICK, 1, session);
			}
			return false;
		}

		if (targetItem == Items.BRICKS) {
			while (countItemInInventory(inv, Items.BRICK) < 4) {
				if (countItemInInventory(inv, Items.CLAY_BALL) < 1) {
					if (!quarryNaturalStone(minion, world, Items.CLAY_BALL, 4, session)) {
						break;
					}
				}
				if (!executeSmelt(minion, world, Items.CLAY_BALL, 1, Items.BRICK, 1, session)) {
					break;
				}
			}

			if (countItemInInventory(inv, Items.BRICK) >= 4) {
				consumeItemFromInventory(inv, Items.BRICK, 4);
				inv.addStack(new ItemStack(Items.BRICKS, 1));
				BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
				playCraftingFeedback(minion, world, tablePos);
				return true;
			} else if (countItemInInventory(inv, Items.BRICK) >= 1) {
				consumeItemFromInventory(inv, Items.BRICK, 1);
				inv.addStack(new ItemStack(Items.BRICKS, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.TERRACOTTA) {
			if (countItemInInventory(inv, Items.CLAY) < 1) {
				craftBrickDerivative(minion, world, Items.CLAY, session);
			}
			if (countItemInInventory(inv, Items.CLAY) >= 1) {
				return executeSmelt(minion, world, Items.CLAY, 1, Items.TERRACOTTA, 1, session);
			}
			return false;
		}

		if (targetItem == Items.BRICK_STAIRS || targetItem == Items.BRICK_SLAB || targetItem == Items.BRICK_WALL) {
			if (countItemInInventory(inv, Items.BRICKS) < 1) {
				craftBrickDerivative(minion, world, Items.BRICKS, session);
			}
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, Items.BRICKS) >= 1) {
				int yield = targetItem == Items.BRICK_SLAB ? 2 : 1;
				return executeStonecut(minion, world, Items.BRICKS, 1, targetItem, yield);
			}
			if (countItemInInventory(inv, Items.BRICKS) >= 1) {
				consumeItemFromInventory(inv, Items.BRICKS, 1);
				int yield = targetItem == Items.BRICK_SLAB ? 2 : 1;
				inv.addStack(new ItemStack(targetItem, yield));
				BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
				playCraftingFeedback(minion, world, tablePos);
				return true;
			}
		}

		return false;
	}

	private static boolean craftDeepslateDerivative(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();

		if (targetItem == Items.DEEPSLATE) {
			if (countItemInInventory(inv, Items.COBBLED_DEEPSLATE) < 1) {
				quarryNaturalStone(minion, world, Items.COBBLED_DEEPSLATE, 1, session);
			}
			if (countItemInInventory(inv, Items.COBBLED_DEEPSLATE) >= 1) {
				return executeSmelt(minion, world, Items.COBBLED_DEEPSLATE, 1, Items.DEEPSLATE, 1, session);
			}
			return false;
		}

		if (targetItem == Items.POLISHED_DEEPSLATE) {
			if (countItemInInventory(inv, Items.COBBLED_DEEPSLATE) < 1) {
				quarryNaturalStone(minion, world, Items.COBBLED_DEEPSLATE, 4, session);
			}
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, Items.COBBLED_DEEPSLATE) >= 1) {
				return executeStonecut(minion, world, Items.COBBLED_DEEPSLATE, 1, Items.POLISHED_DEEPSLATE, 1);
			}
			if (countItemInInventory(inv, Items.COBBLED_DEEPSLATE) >= 1) {
				consumeItemFromInventory(inv, Items.COBBLED_DEEPSLATE, 1);
				inv.addStack(new ItemStack(Items.POLISHED_DEEPSLATE, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.DEEPSLATE_BRICKS) {
			if (countItemInInventory(inv, Items.POLISHED_DEEPSLATE) < 1) {
				craftDeepslateDerivative(minion, world, Items.POLISHED_DEEPSLATE, session);
			}
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, Items.POLISHED_DEEPSLATE) >= 1) {
				return executeStonecut(minion, world, Items.POLISHED_DEEPSLATE, 1, Items.DEEPSLATE_BRICKS, 1);
			}
			if (countItemInInventory(inv, Items.POLISHED_DEEPSLATE) >= 1) {
				consumeItemFromInventory(inv, Items.POLISHED_DEEPSLATE, 1);
				inv.addStack(new ItemStack(Items.DEEPSLATE_BRICKS, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.DEEPSLATE_TILES) {
			if (countItemInInventory(inv, Items.DEEPSLATE_BRICKS) < 1) {
				craftDeepslateDerivative(minion, world, Items.DEEPSLATE_BRICKS, session);
			}
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, Items.DEEPSLATE_BRICKS) >= 1) {
				return executeStonecut(minion, world, Items.DEEPSLATE_BRICKS, 1, Items.DEEPSLATE_TILES, 1);
			}
			if (countItemInInventory(inv, Items.DEEPSLATE_BRICKS) >= 1) {
				consumeItemFromInventory(inv, Items.DEEPSLATE_BRICKS, 1);
				inv.addStack(new ItemStack(Items.DEEPSLATE_TILES, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.CRACKED_DEEPSLATE_BRICKS) {
			if (countItemInInventory(inv, Items.DEEPSLATE_BRICKS) < 1) {
				craftDeepslateDerivative(minion, world, Items.DEEPSLATE_BRICKS, session);
			}
			if (countItemInInventory(inv, Items.DEEPSLATE_BRICKS) >= 1) {
				return executeSmelt(minion, world, Items.DEEPSLATE_BRICKS, 1, Items.CRACKED_DEEPSLATE_BRICKS, 1, session);
			}
			return false;
		}

		Item baseBlock = resolveDeepslateBaseBlock(targetItem);
		if (countItemInInventory(inv, baseBlock) < 1) {
			craftDeepslateDerivative(minion, world, baseBlock, session);
		}
		if (countItemInInventory(inv, baseBlock) >= 1) {
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			int yield = isSlabItem(targetItem) ? 2 : 1;
			if (cutterPos != null) {
				return executeStonecut(minion, world, baseBlock, 1, targetItem, yield);
			}
			consumeItemFromInventory(inv, baseBlock, 1);
			inv.addStack(new ItemStack(targetItem, yield));
			playCraftingFeedback(minion, world, null);
			return true;
		}

		return false;
	}

	private static Item resolveDeepslateBaseBlock(Item item) {
		String path = Registries.ITEM.getId(item).getPath();
		if (path.contains("tile")) return Items.DEEPSLATE_TILES;
		if (path.contains("brick")) return Items.DEEPSLATE_BRICKS;
		if (path.contains("polished")) return Items.POLISHED_DEEPSLATE;
		return Items.COBBLED_DEEPSLATE;
	}

	private static boolean isSlabItem(Item item) {
		return item.getDefaultStack().isIn(ItemTags.SLABS);
	}

	private static boolean craftNetherBrickDerivative(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();

		if (targetItem == Items.NETHER_BRICK) {
			if (countItemInInventory(inv, Items.NETHERRACK) < 1) {
				quarryNaturalStone(minion, world, Items.NETHERRACK, 1, session);
			}
			if (countItemInInventory(inv, Items.NETHERRACK) >= 1) {
				return executeSmelt(minion, world, Items.NETHERRACK, 1, Items.NETHER_BRICK, 1, session);
			}
			return false;
		}

		if (targetItem == Items.NETHER_BRICKS) {
			while (countItemInInventory(inv, Items.NETHER_BRICK) < 4) {
				if (countItemInInventory(inv, Items.NETHERRACK) < 1) {
					if (!quarryNaturalStone(minion, world, Items.NETHERRACK, 4, session)) break;
				}
				if (!executeSmelt(minion, world, Items.NETHERRACK, 1, Items.NETHER_BRICK, 1, session)) break;
			}
			if (countItemInInventory(inv, Items.NETHER_BRICK) >= 4) {
				consumeItemFromInventory(inv, Items.NETHER_BRICK, 4);
				inv.addStack(new ItemStack(Items.NETHER_BRICKS, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			} else if (countItemInInventory(inv, Items.NETHER_BRICK) >= 1) {
				consumeItemFromInventory(inv, Items.NETHER_BRICK, 1);
				inv.addStack(new ItemStack(Items.NETHER_BRICKS, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (countItemInInventory(inv, Items.NETHER_BRICKS) < 1) {
			craftNetherBrickDerivative(minion, world, Items.NETHER_BRICKS, session);
		}
		if (countItemInInventory(inv, Items.NETHER_BRICKS) >= 1) {
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			int yield = isSlabItem(targetItem) ? 2 : 1;
			if (cutterPos != null) {
				return executeStonecut(minion, world, Items.NETHER_BRICKS, 1, targetItem, yield);
			}
			consumeItemFromInventory(inv, Items.NETHER_BRICKS, 1);
			inv.addStack(new ItemStack(targetItem, yield));
			playCraftingFeedback(minion, world, null);
			return true;
		}
		return false;
	}

	private static boolean craftQuartzDerivative(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();

		if (targetItem == Items.SMOOTH_QUARTZ) {
			if (countItemInInventory(inv, Items.QUARTZ_BLOCK) < 1) {
				craftQuartzDerivative(minion, world, Items.QUARTZ_BLOCK, session);
			}
			if (countItemInInventory(inv, Items.QUARTZ_BLOCK) >= 1) {
				return executeSmelt(minion, world, Items.QUARTZ_BLOCK, 1, Items.SMOOTH_QUARTZ, 1, session);
			}
			return false;
		}

		if (targetItem == Items.QUARTZ_BLOCK) {
			if (countItemInInventory(inv, Items.QUARTZ) < 4) {
				quarryNaturalStone(minion, world, Items.QUARTZ, 4, session);
			}
			if (countItemInInventory(inv, Items.QUARTZ) >= 4) {
				consumeItemFromInventory(inv, Items.QUARTZ, 4);
				inv.addStack(new ItemStack(Items.QUARTZ_BLOCK, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			} else if (countItemInInventory(inv, Items.QUARTZ) >= 1) {
				consumeItemFromInventory(inv, Items.QUARTZ, 1);
				inv.addStack(new ItemStack(Items.QUARTZ_BLOCK, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (countItemInInventory(inv, Items.QUARTZ_BLOCK) < 1) {
			craftQuartzDerivative(minion, world, Items.QUARTZ_BLOCK, session);
		}
		if (countItemInInventory(inv, Items.QUARTZ_BLOCK) >= 1) {
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			int yield = isSlabItem(targetItem) ? 2 : 1;
			if (cutterPos != null) {
				return executeStonecut(minion, world, Items.QUARTZ_BLOCK, 1, targetItem, yield);
			}
			consumeItemFromInventory(inv, Items.QUARTZ_BLOCK, 1);
			inv.addStack(new ItemStack(targetItem, yield));
			playCraftingFeedback(minion, world, null);
			return true;
		}
		return false;
	}

	private static boolean craftBasaltDerivative(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();

		if (targetItem == Items.SMOOTH_BASALT) {
			if (countItemInInventory(inv, Items.BASALT) < 1) {
				quarryNaturalStone(minion, world, Items.BASALT, 1, session);
			}
			if (countItemInInventory(inv, Items.BASALT) >= 1) {
				return executeSmelt(minion, world, Items.BASALT, 1, Items.SMOOTH_BASALT, 1, session);
			}
			return false;
		}

		if (targetItem == Items.POLISHED_BASALT) {
			if (countItemInInventory(inv, Items.BASALT) < 1) {
				quarryNaturalStone(minion, world, Items.BASALT, 1, session);
			}
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, Items.BASALT) >= 1) {
				return executeStonecut(minion, world, Items.BASALT, 1, Items.POLISHED_BASALT, 1);
			}
			if (countItemInInventory(inv, Items.BASALT) >= 1) {
				consumeItemFromInventory(inv, Items.BASALT, 1);
				inv.addStack(new ItemStack(Items.POLISHED_BASALT, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}
		return false;
	}

	private static boolean craftPolishedStoneDerivative(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();
		Item rawItem = resolveRawStoneForPolished(targetItem);
		Item polishedItem = resolvePolishedBaseForDerivative(targetItem);

		if (countItemInInventory(inv, polishedItem) < 1) {
			if (countItemInInventory(inv, rawItem) < 1) {
				quarryNaturalStone(minion, world, rawItem, 4, session);
			}
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, rawItem) >= 1) {
				executeStonecut(minion, world, rawItem, 1, polishedItem, 1);
			} else if (countItemInInventory(inv, rawItem) >= 1) {
				consumeItemFromInventory(inv, rawItem, 1);
				inv.addStack(new ItemStack(polishedItem, 1));
				playCraftingFeedback(minion, world, null);
			}
		}

		if (targetItem == polishedItem) {
			return countItemInInventory(inv, polishedItem) >= 1;
		}

		if (countItemInInventory(inv, polishedItem) >= 1) {
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			int yield = isSlabItem(targetItem) ? 2 : 1;
			if (cutterPos != null) {
				return executeStonecut(minion, world, polishedItem, 1, targetItem, yield);
			}
			consumeItemFromInventory(inv, polishedItem, 1);
			inv.addStack(new ItemStack(targetItem, yield));
			playCraftingFeedback(minion, world, null);
			return true;
		}
		return false;
	}

	private static Item resolveRawStoneForPolished(Item item) {
		String path = Registries.ITEM.getId(item).getPath();
		if (path.contains("andesite")) return Items.ANDESITE;
		if (path.contains("diorite")) return Items.DIORITE;
		return Items.GRANITE;
	}

	private static Item resolvePolishedBaseForDerivative(Item item) {
		String path = Registries.ITEM.getId(item).getPath();
		if (path.contains("andesite")) return Items.POLISHED_ANDESITE;
		if (path.contains("diorite")) return Items.POLISHED_DIORITE;
		return Items.POLISHED_GRANITE;
	}

	private static boolean craftMetalDerivative(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();

		if (targetItem == Items.IRON_INGOT) {
			if (countItemInInventory(inv, Items.RAW_IRON) < 1 && countItemInInventory(inv, Items.IRON_ORE) < 1) {
				quarryNaturalStone(minion, world, Items.RAW_IRON, 1, session);
			}
			Item raw = countItemInInventory(inv, Items.RAW_IRON) >= 1 ? Items.RAW_IRON : (countItemInInventory(inv, Items.IRON_ORE) >= 1 ? Items.IRON_ORE : null);
			if (raw != null) {
				return executeSmelt(minion, world, raw, 1, Items.IRON_INGOT, 1, session);
			}
			return false;
		}

		if (targetItem == Items.IRON_BARS) {
			if (countItemInInventory(inv, Items.IRON_INGOT) < 1) {
				craftMetalDerivative(minion, world, Items.IRON_INGOT, session);
			}
			if (countItemInInventory(inv, Items.IRON_INGOT) >= 1) {
				int consumed = Math.min(countItemInInventory(inv, Items.IRON_INGOT), 6);
				int yield = consumed >= 6 ? 16 : consumed * 2;
				consumeItemFromInventory(inv, Items.IRON_INGOT, consumed);
				inv.addStack(new ItemStack(Items.IRON_BARS, yield));
				BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
				playCraftingFeedback(minion, world, tablePos);
				return true;
			}
			return false;
		}

		if (targetItem == Items.CHAIN) {
			if (countItemInInventory(inv, Items.IRON_INGOT) < 1) {
				craftMetalDerivative(minion, world, Items.IRON_INGOT, session);
			}
			if (countItemInInventory(inv, Items.IRON_INGOT) >= 1) {
				consumeItemFromInventory(inv, Items.IRON_INGOT, 1);
				inv.addStack(new ItemStack(Items.CHAIN, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.COPPER_INGOT) {
			if (countItemInInventory(inv, Items.RAW_COPPER) < 1 && countItemInInventory(inv, Items.COPPER_ORE) < 1) {
				quarryNaturalStone(minion, world, Items.RAW_COPPER, 1, session);
			}
			Item raw = countItemInInventory(inv, Items.RAW_COPPER) >= 1 ? Items.RAW_COPPER : (countItemInInventory(inv, Items.COPPER_ORE) >= 1 ? Items.COPPER_ORE : null);
			if (raw != null) {
				return executeSmelt(minion, world, raw, 1, Items.COPPER_INGOT, 1, session);
			}
			return false;
		}

		if (targetItem == Items.GOLD_INGOT) {
			if (countItemInInventory(inv, Items.RAW_GOLD) < 1 && countItemInInventory(inv, Items.GOLD_ORE) < 1) {
				quarryNaturalStone(minion, world, Items.RAW_GOLD, 1, session);
			}
			Item raw = countItemInInventory(inv, Items.RAW_GOLD) >= 1 ? Items.RAW_GOLD : (countItemInInventory(inv, Items.GOLD_ORE) >= 1 ? Items.GOLD_ORE : null);
			if (raw != null) {
				return executeSmelt(minion, world, raw, 1, Items.GOLD_INGOT, 1, session);
			}
			return false;
		}

		if (targetItem == Items.IRON_BLOCK) {
			if (countItemInInventory(inv, Items.IRON_INGOT) < 9) {
				while (countItemInInventory(inv, Items.IRON_INGOT) < 9) {
					if (!craftMetalDerivative(minion, world, Items.IRON_INGOT, session)) break;
				}
			}
			if (countItemInInventory(inv, Items.IRON_INGOT) >= 9) {
				consumeItemFromInventory(inv, Items.IRON_INGOT, 9);
				inv.addStack(new ItemStack(Items.IRON_BLOCK, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			} else if (countItemInInventory(inv, Items.IRON_INGOT) >= 1) {
				consumeItemFromInventory(inv, Items.IRON_INGOT, countItemInInventory(inv, Items.IRON_INGOT));
				inv.addStack(new ItemStack(Items.IRON_BLOCK, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.GOLD_BLOCK) {
			if (countItemInInventory(inv, Items.GOLD_INGOT) < 9) {
				while (countItemInInventory(inv, Items.GOLD_INGOT) < 9) {
					if (!craftMetalDerivative(minion, world, Items.GOLD_INGOT, session)) break;
				}
			}
			if (countItemInInventory(inv, Items.GOLD_INGOT) >= 9) {
				consumeItemFromInventory(inv, Items.GOLD_INGOT, 9);
				inv.addStack(new ItemStack(Items.GOLD_BLOCK, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			} else if (countItemInInventory(inv, Items.GOLD_INGOT) >= 1) {
				consumeItemFromInventory(inv, Items.GOLD_INGOT, countItemInInventory(inv, Items.GOLD_INGOT));
				inv.addStack(new ItemStack(Items.GOLD_BLOCK, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.COPPER_BLOCK) {
			if (countItemInInventory(inv, Items.COPPER_INGOT) < 9) {
				while (countItemInInventory(inv, Items.COPPER_INGOT) < 9) {
					if (!craftMetalDerivative(minion, world, Items.COPPER_INGOT, session)) break;
				}
			}
			if (countItemInInventory(inv, Items.COPPER_INGOT) >= 9) {
				consumeItemFromInventory(inv, Items.COPPER_INGOT, 9);
				inv.addStack(new ItemStack(Items.COPPER_BLOCK, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			} else if (countItemInInventory(inv, Items.COPPER_INGOT) >= 1) {
				consumeItemFromInventory(inv, Items.COPPER_INGOT, countItemInInventory(inv, Items.COPPER_INGOT));
				inv.addStack(new ItemStack(Items.COPPER_BLOCK, 1));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		if (targetItem == Items.CUT_COPPER || targetItem == Items.CUT_COPPER_STAIRS || targetItem == Items.CUT_COPPER_SLAB) {
			if (countItemInInventory(inv, Items.COPPER_BLOCK) < 1) {
				craftMetalDerivative(minion, world, Items.COPPER_BLOCK, session);
			}
			BlockPos cutterPos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.STONECUTTER);
			if (cutterPos != null && countItemInInventory(inv, Items.COPPER_BLOCK) >= 1) {
				int yield = targetItem == Items.CUT_COPPER_SLAB ? 2 : (targetItem == Items.CUT_COPPER ? 4 : 1);
				return executeStonecut(minion, world, Items.COPPER_BLOCK, 1, targetItem, yield);
			}
			if (countItemInInventory(inv, Items.COPPER_BLOCK) >= 1) {
				consumeItemFromInventory(inv, Items.COPPER_BLOCK, 1);
				int yield = targetItem == Items.CUT_COPPER_SLAB ? 2 : (targetItem == Items.CUT_COPPER ? 4 : 1);
				inv.addStack(new ItemStack(targetItem, yield));
				playCraftingFeedback(minion, world, null);
				return true;
			}
			return false;
		}

		return false;
	}

	public static Item resolvePlanksForWoodenItem(Item item) {
		if (item == null) return Items.OAK_PLANKS;
		String path = Registries.ITEM.getId(item).getPath();
		if (path.startsWith("spruce_")) return Items.SPRUCE_PLANKS;
		if (path.startsWith("birch_")) return Items.BIRCH_PLANKS;
		if (path.startsWith("jungle_")) return Items.JUNGLE_PLANKS;
		if (path.startsWith("acacia_")) return Items.ACACIA_PLANKS;
		if (path.startsWith("dark_oak_")) return Items.DARK_OAK_PLANKS;
		if (path.startsWith("mangrove_")) return Items.MANGROVE_PLANKS;
		if (path.startsWith("cherry_")) return Items.CHERRY_PLANKS;
		if (path.startsWith("bamboo_")) return Items.BAMBOO_PLANKS;
		if (path.startsWith("crimson_")) return Items.CRIMSON_PLANKS;
		if (path.startsWith("warped_")) return Items.WARPED_PLANKS;
		return Items.OAK_PLANKS;
	}

	private static boolean craftWoodenDerivative(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		SimpleInventory inv = minion.getInventory();
		Item planksItem = resolvePlanksForWoodenItem(targetItem);

		int neededPlanks = 6;
		if (targetItem.getDefaultStack().isIn(ItemTags.WOODEN_SLABS)) {
			neededPlanks = 3;
		} else if (targetItem.getDefaultStack().isIn(ItemTags.WOODEN_FENCES)) {
			neededPlanks = 4;
		} else if (targetItem.getDefaultStack().isIn(ItemTags.FENCE_GATES)) {
			neededPlanks = 2;
		}

		ensurePlanks(minion, world, session, neededPlanks);
		if (countItemInInventory(inv, planksItem) < neededPlanks && countItemInInventory(inv, ItemTags.PLANKS) >= neededPlanks) {
			planksItem = findItemInInventory(inv, ItemTags.PLANKS);
		}

		if (planksItem != null && countItemInInventory(inv, planksItem) >= neededPlanks) {
			consumeItemFromInventory(inv, planksItem, neededPlanks);

			int yield = 4;
			if (targetItem.getDefaultStack().isIn(ItemTags.WOODEN_SLABS)) {
				yield = 6;
			} else if (targetItem.getDefaultStack().isIn(ItemTags.WOODEN_DOORS)) {
				yield = 3;
			} else if (targetItem.getDefaultStack().isIn(ItemTags.WOODEN_TRAPDOORS)) {
				yield = 2;
			} else if (targetItem.getDefaultStack().isIn(ItemTags.WOODEN_FENCES)) {
				yield = 3;
				consumeItemFromInventory(inv, Items.STICK, 2);
			} else if (targetItem.getDefaultStack().isIn(ItemTags.FENCE_GATES)) {
				yield = 1;
				consumeItemFromInventory(inv, Items.STICK, 4);
			}

			inv.addStack(new ItemStack(targetItem, yield));
			BlockPos tablePos = findNearbyToolBlock(world, minion.getBlockPos(), 16, Blocks.CRAFTING_TABLE);
			playCraftingFeedback(minion, world, tablePos);
			return true;
		}
		return false;
	}

	// =========================================================================
	// Mob Material Procurement, Hunting Contracts, & Synthesis
	// =========================================================================

	/**
	 * Checks if an item can be procured from mob drops or synthesized from mob drops.
	 */
	public static boolean isMobProcurementResource(Item item) {
		if (item == null) return false;
		return isWoolResource(item)
			|| item == Items.STRING
			|| item == Items.SPIDER_EYE
			|| item == Items.FERMENTED_SPIDER_EYE
			|| item == Items.COBWEB
			|| item == Items.BONE
			|| item == Items.BONE_MEAL
			|| item == Items.BONE_BLOCK
			|| item == Items.ARROW
			|| item == Items.SLIME_BALL
			|| item == Items.SLIME_BLOCK
			|| item == Items.STICKY_PISTON
			|| item == Items.MAGMA_CREAM
			|| item == Items.MAGMA_BLOCK
			|| item == Items.LEATHER
			|| item == Items.ITEM_FRAME
			|| item == Items.GLOW_ITEM_FRAME
			|| item == Items.INK_SAC
			|| item == Items.GLOW_INK_SAC
			|| item == Items.FEATHER
			|| item == Items.BLAZE_ROD
			|| item == Items.BLAZE_POWDER
			|| item == Items.ENDER_PEARL
			|| item == Items.ENDER_EYE
			|| item == Items.GUNPOWDER
			|| item == Items.ROTTEN_FLESH
			|| item == Items.PRISMARINE_SHARD
			|| item == Items.PRISMARINE_CRYSTALS
			|| item == Items.PRISMARINE
			|| item == Items.PRISMARINE_BRICKS
			|| item == Items.DARK_PRISMARINE
			|| item == Items.SEA_LANTERN
			|| item == Items.RABBIT_HIDE
			|| item == Items.RABBIT_FOOT
			|| item == Items.PHANTOM_MEMBRANE
			|| item == Items.SHULKER_SHELL
			|| item == Items.SHULKER_BOX;
	}

	/**
	 * Checks if the specified item is any colored wool block.
	 */
	public static boolean isWoolResource(Item item) {
		if (item == null) return false;
		return item == Items.WHITE_WOOL || item == Items.ORANGE_WOOL || item == Items.MAGENTA_WOOL
			|| item == Items.LIGHT_BLUE_WOOL || item == Items.YELLOW_WOOL || item == Items.LIME_WOOL
			|| item == Items.PINK_WOOL || item == Items.GRAY_WOOL || item == Items.LIGHT_GRAY_WOOL
			|| item == Items.CYAN_WOOL || item == Items.PURPLE_WOOL || item == Items.BLUE_WOOL
			|| item == Items.BROWN_WOOL || item == Items.GREEN_WOOL || item == Items.RED_WOOL
			|| item == Items.BLACK_WOOL || item.getDefaultStack().isIn(ItemTags.WOOL);
	}

	/**
	 * Verifies whether a given living entity is a valid mob source for the requested item.
	 */
	public static boolean isTargetMobForResource(LivingEntity entity, Item requiredItem) {
		if (entity == null || requiredItem == null) return false;

		// Wool items: Sheep or Spiders (via string synthesis)
		if (isWoolResource(requiredItem)) {
			return entity instanceof SheepEntity || entity instanceof SpiderEntity || entity instanceof CaveSpiderEntity;
		}
		// String & Spider Eye
		if (requiredItem == Items.STRING || requiredItem == Items.SPIDER_EYE || requiredItem == Items.FERMENTED_SPIDER_EYE || requiredItem == Items.COBWEB) {
			return entity instanceof SpiderEntity || entity instanceof CaveSpiderEntity;
		}
		// Bones, Bone Meal, Bone Block, Arrows
		if (requiredItem == Items.BONE || requiredItem == Items.BONE_MEAL || requiredItem == Items.BONE_BLOCK || requiredItem == Items.ARROW) {
			return entity instanceof AbstractSkeletonEntity;
		}
		// Slime ball, Slime block, Sticky piston
		if (requiredItem == Items.SLIME_BALL || requiredItem == Items.SLIME_BLOCK || requiredItem == Items.STICKY_PISTON) {
			return entity instanceof SlimeEntity;
		}
		// Magma cream, Magma block
		if (requiredItem == Items.MAGMA_CREAM || requiredItem == Items.MAGMA_BLOCK) {
			return entity instanceof MagmaCubeEntity;
		}
		// Leather & Item Frame
		if (requiredItem == Items.LEATHER || requiredItem == Items.ITEM_FRAME || requiredItem == Items.BEEF
			|| requiredItem == Items.LEATHER_HELMET || requiredItem == Items.LEATHER_CHESTPLATE
			|| requiredItem == Items.LEATHER_LEGGINGS || requiredItem == Items.LEATHER_BOOTS) {
			return entity instanceof CowEntity || entity instanceof MooshroomEntity || entity instanceof HoglinEntity
				|| (entity instanceof AbstractHorseEntity horse && !horse.isTame());
		}
		// Ink & Glow Ink
		if (requiredItem == Items.INK_SAC || requiredItem == Items.BLACK_DYE) {
			return entity instanceof SquidEntity;
		}
		if (requiredItem == Items.GLOW_INK_SAC || requiredItem == Items.GLOW_ITEM_FRAME) {
			return entity instanceof GlowSquidEntity;
		}
		// Feathers & Chicken
		if (requiredItem == Items.FEATHER || requiredItem == Items.CHICKEN || requiredItem == Items.COOKED_CHICKEN) {
			return entity instanceof ChickenEntity || entity instanceof ParrotEntity;
		}
		// Blaze Rod & Powder
		if (requiredItem == Items.BLAZE_ROD || requiredItem == Items.BLAZE_POWDER) {
			return entity instanceof BlazeEntity;
		}
		// Ender Pearl & Eye
		if (requiredItem == Items.ENDER_PEARL || requiredItem == Items.ENDER_EYE) {
			return entity instanceof EndermanEntity;
		}
		// Gunpowder & TNT
		if (requiredItem == Items.GUNPOWDER || requiredItem == Items.TNT) {
			return entity instanceof CreeperEntity || entity instanceof GhastEntity || entity instanceof WitchEntity;
		}
		// Rotten Flesh
		if (requiredItem == Items.ROTTEN_FLESH) {
			return entity instanceof ZombieEntity;
		}
		// Prismarine
		if (requiredItem == Items.PRISMARINE_SHARD || requiredItem == Items.PRISMARINE_CRYSTALS
			|| requiredItem == Items.PRISMARINE || requiredItem == Items.PRISMARINE_BRICKS
			|| requiredItem == Items.DARK_PRISMARINE || requiredItem == Items.SEA_LANTERN) {
			return entity instanceof GuardianEntity;
		}
		// Rabbit
		if (requiredItem == Items.RABBIT_HIDE || requiredItem == Items.RABBIT_FOOT || requiredItem == Items.RABBIT) {
			return entity instanceof RabbitEntity;
		}
		// Phantom Membrane
		if (requiredItem == Items.PHANTOM_MEMBRANE) {
			return entity instanceof PhantomEntity;
		}
		// Shulker Shell
		if (requiredItem == Items.SHULKER_SHELL || requiredItem == Items.SHULKER_BOX) {
			return entity instanceof ShulkerEntity;
		}

		return false;
	}

	/**
	 * Validates whether an entity is a safe target for autonomous mob hunting contracts.
	 * Strictly protects player pets, named mobs, villagers, iron golems, allays, and allied minions.
	 */
	public static boolean isSafeHuntTarget(LivingEntity target, UUID ownerUuid) {
		if (target == null || !target.isAlive()) {
			return false;
		}
		// Never hunt players
		if (target instanceof PlayerEntity) {
			return false;
		}
		// Never hunt allied or player minions
		if (target instanceof MinionEntity) {
			return false;
		}
		// Never hunt named mobs (player pets / tagged entities)
		if (target.hasCustomName()) {
			return false;
		}
		// Never hunt tamed pets (wolves, cats, parrots)
		if (target instanceof TameableEntity tameable && tameable.isTamed()) {
			return false;
		}
		// Never hunt tamed horses / mounts
		if (target instanceof AbstractHorseEntity horse && horse.isTame()) {
			return false;
		}
		// Never hunt villagers or wandering traders
		if (target instanceof MerchantEntity) {
			return false;
		}
		// Never hunt village defenders (iron golems, snow golems)
		if (target instanceof IronGolemEntity || target instanceof SnowGolemEntity) {
			return false;
		}
		// Never hunt allays or armor stands
		if (target instanceof AllayEntity || target instanceof ArmorStandEntity) {
			return false;
		}

		return true;
	}

	/**
	 * Checks if sourceItem can be synthesized into targetItem.
	 */
	public static boolean isSynthesisSource(Item sourceItem, Item targetItem) {
		if (sourceItem == null || targetItem == null) return false;
		if (sourceItem == Items.BONE && (targetItem == Items.BONE_MEAL || targetItem == Items.BONE_BLOCK)) return true;
		if (sourceItem == Items.BONE_MEAL && targetItem == Items.BONE_BLOCK) return true;
		if (sourceItem == Items.STRING && (isWoolResource(targetItem) || targetItem == Items.COBWEB)) return true;
		if (sourceItem == Items.SLIME_BALL && (targetItem == Items.SLIME_BLOCK || targetItem == Items.STICKY_PISTON)) return true;
		if (sourceItem == Items.MAGMA_CREAM && targetItem == Items.MAGMA_BLOCK) return true;
		if (sourceItem == Items.BLAZE_ROD && (targetItem == Items.BLAZE_POWDER || targetItem == Items.ENDER_EYE)) return true;
		if (sourceItem == Items.BLAZE_POWDER && targetItem == Items.ENDER_EYE) return true;
		if (sourceItem == Items.ENDER_PEARL && targetItem == Items.ENDER_EYE) return true;
		if (sourceItem == Items.PRISMARINE_SHARD && (targetItem == Items.PRISMARINE || targetItem == Items.PRISMARINE_BRICKS || targetItem == Items.SEA_LANTERN)) return true;
		if (sourceItem == Items.PRISMARINE_CRYSTALS && targetItem == Items.SEA_LANTERN) return true;
		if (sourceItem == Items.RABBIT_HIDE && targetItem == Items.LEATHER) return true;
		return false;
	}

	/**
	 * Synthesizes refined construction materials from raw mob ingredients present in the inventory.
	 */
	public static boolean synthesizeMaterial(SimpleInventory inv, Item targetItem) {
		if (inv == null || targetItem == null) return false;

		// 1. Bone Meal: 1 Bone -> 3 Bone Meal
		if (targetItem == Items.BONE_MEAL) {
			if (countItemInInventory(inv, Items.BONE) >= 1) {
				consumeItemFromInventory(inv, Items.BONE, 1);
				inv.addStack(new ItemStack(Items.BONE_MEAL, 3));
				return true;
			}
		}

		// 2. Bone Block: 9 Bone Meal -> 1 Bone Block (or 3 Bones -> 9 Bone Meal -> 1 Bone Block)
		if (targetItem == Items.BONE_BLOCK) {
			if (countItemInInventory(inv, Items.BONE_MEAL) < 9 && countItemInInventory(inv, Items.BONE) >= 3) {
				int bonesToConvert = Math.min(countItemInInventory(inv, Items.BONE), 3);
				consumeItemFromInventory(inv, Items.BONE, bonesToConvert);
				inv.addStack(new ItemStack(Items.BONE_MEAL, bonesToConvert * 3));
			}
			if (countItemInInventory(inv, Items.BONE_MEAL) >= 9) {
				consumeItemFromInventory(inv, Items.BONE_MEAL, 9);
				inv.addStack(new ItemStack(Items.BONE_BLOCK, 1));
				return true;
			}
		}

		// 3. Wool: 4 String -> 1 Wool
		if (isWoolResource(targetItem)) {
			if (countItemInInventory(inv, Items.STRING) >= 4) {
				consumeItemFromInventory(inv, Items.STRING, 4);
				inv.addStack(new ItemStack(targetItem, 1));
				return true;
			}
		}

		// 4. Slime Block: 9 Slimeballs -> 1 Slime Block
		if (targetItem == Items.SLIME_BLOCK) {
			if (countItemInInventory(inv, Items.SLIME_BALL) >= 9) {
				consumeItemFromInventory(inv, Items.SLIME_BALL, 9);
				inv.addStack(new ItemStack(Items.SLIME_BLOCK, 1));
				return true;
			}
		}

		// 5. Sticky Piston: 1 Piston + 1 Slimeball -> 1 Sticky Piston
		if (targetItem == Items.STICKY_PISTON) {
			if (countItemInInventory(inv, Items.PISTON) >= 1 && countItemInInventory(inv, Items.SLIME_BALL) >= 1) {
				consumeItemFromInventory(inv, Items.PISTON, 1);
				consumeItemFromInventory(inv, Items.SLIME_BALL, 1);
				inv.addStack(new ItemStack(Items.STICKY_PISTON, 1));
				return true;
			}
		}

		// 6. Magma Block: 4 Magma Cream -> 1 Magma Block
		if (targetItem == Items.MAGMA_BLOCK) {
			if (countItemInInventory(inv, Items.MAGMA_CREAM) >= 4) {
				consumeItemFromInventory(inv, Items.MAGMA_CREAM, 4);
				inv.addStack(new ItemStack(Items.MAGMA_BLOCK, 1));
				return true;
			}
		}

		// 7. Blaze Powder: 1 Blaze Rod -> 2 Blaze Powder
		if (targetItem == Items.BLAZE_POWDER) {
			if (countItemInInventory(inv, Items.BLAZE_ROD) >= 1) {
				consumeItemFromInventory(inv, Items.BLAZE_ROD, 1);
				inv.addStack(new ItemStack(Items.BLAZE_POWDER, 2));
				return true;
			}
		}

		// 8. Eye of Ender: 1 Ender Pearl + 1 Blaze Powder -> 1 Eye of Ender
		if (targetItem == Items.ENDER_EYE) {
			if (countItemInInventory(inv, Items.BLAZE_POWDER) < 1 && countItemInInventory(inv, Items.BLAZE_ROD) >= 1) {
				consumeItemFromInventory(inv, Items.BLAZE_ROD, 1);
				inv.addStack(new ItemStack(Items.BLAZE_POWDER, 2));
			}
			if (countItemInInventory(inv, Items.ENDER_PEARL) >= 1 && countItemInInventory(inv, Items.BLAZE_POWDER) >= 1) {
				consumeItemFromInventory(inv, Items.ENDER_PEARL, 1);
				consumeItemFromInventory(inv, Items.BLAZE_POWDER, 1);
				inv.addStack(new ItemStack(Items.ENDER_EYE, 1));
				return true;
			}
		}

		// 9. Prismarine: 4 Prismarine Shards -> 1 Prismarine
		if (targetItem == Items.PRISMARINE) {
			if (countItemInInventory(inv, Items.PRISMARINE_SHARD) >= 4) {
				consumeItemFromInventory(inv, Items.PRISMARINE_SHARD, 4);
				inv.addStack(new ItemStack(Items.PRISMARINE, 1));
				return true;
			}
		}

		// 10. Prismarine Bricks: 9 Prismarine Shards -> 1 Prismarine Bricks
		if (targetItem == Items.PRISMARINE_BRICKS) {
			if (countItemInInventory(inv, Items.PRISMARINE_SHARD) >= 9) {
				consumeItemFromInventory(inv, Items.PRISMARINE_SHARD, 9);
				inv.addStack(new ItemStack(Items.PRISMARINE_BRICKS, 1));
				return true;
			}
		}

		// 11. Sea Lantern: 4 Prismarine Shards + 5 Prismarine Crystals -> 1 Sea Lantern
		if (targetItem == Items.SEA_LANTERN) {
			if (countItemInInventory(inv, Items.PRISMARINE_SHARD) >= 4 && countItemInInventory(inv, Items.PRISMARINE_CRYSTALS) >= 5) {
				consumeItemFromInventory(inv, Items.PRISMARINE_SHARD, 4);
				consumeItemFromInventory(inv, Items.PRISMARINE_CRYSTALS, 5);
				inv.addStack(new ItemStack(Items.SEA_LANTERN, 1));
				return true;
			}
		}

		// 12. Leather: 4 Rabbit Hide -> 1 Leather
		if (targetItem == Items.LEATHER) {
			if (countItemInInventory(inv, Items.RABBIT_HIDE) >= 4) {
				consumeItemFromInventory(inv, Items.RABBIT_HIDE, 4);
				inv.addStack(new ItemStack(Items.LEATHER, 1));
				return true;
			}
		}

		// 13. Cobweb: 9 String -> 1 Cobweb
		if (targetItem == Items.COBWEB) {
			if (countItemInInventory(inv, Items.STRING) >= 9) {
				consumeItemFromInventory(inv, Items.STRING, 9);
				inv.addStack(new ItemStack(Items.COBWEB, 1));
				return true;
			}
		}

		return false;
	}

	/**
	 * Finds a nearby available Warrior thrall belonging to the same owner who can be commissioned.
	 */
	public static MinionEntity findNearbyAvailableWarrior(MinionEntity requester, ServerWorld world, double radius) {
		if (requester == null || world == null) return null;
		UUID ownerUuid = requester.getOwnerUuid();
		if (ownerUuid == null) return null;

		Box box = requester.getBoundingBox().expand(radius);
		List<MinionEntity> warriors = world.getEntitiesByClass(
			MinionEntity.class,
			box,
			m -> m != requester
				&& m.isAlive()
				&& ownerUuid.equals(m.getOwnerUuid())
				&& m.matchesRole(MinionRole.WARRIOR)
				&& !m.isSitting()
				&& !m.hasActiveProcurement()
				&& !m.hasAssaultTargets()
		);

		if (warriors.isEmpty()) return null;
		warriors.sort((a, b) -> Double.compare(a.squaredDistanceTo(requester), b.squaredDistanceTo(requester)));
		return warriors.get(0);
	}

	/**
	 * Finds the closest candidate mob within radius that satisfies safe hunt checks and matches the resource.
	 */
	public static LivingEntity findCandidateMobForProcurement(
		MinionEntity requester,
		ServerWorld world,
		Item requiredItem,
		double radius
	) {
		if (requester == null || world == null || requiredItem == null) return null;
		UUID ownerUuid = requester.getOwnerUuid();
		Box searchBox = requester.getBoundingBox().expand(radius);

		List<LivingEntity> candidates = world.getEntitiesByClass(
			LivingEntity.class,
			searchBox,
			entity -> isSafeHuntTarget(entity, ownerUuid) && isTargetMobForResource(entity, requiredItem)
		);

		if (candidates.isEmpty()) {
			return null;
		}

		candidates.sort((a, b) -> Double.compare(a.squaredDistanceTo(requester), b.squaredDistanceTo(requester)));
		return candidates.get(0);
	}

	/**
	 * Commissions an allied Warrior thrall on a tactical mob hunting procurement contract.
	 */
	public static boolean commissionWarriorHunt(
		MinionEntity builder,
		MinionEntity warrior,
		LivingEntity targetMob,
		Item requiredItem,
		ServerWorld world
	) {
		if (builder == null || warrior == null || targetMob == null || requiredItem == null || world == null) {
			return false;
		}

		warrior.setProcurementRequesterUuid(builder.getUuid());
		warrior.setProcurementItem(requiredItem);
		warrior.setProcurementTarget(targetMob);
		warrior.setTarget(targetMob);

		// Audio feedback: weaponsmith / chime feedback
		world.playSound(
			null,
			builder.getX(),
			builder.getY(),
			builder.getZ(),
			SoundEvents.ENTITY_VILLAGER_WORK_WEAPONSMITH,
			SoundCategory.NEUTRAL,
			1.0F,
			1.0F
		);

		// Visual sharing beam between builder and warrior
		MinionLogisticsHelper.spawnSharingBeam(world, builder.getEyePos(), warrior.getEyePos());

		// Target indicator particles
		world.spawnParticles(
			ParticleTypes.CRIT,
			targetMob.getX(),
			targetMob.getBodyY(0.5D),
			targetMob.getZ(),
			10,
			0.3D,
			0.3D,
			0.3D,
			0.1D
		);

		return true;
	}

	/**
	 * Attempts autonomous mob hunting: scans for candidate mobs and either commissions a nearby Warrior
	 * or falls back to solo hunting by the builder thrall.
	 */
	public static boolean tryAutonomousMobHunting(
		MinionEntity builder,
		ServerWorld world,
		Item requiredItem
	) {
		if (builder == null || world == null || requiredItem == null) {
			return false;
		}

		// 1. Check if we can synthesize from current inventory first
		if (synthesizeMaterial(builder.getInventory(), requiredItem)) {
			return true;
		}

		// 2. Scan for candidate mobs within 32 blocks
		LivingEntity targetMob = findCandidateMobForProcurement(builder, world, requiredItem, 32.0D);
		if (targetMob == null) {
			return false;
		}

		// 3. Find nearby available Warrior thrall
		MinionEntity warrior = findNearbyAvailableWarrior(builder, world, 32.0D);
		if (warrior != null) {
			return commissionWarriorHunt(builder, warrior, targetMob, requiredItem, world);
		} else {
			// Solo Hunting Fallback: Builder engages target mob directly
			builder.setProcurementRequesterUuid(builder.getUuid());
			builder.setProcurementItem(requiredItem);
			builder.setProcurementTarget(targetMob);
			builder.setTarget(targetMob);
			world.playSound(null, builder.getX(), builder.getY(), builder.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.8F, 1.2F);
			return true;
		}
	}

	/**
	 * Processes mob hunting drops upon target slay, synthesizing the required item and delivering it to the requester.
	 */
	public static void processMobHuntingDrops(MinionEntity warrior, LivingEntity slainTarget, ServerWorld world) {
		if (warrior == null || world == null) return;

		Item requiredItem = warrior.getProcurementItem();
		UUID requesterUuid = warrior.getProcurementRequesterUuid();

		if (requiredItem != null) {
			// Resolve primary drops from the slain mob
			List<ItemStack> drops = resolveMobDropsForProcurement(slainTarget, requiredItem);
			for (ItemStack drop : drops) {
				warrior.getInventory().addStack(drop);
			}

			// Perform inventory material synthesis if needed (e.g. bones -> bone meal, strings -> wool)
			synthesizeMaterial(warrior.getInventory(), requiredItem);

			// Deliver synthesized/procured item to requester if alive and nearby
			if (requesterUuid != null) {
				deliverProcuredItemToRequester(warrior, requesterUuid, requiredItem, world);
			}

			// Auditory and particle completion feedback
			world.playSound(
				null,
				warrior.getX(),
				warrior.getY(),
				warrior.getZ(),
				SoundEvents.ENTITY_ITEM_PICKUP,
				SoundCategory.NEUTRAL,
				1.0F,
				1.2F
			);
			world.spawnParticles(
				ParticleTypes.HAPPY_VILLAGER,
				warrior.getX(),
				warrior.getY() + 1.0D,
				warrior.getZ(),
				8,
				0.3D,
				0.3D,
				0.3D,
				0.05D
			);
		}

		warrior.clearProcurementTask();
	}

	/**
	 * Resolves drop items yielded from slaying the target entity for a procurement contract.
	 */
	public static List<ItemStack> resolveMobDropsForProcurement(LivingEntity slainTarget, Item requiredItem) {
		List<ItemStack> drops = new ArrayList<>();
		if (slainTarget == null || requiredItem == null) return drops;

		if (slainTarget instanceof SheepEntity) {
			Item woolItem = isWoolResource(requiredItem) ? requiredItem : Items.WHITE_WOOL;
			drops.add(new ItemStack(woolItem, 2));
			drops.add(new ItemStack(Items.MUTTON, 1));
		} else if (slainTarget instanceof SpiderEntity || slainTarget instanceof CaveSpiderEntity) {
			drops.add(new ItemStack(Items.STRING, 2));
			drops.add(new ItemStack(Items.SPIDER_EYE, 1));
		} else if (slainTarget instanceof AbstractSkeletonEntity) {
			drops.add(new ItemStack(Items.BONE, 2));
			drops.add(new ItemStack(Items.ARROW, 2));
		} else if (slainTarget instanceof SlimeEntity) {
			drops.add(new ItemStack(Items.SLIME_BALL, 2));
		} else if (slainTarget instanceof MagmaCubeEntity) {
			drops.add(new ItemStack(Items.MAGMA_CREAM, 2));
		} else if (slainTarget instanceof CowEntity || slainTarget instanceof MooshroomEntity || slainTarget instanceof HoglinEntity) {
			drops.add(new ItemStack(Items.LEATHER, 2));
			drops.add(new ItemStack(Items.BEEF, 2));
		} else if (slainTarget instanceof SquidEntity) {
			drops.add(new ItemStack(Items.INK_SAC, 2));
		} else if (slainTarget instanceof GlowSquidEntity) {
			drops.add(new ItemStack(Items.GLOW_INK_SAC, 2));
		} else if (slainTarget instanceof ChickenEntity || slainTarget instanceof ParrotEntity) {
			drops.add(new ItemStack(Items.FEATHER, 2));
			drops.add(new ItemStack(Items.CHICKEN, 1));
		} else if (slainTarget instanceof BlazeEntity) {
			drops.add(new ItemStack(Items.BLAZE_ROD, 1));
		} else if (slainTarget instanceof EndermanEntity) {
			drops.add(new ItemStack(Items.ENDER_PEARL, 1));
		} else if (slainTarget instanceof CreeperEntity || slainTarget instanceof GhastEntity || slainTarget instanceof WitchEntity) {
			drops.add(new ItemStack(Items.GUNPOWDER, 2));
		} else if (slainTarget instanceof ZombieEntity) {
			drops.add(new ItemStack(Items.ROTTEN_FLESH, 2));
		} else if (slainTarget instanceof GuardianEntity) {
			drops.add(new ItemStack(Items.PRISMARINE_SHARD, 2));
			drops.add(new ItemStack(Items.PRISMARINE_CRYSTALS, 1));
		} else if (slainTarget instanceof RabbitEntity) {
			drops.add(new ItemStack(Items.RABBIT_HIDE, 2));
			drops.add(new ItemStack(Items.RABBIT_FOOT, 1));
		} else if (slainTarget instanceof PhantomEntity) {
			drops.add(new ItemStack(Items.PHANTOM_MEMBRANE, 1));
		} else if (slainTarget instanceof ShulkerEntity) {
			drops.add(new ItemStack(Items.SHULKER_SHELL, 1));
		} else {
			drops.add(new ItemStack(requiredItem, 1));
		}

		return drops;
	}

	/**
	 * Delivers procured items from the hunting warrior to the original builder minion requester.
	 */
	public static boolean deliverProcuredItemToRequester(
		MinionEntity warrior,
		UUID requesterUuid,
		Item requiredItem,
		ServerWorld world
	) {
		if (warrior == null || requesterUuid == null || world == null) return false;

		// If warrior is the requester (solo hunting fallback), it is already in their inventory
		if (warrior.getUuid().equals(requesterUuid)) {
			return true;
		}

		List<MinionEntity> requesters = world.getEntitiesByClass(
			MinionEntity.class,
			warrior.getBoundingBox().expand(48.0D),
			m -> m.getUuid().equals(requesterUuid) && m.isAlive()
		);

		for (MinionEntity ally : requesters) {
			SimpleInventory warriorInv = warrior.getInventory();
			SimpleInventory allyInv = ally.getInventory();

			// First ensure synthesized if needed
			synthesizeMaterial(warriorInv, requiredItem);

			for (int slot = 0; slot < warriorInv.size(); slot++) {
				ItemStack stack = warriorInv.getStack(slot);
				if (!stack.isEmpty() && stack.isOf(requiredItem) && stack.getCount() > 0) {
					ItemStack transferred = stack.split(1);
					warriorInv.markDirty();
					allyInv.addStack(transferred);
					allyInv.markDirty();

					MinionLogisticsHelper.spawnSharingBeam(world, warrior.getEyePos(), ally.getEyePos());
					world.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.8F, 1.2F);
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isWorkstationResource(Item item) {
		return item == Items.CHEST || item == Items.BARREL || item == Items.CRAFTING_TABLE
			|| item == Items.FURNACE || item == Items.BLAST_FURNACE || item == Items.SMOKER
			|| item == Items.SMITHING_TABLE || item == Items.GRINDSTONE || item == Items.ANVIL
			|| item == Items.CHIPPED_ANVIL || item == Items.DAMAGED_ANVIL
			|| item == Items.LANTERN || item == Items.SOUL_LANTERN || item == Items.TORCH
			|| item == Items.SOUL_TORCH || item == Items.LADDER;
	}

	public static boolean synthesizeWorkstation(
		MinionEntity minion,
		ServerWorld world,
		Item targetItem,
		ConstructionSession session
	) {
		if (minion == null || targetItem == null) return false;
		SimpleInventory inv = minion.getInventory();

		// Chest: 8 Planks
		if (targetItem == Items.CHEST) {
			ensurePlanks(minion, world, session, 8);
			if (countPlanksInInventory(inv) >= 8) {
				consumePlanks(inv, 8);
				inv.addStack(new ItemStack(Items.CHEST, 1));
				return true;
			}
		}

		// Barrel: 6 Planks + 2 Slabs (or 7 Planks)
		if (targetItem == Items.BARREL) {
			ensurePlanks(minion, world, session, 7);
			if (countPlanksInInventory(inv) >= 6) {
				consumePlanks(inv, 6);
				inv.addStack(new ItemStack(Items.BARREL, 1));
				return true;
			}
		}

		// Crafting Table: 4 Planks
		if (targetItem == Items.CRAFTING_TABLE) {
			ensurePlanks(minion, world, session, 4);
			if (countPlanksInInventory(inv) >= 4) {
				consumePlanks(inv, 4);
				inv.addStack(new ItemStack(Items.CRAFTING_TABLE, 1));
				return true;
			}
		}

		// Furnace: 8 Cobblestone
		if (targetItem == Items.FURNACE) {
			if (countItemInInventory(inv, Items.COBBLESTONE) < 8) {
				quarryNaturalStone(minion, world, Items.COBBLESTONE, session);
			}
			if (countItemInInventory(inv, Items.COBBLESTONE) >= 8) {
				consumeItemFromInventory(inv, Items.COBBLESTONE, 8);
				inv.addStack(new ItemStack(Items.FURNACE, 1));
				return true;
			}
		}

		// Blast Furnace: 1 Furnace + 5 Iron Ingots + 3 Smooth Stone (or 8 Cobblestone + 5 Iron Ingots)
		if (targetItem == Items.BLAST_FURNACE) {
			if (countItemInInventory(inv, Items.IRON_INGOT) >= 5) {
				consumeItemFromInventory(inv, Items.IRON_INGOT, 5);
				if (countItemInInventory(inv, Items.COBBLESTONE) >= 8) {
					consumeItemFromInventory(inv, Items.COBBLESTONE, 8);
				}
				inv.addStack(new ItemStack(Items.BLAST_FURNACE, 1));
				return true;
			}
		}

		// Smoker: 1 Furnace + 4 Logs
		if (targetItem == Items.SMOKER) {
			if (countItemInInventory(inv, Items.COBBLESTONE) >= 8) {
				consumeItemFromInventory(inv, Items.COBBLESTONE, 8);
				inv.addStack(new ItemStack(Items.SMOKER, 1));
				return true;
			}
		}

		// Smithing Table: 4 Planks + 2 Iron Ingots
		if (targetItem == Items.SMITHING_TABLE) {
			ensurePlanks(minion, world, session, 4);
			if (countPlanksInInventory(inv) >= 4 && countItemInInventory(inv, Items.IRON_INGOT) >= 2) {
				consumePlanks(inv, 4);
				consumeItemFromInventory(inv, Items.IRON_INGOT, 2);
				inv.addStack(new ItemStack(Items.SMITHING_TABLE, 1));
				return true;
			}
		}

		// Grindstone: 2 Sticks + 1 Stone Slab + 2 Planks
		if (targetItem == Items.GRINDSTONE) {
			ensurePlanks(minion, world, session, 4);
			if (countPlanksInInventory(inv) >= 2) {
				consumePlanks(inv, 2);
				inv.addStack(new ItemStack(Items.GRINDSTONE, 1));
				return true;
			}
		}

		// Anvil: 3 Iron Blocks + 4 Iron Ingots (or 12+ Iron Ingots)
		if (targetItem == Items.ANVIL || targetItem == Items.CHIPPED_ANVIL || targetItem == Items.DAMAGED_ANVIL) {
			if (countItemInInventory(inv, Items.IRON_BLOCK) >= 3 && countItemInInventory(inv, Items.IRON_INGOT) >= 4) {
				consumeItemFromInventory(inv, Items.IRON_BLOCK, 3);
				consumeItemFromInventory(inv, Items.IRON_INGOT, 4);
				inv.addStack(new ItemStack(targetItem, 1));
				return true;
			} else if (countItemInInventory(inv, Items.IRON_INGOT) >= 12) {
				consumeItemFromInventory(inv, Items.IRON_INGOT, 12);
				inv.addStack(new ItemStack(targetItem, 1));
				return true;
			}
		}

		// Lantern: 1 Torch + 8 Iron Nuggets (or 1 Torch + 1 Iron Ingot)
		if (targetItem == Items.LANTERN) {
			if (countItemInInventory(inv, Items.TORCH) < 1) {
				inv.addStack(new ItemStack(Items.TORCH, 4));
			}
			if (countItemInInventory(inv, Items.TORCH) >= 1) {
				consumeItemFromInventory(inv, Items.TORCH, 1);
				if (countItemInInventory(inv, Items.IRON_INGOT) >= 1) {
					consumeItemFromInventory(inv, Items.IRON_INGOT, 1);
				}
				inv.addStack(new ItemStack(Items.LANTERN, 1));
				return true;
			}
		}

		// Soul Lantern
		if (targetItem == Items.SOUL_LANTERN) {
			inv.addStack(new ItemStack(Items.SOUL_LANTERN, 1));
			return true;
		}

		// Torch
		if (targetItem == Items.TORCH) {
			inv.addStack(new ItemStack(Items.TORCH, 4));
			return true;
		}

		// Ladder
		if (targetItem == Items.LADDER) {
			ensurePlanks(minion, world, session, 2);
			consumePlanks(inv, Math.min(2, countPlanksInInventory(inv)));
			inv.addStack(new ItemStack(Items.LADDER, 3));
			return true;
		}

		return false;
	}

	private static void ensurePlanks(MinionEntity minion, ServerWorld world, ConstructionSession session, int required) {
		SimpleInventory inv = minion.getInventory();
		if (countPlanksInInventory(inv) < required) {
			harvestWoodOrAgroForestry(minion, world, session);
			convertLogsToPlanks(minion, Items.OAK_PLANKS);
		}
	}

	private static int countPlanksInInventory(SimpleInventory inv) {
		int count = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && isPlanksItem(s.getItem())) {
				count += s.getCount();
			}
		}
		return count;
	}

	private static void consumePlanks(SimpleInventory inv, int count) {
		int remaining = count;
		for (int i = 0; i < inv.size() && remaining > 0; i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && isPlanksItem(s.getItem())) {
				int consumed = Math.min(remaining, s.getCount());
				s.decrement(consumed);
				remaining -= consumed;
				if (s.isEmpty()) {
					inv.setStack(i, ItemStack.EMPTY);
				}
			}
		}
		inv.markDirty();
	}
}
