package com.example.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Utility helper for managing directional block orientation (Facing, Half, Axis)
 * during Area Fill construction and procedural blueprint synthesis.
 */
public final class BlockOrientationHelper {

	private BlockOrientationHelper() {}

	/**
	 * Determines if the block state possesses directional properties (Facing, Axis, Half, SlabType).
	 */
	public static boolean hasOrientation(BlockState state) {
		if (state == null) return false;
		return state.contains(Properties.HORIZONTAL_FACING)
			|| state.contains(Properties.FACING)
			|| state.contains(Properties.AXIS)
			|| state.contains(Properties.BLOCK_HALF)
			|| state.contains(Properties.SLAB_TYPE);
	}

	/**
	 * Determines if the block state possesses horizontal or 6-way facing or axis alignment.
	 */
	public static boolean hasFacing(BlockState state) {
		if (state == null) return false;
		return state.contains(Properties.HORIZONTAL_FACING)
			|| state.contains(Properties.FACING)
			|| state.contains(Properties.AXIS);
	}

	/**
	 * Determines if the block state possesses a vertical half/level property (Stairs, Slabs, Trapdoors)
	 * or vertical/horizontal axis toggle (Pillars, Logs).
	 */
	public static boolean hasHalf(BlockState state) {
		if (state == null) return false;
		return state.contains(Properties.BLOCK_HALF)
			|| state.contains(Properties.SLAB_TYPE)
			|| isPillar(state);
	}

	/**
	 * Checks if the block is a Stairs block.
	 */
	public static boolean isStairs(BlockState state) {
		return state != null && state.getBlock() instanceof StairsBlock;
	}

	/**
	 * Checks if the block is a Slab block.
	 */
	public static boolean isSlab(BlockState state) {
		return state != null && state.getBlock() instanceof SlabBlock;
	}

	/**
	 * Checks if the block is a Pillar / Log / Basalt block with directional Axis.
	 */
	public static boolean isPillar(BlockState state) {
		return state != null && state.contains(Properties.AXIS) && !state.contains(Properties.HORIZONTAL_FACING);
	}

	/**
	 * Returns a UTF-8 directional arrow symbol for a horizontal direction.
	 */
	public static String getDirectionArrow(Direction dir) {
		if (dir == null) return "⬆";
		return switch (dir) {
			case NORTH -> "⬆";
			case EAST -> "➡";
			case SOUTH -> "⬇";
			case WEST -> "⬅";
			case UP -> "▲";
			case DOWN -> "▼";
		};
	}

	/**
	 * Applies the given facing direction and vertical half state to a target BlockState.
	 *
	 * @param state        The source block state.
	 * @param facing       The desired horizontal or general facing.
	 * @param invertedHalf Whether to invert the block half (Top for stairs/slabs, Bottom otherwise) or horizontal axis for pillars.
	 * @return The updated BlockState with directional properties applied.
	 */
	public static BlockState applyOrientation(BlockState state, Direction facing, boolean invertedHalf) {
		if (state == null) {
			return null;
		}
		BlockState result = state;

		// 1. Horizontal Facing (Stairs, Chests, Furnaces, Glazed Terracotta, Pumpkins, Trapdoors, Observers)
		if (result.contains(Properties.HORIZONTAL_FACING)) {
			Direction hFacing = (facing != null && facing.getAxis().isHorizontal()) ? facing : Direction.NORTH;
			result = result.with(Properties.HORIZONTAL_FACING, hFacing);
		} else if (result.contains(Properties.FACING)) {
			// 6-way facing (Pistons, Dispensers, Droppers)
			Direction f = facing != null ? facing : Direction.NORTH;
			result = result.with(Properties.FACING, f);
		}

		// 2. Axis for Pillars / Logs / Basalt (only if block does not have horizontal facing)
		if (result.contains(Properties.AXIS) && !result.contains(Properties.HORIZONTAL_FACING)) {
			Direction.Axis axis;
			if (!invertedHalf) {
				// Default: Vertical (Upright) pillar / log
				axis = Direction.Axis.Y;
			} else {
				// Inverted: Horizontal beam aligned with chosen facing
				if (facing != null && facing.getAxis() == Direction.Axis.X) {
					axis = Direction.Axis.X;
				} else {
					axis = Direction.Axis.Z;
				}
			}
			result = result.with(Properties.AXIS, axis);
		}

		// 3. Block Half (Stairs, Trapdoors)
		if (result.contains(Properties.BLOCK_HALF)) {
			result = result.with(Properties.BLOCK_HALF, invertedHalf ? BlockHalf.TOP : BlockHalf.BOTTOM);
		}

		// 4. Slab Type (Slabs)
		if (result.contains(Properties.SLAB_TYPE)) {
			result = result.with(Properties.SLAB_TYPE, invertedHalf ? SlabType.TOP : SlabType.BOTTOM);
		}

		return result;
	}
}
