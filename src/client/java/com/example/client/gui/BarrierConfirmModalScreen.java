package com.example.client.gui;

import com.example.client.renderer.ClientBarrierTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Client modal GUI screen prompting the commander to confirm, inspect, adjust height,
 * or cancel/remove the multi-node Sovereign Barrier perimeter before sealing.
 *
 * <p>Explains what the barrier is for:
 * Protects bases, villages, and structures from minion harvesting. Minions will never break blocks,
 * chop trees, mine stone, or scoop water inside this perimeter unless explicitly ordered.
 */
public class BarrierConfirmModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 340;
	public static final int WINDOW_HEIGHT = 160;

	private final ItemStack scepterStack;
	private final Screen parentScreen;

	private net.minecraft.client.gui.widget.TextFieldWidget nameField;
	private ButtonWidget sealButton;
	private ButtonWidget cancelButton;
	private ButtonWidget removeButton;

	private boolean closed = false;

	public BarrierConfirmModalScreen(ItemStack scepterStack, Screen parentScreen) {
		super(Text.literal("Confirm Sovereign Barrier"));
		this.scepterStack = scepterStack;
		this.parentScreen = parentScreen;
	}

	public BarrierConfirmModalScreen() {
		this(null, null);
	}

	@Override
	protected void applyBlur(float delta) {
		// Disable background world blur to preserve crisp tactical visibility
	}

	@Override
	protected void init() {
		super.init();

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// Name input field
		int nextNum = ClientBarrierTracker.getConfirmedZones().size() + 1;
		this.nameField = new net.minecraft.client.gui.widget.TextFieldWidget(
			this.textRenderer,
			startX + 60,
			startY + 44,
			264,
			18,
			Text.literal("Barrier Name")
		);
		this.nameField.setMaxLength(32);
		this.nameField.setText("Sovereign Barrier #" + nextNum);
		this.addSelectableChild(this.nameField);

		// Action buttons at bottom tray: [✦ Seal] [✕ Cancel] [⌫ Remove]
		int bottomY = startY + WINDOW_HEIGHT - 28;
		int btnWidth = 96;

		this.sealButton = ButtonWidget.builder(Text.literal("§6✦ Seal"), b -> sealBarrier())
			.dimensions(startX + 16, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§6✦ Seal Barrier\n§7Locks perimeter to protect from minion harvesting.")))
			.build();
		this.addDrawableChild(this.sealButton);

		this.cancelButton = ButtonWidget.builder(Text.literal("§7✕ Cancel"), b -> cancelModal())
			.dimensions(startX + 122, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§7✕ Cancel Modal\n§7Keeps placed points so you can continue adding more.")))
			.build();
		this.addDrawableChild(this.cancelButton);

		this.removeButton = ButtonWidget.builder(Text.literal("§c⌫ Remove"), b -> removeAllPending())
			.dimensions(startX + 228, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§c⌫ Remove All Points\n§7Clears all unsealed points placed down.")))
			.build();
		this.addDrawableChild(this.removeButton);

		updateSealButtonState();
	}

	private void updateSealButtonState() {
		if (this.sealButton != null) {
			this.sealButton.active = ClientBarrierTracker.getPendingNodeCount() >= 2;
		}
	}

	public void sealBarrier() {
		String customName = this.nameField != null ? this.nameField.getText().trim() : "";
		if (customName.isEmpty()) {
			customName = "Sovereign Barrier #" + (ClientBarrierTracker.getConfirmedZones().size() + 1);
		}
		boolean sealed = ClientBarrierTracker.seal(customName);
		if (sealed && this.client != null && this.client.player != null) {
			this.client.player.sendMessage(Text.literal("§6✦ Barrier '" + customName + "' sealed! Territory protected from minion harvesting.§r"), true);
			this.client.player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.2F, 1.5F);
		}
		this.close();
	}

	public void cancelModal() {
		if (this.client != null && this.client.player != null) {
			this.client.player.sendMessage(Text.literal("§7✦ Barrier draft retained. Left-click to place more points or close on any waypoint to Seal.§r"), true);
			this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.0F);
		}
		this.close();
	}

	public void removeAllPending() {
		ClientBarrierTracker.clear();
		if (this.client != null && this.client.player != null) {
			this.client.player.sendMessage(Text.literal("§c✦ Removed all unsealed barrier points.§r"), true);
			this.client.player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.8F, 1.2F);
		}
		this.close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// Background dark card with golden accent border
		context.fill(startX, startY, startX + WINDOW_WIDTH, startY + WINDOW_HEIGHT, 0xF0101216);
		context.drawBorder(startX, startY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFFFA500);

		// Header accent bar
		context.fill(startX + 1, startY + 1, startX + WINDOW_WIDTH - 1, startY + 22, 0x40FFA500);
		context.drawBorder(startX + 1, startY + 1, WINDOW_WIDTH - 2, 21, 0x80FFA500);

		// Modal title
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§6✦ Confirm Sovereign Barrier ✦"),
			startX + WINDOW_WIDTH / 2,
			startY + 6,
			0xFFD700
		);

		int nodeCount = ClientBarrierTracker.getPendingNodeCount();
		int minY = ClientBarrierTracker.getMinY();
		int maxY = ClientBarrierTracker.getMaxY();
		int height = maxY - minY + 1;

		// Perimeter metrics - clean and compact
		String pointsStr = "§6Perimeter: §f" + nodeCount + " Nodes   §8|   §eHeight: §f" + height + "b   §8(Y: " + minY + " → " + maxY + ")";
		context.drawTextWithShadow(this.textRenderer, Text.literal(pointsStr), startX + 16, startY + 29, 0xFFA500);

		// Name input row label
		context.drawTextWithShadow(this.textRenderer, Text.literal("§eName:"), startX + 16, startY + 49, 0xFFD700);
		if (this.nameField != null) {
			this.nameField.render(context, mouseX, mouseY, delta);
		}

		// Clean bulleted description that fits well within margins
		int descY = startY + 68;
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Protects base & village territory from minion harvesting."), startX + 16, descY, 0xDDDDDD);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§8• Minions will never break blocks, chop trees, or quarry stone inside."), startX + 16, descY + 13, 0x888888);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§8• Follow, hold, patrol routes, and combat remain fully active."), startX + 16, descY + 25, 0x888888);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.nameField != null && this.nameField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				if (this.sealButton != null && this.sealButton.active) {
					sealBarrier();
					return true;
				}
			}
			if (this.nameField.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
		}

		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if (this.sealButton != null && this.sealButton.active) {
				sealBarrier();
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

	public ButtonWidget getSealButton() {
		return this.sealButton;
	}

	public ButtonWidget getCancelButton() {
		return this.cancelButton;
	}

	public ButtonWidget getRemoveButton() {
		return this.removeButton;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
