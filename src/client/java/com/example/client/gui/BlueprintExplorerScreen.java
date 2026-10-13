package com.example.client.gui;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.client.network.ModClientNetworking;
import com.example.item.custom.CommandScepterItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Dedicated architectural GUI screen providing interactive 3D voxel inspection,
 * real-time text search, live specifications, and direct scepter selection for all blueprints.
 * <p>
 * Key Features:
 * <ul>
 *   <li><b>True 3D Rotated Voxel Engine:</b> Real-time 3D rotated quad projection with exact directional sunlight,
 *       6-directional neighbor occlusion culling, and Painter's algorithm depth sorting.</li>
 *   <li><b>Fullscreen / Maximize Mode:</b> One-click toggle (Tab / F) expanding the 3D viewport to fill the entire modal.</li>
 *   <li><b>Architectural Floor Slicer:</b> Vertical cutaway stepper allowing players to peel back roofs and inspect interior rooms.</li>
 *   <li><b>3D Camera Panning & Expanded Zoom:</b> Left-click drag rotates, right-click drag pans, and mouse wheel zooms up to 8x.</li>
 *   <li><b>Ground Foundation Grid & Cardinal Compass:</b> Visual footprint grid and real-time N/S/E/W orientation indicator.</li>
 *   <li><b>Live Search Bar:</b> Instant substring search across blueprint name, ID, and descriptions.</li>
 *   <li><b>Zero Text Cut-off:</b> Automatic text wrapping and ellipsis trimming ensuring pixel-perfect layout at any GUI scale.</li>
 * </ul>
 */
public class BlueprintExplorerScreen extends Screen {

	public static final int WINDOW_WIDTH = 424;
	public static final int WINDOW_HEIGHT = 262;
	public static final int LIST_PAGE_SIZE = 5;

	private final ItemStack scepterStack;
	private final Screen parentScreen;

	private String selectedBlueprintId;
	private StructureBlueprint selectedBlueprint;

	private TextFieldWidget searchField;
	private String searchQuery = "";
	private final List<StructureBlueprint> filteredBlueprints = new ArrayList<>();
	private int listPage = 0;

	private final List<ButtonWidget> blueprintListButtons = new ArrayList<>();
	private ButtonWidget prevListBtn;
	private ButtonWidget nextListBtn;
	private ButtonWidget selectBtn;
	private ButtonWidget renameBtn;
	private ButtonWidget remapBtn;
	private ButtonWidget bomBtn;
	private ButtonWidget deleteBtn;
	private ButtonWidget resetViewBtn;
	private ButtonWidget maximizeBtn;
	private ButtonWidget spinToggleBtn;
	private ButtonWidget sliceDownBtn;
	private ButtonWidget sliceResetBtn;
	private ButtonWidget sliceUpBtn;
	private ButtonWidget closeBtn;

	// 6-Directional Unit Cube Normals: Top, Bottom, East, West, South, North
	private static final int[][] NORMALS = {
		{0, 1, 0},   // Top
		{0, -1, 0},  // Bottom
		{1, 0, 0},   // East
		{-1, 0, 0},  // West
		{0, 0, 1},   // South
		{0, 0, -1}   // North
	};

	// High-Performance 3D Mesh Pre-Cache and Reusable Zero-Allocation Render Buffers
	private BlueprintMeshCache meshCache = null;
	private long[] sortBuffer = new long[2048];
	private float[] projQuadX = new float[2048 * 4];
	private float[] projQuadY = new float[2048 * 4];
	private int[] projColor = new int[2048];

	// 3D Orbital & Pan Camera Parameters
	private float yaw = 0.785F; // 45 degrees
	private float pitch = 0.45F; // ~26 degrees
	private float zoom = 1.0F;
	private float panX = 0.0F;
	private float panY = 0.0F;
	private boolean autoSpin = true;
	private boolean dragging = false;
	private boolean panning = false;
	private double lastDragX = 0;
	private double lastDragY = 0;
	private double lastPanX = 0;
	private double lastPanY = 0;

	// Fullscreen Viewport Mode & Architectural Floor Slicer
	private boolean isMaximized = false;
	private int sliceY = Integer.MAX_VALUE;

	// Deletion Confirmation Modal
	private StructureBlueprint pendingDeleteBlueprint = null;
	private ButtonWidget confirmDeleteBtn;
	private ButtonWidget cancelDeleteBtn;

	// Rename Modal
	private boolean isRenaming = false;
	private String renameError = null;
	private TextFieldWidget renameField;
	private ButtonWidget confirmRenameBtn;
	private ButtonWidget cancelRenameBtn;

	public BlueprintExplorerScreen(String initialBlueprintId, ItemStack scepterStack, Screen parentScreen) {
		super(Text.literal("3D Blueprint Explorer"));
		this.selectedBlueprintId = initialBlueprintId != null ? initialBlueprintId : "";
		this.scepterStack = scepterStack;
		this.parentScreen = parentScreen;
	}

