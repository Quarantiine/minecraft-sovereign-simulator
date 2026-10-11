package com.example.client.gui;

import com.example.client.targeting.ClientTargetFilterTracker;
import com.example.targeting.MinionTargetFilterManager;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating MinionTargetFilterModalScreen selection logic:
 * - Initial checkbox state derived from the disabled set
 * - Toggle, Select All, Clear All
 * - Disabled-set computation for Submit
 * - Cancel discards, close/pause contract
 */
public class MinionTargetFilterModalScreenTest {

	private static final Identifier ZOMBIE = Identifier.ofVanilla("zombie");
	private static final Identifier CREEPER = Identifier.ofVanilla("creeper");
	private static final Identifier SKELETON = Identifier.ofVanilla("skeleton");
	private static final List<Identifier> CANDIDATES = List.of(ZOMBIE, CREEPER, SKELETON);

	@org.junit.jupiter.api.BeforeAll
	static void initBootstrap() {
		try {
			net.minecraft.SharedConstants.createGameVersion();
			net.minecraft.Bootstrap.initialize();
		} catch (Throwable ignored) {}
	}

	@BeforeEach
	void setUp() {
		ClientTargetFilterTracker.clear();
	}

	@Test
	@DisplayName("Modal checks every candidate that is not in the disabled set")
	void testInitialState() {
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, Set.of(CREEPER));

