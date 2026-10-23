package com.example.blueprint;

import java.util.Objects;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

/**
 * Immutable representation of a single block within a structure blueprint.
 * Stores relative coordinates from the structure anchor and the target BlockState.
 *
 * Implements strict bottom-up topological sorting:
 * 1. Effective vertical height (Y coordinate, with dependent hanging blocks sorted after their ceiling anchor).
 * 2. Manhattan distance from origin (center-outward construction order).
 * 3. Lexicographical coordinate tie-breakers (X, then Z).
 */
public record BlueprintBlock(BlockPos offset, BlockState state) implements Comparable<BlueprintBlock> {

	public static final PacketCodec<RegistryByteBuf, BlueprintBlock> PACKET_CODEC = new PacketCodec<>() {
		@Override
		public void encode(RegistryByteBuf buf, BlueprintBlock block) {
			BlockPos.PACKET_CODEC.encode(buf, block.offset());
			PacketCodecs.VAR_INT.encode(buf, Block.getRawIdFromState(block.state()));
		}

		@Override
		public BlueprintBlock decode(RegistryByteBuf buf) {
			BlockPos offset = BlockPos.PACKET_CODEC.decode(buf);
			int rawId = PacketCodecs.VAR_INT.decode(buf);
			BlockState state = Block.getStateFromRawId(rawId);
			if (state == null) {
				state = net.minecraft.block.Blocks.STONE.getDefaultState();
			}
			return new BlueprintBlock(offset, state);
		}
	};

	public BlueprintBlock(BlockPos offset) {
		this(offset, null);
	}

	public BlueprintBlock {
		Objects.requireNonNull(offset, "offset cannot be null");
		if (state != null && state.getBlock() instanceof BedBlock && state.contains(BedBlock.OCCUPIED) && state.get(BedBlock.OCCUPIED)) {
			state = state.with(BedBlock.OCCUPIED, false);
		}
	}

	/**
	 * Computes the absolute world position for this blueprint block relative to an anchor point.
	 *
	 * @param anchor The origin block coordinate in world space.
	 * @return The target world position.
	 */
	public BlockPos toWorldPos(BlockPos anchor) {
		return anchor.add(this.offset);
	}

