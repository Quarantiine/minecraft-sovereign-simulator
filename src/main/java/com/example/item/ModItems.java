package com.example.item;

import com.example.ExampleMod;
import com.example.blueprint.BlueprintRegistry;
import com.example.component.CommandMode;
import com.example.component.ModDataComponents;
import com.example.component.SquadGroup;
import com.example.entity.ModEntities;
import com.example.item.custom.CommandScepterItem;
import com.example.item.custom.FrostGrenadeStickItem;
import com.example.item.custom.MinionSpawnEggItem;
import com.example.item.custom.TntStickItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/**
 * Registry class for mod items adhering to the recommended Fabric architecture.
 * Manages item initialization, custom item helper registration, and creative tab placement.
 */
public class ModItems {

	/**
	 * Creates an Item.Settings instance configured with a RegistryKey if running on Minecraft 1.21.2+,
	 * while maintaining seamless backwards compatibility with Minecraft 1.21.0 / 1.21.1.
	 *
	 * @param name The registry path name for the item.
	 * @return An Item.Settings instance compatible with both versions.
	 */
	public static Item.Settings createSettings(String name) {
		Item.Settings settings = new Item.Settings();
		applyRegistryKeyCompat(settings, name);
		return settings;
	}

	private static void applyRegistryKeyCompat(Item.Settings settings, String name) {
		try {
			Identifier id = Identifier.of(ExampleMod.MOD_ID, name);
			RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
			// 1. Try public method registryKey(RegistryKey) in 1.21.2+
			for (java.lang.reflect.Method m : settings.getClass().getMethods()) {
				if (m.getName().equals("registryKey") && m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(RegistryKey.class)) {
					m.invoke(settings, key);
					return;
				}
			}
			// 2. Try intermediary method method_63686(RegistryKey) in 1.21.2+
			for (java.lang.reflect.Method m : settings.getClass().getDeclaredMethods()) {
				if (m.getName().equals("method_63686") && m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(RegistryKey.class)) {
					m.setAccessible(true);
					m.invoke(settings, key);
					return;
				}
			}
			// 3. Fallback: Try setting field_54117 directly in 1.21.2+
			for (java.lang.reflect.Field f : settings.getClass().getDeclaredFields()) {
				if (f.getName().equals("field_54117") && f.getType().equals(RegistryKey.class)) {
					f.setAccessible(true);
					f.set(settings, key);
					return;
				}
			}
		} catch (Throwable ignored) {
			// On 1.21.0 where RegistryKey on Item.Settings does not exist, safely ignore
		}
	}

	/**
	 * TNT Stick item that launches explosive TNT projectiles.
	 */
	public static final Item TNT_STICK = registerItem(
		"tnt_stick",
		new TntStickItem(createSettings("tnt_stick").maxCount(1).rarity(Rarity.EPIC))
	);

	/**
	 * Frost Grenade Stick item that launches cryogenic frost projectiles.
	 */
	public static final Item FROST_GRENADE_STICK = registerItem(
		"frost_grenade_stick",
		new FrostGrenadeStickItem(createSettings("frost_grenade_stick").maxCount(1).rarity(Rarity.RARE))
	);

	/**
	 * Minion Spawn Egg for summoning autonomous minion thralls that auto-tame to the summoner.
	 */
	public static final Item MINION_SPAWN_EGG = registerItem(
		"minion_spawn_egg",
		new MinionSpawnEggItem(ModEntities.MINION, 0x2C3E50, 0xF1C40F, createSettings("minion_spawn_egg"))
	);

	/**
	 * Loki Command Scepter for orchestrating minion thralls, transfiguration, and multiblock construction.
	 */
	public static final Item COMMAND_SCEPTER = registerItem(
		"command_scepter",
		new CommandScepterItem(
			createSettings("command_scepter")
				.maxCount(1)
				.rarity(Rarity.EPIC)
				.component(ModDataComponents.COMMAND_MODE, CommandMode.FOLLOW)
				.component(ModDataComponents.ACTIVE_BLUEPRINT, "")
				.component(ModDataComponents.TARGET_SQUAD, SquadGroup.ALL)
		)
	);

	/**
	 * Helper method to register an item in the Minecraft item registry under the mod namespace.
	 *
	 * @param name The registry path name for the item.
	 * @param item The item instance to register.
	 * @return The registered item instance.
	 */
	private static Item registerItem(String name, Item item) {
		return Registry.register(Registries.ITEM, Identifier.of(ExampleMod.MOD_ID, name), item);
	}

	/**
	 * Registers mod items into creative inventory tabs and logs item registration progress.
	 * Called during common mod initialization.
	 */
	public static void registerModItems() {
		ExampleMod.LOGGER.info("Registering Mod Items for {}", ExampleMod.MOD_ID);

		// Add TNT Stick, Frost Grenade Stick, and Command Scepter to the Combat item group / creative tab
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(TNT_STICK);
			entries.add(FROST_GRENADE_STICK);
			entries.add(COMMAND_SCEPTER);
		});

		// Add Command Scepter to the Tools item group / creative tab
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(COMMAND_SCEPTER);
		});

		// Add Minion Spawn Egg to the Spawn Eggs creative tab
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
			entries.add(MINION_SPAWN_EGG);
		});
	}
}
