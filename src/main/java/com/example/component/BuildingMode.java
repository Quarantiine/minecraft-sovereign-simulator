package com.example.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.ValueLists;

/**
 * Operational sub-mode for BUILD command mode:
 * <ul>
 * <li><b>BLUEPRINT</b> (id 0, Aqua): Place and construct architectural blueprints from catalog.</li>
 * <li><b>AREA</b> (id 1, Dark Aqua): Custom 3D volumetric fill construction with block picker modal.</li>
 * <li><b>BARRIER</b> (id 2, Gold): Protects territory from minion harvesting.</li>
 * </ul>
 */
public enum BuildingMode implements StringIdentifiable {
	BLUEPRINT(0, "blueprint", "Blueprint / Structure", "§b", Formatting.AQUA),
	AREA(1, "area", "Area Fill / Volumetric", "§3", Formatting.DARK_AQUA),
	BARRIER(2, "barrier", "Barrier", "§6", Formatting.GOLD);

	public static final Codec<BuildingMode> CODEC = StringIdentifiable.createCodec(BuildingMode::values);

	private static final IntFunction<BuildingMode> BY_ID = ValueLists.createIdToValueFunction(
		BuildingMode::getId,
		values(),
		ValueLists.OutOfBoundsHandling.ZERO
	);

	public static final PacketCodec<ByteBuf, BuildingMode> PACKET_CODEC = PacketCodecs.indexed(
		BY_ID,
		BuildingMode::getId
	);

	private final int id;
	private final String name;
	private final String displayName;
	private final String colorCode;
	private final Formatting formatting;

	BuildingMode(int id, String name, String displayName, String colorCode, Formatting formatting) {
		this.id = id;
		this.name = name;
		this.displayName = displayName;
		this.colorCode = colorCode;
		this.formatting = formatting;
	}

	public int getId() {
		return this.id;
	}

	@Override
	public String asString() {
		return this.name;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getColorCode() {
		return this.colorCode;
	}

	public Formatting getFormatting() {
		return this.formatting;
	}

	public String getFormattedName() {
		return this.colorCode + this.displayName;
	}

	public String getTranslationKey() {
		return "building_mode.modid-mmcli-agent-modding." + this.name;
	}

	public static BuildingMode fromId(int id) {
		BuildingMode[] values = values();
		if (id < 0 || id >= values.length) {
			return BLUEPRINT;
		}
		return values[id];
	}

	public static BuildingMode fromName(String name) {
		if (name == null) return BLUEPRINT;
		for (BuildingMode mode : values()) {
			if (mode.name.equalsIgnoreCase(name) || mode.name().equalsIgnoreCase(name)) {
				return mode;
			}
		}
		return BLUEPRINT;
	}

	public BuildingMode next() {
		BuildingMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}

	public BuildingMode previous() {
		BuildingMode[] values = values();
		return values[(this.ordinal() - 1 + values.length) % values.length];
	}
}
