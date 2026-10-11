package com.example.entity.custom;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;

/**
 * Individual rules of engagement policy for a minion thrall.
 * Determines how the minion engages hostile mobs relative to the commander's global target filter.
 */
public enum MinionCombatPolicy implements StringIdentifiable {
	GLOBAL(0, "global", "§a⚔ Global", "Follows commander's global target filter rules"),
	PASSIVE(1, "passive", "§6☮ Passive", "Strictly pacifist; never attacks on sight or retaliates"),
	DEFENSIVE(2, "defensive", "§b🛡 Defensive", "Only retaliates when struck; ignores proactive target filter"),
	CUSTOM(3, "custom", "§d🎯 Custom", "Follows individual target list configured for this unit");

	public static final PacketCodec<ByteBuf, MinionCombatPolicy> PACKET_CODEC = PacketCodecs.INTEGER.xmap(
		MinionCombatPolicy::fromId,
		MinionCombatPolicy::getId
	);

	private final int id;
	private final String name;
	private final String displayFormatted;
	private final String description;

	MinionCombatPolicy(int id, String name, String displayFormatted, String description) {
		this.id = id;
		this.name = name;
		this.displayFormatted = displayFormatted;
		this.description = description;
	}

	public int getId() {
		return this.id;
	}

	public String getDisplayName() {
		return this.displayFormatted;
	}

	public String getDescription() {
		return this.description;
	}

	@Override
	public String asString() {
		return this.name;
	}

	public MinionCombatPolicy next() {
		MinionCombatPolicy[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}

	public static MinionCombatPolicy fromId(int id) {
		for (MinionCombatPolicy policy : values()) {
			if (policy.id == id) {
				return policy;
			}
		}
		return GLOBAL;
	}
}
