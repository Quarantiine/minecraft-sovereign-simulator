package com.example.client.renderer;

import com.example.barrier.BarrierZone;
import com.example.component.BuildingMode;
import com.example.component.CommandMode;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

/**
 * Client-side 3D holographic wireframe and forcefield renderer for Sovereign Barriers (Barrier Mode).
 * Features:
 * - Ping-pong open chain polyline drafting with dynamic crosshair ghost tether.
 * - Snap-to-close loop detection on Point #1 with vibrant emerald highlight.
 * - Dynamic High-in-the-Sky territory crest billboard (+64 blocks above ground, dynamically higher if build > 50b).
 * - Corner waypoint index badges matching the uniform gold aesthetic.
 */
public class BarrierHologramRenderer {

	private static final float GOLD_R = 1.0F;
	private static final float GOLD_G = 0.75F;
	private static final float GOLD_B = 0.15F;

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(BarrierHologramRenderer::render);
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || client.player == null) {
			return;
		}

		MatrixStack matrices = context.matrixStack();
		Camera camera = context.camera();
		if (matrices == null || camera == null) {
			return;
		}

		ClientPlayerEntity player = client.player;
		ItemStack scepterStack = null;
		if (player.getMainHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			scepterStack = player.getMainHandStack();
		} else if (player.getOffHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			scepterStack = player.getOffHandStack();
		}

		if (scepterStack == null) {
			return;
		}

		CommandMode mode = CommandScepterItem.getMode(scepterStack);
		BuildingMode bMode = CommandScepterItem.getBuildingMode(scepterStack);
		boolean isBarrierMode = (mode == CommandMode.BUILD && bMode == BuildingMode.BARRIER);

		Vec3d cameraPos = camera.getPos();
		VertexConsumerProvider consumers = context.consumers();
		if (consumers == null) {
			consumers = client.getBufferBuilders().getEntityVertexConsumers();
		}

		matrices.push();
		matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		VertexConsumer lineBuffer = consumers.getBuffer(RenderLayer.getLines());
		MatrixStack.Entry entry = matrices.peek();
		TextRenderer textRenderer = client.textRenderer;

		float pulse = MathHelper.sin(player.age * 0.12F) * 0.15F;
		float alpha = MathHelper.clamp(0.85F + pulse, 0.50F, 1.0F);

		// -------------------------------------------------------------
		// PASS 1: Wireframe and Line Geometry (RenderLayer.getLines)
		// -------------------------------------------------------------
		List<BarrierZone> confirmed = ClientBarrierTracker.getConfirmedZones();
		for (BarrierZone zone : confirmed) {
			if (zone.isSealed() && zone.getNodeCount() >= 2) {
				renderZoneWireframe(entry, lineBuffer, matrices, zone.getNodes(), zone.getMinY(), zone.getMaxY(), true, isBarrierMode ? 0.65F : 0.35F);
			}
		}

		List<BlockPos> pending = isBarrierMode ? ClientBarrierTracker.getPendingNodes() : java.util.Collections.emptyList();
		boolean snapToClose = false;
		Vec3d ghostTarget = null;
		BlockPos p0 = null;

		if (isBarrierMode && !pending.isEmpty()) {
			int minY = ClientBarrierTracker.getMinY();
			int maxY = ClientBarrierTracker.getMaxY();
			// Open chain drafting (closedLoop = false)
			renderZoneWireframe(entry, lineBuffer, matrices, pending, minY, maxY, false, alpha);

			// Render Crosshair Ghost Line Tether (Ping-Pong Ghost View)
			BlockPos lastPos = pending.get(pending.size() - 1);
			HitResult crossHit = client.crosshairTarget;

			if (crossHit instanceof BlockHitResult bHit && bHit.getType() == HitResult.Type.BLOCK) {
				BlockPos clicked = bHit.getBlockPos();
				BlockPos target = (client.world != null && client.world.getBlockState(clicked).isReplaceable())
					? clicked
					: (bHit.getSide() != null ? clicked.offset(bHit.getSide()) : clicked.up());
				ghostTarget = new Vec3d(target.getX() + 0.5D, target.getY() + 0.05D, target.getZ() + 0.5D);

				BlockPos closeTarget = ClientBarrierTracker.findCloseTarget(target, clicked);
				if (closeTarget != null) {
					snapToClose = true;
					p0 = closeTarget;
					ghostTarget = new Vec3d(p0.getX() + 0.5D, p0.getY() + 0.05D, p0.getZ() + 0.5D);
				}
			} else if (player != null) {
				Vec3d eye = player.getEyePos();
				Vec3d rot = player.getRotationVec(1.0F);
				ghostTarget = eye.add(rot.multiply(6.0D));
			}

			if (ghostTarget != null) {
				Vec3d start = new Vec3d(lastPos.getX() + 0.5D, lastPos.getY() + 0.05D, lastPos.getZ() + 0.5D);
				if (snapToClose && p0 != null) {
					// Vibrant emerald pulse when snapping onto Point 1 to seal
					float snapAlpha = MathHelper.clamp(0.85F + MathHelper.sin(player.age * 0.25F) * 0.2F, 0.4F, 1.0F);
					drawVectorLine(entry, lineBuffer, start, ghostTarget, 0.2F, 1.0F, 0.4F, snapAlpha);

					// Snap indicator beacon on Point 1
					Box snapBox = new Box(p0).expand(0.08D);
					WorldRenderer.drawBox(matrices, lineBuffer, snapBox, 0.2F, 1.0F, 0.4F, snapAlpha);
				} else {
					// Soft gold ghost line showing next wall segment
					drawVectorLine(entry, lineBuffer, start, ghostTarget, GOLD_R, GOLD_G, GOLD_B, 0.55F + pulse * 0.2F);
				}
			}
		}

		BlockPos hoveredConfirmed = null;
		if (isBarrierMode && pending.isEmpty() && client.crosshairTarget instanceof BlockHitResult bHit && bHit.getType() == HitResult.Type.BLOCK) {
			BlockPos clicked = bHit.getBlockPos();
			BlockPos target = (client.world != null && client.world.getBlockState(clicked).isReplaceable())
				? clicked
				: (bHit.getSide() != null ? clicked.offset(bHit.getSide()) : clicked.up());
			hoveredConfirmed = ClientBarrierTracker.findConfirmedNodeAt(target, clicked);
			if (hoveredConfirmed != null) {
				Box hoverBox = new Box(hoveredConfirmed).expand(0.08D);
				WorldRenderer.drawBox(matrices, lineBuffer, hoverBox, 1.0F, 0.65F, 0.0F, 0.85F);
			}
		}

		// Flush line rendering before text rendering starts
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLines());
		}

		// -------------------------------------------------------------
		// PASS 2: Floating Billboards and Holographic Text (TextRenderer)
		// -------------------------------------------------------------
		// 1. Confirmed sealed barrier labels & crests
		for (BarrierZone zone : confirmed) {
			if (zone.isSealed() && zone.getNodeCount() >= 2) {
				List<BlockPos> zNodes = zone.getNodes();
				for (int i = 0; i < zNodes.size(); i++) {
					BlockPos pos = zNodes.get(i);
					String tag = "§6[ " + zone.getName() + " #" + (i + 1) + " ]";
					renderBillboardTag(matrices, camera, consumers, textRenderer, tag, pos.getX() + 0.5D, pos.getY() + 1.4D, pos.getZ() + 0.5D);
				}
			}
		}

		if (hoveredConfirmed != null && isBarrierMode) {
			renderBillboardTag(matrices, camera, consumers, textRenderer, "§6✦ [ Left-Click: Extend | Right-Click: Remove ] ✦", hoveredConfirmed.getX() + 0.5D, hoveredConfirmed.getY() + 1.85D, hoveredConfirmed.getZ() + 0.5D);
		}

		// 2. Active pending barrier node badges
		if (isBarrierMode && !pending.isEmpty()) {
			for (int i = 0; i < pending.size(); i++) {
				BlockPos pos = pending.get(i);
				String tag = (i == 0) ? "§6[ Point 1 • Start ]" : ("§6[ Point " + (i + 1) + " ]");
				renderBillboardTag(matrices, camera, consumers, textRenderer, tag, pos.getX() + 0.5D, pos.getY() + 1.4D, pos.getZ() + 0.5D);
			}

			if (snapToClose && p0 != null) {
				renderBillboardTag(matrices, camera, consumers, textRenderer, "§a✦ [ Left-Click to Close & Seal ] ✦", p0.getX() + 0.5D, p0.getY() + 1.85D, p0.getZ() + 0.5D);
			}
		}

		matrices.pop();

		// CRITICAL: Flush all vertex consumers so text and geometry render to screen
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
	}

	private static void renderZoneWireframe(
		MatrixStack.Entry entry,
		VertexConsumer buffer,
		MatrixStack matrices,
		List<BlockPos> nodes,
		int minY,
		int maxY,
		boolean closedLoop,
		float alpha
	) {
		int count = nodes.size();
		if (count == 0) return;

		// 1. Node bounding cubes on ground
		for (BlockPos pos : nodes) {
			Box nodeBox = new Box(pos);
			WorldRenderer.drawBox(matrices, buffer, nodeBox, GOLD_R, GOLD_G, GOLD_B, alpha);

			// Inner core
			Box core = nodeBox.shrink(0.15D, 0.15D, 0.15D);
			WorldRenderer.drawBox(matrices, buffer, core, GOLD_R, GOLD_G, GOLD_B, alpha * 0.6F);

			// Vertical laser column through the node from minY to maxY
			Vec3d bottom = new Vec3d(pos.getX() + 0.5D, minY, pos.getZ() + 0.5D);
			Vec3d top = new Vec3d(pos.getX() + 0.5D, maxY + 1.0D, pos.getZ() + 0.5D);
			drawVectorLine(entry, buffer, bottom, top, GOLD_R, GOLD_G, GOLD_B, alpha * 0.7F);
		}

		// 2. Connecting laser vector perimeter segments
		if (count >= 2) {
			int segments = closedLoop ? count : (count - 1);
			for (int i = 0; i < segments; i++) {
				BlockPos p1 = nodes.get(i);
				BlockPos p2 = nodes.get((i + 1) % count);

				// Ground line
				Vec3d g1 = new Vec3d(p1.getX() + 0.5D, p1.getY() + 0.05D, p1.getZ() + 0.5D);
				Vec3d g2 = new Vec3d(p2.getX() + 0.5D, p2.getY() + 0.05D, p2.getZ() + 0.5D);
				drawVectorLine(entry, buffer, g1, g2, GOLD_R, GOLD_G, GOLD_B, alpha);

				// Ceiling line at maxY
				Vec3d c1 = new Vec3d(p1.getX() + 0.5D, maxY + 1.0D, p1.getZ() + 0.5D);
				Vec3d c2 = new Vec3d(p2.getX() + 0.5D, maxY + 1.0D, p2.getZ() + 0.5D);
				drawVectorLine(entry, buffer, c1, c2, GOLD_R, GOLD_G, GOLD_B, alpha * 0.8F);

				// Floor line at minY
				Vec3d f1 = new Vec3d(p1.getX() + 0.5D, minY, p1.getZ() + 0.5D);
				Vec3d f2 = new Vec3d(p2.getX() + 0.5D, minY, p2.getZ() + 0.5D);
				drawVectorLine(entry, buffer, f1, f2, GOLD_R, GOLD_G, GOLD_B, alpha * 0.8F);
			}
		}
	}

	private static void drawVectorLine(
		MatrixStack.Entry entry,
		VertexConsumer buffer,
		Vec3d start,
		Vec3d end,
		float r,
		float g,
		float b,
		float alpha
	) {
		float dx = (float) (end.x - start.x);
		float dy = (float) (end.y - start.y);
		float dz = (float) (end.z - start.z);
		float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
		float nx = len > 0.0001F ? dx / len : 0.0F;
		float ny = len > 0.0001F ? dy / len : 1.0F;
		float nz = len > 0.0001F ? dz / len : 0.0F;

		buffer.vertex(entry, (float) start.x, (float) start.y, (float) start.z)
			.color(r, g, b, alpha)
			.normal(entry, nx, ny, nz);
		buffer.vertex(entry, (float) end.x, (float) end.y, (float) end.z)
			.color(r, g, b, alpha)
			.normal(entry, nx, ny, nz);
	}

	private static void renderBillboardTag(
		MatrixStack matrices,
		Camera camera,
		VertexConsumerProvider consumers,
		TextRenderer textRenderer,
		String text,
		double x,
		double y,
		double z
	) {
		matrices.push();
		matrices.translate(x, y, z);
		matrices.multiply(camera.getRotation());
		matrices.scale(-0.025F, -0.025F, 0.025F);

		float textWidth = textRenderer.getWidth(text);
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		textRenderer.draw(
			Text.literal(text),
			-textWidth / 2.0F,
			0.0F,
			0xFFFFFF,
			true,
			matrix,
			consumers,
			TextRenderer.TextLayerType.SEE_THROUGH,
			0x90000000,
			15728880
		);

		matrices.pop();
	}
}
