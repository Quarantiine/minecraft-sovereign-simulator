package com.example.client.renderer;

import com.example.item.custom.CommandScepterItem;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Client-side singleton tracker for 3D quarry and area boundary selections in MINE mode (AREA sub-mode).
 * Stores the two selected bounding corners (Pos1 and Pos2), computes 3D bounding dimensions, volume,
 * calculates non-air and destructible block counts, handles vertical height adjustments,
 * and maintains synchronization with the held Command Scepter.
 */
public class ClientMiningCaptureTracker {

	public static final int MAX_DIMENSION = 64;
	public static final int MAX_HEIGHT = 96;
	public static final int MAX_VOLUME = 393216;

	private static BlockPos pos1 = null;
	private static BlockPos pos2 = null;
	private static Integer initialBaseY = null;
	private static final Set<Identifier> curatedBlockIds = new LinkedHashSet<>();

	public static synchronized Set<Identifier> getCuratedBlockIds() {
		return Collections.unmodifiableSet(curatedBlockIds);
	}

	public static synchronized void setCuratedBlockIds(Set<Identifier> ids) {
		curatedBlockIds.clear();
		if (ids != null) {
			curatedBlockIds.addAll(ids);
		}
	}

	public static synchronized boolean hasCuratedBlocks() {
		return !curatedBlockIds.isEmpty();
	}

	public static synchronized boolean isBlockCurated(Identifier id) {
		if (curatedBlockIds.isEmpty()) {
			return true;
		}
		return curatedBlockIds.contains(id);
	}

	public static synchronized void setPos1(BlockPos pos) {
		pos1 = pos != null ? pos.toImmutable() : null;
		if (pos1 != null && pos2 != null) {
			initialBaseY = Math.min(pos1.getY(), pos2.getY());
		} else if (pos1 != null) {
			initialBaseY = pos1.getY();
		} else if (pos2 == null) {
			initialBaseY = null;
		}
		syncWithHeldScepter();
	}

	public static synchronized BlockPos getPos1() {
		return pos1;
	}

	public static synchronized void setPos2(BlockPos pos) {
		pos2 = pos != null ? pos.toImmutable() : null;
		if (pos1 != null && pos2 != null) {
			initialBaseY = Math.min(pos1.getY(), pos2.getY());
		} else if (pos2 != null) {
			initialBaseY = pos2.getY();
		} else if (pos1 == null) {
			initialBaseY = null;
		}
		syncWithHeldScepter();
	}

	public static synchronized BlockPos getPos2() {
		return pos2;
	}

	public static synchronized void setCorners(BlockPos p1, BlockPos p2) {
		pos1 = p1 != null ? p1.toImmutable() : null;
		pos2 = p2 != null ? p2.toImmutable() : null;
		if (p1 != null && p2 != null) {
			initialBaseY = Math.min(p1.getY(), p2.getY());
		} else if (p1 != null) {
			initialBaseY = p1.getY();
		} else if (p2 != null) {
			initialBaseY = p2.getY();
		} else {
			initialBaseY = null;
		}
		syncWithHeldScepter();
	}

