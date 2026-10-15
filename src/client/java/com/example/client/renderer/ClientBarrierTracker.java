package com.example.client.renderer;

import com.example.barrier.BarrierZone;
import com.example.network.ModifyBarrierZonePayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockPos;

/**
 * Client-side singleton tracker for dynamic spatial barrier zones (Sovereign Wards).
 * Stores pending multi-node boundary points (Pos 1, 2, 3.. N), manages vertical height adjustments,
 * maintains confirmed zones synced from server, and dispatches mutation packets.
 */
public class ClientBarrierTracker {

	private static final List<BlockPos> pendingNodes = Collections.synchronizedList(new ArrayList<>());
	private static final List<BarrierZone> confirmedZones = Collections.synchronizedList(new ArrayList<>());
	private static int heightAdjustment = 0;
	private static int cachedMinY = -64;
	private static int cachedMaxY = 320;

	public static synchronized List<BlockPos> getPendingNodes() {
		return new ArrayList<>(pendingNodes);
	}

	public static synchronized int getPendingNodeCount() {
		return pendingNodes.size();
	}

	public static synchronized boolean hasPendingNodes() {
		return !pendingNodes.isEmpty();
	}

	public static synchronized int addNode(BlockPos pos) {
		if (pos == null) return pendingNodes.size();
		BlockPos imm = pos.toImmutable();

		// Prevent consecutive duplicate clicks on the exact same block
		if (!pendingNodes.isEmpty() && pendingNodes.get(pendingNodes.size() - 1).equals(imm)) {
			return pendingNodes.size();
		}

		pendingNodes.add(imm);
		recalculateBounds();

		try {
			if (ClientPlayNetworking.canSend(ModifyBarrierZonePayload.ID)) {
				ClientPlayNetworking.send(new ModifyBarrierZonePayload(
					ModifyBarrierZonePayload.Action.ADD_NODE,
					0,
					imm,
					cachedMinY,
					cachedMaxY,
					""
				));
			}
		} catch (Throwable ignored) {
			// Unit test / mock safety
		}

		return pendingNodes.size();
	}

	public static synchronized BlockPos removeLastNode() {
		if (pendingNodes.isEmpty()) return null;
		BlockPos removed = pendingNodes.remove(pendingNodes.size() - 1);
		recalculateBounds();

		try {
			if (ClientPlayNetworking.canSend(ModifyBarrierZonePayload.ID)) {
				ClientPlayNetworking.send(new ModifyBarrierZonePayload(
					ModifyBarrierZonePayload.Action.REMOVE_LAST_NODE,
					0,
					removed != null ? removed : BlockPos.ORIGIN,
					cachedMinY,
					cachedMaxY,
					""
				));
			}
		} catch (Throwable ignored) {}

		return removed;
	}

	public static synchronized void clear() {
		pendingNodes.clear();
		heightAdjustment = 0;
		cachedMinY = -64;
		cachedMaxY = 320;

		try {
			if (ClientPlayNetworking.canSend(ModifyBarrierZonePayload.ID)) {
				ClientPlayNetworking.send(new ModifyBarrierZonePayload(
					ModifyBarrierZonePayload.Action.CLEAR_ACTIVE,
					0,
					BlockPos.ORIGIN,
					cachedMinY,
					cachedMaxY,
					""
				));
			}
		} catch (Throwable ignored) {}
	}

	public static synchronized boolean seal(String customName) {
		if (pendingNodes.size() < 2) return false;
		recalculateBounds();

		String finalName = (customName != null && !customName.isBlank()) ? customName.trim() : "";

		try {
			if (ClientPlayNetworking.canSend(ModifyBarrierZonePayload.ID)) {
				ClientPlayNetworking.send(new ModifyBarrierZonePayload(
					ModifyBarrierZonePayload.Action.SEAL_ZONE,
					0,
					BlockPos.ORIGIN,
					cachedMinY,
					cachedMaxY,
					finalName
				));
			}
		} catch (Throwable ignored) {}

		pendingNodes.clear();
		heightAdjustment = 0;
		return true;
	}

	public static synchronized boolean seal() {
		return seal("");
	}

