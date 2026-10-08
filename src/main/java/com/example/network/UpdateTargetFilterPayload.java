package com.example.network;

import com.example.ExampleMod;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) payload submitting the commander's minion combat target filter
 * from the Target Filter modal. Contains every hostile mob type the commander has
 * UNCHECKED (i.e. minions must NOT attack on sight).
 *
 * @param disabledTypes Entity type identifiers minions are forbidden to target on sight.
 */
public record UpdateTargetFilterPayload(
	List<Identifier> disabledTypes
) implements CustomPayload {

	public static final CustomPayload.Id<UpdateTargetFilterPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "update_target_filter")
	);

	public static final PacketCodec<RegistryByteBuf, UpdateTargetFilterPayload> PACKET_CODEC = PacketCodec.tuple(
		Identifier.PACKET_CODEC.collect(PacketCodecs.toList()),
		UpdateTargetFilterPayload::disabledTypes,
		UpdateTargetFilterPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
