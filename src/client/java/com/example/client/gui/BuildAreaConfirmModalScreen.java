package com.example.client.gui;

import com.example.client.network.ModClientNetworking;
import com.example.client.renderer.ClientBuildCaptureTracker;
import com.example.item.custom.CommandScepterItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Client modal GUI screen prompting the commander to inspect, adjust height,
 * select the volumetric fill block, or confirm/reset the 3D spatial build area
 * before initiating minion construction.
 *
 * Designed with perfect symmetry to {@link MiningConfirmModalScreen}.
 */
public class BuildAreaConfirmModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 300;
	public static final int WINDOW_HEIGHT = 196;

	private BlockPos pos1;
	private BlockPos pos2;
	private final ItemStack scepterStack;
	private final Screen parentScreen;

	private Identifier selectedBlockId = Identifier.of("minecraft", "stone_bricks");

	private ButtonWidget startButton;
	private ButtonWidget changeBlockButton;
	private ButtonWidget resetButton;
	private ButtonWidget cancelButton;

	private boolean closed = false;

	public BuildAreaConfirmModalScreen(BlockPos pos1, BlockPos pos2, ItemStack scepterStack, Screen parentScreen) {
		super(Text.translatable("gui.modid-mmcli-agent-modding.build_area_confirm_modal.title"));
		this.pos1 = pos1 != null ? pos1.toImmutable() : ClientBuildCaptureTracker.getPos1();
		this.pos2 = pos2 != null ? pos2.toImmutable() : ClientBuildCaptureTracker.getPos2();
		this.scepterStack = scepterStack;
		this.parentScreen = parentScreen;
	}

	public BuildAreaConfirmModalScreen(ItemStack scepterStack, Screen parentScreen) {
		this(ClientBuildCaptureTracker.getPos1(), ClientBuildCaptureTracker.getPos2(), scepterStack, parentScreen);
	}

	public BuildAreaConfirmModalScreen() {
		this(null, null, null, null);
	}

	public BlockPos getPos1() {
		return this.pos1;
	}

	public BlockPos getPos2() {
		return this.pos2;
	}

	public Identifier getSelectedBlockId() {
		return this.selectedBlockId;
	}

	public void setSelectedBlockId(Identifier id) {
		if (id != null) {
			this.selectedBlockId = id;
			updateStartButtonState();
		}
	}

	public boolean hasValidGeometry() {
		if (this.pos1 == null || this.pos2 == null) {
			return false;
		}
		int sx = Math.abs(this.pos1.getX() - this.pos2.getX()) + 1;
		int sy = Math.abs(this.pos1.getY() - this.pos2.getY()) + 1;
		int sz = Math.abs(this.pos1.getZ() - this.pos2.getZ()) + 1;
		long vol = (long) sx * sy * sz;
		return sx <= ClientBuildCaptureTracker.MAX_DIMENSION
			&& sy <= ClientBuildCaptureTracker.MAX_HEIGHT
			&& sz <= ClientBuildCaptureTracker.MAX_DIMENSION
			&& vol <= ClientBuildCaptureTracker.MAX_VOLUME;
	}

	public int getDimensionX() {
		if (this.pos1 == null || this.pos2 == null) return 0;
		return Math.abs(this.pos1.getX() - this.pos2.getX()) + 1;
	}

	public int getDimensionY() {
		if (this.pos1 == null || this.pos2 == null) return 0;
		return Math.abs(this.pos1.getY() - this.pos2.getY()) + 1;
	}

	public int getDimensionZ() {
		if (this.pos1 == null || this.pos2 == null) return 0;
		return Math.abs(this.pos1.getZ() - this.pos2.getZ()) + 1;
	}

	public long getVolume() {
		if (this.pos1 == null || this.pos2 == null) return 0L;
		return (long) getDimensionX() * getDimensionY() * getDimensionZ();
	}

	@Override
	protected void init() {
		super.init();

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// 1. Height adjustment buttons in dedicated row below spatial info
		if (this.pos1 != null && this.pos2 != null) {
			int hBtnY = startY + 58;
			ButtonWidget minus5 = ButtonWidget.builder(Text.literal("§c-5"), b -> adjustModalHeight(-5))
				.dimensions(startX + 104, hBtnY, 24, 18)
				.tooltip(Tooltip.of(Text.literal("Decrease height by 5 blocks")))
				.build();
			ButtonWidget minus1 = ButtonWidget.builder(Text.literal("§c-1"), b -> adjustModalHeight(-1))
				.dimensions(startX + 132, hBtnY, 20, 18)
				.tooltip(Tooltip.of(Text.literal("Decrease height by 1 block")))
				.build();
			ButtonWidget plus1 = ButtonWidget.builder(Text.literal("§a+1"), b -> adjustModalHeight(1))
				.dimensions(startX + 156, hBtnY, 20, 18)
				.tooltip(Tooltip.of(Text.literal("Increase height by 1 block")))
				.build();
			ButtonWidget plus5 = ButtonWidget.builder(Text.literal("§a+5"), b -> adjustModalHeight(5))
				.dimensions(startX + 180, hBtnY, 24, 18)
				.tooltip(Tooltip.of(Text.literal("Increase height by 5 blocks")))
				.build();

			this.addDrawableChild(minus5);
			this.addDrawableChild(minus1);
			this.addDrawableChild(plus1);
			this.addDrawableChild(plus5);
		}

		// 2. Change Block button
		int blockRowY = startY + 82;
		this.changeBlockButton = ButtonWidget.builder(Text.literal("§b🔍 Change"), b -> openBlockPicker())
			.dimensions(startX + WINDOW_WIDTH - 96, blockRowY, 80, 20)
			.tooltip(Tooltip.of(Text.literal("Pick the placeable block type used to fill this 3D volume")))
			.build();
		this.addDrawableChild(this.changeBlockButton);

		// 3. Action buttons at bottom tray
		int bottomY = startY + WINDOW_HEIGHT - 30;
		int btnWidth = 84;

		this.startButton = ButtonWidget.builder(Text.literal("§a✔ Fill Area"), b -> startBuildArea())
			.dimensions(startX + 16, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§aStart Bottom-to-Top Volumetric Construction")))
			.build();
		this.addDrawableChild(this.startButton);

		this.resetButton = ButtonWidget.builder(Text.literal("§6⌫ Reset"), b -> clearSelection())
			.dimensions(startX + 108, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§6Clear Pos1 and Pos2 corner selections")))
			.build();
		this.addDrawableChild(this.resetButton);

		this.cancelButton = ButtonWidget.builder(Text.literal("§c✖ Cancel"), b -> this.close())
			.dimensions(startX + 200, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§cCancel and return")))
			.build();
		this.addDrawableChild(this.cancelButton);

		updateStartButtonState();
	}

	private void openBlockPicker() {
		if (this.client != null) {
			this.client.setScreen(new BlockPickerModalScreen(this, newId -> {
				this.selectedBlockId = newId;
				updateStartButtonState();
			}));
		}
	}

	private void adjustModalHeight(int delta) {
		if (this.pos1 == null || this.pos2 == null) return;
		ClientBuildCaptureTracker.adjustHeight(delta);
		this.pos1 = ClientBuildCaptureTracker.getPos1();
		this.pos2 = ClientBuildCaptureTracker.getPos2();
		updateStartButtonState();
	}

	private void updateStartButtonState() {
		if (this.startButton != null) {
			this.startButton.active = hasValidGeometry() && this.selectedBlockId != null;
		}
	}

	public void clearSelection() {
		ClientBuildCaptureTracker.clear();
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.clearBuildCorners(this.scepterStack);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.clearBuildCorners(held);
			}
			this.client.player.sendMessage(Text.literal("§6✦ Cleared BUILD corner selections (Pos1 & Pos2) - Ready for Pos1!§r"), true);
		}
		this.close();
	}

	public void startBuildArea() {
		if (!hasValidGeometry() || this.pos1 == null || this.pos2 == null || this.selectedBlockId == null) {
			return;
		}

		BlockPos p1 = this.pos1;
		BlockPos p2 = this.pos2;
		Identifier blockId = this.selectedBlockId;

		// Dispatch C2S StartBuildAreaPayload
		ModClientNetworking.sendStartBuildArea(p1, p2, blockId);

		// Audio and chat feedback
		if (this.client != null && this.client.world != null && this.client.player != null) {
			Item blockItem = Registries.ITEM.get(blockId);
			String blockName = blockItem != null ? blockItem.getName().getString() : blockId.toString();

			this.client.world.playSound(
				null,
				this.client.player.getX(),
				this.client.player.getY(),
				this.client.player.getZ(),
				SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
				SoundCategory.PLAYERS,
				1.0F,
				1.2F
			);
			this.client.player.sendMessage(
				Text.literal("§3✦ Initiating Area Fill with §f" + blockName + "§3: §e" + getDimensionX() + "x" + getDimensionY() + "x" + getDimensionZ() + " §8(§f" + getVolume() + " blocks§8)§r"),
				true
			);
		}

		// Clear local tracker and scepter corners, then close
		ClientBuildCaptureTracker.clear();
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.clearBuildCorners(this.scepterStack);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.clearBuildCorners(held);
			}
		}
		this.close();
	}

	@Override
	protected void applyBlur(float delta) {
		// Disable background world blur post-processing shader while keeping screen darkening and UI elements crisp
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// Modal background plate & cyan/aqua border
		context.fill(startX, startY, startX + WINDOW_WIDTH, startY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(startX, startY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFF00B4D8);

		// Framed Header
		context.fill(startX + 1, startY + 1, startX + WINDOW_WIDTH - 1, startY + 24, 0xDD102A38);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§3✦ CONFIRM AREA FILL ✦"), this.width / 2, startY + 7, 0xFFFFFF);

		Text escText = Text.literal("§e[Esc] §7Close");
		context.drawTextWithShadow(this.textRenderer, escText, startX + WINDOW_WIDTH - this.textRenderer.getWidth(escText) - 10, startY + 7, 0xE0E0E0);

		// Spatial Dimension & Volume info bar
		if (this.pos1 != null && this.pos2 != null) {
			String p1Str = "P1: [" + this.pos1.getX() + ", " + this.pos1.getY() + ", " + this.pos1.getZ() + "]";
			String p2Str = "P2: [" + this.pos2.getX() + ", " + this.pos2.getY() + ", " + this.pos2.getZ() + "]";
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7" + p1Str + "  " + p2Str), startX + 16, startY + 30, 0xAAAAAA);

			boolean valid = hasValidGeometry();
			long volume = getVolume();
			String geomText;
			if (!valid) {
				geomText = "§c⚠ Exceeds volume limits (max 64x96x64, 393216b)";
			} else {
				geomText = "§3Size: §f" + getDimensionX() + "x" + getDimensionY() + "x" + getDimensionZ() + " §8| §e" + volume + " blocks";
			}
			context.drawTextWithShadow(this.textRenderer, Text.literal(geomText), startX + 16, startY + 43, valid ? 0x00D4FF : 0xFF5555);

			// Height adjustment label in dedicated row
			context.drawTextWithShadow(this.textRenderer, Text.literal("§eAdjust Height:"), startX + 16, startY + 62, 0xFFD700);

			// Fill Material Section
			int matY = startY + 84;
			context.drawTextWithShadow(this.textRenderer, Text.literal("§eFill Block:"), startX + 16, matY + 4, 0xFFD700);

			// Draw block icon slot
			int slotX = startX + 80;
			int slotY = matY - 2;
			context.fill(slotX - 1, slotY - 1, slotX + 19, slotY + 19, 0xFF222B38);
			context.drawBorder(slotX - 1, slotY - 1, 20, 20, 0xFF00B4D8);

			Item blockItem = Registries.ITEM.get(this.selectedBlockId);
			if (blockItem != null) {
				ItemStack iconStack = new ItemStack(blockItem);
				context.drawItem(iconStack, slotX + 1, slotY + 1);
				String blockName = iconStack.getName().getString();
				if (blockName.length() > 14) {
					blockName = blockName.substring(0, 13) + "…";
				}
				context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + blockName), slotX + 24, matY + 4, 0xFFFFFF);
			}

			// Instruction / Description lines
			context.drawTextWithShadow(this.textRenderer, Text.literal("§eMethod: §fBottom-to-Top Layer Construction"), startX + 16, startY + 114, 0x00E5FF);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Minions will quarry, craft, and place blocks."), startX + 16, startY + 128, 0xAAAAAA);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§8Identical pre-existing blocks are preserved."), startX + 16, startY + 142, 0x888888);
		} else {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§c⚠ Incomplete corner selection (Set Pos1 & Pos2)"), startX + 16, startY + 34, 0xFF5555);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Left-click blocks in AREA mode to set Pos1 and Pos2."), startX + 16, startY + 54, 0xAAAAAA);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if (this.startButton != null && this.startButton.active) {
				startBuildArea();
				return true;
			}
		}

		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			this.close();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		this.closed = true;
		if (this.client != null) {
			if (this.parentScreen != null) {
				this.client.setScreen(this.parentScreen);
			} else {
				super.close();
			}
		}
	}

	public boolean isClosed() {
		return this.closed;
	}

	public ButtonWidget getStartButton() {
		return this.startButton;
	}

	public ButtonWidget getChangeBlockButton() {
		return this.changeBlockButton;
	}

	public ButtonWidget getResetButton() {
		return this.resetButton;
	}

	public ButtonWidget getCancelButton() {
		return this.cancelButton;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