	public static synchronized boolean renameZone(int zoneId, String newName) {
		if (newName == null || newName.isBlank()) return false;
		String trimmed = newName.trim();
		for (BarrierZone z : confirmedZones) {
			if (z.getId() == zoneId) {
				z.setName(trimmed);
				try {
					if (ClientPlayNetworking.canSend(ModifyBarrierZonePayload.ID)) {
						ClientPlayNetworking.send(new ModifyBarrierZonePayload(
							ModifyBarrierZonePayload.Action.RENAME_ZONE,
							zoneId,
							BlockPos.ORIGIN,
							z.getMinY(),
							z.getMaxY(),
							trimmed
						));
					}
				} catch (Throwable ignored) {}
				return true;
			}
		}
		return false;
	}

	public static synchronized boolean resumeZone(int zoneId) {
		return resumeZoneAt(zoneId, null, null);
	}

	public static synchronized boolean resumeZoneAt(int zoneId, BlockPos targetPos, BlockPos clickedPos) {
		BarrierZone target = null;
		for (BarrierZone z : confirmedZones) {
			if (z.getId() == zoneId) {
				target = z;
				break;
			}
		}
		if (target == null) return false;

		List<BlockPos> orig = target.getNodes();
		if (orig.isEmpty()) return false;

		// Identify which waypoint within the zone was targeted/clicked
		int clickedIdx = -1;
		if (targetPos != null || clickedPos != null) {
			for (int i = 0; i < orig.size(); i++) {
				if (isNodeMatch(orig.get(i), targetPos, clickedPos)) {
					clickedIdx = i;
					break;
				}
			}
		}

		pendingNodes.clear();
		int n = orig.size();
		if (clickedIdx >= 0) {
			// Rotate loop cycle so the clicked waypoint is the LAST node (active extension head/tail)
			// The next node in the cycle (clickedIdx + 1) becomes Point #1 to snap back to when closing & sealing
			for (int i = 0; i < n; i++) {
				int idx = (clickedIdx + 1 + i) % n;
				pendingNodes.add(orig.get(idx));
			}
		} else {
			for (BlockPos p : orig) {
				pendingNodes.add(p);
			}
		}

		cachedMinY = target.getMinY();
		cachedMaxY = target.getMaxY();
		heightAdjustment = 0;

		try {
			if (ClientPlayNetworking.canSend(ModifyBarrierZonePayload.ID)) {
				ClientPlayNetworking.send(new ModifyBarrierZonePayload(
					ModifyBarrierZonePayload.Action.RESUME_ZONE,
					target.getId(),
					BlockPos.ORIGIN,
					cachedMinY,
					cachedMaxY,
					target.getName()
				));
			}
		} catch (Throwable ignored) {}

		return true;
	}

	public static synchronized int findPendingNodeIndex(BlockPos targetPos, BlockPos clickedPos) {
		for (int i = 0; i < pendingNodes.size(); i++) {
			if (isNodeMatch(pendingNodes.get(i), targetPos, clickedPos)) {
				return i;
			}
		}
		return -1;
	}

	public static synchronized boolean truncatePendingTo(int index) {
		if (index >= 0 && index < pendingNodes.size()) {
			while (pendingNodes.size() > index + 1) {
				pendingNodes.remove(pendingNodes.size() - 1);
			}
			recalculateBounds();
			return true;
		}
		return false;
	}

	public static synchronized BarrierZone findConfirmedZoneAt(BlockPos targetPos, BlockPos clickedPos) {
		for (BarrierZone z : confirmedZones) {
			for (BlockPos p : z.getNodes()) {
				if (isNodeMatch(p, targetPos, clickedPos)) {
					return z;
				}
			}
		}
		return null;
	}

	public static synchronized BlockPos findConfirmedNodeAt(BlockPos targetPos, BlockPos clickedPos) {
		for (BarrierZone z : confirmedZones) {
			for (BlockPos p : z.getNodes()) {
				if (isNodeMatch(p, targetPos, clickedPos)) {
					return p;
				}
			}
		}
		return null;
	}

	public static synchronized boolean containsPendingNode(BlockPos pos) {
		if (pos == null) return false;
		for (BlockPos p : pendingNodes) {
			if (p.equals(pos)) {
				return true;
			}
		}
		return false;
	}

	public static synchronized boolean startExtensionFrom(BlockPos node) {
		if (node == null) return false;
		clear();
		addNode(node);
		return true;
	}

