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

	public static final int WINDOW_WIDTH = 340;
	public static final int WINDOW_HEIGHT = 246;
	public static final int PAGE_SIZE = 4;

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
		.dimensions(startX + 120, pageControlsY, 20, 18)
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
		.dimensions(startX + 200, pageControlsY, 20, 18)
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
		.dimensions(startX + 16, actionY, 115, 22)
		.tooltip(Tooltip.of(Text.literal("§a🏗 Ready Blueprint for Construction\n§7Sets this blueprint on your Command Scepter and returns to the battlefield.")))
		.build();
		this.addDrawableChild(this.anchorBtn);

		this.rescanBtn = ButtonWidget.builder(Text.literal("§e↻ Rescan Live"), b -> {
			playClickSound();
			ModClientNetworking.sendRequestResourceEstimation(this.blueprintId);
			if (this.client != null && this.client.player != null) {
				this.client.player.sendMessage(Text.literal("§e↻ Rescanning inventory and minion backpacks..."), true);
			}
		})
		.dimensions(startX + 137, actionY, 105, 22)
		.tooltip(Tooltip.of(Text.literal("§e↻ Rescan Live Inventory\n§7Requests a refreshed server-side material scan across your bag and minion thralls.")))
		.build();
		this.addDrawableChild(this.rescanBtn);

		this.closeBtn = ButtonWidget.builder(Text.literal("§c✕ Close"), b -> this.close())
		.dimensions(startX + 248, actionY, 76, 22)
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

		String readinessSummary = "§fReadiness: §l" + readinessPct + "%§r §7(" + totalAvail + "/" + totalReq + "b) §8| §e" + minionCount + " Minions in 64m";
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(readinessSummary), this.width / 2, barY + 3, 0xFFFFFF);

		// 4. Quick Category Tally Row
		renderCategoryTallies(context, startX, startY + 49);

		// 5. Material Breakdown Table Header
		int tableHeaderY = startY + 63;
		context.fill(startX + 16, tableHeaderY, startX + WINDOW_WIDTH - 16, tableHeaderY + 12, 0xDD182230);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Material"), startX + 22, tableHeaderY + 2, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Req"), startX + 128, tableHeaderY + 2, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Player"), startX + 160, tableHeaderY + 2, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Minions"), startX + 205, tableHeaderY + 2, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§7Status / Delta"), startX + 252, tableHeaderY + 2, 0xAAAAAA);

		// 6. Paginated Material Rows
		List<ResourceEstimateEntry> entries = getEntries();
		int rowStartY = startY + 76;
		int rowHeight = 26;

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
					context.drawItem(displayStack, startX + 18, currentRowY + 2);
				}

				// Item name
				String itemName = !displayStack.isEmpty() ? displayStack.getName().getString() : entry.itemId();
				if (itemName.length() > 14) {
					itemName = itemName.substring(0, 13) + "…";
				}
				context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + itemName), startX + 38, currentRowY + 3, 0xFFFFFF);

				// Category subtitle under item name
				String catTag = getCategoryTag(item);
				context.drawTextWithShadow(this.textRenderer, Text.literal(catTag), startX + 38, currentRowY + 13, 0x888888);

				// Quantities
				context.drawTextWithShadow(this.textRenderer, Text.literal("§e" + entry.requiredCount()), startX + 130, currentRowY + 7, 0xFFFF55);
				context.drawTextWithShadow(this.textRenderer, Text.literal("§a" + entry.playerCount()), startX + 164, currentRowY + 7, 0x55FF55);
				context.drawTextWithShadow(this.textRenderer, Text.literal("§b" + entry.minionCount()), startX + 210, currentRowY + 7, 0x55FFFF);

				// Status & Delta Badge
				int itemDelta = entry.delta();
				if (entry.isSatisfied()) {
					String surplus = itemDelta > 0 ? " (+" + itemDelta + ")" : "";
					context.drawTextWithShadow(this.textRenderer, Text.literal("§a✔ Ready" + surplus), startX + 252, currentRowY + 7, 0x55FF55);
				} else if (entry.harvestable()) {
					context.drawTextWithShadow(this.textRenderer, Text.literal("§6⚒ Auto (" + itemDelta + ")"), startX + 252, currentRowY + 7, 0xFFA500);
				} else {
					context.drawTextWithShadow(this.textRenderer, Text.literal("§c✕ Need (" + itemDelta + ")"), startX + 252, currentRowY + 7, 0xFF5555);
				}

				// Tooltip on row hover
				if (isHovered) {
					List<Text> tooltipLines = new ArrayList<>();
					tooltipLines.add(Text.literal("§6✦ " + (!displayStack.isEmpty() ? displayStack.getName().getString() : entry.itemId())));
					tooltipLines.add(Text.literal("§eRequired: §f" + entry.requiredCount() + " blocks"));
					tooltipLines.add(Text.literal("§aPlayer Inventory: §f" + entry.playerCount()));
					tooltipLines.add(Text.literal("§bNearby Minion Backpacks: §f" + entry.minionCount() + " (across " + minionCount + " minions)"));
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

		// 7. Page Indicator
		int totalPages = getTotalPages();
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§7Page §f" + (this.page + 1) + "§7/§f" + totalPages),
			startX + 170,
			startY + 193,
			0xAAAAAA
		);

		super.render(context, mouseX, mouseY, delta);
	}

	private void renderCategoryTallies(DrawContext context, int startX, int startY) {
		int stoneReq = 0, stoneAvail = 0;
		int timberReq = 0, timberAvail = 0;
		int glassReq = 0, glassAvail = 0;
		int miscReq = 0, miscAvail = 0;

		List<ResourceEstimateEntry> entries = getEntries();
		for (ResourceEstimateEntry entry : entries) {
			Identifier id = Identifier.tryParse(entry.itemId());
			Item item = id != null ? Registries.ITEM.get(id) : null;
			int req = entry.requiredCount();
			int avail = Math.min(req, entry.totalAvailable());

			if (isStoneCategory(item)) {
				stoneReq += req;
				stoneAvail += avail;
			} else if (isTimberCategory(item)) {
				timberReq += req;
				timberAvail += avail;
			} else if (isGlassCategory(item)) {
				glassReq += req;
				glassAvail += avail;
			} else {
				miscReq += req;
				miscAvail += avail;
			}
		}

		int pillW = 73;
		renderCategoryPill(context, startX + 16, startY, "🪨 Stone", stoneAvail, stoneReq);
		renderCategoryPill(context, startX + 16 + pillW + 4, startY, "🪵 Timber", timberAvail, timberReq);
		renderCategoryPill(context, startX + 16 + (pillW + 4) * 2, startY, "🪟 Glass", glassAvail, glassReq);
		renderCategoryPill(context, startX + 16 + (pillW + 4) * 3, startY, "🌿 Organic", miscAvail, miscReq);
	}

	private void renderCategoryPill(DrawContext context, int x, int y, String label, int avail, int req) {
		int w = 73;
		int h = 11;
		int bg = req > 0 ? (avail >= req ? 0xAA1C3B24 : 0xAA2B231A) : 0x88182230;
		context.fill(x, y, x + w, y + h, bg);
		context.drawBorder(x, y, w, h, req > 0 ? (avail >= req ? 0xFF2ECC71 : 0xFFE2B007) : 0xFF3A4E63);

		String text = req > 0 ? (label + " " + avail + "/" + req) : (label + " -");
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + text), x + w / 2, y + 2, 0xCCCCCC);
	}

	private boolean isStoneCategory(Item item) {
		if (item == null) return false;
		return MinionHarvestingHelper.isQuarryResource(item)
			|| MinionHarvestingHelper.isStoneBrickResource(item)
			|| MinionHarvestingHelper.isSandstoneResource(item)
			|| MinionHarvestingHelper.isDeepslateDerivedResource(item)
			|| MinionHarvestingHelper.isPolishedStoneResource(item);
	}

	private boolean isTimberCategory(Item item) {
		if (item == null) return false;
		return MinionHarvestingHelper.isWoodResource(item)
			|| MinionHarvestingHelper.isWoodenDerivative(item);
	}

	private boolean isGlassCategory(Item item) {
		if (item == null) return false;
		return MinionHarvestingHelper.isGlassResource(item)
			|| Registries.ITEM.getId(item).getPath().contains("glass")
			|| Registries.ITEM.getId(item).getPath().contains("pane");
	}

	private String getCategoryTag(Item item) {
		if (isStoneCategory(item)) return "§8[Stone]";
		if (isTimberCategory(item)) return "§6[Timber]";
		if (isGlassCategory(item)) return "§b[Glass]";
		return "§7[Detail]";
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
