package com.example.client.renderer;

import com.example.patrol.PatrolRoute;
import com.example.patrol.PatrolRouteManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.BlockPos;

/**
 * Client-side tracker storing active in-world patrol routes received from the server.
 * Allows 3D holographic wireframe and trajectory rendering while holding the Command Scepter.
 * <p>
 * Invariant: Preserves newest-first route order using an internal synchronized {@link LinkedHashMap}.
 * Newly created routes are prepended so they appear at Slide 1 Slot 1 in visual dashboards.
 * Queries via {@link #getRoute(int)} never create phantom routes, returning {@code null} if nonexistent.
 */
public class ClientPatrolRouteTracker {

	private static final Map<Integer, PatrolRoute> ACTIVE_ROUTES = Collections.synchronizedMap(new LinkedHashMap<>());

	/**
	 * Populates tracker with routes received from server synchronization.
	 *
	 * @param routes List of synchronized patrol routes.
	 */
	public static void setRoutes(List<PatrolRoute> routes) {
		if (routes == null) return;
		synchronized (ACTIVE_ROUTES) {
			ACTIVE_ROUTES.clear();
			for (PatrolRoute r : routes) {
				if (r != null) {
					ACTIVE_ROUTES.put(r.routeId(), r);
				}
			}
		}
	}

	/**
	 * Retrieves an active route by ID.
	 * NEVER creates a phantom route if absent.
	 *
	 * @param routeId Route channel identifier.
	 * @return Existing {@link PatrolRoute}, or {@code null} if route does not exist.
	 */
	public static PatrolRoute getRoute(int routeId) {
		return ACTIVE_ROUTES.get(routeId);
	}

	/**
	 * Returns an unmodifiable collection of all active routes preserving newest-first order.
	 */
	public static Collection<PatrolRoute> getAllRoutes() {
		synchronized (ACTIVE_ROUTES) {
			return new ArrayList<>(ACTIVE_ROUTES.values());
		}
	}

	/**
	 * Returns an ordered list of all active routes.
	 * Unlike legacy sorting by ascending routeId, this preserves insertion order (newest first).
	 */
	public static List<PatrolRoute> getAllRoutesList() {
		synchronized (ACTIVE_ROUTES) {
			return new ArrayList<>(ACTIVE_ROUTES.values());
		}
	}

	/**
	 * Removes a route by ID.
	 *
	 * @param routeId Channel identifier to remove.
	 */
	public static void removeRoute(int routeId) {
		ACTIVE_ROUTES.remove(routeId);
	}

	/**
	 * Updates an existing route in-place preserving its relative order,
	 * or prepends a newly created route to the front so it appears first (Slide 1 Slot 1).
	 *
	 * @param route Patrol route to update or prepend.
	 */
	public static void updateRoute(PatrolRoute route) {
		if (route == null) return;
		synchronized (ACTIVE_ROUTES) {
			if (ACTIVE_ROUTES.containsKey(route.routeId())) {
				// In-place update preserving relative position
				ACTIVE_ROUTES.put(route.routeId(), route);
			} else {
				// Prepend new route to the front
				prependRoute(route);
			}
		}
	}

	/**
	 * Prepends a route to the front of ACTIVE_ROUTES.
	 *
	 * @param route New patrol route to place at front.
	 */
	public static void prependRoute(PatrolRoute route) {
		if (route == null) return;
		synchronized (ACTIVE_ROUTES) {
			Map<Integer, PatrolRoute> copy = new LinkedHashMap<>();
			copy.put(route.routeId(), route);
			for (Map.Entry<Integer, PatrolRoute> entry : ACTIVE_ROUTES.entrySet()) {
				if (entry.getKey() != route.routeId()) {
					copy.put(entry.getKey(), entry.getValue());
				}
			}
			ACTIVE_ROUTES.clear();
			ACTIVE_ROUTES.putAll(copy);
		}
	}

	public static boolean isAnyWaypoint(BlockPos pos) {
		if (pos == null) return false;
		synchronized (ACTIVE_ROUTES) {
			for (PatrolRoute r : ACTIVE_ROUTES.values()) {
				if (r != null && r.waypoints().contains(pos)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isAnyWaypoint(BlockPos... positions) {
		if (positions == null) return false;
		for (BlockPos p : positions) {
			if (isAnyWaypoint(p)) return true;
		}
		return false;
	}

	public static PatrolRouteManager.WaypointMatch findWaypoint(BlockPos... candidatePositions) {
		if (candidatePositions == null) return null;
		synchronized (ACTIVE_ROUTES) {
			for (PatrolRoute r : ACTIVE_ROUTES.values()) {
				if (r == null) continue;
				for (BlockPos cand : candidatePositions) {
					if (cand != null && r.waypoints().contains(cand)) {
						return new PatrolRouteManager.WaypointMatch(r.routeId(), cand, r);
					}
				}
			}
		}
		return null;
	}

	public static void clear() {
		ACTIVE_ROUTES.clear();
	}
}