	public static synchronized BlockPos findCloseTarget(BlockPos targetPos, BlockPos clickedPos) {
		if (pendingNodes.isEmpty()) return null;

		BlockPos lastNode = pendingNodes.get(pendingNodes.size() - 1);

		// 1. Can close on Point #1 of current draft if size >= 2
		if (pendingNodes.size() >= 2) {
			BlockPos firstNode = pendingNodes.get(0);
			if (isNodeMatch(firstNode, targetPos, clickedPos)) {
				return firstNode;
			}
		}

		// 2. Can close on any intermediate node in current draft (except the current extension tail)
		if (pendingNodes.size() >= 3) {
			for (int i = 1; i < pendingNodes.size() - 1; i++) {
				BlockPos node = pendingNodes.get(i);
				if (isNodeMatch(node, targetPos, clickedPos)) {
					return node;
				}
			}
		}

		// 3. Can close on any existing confirmed waypoint from any sealed barrier (except the current extension tail)
		if (pendingNodes.size() >= 1) {
			for (BarrierZone zone : confirmedZones) {
				for (BlockPos p : zone.getNodes()) {
					if (!p.equals(lastNode) && isNodeMatch(p, targetPos, clickedPos)) {
						return p;
					}
				}
			}
		}

		return null;
	}

	public static synchronized boolean isFirstNode(BlockPos targetPos, BlockPos clickedPos) {
		if (pendingNodes.isEmpty()) return false;
		BlockPos first = pendingNodes.get(0);
		return isNodeMatch(first, targetPos, clickedPos);
	}

	/**
	 * Loads the most recent (or nearest) sealed barrier zone's nodes into pending editing mode
	 * and dispatches a RESUME_ZONE packet to the server so players can seamlessly expand their base without starting over.
	 *
	 * @return True if a sealed zone was resumed, false if no zones exist.
	 */
	public static synchronized boolean resumeLatestZone() {
		return resumeNearestOrLatestZone(null);
	}

	public static synchronized boolean resumeNearestOrLatestZone(BlockPos playerPos) {
		if (confirmedZones.isEmpty()) return false;
		BarrierZone target = null;
		BlockPos closestNode = null;
		if (playerPos != null) {
			for (BarrierZone z : confirmedZones) {
				if (z.contains(playerPos)) {
					target = z;
					break;
				}
			}
			double closestDistSq = Double.MAX_VALUE;
			for (BarrierZone z : (target != null ? List.of(target) : confirmedZones)) {
				for (BlockPos p : z.getNodes()) {
					double d = p.getSquaredDistance(playerPos);
					if (d < closestDistSq) {
						closestDistSq = d;
						target = z;
						closestNode = p;
					}
				}
			}
		}
		if (target == null) {
			target = confirmedZones.get(confirmedZones.size() - 1);
		}

		return resumeZoneAt(target.getId(), closestNode, closestNode);
	}

	public static synchronized int adjustHeight(int step) {
		heightAdjustment += step;
		recalculateBounds();
		return cachedMaxY - cachedMinY + 1;
	}

	public static synchronized int getMinY() {
		return cachedMinY;
	}

	public static synchronized int getMaxY() {
		return cachedMaxY;
	}

	public static synchronized void recalculateBounds() {
		if (pendingNodes.isEmpty()) {
			cachedMinY = -64;
			cachedMaxY = 320;
			return;
		}
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (BlockPos p : pendingNodes) {
			if (p.getY() < min) min = p.getY();
			if (p.getY() > max) max = p.getY();
		}
		cachedMinY = Math.max(-64, min - BarrierZone.DEFAULT_VERTICAL_EXPANSION_DOWN);
		cachedMaxY = Math.min(320, max + BarrierZone.DEFAULT_VERTICAL_EXPANSION_UP + heightAdjustment);
		if (cachedMaxY <= cachedMinY) {
			cachedMaxY = cachedMinY + 1;
		}
	}

	public static synchronized String getDimensionString() {
		int count = pendingNodes.size();
		int height = cachedMaxY - cachedMinY + 1;
		return count + " Points | Height: " + height + " blocks (Y: " + cachedMinY + " → " + cachedMaxY + ")";
	}

	public static synchronized List<BarrierZone> getConfirmedZones() {
		confirmedZones.removeIf(z -> z == null || z.getNodeCount() < 2);
		return new ArrayList<>(confirmedZones);
	}

