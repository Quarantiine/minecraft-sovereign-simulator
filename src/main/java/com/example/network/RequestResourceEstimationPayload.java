package com.example.network;

import com.example.ExampleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched to request a live Bill of Materials (BOM)
 * resource estimation delta for a specific blueprint across the commander's inventory and nearby minion backpacks.
 *
 * @param blueprintId Unique blueprint identifier to calculate resource requirements for.
 */
public record RequestResourceEstimationPayload(
	String blueprintId
) implements CustomPayload {

	public static final CustomPayload.Id<RequestResourceEstimationPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "request_resource_estimation")
	);

	public static final PacketCodec<RegistryByteBuf, RequestResourceEstimationPayload> PACKET_CODEC = PacketCodec.tuple(
		PacketCodecs.STRING, RequestResourceEstimationPayload::blueprintId,
		RequestResourceEstimationPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
