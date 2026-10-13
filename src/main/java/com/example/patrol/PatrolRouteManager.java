package com.example.patrol;

import com.example.entity.custom.MinionEntity;
import com.example.network.SyncPatrolRoutesPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Server-side singleton manager maintaining all designated {@link PatrolRoute}s
 * per player commander. Handles route querying, modifications, and synchronization
 * to clients via network payloads.
 */
public class PatrolRouteManager {

	private static final PatrolRouteManager INSTANCE = new PatrolRouteManager();

	private final Map<UUID, Map<Integer, PatrolRoute>> playerRoutes = new ConcurrentHashMap<>();
	private PatrolRoutePersistentState persistentState = null;
	private net.minecraft.world.PersistentStateManager stateManager = null;

	private PatrolRouteManager() {}

	public static PatrolRouteManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Initializes the manager with the world's PersistentStateManager on server startup/load.
	 *
	 * @param overworld The server overworld instance.
	 */
	public void init(net.minecraft.server.world.ServerWorld overworld) {
		if (overworld == null) return;
		this.stateManager = overworld.getPersistentStateManager();
		this.persistentState = this.stateManager.getOrCreate(
			PatrolRoutePersistentState.TYPE,
			PatrolRoutePersistentState.KEY
		);
		loadFromPersistentState(this.persistentState);
	}

	/**
	 * Loads routes directly from a persistent state instance.
	 */
	public void loadFromPersistentState(PatrolRoutePersistentState state) {
		this.persistentState = state;
		this.playerRoutes.clear();
		if (state != null) {
			for (Map.Entry<UUID, Map<Integer, PatrolRoute>> entry : state.getRoutes().entrySet()) {
				Map<Integer, PatrolRoute> playerMap = Collections.synchronizedMap(new LinkedHashMap<>());
				synchronized (entry.getValue()) {
					for (Map.Entry<Integer, PatrolRoute> routeEntry : entry.getValue().entrySet()) {
						playerMap.put(routeEntry.getKey(), routeEntry.getValue());
					}
				}
				this.playerRoutes.put(entry.getKey(), playerMap);
			}
		}
	}

	/**
	 * Called during server shutdown to finalize and save persistent route data.
	 */
	public void onServerStopping() {
		markDirtyAndPersist();
	}

	private void markDirtyAndPersist() {
		if (this.persistentState != null) {
			this.persistentState.getRoutes().clear();
			for (Map.Entry<UUID, Map<Integer, PatrolRoute>> entry : this.playerRoutes.entrySet()) {
				Map<Integer, PatrolRoute> persistentPlayerMap = Collections.synchronizedMap(new LinkedHashMap<>());
				synchronized (entry.getValue()) {
					persistentPlayerMap.putAll(entry.getValue());
				}
				this.persistentState.getRoutes().put(entry.getKey(), persistentPlayerMap);
			}
			this.persistentState.markDirty();
			if (this.stateManager != null) {
				try {
					this.stateManager.save();
				} catch (Exception e) {
					System.err.println("[PatrolRouteManager] Failed to flush persistent state to disk: " + e.getMessage());
				}
			}
		}
	}

	/**
	 * Retrieves or initializes the ordered route map for a player commander.
	 * Initializes ONLY 1 default pathway route (Route 0 / Gold) initially.
	 * Never creates phantom routes.
	 *
	 * @param playerUuid The commanding player.
	 * @return Synchronized LinkedHashMap containing the player's routes.
	 */
	private Map<Integer, PatrolRoute> getOrCreatePlayerRoutes(UUID playerUuid) {
		if (playerUuid == null) {
			Map<Integer, PatrolRoute> fallback = Collections.synchronizedMap(new LinkedHashMap<>());
			fallback.put(0, PatrolRoute.createDefault(0));
			return fallback;
		}
		return playerRoutes.computeIfAbsent(playerUuid, u -> {
			Map<Integer, PatrolRoute> initial = Collections.synchronizedMap(new LinkedHashMap<>());
			initial.put(0, PatrolRoute.createDefault(0));
			return initial;
		});
	}

	/**
	 * Retrieves the designated patrol route for the given player and channel ID.
	 * Never auto-creates phantom routes if absent; returns null if nonexistent.
	 *
	 * @param playerUuid The UUID of the player commander.
	 * @param routeId    The channel index.
	 * @return The active PatrolRoute, or null if nonexistent.
	 */
	public PatrolRoute getRoute(UUID playerUuid, int routeId) {
		if (playerUuid == null) {
			return routeId == 0 ? PatrolRoute.createDefault(0) : null;
		}
		Map<Integer, PatrolRoute> routes = getOrCreatePlayerRoutes(playerUuid);
		return routes.get(routeId);
	}

