package com.example.client.gui;

import com.example.client.renderer.ClientMiningCaptureTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
 * Client modal GUI screen allowing commanders to curate which block types minions should dismantle
 * or leave untouched within a confirmed 3D quarry excavation volume.
 *
 * Features:
 * - Scans real-world terrain in [pos1, pos2] to detect all unique destructible block types and counts.
 * - Real-time search filter ([ 🔍 Search blocks... ]) supporting block display names and registry IDs.
 * - Interactive row toggling: [✔ Mine] vs [✕ Don't Mine].
 * - Quick [✔ Select All] and [✖ Clear All] bulk controls.
 * - Live selection counter displaying target types and total volume of curated blocks to mine.
 * - Fail-safe apply logic: unchecking all blocks reverts to mining all blocks in the area.
 * - Zero world blur bypass to maintain tactical situational awareness.
 */
public class MiningCurationModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 360;
	public static final int WINDOW_HEIGHT = 240;

	private static final int ROW_HEIGHT = 26;
	private static final int SCROLLBAR_WIDTH = 6;

	public record CurationEntry(Identifier id, Block block, Item item, ItemStack icon, String name, int count) {}

	private final BlockPos pos1;
	private final BlockPos pos2;
	private final Screen parentScreen;
	private final Consumer<Set<Identifier>> onApplyCallback;

	private final List<CurationEntry> allEntries = new ArrayList<>();
	private final List<CurationEntry> filteredEntries = new ArrayList<>();
	private final Set<Identifier> selectedBlockIds = new LinkedHashSet<>();

	private TextFieldWidget searchField;
	private String searchQuery = "";

	private ButtonWidget selectAllButton;
	private ButtonWidget clearAllButton;
	private ButtonWidget applyButton;
	private ButtonWidget cancelButton;

	private int winX;
	private int winY;
	private int listX;
	private int listY;
	private int listW;
	private int listH;

	private double scrollOffset = 0.0D;
	private boolean draggingScrollbar = false;
	private boolean isClosed = false;
	private boolean applied = false;
	private final Set<Identifier> originalSelectedIds = new LinkedHashSet<>();

	public MiningCurationModalScreen(
		BlockPos pos1,
		BlockPos pos2,
		Set<Identifier> initialSelectedIds,
		Screen parentScreen,
		Consumer<Set<Identifier>> onApplyCallback
	) {
		super(Text.literal("Curate Mining Targets"));
		this.pos1 = pos1;
		this.pos2 = pos2;
		this.parentScreen = parentScreen;
		this.onApplyCallback = onApplyCallback;

		scanDetectedBlocks();

		if (initialSelectedIds != null && !initialSelectedIds.isEmpty()) {
			this.selectedBlockIds.addAll(initialSelectedIds);
			this.originalSelectedIds.addAll(initialSelectedIds);
		} else {
			// Default: ALL detected blocks checked by default
			for (CurationEntry entry : this.allEntries) {
				this.selectedBlockIds.add(entry.id());
			}
			this.originalSelectedIds.clear();
		}

		ClientMiningCaptureTracker.setCuratedBlockIds(this.selectedBlockIds);
		updateFilteredEntries();
	}

	public MiningCurationModalScreen(
		BlockPos pos1,
		BlockPos pos2,
		Screen parentScreen,
		Consumer<Set<Identifier>> onApplyCallback
	) {
		this(pos1, pos2, Collections.emptySet(), parentScreen, onApplyCallback);
	}

	private void scanDetectedBlocks() {
		this.allEntries.clear();
		if (this.pos1 == null || this.pos2 == null) {
			return;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null) {
			return;
		}

		int minX = Math.min(this.pos1.getX(), this.pos2.getX());
		int maxX = Math.max(this.pos1.getX(), this.pos2.getX());
		int minY = Math.min(this.pos1.getY(), this.pos2.getY());
		int maxY = Math.max(this.pos1.getY(), this.pos2.getY());
		int minZ = Math.min(this.pos1.getZ(), this.pos2.getZ());
		int maxZ = Math.max(this.pos1.getZ(), this.pos2.getZ());

		Map<Block, Integer> tally = new LinkedHashMap<>();
		BlockPos.Mutable m = new BlockPos.Mutable();

		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					m.set(x, y, z);
					BlockState state = mc.world.getBlockState(m);
					if (state != null && !state.isAir()) {
						float hardness = state.getHardness(mc.world, m);
						if (hardness >= 0.0F && state.getBlock() != null) {
							tally.merge(state.getBlock(), 1, Integer::sum);
						}
					}
				}
			}
		}

		for (Map.Entry<Block, Integer> e : tally.entrySet()) {
			Block block = e.getKey();
			Identifier id = Registries.BLOCK.getId(block);
			Item item = block.asItem();
			ItemStack icon;
			if (item != null && item != Items.AIR) {
				icon = new ItemStack(item);
			} else if (block == Blocks.WATER) {
				icon = new ItemStack(Items.WATER_BUCKET);
			} else if (block == Blocks.LAVA) {
				icon = new ItemStack(Items.LAVA_BUCKET);
			} else {
				icon = new ItemStack(block);
			}
			String name = block.getName().getString();
			this.allEntries.add(new CurationEntry(id, block, item, icon, name, e.getValue()));
		}

		// Sort descending by block count, then by block name
		this.allEntries.sort((a, b) -> {
			int cmp = Integer.compare(b.count(), a.count());
			if (cmp != 0) return cmp;
			return a.name().compareToIgnoreCase(b.name());
		});
	}

	public void updateFilteredEntries() {
		this.filteredEntries.clear();
		String q = this.searchQuery.trim().toLowerCase();
		for (CurationEntry e : this.allEntries) {
			if (q.isEmpty()
				|| e.name().toLowerCase().contains(q)
				|| e.id().toString().toLowerCase().contains(q)
				|| e.id().getPath().toLowerCase().contains(q)) {
				this.filteredEntries.add(e);
			}
		}
	}

	@Override
	protected void init() {
		super.init();

		this.winX = (this.width - WINDOW_WIDTH) / 2;
		this.winY = (this.height - WINDOW_HEIGHT) / 2;

		this.listX = this.winX + 16;
		this.listY = this.winY + 56;
		this.listW = WINDOW_WIDTH - 32;
		this.listH = 126;

		// 1. Search Bar
		this.searchField = new TextFieldWidget(
			this.textRenderer,
			this.winX + 16,
			this.winY + 34,
			160,
			18,
			Text.literal("Search Blocks")
		);
		this.searchField.setPlaceholder(Text.literal("§8Search blocks..."));
		this.searchField.setText(this.searchQuery);
		this.searchField.setChangedListener(text -> {
			this.searchQuery = text;
			updateFilteredEntries();
			this.scrollOffset = 0.0D;
		});
		this.addDrawableChild(this.searchField);

		// 2. Select All & Clear All buttons
		this.selectAllButton = ButtonWidget.builder(Text.literal("§a✔ All"), b -> selectAll(true))
			.dimensions(this.winX + 184, this.winY + 34, 74, 18)
			.tooltip(Tooltip.of(Text.literal("Mark all detected blocks for mining")))
			.build();
		this.addDrawableChild(this.selectAllButton);

		this.clearAllButton = ButtonWidget.builder(Text.literal("§c✖ Clear"), b -> selectAll(false))
			.dimensions(this.winX + 264, this.winY + 34, 80, 18)
			.tooltip(Tooltip.of(Text.literal("Uncheck all blocks")))
			.build();
		this.addDrawableChild(this.clearAllButton);

		// 3. Bottom Action Buttons (Clean row below selected status bar)
		int bottomY = this.winY + WINDOW_HEIGHT - 26;

		this.cancelButton = ButtonWidget.builder(Text.literal("§c✖ Cancel"), b -> this.close())
			.dimensions(this.winX + 172, bottomY, 78, 20)
			.tooltip(Tooltip.of(Text.literal("Discard curation changes and return")))
			.build();
		this.addDrawableChild(this.cancelButton);

		this.applyButton = ButtonWidget.builder(Text.literal("§a✔ Apply"), b -> applyAndClose())
			.dimensions(this.winX + 256, bottomY, 88, 20)
			.tooltip(Tooltip.of(Text.literal("Apply curated block targets and return")))
			.build();
		this.addDrawableChild(this.applyButton);
	}

	void selectAll(boolean select) {
		if (select) {
			for (CurationEntry e : this.allEntries) {
				this.selectedBlockIds.add(e.id());
			}
		} else {
			this.selectedBlockIds.clear();
		}
		ClientMiningCaptureTracker.setCuratedBlockIds(this.selectedBlockIds);
		if (this.client != null && this.client.getSoundManager() != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, select ? 1.2F : 0.8F));
		}
	}

	void toggleEntry(Identifier id) {
		if (this.selectedBlockIds.contains(id)) {
			this.selectedBlockIds.remove(id);
		} else {
			this.selectedBlockIds.add(id);
		}
		ClientMiningCaptureTracker.setCuratedBlockIds(this.selectedBlockIds);
		if (this.client != null && this.client.getSoundManager() != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	}

	void applyAndClose() {
		this.applied = true;
		if (this.client != null && this.client.getSoundManager() != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
		}

		// Fail-safe: if player unchecked every block, revert to mining all blocks in the area
		Set<Identifier> result = new LinkedHashSet<>(this.selectedBlockIds);
		if (result.isEmpty()) {
			for (CurationEntry e : this.allEntries) {
				result.add(e.id());
			}
		}

		ClientMiningCaptureTracker.setCuratedBlockIds(result);
		this.close();

		if (this.onApplyCallback != null) {
			this.onApplyCallback.accept(result);
		}
	}

	public int getCuratedBlockCount() {
		int total = 0;
		for (CurationEntry e : this.allEntries) {
			if (this.selectedBlockIds.contains(e.id())) {
				total += e.count();
			}
		}
		return total;
	}

	public int getTotalBlockCount() {
		int total = 0;
		for (CurationEntry e : this.allEntries) {
			total += e.count();
		}
		return total;
	}

	private int contentHeight() {
		return this.filteredEntries.size() * ROW_HEIGHT;
	}

	private double maxScroll() {
		return Math.max(0, contentHeight() - this.listH);
	}

	private void clampScroll() {
		this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll()));
	}

	private void scrollToMouse(double mouseY) {
		if (maxScroll() <= 0) return;
		double relativeY = mouseY - this.listY;
		double pct = Math.max(0.0D, Math.min(1.0D, relativeY / (double) this.listH));
		this.scrollOffset = pct * maxScroll();
		clampScroll();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			// Check if clicking scrollbar
			if (maxScroll() > 0) {
				int scrollbarX = this.listX + this.listW - SCROLLBAR_WIDTH;
				if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH && mouseY >= this.listY && mouseY <= this.listY + this.listH) {
					this.draggingScrollbar = true;
					scrollToMouse(mouseY);
					return true;
				}
			}

			// Check list item clicks
			if (mouseX >= this.listX && mouseX <= this.listX + this.listW && mouseY >= this.listY && mouseY <= this.listY + this.listH) {
				int clickedIdx = (int) ((mouseY - this.listY + this.scrollOffset) / ROW_HEIGHT);
				if (clickedIdx >= 0 && clickedIdx < this.filteredEntries.size()) {
					CurationEntry clicked = this.filteredEntries.get(clickedIdx);
					toggleEntry(clicked.id());
					return true;
				}
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			this.draggingScrollbar = false;
		}
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
		// Suppress background blur shader to preserve tactical world visibility
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);

		// Modal background plate & amber/gold border
		context.fill(this.winX, this.winY, this.winX + WINDOW_WIDTH, this.winY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(this.winX, this.winY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFE2B007);

		// Header plate
		context.fill(this.winX + 1, this.winY + 1, this.winX + WINDOW_WIDTH - 1, this.winY + 24, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§6✦ CURATE MINING TARGETS ✦"),
			this.width / 2,
			this.winY + 7,
			0xFFFFFF
		);

		Text escText = Text.literal("§e[Esc]");
		context.drawTextWithShadow(this.textRenderer, escText, this.winX + WINDOW_WIDTH - this.textRenderer.getWidth(escText) - 10, this.winY + 7, 0xAAAAAA);

		// Subheader instructions
		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("§7Choose which blocks to mine or don't mine:"),
			this.winX + 16,
			this.winY + 22,
			0xAAAAAA
		);

		// Table container
		context.fill(this.listX - 2, this.listY - 2, this.listX + this.listW + 2, this.listY + this.listH + 2, 0xFF151D28);
		context.drawBorder(this.listX - 2, this.listY - 2, this.listW + 4, this.listH + 4, 0xFF3A4E63);

		// Scissor clipping
		context.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);

		CurationEntry hoveredEntry = null;
		boolean hoveredSelected = false;

		if (this.filteredEntries.isEmpty()) {
			String emptyMsg = this.allEntries.isEmpty()
				? "§7No destructible blocks detected in area."
				: "§cNo blocks match search query.";
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal(emptyMsg),
				this.listX + this.listW / 2,
				this.listY + this.listH / 2 - 4,
				0xAAAAAA
			);
		} else {
			int startRow = Math.max(0, (int) (this.scrollOffset / ROW_HEIGHT));
			int endRow = Math.min(this.filteredEntries.size(), (int) ((this.scrollOffset + this.listH) / ROW_HEIGHT) + 1);

			for (int i = startRow; i < endRow; i++) {
				CurationEntry row = this.filteredEntries.get(i);
				int rowY = this.listY + (int) (i * ROW_HEIGHT - this.scrollOffset);

				boolean rowHovered = mouseX >= this.listX && mouseX < this.listX + this.listW
					&& mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;

				boolean selected = this.selectedBlockIds.contains(row.id());

				if (rowHovered) {
					hoveredEntry = row;
					hoveredSelected = selected;
				}

				int rowBg = rowHovered ? (selected ? 0x882A523A : 0x884A2A2A) : ((i % 2 == 0) ? 0x55111A24 : 0x33111A24);
				context.fill(this.listX, rowY, this.listX + this.listW, rowY + ROW_HEIGHT - 2, rowBg);

				// 1. Icon
				if (!row.icon().isEmpty()) {
					context.drawItem(row.icon(), this.listX + 4, rowY + 5);
				}

				// 2. Block Name
				String displayName = row.name();
				if (displayName.length() > 16) {
					displayName = displayName.substring(0, 15) + "…";
				}
				int nameColor = selected ? 0xFFFFFF : 0x888888;
				context.drawTextWithShadow(this.textRenderer, Text.literal((selected ? "§f" : "§8") + displayName), this.listX + 26, rowY + 5, nameColor);

				// 3. Block Count
				context.drawTextWithShadow(this.textRenderer, Text.literal("§7× §e" + row.count()), this.listX + 138, rowY + 5, 0xAAAAAA);

				// 4. Status Badge Button
				int badgeW = 76;
				int badgeH = 18;
				int badgeX = this.listX + this.listW - badgeW - 8;
				int badgeY = rowY + 3;

				boolean badgeHovered = mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY && mouseY <= badgeY + badgeH;

				if (selected) {
					context.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, badgeHovered ? 0xFF2A623A : 0xFF1B4228);
					context.drawBorder(badgeX, badgeY, badgeW, badgeH, badgeHovered ? 0xFF55FF55 : 0xFF2E8B57);
					context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§a✔ Mine"), badgeX + badgeW / 2, badgeY + 5, 0xFFFFFF);
				} else {
					context.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, badgeHovered ? 0xFF4A2A2A : 0xFF2A1B1B);
					context.drawBorder(badgeX, badgeY, badgeW, badgeH, badgeHovered ? 0xFFFF7777 : 0xFF663333);
					context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c✕ Don't Mine"), badgeX + badgeW / 2, badgeY + 5, 0xAAAAAA);
				}
			}
		}

		context.disableScissor();

		// Scrollbar
		if (maxScroll() > 0) {
			int scrollbarX = this.listX + this.listW - SCROLLBAR_WIDTH;
			context.fill(scrollbarX, this.listY, scrollbarX + SCROLLBAR_WIDTH, this.listY + this.listH, 0x55000000);

			double ratio = (double) this.listH / (double) contentHeight();
			int thumbH = Math.max(16, (int) (this.listH * ratio));
			int thumbY = this.listY + (int) ((this.listH - thumbH) * (this.scrollOffset / maxScroll()));

			context.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbH, this.draggingScrollbar ? 0xFFE2B007 : 0xFFAAAAAA);
		}

		// Summary bar in dedicated row above bottom action buttons (guarantees zero overlap with Cancel/Apply)
		int selectedTypes = this.selectedBlockIds.size();
		int totalTypes = this.allEntries.size();
		int curatedBlocks = getCuratedBlockCount();

		String summaryText;
		if (selectedTypes == 0) {
			summaryText = "§eSelected: §c0 Types §8(Will mine all blocks)";
		} else if (selectedTypes == totalTypes) {
			summaryText = "§eSelected: §aAll Types §8(§f" + curatedBlocks + " blocks to mine§8)";
		} else {
			summaryText = "§eSelected: §b" + selectedTypes + " / " + totalTypes + " Types §8(§a" + curatedBlocks + " blocks to mine§8)";
		}

		int summaryBarY = this.winY + 188;
		int summaryBarH = 18;
		context.fill(this.winX + 16, summaryBarY, this.winX + WINDOW_WIDTH - 16, summaryBarY + summaryBarH, 0x660B1017);
		context.drawBorder(this.winX + 16, summaryBarY, WINDOW_WIDTH - 32, summaryBarH, 0xFF2A3A4D);
		context.drawTextWithShadow(this.textRenderer, Text.literal(summaryText), this.winX + 22, summaryBarY + 5, 0xFFFFFF);

		super.render(context, mouseX, mouseY, delta);

		// Hover tooltip rendered on top of UI
		if (hoveredEntry != null) {
			List<Text> tooltip = new ArrayList<>();
			tooltip.add(Text.literal("§e" + hoveredEntry.name()));
			tooltip.add(Text.literal("§8" + hoveredEntry.id().toString()));
			tooltip.add(Text.literal("§7Volume in Area: §f" + hoveredEntry.count() + " blocks"));
			tooltip.add(Text.literal(hoveredSelected ? "§a✔ Status: Will Mine" : "§c✕ Status: Don't Mine"));
			tooltip.add(Text.literal("§8Click row or button to toggle"));
			context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			applyAndClose();
			return true;
		}

		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			this.close();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		this.isClosed = true;
		if (!this.applied) {
			ClientMiningCaptureTracker.setCuratedBlockIds(this.originalSelectedIds);
		}
		if (this.parentScreen != null && this.client != null) {
			this.client.setScreen(this.parentScreen);
		} else if (this.client != null) {
			super.close();
		}
	}

	public boolean isClosed() {
		return this.isClosed;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	public void setSearchQuery(String query) {
		this.searchQuery = query != null ? query : "";
		updateFilteredEntries();
	}

	public List<CurationEntry> getAllEntries() {
		return Collections.unmodifiableList(this.allEntries);
	}

	public List<CurationEntry> getFilteredEntries() {
		return Collections.unmodifiableList(this.filteredEntries);
	}

	public Set<Identifier> getSelectedBlockIds() {
		return Collections.unmodifiableSet(this.selectedBlockIds);
	}

	public void addEntryForTesting(CurationEntry entry) {
		this.allEntries.add(entry);
		this.selectedBlockIds.add(entry.id());
		updateFilteredEntries();
	}
}
