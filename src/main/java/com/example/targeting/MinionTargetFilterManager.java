package com.example.targeting;

import com.example.network.SyncTargetFilterPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentStateManager;

/**
 * Server-side singleton managing each commander's minion combat target filter, which controls
 * which hostile mob types AUTO, SENTINEL and WARRIOR minions may attack on sight.
 * <p>
 * The filter is stored as a per-owner set of <b>disabled</b> entity types, so mob types added
 * later (e.g. by other mods) are engaged by default. Owners who never customized their filter
 * fall back to {@link #DEFAULT_DISABLED}, which preserves the legacy behavior of only engaging
 * {@code HostileEntity} mobs.
 * <p>
 * Only affects proactive on-sight acquisition; retaliation, owner-assist, leader-assist and
 * explicit scepter pings are never filtered.
 */
public class MinionTargetFilterManager {

	/**
	 * Monster-group mobs that legacy minion AI never engaged on sight (they do not extend
	 * {@code HostileEntity}). They stay unchecked until the commander opts in.
	 */
	public static final Set<Identifier> DEFAULT_DISABLED = Set.of(
		Identifier.ofVanilla("slime"),
		Identifier.ofVanilla("magma_cube"),
		Identifier.ofVanilla("ghast"),
		Identifier.ofVanilla("phantom"),
		Identifier.ofVanilla("shulker"),
		Identifier.ofVanilla("hoglin"),
		Identifier.ofVanilla("ender_dragon")
	);

	/** Hard cap on accepted identifiers per update to bound packet processing. */
	public static final int MAX_FILTER_ENTRIES = 1024;

	private static final MinionTargetFilterManager INSTANCE = new MinionTargetFilterManager();

	private final Map<UUID, Set<Identifier>> disabledByOwner = new ConcurrentHashMap<>();
	private MinionTargetFilterPersistentState persistentState = null;
	private PersistentStateManager stateManager = null;

	private MinionTargetFilterManager() {}

	public static MinionTargetFilterManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Initializes the manager with the world's PersistentStateManager on server startup/load.
	 *
	 * @param overworld The server overworld instance.
	 */
	public void init(ServerWorld overworld) {
		if (overworld == null) return;
		this.stateManager = overworld.getPersistentStateManager();
		this.persistentState = this.stateManager.getOrCreate(
			MinionTargetFilterPersistentState.TYPE,
			MinionTargetFilterPersistentState.KEY
		);
		this.disabledByOwner.clear();
		for (Map.Entry<UUID, Set<Identifier>> entry : this.persistentState.getDisabledByOwner().entrySet()) {
			this.disabledByOwner.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}
	}

	/** Called during server shutdown to flush the persistent state. */
	public void onServerStopping() {
		if (this.persistentState != null) {
			this.persistentState.markDirty();
			if (this.stateManager != null) {
				try {
					this.stateManager.save();
				} catch (Exception e) {
					System.err.println("[MinionTargetFilterManager] Failed to flush persistent state to disk: " + e.getMessage());
				}
			}
		}
	}

	/**
	 * @param ownerUuid Commander UUID (nullable).
	 * @return Snapshot of the entity type ids this commander's minions must not attack on sight.
	 */
	public Set<Identifier> getDisabled(UUID ownerUuid) {
		if (ownerUuid == null) {
			return new HashSet<>(DEFAULT_DISABLED);
		}
		Set<Identifier> stored = this.disabledByOwner.get(ownerUuid);
		return stored != null ? new HashSet<>(stored) : new HashSet<>(DEFAULT_DISABLED);
	}

	/**
	 * @param ownerUuid Commander UUID (nullable, e.g. unowned minion).
	 * @param typeId    Registry id of the candidate target's entity type.
	 * @return True if minions owned by this commander may attack this mob type on sight.
	 */
	public boolean isAllowed(UUID ownerUuid, Identifier typeId) {
		if (typeId == null) {
			return false;
		}
		if (ownerUuid == null) {
			return !DEFAULT_DISABLED.contains(typeId);
		}
		Set<Identifier> stored = this.disabledByOwner.get(ownerUuid);
		return stored != null ? !stored.contains(typeId) : !DEFAULT_DISABLED.contains(typeId);
	}

	/**
	 * @param ownerUuid Commander UUID (nullable, e.g. unowned minion).
	 * @param type      Candidate target's entity type.
	 * @return True if minions owned by this commander may attack this mob type on sight.
	 */
	public boolean isAllowed(UUID ownerUuid, EntityType<?> type) {
		return type != null && isAllowed(ownerUuid, Registries.ENTITY_TYPE.getId(type));
	}

	/**
	 * Replaces the commander's disabled set and persists it.
	 *
	 * @param ownerUuid     Commander UUID.
	 * @param disabledTypes Entity type ids that must not be attacked on sight.
	 */
	public void setDisabled(UUID ownerUuid, Collection<Identifier> disabledTypes) {
		if (ownerUuid == null) return;
		Set<Identifier> copy = disabledTypes != null ? new HashSet<>(disabledTypes) : new HashSet<>();
		this.disabledByOwner.put(ownerUuid, copy);
		if (this.persistentState != null) {
			this.persistentState.getDisabledByOwner().put(ownerUuid, new HashSet<>(copy));
			this.persistentState.markDirty();
		}
	}

	/**
	 * Filters raw client-supplied ids down to valid hostile candidate mob types.
	 *
	 * @param requested Ids received from the client.
	 * @return Sanitized list containing only registered candidate entity types.
	 */
	public static List<Identifier> sanitize(Collection<Identifier> requested) {
		List<Identifier> result = new ArrayList<>();
		if (requested == null) {
			return result;
		}
		Set<Identifier> seen = new HashSet<>();
		for (Identifier id : requested) {
			if (id == null || result.size() >= MAX_FILTER_ENTRIES || !seen.add(id)) {
				continue;
			}
			EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
			if (type != null && isCandidate(type)) {
				result.add(id);
			}
		}
		return result;
	}

	/**
	 * A candidate is any hostile (monster spawn group) mob type, excluding this mod's own entities.
	 *
	 * @param type Entity type to test.
	 * @return True if the type is selectable in the Target Filter modal.
	 */
	public static boolean isCandidate(EntityType<?> type) {
		if (type == null || type.getSpawnGroup() != SpawnGroup.MONSTER) {
			return false;
		}
		Identifier id = Registries.ENTITY_TYPE.getId(type);
		return !com.example.ExampleMod.MOD_ID.equals(id.getNamespace());
	}

	/**
	 * @return All selectable hostile mob type ids registered in the game, in registry order.
	 */
	public static List<Identifier> getCandidateIds() {
		List<Identifier> ids = new ArrayList<>();
		for (EntityType<?> type : Registries.ENTITY_TYPE) {
			if (isCandidate(type)) {
				ids.add(Registries.ENTITY_TYPE.getId(type));
			}
		}
		return ids;
	}

	/**
	 * Sends the commander's saved filter to their client.
	 *
	 * @param player The commander to synchronize.
	 */
	public void syncToPlayer(ServerPlayerEntity player) {
		if (player == null) return;
		ServerPlayNetworking.send(player, new SyncTargetFilterPayload(new ArrayList<>(getDisabled(player.getUuid()))));
	}
}
