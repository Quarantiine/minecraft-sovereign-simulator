package com.example.targeting;

import com.example.network.SyncTargetFilterPayload;
import com.example.network.UpdateTargetFilterPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating the minion target filter manager semantics and its network payload contracts.
 */
public class MinionTargetFilterManagerTest {

	private static final Identifier ZOMBIE = Identifier.ofVanilla("zombie");
	private static final Identifier CREEPER = Identifier.ofVanilla("creeper");
	private static final Identifier SLIME = Identifier.ofVanilla("slime");

	@Test
	@DisplayName("Owners without a saved filter fall back to the legacy defaults")
	void testDefaultsPreserveLegacyBehavior() {
		MinionTargetFilterManager manager = MinionTargetFilterManager.getInstance();
		UUID owner = UUID.randomUUID();

		Assertions.assertTrue(manager.isAllowed(owner, ZOMBIE));
		Assertions.assertFalse(manager.isAllowed(owner, SLIME), "Slimes were never engaged by legacy HostileEntity-only AI");
		Assertions.assertTrue(manager.isAllowed(null, CREEPER));
		Assertions.assertFalse(manager.isAllowed(null, SLIME));
		Assertions.assertFalse(manager.isAllowed(owner, (Identifier) null));
		Assertions.assertEquals(MinionTargetFilterManager.DEFAULT_DISABLED, manager.getDisabled(owner));
	}

	@Test
	@DisplayName("Saved filter replaces defaults per owner and is isolated between owners")
	void testSetDisabledIsPerOwner() {
		MinionTargetFilterManager manager = MinionTargetFilterManager.getInstance();
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();

		manager.setDisabled(alice, List.of(CREEPER));

		Assertions.assertFalse(manager.isAllowed(alice, CREEPER));
		Assertions.assertTrue(manager.isAllowed(alice, SLIME), "Saved filter overrides the default-disabled set");
		Assertions.assertTrue(manager.isAllowed(bob, CREEPER));
		Assertions.assertEquals(Set.of(CREEPER), manager.getDisabled(alice));

		manager.setDisabled(alice, List.of());
		Assertions.assertTrue(manager.isAllowed(alice, CREEPER));
		Assertions.assertTrue(manager.getDisabled(alice).isEmpty());
	}

	@Test
	@DisplayName("Target filter payloads expose IDs and survive a packet codec roundtrip")
	void testPayloadRoundtrip() {
		List<Identifier> ids = List.of(CREEPER, SLIME);
		UpdateTargetFilterPayload update = new UpdateTargetFilterPayload(ids);
		SyncTargetFilterPayload sync = new SyncTargetFilterPayload(ids);

		Assertions.assertEquals("update_target_filter", update.getId().id().getPath());
		Assertions.assertEquals("sync_target_filter", sync.getId().id().getPath());

		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			UpdateTargetFilterPayload.PACKET_CODEC.encode(buf, update);
			Assertions.assertEquals(update, UpdateTargetFilterPayload.PACKET_CODEC.decode(buf));
			SyncTargetFilterPayload.PACKET_CODEC.encode(buf, sync);
			Assertions.assertEquals(sync, SyncTargetFilterPayload.PACKET_CODEC.decode(buf));
			Assertions.assertEquals(0, buf.readableBytes());
		} finally {
			buf.release();
		}
	}
}