	@Override
	protected void init() {
		super.init();

		int winX = (this.width - WINDOW_WIDTH) / 2;
		int winY = (this.height - WINDOW_HEIGHT) / 2;

		// 1. Search Bar Field (Left Pane Top)
		int searchX = winX + 14;
		int searchY = winY + 36;
		int searchW = 150;
		this.searchField = new TextFieldWidget(this.textRenderer, searchX, searchY, searchW, 18, Text.literal("Search"));
		this.searchField.setPlaceholder(Text.literal("§8🔍 Search blueprints..."));
		this.searchField.setText(this.searchQuery);
		this.searchField.setChangedListener(this::onSearchQueryChanged);
		this.searchField.visible = !this.isMaximized;
		this.addDrawableChild(this.searchField);

		// 2. Blueprint Card Slot Buttons (5 cards, vertical)
		this.blueprintListButtons.clear();
		int listStartY = winY + 60;
		int cardW = 150;
		int cardH = 32;

		for (int i = 0; i < LIST_PAGE_SIZE; i++) {
			final int slotIndex = i;
			int btnY = listStartY + i * (cardH + 4);
			ButtonWidget cardBtn = new CardSlotButton(searchX, btnY, cardW, cardH, b -> {
				int targetIdx = this.listPage * LIST_PAGE_SIZE + slotIndex;
				if (targetIdx >= 0 && targetIdx < this.filteredBlueprints.size()) {
					selectBlueprintCard(this.filteredBlueprints.get(targetIdx));
				}
			});
			cardBtn.visible = !this.isMaximized;
			this.blueprintListButtons.add(cardBtn);
			this.addDrawableChild(cardBtn);
		}

		// 3. List Pagination Controls (Below card list)
		int listNavY = winY + 236;
		this.prevListBtn = ButtonWidget.builder(Text.literal("◀"), b -> {
			if (this.listPage > 0) {
				this.listPage--;
				updateCardButtons();
			}
		})
		.dimensions(searchX, listNavY, 22, 18)
		.tooltip(Tooltip.of(Text.literal("Previous Page")))
		.build();
		this.prevListBtn.visible = !this.isMaximized;
		this.addDrawableChild(this.prevListBtn);

		this.nextListBtn = ButtonWidget.builder(Text.literal("▶"), b -> {
			int totalPages = getTotalListPages();
			if (this.listPage + 1 < totalPages) {
				this.listPage++;
				updateCardButtons();
			}
		})
		.dimensions(searchX + cardW - 22, listNavY, 22, 18)
		.tooltip(Tooltip.of(Text.literal("Next Page")))
		.build();
		this.nextListBtn.visible = !this.isMaximized;
		this.addDrawableChild(this.nextListBtn);

		// 4. Viewport Coordinates based on Maximized state
		int vpX = this.isMaximized ? (winX + 12) : (winX + 172);
		int vpY = winY + 36;
		int vpW = this.isMaximized ? (WINDOW_WIDTH - 24) : 238;
		int vpH = this.isMaximized ? 192 : 138;
		int btnBarY = winY + 236;

		// 5. 3D Viewport Toolbar Controls (Aligned to top right of 3D preview)
		this.resetViewBtn = ButtonWidget.builder(Text.literal("↻"), b -> reset3DCamera())
			.dimensions(vpX + vpW - 20, vpY + 4, 18, 16)
			.tooltip(Tooltip.of(Text.literal("§6Reset View Angle, Zoom & Pan")))
			.build();
		this.addDrawableChild(this.resetViewBtn);

		this.maximizeBtn = ButtonWidget.builder(Text.literal(this.isMaximized ? "🗗" : "⛶"), b -> toggleMaximize())
			.dimensions(vpX + vpW - 40, vpY + 4, 18, 16)
			.tooltip(Tooltip.of(Text.literal(this.isMaximized ? "§6Restore Split-Pane View (Tab/F)" : "§6Maximize 3D Viewport (Tab/F)")))
			.build();
		this.addDrawableChild(this.maximizeBtn);

		this.spinToggleBtn = ButtonWidget.builder(Text.literal(this.autoSpin ? "⏸" : "▶"), b -> {
			this.autoSpin = !this.autoSpin;
			this.spinToggleBtn.setMessage(Text.literal(this.autoSpin ? "⏸" : "▶"));
			this.spinToggleBtn.setTooltip(Tooltip.of(Text.literal(this.autoSpin ? "§6Pause Auto-Rotation (Space)" : "§6Resume Auto-Rotation (Space)")));
		})
		.dimensions(vpX + vpW - 60, vpY + 4, 18, 16)
		.tooltip(Tooltip.of(Text.literal(this.autoSpin ? "§6Pause Auto-Rotation (Space)" : "§6Resume Auto-Rotation (Space)")))
		.build();
		this.addDrawableChild(this.spinToggleBtn);

		this.sliceUpBtn = ButtonWidget.builder(Text.literal("▲"), b -> stepLayer(1))
			.dimensions(vpX + vpW - 80, vpY + 4, 18, 16)
			.tooltip(Tooltip.of(Text.literal("§6Raise Floor Cutaway Level")))
			.build();
		this.addDrawableChild(this.sliceUpBtn);

		this.sliceResetBtn = ButtonWidget.builder(Text.literal(getSliceLabel()), b -> resetLayerSlice())
			.dimensions(vpX + vpW - 138, vpY + 4, 56, 16)
			.tooltip(Tooltip.of(Text.literal("§6Architectural Floor Slicer\n§7Click to view all floors\n§eUse ▲ and ▼ to cut away upper floors")))
			.build();
		this.addDrawableChild(this.sliceResetBtn);

		this.sliceDownBtn = ButtonWidget.builder(Text.literal("▼"), b -> stepLayer(-1))
			.dimensions(vpX + vpW - 158, vpY + 4, 18, 16)
			.tooltip(Tooltip.of(Text.literal("§6Lower Floor Cutaway Level")))
			.build();
		this.addDrawableChild(this.sliceDownBtn);

		// 6. Action Buttons
		if (!this.isMaximized) {
			int vpStartX = vpX;
			int specY = winY + 180;

			// Rename button positioned cleanly in top-right of Specifications Card
			this.renameBtn = ButtonWidget.builder(Text.literal("§b✏ Rename"), b -> promptRenameCustomBlueprint())
				.dimensions(vpX + vpW - 58, specY + 3, 52, 14)
				.tooltip(Tooltip.of(Text.literal("§bRename this custom blueprint (R).")))
				.build();
			this.addDrawableChild(this.renameBtn);

			// Bottom Bar: 5 spacious, uncluttered action buttons
			this.selectBtn = ButtonWidget.builder(Text.literal("§a✔ Arm"), b -> applyAndCloseSelection())
				.dimensions(vpStartX, btnBarY, 48, 18)
				.tooltip(Tooltip.of(Text.literal("§aArm this blueprint on your Command Scepter.")))
				.build();
			this.addDrawableChild(this.selectBtn);

			this.remapBtn = ButtonWidget.builder(Text.literal("§6🎨 Remap"), b -> openPaletteRemapModal())
				.dimensions(vpStartX + 51, btnBarY, 54, 18)
				.tooltip(Tooltip.of(Text.literal("§6Remap the block palette of this blueprint (Overwrite or Save Copy).")))
				.build();
			this.addDrawableChild(this.remapBtn);

			this.bomBtn = ButtonWidget.builder(Text.literal("§e📋 BOM"), b -> openMaterialBomModal())
				.dimensions(vpStartX + 108, btnBarY, 44, 18)
				.tooltip(Tooltip.of(Text.literal("§eInspect required materials against player & minion inventories.")))
				.build();
			this.addDrawableChild(this.bomBtn);

			this.deleteBtn = ButtonWidget.builder(Text.literal("§c🗑 Del"), b -> promptDeleteCustomBlueprint())
				.dimensions(vpStartX + 155, btnBarY, 40, 18)
				.tooltip(Tooltip.of(Text.literal("§cDelete custom captured blueprint permanently.")))
				.build();
			this.addDrawableChild(this.deleteBtn);

			this.closeBtn = ButtonWidget.builder(Text.literal("§7Close"), b -> closeScreen())
				.dimensions(vpStartX + 198, btnBarY, 40, 18)
				.tooltip(Tooltip.of(Text.literal("Close explorer.")))
				.build();
			this.addDrawableChild(this.closeBtn);
		} else {
			int maxStartX = winX + 24;
			this.selectBtn = ButtonWidget.builder(Text.literal("§a✔ Arm"), b -> applyAndCloseSelection())
				.dimensions(maxStartX, btnBarY, 56, 18)
				.tooltip(Tooltip.of(Text.literal("§aArm this blueprint on your Command Scepter.")))
				.build();
			this.addDrawableChild(this.selectBtn);

			this.renameBtn = ButtonWidget.builder(Text.literal("§b✏ Rename"), b -> promptRenameCustomBlueprint())
				.dimensions(maxStartX + 60, btnBarY, 66, 18)
				.tooltip(Tooltip.of(Text.literal("§bRename this custom blueprint (R).")))
				.build();
			this.addDrawableChild(this.renameBtn);

			this.remapBtn = ButtonWidget.builder(Text.literal("§6🎨 Remap"), b -> openPaletteRemapModal())
				.dimensions(maxStartX + 130, btnBarY, 66, 18)
				.tooltip(Tooltip.of(Text.literal("§6Remap the block palette of this blueprint (Overwrite or Save Copy).")))
				.build();
			this.addDrawableChild(this.remapBtn);

			this.bomBtn = ButtonWidget.builder(Text.literal("§e📋 BOM"), b -> openMaterialBomModal())
				.dimensions(maxStartX + 200, btnBarY, 56, 18)
				.tooltip(Tooltip.of(Text.literal("§eInspect required materials against player & minion inventories.")))
				.build();
			this.addDrawableChild(this.bomBtn);

			this.deleteBtn = ButtonWidget.builder(Text.literal("§c🗑 Del"), b -> promptDeleteCustomBlueprint())
				.dimensions(maxStartX + 260, btnBarY, 52, 18)
				.tooltip(Tooltip.of(Text.literal("§cDelete custom captured blueprint permanently.")))
				.build();
			this.addDrawableChild(this.deleteBtn);

			this.closeBtn = ButtonWidget.builder(Text.literal("§7Close"), b -> closeScreen())
				.dimensions(winX + WINDOW_WIDTH - 64, btnBarY, 46, 18)
				.tooltip(Tooltip.of(Text.literal("Close explorer.")))
				.build();
			this.addDrawableChild(this.closeBtn);
		}

		// 7. Delete Confirmation Modal Buttons (initially invisible)
		int modalW = 220;
		int modalX = (this.width - modalW) / 2;
		int modalY = (this.height - 100) / 2;

		this.confirmDeleteBtn = ButtonWidget.builder(Text.literal("§cConfirm"), b -> confirmDeleteSelected())
			.dimensions(modalX + 16, modalY + 68, 88, 18)
			.build();
		this.confirmDeleteBtn.visible = this.pendingDeleteBlueprint != null;
		this.addDrawableChild(this.confirmDeleteBtn);

		this.cancelDeleteBtn = ButtonWidget.builder(Text.literal("Cancel"), b -> cancelDelete())
			.dimensions(modalX + 116, modalY + 68, 88, 18)
			.build();
		this.cancelDeleteBtn.visible = this.pendingDeleteBlueprint != null;
		this.addDrawableChild(this.cancelDeleteBtn);

		// 8. Rename Modal Controls (initially invisible)
		int renameModalW = 240;
		int renameModalH = 104;
		int renameModalX = (this.width - renameModalW) / 2;
		int renameModalY = (this.height - renameModalH) / 2;

		String curName = (this.renameField != null && this.isRenaming) ? this.renameField.getText()
			: (this.selectedBlueprint != null ? this.selectedBlueprint.getName() : "");
		this.renameField = new TextFieldWidget(this.textRenderer, renameModalX + 16, renameModalY + 40, renameModalW - 32, 18, Text.literal("Blueprint Name"));
		this.renameField.setMaxLength(64);
		this.renameField.setText(curName);
		this.renameField.visible = this.isRenaming;
		this.addDrawableChild(this.renameField);

		this.confirmRenameBtn = ButtonWidget.builder(Text.literal("§a✔ Save"), b -> confirmRename())
			.dimensions(renameModalX + 16, renameModalY + 76, 98, 18)
			.tooltip(Tooltip.of(Text.literal("§aSave updated blueprint name.")))
			.build();
		this.confirmRenameBtn.visible = this.isRenaming;
		this.addDrawableChild(this.confirmRenameBtn);

		this.cancelRenameBtn = ButtonWidget.builder(Text.literal("§7Cancel"), b -> cancelRename())
			.dimensions(renameModalX + 126, renameModalY + 76, 98, 18)
			.tooltip(Tooltip.of(Text.literal("Cancel renaming.")))
			.build();
		this.cancelRenameBtn.visible = this.isRenaming;
		this.addDrawableChild(this.cancelRenameBtn);

		// Refresh filtered dataset and button state
		refreshFilteredDataset();
	}

	private void toggleMaximize() {
		this.isMaximized = !this.isMaximized;
		this.clearAndInit();
	}

	private void reset3DCamera() {
		this.yaw = 0.785F;
		this.pitch = 0.45F;
		this.zoom = 1.0F;
		this.panX = 0.0F;
		this.panY = 0.0F;
		this.sliceY = Integer.MAX_VALUE;
		this.autoSpin = true;
		updateLayerButtons();
	}

	private boolean hasValidBlocks() {
		return this.selectedBlueprint != null && this.selectedBlueprint.getBlocks() != null && !this.selectedBlueprint.getBlocks().isEmpty();
	}

	private void stepLayer(int delta) {
		if (!hasValidBlocks()) return;
		int minY = getBlueprintMinY();
		int maxY = getBlueprintMaxY();
		if (this.sliceY == Integer.MAX_VALUE) {
			this.sliceY = maxY;
		}
		this.sliceY = MathHelper.clamp(this.sliceY + delta, minY, maxY);
		updateLayerButtons();
	}

	private void resetLayerSlice() {
		this.sliceY = Integer.MAX_VALUE;
		updateLayerButtons();
	}

	private int getBlueprintMinY() {
		if (this.selectedBlueprint == null || this.selectedBlueprint.getBoundingBox() == null) return 0;
		return this.selectedBlueprint.getBoundingBox().getMinY();
	}

	private int getBlueprintMaxY() {
		if (this.selectedBlueprint == null || this.selectedBlueprint.getBoundingBox() == null) return 0;
		return this.selectedBlueprint.getBoundingBox().getMaxY();
	}

	private String getSliceLabel() {
		if (!hasValidBlocks()) {
			return "🏢 All";
		}
		int minY = getBlueprintMinY();
		int maxY = getBlueprintMaxY();
		int total = Math.max(1, maxY - minY + 1);
		if (this.sliceY == Integer.MAX_VALUE || this.sliceY >= maxY) {
			return "🏢 All";
		}
		int curLevel = MathHelper.clamp(this.sliceY - minY + 1, 1, total);
		return "🏢 L:" + curLevel + "/" + total;
	}

	private void updateLayerButtons() {
		if (this.sliceResetBtn != null) {
			this.sliceResetBtn.setMessage(Text.literal(getSliceLabel()));
		}
	}

	private void onSearchQueryChanged(String query) {
		this.searchQuery = query != null ? query.trim() : "";
		this.listPage = 0;
		refreshFilteredDataset();
	}

	private void refreshFilteredDataset() {
		List<StructureBlueprint> all = new ArrayList<>(BlueprintRegistry.getAll());
		this.filteredBlueprints.clear();

		if (this.searchQuery.isEmpty()) {
			this.filteredBlueprints.addAll(all);
		} else {
			String lowerQuery = this.searchQuery.toLowerCase(Locale.ROOT);
			for (StructureBlueprint bp : all) {
				boolean matchId = bp.getId().toLowerCase(Locale.ROOT).contains(lowerQuery);
				boolean matchName = bp.getName().toLowerCase(Locale.ROOT).contains(lowerQuery);
				boolean matchDesc = bp.getDescription() != null && bp.getDescription().toLowerCase(Locale.ROOT).contains(lowerQuery);
				if (matchId || matchName || matchDesc) {
					this.filteredBlueprints.add(bp);
				}
			}
		}

		// Ensure current selection is valid
		if (this.selectedBlueprint == null || !this.filteredBlueprints.contains(this.selectedBlueprint)) {
			if (!this.filteredBlueprints.isEmpty()) {
				StructureBlueprint match = null;
				for (StructureBlueprint bp : this.filteredBlueprints) {
					if (bp.getId().equalsIgnoreCase(this.selectedBlueprintId)) {
						match = bp;
						break;
					}
				}
				this.selectedBlueprint = match != null ? match : this.filteredBlueprints.get(0);
				this.selectedBlueprintId = this.selectedBlueprint.getId();
			} else {
				this.selectedBlueprint = null;
				this.selectedBlueprintId = "";
			}
		}

		// Ensure page index is clamped
		int totalPages = getTotalListPages();
		if (this.listPage >= totalPages) {
			this.listPage = Math.max(0, totalPages - 1);
		}

		this.sliceY = Integer.MAX_VALUE;
		this.meshCache = null;
		updateLayerButtons();
		updateCardButtons();
		updateActionButtons();
	}

