package com.example.client.gui;

import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.client.network.ModClientNetworking;
import com.example.client.resource.ClientResourceEstimatorTracker;
import com.example.entity.ai.logistics.MinionHarvestingHelper;
import com.example.item.custom.CommandScepterItem;
import com.example.network.ResourceEstimateEntry;
import com.example.network.SyncResourceEstimationPayload;
import java.util.ArrayList;
import java.util.List;
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
import org.lwjgl.glfw.GLFW;

/**
 * Client modal GUI screen rendering a live Material Bill of Materials (BOM) & Resource Estimator
 * for a blueprint before anchoring in survival mode.
 *
 * Calculates and displays required raw materials (stone, timber, glass, organic) against the player's
 * personal inventory and nearby minion backpacks within 64 meters, annotating builder harvestability.
 */
public class BlueprintResourceEstimatorModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 370;
	public static final int WINDOW_HEIGHT = 246;
	public static final int PAGE_SIZE = 5;

	private final String blueprintId;
	private final StructureBlueprint blueprint;
	private final ItemStack scepterStack;
	private final Screen parentScreen;

	private SyncResourceEstimationPayload estimation;
	private int page = 0;

	private ButtonWidget prevPageBtn;
	private ButtonWidget nextPageBtn;
	private ButtonWidget rescanBtn;
	private ButtonWidget anchorBtn;
	private ButtonWidget closeBtn;

	public BlueprintResourceEstimatorModalScreen(String blueprintId, ItemStack scepterStack, Screen parentScreen) {
		super(Text.literal("Material Bill of Materials (BOM)"));
		this.blueprintId = blueprintId != null ? blueprintId : "empty";
		this.blueprint = BlueprintRegistry.getOrDefault(this.blueprintId);
		this.scepterStack = scepterStack;
		this.parentScreen = parentScreen;
		this.estimation = ClientResourceEstimatorTracker.getEstimation(this.blueprintId);
	}

	@Override
	protected void init() {
		super.init();

		// Request fresh live server-authoritative calculation
		ModClientNetworking.sendRequestResourceEstimation(this.blueprintId);

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// Pagination controls
		int pageControlsY = startY + 188;
		this.prevPageBtn = ButtonWidget.builder(Text.literal("◀"), b -> {
			if (this.page > 0) {
				this.page--;
				updatePaginationButtons();
			}
		})
		.dimensions(startX + 130, pageControlsY, 20, 18)
		.tooltip(Tooltip.of(Text.literal("Previous Page")))
		.build();
		this.addDrawableChild(this.prevPageBtn);

		this.nextPageBtn = ButtonWidget.builder(Text.literal("▶"), b -> {
			int totalPages = getTotalPages();
			if (this.page + 1 < totalPages) {
				this.page++;
				updatePaginationButtons();
			}
		})
		.dimensions(startX + 220, pageControlsY, 20, 18)
		.tooltip(Tooltip.of(Text.literal("Next Page")))
		.build();
		this.addDrawableChild(this.nextPageBtn);

		// Action buttons
		int actionY = startY + 214;

		this.anchorBtn = ButtonWidget.builder(Text.literal("§a🏗 Select & Ready"), b -> {
			if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
				CommandScepterItem.setBlueprintId(this.scepterStack, this.blueprintId);
			}
			playClickSound();
			if (this.client != null && this.client.player != null) {
				int pct = this.estimation != null ? this.estimation.getReadinessPercentage() : 0;
				this.client.player.sendMessage(
					Text.literal("§6✦ Selected Blueprint: §b" + this.blueprint.getName() + " §7(" + pct + "% materials ready). Right-click ground to anchor!"),
					true
				);
			}
			this.close();
		})
		.dimensions(startX + 16, actionY, 125, 22)
		.tooltip(Tooltip.of(Text.literal("§a🏗 Ready Blueprint for Construction\n§7Sets this blueprint on your Command Scepter and returns to the battlefield.")))
		.build();
		this.addDrawableChild(this.anchorBtn);

		this.rescanBtn = ButtonWidget.builder(Text.literal("§e↻ Rescan Live"), b -> {
			playClickSound();
			ModClientNetworking.sendRequestResourceEstimation(this.blueprintId);
			if (this.client != null && this.client.player != null) {
				this.client.player.sendMessage(Text.literal("§e↻ Rescanning inventory, minion backpacks, and nearby chests..."), true);
			}
		})
		.dimensions(startX + 147, actionY, 115, 22)
		.tooltip(Tooltip.of(Text.literal("§e↻ Rescan Live Inventory\n§7Requests a refreshed server-side material scan across your bag, minion thralls, and nearby chests within 32m.")))
		.build();
		this.addDrawableChild(this.rescanBtn);

		this.closeBtn = ButtonWidget.builder(Text.literal("§c✕ Close"), b -> this.close())
		.dimensions(startX + 268, actionY, 86, 22)
		.tooltip(Tooltip.of(Text.literal("§c✕ Close Modal\n§7Return to previous screen.")))
		.build();
		this.addDrawableChild(this.closeBtn);

		updatePaginationButtons();
	}

	/**
	 * Callback invoked when a refreshed {@link SyncResourceEstimationPayload} packet arrives from the server.
	 */
	public void onEstimationUpdated(SyncResourceEstimationPayload updated) {
		if (updated != null && updated.blueprintId().equalsIgnoreCase(this.blueprintId)) {
			this.estimation = updated;
			int totalPages = getTotalPages();
			if (this.page >= totalPages) {
				this.page = Math.max(0, totalPages - 1);
			}
			updatePaginationButtons();
		}
	}

	private int getTotalPages() {
		List<ResourceEstimateEntry> entries = getEntries();
		if (entries.isEmpty()) return 1;
		return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private List<ResourceEstimateEntry> getEntries() {
		if (this.estimation != null && this.estimation.entries() != null) {
			return this.estimation.entries();
		}
		return List.of();
	}

	private void updatePaginationButtons() {
		int totalPages = getTotalPages();
		if (this.prevPageBtn != null) {
			this.prevPageBtn.active = this.page > 0;
		}
		if (this.nextPageBtn != null) {
			this.nextPageBtn.active = (this.page + 1) < totalPages;
		}
	}

	private void playClickSound() {
		if (this.client != null && this.client.world != null && this.client.player != null) {
			this.client.world.playSound(
				null,
				this.client.player.getX(),
				this.client.player.getY(),
				this.client.player.getZ(),
				SoundEvents.UI_BUTTON_CLICK.value(),
				SoundCategory.PLAYERS,
				0.8F,
				1.2F
			);
		}
	}

	@Override
	protected void applyBlur(float delta) {
		// Suppress background world blur shader to preserve tactical battlefield situational awareness
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// 1. Modal background plate & gold border
		context.fill(startX, startY, startX + WINDOW_WIDTH, startY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(startX, startY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFE2B007);

		// 2. Framed Header Plate
		context.fill(startX + 1, startY + 1, startX + WINDOW_WIDTH - 1, startY + 28, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§6✦ MATERIAL BILL OF MATERIALS (BOM) ✦"),
			this.width / 2,
			startY + 6,
			0xFFFFFF
		);

		String bpInfo = "§b" + this.blueprint.getName() + " §8[" + this.blueprint.getSizeX() + "x" +
			this.blueprint.getSizeY() + "x" + this.blueprint.getSizeZ() + " §e" + this.blueprint.getBlockCount() + "b§8]";
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(bpInfo), this.width / 2, startY + 17, 0xAAAAAA);

		Text escText = Text.literal("§e[Esc]");
		context.drawTextWithShadow(this.textRenderer, escText, startX + WINDOW_WIDTH - this.textRenderer.getWidth(escText) - 10, startY + 6, 0xAAAAAA);

		// 3. Overall Readiness Progress Bar
		int barX = startX + 16;
		int barY = startY + 32;
		int barWidth = WINDOW_WIDTH - 32;
		int barHeight = 13;

		int totalReq = this.estimation != null ? this.estimation.totalBlocks() : this.blueprint.getBlockCount();
		int totalAvail = this.estimation != null ? this.estimation.totalAvailable() : 0;
		int readinessPct = this.estimation != null ? this.estimation.getReadinessPercentage() : 0;
		int minionCount = this.estimation != null ? this.estimation.nearbyMinionsCount() : 0;

		context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF151D28);
		context.drawBorder(barX, barY, barWidth, barHeight, 0xFF3A4E63);

		int progressWidth = (int) ((barWidth - 2) * (readinessPct / 100.0D));
		int fillColor = readinessPct >= 100 ? 0xFF2ECC71 : (readinessPct >= 50 ? 0xFFF39C12 : 0xFFE74C3C);
		if (progressWidth > 0) {
			context.fill(barX + 1, barY + 1, barX + 1 + progressWidth, barY + barHeight - 1, fillColor);
		}

		int containerCount = this.estimation != null ? this.estimation.nearbyContainersCount() : 0;
		String readinessSummary = "§fReadiness: §l" + readinessPct + "%§r §7(" + totalAvail + "/" + totalReq + "b) §8| §e" + minionCount + " Minions §8| §6" + containerCount + " Chests";
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(readinessSummary), this.width / 2, barY + 3, 0xFFFFFF);

		// 4. Material Breakdown Table Header
		int tableHeaderY = startY + 49;
		context.fill(startX + 16, tableHeaderY, startX + WINDOW_WIDTH - 16, tableHeaderY + 12, 0xDD182230);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Material"), startX + 22, tableHeaderY + 2, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Req"), startX + 114, tableHeaderY + 2, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Player"), startX + 144, tableHeaderY + 2, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Minions"), startX + 184, tableHeaderY + 2, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Chests"), startX + 226, tableHeaderY + 2, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Status / Delta"), startX + 268, tableHeaderY + 2, 0xAAAAAA);

		// 5. Paginated Material Rows
		List<ResourceEstimateEntry> entries = getEntries();
		int rowStartY = startY + 63;
		int rowHeight = 24;

		if (entries.isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8[ No Materials Required / Blueprint Empty ]"), this.width / 2, startY + 115, 0x888888);
		} else {
			int startIndex = this.page * PAGE_SIZE;
			int endIndex = Math.min(startIndex + PAGE_SIZE, entries.size());

			for (int i = startIndex; i < endIndex; i++) {
				ResourceEstimateEntry entry = entries.get(i);
				int currentRowY = rowStartY + (i - startIndex) * rowHeight;
				boolean isHovered = mouseX >= startX + 16 && mouseX <= startX + WINDOW_WIDTH - 16 && mouseY >= currentRowY && mouseY < currentRowY + rowHeight - 2;

				int rowBg = isHovered ? 0x882A3E54 : ((i % 2 == 0) ? 0x55111A24 : 0x33111A24);
				context.fill(startX + 16, currentRowY, startX + WINDOW_WIDTH - 16, currentRowY + rowHeight - 2, rowBg);

				// Render Item Stack Icon
				Identifier itemIdentifier = Identifier.tryParse(entry.itemId());
				Item item = itemIdentifier != null ? Registries.ITEM.get(itemIdentifier) : null;
				ItemStack displayStack = item != null ? new ItemStack(item) : ItemStack.EMPTY;

				if (!displayStack.isEmpty()) {
					context.drawItem(displayStack, startX + 18, currentRowY + 3);
				}

				// Item name
				String itemName = !displayStack.isEmpty() ? displayStack.getName().getString() : entry.itemId();
				if (itemName.length() > 13) {
					itemName = itemName.substring(0, 12) + "…";
				}
				context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + itemName), startX + 38, currentRowY + 6, 0xFFFFFF);

				// Quantities
				context.drawTextWithShadow(this.textRenderer, Text.literal("§e" + entry.requiredCount()), startX + 116, currentRowY + 6, 0xFFFF55);
				context.drawTextWithShadow(this.textRenderer, Text.literal("§a" + entry.playerCount()), startX + 146, currentRowY + 6, 0x55FF55);
				context.drawTextWithShadow(this.textRenderer, Text.literal("§b" + entry.minionCount()), startX + 186, currentRowY + 6, 0x55FFFF);
				context.drawTextWithShadow(this.textRenderer, Text.literal("§6" + entry.chestCount()), startX + 228, currentRowY + 6, 0xFFAA00);

				// Status & Delta Badge
				int itemDelta = entry.delta();
				if (entry.isSatisfied()) {
					String surplus = itemDelta > 0 ? " (+" + itemDelta + ")" : "";
					context.drawTextWithShadow(this.textRenderer, Text.literal("§a✔ Ready" + surplus), startX + 268, currentRowY + 6, 0x55FF55);
				} else if (entry.harvestable()) {
					context.drawTextWithShadow(this.textRenderer, Text.literal("§6⚒ Auto (" + itemDelta + ")"), startX + 268, currentRowY + 6, 0xFFA500);
				} else {
					context.drawTextWithShadow(this.textRenderer, Text.literal("§c✕ Need (" + itemDelta + ")"), startX + 268, currentRowY + 6, 0xFF5555);
				}

				// Tooltip on row hover
				if (isHovered) {
					List<Text> tooltipLines = new ArrayList<>();
					tooltipLines.add(Text.literal("§6✦ " + (!displayStack.isEmpty() ? displayStack.getName().getString() : entry.itemId())));
					tooltipLines.add(Text.literal("§eRequired: §f" + entry.requiredCount() + " blocks"));
					tooltipLines.add(Text.literal("§aPlayer Inventory: §f" + entry.playerCount()));
					tooltipLines.add(Text.literal("§bNearby Minion Backpacks: §f" + entry.minionCount() + " (across " + minionCount + " minions)"));
					tooltipLines.add(Text.literal("§6Nearby Chests & Containers: §f" + entry.chestCount() + " (across " + containerCount + " containers in 32m)"));
					tooltipLines.add(Text.literal("§7Total Available: " + (entry.isSatisfied() ? "§a" : "§c") + entry.totalAvailable() + " / " + entry.requiredCount()));
					tooltipLines.add(Text.literal("§7Net Inventory Delta: " + (itemDelta >= 0 ? "§a+" + itemDelta + " (Surplus)" : "§c" + itemDelta + " (Deficit)")));
					if (entry.harvestable()) {
						tooltipLines.add(Text.literal("§6⚒ Harvestable: Builder minions will autonomously quarry/cultivate this."));
					} else {
						tooltipLines.add(Text.literal("§c⚠ Builder minions cannot autonomously acquire this resource."));
					}
					context.drawTooltip(this.textRenderer, tooltipLines, mouseX, mouseY);
				}
			}
		}

		// 6. Page Indicator
		int totalPages = getTotalPages();
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§7Page §f" + (this.page + 1) + "§7/§f" + totalPages),
			startX + 160,
			startY + 190,
			0xAAAAAA
		);

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

	@Override
	public boolean shouldPause() {
		return false;
	}
}
