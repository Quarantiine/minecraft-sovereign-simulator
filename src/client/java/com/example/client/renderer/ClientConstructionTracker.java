package com.example.client.renderer;

import com.example.network.SyncConstructionSessionPayload;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client-side tracker storing active in-world construction and deconstruction sessions.
 * Synchronized via server S2C payloads so persistent 3D holographic wireframes
 * remain visible to commanders while minions build until session completion.
 */
public class ClientConstructionTracker {

	public record ActiveSessionClientData(
		UUID sessionId,
		BlockPos anchorPos,
		String blueprintId,
		int rotation,
		boolean isDismantle,
		int sizeX,
		int sizeY,
		int sizeZ,
		Set<Identifier> curatedBlockIds
	) {
		public ActiveSessionClientData(
			UUID sessionId,
			BlockPos anchorPos,
			String blueprintId,
			int rotation,
			boolean isDismantle,
			int sizeX,
			int sizeY,
			int sizeZ
		) {
			this(sessionId, anchorPos, blueprintId, rotation, isDismantle, sizeX, sizeY, sizeZ, Collections.emptySet());
		}

		public ActiveSessionClientData(
			UUID sessionId,
			BlockPos anchorPos,
			String blueprintId,
			int rotation,
			boolean isDismantle
		) {
			this(sessionId, anchorPos, blueprintId, rotation, isDismantle, 0, 0, 0, Collections.emptySet());
		}

		public boolean isBlockCurated(Identifier id) {
			if (curatedBlockIds == null || curatedBlockIds.isEmpty()) {
				return true;
			}
			return curatedBlockIds.contains(id);
		}
	}

	private static final Map<UUID, ActiveSessionClientData> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

	public static void addSession(SyncConstructionSessionPayload payload) {
		if (payload == null) return;
		Set<Identifier> curated = payload.curatedBlockIds() != null && !payload.curatedBlockIds().isEmpty()
			? new LinkedHashSet<>(payload.curatedBlockIds())
			: Collections.emptySet();
		ACTIVE_SESSIONS.put(
			payload.sessionId(),
			new ActiveSessionClientData(
				payload.sessionId(),
				payload.anchorPos(),
				payload.blueprintId(),
				payload.rotation(),
				payload.isDismantle(),
				payload.sizeX(),
				payload.sizeY(),
				payload.sizeZ(),
				curated
			)
		);
	}

	public static void removeSession(UUID sessionId) {
		if (sessionId != null) {
			ACTIVE_SESSIONS.remove(sessionId);
		}
	}

	public static Collection<ActiveSessionClientData> getActiveSessions() {
		return ACTIVE_SESSIONS.values();
	}

	public static void clear() {
		ACTIVE_SESSIONS.clear();
	}
}
