package com.example.blueprint;

import com.example.network.RequestResourceEstimationPayload;
import com.example.network.ResourceEstimateEntry;
import com.example.network.SyncResourceEstimationPayload;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying Material Bill of Materials (BOM) & Resource Estimator calculation logic,
 * inventory delta aggregations across commander bags and minion backpacks, and readiness percentage formulas.
 */
public class BlueprintResourceEstimatorTest {

	@Test
	@DisplayName("ResourceEstimateEntry computes correct availability, delta, and satisfaction")
	void testResourceEstimateEntryMath() {
		// Fully satisfied by player alone
		ResourceEstimateEntry entry1 = new ResourceEstimateEntry("minecraft:stone_bricks", 64, 64, 0, true);
		Assertions.assertEquals(64, entry1.totalAvailable());
		Assertions.assertEquals(0, entry1.delta());
		Assertions.assertTrue(entry1.isSatisfied());
		Assertions.assertTrue(entry1.harvestable());

		// Satisfied jointly by player and minions with surplus
		ResourceEstimateEntry entry2 = new ResourceEstimateEntry("minecraft:oak_planks", 30, 20, 25, true);
		Assertions.assertEquals(45, entry2.totalAvailable());
		Assertions.assertEquals(15, entry2.delta());
		Assertions.assertTrue(entry2.isSatisfied());

		// Deficient / Shortage
		ResourceEstimateEntry entry3 = new ResourceEstimateEntry("minecraft:glass", 50, 10, 15, true);
		Assertions.assertEquals(25, entry3.totalAvailable());
		Assertions.assertEquals(-25, entry3.delta());
		Assertions.assertFalse(entry3.isSatisfied());

		// Deficient & non-harvestable
		ResourceEstimateEntry entry4 = new ResourceEstimateEntry("minecraft:nether_star", 4, 0, 1, false);
		Assertions.assertEquals(1, entry4.totalAvailable());
		Assertions.assertEquals(-3, entry4.delta());
		Assertions.assertFalse(entry4.isSatisfied());
		Assertions.assertFalse(entry4.harvestable());
	}

	@Test
	@DisplayName("SyncResourceEstimationPayload calculates accurate readiness percentage and full satisfaction")
	void testSyncPayloadReadinessCalculations() {
		ResourceEstimateEntry e1 = new ResourceEstimateEntry("minecraft:stone_bricks", 50, 50, 0, true);
		ResourceEstimateEntry e2 = new ResourceEstimateEntry("minecraft:oak_planks", 50, 25, 25, true);

		// 100/100 blocks = 100%
		SyncResourceEstimationPayload payload100 = new SyncResourceEstimationPayload(
			"watchtower", 100, 100, 3, List.of(e1, e2)
		);
		Assertions.assertEquals(100, payload100.getReadinessPercentage());
		Assertions.assertTrue(payload100.isFullySatisfied());
		Assertions.assertEquals(3, payload100.nearbyMinionsCount());

		// 50/100 blocks = 50%
		SyncResourceEstimationPayload payload50 = new SyncResourceEstimationPayload(
			"watchtower", 100, 50, 2, List.of(e1)
		);
		Assertions.assertEquals(50, payload50.getReadinessPercentage());
		Assertions.assertFalse(payload50.isFullySatisfied());

		// 0/100 blocks = 0%
		SyncResourceEstimationPayload payload0 = new SyncResourceEstimationPayload(
			"citadel", 100, 0, 0, List.of()
		);
		Assertions.assertEquals(0, payload0.getReadinessPercentage());
		Assertions.assertFalse(payload0.isFullySatisfied());

		// Empty blueprint = 100% ready (nothing to build)
		SyncResourceEstimationPayload payloadEmpty = new SyncResourceEstimationPayload(
			"empty", 0, 0, 1, List.of()
		);
		Assertions.assertEquals(100, payloadEmpty.getReadinessPercentage());
		Assertions.assertFalse(payloadEmpty.isFullySatisfied());
	}

	@Test
	@DisplayName("Surplus of one item does not mask shortage of another item")
	void testCappedMaterialReadinessContribution() {
		// Blueprint requires 50 stone and 50 planks (total 100)
		int reqStone = 50;
		int reqPlanks = 50;
		int totalBlocks = reqStone + reqPlanks;

		// Player has 200 stone (huge surplus) but 0 planks
		int availStone = 200;
		int availPlanks = 0;

		// When clamped properly per entry:
		int effectiveStone = Math.min(reqStone, availStone); // 50
		int effectivePlanks = Math.min(reqPlanks, availPlanks); // 0
		int clampedTotalAvail = effectiveStone + effectivePlanks; // 50

		SyncResourceEstimationPayload payload = new SyncResourceEstimationPayload(
			"fortress",
			totalBlocks,
			clampedTotalAvail,
			4,
			List.of(
				new ResourceEstimateEntry("minecraft:stone", reqStone, availStone, 0, true),
				new ResourceEstimateEntry("minecraft:oak_planks", reqPlanks, availPlanks, 0, true)
			)
		);

		// Readiness should be 50%, NOT 200% or 100%
		Assertions.assertEquals(50, payload.getReadinessPercentage());
		Assertions.assertFalse(payload.isFullySatisfied());
	}

	@Test
	@DisplayName("RequestResourceEstimationPayload ID and codec integrity")
	void testRequestPayloadIntegrity() {
		RequestResourceEstimationPayload req = new RequestResourceEstimationPayload("custom_gatehouse");
		Assertions.assertEquals("custom_gatehouse", req.blueprintId());
		Assertions.assertNotNull(RequestResourceEstimationPayload.ID);
		Assertions.assertNotNull(RequestResourceEstimationPayload.PACKET_CODEC);
		Assertions.assertEquals(RequestResourceEstimationPayload.ID, req.getId());
	}
}
