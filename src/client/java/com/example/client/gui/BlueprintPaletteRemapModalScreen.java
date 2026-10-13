package com.example.client.gui;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.StructureBlueprint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Dedicated modal screen displaying the extracted block palette of a captured spatial volume
 * or precompiled blueprint, letting players selectively remap blocks or erase them with Air.
 */
public class BlueprintPaletteRemapModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 380;
	public static final int WINDOW_HEIGHT = 240;

	private static final int ROW_HEIGHT = 24;
	private static final int SCROLLBAR_WIDTH = 6;

	public record PaletteRow(Identifier id, Block block, Item item, ItemStack stack, int count) {}

	private final BlockPos pos1;
	private final BlockPos pos2;
	private final StructureBlueprint precompiledBlueprint;
	private final Screen parentScreen;
	private final Consumer<Map<Identifier, Identifier>> onApplyCallback;

	private final List<PaletteRow> detectedRows = new ArrayList<>();
	private final Map<Identifier, Identifier> activeReplacements = new LinkedHashMap<>();

	private int winX;
	private int winY;
	private int listX;
	private int listY;
	private int listW;
	private int listH;

	private double scrollOffset = 0.0D;
	private boolean draggingScrollbar = false;

	private ButtonWidget applyButton;
	private ButtonWidget cancelButton;
	private ButtonWidget resetAllButton;

	public BlueprintPaletteRemapModalScreen(
		BlockPos pos1,
		BlockPos pos2,
		StructureBlueprint precompiledBlueprint,
		Map<Identifier, Identifier> initialReplacements,
		Screen parentScreen,
		Consumer<Map<Identifier, Identifier>> onApplyCallback
	) {
		super(Text.literal("Remap Blueprint Palette"));
		this.pos1 = pos1;
		this.pos2 = pos2;
		this.precompiledBlueprint = precompiledBlueprint;
		if (initialReplacements != null) {
			this.activeReplacements.putAll(initialReplacements);
		}
		this.parentScreen = parentScreen;
		this.onApplyCallback = onApplyCallback;
		extractPalette();
	}

	private void extractPalette() {
		Map<Block, Integer> tally = new LinkedHashMap<>();
		if (this.precompiledBlueprint != null) {
			for (BlueprintBlock b : this.precompiledBlueprint.getBlocks()) {
				if (b.state() != null && !b.state().isAir() && b.state().getBlock() != null) {
					tally.merge(b.state().getBlock(), 1, Integer::sum);
				}
			}
		} else if (this.pos1 != null && this.pos2 != null) {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc != null && mc.world != null) {
				int minX = Math.min(this.pos1.getX(), this.pos2.getX());
				int maxX = Math.max(this.pos1.getX(), this.pos2.getX());
				int minY = Math.min(this.pos1.getY(), this.pos2.getY());
				int maxY = Math.max(this.pos1.getY(), this.pos2.getY());
				int minZ = Math.min(this.pos1.getZ(), this.pos2.getZ());
				int maxZ = Math.max(this.pos1.getZ(), this.pos2.getZ());

				BlockPos.Mutable m = new BlockPos.Mutable();
				for (int y = minY; y <= maxY; y++) {
					for (int x = minX; x <= maxX; x++) {
						for (int z = minZ; z <= maxZ; z++) {
							m.set(x, y, z);
							BlockState state = mc.world.getBlockState(m);
							if (state != null && !state.isAir() && state.getBlock() != null) {
								tally.merge(state.getBlock(), 1, Integer::sum);
							}
						}
					}
				}
			}
		}

		this.detectedRows.clear();
		for (Map.Entry<Block, Integer> entry : tally.entrySet()) {
			Block block = entry.getKey();
			Identifier id = Registries.BLOCK.getId(block);
			Item item = block.asItem();
			ItemStack stack = (item != null && item != Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
			this.detectedRows.add(new PaletteRow(id, block, item, stack, entry.getValue()));
		}

		// Sort descending by block count, then by display name
		this.detectedRows.sort((a, b) -> {
			int cmp = Integer.compare(b.count(), a.count());
			if (cmp != 0) return cmp;
			return a.id().toString().compareToIgnoreCase(b.id().toString());
		});
	}

	@Override
	protected void init() {
		super.init();

		this.winX = (this.width - WINDOW_WIDTH) / 2;
		this.winY = (this.height - WINDOW_HEIGHT) / 2;

		this.listX = this.winX + 16;
		this.listY = this.winY + 48;
		this.listW = WINDOW_WIDTH - 32;
		this.listH = 150;

		// Bottom Action Buttons
		int bottomY = this.winY + WINDOW_HEIGHT - 28;

		this.resetAllButton = ButtonWidget.builder(Text.literal("§6↺ Reset All"), b -> resetAll())
			.dimensions(this.winX + 16, bottomY, 82, 20)
			.tooltip(Tooltip.of(Text.literal("Clear all configured block remappings")))
			.build();
		this.addDrawableChild(this.resetAllButton);

		this.cancelButton = ButtonWidget.builder(Text.literal("§c✖ Cancel"), b -> this.close())
			.dimensions(this.winX + 188, bottomY, 74, 20)
			.tooltip(Tooltip.of(Text.literal("Discard changes and return")))
			.build();
		this.addDrawableChild(this.cancelButton);

		this.applyButton = ButtonWidget.builder(Text.literal("§a✔ Apply"), b -> applyAndClose())
			.dimensions(this.winX + 270, bottomY, 94, 20)
			.tooltip(Tooltip.of(Text.literal("Apply remappings and return to save screen")))
			.build();
		this.addDrawableChild(this.applyButton);
	}

	private void resetAll() {
		this.activeReplacements.clear();
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	}

	private void applyAndClose() {
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
		Map<Identifier, Identifier> chosen = new LinkedHashMap<>(this.activeReplacements);
		if (this.parentScreen != null && this.client != null) {
			this.client.setScreen(this.parentScreen);
		} else {
			super.close();
		}
		if (this.onApplyCallback != null) {
			this.onApplyCallback.accept(chosen);
		}
	}

	private int contentHeight() {
		return this.detectedRows.size() * ROW_HEIGHT;
	}

	private double maxScroll() {
		return Math.max(0, contentHeight() - this.listH);
	}

	private void clampScroll() {
		this.scrollOffset = Math.max(0.0D, Math.min(this.scrollOffset, maxScroll()));
	}

	private boolean isInsideList(double mouseX, double mouseY) {
		return mouseX >= this.listX && mouseX < this.listX + this.listW
			&& mouseY >= this.listY && mouseY < this.listY + this.listH;
	}

	private int rowIndexAt(double mouseX, double mouseY) {
		if (!isInsideList(mouseX, mouseY)) return -1;
		int row = (int) ((mouseY - this.listY + this.scrollOffset) / ROW_HEIGHT);
		return (row >= 0 && row < this.detectedRows.size()) ? row : -1;
	}

	private boolean isOverScrollbar(double mouseX, double mouseY) {
		int trackX = this.listX + this.listW - SCROLLBAR_WIDTH;
		return maxScroll() > 0 && mouseX >= trackX && mouseX <= trackX + SCROLLBAR_WIDTH + 2
			&& mouseY >= this.listY && mouseY < this.listY + this.listH;
	}

	private void scrollToMouse(double mouseY) {
		double ratio = (mouseY - this.listY) / (double) this.listH;
		this.scrollOffset = Math.max(0.0D, Math.min(1.0D, ratio)) * maxScroll();
		clampScroll();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button == 0) {
			if (isOverScrollbar(mouseX, mouseY)) {
				this.draggingScrollbar = true;
				scrollToMouse(mouseY);
				return true;
			}
			int rowIndex = rowIndexAt(mouseX, mouseY);
			if (rowIndex >= 0) {
				PaletteRow row = this.detectedRows.get(rowIndex);
				int rowY = this.listY + (int) (rowIndex * ROW_HEIGHT - this.scrollOffset);

				// Button coordinates within row
				int changeBtnX = this.listX + this.listW - 128;
				int eraseBtnX = this.listX + this.listW - 82;
				int resetBtnX = this.listX + this.listW - 40;
				int btnY = rowY + 3;
				int btnH = 18;

				// 1. [ Change ] button
				if (mouseX >= changeBtnX && mouseX < changeBtnX + 44 && mouseY >= btnY && mouseY < btnY + btnH) {
					openBlockPicker(row.id());
					return true;
				}
				// 2. [ Erase ] button
				if (mouseX >= eraseBtnX && mouseX < eraseBtnX + 38 && mouseY >= btnY && mouseY < btnY + btnH) {
					eraseBlock(row.id());
					return true;
				}
				// 3. [ Reset ] button
				if (mouseX >= resetBtnX && mouseX < resetBtnX + 36 && mouseY >= btnY && mouseY < btnY + btnH) {
					resetBlock(row.id());
					return true;
				}
			}
		}
		return false;
	}

	private void openBlockPicker(Identifier sourceId) {
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			this.client.setScreen(new BlockPickerModalScreen(this, targetId -> {
				this.activeReplacements.put(sourceId, targetId);
			}));
		}
	}

	private void eraseBlock(Identifier sourceId) {
		this.activeReplacements.put(sourceId, Identifier.of("minecraft", "air"));
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	}

	private void resetBlock(Identifier sourceId) {
		this.activeReplacements.remove(sourceId);
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		this.draggingScrollbar = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.draggingScrollbar) {
			scrollToMouse(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (maxScroll() > 0) {
			this.scrollOffset -= verticalAmount * (ROW_HEIGHT * 2.0D);
			clampScroll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	protected void applyBlur(float delta) {
		// Suppress background world blur post-processing shader
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);

		// Modal background plate & gold border
		context.fill(this.winX, this.winY, this.winX + WINDOW_WIDTH, this.winY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(this.winX, this.winY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFE2B007);

		// Header plate
		context.fill(this.winX + 1, this.winY + 1, this.winX + WINDOW_WIDTH - 1, this.winY + 24, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§6✦ REMAP BLUEPRINT PALETTE ✦"),
			this.width / 2,
			this.winY + 7,
			0xFFFFFF
		);

		Text escText = Text.literal("§e[Esc]");
		context.drawTextWithShadow(this.textRenderer, escText, this.winX + WINDOW_WIDTH - this.textRenderer.getWidth(escText) - 10, this.winY + 7, 0xAAAAAA);

		// Subheader tally info
		int activeCount = this.activeReplacements.size();
		String info = "§7Detected: §e" + this.detectedRows.size() + " unique blocks §8| §b" + activeCount + " remapped";
		context.drawTextWithShadow(this.textRenderer, Text.literal(info), this.winX + 16, this.winY + 32, 0xAAAAAA);

		// Table outer container
		context.fill(this.listX - 2, this.listY - 2, this.listX + this.listW + 2, this.listY + this.listH + 2, 0xFF151D28);
		context.drawBorder(this.listX - 2, this.listY - 2, this.listW + 4, this.listH + 4, 0xFF3A4E63);

		// Scissor clipping for rows
		context.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);

		int startRow = Math.max(0, (int) (this.scrollOffset / ROW_HEIGHT));
		int endRow = Math.min(this.detectedRows.size(), (int) ((this.scrollOffset + this.listH) / ROW_HEIGHT) + 1);

		for (int i = startRow; i < endRow; i++) {
			PaletteRow row = this.detectedRows.get(i);
			int rowY = this.listY + (int) (i * ROW_HEIGHT - this.scrollOffset);

			boolean rowHovered = mouseX >= this.listX && mouseX < this.listX + this.listW
				&& mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;

			int rowBg = rowHovered ? 0x882A3E54 : ((i % 2 == 0) ? 0x55111A24 : 0x33111A24);
			context.fill(this.listX, rowY, this.listX + this.listW, rowY + ROW_HEIGHT - 2, rowBg);

			// 1. Source Icon + Display Name + Count
			if (!row.stack().isEmpty()) {
				context.drawItem(row.stack(), this.listX + 2, rowY + 3);
			}
			String srcName = row.block().getName().getString();
			if (srcName.length() > 10) {
				srcName = srcName.substring(0, 9) + "…";
			}
			context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + srcName), this.listX + 22, rowY + 4, 0xFFFFFF);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7x" + row.count()), this.listX + 80, rowY + 4, 0xAAAAAA);

			// 2. Arrow indicator
			context.drawTextWithShadow(this.textRenderer, Text.literal("§e➔"), this.listX + 104, rowY + 4, 0xFFD700);

			// 3. Target Block info
			Identifier targetId = this.activeReplacements.get(row.id());
			if (targetId == null) {
				context.drawTextWithShadow(this.textRenderer, Text.literal("§8Original"), this.listX + 118, rowY + 4, 0x888888);
			} else if (targetId.equals(Identifier.of("minecraft", "air"))) {
				context.drawTextWithShadow(this.textRenderer, Text.literal("§c✕ Erased"), this.listX + 118, rowY + 4, 0xFF5555);
			} else {
				Block targetBlock = Registries.BLOCK.get(targetId);
				ItemStack targetStack = targetBlock != null ? new ItemStack(targetBlock.asItem()) : ItemStack.EMPTY;
				if (!targetStack.isEmpty()) {
					context.drawItem(targetStack, this.listX + 116, rowY + 3);
				}
				String tgtName = targetBlock != null ? targetBlock.getName().getString() : targetId.getPath();
				if (tgtName.length() > 8) {
					tgtName = tgtName.substring(0, 7) + "…";
				}
				context.drawTextWithShadow(this.textRenderer, Text.literal("§a" + tgtName), this.listX + 136, rowY + 4, 0x55FF55);
			}

			// 4. Action Buttons
			int changeBtnX = this.listX + this.listW - 128;
			int eraseBtnX = this.listX + this.listW - 82;
			int resetBtnX = this.listX + this.listW - 40;
			int btnY = rowY + 2;
			int btnH = 18;

			boolean changeHovered = mouseX >= changeBtnX && mouseX < changeBtnX + 44 && mouseY >= btnY && mouseY < btnY + btnH;
			boolean eraseHovered = mouseX >= eraseBtnX && mouseX < eraseBtnX + 38 && mouseY >= btnY && mouseY < btnY + btnH;
			boolean resetHovered = mouseX >= resetBtnX && mouseX < resetBtnX + 36 && mouseY >= btnY && mouseY < btnY + btnH;

			// Change Button [ 🔍 ]
			context.fill(changeBtnX, btnY, changeBtnX + 44, btnY + btnH, changeHovered ? 0xFF2A527A : 0xFF1B2F44);
			context.drawBorder(changeBtnX, btnY, 44, btnH, changeHovered ? 0xFFE2B007 : 0xFF3A5A7C);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§bChange"), changeBtnX + 22, btnY + 5, 0xFFFFFF);

			// Erase Button [ ✕ ]
			context.fill(eraseBtnX, btnY, eraseBtnX + 38, btnY + btnH, eraseHovered ? 0xFF7A2A2A : 0xFF441B1B);
			context.drawBorder(eraseBtnX, btnY, 38, btnH, eraseHovered ? 0xFFFF5555 : 0xFF7C3A3A);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§cErase"), eraseBtnX + 19, btnY + 5, 0xFFFFFF);

			// Reset Button [ ↺ ]
			context.fill(resetBtnX, btnY, resetBtnX + 36, btnY + btnH, resetHovered ? 0xFF555555 : 0xFF2A2A2A);
			context.drawBorder(resetBtnX, btnY, 36, btnH, resetHovered ? 0xFFE0E0E0 : 0xFF4A4A4A);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Reset"), resetBtnX + 18, btnY + 5, 0xCCCCCC);
		}

		context.disableScissor();

		// Scrollbar
		if (maxScroll() > 0) {
			int trackX = this.listX + this.listW + 2;
			context.fill(trackX, this.listY, trackX + SCROLLBAR_WIDTH, this.listY + this.listH, 0xFF151D28);
			int thumbH = Math.max(16, (int) (this.listH * (this.listH / (double) contentHeight())));
			int thumbY = this.listY + (int) ((this.scrollOffset / maxScroll()) * (this.listH - thumbH));
			context.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, 0xFF5B7A9C);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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

	public Map<Identifier, Identifier> getActiveReplacements() {
		return Collections.unmodifiableMap(this.activeReplacements);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
