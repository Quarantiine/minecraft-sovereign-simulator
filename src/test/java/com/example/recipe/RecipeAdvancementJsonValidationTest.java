package com.example.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that all recipe advancement JSON definitions are syntactically valid,
 * properly formatted for Minecraft 1.21+, grant the correct mod recipes,
 * and define functional criteria for vanilla recipe book unlocking.
 */
public class RecipeAdvancementJsonValidationTest {

	private static final String[] ADVANCEMENT_FILES = {
		"data/modid-mmcli-agent-modding/advancement/recipes/equipment/command_scepter.json",
		"data/modid-mmcli-agent-modding/advancement/recipes/misc/minion_spawn_egg.json",
		"data/modid-mmcli-agent-modding/advancement/recipes/equipment/tnt_stick.json",
		"data/modid-mmcli-agent-modding/advancement/recipes/equipment/frost_grenade_stick.json",
		"data/modid-mmcli-agent-modding/advancements/recipes/equipment/command_scepter.json",
		"data/modid-mmcli-agent-modding/advancements/recipes/misc/minion_spawn_egg.json",
		"data/modid-mmcli-agent-modding/advancements/recipes/equipment/tnt_stick.json",
		"data/modid-mmcli-agent-modding/advancements/recipes/equipment/frost_grenade_stick.json"
	};

	@Test
	@DisplayName("Verify all recipe advancement JSON files exist and have valid structure")
	public void testRecipeAdvancementsExistAndAreValid() {
		for (String path : ADVANCEMENT_FILES) {
			var stream = getClass().getClassLoader().getResourceAsStream(path);
			assertNotNull(stream, "Advancement resource must exist on classpath: " + path);

			try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonElement element = JsonParser.parseReader(reader);
				assertTrue(element.isJsonObject(), "Advancement must be a JSON object: " + path);
				JsonObject obj = element.getAsJsonObject();

				assertEquals("minecraft:recipes/root", obj.get("parent").getAsString(),
					"Advancement parent must be minecraft:recipes/root: " + path);

				assertTrue(obj.has("criteria"), "Advancement must have criteria: " + path);
				JsonObject criteria = obj.getAsJsonObject("criteria");
				assertTrue(criteria.size() > 0, "Criteria must not be empty: " + path);

				assertTrue(obj.has("requirements"), "Advancement must have requirements: " + path);
				JsonArray requirements = obj.getAsJsonArray("requirements");
				assertTrue(requirements.size() > 0, "Requirements must not be empty: " + path);

				assertTrue(obj.has("rewards"), "Advancement must have rewards: " + path);
				JsonObject rewards = obj.getAsJsonObject("rewards");
				assertTrue(rewards.has("recipes"), "Rewards must include recipes: " + path);

				JsonArray recipes = rewards.getAsJsonArray("recipes");
				assertTrue(recipes.size() > 0, "Rewarded recipes must not be empty: " + path);
				for (JsonElement r : recipes) {
					String recipeId = r.getAsString();
					assertTrue(recipeId.startsWith("modid-mmcli-agent-modding:"),
						"Rewarded recipe ID must belong to mod: " + recipeId);
				}
			} catch (Exception e) {
				fail("Failed to parse advancement " + path + ": " + e.getMessage());
			}
		}
	}

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
	@DisplayName("Verify all recipe JSON definitions exist, are valid, and conform to Minecraft 1.21+ object ingredient format")
	public void testRecipesExistAndAreValid() {
		for (String path : RECIPE_FILES) {
			var stream = getClass().getClassLoader().getResourceAsStream(path);
			assertNotNull(stream, "Recipe resource must exist on classpath: " + path);

			try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonElement element = JsonParser.parseReader(reader);
				assertTrue(element.isJsonObject(), "Recipe must be a JSON object: " + path);
				JsonObject obj = element.getAsJsonObject();

				assertTrue(obj.has("type"), "Recipe must define type: " + path);
				String type = obj.get("type").getAsString();
				assertTrue(type.equals("minecraft:crafting_shaped") || type.equals("minecraft:crafting_shapeless"),
					"Recipe must be shaped or shapeless: " + path);

				assertTrue(obj.has("result"), "Recipe must define result: " + path);
				JsonObject result = obj.getAsJsonObject("result");
				assertTrue(result.has("id"), "Recipe result must have id: " + path);
				assertTrue(result.get("id").getAsString().startsWith("modid-mmcli-agent-modding:"),
					"Recipe result id must belong to mod: " + path);
				assertTrue(result.has("count") && result.get("count").getAsInt() > 0,
					"Recipe result count must be positive: " + path);

				if (type.equals("minecraft:crafting_shaped")) {
					assertTrue(obj.has("pattern"), "Shaped recipe must define pattern: " + path);
					assertTrue(obj.has("key"), "Shaped recipe must define key: " + path);
					JsonObject key = obj.getAsJsonObject("key");
					for (String k : key.keySet()) {
						JsonElement val = key.get(k);
						assertIngredientConformsTo121(val, path, "key entry '" + k + "'");
					}
				} else {
					assertTrue(obj.has("ingredients"), "Shapeless recipe must define ingredients: " + path);
					JsonArray ingredients = obj.getAsJsonArray("ingredients");
					assertTrue(ingredients.size() > 0, "Shapeless ingredients must not be empty: " + path);
					for (int i = 0; i < ingredients.size(); i++) {
						assertIngredientConformsTo121(ingredients.get(i), path, "ingredient index " + i);
					}
				}
			} catch (Exception e) {
				fail("Failed to parse recipe " + path + ": " + e.getMessage());
			}
		}
	}

	private static void assertIngredientConformsTo121(JsonElement val, String path, String desc) {
		assertFalse(val.isJsonPrimitive(),
			"Minecraft 1.21+ rejects raw string ingredients! Must be JSON object with 'item' or array of objects: " + path + " -> " + desc);
		if (val.isJsonObject()) {
			JsonObject o = val.getAsJsonObject();
			assertTrue(o.has("item") || o.has("tag"),
				"Ingredient object must have 'item' or 'tag': " + path + " -> " + desc);
		} else if (val.isJsonArray()) {
			JsonArray arr = val.getAsJsonArray();
			assertTrue(arr.size() > 0, "Ingredient alternative list must not be empty: " + path + " -> " + desc);
			for (JsonElement item : arr) {
				assertTrue(item.isJsonObject(),
					"Ingredient alternatives in array must be JSON objects with 'item': " + path + " -> " + desc);
				assertTrue(item.getAsJsonObject().has("item") || item.getAsJsonObject().has("tag"),
					"Ingredient alternative object must have 'item' or 'tag': " + path + " -> " + desc);
			}
		} else {
			fail("Unexpected ingredient JSON structure: " + path + " -> " + desc);
		}
	}
}
