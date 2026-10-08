package com.example.network;

import com.example.ExampleMod;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client (S2C) payload synchronizing the commander's saved minion combat target
 * filter so the Target Filter modal opens with the correct checkboxes.
 *
 * @param disabledTypes Entity type identifiers minions are forbidden to target on sight.
 */
public record SyncTargetFilterPayload(
	List<Identifier> disabledTypes
) implements CustomPayload {

	public static final CustomPayload.Id<SyncTargetFilterPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "sync_target_filter")
	);

	public static final PacketCodec<RegistryByteBuf, SyncTargetFilterPayload> PACKET_CODEC = PacketCodec.tuple(
		Identifier.PACKET_CODEC.collect(PacketCodecs.toList()),
		SyncTargetFilterPayload::disabledTypes,
		SyncTargetFilterPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
