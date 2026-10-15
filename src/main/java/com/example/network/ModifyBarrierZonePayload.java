package com.example.network;

import com.example.ExampleMod;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.ValueLists;
import net.minecraft.util.math.BlockPos;

/**
 * Client-to-server (C2S) payload dispatched when modifying a barrier zone (Sovereign Ward):
 * adding nodes, removing nodes, clearing active editing, sealing, expanding, or deleting zones.
 */
public record ModifyBarrierZonePayload(
	Action action,
	int zoneId,
	BlockPos pos,
	int minY,
	int maxY,
	String name
) implements CustomPayload {

	public enum Action {
		ADD_NODE,
		REMOVE_LAST_NODE,
		CLEAR_ACTIVE,
		SEAL_ZONE,
		EXPAND_ZONE,
		DELETE_ZONE,
		RESUME_ZONE,
		REMOVE_NODE,
		RENAME_ZONE;

		private static final IntFunction<Action> BY_ID = ValueLists.createIdToValueFunction(
			Action::ordinal,
			values(),
			ValueLists.OutOfBoundsHandling.ZERO
		);

		public static final PacketCodec<ByteBuf, Action> PACKET_CODEC = PacketCodecs.indexed(
			BY_ID,
			Action::ordinal
		);
	}

	public static final CustomPayload.Id<ModifyBarrierZonePayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "modify_barrier_zone")
	);

	public static final PacketCodec<RegistryByteBuf, ModifyBarrierZonePayload> PACKET_CODEC = PacketCodec.tuple(
		Action.PACKET_CODEC, ModifyBarrierZonePayload::action,
		PacketCodecs.VAR_INT, ModifyBarrierZonePayload::zoneId,
		BlockPos.PACKET_CODEC, ModifyBarrierZonePayload::pos,
		PacketCodecs.VAR_INT, ModifyBarrierZonePayload::minY,
		PacketCodecs.VAR_INT, ModifyBarrierZonePayload::maxY,
		PacketCodecs.STRING, ModifyBarrierZonePayload::name,
		ModifyBarrierZonePayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
