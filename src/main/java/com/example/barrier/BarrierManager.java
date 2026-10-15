package com.example.barrier;

import com.example.network.SyncBarrierZonesPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

/**
 * Server-side singleton manager for player barrier zones (Sovereign Wards).
 * Manages zone creation, node expansion, sealing, point-in-polygon containment checks,
 * world persistence via {@link BarrierPersistentState}, and client synchronization.
 */
public class BarrierManager {

	private static final BarrierManager INSTANCE = new BarrierManager();

	private final Map<UUID, List<BarrierZone>> playerZones = new ConcurrentHashMap<>();
	private BarrierPersistentState persistentState = null;
	private PersistentStateManager stateManager = null;

	private BarrierManager() {}

	public static BarrierManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Initializes the manager with the world's PersistentStateManager on server startup.
	 *
	 * @param overworld The primary server overworld instance.
	 */
	public synchronized void init(ServerWorld overworld) {
		if (overworld == null) return;
		this.stateManager = overworld.getPersistentStateManager();
		this.persistentState = this.stateManager.getOrCreate(
			BarrierPersistentState.TYPE,
			BarrierPersistentState.KEY
		);
		loadFromPersistentState(this.persistentState);
	}

	public void loadFromPersistentState(BarrierPersistentState state) {
		if (state == null) return;
		this.playerZones.clear();
		for (Map.Entry<UUID, List<BarrierZone>> entry : state.getPlayerZones().entrySet()) {
			List<BarrierZone> clean = new ArrayList<>();
			if (entry.getValue() != null) {
				for (BarrierZone z : entry.getValue()) {
					if (z != null && z.getNodeCount() >= 2) {
						clean.add(z);
					}
				}
			}
			this.playerZones.put(entry.getKey(), Collections.synchronizedList(clean));
		}
	}

	public void markDirty() {
		if (this.persistentState != null) {
			this.persistentState.getPlayerZones().clear();
			for (Map.Entry<UUID, List<BarrierZone>> entry : this.playerZones.entrySet()) {
				synchronized (entry.getValue()) {
					this.persistentState.getPlayerZones().put(entry.getKey(), new ArrayList<>(entry.getValue()));
				}
			}
			this.persistentState.markDirty();
		}
	}

	public void onServerStopping() {
		markDirty();
	}

