package com.example.network;

import com.example.ExampleMod;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client-to-server (C2S) networking payload dispatched when a player triggers an in-world
 * spatial area capture in DESIGN mode to compile a custom blueprint from corner bounds.
 *
 * @param id                Unique custom blueprint identifier (e.g. "custom_12345").
 * @param name              User-facing blueprint display name.
 * @param description       Optional user-facing blueprint description.
 * @param pos1              First corner position of the bounding volume.
 * @param pos2              Second corner position of the bounding volume.
 * @param blockReplacements Optional map of source block identifiers to replacement block identifiers (or minecraft:air).
 */
public record CaptureSpatialBlueprintPayload(
	String id,
	String name,
	String description,
	BlockPos pos1,
	BlockPos pos2,
	Map<Identifier, Identifier> blockReplacements
) implements CustomPayload {

	public static final CustomPayload.Id<CaptureSpatialBlueprintPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "capture_spatial_blueprint")
	);

	public static final PacketCodec<RegistryByteBuf, CaptureSpatialBlueprintPayload> PACKET_CODEC = new PacketCodec<>() {
		@Override
		public void encode(RegistryByteBuf buf, CaptureSpatialBlueprintPayload payload) {
			PacketCodecs.STRING.encode(buf, payload.id());
			PacketCodecs.STRING.encode(buf, payload.name());
			PacketCodecs.STRING.encode(buf, payload.description() != null ? payload.description() : "");
			BlockPos.PACKET_CODEC.encode(buf, payload.pos1());
			BlockPos.PACKET_CODEC.encode(buf, payload.pos2());
			Map<Identifier, Identifier> replacements = payload.blockReplacements();
			int count = replacements != null ? replacements.size() : 0;
			PacketCodecs.VAR_INT.encode(buf, count);
			if (replacements != null) {
				for (Map.Entry<Identifier, Identifier> entry : replacements.entrySet()) {
					Identifier.PACKET_CODEC.encode(buf, entry.getKey());
					Identifier.PACKET_CODEC.encode(buf, entry.getValue());
				}
			}
		}

		@Override
		public CaptureSpatialBlueprintPayload decode(RegistryByteBuf buf) {
			String id = PacketCodecs.STRING.decode(buf);
			String name = PacketCodecs.STRING.decode(buf);
			String description = PacketCodecs.STRING.decode(buf);
			BlockPos pos1 = BlockPos.PACKET_CODEC.decode(buf);
			BlockPos pos2 = BlockPos.PACKET_CODEC.decode(buf);
			int count = PacketCodecs.VAR_INT.decode(buf);
			Map<Identifier, Identifier> replacements = new LinkedHashMap<>();
			for (int i = 0; i < count; i++) {
				Identifier from = Identifier.PACKET_CODEC.decode(buf);
				Identifier to = Identifier.PACKET_CODEC.decode(buf);
				replacements.put(from, to);
			}
			return new CaptureSpatialBlueprintPayload(id, name, description, pos1, pos2, replacements);
		}
	};

	public CaptureSpatialBlueprintPayload(String id, String name, String description, BlockPos pos1, BlockPos pos2) {
		this(id, name, description, pos1, pos2, Collections.emptyMap());
	}

	public CaptureSpatialBlueprintPayload(String id, String name, BlockPos pos1, BlockPos pos2) {
		this(id, name, "", pos1, pos2, Collections.emptyMap());
	}

	public CaptureSpatialBlueprintPayload {
		blockReplacements = blockReplacements != null ? Map.copyOf(blockReplacements) : Collections.emptyMap();
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}

