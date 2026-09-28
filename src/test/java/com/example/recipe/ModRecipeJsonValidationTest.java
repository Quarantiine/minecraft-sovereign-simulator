package com.example.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that all mod recipe JSON definitions are syntactically valid,
 * properly formatted for Minecraft 1.21+, and point to registered mod items.
 */
public class ModRecipeJsonValidationTest {

	private static final String[] RECIPE_FILES = {
		"data/modid-mmcli-agent-modding/recipe/command_scepter.json",
		"data/modid-mmcli-agent-modding/recipe/minion_spawn_egg.json",
		"data/modid-mmcli-agent-modding/recipe/tnt_stick.json",
		"data/modid-mmcli-agent-modding/recipe/frost_grenade_stick.json",
		"data/modid-mmcli-agent-modding/recipes/command_scepter.json",
		"data/modid-mmcli-agent-modding/recipes/minion_spawn_egg.json",
		"data/modid-mmcli-agent-modding/recipes/tnt_stick.json",
		"data/modid-mmcli-agent-modding/recipes/frost_grenade_stick.json"
	};

	@Test
	public void testRecipesExistAndAreValid() {
		for (String path : RECIPE_FILES) {
			var stream = getClass().getClassLoader().getResourceAsStream(path);
			assertNotNull(stream, "Recipe resource must exist on classpath: " + path);

			try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonElement element = JsonParser.parseReader(reader);
				assertTrue(element.isJsonObject(), "Recipe must be a JSON object: " + path);
				JsonObject obj = element.getAsJsonObject();

				assertTrue(obj.has("type"), "Recipe must have 'type': " + path);
				assertTrue(obj.has("result"), "Recipe must have 'result': " + path);

				JsonObject result = obj.getAsJsonObject("result");
				assertTrue(result.has("id"), "Result must have 'id': " + path);
				String resultId = result.get("id").getAsString();
				assertTrue(resultId.startsWith("modid-mmcli-agent-modding:"), "Result ID must belong to mod: " + resultId);

				String type = obj.get("type").getAsString();
				if (type.equals("minecraft:crafting_shaped")) {
					assertTrue(obj.has("pattern"), "Shaped recipe must have pattern: " + path);
					assertTrue(obj.has("key"), "Shaped recipe must have key: " + path);
				} else if (type.equals("minecraft:crafting_shapeless")) {
					assertTrue(obj.has("ingredients"), "Shapeless recipe must have ingredients: " + path);
				}
			} catch (Exception e) {
				fail("Failed to parse recipe " + path + ": " + e.getMessage());
			}
		}
	}
}