	/**
	 * Retrieves all patrol routes for a player commander in newest-first order.
	 *
	 * @param playerUuid The UUID of the player commander.
	 * @return List of PatrolRoutes ordered with newly created routes first.
	 */
	public List<PatrolRoute> getAllRoutes(UUID playerUuid) {
		if (playerUuid == null) {
			return List.of(PatrolRoute.createDefault(0));
		}
		Map<Integer, PatrolRoute> routes = getOrCreatePlayerRoutes(playerUuid);
		synchronized (routes) {
			return new ArrayList<>(routes.values());
		}
	}

	/**
	 * Creates a new custom patrol route with the next available ID for the player.
	 * Newly created routes are placed first in order.
	 *
	 * @param playerUuid The commanding player.
	 * @param name       Custom route name.
	 * @param colorRgb   24-bit RGB color.
	 * @return The newly created PatrolRoute.
	 */
	public PatrolRoute createRoute(UUID playerUuid, String name, int colorRgb) {
		if (playerUuid == null) {
			return PatrolRoute.createCustom(0, name, colorRgb);
		}
		int nextId = getNextAvailableRouteId(playerUuid);
		PatrolRoute newRoute = PatrolRoute.createCustom(nextId, name, colorRgb);
		setRoute(playerUuid, newRoute);
		return newRoute;
	}

	/**
	 * Creates a new custom patrol route with the next available ID for the player using a hex color string.
	 *
	 * @param playerUuid The commanding player.
	 * @param name       Custom route name.
	 * @param hexColor   Hex color string (e.g. "#FFD700").
	 * @return The newly created PatrolRoute.
	 */
	public PatrolRoute createRoute(UUID playerUuid, String name, String hexColor) {
		return createRoute(playerUuid, name, PatrolRoute.parseHexColor(hexColor));
	}

	/**
	 * Updates the configuration (name, color, mode) of an existing patrol route while preserving its waypoints.
	 * Preserves the relative order of the route in the dashboard.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel ID to update.
	 * @param name       New route name.
	 * @param colorRgb   New 24-bit RGB color.
	 * @param mode       New patrol mode (LOOP / PING_PONG).
	 * @return The updated PatrolRoute, or null if the route does not exist.
	 */
	public PatrolRoute updateRouteConfig(UUID playerUuid, int routeId, String name, int colorRgb, PatrolRoute.PatrolMode mode) {
		PatrolRoute current = getRoute(playerUuid, routeId);
		if (current == null) {
			return null;
		}
		PatrolRoute updated = new PatrolRoute(
			routeId,
			(name != null && !name.trim().isEmpty()) ? name.trim() : current.name(),
			colorRgb & 0xFFFFFF,
			current.waypoints(),
			mode != null ? mode : current.patrolMode()
		);
		setRoute(playerUuid, updated);
		return updated;
	}

	/**
	 * Deletes a patrol route for a player, unbinds all assigned minions to prevent orphaned states,
	 * places them into holding position, and flushes persistent state to disk.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel ID to delete.
	 * @param world      The server world to unbind minions in (can be null).
	 * @return true if route was deleted, false otherwise.
	 */
	public boolean deleteRoute(UUID playerUuid, int routeId, ServerWorld world) {
		if (playerUuid == null) return false;
		Map<Integer, PatrolRoute> routes = playerRoutes.get(playerUuid);
		if (routes == null || !routes.containsKey(routeId)) return false;

		synchronized (routes) {
			routes.remove(routeId);
		}
		markDirtyAndPersist();

		if (world != null) {
			List<MinionEntity> routeMinions = world.getEntitiesByClass(
				MinionEntity.class,
				new net.minecraft.util.math.Box(-30000000, -64, -30000000, 30000000, 320, 30000000),
				m -> m.isAlive() && playerUuid.equals(m.getOwnerUuid()) && m.getPatrolRouteId() == routeId
			);
			for (MinionEntity m : routeMinions) {
				m.setPatrolRouteId(-1);
				m.setSitting(false);
				m.setGuardAnchorPos(null);
				m.getNavigation().stop();
			}
		}
		return true;
	}

	/**
	 * Deletes a patrol route for a player, unbinds all assigned minions to prevent orphaned states,
	 * sets them to roam freely, flushes persistent state to disk, and syncs to player.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel ID to delete.
	 * @param world      The server world to unbind minions in (can be null).
	 * @param player     The server player to sync to (can be null).
	 * @return true if route was deleted, false otherwise.
	 */
	public boolean deleteRoute(UUID playerUuid, int routeId, ServerWorld world, ServerPlayerEntity player) {
		boolean deleted = deleteRoute(playerUuid, routeId, world);
		if (deleted && player != null) {
			syncToPlayer(player);
		}
		return deleted;
	}

	/**
	 * Deletes a patrol route for a player without server world entity lookup.
	 */
	public boolean deleteRoute(UUID playerUuid, int routeId) {
		return deleteRoute(playerUuid, routeId, null);
	}

