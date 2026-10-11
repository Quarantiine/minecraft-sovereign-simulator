package com.example.client.gui;

import com.example.network.ConfigurePatrolRoutePayload;
import com.example.patrol.PatrolRoute;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test suite validating {@link PatrolRouteEditModalScreen}, hex color picker parsing,
 * color preset swatches, route name validation, and network payload dispatch.
 */
public class PatrolRouteEditModalScreenTest {

	@Test
	@DisplayName("PatrolRoute hex parsing and formatting handles arbitrary RGB values and fallbacks")
	void testHexColorParsingAndFormatting() {
		Assertions.assertEquals(0xFFD700, PatrolRoute.parseHexColor("#FFD700"));
		Assertions.assertEquals(0x00E5FF, PatrolRoute.parseHexColor("0x00E5FF"));
		Assertions.assertEquals(0x00FF66, PatrolRoute.parseHexColor("00FF66"));
		Assertions.assertEquals(0xFF2244, PatrolRoute.parseHexColor("#FF2244"));
		Assertions.assertEquals(0xFFD700, PatrolRoute.parseHexColor("invalid_hex"));
		Assertions.assertEquals(0xFFD700, PatrolRoute.parseHexColor(""));
		Assertions.assertEquals(0xFFD700, PatrolRoute.parseHexColor(null));

		Assertions.assertEquals("#FFD700", PatrolRoute.toHexCode(0xFFD700));
		Assertions.assertEquals("#00E5FF", PatrolRoute.toHexCode(0x00E5FF));
		Assertions.assertEquals("#00FF66", PatrolRoute.toHexCode(0x00FF66));
	}

	@Test
	@DisplayName("PatrolRoute custom route creation and mutation methods")
	void testCustomRouteCreationAndMutation() {
		PatrolRoute route = PatrolRoute.createCustom(5, "Perimeter Alpha", 0x3388FF);
		Assertions.assertEquals(5, route.routeId());
		Assertions.assertEquals("Perimeter Alpha", route.name());
		Assertions.assertEquals(0x3388FF, route.colorRgb());
		Assertions.assertEquals(PatrolRoute.PatrolMode.LOOP, route.patrolMode());
		Assertions.assertTrue(route.waypoints().isEmpty());

		PatrolRoute renamed = route.withName("Inner Fortress");
		Assertions.assertEquals("Inner Fortress", renamed.name());
		Assertions.assertEquals(0x3388FF, renamed.colorRgb());

		PatrolRoute recolored = route.withColorRgb(0xFF3399);
		Assertions.assertEquals(0xFF3399, recolored.colorRgb());
		Assertions.assertEquals("Perimeter Alpha", recolored.name());

		PatrolRoute pingPong = route.withPatrolMode(PatrolRoute.PatrolMode.PING_PONG);
		Assertions.assertEquals(PatrolRoute.PatrolMode.PING_PONG, pingPong.patrolMode());
	}