	/**
	 * Checks whether the given block position is protected by any sealed Sovereign Ward.
	 * Minions will never harvest or break blocks inside protected barrier zones unless ordered.
	 *
	 * @param world World instance.
	 * @param pos Block position.
	 * @param ownerUuid Minion or player UUID.
	 * @return True if pos is contained in a protected barrier perimeter.
	 */
	public boolean isPositionProtected(World world, BlockPos pos, UUID ownerUuid) {
		if (pos == null) return false;

		// 1. Check zones owned by the specific owner
		if (ownerUuid != null) {
			List<BarrierZone> zones = this.playerZones.get(ownerUuid);
			if (zones != null) {
				synchronized (zones) {
					for (BarrierZone zone : zones) {
						if (zone.isSealed() && zone.contains(pos)) {
							return true;
						}
					}
				}
			}
		}

		// 2. Sovereign Realm Protection: Check all sealed zones in the world to protect base perimeters universally
		for (List<BarrierZone> zones : this.playerZones.values()) {
			if (zones == null) continue;
			synchronized (zones) {
				for (BarrierZone zone : zones) {
					if (zone.isSealed() && zone.contains(pos)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	public List<BarrierZone> getZones(UUID ownerUuid) {
		if (ownerUuid == null) return Collections.emptyList();
		List<BarrierZone> zones = this.playerZones.get(ownerUuid);
		if (zones == null) return Collections.emptyList();
		synchronized (zones) {
			zones.removeIf(z -> z == null || (z.isSealed() && z.getNodeCount() < 2));
			List<BarrierZone> sealedZones = new ArrayList<>();
			for (BarrierZone z : zones) {
				if (z.isSealed() && z.getNodeCount() >= 2) {
					sealedZones.add(z);
				}
			}
			return sealedZones;
		}
	}

	public BarrierZone getZone(UUID ownerUuid, int zoneId) {
		if (ownerUuid == null) return null;
		List<BarrierZone> zones = this.playerZones.get(ownerUuid);
		if (zones == null) return null;
		synchronized (zones) {
			for (BarrierZone z : zones) {
				if (z.getId() == zoneId) return z;
			}
		}
		return null;
	}

	/**
	 * Gets the current unsealed zone being edited by the player, or creates a new one.
	 */
	public synchronized BarrierZone getOrCreateActiveZone(UUID ownerUuid) {
		if (ownerUuid == null) return null;
		List<BarrierZone> zones = this.playerZones.computeIfAbsent(
			ownerUuid,
			k -> Collections.synchronizedList(new ArrayList<>())
		);
		synchronized (zones) {
			for (BarrierZone z : zones) {
				if (!z.isSealed()) {
					return z;
				}
			}
			int nextId = 1;
			for (BarrierZone z : zones) {
				if (z.getId() >= nextId) {
					nextId = z.getId() + 1;
				}
			}
			BarrierZone newZone = new BarrierZone(nextId, ownerUuid, "Sovereign Barrier #" + nextId);
			zones.add(newZone);
			return newZone;
		}
	}

	/**
	 * Resumes an existing sealed barrier zone for dynamic editing/expansion.
	 * Keeps the original zone sealed and continuously protecting the base, while
	 * creating an active unsealed draft containing all existing nodes and seamlessly
	 * connecting the new expansion point to its nearest neighboring node.
	 *
	 * @param ownerUuid Player UUID.
	 * @param zoneId Target zone ID, or <= 0 for the most recent sealed zone.
	 * @param newPos Optional new perimeter coordinate to insert connected to nearest node.
	 * @return The unsealed draft zone ready for new nodes.
	 */
	public synchronized BarrierZone resumeZoneForEditing(UUID ownerUuid, int zoneId, BlockPos newPos) {
		if (ownerUuid == null) return null;
		List<BarrierZone> zones = this.playerZones.computeIfAbsent(
			ownerUuid,
			k -> Collections.synchronizedList(new ArrayList<>())
		);
		synchronized (zones) {
			BarrierZone target = null;
			if (zoneId > 0) {
				for (BarrierZone z : zones) {
					if (z.isSealed() && z.getId() == zoneId) {
						target = z;
						break;
					}
				}
			} else {
				for (int i = zones.size() - 1; i >= 0; i--) {
					if (zones.get(i).isSealed()) {
						target = zones.get(i);
						break;
					}
				}
			}
			if (target == null) return null;

			// Clear any other unsealed draft zones first
			zones.removeIf(z -> !z.isSealed());

			// Create active draft zone copying target nodes
			BarrierZone draft = new BarrierZone(target.getId(), ownerUuid, target.getName());
			for (BlockPos p : target.getNodes()) {
				draft.addNode(p);
			}
			draft.setMinY(target.getMinY());
			draft.setMaxY(target.getMaxY());
			draft.setSealed(false);
			draft.setResumedFromZoneId(target.getId());

			if (newPos != null && !newPos.equals(BlockPos.ORIGIN)) {
				int insertIndex = BarrierZone.findBestInsertionIndex(draft.getNodes(), newPos);
				draft.insertNode(insertIndex, newPos);
			}

			zones.add(draft);
			markDirty();
			return draft;
		}
	}

	public synchronized BarrierZone resumeZoneForEditing(UUID ownerUuid, int zoneId) {
		return resumeZoneForEditing(ownerUuid, zoneId, null);
	}

	/**
	 * Appends a node to the player's active barrier zone.
	 */
	public synchronized BarrierZone addNodeToActiveZone(UUID ownerUuid, BlockPos pos) {
		BarrierZone active = getOrCreateActiveZone(ownerUuid);
		if (active != null && pos != null) {
			active.addNode(pos);
			markDirty();
		}
		return active;
	}

	/**
	 * Removes the last placed node from the player's active zone.
	 */
	public synchronized BlockPos removeLastNodeFromActiveZone(UUID ownerUuid) {
		List<BarrierZone> zones = this.playerZones.get(ownerUuid);
		if (zones == null) return null;
		synchronized (zones) {
			for (BarrierZone z : zones) {
				if (!z.isSealed()) {
					BlockPos removed = z.removeLastNode();
					markDirty();
					return removed;
				}
			}
		}
		return null;
	}

	/**
	 * Removes a specific node from either an active unsealed zone or a confirmed/sealed zone.
	 * Only deletes the specific node targeted, preserving the rest of the connected barrier perimeters.
	 * If the zone has 0 nodes remaining after removal, the zone is deleted.
	 */
	public synchronized boolean removeNodeFromAnyZone(UUID ownerUuid, int zoneId, BlockPos targetPos) {
		List<BarrierZone> zones = this.playerZones.get(ownerUuid);
		if (zones == null || targetPos == null) return false;
		synchronized (zones) {
			if (zoneId > 0) {
				for (java.util.Iterator<BarrierZone> it = zones.iterator(); it.hasNext();) {
					BarrierZone z = it.next();
					if (z.getId() == zoneId) {
						boolean removed = z.removeNode(targetPos);
						if (removed) {
							if (z.getNodeCount() < 2) {
								it.remove();
							}
							markDirty();
							return true;
						}
					}
				}
			}

			// Check unsealed draft zones first
			for (java.util.Iterator<BarrierZone> it = zones.iterator(); it.hasNext();) {
				BarrierZone z = it.next();
				if (!z.isSealed() && z.removeNode(targetPos)) {
					if (z.getNodeCount() == 0) {
						it.remove();
					}
					markDirty();
					return true;
				}
			}

			// Check sealed zones
			for (java.util.Iterator<BarrierZone> it = zones.iterator(); it.hasNext();) {
				BarrierZone z = it.next();
				if (z.isSealed() && z.removeNode(targetPos)) {
					if (z.getNodeCount() < 2) {
						it.remove();
					}
					markDirty();
					return true;
				}
			}
		}
		return false;
	}

	public synchronized boolean removeNodeFromAnyZone(UUID ownerUuid, BlockPos targetPos) {
		return removeNodeFromAnyZone(ownerUuid, 0, targetPos);
	}

	/**
	 * Removes a specific node from the player's active unsealed zone.
	 */
	public synchronized boolean removeNodeFromActiveZone(UUID ownerUuid, BlockPos targetPos) {
		return removeNodeFromAnyZone(ownerUuid, 0, targetPos);
	}

	/**
	 * Clears unsealed editing nodes for the player.
	 */
	public synchronized void clearActiveZone(UUID ownerUuid) {
		List<BarrierZone> zones = this.playerZones.get(ownerUuid);
		if (zones == null) return;
		synchronized (zones) {
			zones.removeIf(z -> !z.isSealed());
		}
		markDirty();
	}

	/**
	 * Seals the active barrier zone, finalizing its perimeter and activating minion protection.
	 */
	public synchronized BarrierZone sealActiveZone(UUID ownerUuid, int zoneId, String customName, int customMinY, int customMaxY) {
		List<BarrierZone> zones = this.playerZones.get(ownerUuid);
		if (zones == null) return null;
		synchronized (zones) {
			BarrierZone active = null;
			for (BarrierZone z : zones) {
				if (!z.isSealed() && (zoneId <= 0 || z.getId() == zoneId)) {
					active = z;
					break;
				}
			}
			if (active != null) {
				int resumedId = active.getResumedFromZoneId();
				if (resumedId > 0) {
					BarrierZone original = null;
					for (BarrierZone z : zones) {
						if (z.isSealed() && z.getId() == resumedId) {
							original = z;
							break;
						}
					}
					if (original != null) {
						original.clearNodes();
						for (BlockPos p : active.getNodes()) {
							original.addNode(p);
						}
						if (customName != null && !customName.isBlank()) {
							original.setName(customName);
						} else {
							original.setName(active.getName());
						}
						if (customMinY < customMaxY) {
							original.setMinY(customMinY);
							original.setMaxY(customMaxY);
						} else {
							original.setMinY(active.getMinY());
							original.setMaxY(active.getMaxY());
						}
						original.setSealed(true);
						zones.remove(active);
						markDirty();
						return original;
					}
				}

				if (customName != null && !customName.isBlank()) {
					active.setName(customName);
				}
				if (customMinY < customMaxY) {
					active.setMinY(customMinY);
					active.setMaxY(customMaxY);
				}
				active.setSealed(true);
				markDirty();
				return active;
			}
		}
		return null;
	}

	/**
	 * Expands an existing sealed barrier zone by appending a new boundary point without starting over.
	 *
	 * @param ownerUuid Player UUID.
	 * @param zoneId Existing zone ID.
	 * @param newPos New perimeter coordinate.
	 * @return The updated BarrierZone.
	 */
	public synchronized BarrierZone expandZone(UUID ownerUuid, int zoneId, BlockPos newPos) {
		BarrierZone target = getZone(ownerUuid, zoneId);
		if (target != null && newPos != null) {
			target.addNode(newPos);
			target.setSealed(true);
			markDirty();
			return target;
		}
		return null;
	}

	/**
	 * Deletes a barrier zone.
	 */
	public synchronized boolean deleteZone(UUID ownerUuid, int zoneId) {
		List<BarrierZone> zones = this.playerZones.get(ownerUuid);
		if (zones == null) return false;
		boolean removed;
		synchronized (zones) {
			removed = zones.removeIf(z -> z.getId() == zoneId);
		}
		if (removed) {
			markDirty();
		}
		return removed;
	}

	/**
	 * Synchronizes all barrier zones for this player to their client.
	 */
	public void syncToPlayer(ServerPlayerEntity player) {
		if (player == null) return;
		List<BarrierZone> zones = getZones(player.getUuid());
		ServerPlayNetworking.send(player, new SyncBarrierZonesPayload(zones));
	}
}
