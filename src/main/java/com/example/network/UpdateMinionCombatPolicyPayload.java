package com.example.network;

import com.example.ExampleMod;
import com.example.entity.custom.MinionCombatPolicy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server payload updating an individual minion's combat policy and custom target filter.
 *
 * @param minionId        The entity ID of the minion.
 * @param policy          The assigned {@link MinionCombatPolicy} (GLOBAL, PASSIVE, DEFENSIVE, CUSTOM).
 * @param disabledTargets List of hostile mob IDs that are disabled for this minion when CUSTOM policy is active.
 */
public record UpdateMinionCombatPolicyPayload(
	int minionId,
	MinionCombatPolicy policy,
	List<Identifier> disabledTargets
) implements CustomPayload {

	public static final CustomPayload.Id<UpdateMinionCombatPolicyPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "update_minion_combat_policy")
	);

	public static final PacketCodec<RegistryByteBuf, UpdateMinionCombatPolicyPayload> PACKET_CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER,
		UpdateMinionCombatPolicyPayload::minionId,
		MinionCombatPolicy.PACKET_CODEC.cast(),
		UpdateMinionCombatPolicyPayload::policy,
		PacketCodecs.collection(ArrayList::new, Identifier.PACKET_CODEC),
		UpdateMinionCombatPolicyPayload::disabledTargets,
		UpdateMinionCombatPolicyPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
