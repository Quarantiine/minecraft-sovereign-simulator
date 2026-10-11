package com.example.blueprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit and source invariant tests for:
 * 1. Newest-first custom blueprint ordering (Slide 1 Slot 1 default).
 * 2. Dedicated BlueprintExplorerScreen 3D GUI with search filtering and zero text cut-off.
 * 3. Minion waypoint navigation anti-teleport / anti-jitter safety.
 */
public class BlueprintExplorerAndOrderingTest {

	private StructureBlueprint bpCastle;
	private StructureBlueprint bpWatchtower;
	private StructureBlueprint bpOutpost;

	@BeforeAll
	static void initBootstrap() {
		try {
			net.minecraft.SharedConstants.createGameVersion();
			net.minecraft.Bootstrap.initialize();
		} catch (Throwable ignored) {}
	}

	@BeforeEach
	void setUp() {
		BlueprintRegistry.clearCustomBlueprints();

		bpCastle = StructureBlueprint.builder("castle_keep", "Royal Castle Keep")
			.description("Majestic multi-story stone keep with battlements.")
			.build();

		bpWatchtower = StructureBlueprint.builder("watchtower_alpha", "Watchtower Alpha")
			.description("Tall wooden observation post.")
			.build();

		bpOutpost = StructureBlueprint.builder("outpost_bravo", "Frontier Outpost")
			.description("Fortified stone and wood frontier outpost.")
			.build();
	}

	@AfterEach
	void tearDown() {
		BlueprintRegistry.clearCustomBlueprints();
	}

	// =========================================================================
	// 1. Newest-First Custom Blueprint Ordering
	// =========================================================================

	@Test
	@DisplayName("registerCustomBlueprintFirst places newly saved blueprint at index 0 (Slide 1 Slot 1)")
	void testRegisterCustomBlueprintFirstPlacesAtFront() {
		// Register initial blueprint
		BlueprintRegistry.registerCustomBlueprint(bpCastle);
		List<StructureBlueprint> catalog1 = BlueprintRegistry.getActiveCatalog();
		Assertions.assertEquals(1, catalog1.size());
		Assertions.assertEquals("castle_keep", catalog1.get(0).getId());

		// Save a new blueprint with registerCustomBlueprintFirst
		BlueprintRegistry.registerCustomBlueprintFirst(bpWatchtower);

		List<StructureBlueprint> catalog2 = BlueprintRegistry.getActiveCatalog();
		Assertions.assertEquals(2, catalog2.size());
		// Newly saved watchtower MUST be at index 0 (Slide 1 Slot 1)!
		Assertions.assertEquals("watchtower_alpha", catalog2.get(0).getId(),
			"Newly saved blueprint must be at index 0 (first in catalog)");
		Assertions.assertEquals("castle_keep", catalog2.get(1).getId());

		// Save a third blueprint with registerCustomBlueprintFirst
		BlueprintRegistry.registerCustomBlueprintFirst(bpOutpost);

		List<StructureBlueprint> catalog3 = BlueprintRegistry.getActiveCatalog();
		Assertions.assertEquals(3, catalog3.size());
		Assertions.assertEquals("outpost_bravo", catalog3.get(0).getId(),
			"Most recently saved blueprint must be index 0");
		Assertions.assertEquals("watchtower_alpha", catalog3.get(1).getId());
		Assertions.assertEquals("castle_keep", catalog3.get(2).getId());
	}

	@Test
	@DisplayName("CustomBlueprintPersistentState preserves newest-first ordering on save and load")
	void testPersistentStatePreservesNewestFirstOrder() {
		CustomBlueprintPersistentState state = new CustomBlueprintPersistentState();

		state.addBlueprint(bpCastle);
		state.addBlueprint(bpWatchtower); // Newest
		state.addBlueprint(bpOutpost);    // Newest of all

		List<StructureBlueprint> list = new ArrayList<>(state.getAllBlueprints());
		Assertions.assertEquals(3, list.size());
		Assertions.assertEquals("outpost_bravo", list.get(0).getId(), "Newest must be at index 0");
		Assertions.assertEquals("watchtower_alpha", list.get(1).getId());
		Assertions.assertEquals("castle_keep", list.get(2).getId());

		// Round-trip serialize to NBT
		NbtCompound nbt = new NbtCompound();
		state.writeNbt(nbt, null);

		CustomBlueprintPersistentState loaded = CustomBlueprintPersistentState.fromNbt(nbt, null);
		List<StructureBlueprint> loadedList = new ArrayList<>(loaded.getAllBlueprints());
		Assertions.assertEquals(3, loadedList.size());
		Assertions.assertEquals("outpost_bravo", loadedList.get(0).getId(), "Loaded order must preserve newest at index 0");
		Assertions.assertEquals("watchtower_alpha", loadedList.get(1).getId());
		Assertions.assertEquals("castle_keep", loadedList.get(2).getId());
	}

