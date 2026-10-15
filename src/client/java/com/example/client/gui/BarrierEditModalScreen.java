package com.example.client.gui;

import com.example.barrier.BarrierZone;
import com.example.client.renderer.ClientBarrierTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Client modal screen for inspecting, renaming, and deleting an existing sealed Sovereign Barrier.
 */
public class BarrierEditModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 300;
	public static final int WINDOW_HEIGHT = 160;

	private final int zoneId;
	private final Screen parentScreen;

	private BarrierZone zone;
	private TextFieldWidget nameField;
	private ButtonWidget saveButton;
	private ButtonWidget deleteButton;
	private ButtonWidget cancelButton;

	public BarrierEditModalScreen(BarrierZone zone, Screen parentScreen) {
		super(Text.literal("Edit Sovereign Barrier"));
		this.zone = zone;
		this.zoneId = zone != null ? zone.getId() : 0;
		this.parentScreen = parentScreen;
	}

	@Override
	protected void applyBlur(float delta) {
		// Preserve tactical background visibility
	}

	@Override
	protected void init() {
		super.init();

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// Re-locate zone in confirmed list if possible
		if (this.zone == null && this.zoneId > 0) {
			for (BarrierZone z : ClientBarrierTracker.getConfirmedZones()) {
				if (z.getId() == this.zoneId) {
					this.zone = z;
					break;
				}
			}
		}

		String currentName = (this.zone != null && this.zone.getName() != null) ? this.zone.getName() : ("Sovereign Barrier #" + this.zoneId);

		// Name text input field
		this.nameField = new TextFieldWidget(
			this.textRenderer,
			startX + 60,
			startY + 56,
			224,
			18,
			Text.literal("Barrier Name")
		);
		this.nameField.setMaxLength(32);
		this.nameField.setText(currentName);
		this.addSelectableChild(this.nameField);

		// Action buttons tray
		int bottomY = startY + WINDOW_HEIGHT - 28;
		int btnWidth = 86;

		this.saveButton = ButtonWidget.builder(Text.literal("§a✔ Save"), b -> saveChanges())
			.dimensions(startX + 15, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§a✔ Save Changes\n§7Updates the barrier's name across the realm.")))
			.build();
		this.addDrawableChild(this.saveButton);

		this.cancelButton = ButtonWidget.builder(Text.literal("§7✕ Cancel"), b -> close())
			.dimensions(startX + 107, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§7✕ Cancel\n§7Return without saving changes.")))
			.build();
		this.addDrawableChild(this.cancelButton);

		this.deleteButton = ButtonWidget.builder(Text.literal("§c✕ Delete"), b -> deleteBarrier())
			.dimensions(startX + 199, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§c✕ Delete Barrier\n§7Permanently removes this entire barrier and all its nodes.")))
			.build();
		this.addDrawableChild(this.deleteButton);
	}

	private void saveChanges() {
		String newName = this.nameField != null ? this.nameField.getText().trim() : "";
		if (!newName.isEmpty()) {
			ClientBarrierTracker.renameZone(this.zoneId, newName);
			if (this.client != null && this.client.player != null) {
				this.client.player.sendMessage(Text.literal("§6✦ Renamed Barrier to '" + newName + "'§r"), true);
				this.client.player.playSound(SoundEvents.BLOCK_ANVIL_USE, 0.8F, 1.2F);
			}
		}
		this.close();
	}

	private void deleteBarrier() {
		boolean deleted = ClientBarrierTracker.deleteConfirmedZone(this.zoneId);
		if (deleted && this.client != null && this.client.player != null) {
			this.client.player.sendMessage(Text.literal("§c✦ Deleted Sovereign Barrier #" + this.zoneId + " and all boundary nodes.§r"), true);
			this.client.player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.9F);
		}
		this.close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// Card background with golden accent
		context.fill(startX, startY, startX + WINDOW_WIDTH, startY + WINDOW_HEIGHT, 0xF0101216);
		context.drawBorder(startX, startY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFFFA500);

		// Header accent bar
		context.fill(startX + 1, startY + 1, startX + WINDOW_WIDTH - 1, startY + 22, 0x40FFA500);
		context.drawBorder(startX + 1, startY + 1, WINDOW_WIDTH - 2, 21, 0x80FFA500);

		// Header title
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§6✦ Edit Sovereign Barrier #" + this.zoneId + " ✦"),
			startX + WINDOW_WIDTH / 2,
			startY + 6,
			0xFFD700
		);

		// Perimeter metrics if available
		if (this.zone != null) {
			int height = this.zone.getMaxY() - this.zone.getMinY() + 1;
			String stats = "§6Nodes: §f" + this.zone.getNodeCount() + " Waypoints  §8|  §eHeight: §f" + height + "b §8(Y: " + this.zone.getMinY() + " → " + this.zone.getMaxY() + ")";
			context.drawTextWithShadow(this.textRenderer, Text.literal(stats), startX + 16, startY + 34, 0xFFA500);
		} else {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Protected territory boundary zone."), startX + 16, startY + 34, 0xCCCCCC);
		}

		// Name label
		context.drawTextWithShadow(this.textRenderer, Text.literal("§eName:"), startX + 16, startY + 61, 0xFFD700);
		if (this.nameField != null) {
			this.nameField.render(context, mouseX, mouseY, delta);
		}

		// Helper sub-note
		context.drawTextWithShadow(this.textRenderer, Text.literal("§8Renames the sky banner and territory in the Command Scepter."), startX + 16, startY + 86, 0x888888);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§8To remove completely, click §c[✕ Delete] §8below."), startX + 16, startY + 100, 0x888888);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.nameField != null && this.nameField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				saveChanges();
				return true;
			}
			if (this.nameField.keyPressed(keyCode, scanCode, modifiers)) {
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
		if (this.client != null) {
			if (this.parentScreen != null) {
				this.client.setScreen(this.parentScreen);
			} else {
				super.close();
			}
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