	@Test
	@DisplayName("Source contract: PatrolRouteEditModalScreen defines presets and disables blur")
	void testPatrolRouteEditModalScreenSourceContract() throws IOException {
		Path path = Path.of("src/client/java/com/example/client/gui/PatrolRouteEditModalScreen.java");
		Assertions.assertTrue(Files.exists(path), "PatrolRouteEditModalScreen.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(content.contains("public static final int WINDOW_WIDTH = 300;"));
		Assertions.assertTrue(content.contains("public static final int WINDOW_HEIGHT = 240;"));
		Assertions.assertTrue(content.contains("public static final int[] PRESET_COLORS = {"));
		Assertions.assertTrue(content.contains("0xFFD700, // Gold"));
		Assertions.assertTrue(content.contains("0x00E5FF, // Cyan"));
		Assertions.assertTrue(content.contains("0x00FF66, // Emerald"));
		Assertions.assertTrue(content.contains("0xB300FF, // Arcane Purple"));
		Assertions.assertTrue(content.contains("0xFF2244, // Crimson"));
		Assertions.assertTrue(content.contains("protected void applyBlur(float delta)"));
		Assertions.assertTrue(content.contains("ModClientNetworking.sendSavePatrolRoute"));
		Assertions.assertTrue(content.contains("ModClientNetworking.sendDeletePatrolRoute"));
	}

	@Test
	@DisplayName("ConfigurePatrolRoutePayload serialization contract")
	void testConfigurePatrolRoutePayload() {
		ConfigurePatrolRoutePayload savePayload = new ConfigurePatrolRoutePayload(
			ConfigurePatrolRoutePayload.Action.SAVE,
			2,
			"Wall Sentry Route",
			0x00FF66,
			PatrolRoute.PatrolMode.PING_PONG
		);

		Assertions.assertEquals(ConfigurePatrolRoutePayload.Action.SAVE, savePayload.action());
		Assertions.assertEquals(2, savePayload.routeId());
		Assertions.assertEquals("Wall Sentry Route", savePayload.name());
		Assertions.assertEquals(0x00FF66, savePayload.colorRgb());
		Assertions.assertEquals(PatrolRoute.PatrolMode.PING_PONG, savePayload.patrolMode());
		Assertions.assertNotNull(savePayload.getId());
		Assertions.assertNotNull(ConfigurePatrolRoutePayload.PACKET_CODEC);

		ConfigurePatrolRoutePayload deletePayload = new ConfigurePatrolRoutePayload(
			ConfigurePatrolRoutePayload.Action.DELETE,
			2,
			"Wall Sentry Route",
			0,
			PatrolRoute.PatrolMode.LOOP
		);
		Assertions.assertEquals(ConfigurePatrolRoutePayload.Action.DELETE, deletePayload.action());
	}

	@Test
	@DisplayName("ClientPatrolRouteTracker prevents phantom route creation and prepends newly created routes")
	void testClientPatrolRouteTrackerNoPhantomAndPrependOrdering() {
		com.example.client.renderer.ClientPatrolRouteTracker.clear();

		// 1. getRoute must return null when route does not exist (no phantom creation)
		Assertions.assertNull(
			com.example.client.renderer.ClientPatrolRouteTracker.getRoute(99),
			"getRoute must return null for non-existent route, never auto-creating"
		);
		Assertions.assertTrue(
			com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList().isEmpty(),
			"Tracker must remain empty after querying non-existent route"
		);

		// 2. Initialize with Route 0
		PatrolRoute route0 = PatrolRoute.createDefault(0);
		com.example.client.renderer.ClientPatrolRouteTracker.updateRoute(route0);
		Assertions.assertEquals(1, com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList().size());
		Assertions.assertEquals(0, com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList().get(0).routeId());

		// 3. Newly created route (Route 1) must be prepended so it comes first in order (Slide 1 Slot 1)
		PatrolRoute route1 = PatrolRoute.createCustom(1, "Outpost Perimeter", 0x00E5FF);
		com.example.client.renderer.ClientPatrolRouteTracker.updateRoute(route1);

		List<PatrolRoute> ordered = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
		Assertions.assertEquals(2, ordered.size());
		Assertions.assertEquals(1, ordered.get(0).routeId(), "Newly created route must appear first in order");
		Assertions.assertEquals(0, ordered.get(1).routeId(), "Older route must shift to index 1");

		// 4. In-place edit of existing route preserves relative ordering
		PatrolRoute editedRoute0 = route0.withName("Gold Base Loop");
		com.example.client.renderer.ClientPatrolRouteTracker.updateRoute(editedRoute0);

		List<PatrolRoute> afterEdit = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
		Assertions.assertEquals(2, afterEdit.size());
		Assertions.assertEquals(1, afterEdit.get(0).routeId(), "Route 1 must still be first");
		Assertions.assertEquals(0, afterEdit.get(1).routeId(), "Route 0 must remain second");
		Assertions.assertEquals("Gold Base Loop", afterEdit.get(1).name());

		// 5. Creating a 3rd route (Route 2) prepends to front
		PatrolRoute route2 = PatrolRoute.createCustom(2, "Castle Gate", 0x00FF66);
		com.example.client.renderer.ClientPatrolRouteTracker.updateRoute(route2);

		List<PatrolRoute> threeRoutes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
		Assertions.assertEquals(3, threeRoutes.size());
		Assertions.assertEquals(2, threeRoutes.get(0).routeId(), "Route 2 must appear first");
		Assertions.assertEquals(1, threeRoutes.get(1).routeId(), "Route 1 must appear second");
		Assertions.assertEquals(0, threeRoutes.get(2).routeId(), "Route 0 must appear third");
	}

	@Test
	@DisplayName("PatrolRouteEditModalScreen does not pre-register route on instantiation")
	void testPatrolRouteEditModalScreenNoPreRegistration() {
		com.example.client.renderer.ClientPatrolRouteTracker.clear();

		// Opening modal for a new route ID 5 must NOT register route 5 into tracker
		PatrolRouteEditModalScreen modal = new PatrolRouteEditModalScreen(5, true, null);
		Assertions.assertEquals(5, modal.getRouteId());
		Assertions.assertTrue(modal.isNewRoute());

		// Verify tracker is still clean
		Assertions.assertNull(
			com.example.client.renderer.ClientPatrolRouteTracker.getRoute(5),
			"Instantiating edit modal for new route must not pre-register phantom route"
		);
		Assertions.assertTrue(
			com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList().isEmpty(),
			"Tracker must remain empty"
		);
	}
}