	public static synchronized void setConfirmedZones(List<BarrierZone> zones) {
		confirmedZones.clear();
		if (zones != null) {
			for (BarrierZone z : zones) {
				if (z != null && z.getNodeCount() >= 2) {
					confirmedZones.add(z);
				}
			}
		}
	}

	public static synchronized BlockPos findMatchingPendingNode(BlockPos targetPos, BlockPos clickedPos) {
		return findMatchingPendingNode(targetPos, clickedPos, null, null, 0.0D);
	}

	public static synchronized BlockPos findMatchingPendingNode(BlockPos targetPos, BlockPos clickedPos, net.minecraft.util.math.Vec3d rayOrigin, net.minecraft.util.math.Vec3d rayDir, double maxReach) {
		if (pendingNodes.isEmpty()) return null;

		// 1. Exact coordinate match when a block is clicked or targeted
		for (BlockPos p : pendingNodes) {
			if (isNodeMatch(p, targetPos, clickedPos)) {
				return p;
			}
		}

		// 2. Raycast check ONLY when clicking open air (i.e. targetPos == null && clickedPos == null)
		// and aiming directly at the floating hologram marker cube (within reach)
		if (targetPos == null && clickedPos == null && rayOrigin != null && rayDir != null && maxReach > 0.0D) {
			double reach = Math.min(maxReach, 6.0D);
			net.minecraft.util.math.Vec3d rayEnd = rayOrigin.add(rayDir.multiply(reach));
			BlockPos closest = null;
			double closestDist = Double.MAX_VALUE;

			for (BlockPos p : pendingNodes) {
				net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(p);
				java.util.Optional<net.minecraft.util.math.Vec3d> hit = box.raycast(rayOrigin, rayEnd);
				if (hit.isPresent()) {
					double dist = hit.get().squaredDistanceTo(rayOrigin);
					if (dist < closestDist) {
						closestDist = dist;
						closest = p;
					}
				}
			}
			return closest;
		}

		return null;
	}

	public static synchronized BarrierZone findMatchingConfirmedZone(BlockPos targetPos, BlockPos clickedPos) {
		return findMatchingConfirmedZone(targetPos, clickedPos, null, null, 0.0D);
	}

	public static synchronized BarrierZone findMatchingConfirmedZone(BlockPos targetPos, BlockPos clickedPos, net.minecraft.util.math.Vec3d rayOrigin, net.minecraft.util.math.Vec3d rayDir, double maxReach) {
		if (confirmedZones.isEmpty()) return null;

		// 1. Direct block hit check
		for (BarrierZone zone : confirmedZones) {
			for (BlockPos p : zone.getNodes()) {
				if (isNodeMatch(p, targetPos, clickedPos)) {
					return zone;
				}
			}
		}

		// 2. Raycast check ONLY when clicking open air (targetPos == null && clickedPos == null)
		if (targetPos == null && clickedPos == null && rayOrigin != null && rayDir != null && maxReach > 0.0D) {
			double reach = Math.min(maxReach, 6.0D);
			net.minecraft.util.math.Vec3d rayEnd = rayOrigin.add(rayDir.multiply(reach));
			BarrierZone closest = null;
			double closestDist = Double.MAX_VALUE;

			for (BarrierZone zone : confirmedZones) {
				for (BlockPos p : zone.getNodes()) {
					net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(p);
					java.util.Optional<net.minecraft.util.math.Vec3d> hit = box.raycast(rayOrigin, rayEnd);
					if (hit.isPresent()) {
						double dist = hit.get().squaredDistanceTo(rayOrigin);
						if (dist < closestDist) {
							closestDist = dist;
							closest = zone;
						}
					}
				}
			}
			return closest;
		}

		return null;
	}

