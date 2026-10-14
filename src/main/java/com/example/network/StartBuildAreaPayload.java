package com.example.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * C2S payload sent by the client when confirming an Area Fill / Volumetric Construction session
 * with a specified 3D bounding box (pos1, pos2), selected fill block identifier, and directional orientation.
 */
public record StartBuildAreaPayload(
	BlockPos pos1,
	BlockPos pos2,
	Identifier blockId,
	Direction facing,
	boolean invertedHalf
) implements CustomPayload {

	public static final CustomPayload.Id<StartBuildAreaPayload> ID = new CustomPayload.Id<>(
		Identifier.of("modid-mmcli-agent-modding", "start_build_area")
	);

	public static final PacketCodec<RegistryByteBuf, StartBuildAreaPayload> PACKET_CODEC = PacketCodec.tuple(
		BlockPos.PACKET_CODEC,
		StartBuildAreaPayload::pos1,
		BlockPos.PACKET_CODEC,
		StartBuildAreaPayload::pos2,
		Identifier.PACKET_CODEC,
		StartBuildAreaPayload::blockId,
		Direction.PACKET_CODEC,
		StartBuildAreaPayload::facing,
		PacketCodecs.BOOL,
		StartBuildAreaPayload::invertedHalf,
		StartBuildAreaPayload::new
	);

	public StartBuildAreaPayload(BlockPos pos1, BlockPos pos2, Identifier blockId) {
		this(pos1, pos2, blockId, Direction.NORTH, false);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
