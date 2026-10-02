package com.example.network;

import com.example.ExampleMod;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client (S2C) networking payload delivering the live Bill of Materials (BOM)
 * calculation and inventory delta for an architectural blueprint.
 *
 * @param blueprintId        Blueprint identifier.
 * @param totalBlocks        Total blocks required by the blueprint.
 * @param totalAvailable     Total matching blocks available (clamped to required per item).
 * @param nearbyMinionsCount Count of nearby allied minions contributing to the backpack tally.
 * @param entries            List of individual resource breakdown entries.
 */
public record SyncResourceEstimationPayload(
	String blueprintId,
	int totalBlocks,
	int totalAvailable,
	int nearbyMinionsCount,
	List<ResourceEstimateEntry> entries
) implements CustomPayload {

	public static final CustomPayload.Id<SyncResourceEstimationPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "sync_resource_estimation")
	);

	public static final PacketCodec<RegistryByteBuf, SyncResourceEstimationPayload> PACKET_CODEC = PacketCodec.tuple(
		PacketCodecs.STRING, SyncResourceEstimationPayload::blueprintId,
		PacketCodecs.INTEGER, SyncResourceEstimationPayload::totalBlocks,
		PacketCodecs.INTEGER, SyncResourceEstimationPayload::totalAvailable,
		PacketCodecs.INTEGER, SyncResourceEstimationPayload::nearbyMinionsCount,
		ResourceEstimateEntry.PACKET_CODEC.collect(PacketCodecs.toList()), SyncResourceEstimationPayload::entries,
		SyncResourceEstimationPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	/**
	 * Returns the overall construction readiness percentage (0 to 100).
	 */
	public int getReadinessPercentage() {
		if (this.totalBlocks <= 0) return 100;
		return Math.min(100, Math.max(0, (int) Math.round((double) this.totalAvailable / (double) this.totalBlocks * 100.0D)));
	}

	/**
	 * Returns true if all required materials are fully satisfied across player and minion inventories.
	 */
	public boolean isFullySatisfied() {
		return this.totalAvailable >= this.totalBlocks && this.totalBlocks > 0;
	}
}
