package com.example.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Represents an individual material requirement and its live inventory availability
 * across the player's personal inventory, nearby minion backpacks, and nearby containers/chests.
 *
 * @param itemId        Resource identifier string (e.g. "minecraft:stone_bricks").
 * @param requiredCount Total quantity required by the blueprint.
 * @param playerCount   Quantity currently carried in the commander's personal inventory.
 * @param minionCount   Quantity aggregated across nearby allied minion backpacks within 64m.
 * @param chestCount    Quantity aggregated across nearby containers (chests, barrels, etc.) within 48m.
 * @param harvestable   Whether builder minions can autonomously quarry, smelt, or synthesize this resource.
 */
public record ResourceEstimateEntry(
	String itemId,
	int requiredCount,
	int playerCount,
	int minionCount,
	int chestCount,
	boolean harvestable
) {

	public ResourceEstimateEntry(
		String itemId,
		int requiredCount,
		int playerCount,
		int minionCount,
		boolean harvestable
	) {
		this(itemId, requiredCount, playerCount, minionCount, 0, harvestable);
	}

	public static final PacketCodec<RegistryByteBuf, ResourceEstimateEntry> PACKET_CODEC = PacketCodec.tuple(
		PacketCodecs.STRING, ResourceEstimateEntry::itemId,
		PacketCodecs.INTEGER, ResourceEstimateEntry::requiredCount,
		PacketCodecs.INTEGER, ResourceEstimateEntry::playerCount,
		PacketCodecs.INTEGER, ResourceEstimateEntry::minionCount,
		PacketCodecs.INTEGER, ResourceEstimateEntry::chestCount,
		PacketCodecs.BOOL, ResourceEstimateEntry::harvestable,
		ResourceEstimateEntry::new
	);

	/**
	 * Returns the combined quantity available across the commander, nearby minions, and nearby containers.
	 */
	public int totalAvailable() {
		return this.playerCount + this.minionCount + this.chestCount;
	}

	/**
	 * Returns the net delta (positive/zero if satisfied, negative if deficient).
	 */
	public int delta() {
		return totalAvailable() - this.requiredCount;
	}

	/**
	 * Returns true if the available inventory meets or exceeds the required count.
	 */
	public boolean isSatisfied() {
		return totalAvailable() >= this.requiredCount;
	}
}
