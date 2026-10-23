package com.example.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Dedicated modal screen allowing the player to pick a valid placeable replacement block.
 *
 * Strict Guardrail:
 * Only items that are {@link BlockItem} and non-air blocks can be selected.
 * Non-block items (weapons, tools, armor, food, raw materials) are strictly filtered out.
 */
public class BlockPickerModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 380;
	public static final int WINDOW_HEIGHT = 240;

	private static final int COLUMNS = 9;
	private static final int SLOT_SIZE = 24;
	private static final int ICON_INSET = 4;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int MAX_RECENT_BLOCKS = 10;

	public record BlockEntry(Identifier id, Block block, Item item, ItemStack stack, String searchKey) {}

	private static List<BlockEntry> cachedAllBlocks = null;
	private static final List<Identifier> RECENT_PICKED_BLOCKS = new ArrayList<>();

	private final Screen parentScreen;
	private final Consumer<Identifier> onSelectCallback;

	private TextFieldWidget searchField;
	private String searchQuery = "";
	private final List<BlockEntry> filteredEntries = new ArrayList<>();

	private ButtonWidget cancelButton;

	private int winX;
	private int winY;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;

	private int recentX;
	private int recentY;
	private int recentW;
	private int recentH;

	private double scrollOffset = 0.0D;
	private boolean draggingScrollbar = false;

	public BlockPickerModalScreen(Screen parentScreen, Consumer<Identifier> onSelectCallback) {
		super(Text.literal("Select Replacement Block"));
		this.parentScreen = parentScreen;
		this.onSelectCallback = onSelectCallback;
		initBlockCache();
	}

	private static void initBlockCache() {
		if (cachedAllBlocks != null) {
			return;
		}
		List<BlockEntry> list = new ArrayList<>();
		if (Registries.BLOCK != null && Registries.ITEM != null) {
			for (Block block : Registries.BLOCK) {
				if (block == null || block.getDefaultState().isAir() || !com.example.blueprint.BlueprintBlock.isSurvivalPlaceableBlock(block)) {
					continue;
				}
				Identifier id = Registries.BLOCK.getId(block);
				if (id == null) continue;

				Item item = block.asItem();
				ItemStack stack;
				String name;

				if (block == net.minecraft.block.Blocks.FARMLAND) {
					item = net.minecraft.item.Items.DIRT;
					stack = new ItemStack(net.minecraft.item.Items.DIRT);
					name = "Farmland (Tilled Dirt)";
				} else if (block == net.minecraft.block.Blocks.DIRT_PATH) {
					item = net.minecraft.item.Items.DIRT;
					stack = new ItemStack(net.minecraft.item.Items.DIRT);
					name = "Dirt Path (Flattened Dirt)";
				} else if (item instanceof BlockItem blockItem && !blockItem.getBlock().getDefaultState().isAir()) {
					stack = new ItemStack(item);
					name = item.getName().getString();
				} else {
					continue;
				}

				String searchKey = id.toString().toLowerCase(Locale.ROOT) + " " + name.toLowerCase(Locale.ROOT);
				list.add(new BlockEntry(id, block, item, stack, searchKey));
			}
			list.sort((a, b) -> a.stack().getName().getString().compareToIgnoreCase(b.stack().getName().getString()));
		}
		cachedAllBlocks = Collections.unmodifiableList(list);
	}

	@Override
	protected void init() {
		super.init();

		this.winX = (this.width - WINDOW_WIDTH) / 2;
		this.winY = (this.height - WINDOW_HEIGHT) / 2;

		this.gridX = this.winX + 16;
		this.gridY = this.winY + 58;
		this.gridW = COLUMNS * SLOT_SIZE;
		this.gridH = 140;

		this.recentX = this.winX + 252;
		this.recentY = this.winY + 58;
		this.recentW = 112;
		this.recentH = 140;

		// 1. Search bar
		this.searchField = new TextFieldWidget(
			this.textRenderer,
			this.winX + 16,
			this.winY + 32,
			WINDOW_WIDTH - 32,
			18,
			Text.literal("Search Blocks")
		);
		this.searchField.setMaxLength(32);
		this.searchField.setPlaceholder(Text.literal("§8🔍 Search placeable blocks..."));
		this.searchField.setChangedListener(this::setSearchQuery);
		this.addSelectableChild(this.searchField);
		this.setInitialFocus(this.searchField);

		// 2. Action buttons at bottom tray
		this.cancelButton = ButtonWidget.builder(Text.literal("§c✖ Cancel"), b -> this.close())
			.dimensions(this.winX + WINDOW_WIDTH - 96, this.winY + WINDOW_HEIGHT - 28, 80, 20)
			.tooltip(Tooltip.of(Text.literal("Cancel and return")))
			.build();
		this.addDrawableChild(this.cancelButton);

		refilter();
	}

	public void setSearchQuery(String query) {
		this.searchQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
		this.scrollOffset = 0.0D;
		refilter();
	}

	private void refilter() {
		this.filteredEntries.clear();
		if (cachedAllBlocks == null) {
			initBlockCache();
		}
		if (cachedAllBlocks != null) {
			if (this.searchQuery.isEmpty()) {
				this.filteredEntries.addAll(cachedAllBlocks);
			} else {
				for (BlockEntry entry : cachedAllBlocks) {
					if (entry.searchKey().contains(this.searchQuery)) {
						this.filteredEntries.add(entry);
					}
				}
			}
		}
	}

	private int rowCount() {
		return (this.filteredEntries.size() + COLUMNS - 1) / COLUMNS;
	}

	private int contentHeight() {
		return rowCount() * SLOT_SIZE;
	}

	private double maxScroll() {
		return Math.max(0, contentHeight() - this.gridH);
	}

	private void clampScroll() {
		this.scrollOffset = Math.max(0.0D, Math.min(this.scrollOffset, maxScroll()));
	}

	private boolean isInsideGrid(double mouseX, double mouseY) {
		return mouseX >= this.gridX && mouseX < this.gridX + this.gridW
			&& mouseY >= this.gridY && mouseY < this.gridY + this.gridH;
	}

	private int entryIndexAt(double mouseX, double mouseY) {
		if (!isInsideGrid(mouseX, mouseY)) {
			return -1;
		}
		int col = (int) ((mouseX - this.gridX) / SLOT_SIZE);
		int row = (int) ((mouseY - this.gridY + this.scrollOffset) / SLOT_SIZE);
		if (col < 0 || col >= COLUMNS || row < 0) {
			return -1;
		}
		int index = row * COLUMNS + col;
		return index < this.filteredEntries.size() ? index : -1;
	}

	private boolean isOverScrollbar(double mouseX, double mouseY) {
		int trackX = this.gridX + this.gridW + 6;
		return maxScroll() > 0 && mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
			&& mouseY >= this.gridY && mouseY < this.gridY + this.gridH;
	}

	private void scrollToMouse(double mouseY) {
		double ratio = (mouseY - this.gridY) / (double) this.gridH;
		this.scrollOffset = Math.max(0.0D, Math.min(1.0D, ratio)) * maxScroll();
		clampScroll();
	}

	private Identifier recentBlockAt(double mouseX, double mouseY) {
		int startY = this.recentY + 16;
		int slotSize = 22;
		for (int i = 0; i < RECENT_PICKED_BLOCKS.size() && i < MAX_RECENT_BLOCKS; i++) {
			int col = i % 4;
			int row = i / 4;
			int sx = this.recentX + 6 + col * 26;
			int sy = startY + row * 26;
			if (mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
				return RECENT_PICKED_BLOCKS.get(i);
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button == 0) {
			Identifier clickedRecent = recentBlockAt(mouseX, mouseY);
			if (clickedRecent != null) {
				selectBlock(clickedRecent);
				return true;
			}
			if (isOverScrollbar(mouseX, mouseY)) {
				this.draggingScrollbar = true;
				scrollToMouse(mouseY);
				return true;
			}
			int index = entryIndexAt(mouseX, mouseY);
			if (index >= 0) {
				BlockEntry entry = this.filteredEntries.get(index);
				selectBlock(entry.id());
				return true;
			}
		}
		return false;
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
			this.scrollOffset -= verticalAmount * (SLOT_SIZE * 2.0D);
			clampScroll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	public void selectBlock(Identifier blockId) {
		if (blockId != null) {
			RECENT_PICKED_BLOCKS.remove(blockId);
			RECENT_PICKED_BLOCKS.add(0, blockId);
			while (RECENT_PICKED_BLOCKS.size() > MAX_RECENT_BLOCKS) {
				RECENT_PICKED_BLOCKS.remove(RECENT_PICKED_BLOCKS.size() - 1);
			}
		}
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
		if (this.onSelectCallback != null) {
			this.onSelectCallback.accept(blockId);
		}
		this.close();
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
			Text.literal("§6✦ SELECT REPLACEMENT BLOCK ✦"),
			this.width / 2,
			this.winY + 7,
			0xFFFFFF
		);

		Text escText = Text.literal("§e[Esc]");
		context.drawTextWithShadow(this.textRenderer, escText, this.winX + WINDOW_WIDTH - this.textRenderer.getWidth(escText) - 10, this.winY + 7, 0xAAAAAA);

		// Status info below header
		String countStr = "§7Matches: §e" + this.filteredEntries.size() + "§7 placeable blocks";
		context.drawTextWithShadow(this.textRenderer, Text.literal(countStr), this.winX + 16, this.winY + WINDOW_HEIGHT - 22, 0xAAAAAA);

		// Search field render
		this.searchField.render(context, mouseX, mouseY, delta);

		// Render grid container background
		context.fill(this.gridX - 2, this.gridY - 2, this.gridX + this.gridW + 2, this.gridY + this.gridH + 2, 0xFF151D28);
		context.drawBorder(this.gridX - 2, this.gridY - 2, this.gridW + 4, this.gridH + 4, 0xFF3A4E63);

		// Render Recent Picked container background
		context.fill(this.recentX - 2, this.recentY - 2, this.recentX + this.recentW + 2, this.recentY + this.recentH + 2, 0xFF151D28);
		context.drawBorder(this.recentX - 2, this.recentY - 2, this.recentW + 4, this.recentH + 4, 0xFFE2B007);
		context.fill(this.recentX - 1, this.recentY - 1, this.recentX + this.recentW + 1, this.recentY + 13, 0xDD1B2A3A);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§6✦ Recent Picked"), this.recentX + 4, this.recentY + 2, 0xFFFFFF);

		// Enable scissor clipping for scrollable grid
		context.enableScissor(this.gridX, this.gridY, this.gridX + this.gridW, this.gridY + this.gridH);

		BlockEntry hoveredEntry = null;
		ItemStack hoveredRecentStack = null;
		Identifier hoveredRecentId = null;

		int totalEntries = this.filteredEntries.size();
		int startRow = Math.max(0, (int) (this.scrollOffset / SLOT_SIZE));
		int endRow = Math.min(rowCount(), (int) ((this.scrollOffset + this.gridH) / SLOT_SIZE) + 1);

		for (int r = startRow; r < endRow; r++) {
			for (int c = 0; c < COLUMNS; c++) {
				int idx = r * COLUMNS + c;
				if (idx >= totalEntries) break;

				int slotX = this.gridX + c * SLOT_SIZE;
				int slotY = this.gridY + (int) (r * SLOT_SIZE - this.scrollOffset);

				boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE
					&& mouseY >= slotY && mouseY < slotY + SLOT_SIZE
					&& mouseY >= this.gridY && mouseY < this.gridY + this.gridH;

				int slotBg = hovered ? 0x883A5370 : 0x551B2A3A;
				context.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, slotBg);
				context.drawBorder(slotX, slotY, SLOT_SIZE, SLOT_SIZE, hovered ? 0xFFE2B007 : 0xFF2A3E54);

				BlockEntry entry = this.filteredEntries.get(idx);
				context.drawItem(entry.stack(), slotX + ICON_INSET, slotY + ICON_INSET);

				if (hovered) {
					hoveredEntry = entry;
				}
			}
		}

		context.disableScissor();

		// Render Recent Picked slots
		int rStartY = this.recentY + 16;
		int rSlotSize = 22;
		if (RECENT_PICKED_BLOCKS.isEmpty()) {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§8None yet"), this.recentX + 8, rStartY + 4, 0x888888);
		} else {
			for (int i = 0; i < RECENT_PICKED_BLOCKS.size() && i < MAX_RECENT_BLOCKS; i++) {
				Identifier rId = RECENT_PICKED_BLOCKS.get(i);
				Block rBlock = Registries.BLOCK.get(rId);
				ItemStack rStack = new ItemStack(rBlock);

				int col = i % 4;
				int row = i / 4;
				int sx = this.recentX + 6 + col * 26;
				int sy = rStartY + row * 26;

				boolean hovered = mouseX >= sx && mouseX < sx + rSlotSize && mouseY >= sy && mouseY < sy + rSlotSize;
				int slotBg = hovered ? 0x88E2B007 : 0x551B2A3A;
				context.fill(sx, sy, sx + rSlotSize, sy + rSlotSize, slotBg);
				context.drawBorder(sx, sy, rSlotSize, rSlotSize, hovered ? 0xFFFFFF : 0xFFE2B007);
				context.drawItem(rStack, sx + 3, sy + 3);

				if (hovered) {
					hoveredRecentStack = rStack;
					hoveredRecentId = rId;
				}
			}
		}

		// Render scrollbar if content overflows
		if (maxScroll() > 0) {
			int trackX = this.gridX + this.gridW + 4;
			context.fill(trackX, this.gridY, trackX + SCROLLBAR_WIDTH, this.gridY + this.gridH, 0xFF151D28);
			int thumbH = Math.max(16, (int) (this.gridH * (this.gridH / (double) contentHeight())));
			int thumbY = this.gridY + (int) ((this.scrollOffset / maxScroll()) * (this.gridH - thumbH));
			context.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, 0xFF5B7A9C);
		}

		super.render(context, mouseX, mouseY, delta);

		// Render tooltip for hovered item above everything
		if (hoveredEntry != null) {
			List<Text> tooltip = new ArrayList<>();
			tooltip.add(Text.literal("§f" + hoveredEntry.stack().getName().getString()));
			tooltip.add(Text.literal("§8" + hoveredEntry.id().toString()));
			context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
		} else if (hoveredRecentStack != null && hoveredRecentId != null) {
			List<Text> tooltip = new ArrayList<>();
			tooltip.add(Text.literal("§6[Recent] §f" + hoveredRecentStack.getName().getString()));
			tooltip.add(Text.literal("§8" + hoveredRecentId.toString()));
			tooltip.add(Text.literal("§eClick to pick instantly"));
			context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			this.close();
			return true;
		}
		if (this.searchField != null && this.searchField.keyPressed(keyCode, scanCode, modifiers)) {
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