		Assertions.assertTrue(modal.isAllowed(ZOMBIE));
		Assertions.assertFalse(modal.isAllowed(CREEPER));
		Assertions.assertTrue(modal.isAllowed(SKELETON));
		Assertions.assertEquals(2, modal.getAllowedCount());
		Assertions.assertEquals(3, modal.getTotalCount());
		Assertions.assertEquals(List.of(CREEPER), modal.computeDisabled());
	}

	@Test
	@DisplayName("Toggle flips a single mob and ignores unknown ids")
	void testToggle() {
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, Set.of());

		modal.toggle(ZOMBIE);
		Assertions.assertFalse(modal.isAllowed(ZOMBIE));
		Assertions.assertEquals(List.of(ZOMBIE), modal.computeDisabled());

		modal.toggle(ZOMBIE);
		Assertions.assertTrue(modal.isAllowed(ZOMBIE));
		Assertions.assertTrue(modal.computeDisabled().isEmpty());

		modal.toggle(Identifier.ofVanilla("cow"));
		modal.toggle(null);
		Assertions.assertEquals(3, modal.getAllowedCount());
	}

	@Test
	@DisplayName("Select All and Clear All update every checkbox")
	void testSelectAndClearAll() {
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, Set.of(ZOMBIE, CREEPER));

		modal.selectAll();
		Assertions.assertEquals(3, modal.getAllowedCount());
		Assertions.assertTrue(modal.computeDisabled().isEmpty());

		modal.clearAll();
		Assertions.assertEquals(0, modal.getAllowedCount());
		Assertions.assertEquals(CANDIDATES, modal.computeDisabled());
	}

	@Test
	@DisplayName("Cancel closes without touching the cached filter")
	void testCancel() {
		ClientTargetFilterTracker.setDisabled(Set.of(CREEPER));
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, ClientTargetFilterTracker.getDisabled());

		modal.clearAll();
		modal.cancel();

		Assertions.assertTrue(modal.isClosed());
		Assertions.assertFalse(modal.isSubmitted());
		Assertions.assertEquals(Set.of(CREEPER), ClientTargetFilterTracker.getDisabled());
	}

	@Test
	@DisplayName("Submit caches the unchecked mobs locally and closes the modal")
	void testSubmit() {
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, Set.of());

		modal.toggle(SKELETON);
		Assertions.assertDoesNotThrow(modal::submit);

		Assertions.assertTrue(modal.isSubmitted());
		Assertions.assertTrue(modal.isClosed());
		Assertions.assertEquals(Set.of(SKELETON), ClientTargetFilterTracker.getDisabled());
	}

	@Test
	@DisplayName("Tracker defaults to the legacy disabled set and resets on clear")
	void testTrackerDefaults() {
		Assertions.assertEquals(MinionTargetFilterManager.DEFAULT_DISABLED, ClientTargetFilterTracker.getDisabled());

		ClientTargetFilterTracker.setDisabled(Set.of(ZOMBIE));
		Assertions.assertEquals(Set.of(ZOMBIE), ClientTargetFilterTracker.getDisabled());

		ClientTargetFilterTracker.clear();
		Assertions.assertEquals(MinionTargetFilterManager.DEFAULT_DISABLED, ClientTargetFilterTracker.getDisabled());
	}

	@Test
	@DisplayName("Modal constants and pause behavior")
	void testConstantsAndPause() {
		Assertions.assertEquals(372, MinionTargetFilterModalScreen.WINDOW_WIDTH);
		Assertions.assertEquals(300, MinionTargetFilterModalScreen.WINDOW_HEIGHT);
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, Set.of());
		Assertions.assertFalse(modal.shouldPause(), "Target filter modal must not pause singleplayer game");
	}

	@Test
	@DisplayName("Search query dynamically filters visible entries while preserving selection state")
	void testSearchQueryFiltering() {
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, Set.of());
		Assertions.assertEquals(3, modal.getFilteredEntriesCount());

		// Search for "creeper"
		modal.setSearchQuery("creeper");
		Assertions.assertEquals("creeper", modal.getSearchQuery());
		Assertions.assertEquals(1, modal.getFilteredEntriesCount());
		Assertions.assertEquals(CREEPER, modal.getFilteredEntries().get(0).id());

		// Toggle creeper while filtered
		modal.toggle(CREEPER);
		Assertions.assertFalse(modal.isAllowed(CREEPER));
		Assertions.assertTrue(modal.isAllowed(ZOMBIE));

		// Clear search query
		modal.setSearchQuery("");
		Assertions.assertEquals(3, modal.getFilteredEntriesCount());
		Assertions.assertFalse(modal.isAllowed(CREEPER));
		Assertions.assertTrue(modal.isAllowed(ZOMBIE));
	}

	@Test
	@DisplayName("Applying built-in presets automatically updates checkbox selections")
	void testPresetApplication() {
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, Set.of());

		// Apply None (Passive)
		modal.applyPreset("None (Passive)");
		Assertions.assertEquals(0, modal.getAllowedCount());

		// Apply All Hostiles
		modal.applyPreset("All Hostiles");
		Assertions.assertEquals(3, modal.getAllowedCount());

		// Apply Creeper Safe
		modal.applyPreset("Creeper Safe");
		Assertions.assertEquals(2, modal.getAllowedCount());
		Assertions.assertTrue(modal.isAllowed(ZOMBIE));
		Assertions.assertTrue(modal.isAllowed(SKELETON));
		Assertions.assertFalse(modal.isAllowed(CREEPER));
	}

	@Test
	@DisplayName("Cycling presets steps through available presets and updates title")
	void testPresetCycling() {
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, Set.of());
		String firstTitle = modal.getSelectedPresetTitle();
		Assertions.assertNotNull(firstTitle);

		modal.cyclePreset(false);
		String secondTitle = modal.getSelectedPresetTitle();
		Assertions.assertNotEquals(firstTitle, secondTitle);

		modal.cyclePreset(true);
		Assertions.assertEquals(firstTitle, modal.getSelectedPresetTitle());
	}

	@Test
	@DisplayName("Saving custom preset from modal registers it and sets it as active")
	void testSaveCustomPresetFromModal() {
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, Set.of(CREEPER));
		modal.saveCurrentAsPreset("My Custom Setup");

		Assertions.assertEquals("My Custom Setup", modal.getSelectedPresetTitle());
		Assertions.assertTrue(modal.isCurrentPresetUserSaved());

		boolean deleted = modal.deleteCurrentPreset();
		Assertions.assertTrue(deleted);
		Assertions.assertFalse(modal.isCurrentPresetUserSaved());
	}

	@Test
	@DisplayName("Custom submit consumer is invoked with computed disabled mob IDs")
	void testCustomSubmitCallback() {
		List<Identifier> captured = new java.util.ArrayList<>();
		MinionTargetFilterModalScreen modal = new MinionTargetFilterModalScreen(null, CANDIDATES, Set.of(), captured::addAll);

		modal.toggle(ZOMBIE);
		modal.submit();

		Assertions.assertTrue(modal.isSubmitted());
		Assertions.assertTrue(modal.isClosed());
		Assertions.assertEquals(List.of(ZOMBIE), captured);
	}
}