	private int getTotalListPages() {
		return Math.max(1, (this.filteredBlueprints.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
	}

	private void selectBlueprintCard(StructureBlueprint bp) {
		if (bp == null) return;
		this.selectedBlueprint = bp;
		this.selectedBlueprintId = bp.getId();
		this.sliceY = Integer.MAX_VALUE;
		this.meshCache = null;
		updateLayerButtons();
		updateCardButtons();
		updateActionButtons();
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
		}
	}

	public void refreshAndSelect(String blueprintId) {
		if (blueprintId != null) {
			this.selectedBlueprintId = blueprintId;
		}
		refreshFilteredDataset();
	}

	private void updateCardButtons() {
		int startIndex = this.listPage * LIST_PAGE_SIZE;

		for (int i = 0; i < LIST_PAGE_SIZE && i < this.blueprintListButtons.size(); i++) {
			ButtonWidget btn = this.blueprintListButtons.get(i);
			int itemIndex = startIndex + i;

			if (itemIndex < this.filteredBlueprints.size()) {
				StructureBlueprint bp = this.filteredBlueprints.get(itemIndex);

				btn.visible = !this.isMaximized;
				btn.active = this.pendingDeleteBlueprint == null && !this.isRenaming;
				btn.setMessage(Text.empty());

				List<Text> tooltipLines = new ArrayList<>();
				tooltipLines.add(Text.literal("§6✦ " + bp.getName()));
				tooltipLines.add(Text.literal("§7ID: §8" + bp.getId()));
				net.minecraft.util.math.BlockBox bpBox = bp.getBoundingBox();
				tooltipLines.add(Text.literal("§eSize: §f" + bpBox.getBlockCountX() + "×" + bpBox.getBlockCountY() + "×" + bpBox.getBlockCountZ()));
				tooltipLines.add(Text.literal("§bTotal Blocks: §f" + bp.getBlockCount()));
				if (bp.getDescription() != null && !bp.getDescription().isBlank()) {
					tooltipLines.add(Text.literal("§8" + bp.getDescription()));
				}
				StringBuilder sb = new StringBuilder();
				for (int t = 0; t < tooltipLines.size(); t++) {
					if (t > 0) sb.append("\n");
					sb.append(tooltipLines.get(t).getString());
				}
				btn.setTooltip(Tooltip.of(Text.literal(sb.toString())));
			} else {
				btn.visible = false;
				btn.active = false;
			}
		}

		if (this.prevListBtn != null) {
			this.prevListBtn.visible = !this.isMaximized;
			this.prevListBtn.active = this.pendingDeleteBlueprint == null && !this.isRenaming && this.listPage > 0;
		}
		if (this.nextListBtn != null) {
			this.nextListBtn.visible = !this.isMaximized;
			this.nextListBtn.active = this.pendingDeleteBlueprint == null && !this.isRenaming && (this.listPage + 1) < getTotalListPages();
		}
	}

	private void updateActionButtons() {
		boolean modalOpen = this.pendingDeleteBlueprint != null || this.isRenaming;
		boolean hasSelection = this.selectedBlueprint != null && !this.selectedBlueprint.getId().equalsIgnoreCase("empty");
		boolean isCustom = hasSelection && BlueprintRegistry.isCustom(this.selectedBlueprint.getId());

		if (this.selectBtn != null) {
			this.selectBtn.active = !modalOpen && hasSelection;
		}
		if (this.renameBtn != null) {
			this.renameBtn.visible = !this.isMaximized ? (hasSelection && isCustom) : true;
			this.renameBtn.active = !modalOpen && isCustom;
		}
		if (this.remapBtn != null) {
			this.remapBtn.active = !modalOpen && hasSelection && hasValidBlocks();
		}
		if (this.bomBtn != null) {
			this.bomBtn.active = !modalOpen && hasSelection;
		}
		if (this.deleteBtn != null) {
			this.deleteBtn.active = !modalOpen && isCustom;
		}
		if (this.resetViewBtn != null) {
			this.resetViewBtn.active = !modalOpen;
		}
		if (this.maximizeBtn != null) {
			this.maximizeBtn.active = !modalOpen;
		}
		if (this.spinToggleBtn != null) {
			this.spinToggleBtn.active = !modalOpen;
		}
		if (this.sliceUpBtn != null) {
			this.sliceUpBtn.active = !modalOpen && hasSelection;
		}
		if (this.sliceResetBtn != null) {
			this.sliceResetBtn.active = !modalOpen && hasSelection;
		}
		if (this.sliceDownBtn != null) {
			this.sliceDownBtn.active = !modalOpen && hasSelection;
		}
		if (this.closeBtn != null) {
			this.closeBtn.active = !modalOpen;
		}
	}

	private void applyAndCloseSelection() {
		if (this.selectedBlueprint != null && this.client != null) {
			if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
				CommandScepterItem.setBlueprintId(this.scepterStack, this.selectedBlueprint.getId());
			}
			ModClientNetworking.sendRequestResourceEstimation(this.selectedBlueprint.getId());

			if (this.parentScreen instanceof CommandScepterScreen scepterScreen) {
				scepterScreen.selectBlueprintFromExplorer(this.selectedBlueprint.getId());
				this.client.setScreen(scepterScreen);
			} else {
				this.client.setScreen(null);
			}
		}
	}

	private void openPaletteRemapModal() {
		if (this.selectedBlueprint != null && this.client != null) {
			this.client.setScreen(new BlueprintPaletteRemapModalScreen(
				null,
				null,
				this.selectedBlueprint,
				null,
				this,
				replacements -> {
					if (replacements != null && !replacements.isEmpty()) {
						if (this.client != null) {
							this.client.setScreen(new BlueprintSaveChoiceModalScreen(
								this.selectedBlueprint,
								replacements,
								this.scepterStack,
								this
							));
						}
					}
				}
			));
		}
	}

	private void openMaterialBomModal() {
		if (this.selectedBlueprint != null && this.client != null) {
			this.client.setScreen(new BlueprintResourceEstimatorModalScreen(
				this.selectedBlueprint.getId(),
				this.scepterStack,
				this
			));
		}
	}

	private void promptDeleteCustomBlueprint() {
		if (this.selectedBlueprint == null || !BlueprintRegistry.isCustom(this.selectedBlueprint.getId())) {
			return;
		}
		this.pendingDeleteBlueprint = this.selectedBlueprint;
		this.confirmDeleteBtn.visible = true;
		this.cancelDeleteBtn.visible = true;
		updateCardButtons();
		updateActionButtons();
	}

