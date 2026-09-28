package com.example.item;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class CommandScepterTooltipAndComponentsTest {

    @Test
    @DisplayName("Validate applyRegistryKeyCompat in ModItems strictly filters method names to prevent JukeboxPlayableComponent injection")
    void testApplyRegistryKeyCompatMethodGuards() throws Exception {
        Path modItemsPath = Path.of("src/main/java/com/example/item/ModItems.java");
        Assertions.assertTrue(Files.exists(modItemsPath), "ModItems.java must exist");
        String content = Files.readString(modItemsPath);

        // Verify that applyRegistryKeyCompat strictly guards by method name
        Assertions.assertTrue(content.contains("m.getName().equals(\"registryKey\")"),
                "applyRegistryKeyCompat must check for method name 'registryKey'");
        Assertions.assertTrue(content.contains("m.getName().equals(\"method_63686\")"),
                "applyRegistryKeyCompat must check for intermediary method name 'method_63686'");
        Assertions.assertTrue(content.contains("f.getName().equals(\"field_54117\")"),
                "applyRegistryKeyCompat must check for intermediary field name 'field_54117'");

        // Verify that unsafe wildcard matching (which accidentally matched jukeboxPlayable) is removed
        Assertions.assertFalse(content.contains("m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(RegistryKey.class)\n\t\t\t\t\tm.invoke"),
                "applyRegistryKeyCompat must NOT blindly invoke any 1-arg RegistryKey method without name verification");

        // Verify all 4 mod items use createSettings
        Assertions.assertTrue(content.contains("createSettings(\"tnt_stick\")"));
        Assertions.assertTrue(content.contains("createSettings(\"frost_grenade_stick\")"));
        Assertions.assertTrue(content.contains("createSettings(\"minion_spawn_egg\")"));
        Assertions.assertTrue(content.contains("createSettings(\"command_scepter\")"));
    }

    @Test
    @DisplayName("Validate ExampleMixin is removed from modid.mixins.json to prevent MinecraftServer loadWorld crash")
    void testExampleMixinRemoved() throws Exception {
        Path mixinJsonPath = Path.of("src/main/resources/modid.mixins.json");
        Assertions.assertTrue(Files.exists(mixinJsonPath), "modid.mixins.json must exist");
        String mixinContent = Files.readString(mixinJsonPath);

        Assertions.assertFalse(mixinContent.contains("ExampleMixin"),
                "modid.mixins.json must NOT contain ExampleMixin, as MinecraftServer.loadWorld injection fails across versions");
        Assertions.assertTrue(mixinContent.contains("TridentEntityMixin"),
                "modid.mixins.json must retain TridentEntityMixin");
    }

    @Test
    @DisplayName("Validate CommandScepterItem appendTooltip structure and guidance lines")
    void testCommandScepterTooltipInvariants() throws Exception {
        Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
        Assertions.assertTrue(Files.exists(scepterPath), "CommandScepterItem.java must exist");
        String content = Files.readString(scepterPath);

        Assertions.assertTrue(content.contains("public void appendTooltip"), "CommandScepterItem must define appendTooltip");
        Assertions.assertTrue(content.contains("Command Mode:"), "Tooltip must display Command Mode");
        Assertions.assertTrue(content.contains("Target Squad:"), "Tooltip must display Target Squad");
        Assertions.assertTrue(content.contains("Target Archetype:"), "Tooltip must display Target Archetype");
        Assertions.assertTrue(content.contains("Open Command Hub GUI"), "Tooltip must include Command Hub guidance");
    }
}
