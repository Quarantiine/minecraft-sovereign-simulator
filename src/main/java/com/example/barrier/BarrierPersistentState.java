package com.example.barrier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;

/**
 * World-saved persistent state for player barrier zones (Sovereign Wards).
 * Stored in data/sovereign_barrier_zones.dat to ensure all protected perimeters
 * persist across server restarts and dimension reloads.
 */
public class BarrierPersistentState extends PersistentState {

	public static final String KEY = "sovereign_barrier_zones";

	private final Map<UUID, List<BarrierZone>> playerZones = new ConcurrentHashMap<>();

	public BarrierPersistentState() {}

	public static final Type<BarrierPersistentState> TYPE = new Type<>(
		BarrierPersistentState::new,
		BarrierPersistentState::fromNbt,
		null
	);

	public static BarrierPersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		BarrierPersistentState state = new BarrierPersistentState();
		if (nbt == null) {
			return state;
		}

		NbtCompound dataCompound = nbt.contains("data", NbtElement.COMPOUND_TYPE) ? nbt.getCompound("data") : nbt;
		if (!dataCompound.contains("Players", NbtElement.COMPOUND_TYPE)) {
			return state;
		}

		NbtCompound playersNbt = dataCompound.getCompound("Players");
		for (String uuidKey : playersNbt.getKeys()) {
			try {
				UUID playerUuid = UUID.fromString(uuidKey);
				NbtList zoneListNbt = playersNbt.getList(uuidKey, NbtElement.COMPOUND_TYPE);
				List<BarrierZone> zones = Collections.synchronizedList(new ArrayList<>());

				for (int i = 0; i < zoneListNbt.size(); i++) {
					NbtCompound zNbt = zoneListNbt.getCompound(i);
					zones.add(BarrierZone.fromNbt(zNbt));
				}
				state.playerZones.put(playerUuid, zones);
			} catch (Exception e) {
				System.err.println("[BarrierPersistentState] Failed to deserialize barrier zone for " + uuidKey + ": " + e.getMessage());
			}
		}

		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound playersNbt = new NbtCompound();

		for (Map.Entry<UUID, List<BarrierZone>> entry : this.playerZones.entrySet()) {
			NbtList zoneListNbt = new NbtList();
			synchronized (entry.getValue()) {
				for (BarrierZone zone : entry.getValue()) {
					zoneListNbt.add(zone.toNbt());
				}
			}
			playersNbt.put(entry.getKey().toString(), zoneListNbt);
		}

		nbt.put("Players", playersNbt);
		return nbt;
	}

	public Map<UUID, List<BarrierZone>> getPlayerZones() {
		return this.playerZones;
	}
}
