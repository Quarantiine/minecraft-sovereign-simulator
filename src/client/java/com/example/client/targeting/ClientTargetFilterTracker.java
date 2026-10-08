package com.example.client.targeting;

import com.example.targeting.MinionTargetFilterManager;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.Identifier;

/**
 * Client-side cache of the commander's minion combat target filter (the set of hostile mob
 * types minions must NOT attack on sight), synchronized from the server on join and after
 * every submit. Defaults to {@link MinionTargetFilterManager#DEFAULT_DISABLED} until synced.
 */
public final class ClientTargetFilterTracker {

	private static volatile Set<Identifier> disabled = new HashSet<>(MinionTargetFilterManager.DEFAULT_DISABLED);

	private ClientTargetFilterTracker() {}

	/**
	 * @return Snapshot copy of the currently disabled mob type ids.
	 */
	public static Set<Identifier> getDisabled() {
		return new HashSet<>(disabled);
	}

	/**
	 * @param types Disabled mob type ids received from the server (or submitted locally).
	 */
	public static void setDisabled(Collection<Identifier> types) {
		disabled = types != null ? new HashSet<>(types) : new HashSet<>();
	}

	/** Resets to the legacy default filter (used on disconnect). */
	public static void clear() {
		disabled = new HashSet<>(MinionTargetFilterManager.DEFAULT_DISABLED);
	}
}