	public static synchronized void clear() {
		pos1 = null;
		pos2 = null;
		initialBaseY = null;
		curatedBlockIds.clear();
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc != null && mc.player != null) {
				ItemStack held = CommandScepterItem.getHeldScepter(mc.player);
				if (!held.isEmpty()) {
					CommandScepterItem.clearMineCorners(held);
				}
			}
		} catch (Throwable ignored) {
			// Headless / mock environment safety
		}
	}

	public static synchronized boolean hasPos1() {
		return pos1 != null;
	}

	public static synchronized boolean hasPos2() {
		return pos2 != null;
	}

	public static synchronized boolean hasCompleteSelection() {
		return pos1 != null && pos2 != null;
	}

	/**
	 * Sequentially advances corner selection with a left-click:
	 * - If Pos1 is null or if both Pos1 and Pos2 are already set, assigns Pos1 = pos and clears Pos2 (returns 1).
	 * - If Pos1 is present and Pos2 is null, assigns Pos2 = pos to complete the volume (returns 2).
	 *
	 * @param pos The clicked block position.
	 * @return 1 if Pos1 was set, 2 if Pos2 was set, or 0 if pos is null.
	 */
	public static synchronized int stepCorner(BlockPos pos) {
		if (pos == null) return 0;
		if (pos1 == null) {
			pos1 = pos.toImmutable();
			pos2 = null;
			initialBaseY = pos.getY();
			curatedBlockIds.clear();
			syncWithHeldScepter();
			return 1;
		} else if (pos2 == null) {
			pos2 = pos.toImmutable();
			initialBaseY = Math.min(pos1.getY(), pos2.getY());
			syncWithHeldScepter();
			return 2;
		} else {
			// Duplicate click guard
			if (pos.equals(pos2)) {
				return 2;
			}
			pos1 = pos.toImmutable();
			pos2 = null;
			initialBaseY = pos.getY();
			curatedBlockIds.clear();
			syncWithHeldScepter();
			return 1;
		}
	}

	public static synchronized int getSizeX() {
		if (pos1 == null || pos2 == null) return 0;
		return Math.abs(pos1.getX() - pos2.getX()) + 1;
	}

	public static synchronized int getSizeY() {
		if (pos1 == null || pos2 == null) return 0;
		return Math.abs(pos1.getY() - pos2.getY()) + 1;
	}

	public static synchronized int getSizeZ() {
		if (pos1 == null || pos2 == null) return 0;
		return Math.abs(pos1.getZ() - pos2.getZ()) + 1;
	}

	public static synchronized long getVolume() {
		if (pos1 == null || pos2 == null) return 0L;
		return (long) getSizeX() * getSizeY() * getSizeZ();
	}

	public static synchronized boolean isWithinLimit() {
		if (!hasCompleteSelection()) return false;
		return getSizeX() <= MAX_DIMENSION
			&& getSizeY() <= MAX_HEIGHT
			&& getSizeZ() <= MAX_DIMENSION
			&& getVolume() <= MAX_VOLUME;
	}

	public static synchronized BlockBox getBlockBox() {
		if (pos1 == null || pos2 == null) return null;
		int minX = Math.min(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ());
		int maxX = Math.max(pos1.getX(), pos2.getX());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int maxZ = Math.max(pos1.getZ(), pos2.getZ());
		return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public static synchronized Box getBox() {
		if (pos1 == null || pos2 == null) return null;
		int minX = Math.min(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ());
		int maxX = Math.max(pos1.getX(), pos2.getX());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int maxZ = Math.max(pos1.getZ(), pos2.getZ());
		return new Box(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
	}

	public static synchronized String getDimensionString() {
		if (!hasCompleteSelection()) {
			return "Incomplete Selection";
		}
		return getSizeX() + "x" + getSizeY() + "x" + getSizeZ() + " (" + getVolume() + "b)";
	}

	/**
	 * Computes the count of destructible (non-air, hardness >= 0) blocks enclosed within the selection.
	 *
	 * @return Count of destructible blocks in the client world volume.
	 */
	public static synchronized int getDestructibleBlockCount() {
		if (pos1 == null || pos2 == null) {
			return 0;
		}
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc != null && mc.world != null) {
				int minX = Math.min(pos1.getX(), pos2.getX());
				int maxX = Math.max(pos1.getX(), pos2.getX());
				int minY = Math.min(pos1.getY(), pos2.getY());
				int maxY = Math.max(pos1.getY(), pos2.getY());
				int minZ = Math.min(pos1.getZ(), pos2.getZ());
				int maxZ = Math.max(pos1.getZ(), pos2.getZ());
				int count = 0;
				BlockPos.Mutable m = new BlockPos.Mutable();
				for (int y = minY; y <= maxY; y++) {
					for (int x = minX; x <= maxX; x++) {
						for (int z = minZ; z <= maxZ; z++) {
							m.set(x, y, z);
							BlockState state = mc.world.getBlockState(m);
							if (state != null && !state.isAir()) {
								float hardness = state.getHardness(mc.world, m);
								if (hardness >= 0.0F) {
									Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
									if (isBlockCurated(blockId)) {
										count++;
									}
								}
							}
						}
					}
				}
				return count;
			}
		} catch (Throwable ignored) {
			// Headless / mock environment safety
		}
		return (int) Math.min(Integer.MAX_VALUE, getVolume());
	}

	private static int getWorldBottomY() {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc != null && mc.world != null) {
				return mc.world.getBottomY();
			}
		} catch (Throwable ignored) {
		}
		return -64;
	}

	public static synchronized int getInitialBaseY() {
		if (initialBaseY != null) {
			return initialBaseY;
		}
		if (pos1 != null && pos2 != null) {
			return Math.min(pos1.getY(), pos2.getY());
		}
		if (pos1 != null) {
			return pos1.getY();
		}
		if (pos2 != null) {
			return pos2.getY();
		}
		return 64;
	}

	public static synchronized boolean isUnderground() {
		if (pos1 == null || pos2 == null) return false;
		int baseY = getInitialBaseY();
		int minY = Math.min(pos1.getY(), pos2.getY());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		return minY < baseY && maxY <= baseY;
	}

	public static synchronized int getUndergroundDepth() {
		if (!isUnderground()) return 0;
		return getInitialBaseY() - Math.min(pos1.getY(), pos2.getY());
	}

	/**
	 * Adjusts the vertical bounds of the current mining selection box by {@code delta} blocks.
	 * Unified bi-directional control:
	 * - Above the baseline surface layer: delta expands upward (+delta) or shrinks downward (-delta).
	 * - At baseline surface layer (height 1): decreasing past 1 expands downward into the ground.
	 * - Below baseline surface layer: delta digs deeper underground (-delta) or shrinks back up (+delta).
	 *
	 * @param delta Number of blocks to adjust (+1, +5, -1, -5).
	 * @return The new height in blocks (getSizeY()).
	 */
	public static synchronized int adjustHeight(int delta) {
		if (pos1 == null && pos2 == null) {
			return 0;
		}

		int worldBottom = getWorldBottomY();
		int baseY = getInitialBaseY();

		if (pos1 != null && pos2 == null) {
			if (delta >= 0) {
				int targetY = Math.min(baseY + MAX_HEIGHT - 1, pos1.getY() + delta);
				pos2 = new BlockPos(pos1.getX(), targetY, pos1.getZ());
			} else {
				// Extend underground below Pos1
				int targetY = Math.max(worldBottom, Math.max(baseY - MAX_HEIGHT + 1, pos1.getY() + delta));
				pos2 = new BlockPos(pos1.getX(), targetY, pos1.getZ());
			}
			syncWithHeldScepter();
			return getSizeY();
		}

		int minY = Math.min(pos1.getY(), pos2.getY());
		int maxY = Math.max(pos1.getY(), pos2.getY());

		if (minY >= baseY) {
			// Box is on or above the baseline surface layer
			if (delta > 0) {
				int newMaxY = Math.min(baseY + MAX_HEIGHT - 1, maxY + delta);
				maxY = newMaxY;
				minY = baseY;
			} else if (delta < 0) {
				int canShrinkAbove = maxY - baseY;
				int absDelta = -delta;
				if (absDelta <= canShrinkAbove) {
					maxY -= absDelta;
					minY = baseY;
				} else {
					// Shrink all the way to baseY, then remaining delta expands underground!
					int remaining = absDelta - canShrinkAbove;
					maxY = baseY;
					minY = Math.max(worldBottom, Math.max(baseY - MAX_HEIGHT + 1, baseY - remaining));
				}
			}
		} else if (maxY <= baseY) {
			// Box is on or below the baseline surface layer (underground)
			if (delta < 0) {
				// Dig deeper underground
				int newMinY = Math.max(worldBottom, Math.max(baseY - MAX_HEIGHT + 1, minY + delta));
				minY = newMinY;
				maxY = baseY;
			} else if (delta > 0) {
				int canPullUp = baseY - minY;
				if (delta <= canPullUp) {
					minY += delta;
					maxY = baseY;
				} else {
					// Pull up all the way to baseY, then remaining delta expands above ground!
					int remaining = delta - canPullUp;
					minY = baseY;
					maxY = Math.min(baseY + MAX_HEIGHT - 1, baseY + remaining);
				}
			}
		} else {
			// Box spans both above and below baseY
			if (delta > 0) {
				maxY = Math.min(baseY + MAX_HEIGHT - 1, maxY + delta);
			} else if (delta < 0) {
				int canShrinkAbove = maxY - baseY;
				int absDelta = -delta;
				if (absDelta <= canShrinkAbove) {
					maxY -= absDelta;
				} else {
					int remaining = absDelta - canShrinkAbove;
					maxY = baseY;
					minY = Math.max(worldBottom, Math.max(baseY - MAX_HEIGHT + 1, minY - remaining));
				}
			}
		}

		// Reassign pos1 and pos2 Y coordinates to match [minY, maxY]
		if (pos1.getY() >= pos2.getY()) {
			pos1 = new BlockPos(pos1.getX(), maxY, pos1.getZ());
			pos2 = new BlockPos(pos2.getX(), minY, pos2.getZ());
		} else {
			pos1 = new BlockPos(pos1.getX(), minY, pos1.getZ());
			pos2 = new BlockPos(pos2.getX(), maxY, pos2.getZ());
		}

		syncWithHeldScepter();
		return getSizeY();
	}

	/**
	 * Explicitly sets the height of the current mining selection box to {@code targetHeight} blocks.
	 * Clamps height to between 1 and MAX_HEIGHT (96).
	 *
	 * @param targetHeight Desired height in blocks.
	 * @return The resulting height in blocks (getSizeY()).
	 */
	public static synchronized int setHeight(int targetHeight) {
		if (pos1 == null || pos2 == null) return 0;
		int clampedHeight = Math.max(1, Math.min(MAX_HEIGHT, targetHeight));
		int worldBottom = getWorldBottomY();
		int baseY = getInitialBaseY();

		int minY;
		int maxY;
		if (isUnderground()) {
			// Preserve underground orientation if currently underground
			maxY = baseY;
			minY = Math.max(worldBottom, baseY - clampedHeight + 1);
		} else {
			minY = Math.min(baseY, Math.min(pos1.getY(), pos2.getY()));
			maxY = minY + clampedHeight - 1;
		}

		if (pos1.getY() >= pos2.getY()) {
			pos1 = new BlockPos(pos1.getX(), maxY, pos1.getZ());
			pos2 = new BlockPos(pos2.getX(), minY, pos2.getZ());
		} else {
			pos1 = new BlockPos(pos1.getX(), minY, pos1.getZ());
			pos2 = new BlockPos(pos2.getX(), maxY, pos2.getZ());
		}

		syncWithHeldScepter();
		return getSizeY();
	}

	private static void syncWithHeldScepter() {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc != null && mc.player != null) {
				ItemStack held = CommandScepterItem.getHeldScepter(mc.player);
				if (!held.isEmpty()) {
					CommandScepterItem.setMineCorners(held, pos1, pos2);
				}
			}
		} catch (Throwable ignored) {
			// Headless / mock environment safety
		}
	}
}
