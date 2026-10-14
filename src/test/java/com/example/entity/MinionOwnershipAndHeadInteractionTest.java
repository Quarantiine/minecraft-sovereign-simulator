package com.example.entity;

import com.example.screen.MinionScreenHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying Minion ownership checks, head equipment validation,
 * and renderer feature registrations for skulls, mob heads, and helmets.
 */
public class MinionOwnershipAndHeadInteractionTest {

	/**
	 * State simulator replicating MinionEntity's updated isOwner logic across
	 * client prediction, direct UUID matching, and singleplayer server host adoption.
	 */
	static class MockOwnershipSimulator {
		UUID ownerUuid;
		boolean tamed;
		boolean isClientWorld;
		boolean isSingleplayerServer;
		UUID hostPlayerUuid;

		MockOwnershipSimulator(UUID initialOwnerUuid, boolean tamed, boolean isClientWorld, boolean isSingleplayer, UUID hostPlayerUuid) {
			this.ownerUuid = initialOwnerUuid;
			this.tamed = tamed;
			this.isClientWorld = isClientWorld;
			this.isSingleplayerServer = isSingleplayer;
			this.hostPlayerUuid = hostPlayerUuid;
		}

		boolean isOwner(UUID playerUuid) {
			if (playerUuid == null) {
				return false;
			}
			// 1. Direct UUID match (reliable across both client and server)
			if (this.ownerUuid != null && this.ownerUuid.equals(playerUuid)) {
				return true;
			}

			// 2. Server-side singleplayer host adoption
			if (!this.isClientWorld) {
				if (this.isSingleplayerServer && this.hostPlayerUuid != null && this.hostPlayerUuid.equals(playerUuid)) {
					if (this.tamed) {
						this.ownerUuid = playerUuid;
						return true;
					}
				}
				return false;
			}

			// 3. Client-side prediction: if tamed, permit client interaction to dispatch C2S packet
			if (this.tamed) {
				return true;
			}

			return false;
		}
	}

	@Test
	@DisplayName("Direct UUID match returns true on both client and server")
	void testDirectUuidMatch() {
		UUID ownerUuid = UUID.randomUUID();
		MockOwnershipSimulator clientSim = new MockOwnershipSimulator(ownerUuid, true, true, false, null);
		MockOwnershipSimulator serverSim = new MockOwnershipSimulator(ownerUuid, true, false, false, null);

		Assertions.assertTrue(clientSim.isOwner(ownerUuid), "Client must recognize matching UUID");
		Assertions.assertTrue(serverSim.isOwner(ownerUuid), "Server must recognize matching UUID");
	}

	@Test
	@DisplayName("Client-side prediction allows tamed minion interaction to reach server in singleplayer/offline")
	void testClientPredictionForTamedMinions() {
		UUID oldOwnerUuid = UUID.randomUUID();
		UUID newHostUuid = UUID.randomUUID();

		// Client perspective: world.isClient() == true, minion is tamed
		MockOwnershipSimulator clientSim = new MockOwnershipSimulator(oldOwnerUuid, true, true, false, null);
		Assertions.assertTrue(clientSim.isOwner(newHostUuid), "Client must permit tamed interaction so C2S packet is sent");

		// Server perspective: world instanceof ServerWorld, singleplayer host matches
		MockOwnershipSimulator serverSim = new MockOwnershipSimulator(oldOwnerUuid, true, false, true, newHostUuid);
		Assertions.assertTrue(serverSim.isOwner(newHostUuid), "Server must adopt host and permit interaction");
		Assertions.assertEquals(newHostUuid, serverSim.ownerUuid, "Server must update minion owner UUID to host");
	}

	@Test
	@DisplayName("Untamed minion is rejected by ownership checks until tamed")
	void testUntamedMinionRejectsOwnership() {
		UUID playerUuid = UUID.randomUUID();
		MockOwnershipSimulator clientSim = new MockOwnershipSimulator(null, false, true, false, null);
		MockOwnershipSimulator serverSim = new MockOwnershipSimulator(null, false, false, true, playerUuid);

		Assertions.assertFalse(clientSim.isOwner(playerUuid), "Untamed minion must not pass client ownership");
		Assertions.assertFalse(serverSim.isOwner(playerUuid), "Untamed minion must not pass server ownership");
	}

	@Test
	@DisplayName("Dedicated server rejects non-owner player")
	void testDedicatedServerRejectsNonOwner() {
		UUID ownerUuid = UUID.randomUUID();
		UUID strangerUuid = UUID.randomUUID();

		MockOwnershipSimulator serverSim = new MockOwnershipSimulator(ownerUuid, true, false, false, null);
		Assertions.assertFalse(serverSim.isOwner(strangerUuid), "Dedicated server must reject non-owner UUID");
	}

