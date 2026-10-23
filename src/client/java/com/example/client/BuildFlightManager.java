package com.example.client;

import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.component.CommandMode;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Manages Arcane Build Flight (Option A) with locked hover elevation.
 * <p>
 * When holding the Command Scepter in BUILD mode, the player automatically
 * enters physical flight in both Creative and Survival, smoothly elevating to and
 * locking at a height calculated from the blueprint:
 * {@code Target Hover Y = Ground Y + max(6.0, Blueprint Height + 3.0)}
 * (clamped safely below overhead ceilings).
 * <p>
 * Horizontal movement (WASD) operates with full responsiveness while vertical
 * position is locked. Upon exiting BUILD mode or stowing the scepter, flight is
 * safely revoked with gentle descent and zero fall damage.
 */
public class BuildFlightManager {

	private static boolean flightActive = false;
	private static double lockedHoverY = 0.0D;
	private static double initialGroundY = 0.0D;
	private static String lastBlueprintId = "";
	private static int lastSize = -1;
	private static boolean descending = false;
	private static int statusMessageCooldown = 0;

	public static boolean isFlightActive() {
		return flightActive;
	}

	public static double getLockedHoverY() {
		return lockedHoverY;
	}

	/**
	 * Client tick handler invoked at {@code ClientTickEvents.END_CLIENT_TICK}.
	 */
	public static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			flightActive = false;
			descending = false;
			return;
		}

		ClientPlayerEntity player = client.player;
		World world = client.world;

		ItemStack heldStack = ItemStack.EMPTY;
		if (player.getMainHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			heldStack = player.getMainHandStack();
		} else if (player.getOffHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			heldStack = player.getOffHandStack();
		}

		boolean shouldFly = !heldStack.isEmpty() && (CommandScepterItem.getMode(heldStack) == CommandMode.BUILD || CommandScepterItem.getMode(heldStack) == CommandMode.MINE || CommandScepterItem.getMode(heldStack) == CommandMode.DESIGN);

		boolean overWater = CommandScepterItem.isPlayerOverWater(world, player);
		if (overWater && shouldFly) {
			flightActive = false;
			descending = false;
			if (!player.isCreative() && !player.isSpectator()) {
				player.getAbilities().flying = false;
				player.getAbilities().allowFlying = false;
			}
			CommandScepterItem.setMode(heldStack, CommandMode.FOLLOW);
			player.sendMessage(Text.literal("§c⚠ Flight cancelled: Flying over deep water is prohibited!§r"), true);
			return;
		}

		if (shouldFly) {
			boolean isMineMode = CommandScepterItem.getMode(heldStack) == CommandMode.MINE;
			boolean isDesignMode = CommandScepterItem.getMode(heldStack) == CommandMode.DESIGN;
			String bpId = (isMineMode || isDesignMode) ? "" : CommandScepterItem.getBlueprintId(heldStack);

			if (!flightActive) {
				initialGroundY = findGroundBelow(world, player.getBlockPos());
				double targetY;
				if (isMineMode) {
					targetY = initialGroundY + 12.0D;
				} else if (isDesignMode) {
					targetY = initialGroundY + 10.0D;
				} else {
					int bpHeight = resolveBlueprintHeight(bpId);
					targetY = initialGroundY + Math.max(6.0D, bpHeight + 3.0D);
				}
				double ceilingClamp = checkCeilingClearance(world, player, player.getEyePos(), targetY);
				lockedHoverY = Math.min(targetY, ceilingClamp);

				flightActive = true;
				descending = false;
				lastBlueprintId = bpId;

				player.getAbilities().allowFlying = true;
				player.getAbilities().flying = true;

				Text welcomeMsg = isMineMode
					? Text.literal("§6✦ Quarry Aerial Surveyor §8| §fFree Flight §8| §7[Space/Shift] Alt")
					: (isDesignMode
						? Text.literal("§d✦ Architect Aerial Surveyor §8| §fFree Flight §8| §7[Space/Shift] Alt §8| §7[Pos1/Pos2] Capture")
						: Text.literal("§b✦ Arcane Build Flight §8| §fFree Flight §8| §7[Space/Shift] Alt §8| §7[R/L-Click] Rotate"));
				player.sendMessage(welcomeMsg, true);
				statusMessageCooldown = 40;
			} else {
				if (!bpId.equalsIgnoreCase(lastBlueprintId)) {
					lastBlueprintId = bpId;
				}

				// Enforce flight abilities and zero fall distance for free survival flight
				player.getAbilities().allowFlying = true;
				player.getAbilities().flying = true;
				player.fallDistance = 0.0F;

				// Native flight altitude adjustment with Space and Shift keys
				net.minecraft.client.option.GameOptions options = client.options;
				if (options.jumpKey.isPressed()) {
					lockedHoverY = player.getY();
				} else if (options.sneakKey.isPressed()) {
					lockedHoverY = player.getY();
				}

				// Periodic Action Bar status feedback
				statusMessageCooldown--;
				if (statusMessageCooldown <= 0) {
					statusMessageCooldown = 50;
					Text statusMsg = isMineMode
						? Text.literal("§6✦ Quarry Aerial Surveyor §8| §fFree Flight §8| §7[Space/Shift] Alt")
						: (isDesignMode
							? Text.literal("§d✦ Architect Aerial Surveyor §8| §fFree Flight §8| §7[Space/Shift] Alt §8| §7[Pos1/Pos2] Capture")
							: Text.literal("§b✦ Arcane Build Flight §8| §fFree Flight §8| §7[Space/Shift] Alt §8| §7[R] Rotate"));
					player.sendMessage(statusMsg, true);
				}
			}
		} else {
			// Scepter stowed, mode changed, or item switched
			if (flightActive) {
				flightActive = false;
				descending = true;

				if (!player.isCreative() && !player.isSpectator()) {
					player.getAbilities().flying = false;
					player.getAbilities().allowFlying = false;
				}

				player.sendMessage(Text.literal("§a✦ Safe Descent Engaged"), true);
			}

			// Safe descent glide with zero fall damage until touching ground
			if (descending) {
				player.fallDistance = 0.0F;
				if (player.isOnGround()) {
					descending = false;
				} else {
					// Gentle descent glide
					Vec3d vel = player.getVelocity();
					if (vel.y < -0.22D) {
						player.setVelocity(vel.x, -0.22D, vel.z);
						player.velocityModified = true;
					}
				}
			}
		}
	}

	/**
	 * Scans down from player feet to determine the highest solid terrain ground level.
	 */
	public static double findGroundBelow(World world, BlockPos startPos) {
		BlockPos.Mutable mut = startPos.mutableCopy();
		int bottomY = world.getBottomY();
		while (mut.getY() > bottomY) {
			BlockState state = world.getBlockState(mut);
			if (state.isSolidBlock(world, mut) || state.isLiquid()) {
				return mut.getY() + 1.0D;
			}
			mut.move(0, -1, 0);
		}
		return startPos.getY();
	}

	/**
	 * Raycasts vertically up to detect overhead cavern or indoor ceilings.
	 * Returns the maximum safe Y altitude that remains at least 1.5 blocks below any ceiling.
	 */
	public static double checkCeilingClearance(World world, Vec3d eyePos, double targetY) {
		return checkCeilingClearance(world, null, eyePos, targetY);
	}

	/**
	 * Raycasts vertically up to detect overhead cavern or indoor ceilings relative to the player.
	 * Returns the maximum safe Y altitude that remains at least 1.5 blocks below any ceiling.
	 */
	public static double checkCeilingClearance(World world, ClientPlayerEntity player, Vec3d eyePos, double targetY) {
		double maxTestY = targetY + 2.0D;
		Vec3d endPos = new Vec3d(eyePos.x, maxTestY, eyePos.z);
		net.minecraft.block.ShapeContext shapeContext = player != null ? net.minecraft.block.ShapeContext.of(player) : net.minecraft.block.ShapeContext.absent();
		BlockHitResult hit = world.raycast(new RaycastContext(
			eyePos,
			endPos,
			RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.NONE,
			shapeContext
		));

		if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
			return Math.max(eyePos.y + 1.0D, hit.getPos().y - 1.5D);
		}
		return targetY;
	}

	/**
	 * Resolves vertical height for the specified blueprint ID.
	 */
	public static int resolveBlueprintHeight(String blueprintId) {
		if (blueprintId == null || blueprintId.isBlank()) {
			return 8;
		}

		StructureBlueprint bp = BlueprintRegistry.get(blueprintId).orElse(null);
		return bp != null ? Math.max(1, bp.getSizeY()) : 8;
	}

	public static int resolveBlueprintHeight(String blueprintId, int size) {
		return resolveBlueprintHeight(blueprintId);
	}
}