	/**
	 * Resolves the item needed by minions to construct this block in survival mode.
	 *
	 * @return The Item associated with this block state, or Items.AIR if not obtainable.
	 */
	public Item getRequiredItem() {
		try {
			if (this.state == null) {
				return net.minecraft.item.Items.AIR;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.WATER)) {
				return net.minecraft.item.Items.WATER_BUCKET;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.LAVA)) {
				return net.minecraft.item.Items.LAVA_BUCKET;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.SKELETON_WALL_SKULL)) {
				return net.minecraft.item.Items.SKELETON_SKULL;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.WITHER_SKELETON_WALL_SKULL)) {
				return net.minecraft.item.Items.WITHER_SKELETON_SKULL;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.PLAYER_WALL_HEAD)) {
				return net.minecraft.item.Items.PLAYER_HEAD;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.ZOMBIE_WALL_HEAD)) {
				return net.minecraft.item.Items.ZOMBIE_HEAD;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.CREEPER_WALL_HEAD)) {
				return net.minecraft.item.Items.CREEPER_HEAD;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.PIGLIN_WALL_HEAD)) {
				return net.minecraft.item.Items.PIGLIN_HEAD;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.DRAGON_WALL_HEAD)) {
				return net.minecraft.item.Items.DRAGON_HEAD;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.WALL_TORCH)) {
				return net.minecraft.item.Items.TORCH;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.SOUL_WALL_TORCH)) {
				return net.minecraft.item.Items.SOUL_TORCH;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.FARMLAND) || this.state.isOf(net.minecraft.block.Blocks.DIRT_PATH)) {
				return net.minecraft.item.Items.DIRT;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.WHEAT)) {
				return net.minecraft.item.Items.WHEAT_SEEDS;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.CARROTS)) {
				return net.minecraft.item.Items.CARROT;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.POTATOES)) {
				return net.minecraft.item.Items.POTATO;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.BEETROOTS)) {
				return net.minecraft.item.Items.BEETROOT_SEEDS;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.MELON_STEM) || this.state.isOf(net.minecraft.block.Blocks.ATTACHED_MELON_STEM)) {
				return net.minecraft.item.Items.MELON_SEEDS;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.PUMPKIN_STEM) || this.state.isOf(net.minecraft.block.Blocks.ATTACHED_PUMPKIN_STEM)) {
				return net.minecraft.item.Items.PUMPKIN_SEEDS;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.NETHER_WART)) {
				return net.minecraft.item.Items.NETHER_WART;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.SWEET_BERRY_BUSH)) {
				return net.minecraft.item.Items.SWEET_BERRIES;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.COCOA)) {
				return net.minecraft.item.Items.COCOA_BEANS;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.TRIPWIRE)) {
				return net.minecraft.item.Items.STRING;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.REDSTONE_WIRE)) {
				return net.minecraft.item.Items.REDSTONE;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.KELP_PLANT)) {
				return net.minecraft.item.Items.KELP;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.BAMBOO_SAPLING)) {
				return net.minecraft.item.Items.BAMBOO;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.CAVE_VINES) || this.state.isOf(net.minecraft.block.Blocks.CAVE_VINES_PLANT)) {
				return net.minecraft.item.Items.GLOW_BERRIES;
			}
			Item item = this.state.getBlock().asItem();
			if (item != null && item != net.minecraft.item.Items.AIR) {
				return item;
			}
			// Universal wall-variant mapping for unmapped block-items (wall signs, wall banners, hanging signs, torches, heads)
			try {
				net.minecraft.util.Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(this.state.getBlock());
				if (blockId != null) {
					String path = blockId.getPath();
					String targetPath = null;
					if (path.contains("_wall_hanging_sign")) {
						targetPath = path.replace("_wall_hanging_sign", "_hanging_sign");
					} else if (path.contains("_wall_sign")) {
						targetPath = path.replace("_wall_sign", "_sign");
					} else if (path.contains("_wall_banner")) {
						targetPath = path.replace("_wall_banner", "_banner");
					} else if (path.contains("_wall_fan")) {
						targetPath = path.replace("_wall_fan", "_fan");
					} else if (path.contains("_wall_head")) {
						targetPath = path.replace("_wall_head", "_head");
					} else if (path.contains("_wall_skull")) {
						targetPath = path.replace("_wall_skull", "_skull");
					} else if (path.equals("wall_torch")) {
						targetPath = "torch";
					} else if (path.equals("soul_wall_torch")) {
						targetPath = "soul_torch";
					} else if (path.equals("redstone_wall_torch")) {
						targetPath = "redstone_torch";
					}

					if (targetPath != null) {
						Item mapped = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(blockId.getNamespace(), targetPath));
						if (mapped != null && mapped != net.minecraft.item.Items.AIR) {
							return mapped;
						}
					}
				}
			} catch (Throwable ignored) {}
			return item != null ? item : net.minecraft.item.Items.AIR;
		} catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * Creates a single-count ItemStack of the required item.
	 *
	 * @return An ItemStack representing one unit of the required resource.
	 */
	public ItemStack getRequiredStack() {
		return new ItemStack(getRequiredItem());
	}

	/**
	 * Determines whether this block relies on a supporting block above it (such as a hanging lantern).
	 *
	 * @return True if this block hangs from an overhead support.
	 */
	public boolean isHanging() {
		try {
			return this.state != null && this.state.contains(Properties.HANGING) && Boolean.TRUE.equals(this.state.get(Properties.HANGING));
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * Calculates the effective vertical construction layer.
	 * Hanging blocks are shifted up one layer so their overhead ceiling support is guaranteed
	 * to be placed first during topological bottom-up execution.
	 *
	 * @return The effective vertical layer for sorting.
	 */
	public int getEffectiveY() {
		return isHanging() ? this.offset.getY() + 1 : this.offset.getY();
	}

	/**
	 * Calculates the horizontal Manhattan distance from the blueprint origin (0, 0).
	 * Used to prioritize central structural cores before building outward overhangs and buttresses.
	 *
	 * @return Manhattan distance |X| + |Z|.
	 */
	public int getManhattanDistance() {
		return Math.abs(this.offset.getX()) + Math.abs(this.offset.getZ());
	}

	/**
	 * Determines whether this block represents a fluid (such as water or lava).
	 * Uses safe inspection that functions in both live Minecraft and lightweight unit tests.
	 *
	 * @return True if this block state is liquid or a fluid block.
	 */
	public boolean isFluid() {
		try {
			if (this.state == null) {
				return false;
			}
			return this.state.isLiquid()
				|| (this.state.getBlock() instanceof net.minecraft.block.FluidBlock)
				|| (this.state.getFluidState() != null && !this.state.getFluidState().isEmpty());
		} catch (Throwable ignored) {
			return false;
		}
	}

	public enum ToolTransformationType {
		NONE,
		HOE_TILL,          // Dirt -> Farmland
		SHOVEL_FLATTEN,    // Dirt -> Dirt Path
		AXE_STRIP,         // Log -> Stripped Log
		SHEAR_CARVE,       // Pumpkin -> Carved Pumpkin
		SHOVEL_EXTINGUISH  // Campfire -> Unlit Campfire
	}

	/**
	 * Determines the tool transformation required to produce this block in survival mode.
	 */
	public ToolTransformationType getToolTransformation() {
		try {
			if (this.state == null) {
				return ToolTransformationType.NONE;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.FARMLAND)) {
				return ToolTransformationType.HOE_TILL;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.DIRT_PATH)) {
				return ToolTransformationType.SHOVEL_FLATTEN;
			}
			if (this.state.isOf(net.minecraft.block.Blocks.CARVED_PUMPKIN)) {
				return ToolTransformationType.SHEAR_CARVE;
			}
			if (this.state.getBlock() != null) {
				net.minecraft.util.Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(this.state.getBlock());
				if (blockId != null && blockId.getPath().startsWith("stripped_")) {
					return ToolTransformationType.AXE_STRIP;
				}
			}
			if ((this.state.isOf(net.minecraft.block.Blocks.CAMPFIRE) || this.state.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE))
					&& this.state.contains(net.minecraft.state.property.Properties.LIT) && !this.state.get(net.minecraft.state.property.Properties.LIT)) {
				return ToolTransformationType.SHOVEL_EXTINGUISH;
			}
		} catch (Throwable ignored) {
		}
		return ToolTransformationType.NONE;
	}

	/**
	 * Returns the base block state that must be placed before applying the tool transformation.
	 */
	public BlockState getBaseStateForTransformation() {
		ToolTransformationType type = getToolTransformation();
		if (type == ToolTransformationType.HOE_TILL || type == ToolTransformationType.SHOVEL_FLATTEN) {
			return net.minecraft.block.Blocks.DIRT.getDefaultState();
		}
		if (type == ToolTransformationType.SHEAR_CARVE) {
			return net.minecraft.block.Blocks.PUMPKIN.getDefaultState();
		}
		if (type == ToolTransformationType.AXE_STRIP) {
			try {
				net.minecraft.util.Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(this.state.getBlock());
				if (blockId != null && blockId.getPath().startsWith("stripped_")) {
					String baseName = blockId.getPath().substring("stripped_".length());
					net.minecraft.block.Block baseBlock = net.minecraft.registry.Registries.BLOCK.get(
						net.minecraft.util.Identifier.of(blockId.getNamespace(), baseName)
					);
					if (baseBlock != null && baseBlock != net.minecraft.block.Blocks.AIR) {
						BlockState baseState = baseBlock.getDefaultState();
						if (this.state.contains(net.minecraft.state.property.Properties.AXIS) && baseState.contains(net.minecraft.state.property.Properties.AXIS)) {
							baseState = baseState.with(net.minecraft.state.property.Properties.AXIS, this.state.get(net.minecraft.state.property.Properties.AXIS));
						}
						return baseState;
					}
				}
			} catch (Throwable ignored) {
			}
			return net.minecraft.block.Blocks.OAK_LOG.getDefaultState();
		}
		if (type == ToolTransformationType.SHOVEL_EXTINGUISH) {
			return this.state.with(net.minecraft.state.property.Properties.LIT, true);
		}
		return this.state;
	}

	/**
	 * Determines whether a block can be legitimately obtained and placed in standard survival gameplay.
	 * Strictly filters out creative-only, operator-only, bedrock, technical, and unobtainable blocks.
	 */
	public static boolean isSurvivalPlaceableBlock(net.minecraft.block.Block block) {
		if (block == null) return false;
		if (block.getDefaultState().isAir()) return false;

		// Explicit survival-friendly blocks that lack a direct item form
		if (block == net.minecraft.block.Blocks.FARMLAND || block == net.minecraft.block.Blocks.DIRT_PATH) {
			return true;
		}

		// Strictly exclude creative/operator-only, unbreakable, and system-only blocks
		if (block == net.minecraft.block.Blocks.LIGHT
				|| block == net.minecraft.block.Blocks.BARRIER
				|| block == net.minecraft.block.Blocks.STRUCTURE_BLOCK
				|| block == net.minecraft.block.Blocks.STRUCTURE_VOID
				|| block == net.minecraft.block.Blocks.JIGSAW
				|| block == net.minecraft.block.Blocks.COMMAND_BLOCK
				|| block == net.minecraft.block.Blocks.CHAIN_COMMAND_BLOCK
				|| block == net.minecraft.block.Blocks.REPEATING_COMMAND_BLOCK
				|| block == net.minecraft.block.Blocks.SPAWNER
				|| block == net.minecraft.block.Blocks.TRIAL_SPAWNER
				|| block == net.minecraft.block.Blocks.VAULT
				|| block == net.minecraft.block.Blocks.BEDROCK
				|| block == net.minecraft.block.Blocks.END_PORTAL
				|| block == net.minecraft.block.Blocks.END_PORTAL_FRAME
				|| block == net.minecraft.block.Blocks.END_GATEWAY
				|| block == net.minecraft.block.Blocks.NETHER_PORTAL
				|| block == net.minecraft.block.Blocks.REINFORCED_DEEPSLATE
				|| block == net.minecraft.block.Blocks.MOVING_PISTON
				|| block == net.minecraft.block.Blocks.PISTON_HEAD) {
			return false;
		}

		try {
			net.minecraft.util.Identifier id = net.minecraft.registry.Registries.BLOCK.getId(block);
			if (id != null) {
				String path = id.getPath();
				if (path.contains("command_block") || path.contains("structure_") || path.contains("jigsaw") || path.equals("light")) {
					return false;
				}
			}
		} catch (Throwable ignored) {
		}

		net.minecraft.item.Item item = block.asItem();
		return item instanceof net.minecraft.item.BlockItem;
	}

	@Override
	public int compareTo(BlueprintBlock other) {
		// 1. Primary: Bottom-up layer ordering by effective Y
		int layerComp = Integer.compare(this.getEffectiveY(), other.getEffectiveY());
		if (layerComp != 0) {
			return layerComp;
		}

		// 2. Hanging dependency guard: non-hanging blocks precede hanging blocks on the same effective layer
		int hangingComp = Boolean.compare(this.isHanging(), other.isHanging());
		if (hangingComp != 0) {
			return hangingComp;
		}

		// 2.5 Fluid containment guard: solid blocks precede fluid sources on the same layer
		// Ensures basins, rims, and walls are placed before pouring water or lava
		boolean thisIsFluid = this.isFluid();
		boolean otherIsFluid = other.isFluid();
		int fluidComp = Boolean.compare(thisIsFluid, otherIsFluid);
		if (fluidComp != 0) {
			return fluidComp;
		}

		// 3. Secondary: Radial Manhattan distance from center outward
		int distComp = Integer.compare(this.getManhattanDistance(), other.getManhattanDistance());
		if (distComp != 0) {
			return distComp;
		}

		// 4. Tertiary: Coordinate tie-breakers for deterministic total ordering
		int xComp = Integer.compare(this.offset.getX(), other.offset.getX());
		if (xComp != 0) {
			return xComp;
		}

		int zComp = Integer.compare(this.offset.getZ(), other.offset.getZ());
		if (zComp != 0) {
			return zComp;
		}

		// 5. Quaternary: Actual Y coordinate (in case hanging shifted the effective Y)
		int rawYComp = Integer.compare(this.offset.getY(), other.offset.getY());
		if (rawYComp != 0) {
			return rawYComp;
		}

		// 6. Block state string comparison for complete total ordering
		String s1 = this.state != null ? this.state.toString() : "";
		String s2 = other.state != null ? other.state.toString() : "";
		return s1.compareTo(s2);
	}
}
