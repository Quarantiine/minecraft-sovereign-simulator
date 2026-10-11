package com.example.client.gui;

import com.example.client.network.ModClientNetworking;
import com.example.client.targeting.ClientTargetFilterTracker;
import com.example.targeting.MinionTargetFilterManager;
import com.example.targeting.TargetFilterPreset;
import com.example.targeting.TargetFilterPresetManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client modal GUI screen letting the commander choose which hostile mobs their AUTO, SENTINEL
 * and WARRIOR minions are allowed to attack on sight.
 *
 * Features:
 * - Real-time mob search filter (`[ 🔍 Search mobs... ]`) supporting entity display names and IDs.
 * - Named Preset management (`[ 📋 Presets ]`, `[ 💾 Save ]`, `[ ✖ Delete ]`) persisted across game restarts.
 * - Scrollable grid of candidate hostile mob types, each with its spawn egg icon, name, and a checkbox.
 * - Checked = minions may attack the mob on sight; unchecked = minions leave it alone.
 * - Quick [Select All] / [Clear All] controls and a live allowed counter.
 * - Actions: [Submit] applies to global or custom minion callback; [Cancel] discards changes.
 * - Background blur suppression to preserve battlefield tactical visibility.
 */
public class MinionTargetFilterModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 372;
	public static final int WINDOW_HEIGHT = 300;
	private static final int COLUMNS = 3;
	private static final int CELL_HEIGHT = 24;
	private static final int SCROLLBAR_WIDTH = 6;

	public record Entry(Identifier id, Text name, ItemStack icon) {}

	private final Screen parentScreen;
	private final List<Identifier> candidateIds;
	private final Set<Identifier> allowedIds = new LinkedHashSet<>();
	private final Consumer<List<Identifier>> onSubmitCallback;

	private final List<Entry> allEntries = new ArrayList<>();
	private final List<Entry> filteredEntries = new ArrayList<>();

	private List<TargetFilterPreset> availablePresets = new ArrayList<>();
	private int selectedPresetIndex = 0;

	private TextFieldWidget searchField;
	private String searchQuery = "";

	private ButtonWidget presetCycleButton;
	private ButtonWidget savePresetButton;
	private ButtonWidget deletePresetButton;

	private ButtonWidget selectAllButton;
	private ButtonWidget clearAllButton;
	private ButtonWidget submitButton;
	private ButtonWidget cancelButton;

	private int winX;
	private int winY;
	private int winH;
	private int listX;
	private int listY;
	private int listW;
	private int listH;
	private int cellW;

	private double scrollOffset = 0.0D;
	private boolean draggingScrollbar = false;
	private boolean closed = false;
	private boolean submitted = false;

	/**
	 * Opens the modal using every registered hostile mob type and the synchronized client filter.
	 *
	 * @param parentScreen Screen to return to on submit/cancel (nullable).
	 */
	public MinionTargetFilterModalScreen(Screen parentScreen) {
		this(parentScreen, MinionTargetFilterManager.getCandidateIds(), ClientTargetFilterTracker.getDisabled(), null);
	}

	/**
	 * @param parentScreen Screen to return to on submit/cancel (nullable).
	 * @param candidateIds All selectable hostile mob type ids.
	 * @param disabledIds  Mob type ids currently forbidden (shown unchecked).
	 */
	public MinionTargetFilterModalScreen(Screen parentScreen, List<Identifier> candidateIds, Set<Identifier> disabledIds) {
		this(parentScreen, candidateIds, disabledIds, null);
	}

	/**
	 * @param parentScreen     Screen to return to on submit/cancel (nullable).
	 * @param candidateIds     All selectable hostile mob type ids.
	 * @param disabledIds      Mob type ids currently forbidden (shown unchecked).
	 * @param onSubmitCallback Optional consumer invoked with disabled mob IDs on submit.
	 */
	public MinionTargetFilterModalScreen(
		Screen parentScreen,
		List<Identifier> candidateIds,
		Set<Identifier> disabledIds,
		Consumer<List<Identifier>> onSubmitCallback
	) {
		super(Text.translatable("gui.modid-mmcli-agent-modding.target_filter_modal.title"));
		this.parentScreen = parentScreen;
		this.candidateIds = new ArrayList<>(candidateIds != null ? candidateIds : List.of());
		this.onSubmitCallback = onSubmitCallback;
		Set<Identifier> disabled = disabledIds != null ? disabledIds : Set.of();
		for (Identifier id : this.candidateIds) {
			if (!disabled.contains(id)) {
				this.allowedIds.add(id);
			}
		}
		updateAvailablePresets();
	}

	private void ensureEntriesInitialized() {
		if (!this.allEntries.isEmpty()) {
			return;
		}
		for (Identifier id : this.candidateIds) {
			Text name = null;
			ItemStack icon = null;
			try {
				icon = ItemStack.EMPTY;
				EntityType<?> type = Registries.ENTITY_TYPE.get(id);
				if (type != null) {
					name = type.getName();
					icon = iconFor(id, type);
				}
			} catch (Throwable ignored) {}
			if (name == null) {
				name = Text.literal(id.getPath());
			}
			this.allEntries.add(new Entry(id, name, icon));
		}
		this.allEntries.sort(Comparator.comparing(e -> e.name().getString().toLowerCase()));
		filterEntries();
	}

	// ---- Selection & Filtering logic (pure, unit-testable) ----

	public boolean isAllowed(Identifier id) {
		return this.allowedIds.contains(id);
	}

	public void toggle(Identifier id) {
		if (id == null || !this.candidateIds.contains(id)) {
			return;
		}
		if (!this.allowedIds.remove(id)) {
			this.allowedIds.add(id);
		}
	}

	public void selectAll() {
		this.allowedIds.clear();
		this.allowedIds.addAll(this.candidateIds);
	}

	public void clearAll() {
		this.allowedIds.clear();
	}

	public int getAllowedCount() {
		return this.allowedIds.size();
	}

	public int getTotalCount() {
		return this.candidateIds.size();
	}

	public String getSearchQuery() {
		return this.searchQuery;
	}

	public void setSearchQuery(String query) {
		this.searchQuery = query != null ? query.trim() : "";
		ensureEntriesInitialized();
		filterEntries();
	}

	public List<Entry> getFilteredEntries() {
		ensureEntriesInitialized();
		return Collections.unmodifiableList(this.filteredEntries);
	}

	public int getFilteredEntriesCount() {
		ensureEntriesInitialized();
		return this.filteredEntries.size();
	}

	private void filterEntries() {
		this.filteredEntries.clear();
		if (this.searchQuery.isEmpty()) {
			this.filteredEntries.addAll(this.allEntries);
		} else {
			String q = this.searchQuery.toLowerCase();
			for (Entry entry : this.allEntries) {
				String nameStr = entry.name().getString().toLowerCase();
				String pathStr = entry.id().getPath().toLowerCase();
				if (nameStr.contains(q) || pathStr.contains(q)) {
					this.filteredEntries.add(entry);
				}
			}
		}
		this.scrollOffset = 0.0D;
		clampScroll();
	}

	// ---- Preset operations (pure, unit-testable) ----

	public void updateAvailablePresets() {
		this.availablePresets = new ArrayList<>(TargetFilterPresetManager.getInstance().getAllPresets(this.candidateIds));
		if (this.selectedPresetIndex >= this.availablePresets.size()) {
			this.selectedPresetIndex = 0;
		}
		updatePresetWidgets();
	}

	public List<TargetFilterPreset> getAllPresets() {
		if (this.availablePresets.isEmpty()) {
			updateAvailablePresets();
		}
		return Collections.unmodifiableList(this.availablePresets);
	}

	public String getSelectedPresetTitle() {
		if (this.availablePresets.isEmpty()) {
			updateAvailablePresets();
		}
		if (this.availablePresets.isEmpty()) {
			return "None";
		}
		int idx = Math.max(0, Math.min(this.selectedPresetIndex, this.availablePresets.size() - 1));
		return this.availablePresets.get(idx).title();
	}

	public boolean isCurrentPresetUserSaved() {
		if (this.availablePresets.isEmpty()) return false;
		String title = getSelectedPresetTitle();
		return TargetFilterPresetManager.getInstance().getUserPreset(title).isPresent();
	}

	public void cyclePreset(boolean backwards) {
		if (this.availablePresets.isEmpty()) {
			updateAvailablePresets();
		}
		if (this.availablePresets.isEmpty()) return;
		int size = this.availablePresets.size();
		if (backwards) {
			this.selectedPresetIndex = (this.selectedPresetIndex - 1 + size) % size;
		} else {
			this.selectedPresetIndex = (this.selectedPresetIndex + 1) % size;
		}
		TargetFilterPreset preset = this.availablePresets.get(this.selectedPresetIndex);
		applyPreset(preset);
		updatePresetWidgets();
	}

	public void applyPreset(TargetFilterPreset preset) {
		if (preset == null) return;
		this.allowedIds.clear();
		for (Identifier id : preset.allowedIds()) {
			if (this.candidateIds.contains(id)) {
				this.allowedIds.add(id);
			}
		}
	}

	public void applyPreset(String title) {
		if (title == null) return;
		if (this.availablePresets.isEmpty()) {
			updateAvailablePresets();
		}
		for (int i = 0; i < this.availablePresets.size(); i++) {
			if (this.availablePresets.get(i).title().equalsIgnoreCase(title.trim())) {
				this.selectedPresetIndex = i;
				applyPreset(this.availablePresets.get(i));
				updatePresetWidgets();
				return;
			}
		}
	}

	public void saveCurrentAsPreset(String title) {
		String cleanTitle = (title != null && !title.trim().isEmpty())
			? title.trim()
			: (!this.searchQuery.isEmpty() ? this.searchQuery : "Preset " + (TargetFilterPresetManager.getInstance().getUserPresets().size() + 1));
		TargetFilterPreset preset = TargetFilterPresetManager.getInstance().savePreset(cleanTitle, this.allowedIds);
		updateAvailablePresets();
		for (int i = 0; i < this.availablePresets.size(); i++) {
			if (this.availablePresets.get(i).title().equals(preset.title())) {
				this.selectedPresetIndex = i;
				break;
			}
		}
		if (this.searchField != null && !this.searchField.getText().isEmpty()) {
			this.searchField.setText("");
			setSearchQuery("");
		}
		updatePresetWidgets();
	}

	public boolean deleteCurrentPreset() {
		if (!isCurrentPresetUserSaved()) return false;
		String title = getSelectedPresetTitle();
		boolean deleted = TargetFilterPresetManager.getInstance().deletePreset(title);
		if (deleted) {
			updateAvailablePresets();
		}
		return deleted;
	}

	private void updatePresetWidgets() {
		if (this.presetCycleButton != null) {
			String title = getSelectedPresetTitle();
			this.presetCycleButton.setMessage(Text.literal("§6📋 " + truncate(title, 14)));
			this.presetCycleButton.setTooltip(Tooltip.of(Text.literal("§6Preset: §f" + title + "\n§eClick to cycle & apply preset\n§7Shift-Click to cycle backwards")));
		}
		if (this.deletePresetButton != null) {
			boolean isUser = isCurrentPresetUserSaved();
			this.deletePresetButton.active = isUser;
			this.deletePresetButton.setTooltip(Tooltip.of(Text.literal(isUser ? "§cDelete custom preset: " + getSelectedPresetTitle() : "§8Built-in presets cannot be deleted")));
		}
	}

	private static String truncate(String text, int maxLength) {
		if (text == null) return "";
		return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
	}

	/**
	 * @return The mob type ids that are currently unchecked (minions must NOT attack these on sight).
	 */
	public List<Identifier> computeDisabled() {
		List<Identifier> disabled = new ArrayList<>();
		for (Identifier id : this.candidateIds) {
			if (!this.allowedIds.contains(id)) {
				disabled.add(id);
			}
		}
		return disabled;
	}

	/**
	 * Sends the filter to the server or callback, updates local cache, and closes the modal.
	 */
	public void submit() {
		List<Identifier> disabled = computeDisabled();
		this.submitted = true;
		if (this.onSubmitCallback != null) {
			this.onSubmitCallback.accept(disabled);
		} else {
			try {
				ModClientNetworking.sendUpdateTargetFilter(disabled);
			} catch (Throwable ignored) {}
			ClientTargetFilterTracker.setDisabled(disabled);
		}
		this.close();
	}

	/**
	 * Discards any pending checkbox changes and closes the modal.
	 */
	public void cancel() {
		this.close();
	}

	// ---- Screen lifecycle ----

	@Override
	protected void init() {
		super.init();

		ensureEntriesInitialized();
		filterEntries();
		updateAvailablePresets();

		this.winH = Math.min(WINDOW_HEIGHT, this.height - 16);
		this.winX = (this.width - WINDOW_WIDTH) / 2;
		this.winY = (this.height - this.winH) / 2;

		int controlsY = this.winY + 30;

		// 1. Search text field
		this.searchField = new TextFieldWidget(this.textRenderer, this.winX + 12, controlsY, 110, 18, Text.literal("Search"));
		this.searchField.setPlaceholder(Text.literal("§8🔍 Search mobs..."));
		this.searchField.setText(this.searchQuery);
		this.searchField.setChangedListener(this::setSearchQuery);
		this.addDrawableChild(this.searchField);

		// 2. Preset cycler button
		this.presetCycleButton = ButtonWidget.builder(
			Text.literal("§6📋 " + truncate(getSelectedPresetTitle(), 14)),
			b -> {
				cyclePreset(Screen.hasShiftDown());
				if (this.client != null) {
					this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.1F));
				}
			}
		)
		.dimensions(this.winX + 126, controlsY, 140, 18)
		.build();
		this.addDrawableChild(this.presetCycleButton);

		// 3. Save current preset button
		this.savePresetButton = ButtonWidget.builder(
			Text.literal("§a💾 Save"),
			b -> {
				saveCurrentAsPreset(this.searchField != null ? this.searchField.getText() : null);
				if (this.client != null) {
					this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.4F));
				}
			}
		)
		.dimensions(this.winX + 270, controlsY, 50, 18)
		.tooltip(Tooltip.of(Text.literal("§aSave current selection as a preset\n§7Uses search box as title if typed")))
		.build();
		this.addDrawableChild(this.savePresetButton);

		// 4. Delete custom preset button
		this.deletePresetButton = ButtonWidget.builder(
			Text.literal("§c✖"),
			b -> {
				if (deleteCurrentPreset() && this.client != null) {
					this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 0.8F));
				}
			}
		)
		.dimensions(this.winX + 324, controlsY, 24, 18)
		.tooltip(Tooltip.of(Text.literal("§cDelete custom preset")))
		.build();
		this.addDrawableChild(this.deletePresetButton);

		updatePresetWidgets();

		// 5. Scrollable grid bounds
		this.listX = this.winX + 12;
		this.listY = this.winY + 68;
		this.listW = WINDOW_WIDTH - 24 - SCROLLBAR_WIDTH - 2;
		this.listH = this.winH - 68 - 36;
		this.cellW = this.listW / COLUMNS;
		clampScroll();

		// 6. Action buttons along bottom
		int btnY = this.winY + this.winH - 28;
		this.selectAllButton = ButtonWidget.builder(Text.literal("§a✔ Select All"), b -> selectAll())
			.dimensions(this.winX + 12, btnY, 78, 20)
			.tooltip(Tooltip.of(Text.literal("§aAllow minions to attack every listed mob on sight")))
			.build();
		this.clearAllButton = ButtonWidget.builder(Text.literal("§6✖ Clear All"), b -> clearAll())
			.dimensions(this.winX + 94, btnY, 78, 20)
			.tooltip(Tooltip.of(Text.literal("§6Forbid minions from attacking any listed mob on sight")))
			.build();
		this.submitButton = ButtonWidget.builder(Text.literal("§a✔ Submit"), b -> submit())
			.dimensions(this.winX + WINDOW_WIDTH - 12 - 70 - 6 - 84, btnY, 84, 20)
			.tooltip(Tooltip.of(Text.literal("§aSave target filter for minions")))
			.build();
		this.cancelButton = ButtonWidget.builder(Text.literal("§c✖ Cancel"), b -> cancel())
			.dimensions(this.winX + WINDOW_WIDTH - 12 - 70, btnY, 70, 20)
			.tooltip(Tooltip.of(Text.literal("§cDiscard changes and return")))
			.build();

		this.addDrawableChild(this.selectAllButton);
		this.addDrawableChild(this.clearAllButton);
		this.addDrawableChild(this.submitButton);
		this.addDrawableChild(this.cancelButton);
	}

	private static ItemStack iconFor(Identifier id, EntityType<?> type) {
		if (type != null) {
			SpawnEggItem egg = SpawnEggItem.forEntity(type);
			if (egg != null) {
				return new ItemStack(egg);
			}
		}
		Item fallback = switch (id.getPath()) {
			case "ender_dragon" -> Items.DRAGON_HEAD;
			case "wither" -> Items.WITHER_SKELETON_SKULL;
			case "giant" -> Items.ZOMBIE_HEAD;
			default -> Items.IRON_SWORD;
		};
		return new ItemStack(fallback);
	}

	// ---- Scrolling / hit testing ----

	private int rowCount() {
		return (this.filteredEntries.size() + COLUMNS - 1) / COLUMNS;
	}

	private int contentHeight() {
		return rowCount() * CELL_HEIGHT;
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

	private int entryIndexAt(double mouseX, double mouseY) {
		if (!isInsideList(mouseX, mouseY)) {
			return -1;
		}
		int col = (int) ((mouseX - this.listX) / this.cellW);
		int row = (int) ((mouseY - this.listY + this.scrollOffset) / CELL_HEIGHT);
		if (col < 0 || col >= COLUMNS || row < 0) {
			return -1;
		}
		int index = row * COLUMNS + col;
		return index < this.filteredEntries.size() ? index : -1;
	}

	private boolean isOverScrollbar(double mouseX, double mouseY) {
		int trackX = this.listX + this.listW + 2;
		return maxScroll() > 0 && mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
			&& mouseY >= this.listY && mouseY < this.listY + this.listH;
	}

	private void scrollToMouse(double mouseY) {
		double ratio = (mouseY - this.listY) / (double) this.listH;
		this.scrollOffset = Math.max(0.0D, Math.min(1.0D, ratio)) * maxScroll();
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
			int index = entryIndexAt(mouseX, mouseY);
			if (index >= 0) {
				toggle(this.filteredEntries.get(index).id());
				if (this.client != null) {
					this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				}
				return true;
			}
		}
		return false;
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
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		this.draggingScrollbar = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		this.scrollOffset -= verticalAmount * CELL_HEIGHT;
		clampScroll();
		return true;
	}

	// ---- Rendering ----

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void applyBlur(float delta) {
		// Suppress background world blur shader to preserve tactical visibility
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);

		// Modal background plate & amber/gold border
		context.fill(this.winX, this.winY, this.winX + WINDOW_WIDTH, this.winY + this.winH, 0xEE111822);
		context.drawBorder(this.winX, this.winY, WINDOW_WIDTH, this.winH, 0xFFE2B007);

		// Framed header
		context.fill(this.winX + 1, this.winY + 1, this.winX + WINDOW_WIDTH - 1, this.winY + 26, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c⚔ MINION TARGET FILTER ⚔"), this.width / 2, this.winY + 8, 0xFFFFFF);
		Text escText = Text.literal("§e[Esc] §7Cancel");
		context.drawTextWithShadow(this.textRenderer, escText, this.winX + WINDOW_WIDTH - this.textRenderer.getWidth(escText) - 10, this.winY + 8, 0xE0E0E0);

		// Live counts & active search status
		int allowed = getAllowedCount();
		int total = getTotalCount();
		int shown = getFilteredEntriesCount();
		String countText = "§6Allowed: " + (allowed > 0 ? "§a" : "§c") + allowed + "§7/§f" + total;
		if (shown < total) {
			countText += " §8| §eShowing: §f" + shown;
		}
		context.drawTextWithShadow(this.textRenderer, Text.literal(countText), this.winX + 12, this.winY + 54, 0xFFD700);

		// List viewport
		context.fill(this.listX - 1, this.listY - 1, this.listX + this.listW + 1, this.listY + this.listH + 1, 0xFF0B1118);
		context.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
		int hoveredIndex = entryIndexAt(mouseX, mouseY);
		int firstRow = (int) (this.scrollOffset / CELL_HEIGHT);
		int lastRow = (int) ((this.scrollOffset + this.listH) / CELL_HEIGHT);
		for (int row = firstRow; row <= lastRow; row++) {
			for (int col = 0; col < COLUMNS; col++) {
				int index = row * COLUMNS + col;
				if (index >= this.filteredEntries.size()) {
					break;
				}
				renderEntry(context, this.filteredEntries.get(index), index == hoveredIndex,
					this.listX + col * this.cellW, this.listY + row * CELL_HEIGHT - (int) this.scrollOffset);
			}
		}
		context.disableScissor();

		renderScrollbar(context);

		super.render(context, mouseX, mouseY, delta);

		if (hoveredIndex >= 0 && hoveredIndex < this.filteredEntries.size()) {
			Entry hovered = this.filteredEntries.get(hoveredIndex);
			List<Text> tooltip = new ArrayList<>();
			tooltip.add(hovered.name());
			tooltip.add(Text.literal("§8" + hovered.id()));
			tooltip.add(Text.literal(isAllowed(hovered.id()) ? "§a✔ Attacked on sight" : "§c✖ Left alone"));
			tooltip.add(Text.literal("§eClick to toggle"));
			context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
		}
	}

	private void renderEntry(DrawContext context, Entry entry, boolean hovered, int x, int y) {
		boolean allowed = isAllowed(entry.id());
		int w = this.cellW - 2;
		int h = CELL_HEIGHT - 2;
		int bg = hovered ? 0xCC2A3A52 : (allowed ? 0xAA1B3A2A : 0xAA1B2230);
		context.fill(x + 1, y + 1, x + 1 + w, y + 1 + h, bg);
		context.drawBorder(x + 1, y + 1, w, h, allowed ? 0xFF2E8B57 : 0xFF3A465A);

		// Checkbox
		int cbX = x + 5;
		int cbY = y + 7;
		context.fill(cbX, cbY, cbX + 10, cbY + 10, 0xFF0B1118);
		context.drawBorder(cbX, cbY, 10, 10, allowed ? 0xFF55FF55 : 0xFF8899AA);
		if (allowed) {
			context.fill(cbX + 2, cbY + 2, cbX + 8, cbY + 8, 0xFF55FF55);
		}

		// Mob icon & name
		if (entry.icon() != null) {
			context.drawItem(entry.icon(), x + 18, y + 4);
		}
		String name = this.textRenderer != null ? this.textRenderer.trimToWidth(entry.name().getString(), this.cellW - 42) : entry.name().getString();
		if (this.textRenderer != null) {
			context.drawTextWithShadow(this.textRenderer, Text.literal((allowed ? "§f" : "§7") + name), x + 38, y + 8, 0xFFFFFF);
		}
	}

	private void renderScrollbar(DrawContext context) {
		int trackX = this.listX + this.listW + 2;
		context.fill(trackX, this.listY, trackX + SCROLLBAR_WIDTH, this.listY + this.listH, 0xFF0B1118);
		double max = maxScroll();
		if (max <= 0) {
			return;
		}
		int thumbH = Math.max(16, (int) ((double) this.listH * this.listH / contentHeight()));
		int thumbY = this.listY + (int) ((this.listH - thumbH) * (this.scrollOffset / max));
		context.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, this.draggingScrollbar ? 0xFFFFD700 : 0xFFE2B007);
	}

	// ---- Input ----

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			submit();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			cancel();
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

	public boolean isSubmitted() {
		return this.submitted;
	}

	public ButtonWidget getSelectAllButton() {
		return this.selectAllButton;
	}

	public ButtonWidget getClearAllButton() {
		return this.clearAllButton;
	}

	public ButtonWidget getSubmitButton() {
		return this.submitButton;
	}

	public ButtonWidget getCancelButton() {
		return this.cancelButton;
	}

	public TextFieldWidget getSearchField() {
		return this.searchField;
	}

	public ButtonWidget getPresetCycleButton() {
		return this.presetCycleButton;
	}

	public ButtonWidget getSavePresetButton() {
		return this.savePresetButton;
	}

	public ButtonWidget getDeletePresetButton() {
		return this.deletePresetButton;
	}
}
