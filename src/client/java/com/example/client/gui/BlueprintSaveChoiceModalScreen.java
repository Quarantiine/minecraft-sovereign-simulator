package com.example.client.gui;

import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.client.network.ModClientNetworking;
import com.example.item.custom.CommandScepterItem;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Modal dialog presented after palette remapping in the 3D Blueprint Explorer,
 * prompting the player to either overwrite the active blueprint or save as a new copy.
 */
public class BlueprintSaveChoiceModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 320;
	public static final int WINDOW_HEIGHT = 168;

	private final StructureBlueprint sourceBlueprint;
	private final Map<Identifier, Identifier> replacements;
	private final ItemStack scepterStack;
	private final Screen parentScreen;
	private final Consumer<StructureBlueprint> onSavedCallback;

	private TextFieldWidget nameField;
	private ButtonWidget overwriteButton;
	private ButtonWidget saveCopyButton;
	private ButtonWidget cancelButton;
	private String errorMessage = null;

	public BlueprintSaveChoiceModalScreen(
		StructureBlueprint sourceBlueprint,
		Map<Identifier, Identifier> replacements,
		ItemStack scepterStack,
		Screen parentScreen,
		Consumer<StructureBlueprint> onSavedCallback
	) {
		super(Text.literal("Save Remapped Blueprint"));
		this.sourceBlueprint = Objects.requireNonNull(sourceBlueprint, "sourceBlueprint cannot be null");
		this.replacements = replacements != null ? Map.copyOf(replacements) : Map.of();
		this.scepterStack = scepterStack;
		this.parentScreen = parentScreen;
		this.onSavedCallback = onSavedCallback;
	}

	public BlueprintSaveChoiceModalScreen(
		StructureBlueprint sourceBlueprint,
		Map<Identifier, Identifier> replacements,
		ItemStack scepterStack,
		Screen parentScreen
	) {
		this(sourceBlueprint, replacements, scepterStack, parentScreen, null);
	}

	@Override
	protected void init() {
		super.init();

		int winX = (this.width - WINDOW_WIDTH) / 2;
		int winY = (this.height - WINDOW_HEIGHT) / 2;

		int fieldX = winX + 16;
		int fieldY = winY + 84;
		int fieldW = WINDOW_WIDTH - 32;

		String defaultName = this.sourceBlueprint.getName();
		if (this.nameField != null) {
			defaultName = this.nameField.getText();
		} else if (!defaultName.endsWith("(Remapped)")) {
			defaultName = defaultName + " (Remapped)";
		}

		this.nameField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldW, 18, Text.literal("Blueprint Name"));
		this.nameField.setMaxLength(64);
		this.nameField.setText(defaultName);
		this.addDrawableChild(this.nameField);

		int btnY = winY + WINDOW_HEIGHT - 28;

		this.overwriteButton = ButtonWidget.builder(Text.literal("§6💾 Overwrite"), b -> executeSave(true))
			.dimensions(winX + 16, btnY, 92, 20)
			.tooltip(Tooltip.of(Text.literal("§6Overwrite this blueprint permanently with the new palette.\n§7Keeps the existing blueprint ID.")))
			.build();
		this.addDrawableChild(this.overwriteButton);

		this.saveCopyButton = ButtonWidget.builder(Text.literal("§b📋 Save Copy"), b -> executeSave(false))
			.dimensions(winX + 114, btnY, 114, 20)
			.tooltip(Tooltip.of(Text.literal("§bSave as a new custom blueprint copy.\n§7Preserves the original blueprint unmodified.")))
			.build();
		this.addDrawableChild(this.saveCopyButton);

		this.cancelButton = ButtonWidget.builder(Text.literal("§c✖ Cancel"), b -> closeScreen())
			.dimensions(winX + 234, btnY, 70, 20)
			.tooltip(Tooltip.of(Text.literal("§7Discard palette remappings and return.")))
			.build();
		this.addDrawableChild(this.cancelButton);
	}

	private void executeSave(boolean isOverwrite) {
		String cleanName = this.nameField != null ? this.nameField.getText().trim() : "";
		if (cleanName.isBlank()) {
			this.errorMessage = "§cBlueprint name cannot be empty.";
			return;
		}

		String targetId;
		if (isOverwrite) {
			targetId = this.sourceBlueprint.getId();
		} else {
			String sanitized = cleanName.toLowerCase().replaceAll("[^a-z0-9_]+", "_");
			if (sanitized.isBlank()) sanitized = "custom";
			targetId = sanitized + "_" + System.currentTimeMillis();
		}

		StructureBlueprint remapped = StructureBlueprint.createRemapped(
			this.sourceBlueprint,
			targetId,
			cleanName,
			this.sourceBlueprint.getDescription(),
			this.replacements
		);

		if (remapped.getBlockCount() == 0) {
			this.errorMessage = "§cCannot save: all blocks were erased.";
			return;
		}

		// 1. Dispatch persistence payload to server
		ModClientNetworking.sendCreateCustomBlueprint(remapped);

		// 2. Register immediately in client registry
		BlueprintRegistry.registerCustomBlueprint(remapped, false);

		// 3. Update active blueprint on Command Scepter
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setBlueprintId(this.scepterStack, remapped.getId());
		}

		// 4. Play audio feedback
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc != null && mc.getSoundManager() != null) {
			mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2F, 1.4F));
			mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 0.8F, 1.1F));
		}
		if (mc != null && mc.player != null) {
			String actionLabel = isOverwrite ? "Overwrote" : "Created";
			mc.player.sendMessage(
				Text.literal("§6✦ " + actionLabel + " Blueprint: §b" + remapped.getName() + " §7(" + remapped.getBlockCount() + " blocks)§r"),
				true
			);
		}

		// 5. Notify callback
		if (this.onSavedCallback != null) {
			this.onSavedCallback.accept(remapped);
		}

		// 6. Return to parent screen
		if (this.parentScreen instanceof BlueprintExplorerScreen explorer) {
			explorer.refreshAndSelect(remapped.getId());
			if (mc != null) {
				mc.setScreen(explorer);
			}
		} else if (this.parentScreen != null) {
			if (mc != null) {
				mc.setScreen(this.parentScreen);
			}
		} else {
			this.close();
		}
	}

	private void closeScreen() {
		if (this.client != null) {
			if (this.parentScreen != null) {
				this.client.setScreen(this.parentScreen);
			} else {
				this.close();
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int winX = (this.width - WINDOW_WIDTH) / 2;
		int winY = (this.height - WINDOW_HEIGHT) / 2;

		// Modal backdrop & golden border
		context.fill(winX, winY, winX + WINDOW_WIDTH, winY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(winX, winY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFE2B007);

		// Header bar
		context.fill(winX + 1, winY + 1, winX + WINDOW_WIDTH - 1, winY + 24, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§6✦ SAVE REMAPPED BLUEPRINT ✦"),
			this.width / 2,
			winY + 8,
			0xFFFFFF
		);

		// Content labels
		String origName = this.sourceBlueprint.getName();
		if (this.textRenderer.getWidth(origName) > WINDOW_WIDTH - 110) {
			origName = this.textRenderer.trimToWidth(origName, WINDOW_WIDTH - 120) + "…";
		}
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Original: §f\"" + origName + "\""), winX + 16, winY + 34, 0xDDDDDD);

		int count = this.replacements.size();
		context.drawTextWithShadow(this.textRenderer, Text.literal("§ePalette Remap: §b" + count + " §7block type(s) modified"), winX + 16, winY + 48, 0xAAAAAA);

		context.drawTextWithShadow(this.textRenderer, Text.literal("§fBlueprint Display Name:"), winX + 16, winY + 70, 0xFFFFFF);

		// Error banner
		if (this.errorMessage != null) {
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.errorMessage), winX + WINDOW_WIDTH / 2, winY + 110, 0xFF5555);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			closeScreen();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			executeSave(false);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