	// =========================================================================
	// 2. BlueprintExplorerScreen Source Invariants & Functionality
	// =========================================================================

	@Test
	@DisplayName("Source Invariant: BlueprintExplorerScreen defines search filtering, 3D projection, and zero cut-off")
	void testBlueprintExplorerScreenSourceInvariants() throws IOException {
		Path path = Path.of("src/client/java/com/example/client/gui/BlueprintExplorerScreen.java");
		Assertions.assertTrue(Files.exists(path), "BlueprintExplorerScreen.java must exist");
		String content = Files.readString(path);

		// Window dimensions & layout
		Assertions.assertTrue(content.contains("WINDOW_WIDTH = 424;"), "Explorer window width must be 424");
		Assertions.assertTrue(content.contains("WINDOW_HEIGHT = 262;"), "Explorer window height must be 262");
		Assertions.assertTrue(content.contains("LIST_PAGE_SIZE = 5;"), "Card list page size must be 5");

		// Search field & query filtering
		Assertions.assertTrue(content.contains("TextFieldWidget"), "Must declare search TextFieldWidget");
		Assertions.assertTrue(content.contains("searchField.setChangedListener"), "Must listen to real-time search query changes");
		Assertions.assertTrue(content.contains("refreshFilteredDataset"), "Must filter blueprints dataset on query change");

		// 3D Voxel rendering & orbital controls
		Assertions.assertTrue(content.contains("render3DBlueprint"), "Must define render3DBlueprint");
		Assertions.assertTrue(content.contains("mouseDragged"), "Must support drag rotation");
		Assertions.assertTrue(content.contains("mouseScrolled"), "Must support zoom scrolling");
		Assertions.assertTrue(content.contains("context.enableScissor"), "Must use scissor clipping for 3D viewport");

		// Blur disabled
		Assertions.assertTrue(content.contains("protected void applyBlur(float delta)"), "Must disable background blur");

		// Text wrapping for zero cut-off
		Assertions.assertTrue(content.contains("textRenderer.wrapLines"), "Must wrap description text to prevent cut-off");
		Assertions.assertTrue(content.contains("textRenderer.trimToWidth"), "Must trim titles with ellipsis");

		// Edge case: override close() to safely return to parent screen
		Assertions.assertTrue(content.contains("public void close()"), "Must override close to return to parent screen");
		Assertions.assertTrue(content.contains("No Matching Blueprints"), "Must display placeholder when search yields 0 matches");
	}

