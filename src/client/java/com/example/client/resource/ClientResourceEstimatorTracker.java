package com.example.client.resource;

import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.entity.ai.logistics.MinionHarvestingHelper;
import com.example.entity.custom.MinionEntity;
import com.example.item.custom.CommandScepterItem;
import com.example.network.ResourceEstimateEntry;
import com.example.network.SyncResourceEstimationPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;

/**
 * Client-side tracker and cache for Material Bill of Materials (BOM) resource estimation.
 * Maintains server-synced estimation payloads and provides instant client-side offline calculation
 * fallbacks for zero-latency UI rendering.
 */
public final class ClientResourceEstimatorTracker {

	private static final Map<String, SyncResourceEstimationPayload> ESTIMATION_CACHE = new ConcurrentHashMap<>();
	private static volatile SyncResourceEstimationPayload latestEstimation = null;

	private ClientResourceEstimatorTracker() {}

	/**
	 * Stores an updated server-authoritative resource estimation payload.
	 *
	 * @param payload The incoming server-to-client resource estimation packet.
	 */
	public static void setEstimation(SyncResourceEstimationPayload payload) {
		if (payload == null) return;
		ESTIMATION_CACHE.put(payload.blueprintId(), payload);
		latestEstimation = payload;
	}

	/**
	 * Retrieves the most recent estimation payload for the given blueprint ID.
	 * If not cached from the server, computes a local inventory fallback estimate.
	 *
	 * @param blueprintId Target blueprint identifier.
	 * @return Cached or locally computed resource estimation payload.
	 */
	public static SyncResourceEstimationPayload getEstimation(String blueprintId) {
		if (blueprintId == null || blueprintId.isBlank()) {
			return new SyncResourceEstimationPayload("empty", 0, 0, 0, List.of());
		}

		SyncResourceEstimationPayload cached = ESTIMATION_CACHE.get(blueprintId);
		if (cached != null) {
			return cached;
		}

		// Compute local estimate fallback
		return computeLocalEstimate(blueprintId);
	}

	/**
	 * Returns the most recently updated resource estimation payload regardless of blueprint.
	 */
	public static SyncResourceEstimationPayload getLatestEstimation() {
		return latestEstimation;
	}

	/**
	 * Computes a local client-side estimation from the player's personal inventory and visible minions.
	 *
	 * @param blueprintId Target blueprint identifier.
	 * @return A synthesized resource estimation payload.
	 */
	public static SyncResourceEstimationPayload computeLocalEstimate(String blueprintId) {
		StructureBlueprint blueprint = BlueprintRegistry.getOrDefault(blueprintId);
		if (blueprint == null || blueprint.getBlockCount() == 0) {
			return new SyncResourceEstimationPayload(blueprintId, 0, 0, 0, List.of());
		}

		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;

		int nearbyMinionsCount = 0;
		if (player != null && world != null) {
			Box searchBox = player.getBoundingBox().expand(CommandScepterItem.MINION_COMMAND_RADIUS);
			List<MinionEntity> minions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player)
			);
			nearbyMinionsCount = minions.size();
		}

		Map<Item, Integer> requiredItems = blueprint.getRequiredItems();
		List<ResourceEstimateEntry> entries = new ArrayList<>();
		int totalReqBlocks = blueprint.getBlockCount();
		int totalAvailableBlocks = 0;

		for (Map.Entry<Item, Integer> entry : requiredItems.entrySet()) {
			Item item = entry.getKey();
			int req = entry.getValue();
			if (req <= 0) continue;

			int playerCount = 0;
			if (player != null) {
				for (int i = 0; i < player.getInventory().size(); i++) {
					ItemStack stack = player.getInventory().getStack(i);
					if (!stack.isEmpty() && stack.isOf(item)) {
						playerCount += stack.getCount();
					}
				}
			}

			boolean harvestable = MinionHarvestingHelper.isHarvestable(item);

			String itemId = Registries.ITEM.getId(item).toString();
			entries.add(new ResourceEstimateEntry(itemId, req, playerCount, 0, harvestable));

			totalAvailableBlocks += Math.min(req, playerCount);
		}

		SyncResourceEstimationPayload localPayload = new SyncResourceEstimationPayload(
			blueprintId,
			totalReqBlocks,
			totalAvailableBlocks,
			nearbyMinionsCount,
			entries
		);
		ESTIMATION_CACHE.put(blueprintId, localPayload);
		return localPayload;
	}

	/**
	 * Clears the estimation cache upon server disconnect.
	 */
	public static void clear() {
		ESTIMATION_CACHE.clear();
		latestEstimation = null;
	}
}