	/**
	 * Removes ONLY the single targeted barrier node/waypoint, keeping all other connected nodes intact.
	 *
	 * @param targetPos Target block position offset on solid ground.
	 * @param clickedPos Directly clicked block coordinate.
	 * @param rayOrigin Eye origin if raycasting floating marker.
	 * @param rayDir Eye look vector if raycasting floating marker.
	 * @param maxReach Reach distance.
	 * @return The specific BlockPos node removed, or null if no node was hit.
	 */
	public static synchronized BlockPos removeNodeFromAnyBarrier(BlockPos targetPos, BlockPos clickedPos, net.minecraft.util.math.Vec3d rayOrigin, net.minecraft.util.math.Vec3d rayDir, double maxReach) {
		// 1. Check pending nodes first
		BlockPos pending = findMatchingPendingNode(targetPos, clickedPos, rayOrigin, rayDir, maxReach);
		if (pending != null) {
			removePendingNode(pending);
			return pending;
		}

		// 2. Check confirmed zones for matching individual node (exact coordinate match)
		for (BarrierZone zone : new ArrayList<>(confirmedZones)) {
			for (BlockPos p : new ArrayList<>(zone.getNodes())) {
				if (isNodeMatch(p, targetPos, clickedPos)) {
					removeNodeFromConfirmedZone(zone, p);
					return p;
				}
			}
		}

		// 3. Raycast check against confirmed zones ONLY when clicking open air (targetPos == null && clickedPos == null)
		if (targetPos == null && clickedPos == null && rayOrigin != null && rayDir != null && maxReach > 0.0D && !confirmedZones.isEmpty()) {
			double reach = Math.min(maxReach, 6.0D);
			net.minecraft.util.math.Vec3d rayEnd = rayOrigin.add(rayDir.multiply(reach));
			BarrierZone closestRayZone = null;
			BlockPos closestRayNode = null;
			double closestRayDist = Double.MAX_VALUE;

			for (BarrierZone zone : new ArrayList<>(confirmedZones)) {
				for (BlockPos p : new ArrayList<>(zone.getNodes())) {
					net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(p);
					java.util.Optional<net.minecraft.util.math.Vec3d> hit = box.raycast(rayOrigin, rayEnd);
					if (hit.isPresent()) {
						double dist = hit.get().squaredDistanceTo(rayOrigin);
						if (dist < closestRayDist) {
							closestRayDist = dist;
							closestRayZone = zone;
							closestRayNode = p;
						}
					}
				}
			}

			if (closestRayZone != null && closestRayNode != null) {
				removeNodeFromConfirmedZone(closestRayZone, closestRayNode);
				return closestRayNode;
			}
		}

		return null;
	}

	public static synchronized boolean removeNodeFromConfirmedZone(BarrierZone zone, BlockPos node) {
		if (zone == null || node == null) return false;
		boolean removed = zone.removeNode(node);
		if (removed) {
			if (zone.getNodeCount() < 2) {
				confirmedZones.remove(zone);
			}
			try {
				if (ClientPlayNetworking.canSend(ModifyBarrierZonePayload.ID)) {
					ClientPlayNetworking.send(new ModifyBarrierZonePayload(
						ModifyBarrierZonePayload.Action.REMOVE_NODE,
						zone.getId(),
						node,
						zone.getMinY(),
						zone.getMaxY(),
						zone.getName()
					));
				}
			} catch (Throwable ignored) {}
		}
		return removed;
	}

	public static boolean isNodeMatch(BlockPos node, BlockPos targetPos, BlockPos clickedPos) {
		if (node == null) return false;
		if (targetPos != null && node.equals(targetPos)) {
			return true;
		}
		if (clickedPos != null && (node.equals(clickedPos) || node.equals(clickedPos.up()))) {
			return true;
		}
		return false;
	}

	public static synchronized boolean removePendingNode(BlockPos node) {
		if (node == null || pendingNodes.isEmpty()) return false;
		boolean removed = pendingNodes.removeIf(p -> p.equals(node));
		if (removed) {
			recalculateBounds();
			try {
				if (ClientPlayNetworking.canSend(ModifyBarrierZonePayload.ID)) {
					ClientPlayNetworking.send(new ModifyBarrierZonePayload(
						ModifyBarrierZonePayload.Action.REMOVE_NODE,
						0,
						node,
						cachedMinY,
						cachedMaxY,
						""
					));
				}
			} catch (Throwable ignored) {}
		}
		return removed;
	}

	public static synchronized boolean deleteConfirmedZone(int zoneId) {
		boolean removed = confirmedZones.removeIf(z -> z.getId() == zoneId);
		try {
			if (ClientPlayNetworking.canSend(ModifyBarrierZonePayload.ID)) {
				ClientPlayNetworking.send(new ModifyBarrierZonePayload(
					ModifyBarrierZonePayload.Action.DELETE_ZONE,
					zoneId,
					BlockPos.ORIGIN,
					cachedMinY,
					cachedMaxY,
					""
				));
			}
		} catch (Throwable ignored) {}
		return removed;
	}
}