	@Test
	@DisplayName("Source Invariant: BlueprintExplorerScreen defines 3D clarity features (quads, culling, maximize, layer slicer, and pan)")
	void testBlueprint3DClarityOverhaulSourceInvariants() throws IOException {
		Path path = Path.of("src/client/java/com/example/client/gui/BlueprintExplorerScreen.java");
		Assertions.assertTrue(Files.exists(path), "BlueprintExplorerScreen.java must exist");
		String content = Files.readString(path);

		// 1. Maximize / Fullscreen Viewport Mode
		Assertions.assertTrue(content.contains("isMaximized"), "Must support maximized viewport state");
		Assertions.assertTrue(content.contains("toggleMaximize"), "Must define toggleMaximize method");
		Assertions.assertTrue(content.contains("maximizeBtn"), "Must declare maximizeBtn button");

		// 2. Architectural Layer Slicer / Cutaway View
		Assertions.assertTrue(content.contains("sliceY"), "Must define sliceY layer cutaway variable");
		Assertions.assertTrue(content.contains("stepLayer"), "Must define stepLayer method for stepping layers");
		Assertions.assertTrue(content.contains("resetLayerSlice"), "Must define resetLayerSlice method");
		Assertions.assertTrue(content.contains("sliceDownBtn") && content.contains("sliceUpBtn"), "Must declare layer slicer buttons");

		// 3. True 3D Rotated Geometry & Occlusion Culling
		Assertions.assertTrue(content.contains("ProjectedFace"), "Must define ProjectedFace for true 3D rotated face quads");
		Assertions.assertTrue(content.contains("isOpaque"), "Must define isOpaque for 6-directional interior block occlusion culling");
		Assertions.assertTrue(content.contains("sunX") && content.contains("sunY"), "Must define directional sun lighting vector");

		// 4. Pan & Compass Controls
		Assertions.assertTrue(content.contains("panX") && content.contains("panY"), "Must support 2D camera panning");
		Assertions.assertTrue(content.contains("renderGroundPedestal"), "Must render ground foundation pedestal grid");
		Assertions.assertTrue(content.contains("renderViewportHud"), "Must render viewport HUD with compass indicator");

		// 5. Button Mouse Click Priority Delegation
		Assertions.assertTrue(content.contains("if (super.mouseClicked(mouseX, mouseY, button)) {\n\t\t\treturn true;\n\t\t}"),
			"mouseClicked must delegate to super.mouseClicked first so toolbar and modal buttons receive mouse clicks");

		// 6. Architectural & Botanical Profile Bounds & Multi-Face Textures
		Assertions.assertTrue(content.contains("getBlockBounds"), "Must define getBlockBounds for custom flower/plant/slab geometry");
		Assertions.assertTrue(content.contains("getFaceCorners"), "Must define getFaceCorners for multi-face rendering");
		Assertions.assertTrue(content.contains("resolveDyeColor"), "Must define resolveDyeColor for 16-color wool/concrete/terracotta/glass");
		Assertions.assertTrue(content.contains("poppy") && content.contains("dandelion") && content.contains("blue_orchid"),
			"Must map authentic flower bloom colors so flora does not look like stone");
	}

	@Test
	@DisplayName("Source Invariant: CommandScepterScreen integrates 3D View button and explorer callbacks")
	void testCommandScepterScreenExplorerIntegration() throws IOException {
		Path path = Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java");
		Assertions.assertTrue(Files.exists(path), "CommandScepterScreen.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(content.contains("explorerBtn"), "Must declare explorerBtn");
		Assertions.assertTrue(content.contains("openBlueprintExplorer"), "Must define openBlueprintExplorer method");
		Assertions.assertTrue(content.contains("selectBlueprintFromExplorer"), "Must define selectBlueprintFromExplorer callback");
		Assertions.assertTrue(content.contains("BlueprintExplorerScreen"), "Must instantiate BlueprintExplorerScreen");
	}

	// =========================================================================
	// 3. Minion Waypoint Unstuck Safeguard & Anti-Jitter Tests
	// =========================================================================

	@Test
	@DisplayName("Source Invariant: MinionEntity unstuck check excludes walkable floor blocks and requires torso/head suffocation")
	void testMinionEntityUnstuckSafeguardLogic() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		// Must check torso and head
		Assertions.assertTrue(content.contains("BlockPos torsoPos = BlockPos.ofFloored(this.getX(), this.getY() + 0.6D, this.getZ());"),
			"Must sample torso position at Y + 0.6D to avoid false floor triggers");
		Assertions.assertTrue(content.contains("BlockPos headPos = BlockPos.ofFloored(this.getX(), this.getEyeY(), this.getZ());"),
			"Must sample head position at eye Y");

		// Must use shouldSuffocate / isFullCube
		Assertions.assertTrue(content.contains("torsoState.shouldSuffocate"),
			"Must check if torso block suffocates entity");
		Assertions.assertTrue(content.contains("headState.shouldSuffocate"),
			"Must check if head block suffocates entity");

		// Must exclude entities in vehicles (minecarts, boats)
		Assertions.assertTrue(content.contains("this.hasVehicle()"),
			"Must not trigger unstuck if minion is riding a vehicle");

		// Must require cooldown to prevent teleport loops
		Assertions.assertTrue(content.contains("if (this.unstuckCooldownTicks > 0)"),
			"Must check unstuckCooldownTicks before attempting teleport");
		Assertions.assertTrue(content.contains("this.unstuckCooldownTicks = 20;"),
			"Must set 20-tick safety cooldown on successful unstuck teleport");
		Assertions.assertTrue(content.contains("if (this.unstuckCooldownTicks > 0) {\n\t\t\tthis.unstuckCooldownTicks--;"),
			"Must decrement unstuckCooldownTicks in tick()");
	}
}
