package com.example;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FabricModJsonCompatibilityTest {

    @Test
    @DisplayName("Verify fabric.mod.json declares compatibility with both 1.21.x and 26.2")
    void testFabricModJsonMinecraftVersions() throws Exception {
        Path modJsonPath = Paths.get("src/main/resources/fabric.mod.json");
        assertTrue(Files.exists(modJsonPath), "fabric.mod.json must exist in src/main/resources");

        try (InputStream is = Files.newInputStream(modJsonPath)) {
            ModMetadata meta = ModMetadataParser.parseMetadata(
                is,
                "modid-mmcli-agent-modding",
                Collections.emptyList(),
                new VersionOverrides(),
                new DependencyOverrides(Paths.get(".")),
                false
            );

            assertNotNull(meta, "Parsed metadata should not be null");

            ModDependency mcDep = meta.getDependencies().stream()
                .filter(d -> "minecraft".equals(d.getModId()))
                .findFirst()
                .orElse(null);

            assertNotNull(mcDep, "minecraft dependency constraint must be declared in depends block");

            // Test 1.21 family versions
            assertTrue(mcDep.matches(Version.parse("1.21")), "Must match Minecraft 1.21");
            assertTrue(mcDep.matches(Version.parse("1.21.1")), "Must match Minecraft 1.21.1");
            assertTrue(mcDep.matches(Version.parse("1.21.2")), "Must match Minecraft 1.21.2");
            assertTrue(mcDep.matches(Version.parse("1.21.4")), "Must match Minecraft 1.21.4");

            // Test 26.2 version
            assertTrue(mcDep.matches(Version.parse("26.2")), "Must match Minecraft 26.2");
        }
    }
}