	private void confirmDeleteSelected() {
		if (this.pendingDeleteBlueprint != null && this.client != null) {
			String idToDelete = this.pendingDeleteBlueprint.getId();
			ModClientNetworking.sendDeleteCustomBlueprint(idToDelete);
			BlueprintRegistry.unregisterCustomBlueprint(idToDelete);

			this.pendingDeleteBlueprint = null;
			this.confirmDeleteBtn.visible = false;
			this.cancelDeleteBtn.visible = false;

			refreshFilteredDataset();
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8F));
		}
	}

	private void cancelDelete() {
		this.pendingDeleteBlueprint = null;
		this.confirmDeleteBtn.visible = false;
		this.cancelDeleteBtn.visible = false;
		updateCardButtons();
		updateActionButtons();
	}

	private void promptRenameCustomBlueprint() {
		if (this.selectedBlueprint == null || !BlueprintRegistry.isCustom(this.selectedBlueprint.getId())) {
			return;
		}
		this.isRenaming = true;
		this.renameError = null;
		if (this.renameField != null) {
			this.renameField.setText(this.selectedBlueprint.getName());
			this.renameField.visible = true;
			this.renameField.setFocused(true);
			this.setFocused(this.renameField);
		}
		if (this.confirmRenameBtn != null) {
			this.confirmRenameBtn.visible = true;
		}
		if (this.cancelRenameBtn != null) {
			this.cancelRenameBtn.visible = true;
		}
		if (this.searchField != null) {
			this.searchField.setFocused(false);
		}
		updateCardButtons();
		updateActionButtons();
	}

	private void confirmRename() {
		if (!this.isRenaming || this.selectedBlueprint == null || this.client == null) {
			return;
		}
		String newName = this.renameField != null ? this.renameField.getText().trim() : "";
		if (newName.isBlank()) {
			this.renameError = "Name cannot be empty";
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8F));
			return;
		}

		StructureBlueprint renamed = this.selectedBlueprint.withName(newName);
		ModClientNetworking.sendCreateCustomBlueprint(renamed);
		BlueprintRegistry.registerCustomBlueprint(renamed, false);

		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			if (this.selectedBlueprint.getId().equalsIgnoreCase(CommandScepterItem.getBlueprintId(this.scepterStack))) {
				CommandScepterItem.setBlueprintId(this.scepterStack, renamed.getId());
			}
		}

		this.selectedBlueprint = renamed;
		this.selectedBlueprintId = renamed.getId();
		cancelRename();

		refreshFilteredDataset();
		this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2F));
	}

	private void cancelRename() {
		this.isRenaming = false;
		this.renameError = null;
		if (this.renameField != null) {
			this.renameField.visible = false;
			this.renameField.setFocused(false);
		}
		if (this.confirmRenameBtn != null) {
			this.confirmRenameBtn.visible = false;
		}
		if (this.cancelRenameBtn != null) {
			this.cancelRenameBtn.visible = false;
		}
		this.setFocused(null);
		updateCardButtons();
		updateActionButtons();
	}

	private void closeScreen() {
		if (this.client != null) {
			this.client.setScreen(this.parentScreen);
		}
	}

	@Override
	public void close() {
		closeScreen();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.isRenaming) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				cancelRename();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				confirmRename();
				return true;
			}
			if (this.renameField != null && this.renameField.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
			return true;
		}

		if (this.pendingDeleteBlueprint != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				cancelDelete();
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (this.isMaximized) {
				toggleMaximize();
				return true;
			}
			closeScreen();
			return true;
		}

		// Fullscreen toggle hotkey: Tab or F (when search field is not active)
		if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_F) {
			if (this.searchField == null || !this.searchField.isFocused()) {
				toggleMaximize();
				return true;
			}
		}

		// Rename hotkey: R (when search field is not active and custom blueprint selected)
		if (keyCode == GLFW.GLFW_KEY_R) {
			if ((this.searchField == null || !this.searchField.isFocused()) && this.renameBtn != null && this.renameBtn.active) {
				promptRenameCustomBlueprint();
				return true;
			}
		}

		// Auto-spin toggle hotkey: Space
		if (keyCode == GLFW.GLFW_KEY_SPACE) {
			if (this.searchField == null || !this.searchField.isFocused()) {
				this.autoSpin = !this.autoSpin;
				if (this.spinToggleBtn != null) {
					this.spinToggleBtn.setMessage(Text.literal(this.autoSpin ? "⏸" : "▶"));
				}
				return true;
			}
		}

		if (this.searchField != null && this.searchField.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.pendingDeleteBlueprint != null || this.isRenaming) {
			if (this.isRenaming && this.renameField != null && this.renameField.mouseClicked(mouseX, mouseY, button)) {
				this.setFocused(this.renameField);
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		// 1. Allow any active GUI buttons or widgets to consume the click first
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}

		int winX = (this.width - WINDOW_WIDTH) / 2;
		int winY = (this.height - WINDOW_HEIGHT) / 2;
		int vpX = this.isMaximized ? (winX + 12) : (winX + 172);
		int vpY = winY + 36;
		int vpW = this.isMaximized ? (WINDOW_WIDTH - 24) : 238;
		int vpH = this.isMaximized ? 192 : 138;

		// 2. Check if click occurred inside empty 3D Viewport space
		if (mouseX >= vpX && mouseX <= vpX + vpW && mouseY >= vpY && mouseY <= vpY + vpH) {
			if (this.searchField != null) {
				this.searchField.setFocused(false);
			}
			this.setFocused(null);

			// Left click -> Rotate
			if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
				this.dragging = true;
				this.lastDragX = mouseX;
				this.lastDragY = mouseY;
				this.autoSpin = false;
				return true;
			}
			// Right click -> Pan
			if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
				this.panning = true;
				this.lastPanX = mouseX;
				this.lastPanY = mouseY;
				this.autoSpin = false;
				return true;
			}
			// Middle click -> Reset
			if (button == GLFW.GLFW_MOUSE_BUTTON_3) {
				reset3DCamera();
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.dragging) {
			this.yaw += (float) (deltaX * 0.02F);
			float twoPi = (float) (Math.PI * 2);
			if (this.yaw > twoPi) {
				this.yaw -= twoPi;
			} else if (this.yaw < 0.0F) {
				this.yaw += twoPi;
			}
			this.pitch = MathHelper.clamp(this.pitch + (float) (deltaY * 0.02F), -1.35F, 1.35F);
			return true;
		}
		if (this.panning) {
			this.panX += (float) deltaX;
			this.panY += (float) deltaY;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_1 && this.dragging) {
			this.dragging = false;
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_2 && this.panning) {
			this.panning = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int winX = (this.width - WINDOW_WIDTH) / 2;
		int winY = (this.height - WINDOW_HEIGHT) / 2;
		int vpX = this.isMaximized ? (winX + 12) : (winX + 172);
		int vpY = winY + 36;
		int vpW = this.isMaximized ? (WINDOW_WIDTH - 24) : 238;
		int vpH = this.isMaximized ? 192 : 138;

		if (mouseX >= vpX && mouseX <= vpX + vpW && mouseY >= vpY && mouseY <= vpY + vpH) {
			this.zoom = MathHelper.clamp(this.zoom + (float) (verticalAmount * 0.18F), 0.25F, 8.0F);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.isRenaming && this.renameField != null && this.renameField.charTyped(chr, modifiers)) {
			return true;
		}
		if (this.searchField != null && this.searchField.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.autoSpin && !this.dragging && !this.panning && this.pendingDeleteBlueprint == null && !this.isRenaming) {
			this.yaw += 0.012F;
			float twoPi = (float) (Math.PI * 2);
			if (this.yaw > twoPi) {
				this.yaw -= twoPi;
			}
		}
	}

	@Override
	protected void applyBlur(float delta) {
		// Suppress world background blur for high-fidelity 3D rendering
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// 1. Dark Glassmorphism Modal Background Frame
		int winX = (this.width - WINDOW_WIDTH) / 2;
		int winY = (this.height - WINDOW_HEIGHT) / 2;

		// Main body backing
		context.fill(winX, winY, winX + WINDOW_WIDTH, winY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(winX, winY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFE2B007);

		// Header Bar
		context.fill(winX + 1, winY + 1, winX + WINDOW_WIDTH - 1, winY + 28, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§6✦ ARCHITECTURAL BLUEPRINT 3D EXPLORER ✦"),
			this.width / 2,
			winY + 6,
			0xFFFFFF
		);

		Text escText = Text.literal("§e[Esc] §7Close");
		context.drawTextWithShadow(
			this.textRenderer,
			escText,
			winX + WINDOW_WIDTH - this.textRenderer.getWidth(escText) - 10,
			winY + 6,
			0xCCCCCC
		);

		// Sub-header status
		int totalBpCount = BlueprintRegistry.getAll().size();
		int shownBpCount = this.filteredBlueprints.size();
		String countLabel = "§7Showing §b" + shownBpCount + "§7 of §f" + totalBpCount + "§7 blueprints";
		context.drawTextWithShadow(this.textRenderer, Text.literal(countLabel), winX + 14, winY + 16, 0xAAAAAA);

		// 2. Left Pane (Search Bar + Card List + Pagination Indicator) - only rendered in split mode
		int cardW = 150;
		int searchX = winX + 14;
		int listNavY = winY + 236;

		if (!this.isMaximized) {
			int totalPages = getTotalListPages();
			String pageLabel = "§7" + (this.listPage + 1) + " / " + totalPages;
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(pageLabel), searchX + cardW / 2, listNavY + 5, 0xAAAAAA);

			// Draw Card Backgrounds and Badges
			int listStartY = winY + 60;
			int cardH = 32;
			int startIndex = this.listPage * LIST_PAGE_SIZE;

			for (int i = 0; i < LIST_PAGE_SIZE; i++) {
				int itemIndex = startIndex + i;
				if (itemIndex < this.filteredBlueprints.size()) {
					StructureBlueprint bp = this.filteredBlueprints.get(itemIndex);
					int cardY = listStartY + i * (cardH + 4);
					boolean isSelected = this.selectedBlueprint != null && bp.getId().equalsIgnoreCase(this.selectedBlueprint.getId());
					boolean isHovered = mouseX >= searchX && mouseX < searchX + cardW && mouseY >= cardY && mouseY < cardY + cardH;

					int bgCol = isSelected ? 0x662979FF : (isHovered ? 0x551E2D3E : 0x44141E28);
					context.fill(searchX, cardY, searchX + cardW, cardY + cardH, bgCol);

					int borderCol = isSelected ? 0xFF00E5FF : (isHovered ? 0xFF4A688F : 0xFF2A3D54);
					context.drawBorder(searchX, cardY, cardW, cardH, borderCol);

					// 1. Card Title on top line (cardY + 5)
					String cleanName = bp.getName();
					if (this.textRenderer.getWidth(cleanName) > 115) {
						cleanName = this.textRenderer.trimToWidth(cleanName, 108) + "…";
					}
					String prefix = isSelected ? "§b▶ §f" : "§7  ";
					context.drawTextWithShadow(this.textRenderer, Text.literal(prefix + cleanName), searchX + 6, cardY + 5, 0xFFFFFF);

					// 2. Card Size & block count on bottom line (cardY + 18)
					net.minecraft.util.math.BlockBox itemBox = bp.getBoundingBox();
					String sub = "§7" + itemBox.getBlockCountX() + "×" + itemBox.getBlockCountY() + "×" + itemBox.getBlockCountZ() + " §8| §b" + bp.getBlockCount() + "b";
					context.drawTextWithShadow(this.textRenderer, Text.literal(sub), searchX + 18, cardY + 18, 0x888888);

					boolean isCustom = BlueprintRegistry.isCustom(bp.getId());
					String badge = isCustom ? "§6[C]" : "§8[B]";
					context.drawTextWithShadow(this.textRenderer, Text.literal(badge), searchX + cardW - 20, cardY + 18, 0xFFFFFF);
				}
			}
		}

		// 3. 3D Isometric Viewport Bay
		int vpX = this.isMaximized ? (winX + 12) : (winX + 172);
		int vpY = winY + 36;
		int vpW = this.isMaximized ? (WINDOW_WIDTH - 24) : 238;
		int vpH = this.isMaximized ? 192 : 138;

		context.fill(vpX, vpY, vpX + vpW, vpY + vpH, 0xFF0A0F16);
		context.drawBorder(vpX, vpY, vpW, vpH, 0xFF2A3D54);

		// Viewport Header Label
		context.drawTextWithShadow(this.textRenderer, Text.literal("§b3D Preview"), vpX + 6, vpY + 5, 0x55FFFF);

		// Render True 3D Rotated Voxel Blueprint
		render3DBlueprint(context, vpX, vpY, vpW, vpH);

		// 4. Specifications Card (Below Viewport in split mode, or floating HUD in maximized mode)
		if (!this.isMaximized) {
			int specY = winY + 180;
			int specH = 50;
			context.fill(vpX, specY, vpX + vpW, specY + specH, 0xCC111824);
			context.drawBorder(vpX, specY, vpW, specH, 0xFF1E2D3E);

			if (this.selectedBlueprint != null) {
				net.minecraft.util.math.BlockBox selBox = this.selectedBlueprint.getBoundingBox();
				int width = selBox.getBlockCountX();
				int height = selBox.getBlockCountY();
				int length = selBox.getBlockCountZ();
				int blocks = this.selectedBlueprint.getBlockCount();
				long volume = (long) width * height * length;

				// Line 1: Blueprint Name (leaving space on the right for the Rename button)
				String title = "§6✦ " + this.selectedBlueprint.getName();
				int maxTitleW = (this.renameBtn != null && this.renameBtn.visible) ? (vpW - 68) : (vpW - 12);
				if (this.textRenderer.getWidth(title) > maxTitleW) {
					title = this.textRenderer.trimToWidth(title, maxTitleW - 8) + "…";
				}
				context.drawTextWithShadow(this.textRenderer, Text.literal(title), vpX + 6, specY + 5, 0xFFFFFF);

				// Line 2: Size & Blocks Specs
				String specRow1 = "§eSize: §f" + width + "×" + height + "×" + length +
					" §8| §b" + blocks + "b" +
					" §8| §a" + volume + "m³";
				context.drawTextWithShadow(this.textRenderer, Text.literal(specRow1), vpX + 6, specY + 18, 0xEEEEEE);

				// Line 3: Description text
				String desc = this.selectedBlueprint.getDescription();
				if (desc == null || desc.isBlank()) {
					desc = "Standard architectural construction directive.";
				}
				List<OrderedText> wrappedDesc = this.textRenderer.wrapLines(Text.literal("§7" + desc), vpW - 12);
				if (!wrappedDesc.isEmpty()) {
					context.drawTextWithShadow(this.textRenderer, wrappedDesc.get(0), vpX + 6, specY + 31, 0xAAAAAA);
				}
				if (wrappedDesc.size() > 1) {
					context.drawTextWithShadow(this.textRenderer, wrappedDesc.get(1), vpX + 6, specY + 40, 0x888888);
				}
			} else {
				context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal("§8No matching blueprint selected"),
					vpX + vpW / 2,
					specY + 20,
					0x888888
				);
			}
		} else if (this.selectedBlueprint != null) {
			// Floating Specs Pill in Maximized Mode (Bottom-left of Viewport)
			net.minecraft.util.math.BlockBox selBox = this.selectedBlueprint.getBoundingBox();
			String maxSpec = "§6✦ " + this.selectedBlueprint.getName() + " §8| §e" +
				selBox.getBlockCountX() + "×" + selBox.getBlockCountY() + "×" + selBox.getBlockCountZ() +
				" §8| §b" + this.selectedBlueprint.getBlockCount() + " blocks §8| §a" + this.zoom + "x Zoom";
			int pillW = this.textRenderer.getWidth(maxSpec) + 12;
			context.fill(vpX + 6, vpY + vpH - 18, vpX + 6 + pillW, vpY + vpH - 4, 0xAA080E14);
			context.drawBorder(vpX + 6, vpY + vpH - 18, pillW, 14, 0xFF2A3D54);
			context.drawTextWithShadow(this.textRenderer, Text.literal(maxSpec), vpX + 12, vpY + vpH - 15, 0xFFFFFF);
		}

		// 5. Render Child Widgets (Buttons, search field)
		super.render(context, mouseX, mouseY, delta);

		// 6. Render Confirmation Modal Overlay if active
		if (this.pendingDeleteBlueprint != null) {
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 400.0F);

			context.fill(0, 0, this.width, this.height, 0xAA080C12);

			int modalW = 220;
			int modalH = 100;
			int modalX = (this.width - modalW) / 2;
			int modalY = (this.height - modalH) / 2;

			context.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFA141E2B);
			context.drawBorder(modalX, modalY, modalW, modalH, 0xFFE2B007);

			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c✦ CONFIRM DELETION ✦"), modalX + modalW / 2, modalY + 10, 0xFF5555);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Delete custom blueprint:"), modalX + modalW / 2, modalY + 26, 0xAAAAAA);

			String truncName = this.pendingDeleteBlueprint.getName();
			if (this.textRenderer.getWidth(truncName) > modalW - 24) {
				truncName = this.textRenderer.trimToWidth(truncName, modalW - 32) + "…";
			}
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e\"" + truncName + "\""), modalX + modalW / 2, modalY + 38, 0xFFFF55);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8This action cannot be undone."), modalX + modalW / 2, modalY + 52, 0x888888);

			this.confirmDeleteBtn.render(context, mouseX, mouseY, delta);
			this.cancelDeleteBtn.render(context, mouseX, mouseY, delta);

			context.getMatrices().pop();
		}

		// 7. Render Rename Modal Overlay if active
		if (this.isRenaming) {
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 400.0F);

			context.fill(0, 0, this.width, this.height, 0xAA080C12);

			int modalW = 240;
			int modalH = 104;
			int modalX = (this.width - modalW) / 2;
			int modalY = (this.height - modalH) / 2;

			context.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFA141E2B);
			context.drawBorder(modalX, modalY, modalW, modalH, 0xFF00E5FF);

			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§b✦ RENAME BLUEPRINT ✦"), modalX + modalW / 2, modalY + 10, 0x55FFFF);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Enter new display name:"), modalX + modalW / 2, modalY + 25, 0xAAAAAA);

			if (this.renameField != null) {
				this.renameField.render(context, mouseX, mouseY, delta);
			}

			if (this.renameError != null) {
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c" + this.renameError), modalX + modalW / 2, modalY + 62, 0xFF5555);
			} else {
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8Press Enter to save, Esc to cancel"), modalX + modalW / 2, modalY + 62, 0x777777);
			}

			if (this.confirmRenameBtn != null) {
				this.confirmRenameBtn.render(context, mouseX, mouseY, delta);
			}
			if (this.cancelRenameBtn != null) {
				this.cancelRenameBtn.render(context, mouseX, mouseY, delta);
			}

			context.getMatrices().pop();
		}
	}

	/**
	 * Custom card button widget that delegates rendering to BlueprintExplorerScreen.render()
	 * to prevent vanilla button text positioning from colliding with card specifications.
	 */
	private static class CardSlotButton extends ButtonWidget {
		CardSlotButton(int x, int y, int width, int height, PressAction onPress) {
			super(x, y, width, height, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			// Card visual rendering handled in BlueprintExplorerScreen.render() to prevent title/size text collision
		}
	}

	/**
	 * Pre-cached surface face representation (ProjectedFace) for high-performance 3D rendering.
	 */
	private static class CachedFace {
		final byte faceIndex;
		final int blockY;
		final boolean occludedUnlessSliced;
		final int baseColor;
		final float fcX, fcY, fcZ;
		final float c0x, c0y, c0z;
		final float c1x, c1y, c1z;
		final float c2x, c2y, c2z;
		final float c3x, c3y, c3z;

		CachedFace(
			byte faceIndex, int blockY, boolean occludedUnlessSliced, int baseColor,
			float fcX, float fcY, float fcZ,
			float c0x, float c0y, float c0z,
			float c1x, float c1y, float c1z,
			float c2x, float c2y, float c2z,
			float c3x, float c3y, float c3z
		) {
			this.faceIndex = faceIndex;
			this.blockY = blockY;
			this.occludedUnlessSliced = occludedUnlessSliced;
			this.baseColor = baseColor;
			this.fcX = fcX; this.fcY = fcY; this.fcZ = fcZ;
			this.c0x = c0x; this.c0y = c0y; this.c0z = c0z;
			this.c1x = c1x; this.c1y = c1y; this.c1z = c1z;
			this.c2x = c2x; this.c2y = c2y; this.c2z = c2z;
			this.c3x = c3x; this.c3y = c3y; this.c3z = c3z;
		}
	}

	/**
	 * Precomputed blueprint mesh cache storing interior-culled surface faces and bounding extents.
	 */
	private static class BlueprintMeshCache {
		final StructureBlueprint blueprint;
		final int minX, maxX, minY, maxY, minZ, maxZ;
		final double cx, cy, cz;
		final double maxDim;
		final CachedFace[] faces;

		BlueprintMeshCache(
			StructureBlueprint blueprint,
			int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
			double cx, double cy, double cz, double maxDim,
			CachedFace[] faces
		) {
			this.blueprint = blueprint;
			this.minX = minX; this.maxX = maxX;
			this.minY = minY; this.maxY = maxY;
			this.minZ = minZ; this.maxZ = maxZ;
			this.cx = cx; this.cy = cy; this.cz = cz;
			this.maxDim = maxDim;
			this.faces = faces;
		}

		static BlueprintMeshCache build(StructureBlueprint bp) {
			if (bp == null || bp.getBlocks() == null || bp.getBlocks().isEmpty()) {
				return null;
			}
			List<BlueprintBlock> blocks = bp.getBlocks();
			int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
			int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

			Set<BlockPos> occupied = new HashSet<>(blocks.size());
			for (BlueprintBlock b : blocks) {
				BlockPos pos = b.offset();
				int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();
				if (bx < minX) minX = bx;
				if (bx > maxX) maxX = bx;
				if (by < minY) minY = by;
				if (by > maxY) maxY = by;
				if (bz < minZ) minZ = bz;
				if (bz > maxZ) maxZ = bz;

				if (isOpaque(b.state())) {
					occupied.add(pos);
				}
			}

			double cx = (minX + maxX) / 2.0;
			double cy = (minY + maxY) / 2.0;
			double cz = (minZ + maxZ) / 2.0;

			int spanX = Math.max(1, maxX - minX + 1);
			int spanY = Math.max(1, maxY - minY + 1);
			int spanZ = Math.max(1, maxZ - minZ + 1);
			double maxDim = Math.max(spanX, Math.max(spanY, spanZ));

			List<CachedFace> faceList = new ArrayList<>(blocks.size() * 3);

			for (BlueprintBlock b : blocks) {
				BlockPos pos = b.offset();
				int bx = pos.getX();
				int by = pos.getY();
				int bz = pos.getZ();

				double relX = bx - cx;
				double relY = by - cy;
				double relZ = bz - cz;

				double[] bnd = getBlockBounds(b.state());
				double xMin = bnd[0], xMax = bnd[1];
				double yMin = bnd[2], yMax = bnd[3];
				double zMin = bnd[4], zMax = bnd[5];
				boolean isFullCube = (xMin == -0.5 && xMax == 0.5 && yMin == -0.5 && yMax == 0.5 && zMin == -0.5 && zMax == 0.5);

				for (int f = 0; f < 6; f++) {
					int[] norm = NORMALS[f];
					int nx = norm[0], ny = norm[1], nz = norm[2];

					boolean occludedUnlessSliced = false;
					if (isFullCube) {
						BlockPos neighborPos = pos.add(nx, ny, nz);
						if (occupied.contains(neighborPos)) {
							if (f == 0) { // Top face (+Y)
								// Occluded in full view, but exposed if sliced at by
								occludedUnlessSliced = true;
							} else {
								// Bottom, East, West, South, North: neighbor is at or below this layer,
								// so if occupied, this face is never exposed under slicing!
								continue;
							}
						}
					}

					int baseColor = getBlockColor(b.state(), f);
					double[][] c = getFaceCorners(f, xMin, xMax, yMin, yMax, zMin, zMax);

					float fcX = (float) (relX + (xMin + xMax) * 0.5 + nx * 0.02);
					float fcY = (float) (relY + (yMin + yMax) * 0.5 + ny * 0.02);
					float fcZ = (float) (relZ + (zMin + zMax) * 0.5 + nz * 0.02);

					faceList.add(new CachedFace(
						(byte) f, by, occludedUnlessSliced, baseColor,
						fcX, fcY, fcZ,
						(float) (relX + c[0][0]), (float) (relY + c[0][1]), (float) (relZ + c[0][2]),
						(float) (relX + c[1][0]), (float) (relY + c[1][1]), (float) (relZ + c[1][2]),
						(float) (relX + c[2][0]), (float) (relY + c[2][1]), (float) (relZ + c[2][2]),
						(float) (relX + c[3][0]), (float) (relY + c[3][1]), (float) (relZ + c[3][2])
					));
				}
			}

			return new BlueprintMeshCache(
				bp,
				minX, maxX, minY, maxY, minZ, maxZ,
				cx, cy, cz, maxDim,
				faceList.toArray(new CachedFace[0])
			);
		}
	}

	private void ensureBufferCapacity(int count) {
		if (this.sortBuffer.length < count) {
			int newCap = Math.max(this.sortBuffer.length * 2, count);
			this.sortBuffer = new long[newCap];
			this.projQuadX = new float[newCap * 4];
			this.projQuadY = new float[newCap * 4];
			this.projColor = new int[newCap];
		}
	}

	/**
	 * Renders the 3D isometric structure preview using pre-cached surface geometry,
	 * zero-allocation primitive depth sorting, and fast Painter's algorithm quad rasterization.
	 */
	private void render3DBlueprint(DrawContext context, int vpX, int vpY, int vpW, int vpH) {
		if (!hasValidBlocks()) {
			String placeholder = this.selectedBlueprint == null ? "§8[ No Matching Blueprints ]" : "§8[ No 3D Structure Data ]";
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal(placeholder),
				vpX + vpW / 2,
				vpY + vpH / 2,
				0x666666
			);
			return;
		}

		if (this.meshCache == null || this.meshCache.blueprint != this.selectedBlueprint) {
			this.meshCache = BlueprintMeshCache.build(this.selectedBlueprint);
		}
		BlueprintMeshCache mesh = this.meshCache;
		if (mesh == null || mesh.faces.length == 0) {
			return;
		}

		int minX = mesh.minX, maxX = mesh.maxX;
		int minY = mesh.minY, maxY = mesh.maxY;
		int minZ = mesh.minZ, maxZ = mesh.maxZ;
		double cx = mesh.cx, cy = mesh.cy, cz = mesh.cz;
		double maxDim = mesh.maxDim;

		// Floor Cutaway: clamp active slice level
		int currentSlice = (this.sliceY == Integer.MAX_VALUE || this.sliceY > maxY) ? maxY : Math.max(minY, Math.min(maxY, this.sliceY));

		// Pixel scale calculation
		double baseScale = Math.min((vpW - 32) / (maxDim * 1.5), (vpH - 32) / (maxDim * 1.4)) * this.zoom;
		baseScale = Math.max(1.5, Math.min(64.0, baseScale));

		double cosYaw = Math.cos(this.yaw);
		double sinYaw = Math.sin(this.yaw);
		double cosPitch = Math.cos(this.pitch);
		double sinPitch = Math.sin(this.pitch);

		double centerX = vpX + vpW / 2.0 + this.panX;
		double centerY = vpY + vpH / 2.0 + this.panY;

		// Directional Sun Vector in world space: (0.355, 0.862, -0.355) normalized
		final double sunX = 0.355;
		final double sunY = 0.862;
		final double sunZ = -0.355;

		ensureBufferCapacity(mesh.faces.length);
		int survivorCount = 0;

		for (CachedFace face : mesh.faces) {
			if (face.blockY > currentSlice) {
				continue;
			}
			if (face.occludedUnlessSliced && face.blockY + 1 <= currentSlice) {
				continue;
			}

			int[] norm = NORMALS[face.faceIndex];
			int nx = norm[0], ny = norm[1], nz = norm[2];

			// Backface culling: rotate face normal by (yaw, pitch)
			double rnx1 = nx * cosYaw - nz * sinYaw;
			double rnz1 = nx * sinYaw + nz * cosYaw;
			double rny1 = ny * cosPitch - rnz1 * sinPitch;
			double rnz2 = ny * sinPitch + rnz1 * cosPitch;

			if (rnz2 <= 0.001) {
				continue;
			}

			// Directional Sunlight intensity: Dot product (Normal · Sun)
			double dot = nx * sunX + ny * sunY + nz * sunZ;
			float brightness = (float) MathHelper.clamp(0.55 + 0.45 * Math.max(0.0, dot), 0.42, 1.25);
			int shadedColor = shadeColor(face.baseColor, brightness);

			int baseVertex = survivorCount * 4;

			// Project Corner 0
			double x1_0 = face.c0x * cosYaw - face.c0z * sinYaw;
			double z1_0 = face.c0x * sinYaw + face.c0z * cosYaw;
			double y1_0 = face.c0y * cosPitch - z1_0 * sinPitch;
			projQuadX[baseVertex] = (float) (centerX + x1_0 * baseScale);
			projQuadY[baseVertex] = (float) (centerY - y1_0 * baseScale);

			// Project Corner 1
			double x1_1 = face.c1x * cosYaw - face.c1z * sinYaw;
			double z1_1 = face.c1x * sinYaw + face.c1z * cosYaw;
			double y1_1 = face.c1y * cosPitch - z1_1 * sinPitch;
			projQuadX[baseVertex + 1] = (float) (centerX + x1_1 * baseScale);
			projQuadY[baseVertex + 1] = (float) (centerY - y1_1 * baseScale);

			// Project Corner 2
			double x1_2 = face.c2x * cosYaw - face.c2z * sinYaw;
			double z1_2 = face.c2x * sinYaw + face.c2z * cosYaw;
			double y1_2 = face.c2y * cosPitch - z1_2 * sinPitch;
			projQuadX[baseVertex + 2] = (float) (centerX + x1_2 * baseScale);
			projQuadY[baseVertex + 2] = (float) (centerY - y1_2 * baseScale);

			// Project Corner 3
			double x1_3 = face.c3x * cosYaw - face.c3z * sinYaw;
			double z1_3 = face.c3x * sinYaw + face.c3z * cosYaw;
			double y1_3 = face.c3y * cosPitch - z1_3 * sinPitch;
			projQuadX[baseVertex + 3] = (float) (centerX + x1_3 * baseScale);
			projQuadY[baseVertex + 3] = (float) (centerY - y1_3 * baseScale);

			projColor[survivorCount] = shadedColor;

			// Face center depth for Painter's algorithm
			double cz1 = face.fcX * sinYaw + face.fcZ * cosYaw;
			float depth = (float) (face.fcY * sinPitch + cz1 * cosPitch);

			sortBuffer[survivorCount] = (((long) Float.floatToIntBits(depth + 100000.0F)) << 32) | (survivorCount & 0xFFFFFFFFL);
			survivorCount++;
		}

		// Painter's Algorithm: Sort faces by depth ascending (furthest first, closest on top)
		Arrays.sort(this.sortBuffer, 0, survivorCount);

		// Hardware-accelerated viewport scissor clipping
		context.enableScissor(vpX + 1, vpY + 1, vpX + vpW - 1, vpY + vpH - 1);

		// Ground Foundation Pedestal Grid (Rendered beneath the structure)
		renderGroundPedestal(context, minX, maxX, minY, minZ, maxZ, cx, cy, cz, cosYaw, sinYaw, cosPitch, sinPitch, centerX, centerY, baseScale);

		// Render all sorted 3D quads via batched VertexConsumer
		VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

		for (int i = 0; i < survivorCount; i++) {
			int sIdx = (int) (sortBuffer[i] & 0xFFFFFFFFL);
			int baseV = sIdx * 4;
			int col = projColor[sIdx];
			buffer.vertex(matrix, projQuadX[baseV], projQuadY[baseV], 0.0F).color(col);
			buffer.vertex(matrix, projQuadX[baseV + 1], projQuadY[baseV + 1], 0.0F).color(col);
			buffer.vertex(matrix, projQuadX[baseV + 2], projQuadY[baseV + 2], 0.0F).color(col);
			buffer.vertex(matrix, projQuadX[baseV + 3], projQuadY[baseV + 3], 0.0F).color(col);
		}

		context.draw();

		// Viewport HUD Overlays (Compass, Cutaway status, controls guide)
		renderViewportHud(context, vpX, vpY, vpW, vpH, currentSlice, minY, maxY);

		context.disableScissor();
	}

	/**
	 * Renders a subtle ground grid pedestal showing foundation footprint and coordinate boundaries.
	 */
	private void renderGroundPedestal(
		DrawContext context,
		int minX, int maxX, int minY, int minZ, int maxZ,
		double cx, double cy, double cz,
		double cosYaw, double sinYaw, double cosPitch, double sinPitch,
		double centerX, double centerY, double baseScale
	) {
		double groundRelY = (minY - 0.5D) - cy;
		double gMinX = (minX - 0.5D) - cx;
		double gMaxX = (maxX + 0.5D) - cx;
		double gMinZ = (minZ - 0.5D) - cz;
		double gMaxZ = (maxZ + 0.5D) - cz;

		double[][] corners = {
			{gMinX, groundRelY, gMinZ},
			{gMinX, groundRelY, gMaxZ},
			{gMaxX, groundRelY, gMaxZ},
			{gMaxX, groundRelY, gMinZ}
		};

		float[] sx = new float[4];
		float[] sy = new float[4];
		for (int i = 0; i < 4; i++) {
			double x1 = corners[i][0] * cosYaw - corners[i][2] * sinYaw;
			double z1 = corners[i][0] * sinYaw + corners[i][2] * cosYaw;
			double y1 = corners[i][1] * cosPitch - z1 * sinPitch;
			sx[i] = (float) (centerX + x1 * baseScale);
			sy[i] = (float) (centerY - y1 * baseScale);
		}

		VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

		// Pedestal fill (translucent slate)
		int groundCol = 0x2A101C28;
		buffer.vertex(matrix, sx[0], sy[0], 0.0F).color(groundCol);
		buffer.vertex(matrix, sx[1], sy[1], 0.0F).color(groundCol);
		buffer.vertex(matrix, sx[2], sy[2], 0.0F).color(groundCol);
		buffer.vertex(matrix, sx[3], sy[3], 0.0F).color(groundCol);

		context.draw();
	}

	/**
	 * Renders cardinal compass orientation, floor cutaway status, and mouse control guide.
	 */
	private void renderViewportHud(
		DrawContext context,
		int vpX, int vpY, int vpW, int vpH,
		int currentSlice, int minY, int maxY
	) {
		// 1. Cardinal Compass Facing Indicator
		float degrees = (float) Math.toDegrees(this.yaw);
		while (degrees < 0) degrees += 360.0F;
		degrees = degrees % 360.0F;

		String facing;
		if (degrees >= 315.0F || degrees < 45.0F) {
			facing = "§bNorth §7(Front)";
		} else if (degrees >= 45.0F && degrees < 135.0F) {
			facing = "§eEast §7(Right)";
		} else if (degrees >= 135.0F && degrees < 225.0F) {
			facing = "§cSouth §7(Back)";
		} else {
			facing = "§aWest §7(Left)";
		}
		context.drawTextWithShadow(this.textRenderer, Text.literal("§6Compass: " + facing), vpX + 8, vpY + 22, 0xFFFFFF);

		// 2. Active Floor Cutaway Alert
		if (currentSlice < maxY) {
			int currentLevel = currentSlice - minY + 1;
			int totalLevels = maxY - minY + 1;
			String cutaway = "§e🏢 Floor Cutaway: L" + currentLevel + " of " + totalLevels + " (Y=" + currentSlice + ")";
			context.drawTextWithShadow(this.textRenderer, Text.literal(cutaway), vpX + 8, vpY + 34, 0xFFFF55);
		}

		// 3. Controls Guide HUD (Bottom Right of Viewport)
		String guide = "§8L-Drag: Rotate • R-Drag: Pan • Scroll: Zoom";
		int guideW = this.textRenderer.getWidth(guide);
		context.drawTextWithShadow(this.textRenderer, Text.literal(guide), vpX + vpW - guideW - 8, vpY + vpH - 14, 0x667788);
	}

	/**
	 * Determines whether a block state is solid and opaque for 6-directional face culling.
	 */
	private static boolean isOpaque(BlockState state) {
		if (state == null || state.isAir()) return false;
		Block block = state.getBlock();
		String id = Registries.BLOCK.getId(block).getPath().toLowerCase(Locale.ROOT);
		if (id.contains("glass") || id.contains("pane") || id.contains("door") || id.contains("trapdoor")
			|| id.contains("stair") || id.contains("slab") || id.contains("fence") || id.contains("wall")
			|| id.contains("gate") || id.contains("torch") || id.contains("lantern") || id.contains("chain")
			|| id.contains("iron_bars") || id.contains("carpet") || id.contains("flower") || id.contains("sapling")
			|| id.contains("leaf") || id.contains("leaves") || id.contains("water") || id.contains("ice")
			|| id.contains("wheat") || id.contains("carrot") || id.contains("potato") || id.contains("beetroot")
			|| id.contains("candle") || id.contains("rod") || id.contains("mushroom") || id.contains("fungus")
			|| id.contains("grass") || id.contains("fern") || id.contains("vine") || id.contains("lichen")
			|| id.contains("sugar_cane") || id.contains("bamboo") || id.contains("sprouts") || id.contains("roots")
			|| id.contains("kelp") || id.contains("seagrass")) {
			return false;
		}
		return state.isOpaque();
	}

	/**
	 * Resolves bounding dimensions [xMin, xMax, yMin, yMax, zMin, zMax] relative to block center
	 * to accurately model flowers, plants, crops, torches, slabs, and carpets.
	 */
	private static double[] getBlockBounds(BlockState state) {
		if (state == null) return new double[]{-0.5, 0.5, -0.5, 0.5, -0.5, 0.5};
		String id = Registries.BLOCK.getId(state.getBlock()).getPath().toLowerCase(Locale.ROOT);

		// 1. Flowers, botanical plants, crops, saplings, mushrooms (slender botanical voxel)
		if (id.contains("flower") || id.contains("tulip") || id.contains("orchid") || id.contains("allium")
			|| id.contains("poppy") || id.contains("dandelion") || id.contains("daisy") || id.contains("cornflower")
			|| id.contains("bluet") || id.contains("peony") || id.contains("lilac") || id.contains("rose")
			|| id.contains("sapling") || id.contains("wheat") || id.contains("carrot") || id.contains("potato")
			|| id.contains("beetroot") || id.contains("mushroom") || id.contains("fungus") || id.contains("fern")
			|| (id.contains("grass") && !id.contains("block")) || id.contains("sprouts") || id.contains("roots")
			|| id.contains("spore_blossom") || id.contains("sweet_berry")) {
			return new double[]{-0.28, 0.28, -0.5, 0.35, -0.28, 0.28};
		}

		// 2. Torches, rods, candles, levers
		if (id.contains("torch") || id.contains("candle") || id.contains("rod") || id.contains("lever")) {
			return new double[]{-0.15, 0.15, -0.5, 0.25, -0.15, 0.15};
		}

		// 3. Slabs (bottom half slab)
		if (id.contains("slab") && !id.contains("double")) {
			return new double[]{-0.5, 0.5, -0.5, 0.0, -0.5, 0.5};
		}

		// 4. Carpets & pressure plates (thin floor sheet)
		if (id.contains("carpet") || id.contains("pressure_plate")) {
			return new double[]{-0.5, 0.5, -0.5, -0.375, -0.5, 0.5};
		}

		// 5. Glass panes & iron bars (slender vertical plane)
		if (id.contains("pane") || id.contains("iron_bars")) {
			return new double[]{-0.15, 0.15, -0.5, 0.5, -0.15, 0.15};
		}

		// Default full unit cube
		return new double[]{-0.5, 0.5, -0.5, 0.5, -0.5, 0.5};
	}

	/**
	 * Projects 4 corner vertices for face f from custom bounding box dimensions.
	 */
	private static double[][] getFaceCorners(int f, double xMin, double xMax, double yMin, double yMax, double zMin, double zMax) {
		return switch (f) {
			case 0 -> new double[][]{ {xMin, yMax, zMin}, {xMin, yMax, zMax}, {xMax, yMax, zMax}, {xMax, yMax, zMin} }; // Top
			case 1 -> new double[][]{ {xMin, yMin, zMax}, {xMin, yMin, zMin}, {xMax, yMin, zMin}, {xMax, yMin, zMax} }; // Bottom
			case 2 -> new double[][]{ {xMax, yMin, zMin}, {xMax, yMax, zMin}, {xMax, yMax, zMax}, {xMax, yMin, zMax} }; // East
			case 3 -> new double[][]{ {xMin, yMin, zMax}, {xMin, yMax, zMax}, {xMin, yMax, zMin}, {xMin, yMin, zMin} }; // West
			case 4 -> new double[][]{ {xMin, yMin, zMax}, {xMax, yMin, zMax}, {xMax, yMax, zMax}, {xMin, yMax, zMax} }; // South
			case 5 -> new double[][]{ {xMax, yMin, zMin}, {xMin, yMin, zMin}, {xMin, yMax, zMin}, {xMax, yMax, zMin} }; // North
			default -> new double[][]{ {-0.5, 0.5, -0.5}, {-0.5, 0.5, 0.5}, {0.5, 0.5, 0.5}, {0.5, 0.5, -0.5} };
		};
	}

	/**
	 * Material palette color mapping for authentic architectural block representation.
	 */
	private static int getBlockColor(BlockState state) {
		return getBlockColor(state, 0);
	}

	/**
	 * Multi-face textured color mapping providing distinct top/bottom/side textures,
	 * authentic flower bloom hues, botanical greens, wood rings vs bark, and 16-dye color support.
	 */
	private static int getBlockColor(BlockState state, int face) {
		if (state == null) return 0xFF888888;
		Block block = state.getBlock();
		String id = Registries.BLOCK.getId(block).getPath().toLowerCase(Locale.ROOT);

		// 1. Flowers & Botanical Flora (Vibrant blooms on top, rich botanical greens on sides/stem)
		if (id.contains("poppy") || id.contains("red_tulip")) {
			return face == 0 ? 0xFFE52521 : (face == 1 ? 0xFF388E3C : 0xFFD32F2F);
		}
		if (id.contains("dandelion") || id.contains("sunflower")) {
			return face == 0 ? 0xFFFFD600 : (face == 1 ? 0xFF388E3C : 0xFFFFEB3B);
		}
		if (id.contains("blue_orchid")) {
			return face == 0 ? 0xFF29B6F6 : (face == 1 ? 0xFF388E3C : 0xFF03A9F4);
		}
		if (id.contains("allium")) {
			return face == 0 ? 0xFFBA68C8 : (face == 1 ? 0xFF388E3C : 0xFFAB47BC);
		}
		if (id.contains("azure_bluet") || id.contains("oxeye_daisy") || id.contains("lily_of_the_valley") || id.contains("white_tulip")) {
			return face == 0 ? 0xFFFFFFFF : (face == 1 ? 0xFF388E3C : 0xFFE0E0E0);
		}
		if (id.contains("orange_tulip") || id.contains("torchflower")) {
			return face == 0 ? 0xFFFF6D00 : (face == 1 ? 0xFF388E3C : 0xFFFF8F00);
		}
		if (id.contains("pink_tulip") || id.contains("peony") || id.contains("spore_blossom")) {
			return face == 0 ? 0xFFF48FB1 : (face == 1 ? 0xFF388E3C : 0xFFEC407A);
		}
		if (id.contains("cornflower")) {
			return face == 0 ? 0xFF3D5AFE : (face == 1 ? 0xFF388E3C : 0xFF304FFE);
		}
		if (id.contains("wither_rose")) {
			return face == 0 ? 0xFF263238 : (face == 1 ? 0xFF212121 : 0xFF37474F);
		}
		if (id.contains("pitcher_plant")) {
			return face == 0 ? 0xFF5C6BC0 : (face == 1 ? 0xFF388E3C : 0xFF3F51B5);
		}
		if (id.contains("lilac")) {
			return face == 0 ? 0xFFAB47BC : (face == 1 ? 0xFF388E3C : 0xFF8E24AA);
		}
		if (id.contains("rose_bush") || id.contains("rose")) {
			return face == 0 ? 0xFFD32F2F : (face == 1 ? 0xFF2E7D32 : 0xFFC2185B);
		}
		if (id.contains("flower") || id.contains("blossom")) {
			return face == 0 ? 0xFFFF80AB : (face == 1 ? 0xFF388E3C : 0xFFE91E63);
		}

		// 2. Agricultural Crops & Vegetables
		if (id.contains("wheat") || id.contains("hay")) {
			return face == 0 ? 0xFFE6C65B : 0xFFD4AF37;
		}
		if (id.contains("carrot")) {
			return face == 0 ? 0xFF43A047 : 0xFFFF8F00;
		}
		if (id.contains("potato")) {
			return face == 0 ? 0xFF43A047 : 0xFFC29B38;
		}
		if (id.contains("beetroot")) {
			return face == 0 ? 0xFF43A047 : 0xFF880E4F;
		}
		if (id.contains("melon")) {
			return face == 0 ? 0xFF9CCC65 : 0xFF7CB342;
		}
		if (id.contains("pumpkin")) {
			if (id.contains("carved") || id.contains("jack_o_lantern")) {
				return (face == 4 || face == 5) ? 0xFFFFB300 : (face == 0 ? 0xFFE65100 : 0xFFEF6C00);
			}
			return face == 0 ? 0xFFE65100 : 0xFFF57C00;
		}
		if (id.contains("sugar_cane") || id.contains("cane")) {
			return 0xFF8BC34A;
		}
		if (id.contains("cactus")) {
			return face == 0 ? 0xFF558B2F : 0xFF437324;
		}
		if (id.contains("sweet_berry")) {
			return face == 0 ? 0xFFE91E63 : 0xFF2E7D32;
		}
		if (id.contains("cocoa")) {
			return 0xFF6D4C41;
		}

		// 3. Fungi, Mushrooms & Vines
		if (id.contains("red_mushroom")) {
			return face == 0 ? 0xFFD32F2F : (face == 1 ? 0xFFEEEEEE : 0xFFE53935);
		}
		if (id.contains("brown_mushroom") || id.contains("mushroom")) {
			return face == 0 ? 0xFF795548 : (face == 1 ? 0xFFD7CCC8 : 0xFF6D4C41);
		}
		if (id.contains("crimson_fungus") || id.contains("crimson_roots")) {
			return 0xFFB71C1C;
		}
		if (id.contains("warped_fungus") || id.contains("warped_roots") || id.contains("nether_sprouts")) {
			return 0xFF00897B;
		}
		if (id.contains("weeping_vines")) return 0xFF990000;
		if (id.contains("twisting_vines")) return 0xFF00796B;
		if (id.contains("kelp") || id.contains("seagrass")) return 0xFF2E7D32;
		if (id.contains("sea_pickle")) return 0xFF8BC34A;
		if (id.contains("lily_pad")) return 0xFF2E7D32;
		if (id.contains("fern") || id.contains("sapling")) return 0xFF43A047;
		if (id.contains("dead_bush")) return 0xFF9E8158;

		// 4. Grass & Soil Variants
		if (id.equals("grass_block") || id.equals("grass")) {
			return face == 0 ? 0xFF5B8C32 : (face == 1 ? 0xFF866043 : 0xFF6A7B38);
		}
		if (id.contains("dirt_path") || id.contains("path")) {
			return face == 0 ? 0xFF9E7E4F : 0xFF866043;
		}
		if (id.contains("podzol")) {
			return face == 0 ? 0xFF5C4033 : 0xFF866043;
		}
		if (id.contains("mycelium")) {
			return face == 0 ? 0xFF6F6265 : 0xFF866043;
		}
		if (id.contains("moss")) return 0xFF5B8C32;
		if (id.contains("dirt") || id.contains("mud") || id.contains("farmland") || id.contains("clay")) {
			return 0xFF866043;
		}
		if (id.contains("leaves") || id.contains("leaf") || id.contains("vine") || id.contains("lichen")) {
			if (id.contains("cherry")) return 0xFFF48FB1;
			if (id.contains("azalea")) return 0xFF488226;
			if (id.contains("birch")) return 0xFF6BA338;
			if (id.contains("spruce")) return 0xFF355E3B;
			return 0xE63E7525;
		}

		// 5. Timber, Logs & Wood Species (Cut inner rings on top/bottom, distinctive bark on sides)
		boolean isLogOrWood = id.endsWith("_log") || id.endsWith("_wood") || id.contains("stem") || id.contains("hyphae");
		boolean isEndFace = (face == 0 || face == 1);
		if (id.contains("birch")) {
			return isLogOrWood ? (isEndFace ? 0xFFD7C58D : 0xFFEBEBEB) : 0xFFD7C58D;
		}
		if (id.contains("dark_oak")) {
			return isLogOrWood ? (isEndFace ? 0xFF3D2716 : 0xFF2C1C0E) : 0xFF422A15;
		}
		if (id.contains("spruce")) {
			return isLogOrWood ? (isEndFace ? 0xFF7B5C3F : 0xFF3B2817) : 0xFF6B5030;
		}
		if (id.contains("acacia")) {
			return isLogOrWood ? (isEndFace ? 0xFFB86638 : 0xFF635E5B) : 0xFFBA6336;
		}
		if (id.contains("jungle")) {
			return isLogOrWood ? (isEndFace ? 0xFFA57855 : 0xFF56432D) : 0xFFA07350;
		}
		if (id.contains("cherry")) {
			return isLogOrWood ? (isEndFace ? 0xFFE8A5AF : 0xFF36272B) : 0xFFE09BA5;
		}
		if (id.contains("mangrove")) {
			return isLogOrWood ? (isEndFace ? 0xFF783832 : 0xFF4D332D) : 0xFF763631;
		}
		if (id.contains("bamboo")) {
			return isLogOrWood ? (isEndFace ? 0xFF98853B : 0xFFC0A44D) : 0xFFC0A44D;
		}
		if (id.contains("crimson")) {
			return isLogOrWood ? (isEndFace ? 0xFF5A1428 : 0xFF42101F) : 0xFF651B32;
		}
		if (id.contains("warped")) {
			return isLogOrWood ? (isEndFace ? 0xFF255E5C : 0xFF163C3B) : 0xFF2B6865;
		}
		if (id.contains("oak")) {
			return isLogOrWood ? (isEndFace ? 0xFFB8945F : 0xFF6A5237) : 0xFFB8945F;
		}
		if (id.contains("plank") || id.contains("wood") || id.contains("log") || id.contains("fence") || id.contains("door") || id.contains("trapdoor") || id.contains("gate")) {
			return 0xFF9E7E4F;
		}

		// 6. Utility & Workstation Furniture
		if (id.contains("bookshelf")) {
			return isEndFace ? 0xFFB8945F : 0xFF875A32;
		}
		if (id.contains("crafting_table")) {
			return face == 0 ? 0xFFC29B63 : (face == 1 ? 0xFFB8945F : 0xFF8F6838);
		}
		if (id.contains("furnace") || id.contains("smoker") || id.contains("blast_furnace")) {
			return (face == 4 || face == 5) ? 0xFF353538 : 0xFF5A5A5E;
		}
		if (id.contains("tnt")) {
			return isEndFace ? 0xFFD1B096 : 0xFFD32F2F;
		}
		if (id.contains("target")) {
			return isEndFace ? 0xFFE0E0E0 : 0xFFD32F2F;
		}
		if (id.contains("chest") || id.contains("barrel")) {
			return 0xFF8D6534;
		}
		if (id.contains("ender_chest")) {
			return 0xFF0F2624;
		}
		if (id.contains("anvil")) {
			return 0xFF38383C;
		}
		if (id.contains("enchanting_table")) {
			return face == 0 ? 0xFF9C27B0 : 0xFF2C1E38;
		}

		// 7. Lighting & Emissives
		if (id.contains("soul_lantern") || id.contains("soul_torch") || id.contains("soul_fire")) {
			return 0xFF00E5FF;
		}
		if (id.contains("lantern") || id.contains("torch") || id.contains("glowstone") || id.contains("lamp") || id.contains("candle")) {
			return 0xFFFFD54F;
		}
		if (id.contains("sea_lantern") || id.contains("beacon") || id.contains("conduit")) {
			return 0xFF80DEEA;
		}
		if (id.contains("shroomlight")) {
			return 0xFFFF8A65;
		}
		if (id.contains("end_rod")) {
			return 0xFFFFFDE7;
		}
		if (id.contains("lightning_rod")) {
			return 0xFFE07A5F;
		}

		// 8. 16 Dye Colors for Wool, Carpet, Concrete, Bed, Shulker, Banner, Glass
		int dye = resolveDyeColor(id);
		if (dye != 0) {
			if (id.contains("glass")) {
				return 0xAA000000 | (dye & 0x00FFFFFF);
			}
			if (id.contains("terracotta")) {
				return getTerracottaColor(id);
			}
			return dye;
		}
		if (id.contains("terracotta")) {
			return 0xFF9E6148;
		}
		if (id.contains("wool") || id.contains("carpet") || id.contains("concrete")) {
			return 0xFFE0E0E0;
		}

		// 9. Translucents & Fluids
		if (id.contains("glass")) return 0xAA80D8FF;
		if (id.contains("ice")) return 0xCC90CAF9;
		if (id.contains("water")) return 0x991E88E5;
		if (id.contains("lava") || id.contains("fire") || id.contains("magma")) return 0xFFFF5722;

		// 10. Metals & Ores
		if (id.contains("gold")) return 0xFFFFD700;
		if (id.contains("iron") || id.contains("chain")) return 0xFFD8D8D8;
		if (id.contains("copper")) return id.contains("oxidized") ? 0xFF52A085 : (id.contains("weathered") ? 0xFF6BA388 : 0xFFC06C4C);
		if (id.contains("diamond")) return 0xFF4DD0E1;
		if (id.contains("emerald")) return 0xFF2ECC71;
		if (id.contains("lapis")) return 0xFF1976D2;
		if (id.contains("redstone")) return 0xFFD32F2F;
		if (id.contains("amethyst")) return 0xFF9C27B0;
		if (id.contains("quartz")) return 0xFFECE6DC;
		if (id.contains("prismarine")) return 0xFF5BB2A5;
		if (id.contains("purpur")) return 0xFFA97FA9;
		if (id.contains("end_stone")) return 0xFFDDD6A5;
		if (id.contains("obsidian")) return 0xFF1B122C;
		if (id.contains("netherrack")) return 0xFF6E2828;
		if (id.contains("nether_brick")) return 0xFF40181D;

		// 11. Masonry & Stone
		if (id.contains("cobblestone") || id.contains("cobble")) return 0xFF737373;
		if (id.contains("deepslate") || id.contains("blackstone") || id.contains("basalt")) return 0xFF3E3E42;
		if (id.contains("granite") || id.contains("brick")) return 0xFF964C3B;
		if (id.contains("sandstone") || id.contains("sand")) return 0xFFD8CB9B;
		if (id.contains("diorite")) return 0xFFD1D1D1;
		if (id.contains("andesite")) return 0xFF858585;
		if (id.contains("stone")) return 0xFF888888;

		return 0xFF7F8C8D;
	}

	private static int resolveDyeColor(String id) {
		if (id.contains("white")) return 0xFFF9F9F9;
		if (id.contains("orange")) return 0xFFF9801D;
		if (id.contains("magenta")) return 0xFFC74EBD;
		if (id.contains("light_blue")) return 0xFF3AB3DA;
		if (id.contains("yellow")) return 0xFFFED83D;
		if (id.contains("lime")) return 0xFF80C71F;
		if (id.contains("pink")) return 0xFFF38BAA;
		if (id.contains("light_gray")) return 0xFF9D9D97;
		if (id.contains("gray")) return 0xFF474F52;
		if (id.contains("cyan")) return 0xFF169C9C;
		if (id.contains("purple")) return 0xFF8932B8;
		if (id.contains("blue")) return 0xFF3C44AA;
		if (id.contains("brown")) return 0xFF835432;
		if (id.contains("green")) return 0xFF5E7C16;
		if (id.contains("red")) return 0xFFB02E26;
		if (id.contains("black")) return 0xFF1D1D21;
		return 0;
	}

	private static int getTerracottaColor(String id) {
		if (id.contains("white")) return 0xFFD1B1A1;
		if (id.contains("orange")) return 0xFFA05325;
		if (id.contains("magenta")) return 0xFF95576C;
		if (id.contains("light_blue")) return 0xFF706C8A;
		if (id.contains("yellow")) return 0xFFBA8524;
		if (id.contains("lime")) return 0xFF677535;
		if (id.contains("pink")) return 0xFFA04D4E;
		if (id.contains("gray")) return 0xFF392923;
		if (id.contains("light_gray")) return 0xFF876B61;
		if (id.contains("cyan")) return 0xFF575B5B;
		if (id.contains("purple")) return 0xFF764555;
		if (id.contains("blue")) return 0xFF4A3B5B;
		if (id.contains("brown")) return 0xFF4D3224;
		if (id.contains("green")) return 0xFF4C522A;
		if (id.contains("red")) return 0xFF8E3C2E;
		if (id.contains("black")) return 0xFF251610;
		return 0xFF9E6148;
	}

	private static int shadeColor(int argb, float factor) {
		int a = (argb >> 24) & 0xFF;
		if (a == 0) a = 0xFF;
		int r = (int) (((argb >> 16) & 0xFF) * factor);
		int g = (int) (((argb >> 8) & 0xFF) * factor);
		int b = (int) ((argb & 0xFF) * factor);
		r = MathHelper.clamp(r, 0, 255);
		g = MathHelper.clamp(g, 0, 255);
		b = MathHelper.clamp(b, 0, 255);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
