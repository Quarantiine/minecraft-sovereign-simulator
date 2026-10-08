package com.example.targeting;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

/**
 * World-saved persistent state holding each commander's disabled mob-type set
 * for minion on-sight targeting (data/minion_target_filter.dat).
 * Only players who have customized their filter have an entry; everyone else
 * uses {@link MinionTargetFilterManager#DEFAULT_DISABLED}.
 */
public class MinionTargetFilterPersistentState extends PersistentState {

	public static final String KEY = "minion_target_filter";

	private final Map<UUID, Set<Identifier>> disabledByOwner = new ConcurrentHashMap<>();

	public MinionTargetFilterPersistentState() {}

	public static final Type<MinionTargetFilterPersistentState> TYPE = new Type<>(
		MinionTargetFilterPersistentState::new,
		MinionTargetFilterPersistentState::fromNbt,
		null
	);

	public static MinionTargetFilterPersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		MinionTargetFilterPersistentState state = new MinionTargetFilterPersistentState();
		if (nbt == null) {
			return state;
		}

		// Handle root wrapper if present ("data" tag used by PersistentStateManager)
		NbtCompound dataCompound = nbt.contains("data", NbtElement.COMPOUND_TYPE) ? nbt.getCompound("data") : nbt;
		if (!dataCompound.contains("Players", NbtElement.COMPOUND_TYPE)) {
			return state;
		}

		NbtCompound playersNbt = dataCompound.getCompound("Players");
		for (String uuidKey : playersNbt.getKeys()) {
			try {
				UUID ownerUuid = UUID.fromString(uuidKey);
				NbtList idList = playersNbt.getList(uuidKey, NbtElement.STRING_TYPE);
				Set<Identifier> disabled = new HashSet<>();
				for (int i = 0; i < idList.size(); i++) {
					Identifier id = Identifier.tryParse(idList.getString(i));
					if (id != null) {
						disabled.add(id);
					}
				}
				state.disabledByOwner.put(ownerUuid, disabled);
			} catch (Exception e) {
				System.err.println("[MinionTargetFilterPersistentState] Failed to deserialize target filter for " + uuidKey + ": " + e.getMessage());
			}
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound playersNbt = new NbtCompound();
		for (Map.Entry<UUID, Set<Identifier>> entry : this.disabledByOwner.entrySet()) {
			NbtList idList = new NbtList();
			for (Identifier id : entry.getValue()) {
				idList.add(NbtString.of(id.toString()));
			}
			playersNbt.put(entry.getKey().toString(), idList);
		}
		nbt.put("Players", playersNbt);
		return nbt;
	}

	public Map<UUID, Set<Identifier>> getDisabledByOwner() {
		return this.disabledByOwner;
	}
}
