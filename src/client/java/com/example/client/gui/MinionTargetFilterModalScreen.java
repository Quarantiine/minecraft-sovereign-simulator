package com.example.client.gui;

import com.example.client.network.ModClientNetworking;
import com.example.client.targeting.ClientTargetFilterTracker;
import com.example.targeting.MinionTargetFilterManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
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
 * - Scrollable grid of every hostile mob type, each with its spawn egg icon, name, and a checkbox.
 * - Checked = minions may attack the mob on sight; unchecked = minions leave it alone.
 * - Quick [Select All] / [Clear All] controls and a live allowed counter.
 * - Actions: [Submit] sends an {@link com.example.network.UpdateTargetFilterPayload}; [Cancel] discards changes.
 * - Background blur suppression to preserve battlefield tactical visibility.
 */
public class MinionTargetFilterModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 372;
	public static final int WINDOW_HEIGHT = 300;
	private static final int COLUMNS = 3;
	private static final int CELL_HEIGHT = 24;
	private static final int SCROLLBAR_WIDTH = 6;

	private record Entry(Identifier id, Text name, ItemStack icon) {}

	private final Screen parentScreen;
	private final List<Identifier> candidateIds;
	private final Set<Identifier> allowedIds = new LinkedHashSet<>();

	private final List<Entry> entries = new ArrayList<>();

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
		this(parentScreen, MinionTargetFilterManager.getCandidateIds(), ClientTargetFilterTracker.getDisabled());
	}

	/**
	 * @param parentScreen  Screen to return to on submit/cancel (nullable).
	 * @param candidateIds  All selectable hostile mob type ids.
	 * @param disabledIds   Mob type ids currently forbidden (shown unchecked).
	 */
	public MinionTargetFilterModalScreen(Screen parentScreen, List<Identifier> candidateIds, Set<Identifier> disabledIds) {
		super(Text.translatable("gui.modid-mmcli-agent-modding.target_filter_modal.title"));
		this.parentScreen = parentScreen;
		this.candidateIds = new ArrayList<>(candidateIds);
		Set<Identifier> disabled = disabledIds != null ? disabledIds : Set.of();
		for (Identifier id : this.candidateIds) {
			if (!disabled.contains(id)) {
				this.allowedIds.add(id);
			}
		}
	}

	// ---- Selection logic (pure, unit-testable) ----

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
	 * Sends the filter to the server, updates the local cache optimistically, and closes the modal.
	 */
	public void submit() {
		List<Identifier> disabled = computeDisabled();
		this.submitted = true;
		try {
			ModClientNetworking.sendUpdateTargetFilter(disabled);
		} catch (Throwable ignored) {}
		ClientTargetFilterTracker.setDisabled(disabled);
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

		this.entries.clear();
		for (Identifier id : this.candidateIds) {
			EntityType<?> type = Registries.ENTITY_TYPE.get(id);
			this.entries.add(new Entry(id, type.getName(), iconFor(id, type)));
		}
		this.entries.sort(Comparator.comparing(e -> e.name().getString().toLowerCase()));

		this.winH = Math.min(WINDOW_HEIGHT, this.height - 16);
		this.winX = (this.width - WINDOW_WIDTH) / 2;
		this.winY = (this.height - this.winH) / 2;

		this.listX = this.winX + 12;
		this.listY = this.winY + 62;
		this.listW = WINDOW_WIDTH - 24 - SCROLLBAR_WIDTH - 2;
		this.listH = this.winH - 62 - 42;
		this.cellW = this.listW / COLUMNS;
		clampScroll();

		int btnY = this.winY + this.winH - 30;
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
			.tooltip(Tooltip.of(Text.literal("§aSave this target filter for all your minions")))
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
		SpawnEggItem egg = SpawnEggItem.forEntity(type);
		if (egg != null) {
			return new ItemStack(egg);
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
		return (this.entries.size() + COLUMNS - 1) / COLUMNS;
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
		return index < this.entries.size() ? index : -1;
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
				toggle(this.entries.get(index).id());
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
	protected void applyBlur(float delta) {
		// Disable background world blur post-processing shader while keeping screen darkening and UI elements crisp
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

		// Description + live counter
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Checked mobs are attacked on sight by §eAuto§7, §aSentinel §7& §cWarrior §7minions."), this.winX + 12, this.winY + 32, 0xAAAAAA);
		int allowed = getAllowedCount();
		int total = getTotalCount();
		context.drawTextWithShadow(this.textRenderer,
			Text.literal("§6Allowed: " + (allowed > 0 ? "§a" : "§c") + allowed + "§7/§f" + total + " §8| §7Retaliation & scepter pings are never blocked."),
			this.winX + 12, this.winY + 46, 0xFFD700);

		// List viewport
		context.fill(this.listX - 1, this.listY - 1, this.listX + this.listW + 1, this.listY + this.listH + 1, 0xFF0B1118);
		context.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
		int hoveredIndex = entryIndexAt(mouseX, mouseY);
		int firstRow = (int) (this.scrollOffset / CELL_HEIGHT);
		int lastRow = (int) ((this.scrollOffset + this.listH) / CELL_HEIGHT);
		for (int row = firstRow; row <= lastRow; row++) {
			for (int col = 0; col < COLUMNS; col++) {
				int index = row * COLUMNS + col;
				if (index >= this.entries.size()) {
					break;
				}
				renderEntry(context, this.entries.get(index), index == hoveredIndex,
					this.listX + col * this.cellW, this.listY + row * CELL_HEIGHT - (int) this.scrollOffset);
			}
		}
		context.disableScissor();

		renderScrollbar(context);

		super.render(context, mouseX, mouseY, delta);

		if (hoveredIndex >= 0) {
			Entry hovered = this.entries.get(hoveredIndex);
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
		context.drawItem(entry.icon(), x + 18, y + 4);
		String name = this.textRenderer.trimToWidth(entry.name().getString(), this.cellW - 42);
		context.drawTextWithShadow(this.textRenderer, Text.literal((allowed ? "§f" : "§7") + name), x + 38, y + 8, 0xFFFFFF);
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

	@Override
	public boolean shouldPause() {
		return false;
	}
}