	/**
	 * Finds the smallest unused route ID for the given player commander.
	 */
	public int getNextAvailableRouteId(UUID playerUuid) {
		if (playerUuid == null) return 0;
		Map<Integer, PatrolRoute> routes = getOrCreatePlayerRoutes(playerUuid);
		synchronized (routes) {
			int id = 0;
			while (routes.containsKey(id)) {
				id++;
			}
			return id;
		}
	}

	/**
	 * Stores or updates an entire patrol route for a player commander.
	 * Newly created routes are prepended so they appear first in order.
	 * Existing route edits update in-place preserving their relative order.
	 */
	public void setRoute(UUID playerUuid, PatrolRoute route) {
		if (playerUuid == null || route == null) return;
		Map<Integer, PatrolRoute> routes = getOrCreatePlayerRoutes(playerUuid);
		synchronized (routes) {
			if (routes.containsKey(route.routeId())) {
				// Update in-place to preserve relative order of existing routes
				routes.put(route.routeId(), route);
			} else {
				// Newly created route comes first in order (prepended via LinkedHashMap)
				Map<Integer, PatrolRoute> updated = new LinkedHashMap<>();
				updated.put(route.routeId(), route);
				for (Map.Entry<Integer, PatrolRoute> entry : routes.entrySet()) {
					if (!entry.getKey().equals(route.routeId())) {
						updated.put(entry.getKey(), entry.getValue());
					}
				}
				routes.clear();
				routes.putAll(updated);
			}
		}
		markDirtyAndPersist();
	}

	/**
	 * Appends a waypoint block position to the specified route.
	 * Never auto-creates routes; returns null if the route does not exist.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel index.
	 * @return The updated PatrolRoute, or null if the route does not exist.
	 */
	public PatrolRoute addWaypoint(UUID playerUuid, int routeId, BlockPos pos) {
		PatrolRoute current = getRoute(playerUuid, routeId);
		if (current == null) {
			return null;
		}
		PatrolRoute updated = current.withAddedWaypoint(pos);
		setRoute(playerUuid, updated);
		return updated;
	}

	/**
	 * Removes a waypoint block position from the specified route.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel index.
	 * @return The updated PatrolRoute, or null if the route does not exist.
	 */
	public PatrolRoute removeWaypoint(UUID playerUuid, int routeId, BlockPos pos) {
		PatrolRoute current = getRoute(playerUuid, routeId);
		if (current == null) {
			return null;
		}
		PatrolRoute updated = current.withRemovedWaypoint(pos);
		setRoute(playerUuid, updated);
		return updated;
	}

	public record WaypointMatch(int routeId, BlockPos pos, PatrolRoute route) {}

	/**
	 * Scans across all route channels for a player to find if any route contains any candidate position.
	 *
	 * @param playerUuid         The commanding player.
	 * @param candidatePositions One or more world block positions to check.
	 * @return The WaypointMatch if found on any route channel, or null if none match.
	 */
	public WaypointMatch findWaypoint(UUID playerUuid, BlockPos... candidatePositions) {
		if (playerUuid == null || candidatePositions == null) {
			return null;
		}
		Map<Integer, PatrolRoute> routes = playerRoutes.get(playerUuid);
		if (routes != null) {
			for (PatrolRoute route : routes.values()) {
				if (route == null) continue;
				for (BlockPos cand : candidatePositions) {
					if (cand != null && route.waypoints().contains(cand)) {
						return new WaypointMatch(route.routeId(), cand, route);
					}
				}
			}
		}
		return null;
	}

	/**
	 * Clears all waypoints from the specified route.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel index.
	 * @return The cleared PatrolRoute.
	 */
	public PatrolRoute clearRoute(UUID playerUuid, int routeId) {
		PatrolRoute current = getRoute(playerUuid, routeId);
		if (current == null) {
			return null;
		}
		PatrolRoute updated = current.withClearedWaypoints();
		setRoute(playerUuid, updated);
		return updated;
	}

	/**
	 * Toggles the patrol mode (LOOP vs PING_PONG) for the specified route.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel index.
	 * @return The updated PatrolRoute, or null if the route does not exist.
	 */
	public PatrolRoute togglePatrolMode(UUID playerUuid, int routeId) {
		PatrolRoute current = getRoute(playerUuid, routeId);
		if (current == null) {
			return null;
		}
		PatrolRoute updated = current.withPatrolMode(current.patrolMode().toggle());
		setRoute(playerUuid, updated);
		return updated;
	}

	/**
	 * Synchronizes all active patrol routes for a player from server to client.
	 *
	 * @param player The server player entity.
	 */
	public void syncToPlayer(ServerPlayerEntity player) {
		if (player == null) return;
		if (this.persistentState == null && player.getServer() != null) {
			net.minecraft.server.world.ServerWorld overworld = player.getServer().getOverworld();
			if (overworld != null) {
				init(overworld);
			}
		}
		List<PatrolRoute> routes = getAllRoutes(player.getUuid());
		ServerPlayNetworking.send(player, new SyncPatrolRoutesPayload(routes));
	}
}