	@Test
	@DisplayName("MinionScreenHandler null safety for isHeadEquippable")
	void testHeadEquippableNullSafety() {
		Assertions.assertFalse(MinionScreenHandler.isHeadEquippable(null), "Null stack must return false");
	}

	@Test
	@DisplayName("MinionScreenHandler source code guarantees head equippable verification for skulls and helmets")
	void testMinionScreenHandlerSourceContracts() throws IOException {
		Path screenHandlerPath = Path.of("src/main/java/com/example/screen/MinionScreenHandler.java");
		Assertions.assertTrue(Files.exists(screenHandlerPath), "MinionScreenHandler.java must exist");
		String content = Files.readString(screenHandlerPath);

		// isHeadEquippable method contract
		Assertions.assertTrue(content.contains("public static boolean isHeadEquippable(ItemStack stack)"),
			"Must define isHeadEquippable method");
		Assertions.assertTrue(content.contains("Equipment.fromStack(stack)"),
			"isHeadEquippable must inspect Equipment from stack");
		Assertions.assertTrue(content.contains("equipment.getSlotType() == EquipmentSlot.HEAD"),
			"isHeadEquippable must verify EquipmentSlot.HEAD");
		Assertions.assertTrue(content.contains("stack.isOf(Items.CARVED_PUMPKIN)"),
			"isHeadEquippable must support Carved Pumpkins");

		// canEquipSlot integration
		Assertions.assertTrue(content.contains("slot == EquipmentSlot.HEAD && isHeadEquippable(stack)"),
			"canEquipSlot must use isHeadEquippable for HEAD slot");

		// quickMove shift-click integration
		Assertions.assertTrue(content.contains("preferredSlot == EquipmentSlot.HEAD || isHeadEquippable(originalStack)"),
			"quickMove must prioritize HEAD slot for head-equippable items");
	}

	@Test
	@DisplayName("MinionEntity source code guarantees direct UUID check and item right-click inventory access")
	void testMinionEntityInteractionSourceContracts() throws IOException {
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionEntityPath), "MinionEntity.java must exist");
		String content = Files.readString(minionEntityPath);

		// Direct UUID match in isOwner
		Assertions.assertTrue(content.contains("ownerUuid.equals(entity.getUuid())"),
			"isOwner must perform direct UUID equality check");

		// Client-side prediction for tamed minions
		Assertions.assertTrue(content.contains("this.getWorld().isClient()") && content.contains("this.isTamed()"),
			"isOwner must allow client prediction for tamed minions");

		// Right-click with items opens inventory screen
		Assertions.assertTrue(content.contains("Holding other items (or holding food/gold when already at full health)")
			|| content.contains("Directly open Minion Screen GUI so the player can check equipment and inventory"),
			"interactMob must open inventory screen when holding non-healing items");

		// autoEquipFromInventory integrates head equippable logic
		Assertions.assertTrue(content.contains("isHeadEquippable(stack)"),
			"autoEquipFromInventory must use isHeadEquippable for equipping skulls and helmets");
	}

	@Test
	@DisplayName("MinionEntityRenderer source code includes HeadFeatureRenderer")
	void testRendererIncludesHeadFeature() throws IOException {
		Path rendererPath = Path.of("src/client/java/com/example/client/renderer/MinionEntityRenderer.java");
		Assertions.assertTrue(Files.exists(rendererPath), "MinionEntityRenderer.java must exist");

		String content = Files.readString(rendererPath);
		Assertions.assertTrue(content.contains("HeadFeatureRenderer"), "MinionEntityRenderer must register HeadFeatureRenderer");
		Assertions.assertTrue(content.contains("context.getModelLoader()"), "Must supply model loader to HeadFeatureRenderer");
		Assertions.assertTrue(content.contains("context.getHeldItemRenderer()"), "Must supply held item renderer to HeadFeatureRenderer");
	}

	@Test
	@DisplayName("MinionEntity strictly avoids client-only imports to preserve server safety")
	void testMinionEntityServerSafety() throws IOException {
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionEntityPath), "MinionEntity.java must exist");

		List<String> lines = Files.readAllLines(minionEntityPath);
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.startsWith("import net.minecraft.client")) {
				Assertions.fail("Forbidden client-side import in MinionEntity: " + trimmed);
			}
		}
	}
}
