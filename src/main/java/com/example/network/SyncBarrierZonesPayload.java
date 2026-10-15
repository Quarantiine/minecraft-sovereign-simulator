package com.example.network;

import com.example.ExampleMod;
import com.example.barrier.BarrierZone;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client (S2C) payload synchronizing player barrier zones (Sovereign Wards)
 * to the client for 3D holographic wireframe and forcefield rendering.
 *
 * @param zones List of BarrierZones owned by the player.
 */
public record SyncBarrierZonesPayload(
	List<BarrierZone> zones
) implements CustomPayload {

	public static final CustomPayload.Id<SyncBarrierZonesPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "sync_barrier_zones")
	);

	public static final PacketCodec<RegistryByteBuf, SyncBarrierZonesPayload> PACKET_CODEC = PacketCodec.tuple(
		BarrierZone.PACKET_CODEC.collect(PacketCodecs.toList()),
		SyncBarrierZonesPayload::zones,
		SyncBarrierZonesPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
