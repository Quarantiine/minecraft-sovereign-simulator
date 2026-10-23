package com.example.client.gui;

import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.client.ExampleModClient;
import com.example.client.network.ModClientNetworking;
import com.example.client.renderer.ClientBuildCaptureTracker;
import com.example.client.renderer.ClientDesignCaptureTracker;
import com.example.client.renderer.ClientMiningCaptureTracker;
import com.example.client.resource.ClientResourceEstimatorTracker;
import com.example.component.BuildingMode;
import com.example.component.CommandMode;
import com.example.network.SyncResourceEstimationPayload;
import com.example.component.MiningMode;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import com.example.item.custom.CommandScepterItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Interactive Client GUI for the Loki Command Scepter.
 * Allows switching scepter operating modes, inspecting/selecting architectural blueprints,
 * observing nearby minions, configuring DESIGN spatial capture, and broadcasting tactical directives via C2S packets.
 */
public class CommandScepterScreen extends Screen {

	private static final int WINDOW_WIDTH = 340;
	private static final int WINDOW_HEIGHT = 280;
	private static final int BLUEPRINT_PAGE_SIZE = 3;
	public static final int PATHWAY_PAGE_SIZE = 4;

	private Hand hand;
	private ItemStack scepterStack;
	private CommandMode selectedMode;
	private MiningMode selectedMiningMode = MiningMode.AREA;
	private BuildingMode selectedBuildingMode = BuildingMode.BLUEPRINT;
	private String selectedBlueprintId;
	private SquadGroup selectedSquad;
	private MinionRole selectedRole;
	private int nearbyThralls = 0;
	private int nearbySelectedThralls = 0;
	private int blueprintPage = 0;
	private int pathwayPage = 0;

	private int selectedRotation = 0;
	private int activePatrolRouteId = 0;

	private final List<ButtonWidget> squadButtons = new ArrayList<>();
	private final List<ButtonWidget> roleButtons = new ArrayList<>();
	private final List<ButtonWidget> modeButtons = new ArrayList<>();
	private final List<ButtonWidget> blueprintButtons = new ArrayList<>();
	private final List<ButtonWidget> blueprintDeleteButtons = new ArrayList<>();
	private final List<ButtonWidget> pathwayWidgets = new ArrayList<>();
	private final List<ButtonWidget> pathwaySelectButtons = new ArrayList<>();
	private final List<ButtonWidget> pathwayModeButtons = new ArrayList<>();
	private final List<ButtonWidget> pathwayEditButtons = new ArrayList<>();
	private final List<ButtonWidget> pathwayClearButtons = new ArrayList<>();
	private final List<ButtonWidget> pathwayDeleteButtons = new ArrayList<>();
	private ButtonWidget addRouteBtn;
	private ButtonWidget prevPathwayPageBtn;
	private ButtonWidget nextPathwayPageBtn;

	public static final int BARRIER_PAGE_SIZE = 4;
	private int barrierPage = 0;
	private final List<ButtonWidget> barrierDashboardWidgets = new ArrayList<>();
	private final List<ButtonWidget> barrierSelectButtons = new ArrayList<>();
	private final List<ButtonWidget> barrierEditButtons = new ArrayList<>();
	private final List<ButtonWidget> barrierDeleteButtons = new ArrayList<>();
	private ButtonWidget prevBarrierPageBtn;
	private ButtonWidget nextBarrierPageBtn;
	private ButtonWidget rotateBtn;
	private ButtonWidget materialBomBtn;
	private ButtonWidget buildingModeBtn;
	private ButtonWidget buildConfirmModalBtn;
	private ButtonWidget clearBuildCornersBtn;
	private ButtonWidget sealBarrierBtn;
	private ButtonWidget clearBarrierBtn;
	private ButtonWidget captureModalBtn;
	private ButtonWidget clearCornersBtn;
	private ButtonWidget miningModeBtn;
	private ButtonWidget miningConfirmModalBtn;
	private ButtonWidget clearMineCornersBtn;
	private ButtonWidget prevPageBtn;
	private ButtonWidget nextPageBtn;
	private ButtonWidget targetFilterBtn;
	private ButtonWidget explorerBtn;

	public enum ConfirmationType {
		NONE,
		DELETE_BLUEPRINT,
		DISMISS_MINIONS
	}

	private ConfirmationType pendingConfirmation = ConfirmationType.NONE;
	private StructureBlueprint pendingBlueprintToDelete = null;

	private ButtonWidget confirmDeleteBpBtn;
	private ButtonWidget confirmDismissSelectedBtn;
	private ButtonWidget confirmDismissAllBtn;
	private ButtonWidget cancelModalBtn;

	private boolean shiftHeldOnOpen = false;
	private boolean initializedOpenState = false;
	private boolean closed = false;

	public CommandScepterScreen(Hand hand, ItemStack scepterStack) {
		super(Text.translatable("gui.modid-mmcli-agent-modding.command_hub.title"));
		this.hand = hand;
		this.scepterStack = scepterStack;
		if (scepterStack != null && !scepterStack.isEmpty()) {
			this.selectedMode = CommandScepterItem.getMode(scepterStack);
			this.selectedMiningMode = CommandScepterItem.getMiningMode(scepterStack);
			this.selectedBuildingMode = CommandScepterItem.getBuildingMode(scepterStack);
			this.selectedBlueprintId = CommandScepterItem.getBlueprintId(scepterStack);
			if ((this.selectedBlueprintId == null || this.selectedBlueprintId.isBlank()) && !BlueprintRegistry.getAll().isEmpty()) {
				this.selectedBlueprintId = BlueprintRegistry.getAll().iterator().next().getId();
			}
			this.selectedSquad = CommandScepterItem.getTargetSquad(scepterStack);
			this.selectedRole = null;
			this.selectedRotation = CommandScepterItem.getRotationIndex(scepterStack);
			int heldRoute = CommandScepterItem.getActivePatrolRoute(scepterStack);
			List<com.example.patrol.PatrolRoute> existingRoutes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
			if (!existingRoutes.isEmpty()) {
				boolean found = false;
				for (com.example.patrol.PatrolRoute r : existingRoutes) {
					if (r.routeId() == heldRoute) {
						found = true;
						break;
					}
				}
				this.activePatrolRouteId = found ? heldRoute : existingRoutes.get(0).routeId();
			} else {
				this.activePatrolRouteId = heldRoute;
			}
		} else {
			this.selectedMode = CommandMode.FOLLOW;
			this.selectedMiningMode = MiningMode.AREA;
			this.selectedBuildingMode = BuildingMode.BLUEPRINT;
			this.selectedBlueprintId = BlueprintRegistry.getAll().isEmpty() ? "" : BlueprintRegistry.getAll().iterator().next().getId();
			this.selectedSquad = SquadGroup.ALL;
			this.selectedRole = null;
			this.selectedRotation = 0;
			List<com.example.patrol.PatrolRoute> existingRoutes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
			this.activePatrolRouteId = existingRoutes.isEmpty() ? 0 : existingRoutes.get(0).routeId();
		}
		this.shiftHeldOnOpen = isShiftOrSneakDown();
	}

	public CommandScepterScreen() {
		this(Hand.MAIN_HAND, null);
	}

	@Override
	protected void init() {
		super.init();
		this.squadButtons.clear();
		this.roleButtons.clear();
		this.modeButtons.clear();
		this.blueprintButtons.clear();
		this.blueprintDeleteButtons.clear();
		this.pathwayWidgets.clear();
		this.pathwaySelectButtons.clear();
		this.pathwayModeButtons.clear();
		this.pathwayEditButtons.clear();
		this.pathwayClearButtons.clear();
		this.pathwayDeleteButtons.clear();
		this.barrierDashboardWidgets.clear();
		this.barrierSelectButtons.clear();
		this.barrierEditButtons.clear();
		this.barrierDeleteButtons.clear();

		if (!this.initializedOpenState) {
			if (!this.shiftHeldOnOpen) {
				this.shiftHeldOnOpen = isShiftOrSneakDown();
			}
			this.initializedOpenState = true;
		}

		if (this.client != null && this.client.world != null && this.client.player != null) {
			List<MinionEntity> minions = this.client.world.getEntitiesByClass(
				MinionEntity.class,
				this.client.player.getBoundingBox().expand(CommandScepterItem.MINION_COMMAND_RADIUS),
				m -> m.isAlive() && m.isOwner(this.client.player)
			);
			this.nearbyThralls = minions.size();
			this.nearbySelectedThralls = (int) minions.stream().filter(MinionEntity::isSelected).count();
		}

		if (this.selectedBlueprintId != null && !this.selectedBlueprintId.isBlank()) {
			ModClientNetworking.sendRequestResourceEstimation(this.selectedBlueprintId);
		}

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// 5 Squad Filter Buttons: ALL, ALPHA, BRAVO, CHARLIE, DELTA
		SquadGroup[] squads = SquadGroup.values();
		int squadBtnWidth = 58;
		int squadGap = 5;
		int squadStartX = startX + 15;
		int squadY = startY + 46;
		for (int i = 0; i < squads.length; i++) {
			SquadGroup squad = squads[i];
			int btnX = squadStartX + i * (squadBtnWidth + squadGap);
			ButtonWidget btn = ButtonWidget.builder(
				getSquadButtonText(squad),
				b -> selectSquad(squad)
			)
			.dimensions(btnX, squadY, squadBtnWidth, 20)
			.tooltip(getSquadTooltip(squad))
			.build();

			this.squadButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// 4 Minion Role Archetype Buttons
		MinionRole[] roles = MinionRole.values();
		int roleBtnWidth = 72;
		int roleGap = 6;
		int roleStartX = startX + 17;
		int roleY = startY + 80;
		for (int i = 0; i < roles.length; i++) {
			MinionRole role = roles[i];
			int btnX = roleStartX + i * (roleBtnWidth + roleGap);
			ButtonWidget btn = ButtonWidget.builder(
				getRoleButtonText(role),
				b -> toggleRole(role)
			)
			.dimensions(btnX, roleY, roleBtnWidth, 20)
			.tooltip(getRoleTooltip(role))
			.build();

			this.roleButtons.add(btn);
			this.addDrawableChild(btn);
		}


		// Command Mode Buttons in a 2-column grid (8 modes)
		CommandMode[] modes = CommandMode.values();
		for (int i = 0; i < modes.length; i++) {
			CommandMode mode = modes[i];
			int col = i % 2;
			int row = i / 2;
			int btnX = startX + 16 + col * 70;
			int btnY = startY + 118 + row * 21;

			ButtonWidget btn = ButtonWidget.builder(getModeButtonText(mode), b -> selectMode(mode))
				.dimensions(btnX, btnY, 66, 19)
				.build();

			this.modeButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// Minion Target Filter Button: positioned symmetrically above column 1 in header area
		this.targetFilterBtn = ButtonWidget.builder(
			Text.literal("§c⚔ Targets"),
			b -> openTargetFilterModal()
		)
		.dimensions(startX + 87, startY + 100, 65, 15)
		.tooltip(Tooltip.of(Text.literal("§c⚔ Minion Target Filter\n§7Choose which mobs your Auto, Sentinel & Warrior\nminions are allowed to attack on sight.")))
		.build();
		this.addDrawableChild(this.targetFilterBtn);

		// Architectural Blueprint Catalog with fixed 3-item viewport and pagination controls
		List<StructureBlueprint> blueprints = new ArrayList<>(BlueprintRegistry.getAll());
		for (int i = 0; i < blueprints.size(); i++) {
			if (blueprints.get(i).getId().equalsIgnoreCase(this.selectedBlueprintId)) {
				this.blueprintPage = i / BLUEPRINT_PAGE_SIZE;
				break;
			}
		}

		for (int slot = 0; slot < BLUEPRINT_PAGE_SIZE; slot++) {
			final int slotIndex = slot;
			int btnX = startX + 165;
			int btnY = startY + 118 + slot * 36;

			ButtonWidget btn = ButtonWidget.builder(Text.empty(), b -> {
				int index = this.blueprintPage * BLUEPRINT_PAGE_SIZE + slotIndex;
				List<StructureBlueprint> bps = new ArrayList<>(BlueprintRegistry.getAll());
				if (index >= 0 && index < bps.size()) {
					selectBlueprint(bps.get(index).getId());
				}
			})
			.dimensions(btnX, btnY, 160, 30)
			.build();

			this.blueprintButtons.add(btn);
			this.addDrawableChild(btn);

			ButtonWidget delBtn = ButtonWidget.builder(Text.literal("§c✕"), b -> {
				int index = this.blueprintPage * BLUEPRINT_PAGE_SIZE + slotIndex;
				List<StructureBlueprint> bps = new ArrayList<>(BlueprintRegistry.getAll());
				if (index >= 0 && index < bps.size()) {
					StructureBlueprint target = bps.get(index);
					if (BlueprintRegistry.isCustom(target.getId())) {
						requestDeleteBlueprintConfirmation(target);
					}
				}
			})
			.dimensions(startX + 303, btnY + 4, 22, 22)
			.tooltip(Tooltip.of(Text.literal("§c✕ Remove Custom Blueprint\n§7Delete this custom blueprint from your active catalog.")))
			.build();
			delBtn.visible = false;
			delBtn.active = false;

			this.blueprintDeleteButtons.add(delBtn);
			this.addDrawableChild(delBtn);
		}

		// Blueprint Catalog Pagination Controls
		int pageControlsY = startY + 226;
		this.prevPageBtn = ButtonWidget.builder(Text.literal("◀"), b -> {
			if (this.blueprintPage > 0) {
				this.blueprintPage--;
				updateBlueprintButtons();
			}
		})
		.dimensions(startX + 165, pageControlsY, 20, 18)
		.tooltip(Tooltip.of(Text.literal("Previous Page")))
		.build();
		this.addDrawableChild(this.prevPageBtn);


		this.nextPageBtn = ButtonWidget.builder(Text.literal("▶"), b -> {
			int totalPages = Math.max(1, (BlueprintRegistry.getAll().size() + BLUEPRINT_PAGE_SIZE - 1) / BLUEPRINT_PAGE_SIZE);
			if (this.blueprintPage + 1 < totalPages) {
				this.blueprintPage++;
				updateBlueprintButtons();
			}
		})
		.dimensions(startX + 305, pageControlsY, 20, 18)
		.tooltip(Tooltip.of(Text.literal("Next Page")))
		.build();
		this.addDrawableChild(this.nextPageBtn);

		// Building Mode Toggle Button (visible in BUILD mode)
		this.buildingModeBtn = ButtonWidget.builder(
			getBuildingModeButtonText(),
			b -> toggleBuildingModeGui()
		)
		.dimensions(startX + 16, pageControlsY, 44, 19)
		.tooltip(getBuildingModeTooltip())
		.build();
		this.buildingModeBtn.visible = this.selectedMode == CommandMode.BUILD;
		this.addDrawableChild(this.buildingModeBtn);

		// Blueprint Rotation Button (visible in BUILD mode with BLUEPRINT sub-mode)
		this.rotateBtn = ButtonWidget.builder(
			getRotateButtonText(),
			b -> cycleRotationGui()
		)
		.dimensions(startX + 62, pageControlsY, 46, 19)
		.tooltip(Tooltip.of(Text.literal("§6✦ Rotate Blueprint 90° Clockwise\n§7In-Game: Press [R] or Left-Click with Scepter.")))
		.build();
		this.rotateBtn.visible = this.selectedMode == CommandMode.BUILD && this.selectedBuildingMode == BuildingMode.BLUEPRINT;
		this.addDrawableChild(this.rotateBtn);

		// Material Bill of Materials (BOM) & Resource Estimator Button (visible in BUILD mode with BLUEPRINT sub-mode)
		this.materialBomBtn = ButtonWidget.builder(
			Text.literal("§e📋 BOM"),
			b -> openResourceEstimatorModal()
		)
		.dimensions(startX + 110, pageControlsY, 48, 19)
		.tooltip(Tooltip.of(Text.literal("§e📋 Material Bill of Materials (BOM)\n§7Inspect live raw material delta across your inventory\nand nearby minion backpacks before anchoring.")))
		.build();
		this.materialBomBtn.visible = this.selectedMode == CommandMode.BUILD && this.selectedBuildingMode == BuildingMode.BLUEPRINT;
		this.addDrawableChild(this.materialBomBtn);

		// Build Area Confirm Modal Button (visible in BUILD mode with AREA sub-mode)
		this.buildConfirmModalBtn = ButtonWidget.builder(
			Text.literal("§3✦ Fill"),
			b -> openBuildAreaConfirmModal()
		)
		.dimensions(startX + 62, pageControlsY, 52, 19)
		.tooltip(Tooltip.of(Text.literal("§3✦ Confirm Area Fill\n§7Open modal to choose block and start volumetric construction.")))
		.build();
		this.buildConfirmModalBtn.visible = this.selectedMode == CommandMode.BUILD && this.selectedBuildingMode == BuildingMode.AREA;
		this.addDrawableChild(this.buildConfirmModalBtn);

		// Clear Build Corners Button (visible in BUILD mode with AREA sub-mode)
		this.clearBuildCornersBtn = ButtonWidget.builder(
			Text.literal("§6⌫ Reset"),
			b -> clearBuildCornersGui()
		)
		.dimensions(startX + 116, pageControlsY, 42, 19)
		.tooltip(Tooltip.of(Text.literal("§6⌫ Reset Build Corners\n§7Clears Pos1 and Pos2 fill selection.")))
		.build();
		this.clearBuildCornersBtn.visible = this.selectedMode == CommandMode.BUILD && this.selectedBuildingMode == BuildingMode.AREA;
		this.addDrawableChild(this.clearBuildCornersBtn);

		// Seal Barrier Button (visible in BUILD mode with BARRIER sub-mode)
		this.sealBarrierBtn = ButtonWidget.builder(
			Text.literal("§6✦ Seal"),
			b -> sealBarrierGui()
		)
		.dimensions(startX + 62, pageControlsY, 52, 19)
		.tooltip(Tooltip.of(Text.literal("§6✦ Seal Barrier\n§7Opens modal to seal perimeter.\n§7Stops minions from harvesting inside.")))
		.build();
		this.sealBarrierBtn.visible = this.selectedMode == CommandMode.BUILD && this.selectedBuildingMode == BuildingMode.BARRIER;
		this.addDrawableChild(this.sealBarrierBtn);

		// Clear Barrier Nodes Button (visible in BUILD mode with BARRIER sub-mode)
		this.clearBarrierBtn = ButtonWidget.builder(
			Text.literal("§c⌫ Reset"),
			b -> clearBarrierNodesGui()
		)
		.dimensions(startX + 116, pageControlsY, 44, 19)
		.tooltip(Tooltip.of(Text.literal("§c⌫ Reset Barrier Nodes\n§7Clears all pending boundary points.")))
		.build();
		this.clearBarrierBtn.visible = this.selectedMode == CommandMode.BUILD && this.selectedBuildingMode == BuildingMode.BARRIER;
		this.addDrawableChild(this.clearBarrierBtn);

		// 3D Architectural Blueprint Explorer Button (visible in BUILD mode)
		this.explorerBtn = ButtonWidget.builder(
			Text.literal("§6🔍 3D View"),
			b -> openBlueprintExplorer()
		)
		.dimensions(startX + 260, startY + 100, 65, 15)
		.tooltip(Tooltip.of(Text.literal("§6✦ 3D Architectural Blueprint Explorer\n§7Browse, search, rotate structures in 3D, and inspect specifications.")))
		.build();
		this.explorerBtn.visible = this.selectedMode == CommandMode.BUILD && (this.selectedBuildingMode == BuildingMode.BLUEPRINT);
		this.addDrawableChild(this.explorerBtn);

		// Spatial Capture Modal Button (visible in DESIGN mode)
		this.captureModalBtn = ButtonWidget.builder(
			Text.literal("§d✦ Capture"),
			b -> openCaptureModal()
		)
		.dimensions(startX + 16, pageControlsY, 68, 19)
		.tooltip(Tooltip.of(Text.literal("§d✦ Open Blueprint Capture Modal\n§7Compile and name the in-world volume between Pos1 and Pos2.")))
		.build();
		this.captureModalBtn.visible = this.selectedMode == CommandMode.DESIGN;
		this.addDrawableChild(this.captureModalBtn);

		// Clear Corners Button (visible in DESIGN mode)
		this.clearCornersBtn = ButtonWidget.builder(
			Text.literal("§6⌫ Reset"),
			b -> clearCornersGui()
		)
		.dimensions(startX + 88, pageControlsY, 70, 19)
		.tooltip(Tooltip.of(Text.literal("§6⌫ Reset Corner Points\n§7Clears Pos1 and Pos2 selection.")))
		.build();
		this.clearCornersBtn.visible = this.selectedMode == CommandMode.DESIGN;
		this.addDrawableChild(this.clearCornersBtn);

		// Mining Mode Toggle Button (hidden as mining is strictly custom area)
		this.miningModeBtn = ButtonWidget.builder(
			Text.literal("§eCustom Area"),
			b -> {}
		)
		.dimensions(startX + 16, pageControlsY, 44, 19)
		.build();
		this.miningModeBtn.visible = false;
		this.addDrawableChild(this.miningModeBtn);

		// Mining Confirm Modal Button (visible in MINE mode)
		this.miningConfirmModalBtn = ButtonWidget.builder(
			Text.literal("§e⛏ Confirm Area"),
			b -> openMiningConfirmModal()
		)
		.dimensions(startX + 16, pageControlsY, 92, 19)
		.tooltip(Tooltip.of(Text.literal("§e✦ Confirm Mining Area\n§7Open modal to inspect area volume and start 3D quarry excavation.")))
		.build();
		this.miningConfirmModalBtn.visible = this.selectedMode == CommandMode.MINE;
		this.addDrawableChild(this.miningConfirmModalBtn);

		// Clear Mining Corners Button (visible in MINE mode)
		this.clearMineCornersBtn = ButtonWidget.builder(
			Text.literal("§6⌫ Reset"),
			b -> clearMineCornersGui()
		)
		.dimensions(startX + 112, pageControlsY, 46, 19)
		.tooltip(Tooltip.of(Text.literal("§6⌫ Reset Mining Corners\n§7Clears Pos1 and Pos2 mining selection.")))
		.build();
		this.clearMineCornersBtn.visible = this.selectedMode == CommandMode.MINE;
		this.addDrawableChild(this.clearMineCornersBtn);

		// Pathway Route Dashboard Controls (Paginated 4 Channel Rows per page: Select, Mode, Edit, Clear, Delete)
		for (int slot = 0; slot < PATHWAY_PAGE_SIZE; slot++) {
			final int slotIndex = slot;
			int rowY = startY + 118 + slot * 24;

			ButtonWidget selectBtn = ButtonWidget.builder(Text.empty(), b -> {
				int index = this.pathwayPage * PATHWAY_PAGE_SIZE + slotIndex;
				List<com.example.patrol.PatrolRoute> routes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
				if (index >= 0 && index < routes.size()) {
					selectActiveRoute(routes.get(index).routeId());
				}
			})
			.dimensions(startX + 165, rowY, 70, 20)
			.build();
			this.pathwaySelectButtons.add(selectBtn);
			this.pathwayWidgets.add(selectBtn);
			this.addDrawableChild(selectBtn);

			ButtonWidget modeBtn = ButtonWidget.builder(Text.empty(), b -> {
				int index = this.pathwayPage * PATHWAY_PAGE_SIZE + slotIndex;
				List<com.example.patrol.PatrolRoute> routes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
				if (index >= 0 && index < routes.size()) {
					toggleRouteMode(routes.get(index).routeId());
				}
			})
			.dimensions(startX + 237, rowY, 26, 20)
			.build();
			this.pathwayModeButtons.add(modeBtn);
			this.pathwayWidgets.add(modeBtn);
			this.addDrawableChild(modeBtn);

			ButtonWidget editBtn = ButtonWidget.builder(Text.literal("§e✎"), b -> {
				int index = this.pathwayPage * PATHWAY_PAGE_SIZE + slotIndex;
				List<com.example.patrol.PatrolRoute> routes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
				if (index >= 0 && index < routes.size()) {
					openEditRouteModal(routes.get(index));
				}
			})
			.dimensions(startX + 265, rowY, 18, 20)
			.tooltip(Tooltip.of(Text.literal("§e✎ Edit Route\n§7Configure name, hex color, and behavior.")))
			.build();
			this.pathwayEditButtons.add(editBtn);
			this.pathwayWidgets.add(editBtn);
			this.addDrawableChild(editBtn);

			ButtonWidget clearBtn = ButtonWidget.builder(Text.literal("§6⌫"), b -> {
				int index = this.pathwayPage * PATHWAY_PAGE_SIZE + slotIndex;
				List<com.example.patrol.PatrolRoute> routes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
				if (index >= 0 && index < routes.size()) {
					clearRoute(routes.get(index).routeId());
				}
			})
			.dimensions(startX + 285, rowY, 18, 20)
			.tooltip(Tooltip.of(Text.literal("§6⌫ Clear Waypoints\n§7Remove all waypoints from this route.")))
			.build();
			this.pathwayClearButtons.add(clearBtn);
			this.pathwayWidgets.add(clearBtn);
			this.addDrawableChild(clearBtn);

			ButtonWidget delBtn = ButtonWidget.builder(Text.literal("§c✕"), b -> {
				int index = this.pathwayPage * PATHWAY_PAGE_SIZE + slotIndex;
				List<com.example.patrol.PatrolRoute> routes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
				if (index >= 0 && index < routes.size()) {
					deleteRouteGui(routes.get(index).routeId());
				}
			})
			.dimensions(startX + 305, rowY, 20, 20)
			.tooltip(Tooltip.of(Text.literal("§c✕ Delete Route\n§7Permanently delete this patrol route.")))
			.build();
			this.pathwayDeleteButtons.add(delBtn);
			this.pathwayWidgets.add(delBtn);
			this.addDrawableChild(delBtn);
		}

		// Pathway Dashboard Pagination & Add Route Controls
		int pathwayControlsY = startY + 226;
		this.prevPathwayPageBtn = ButtonWidget.builder(Text.literal("◀"), b -> {
			if (this.pathwayPage > 0) {
				this.pathwayPage--;
				updatePathwayButtons();
			}
		})
		.dimensions(startX + 165, pathwayControlsY, 18, 18)
		.tooltip(Tooltip.of(Text.literal("Previous Page")))
		.build();
		this.pathwayWidgets.add(this.prevPathwayPageBtn);
		this.addDrawableChild(this.prevPathwayPageBtn);

		this.addRouteBtn = ButtonWidget.builder(Text.literal("§a+ New Route"), b -> openAddRouteModal())
			.dimensions(startX + 185, pathwayControlsY, 118, 18)
			.tooltip(Tooltip.of(Text.literal("§a+ Add New Patrol Route\n§7Create a new custom pathway with custom hex color.")))
			.build();
		this.pathwayWidgets.add(this.addRouteBtn);
		this.addDrawableChild(this.addRouteBtn);

		this.nextPathwayPageBtn = ButtonWidget.builder(Text.literal("▶"), b -> {
			List<com.example.patrol.PatrolRoute> routes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
			int totalPages = Math.max(1, (routes.size() + PATHWAY_PAGE_SIZE - 1) / PATHWAY_PAGE_SIZE);
			if (this.pathwayPage + 1 < totalPages) {
				this.pathwayPage++;
				updatePathwayButtons();
			}
		})
		.dimensions(startX + 305, pathwayControlsY, 18, 18)
		.tooltip(Tooltip.of(Text.literal("Next Page")))
		.build();
		this.pathwayWidgets.add(this.nextPathwayPageBtn);
		this.addDrawableChild(this.nextPathwayPageBtn);

		// Barrier Dashboard Controls (Paginated 4 Channel Rows: Select/Resume, Edit/Rename, Delete)
		for (int slot = 0; slot < BARRIER_PAGE_SIZE; slot++) {
			final int slotIndex = slot;
			int rowY = startY + 118 + slot * 24;

			ButtonWidget selectBtn = ButtonWidget.builder(Text.empty(), b -> {
				int index = this.barrierPage * BARRIER_PAGE_SIZE + slotIndex;
				List<com.example.barrier.BarrierZone> zones = com.example.client.renderer.ClientBarrierTracker.getConfirmedZones();
				if (index >= 0 && index < zones.size()) {
					com.example.client.renderer.ClientBarrierTracker.resumeZone(zones.get(index).getId());
					if (this.client != null && this.client.player != null) {
						this.client.player.sendMessage(Text.literal("§6✦ Resumed editing " + zones.get(index).getName() + ". Left-click points and snap to any waypoint to Seal!§r"), true);
					}
					this.close();
				}
			})
			.dimensions(startX + 165, rowY, 114, 20)
			.build();
			this.barrierSelectButtons.add(selectBtn);
			this.barrierDashboardWidgets.add(selectBtn);
			this.addDrawableChild(selectBtn);

			ButtonWidget editBtn = ButtonWidget.builder(Text.literal("§e✎"), b -> {
				int index = this.barrierPage * BARRIER_PAGE_SIZE + slotIndex;
				List<com.example.barrier.BarrierZone> zones = com.example.client.renderer.ClientBarrierTracker.getConfirmedZones();
				if (index >= 0 && index < zones.size()) {
					if (this.client != null) {
						this.client.setScreen(new BarrierEditModalScreen(zones.get(index), this));
					}
				}
			})
			.dimensions(startX + 282, rowY, 20, 20)
			.tooltip(Tooltip.of(Text.literal("§e✎ Edit Barrier\n§7Rename or inspect barrier specifications.")))
			.build();
			this.barrierEditButtons.add(editBtn);
			this.barrierDashboardWidgets.add(editBtn);
			this.addDrawableChild(editBtn);

			ButtonWidget delBtn = ButtonWidget.builder(Text.literal("§c✕"), b -> {
				int index = this.barrierPage * BARRIER_PAGE_SIZE + slotIndex;
				List<com.example.barrier.BarrierZone> zones = com.example.client.renderer.ClientBarrierTracker.getConfirmedZones();
				if (index >= 0 && index < zones.size()) {
					int zId = zones.get(index).getId();
					String zName = zones.get(index).getName();
					com.example.client.renderer.ClientBarrierTracker.deleteConfirmedZone(zId);
					if (this.client != null && this.client.player != null) {
						this.client.player.sendMessage(Text.literal("§c✦ Deleted Sovereign Barrier: " + zName + "§r"), true);
						this.client.player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.9F);
					}
					updateBarrierButtons();
				}
			})
			.dimensions(startX + 304, rowY, 20, 20)
			.tooltip(Tooltip.of(Text.literal("§c✕ Delete Barrier\n§7Permanently removes this entire barrier and all its nodes.")))
			.build();
			this.barrierDeleteButtons.add(delBtn);
			this.barrierDashboardWidgets.add(delBtn);
			this.addDrawableChild(delBtn);
		}

		// Barrier Dashboard Pagination
		int barrierControlsY = startY + 226;
		this.prevBarrierPageBtn = ButtonWidget.builder(Text.literal("◀"), b -> {
			if (this.barrierPage > 0) {
				this.barrierPage--;
				updateBarrierButtons();
			}
		})
		.dimensions(startX + 165, barrierControlsY, 18, 18)
		.tooltip(Tooltip.of(Text.literal("Previous Page")))
		.build();
		this.barrierDashboardWidgets.add(this.prevBarrierPageBtn);
		this.addDrawableChild(this.prevBarrierPageBtn);

		this.nextBarrierPageBtn = ButtonWidget.builder(Text.literal("▶"), b -> {
			List<com.example.barrier.BarrierZone> zones = com.example.client.renderer.ClientBarrierTracker.getConfirmedZones();
			int totalPages = Math.max(1, (zones.size() + BARRIER_PAGE_SIZE - 1) / BARRIER_PAGE_SIZE);
			if (this.barrierPage + 1 < totalPages) {
				this.barrierPage++;
				updateBarrierButtons();
			}
		})
		.dimensions(startX + 306, barrierControlsY, 18, 18)
		.tooltip(Tooltip.of(Text.literal("Next Page")))
		.build();
		this.barrierDashboardWidgets.add(this.nextBarrierPageBtn);
		this.addDrawableChild(this.nextBarrierPageBtn);

		updateBlueprintButtons();
		updatePathwayButtons();
		updateBarrierButtons();
		updateControlsVisibility();

		// Action Buttons: Execute Directive, Deselect All, Teleport Minions, Dismiss All Minions, & Close
		int bottomY = startY + 250;
		ButtonWidget executeBtn = ButtonWidget.builder(
			Text.translatable("gui.modid-mmcli-agent-modding.command_hub.execute"),
			b -> executeDirective()
		).dimensions(startX + 14, bottomY, 58, 20).build();
		this.addDrawableChild(executeBtn);

		ButtonWidget deselectBtn = ButtonWidget.builder(
			Text.literal("§e✕ Deselect"),
			b -> {
				ModClientNetworking.sendDeselectAllMinions();
				this.close();
			}
		)
		.dimensions(startX + 76, bottomY, 62, 20)
		.tooltip(Tooltip.of(Text.literal("Deselect all nearby minions and anchor them at their posts")))
		.build();
		deselectBtn.active = this.nearbyThralls > 0;
		this.addDrawableChild(deselectBtn);

		ButtonWidget teleportBtn = ButtonWidget.builder(
			Text.literal("§d✦ Teleport"),
			b -> {
				ModClientNetworking.sendTeleportAllMinions();
				this.close();
			}
		)
		.dimensions(startX + 142, bottomY, 60, 20)
		.tooltip(Tooltip.of(this.nearbySelectedThralls > 0
			? Text.literal("§dTeleport " + this.nearbySelectedThralls + " selected minion(s) to you")
			: Text.literal("§7Select minion(s) first to teleport")))
		.build();
		teleportBtn.active = this.nearbySelectedThralls > 0;
		this.addDrawableChild(teleportBtn);

		ButtonWidget dismissBtn = ButtonWidget.builder(
			Text.literal("§c✖ Destroy"),
			b -> requestDismissMinionsConfirmation()
		)
		.dimensions(startX + 206, bottomY, 66, 20)
		.tooltip(Tooltip.of(this.nearbyThralls > 0
			? Text.literal("§cDecommission nearby owned minions\n§7Allows destroying Selected or All thralls.")
			: Text.translatable("message.modid-mmcli-agent-modding.no_minions_to_dismiss")))
		.build();
		dismissBtn.active = this.nearbyThralls > 0;
		this.addDrawableChild(dismissBtn);

		ButtonWidget closeBtn = ButtonWidget.builder(
			Text.translatable("gui.modid-mmcli-agent-modding.command_hub.close"),
			b -> this.close()
		)
		.dimensions(startX + 276, bottomY, 50, 20)
		.tooltip(Tooltip.of(Text.translatable("tooltip.modid-mmcli-agent-modding.command_hub.close_desc")))
		.build();
		this.addDrawableChild(closeBtn);

		// Confirmation Modal Buttons (centered in screen)
		int modalW = 240;
		int modalH = 110;
		int modalX = (this.width - modalW) / 2;
		int modalBtnY = (this.height - modalH) / 2 + modalH - 28;

		this.confirmDeleteBpBtn = ButtonWidget.builder(Text.literal("§c✕ Delete"), b -> confirmDeleteCustomBlueprint())
			.dimensions(modalX + 24, modalBtnY, 90, 20)
			.build();
		this.confirmDeleteBpBtn.visible = false;
		this.addDrawableChild(this.confirmDeleteBpBtn);

		this.confirmDismissSelectedBtn = ButtonWidget.builder(Text.literal("§6Selected"), b -> confirmDismissSelectedMinions())
			.dimensions(modalX + 10, modalBtnY, 78, 20)
			.build();
		this.confirmDismissSelectedBtn.visible = false;
		this.addDrawableChild(this.confirmDismissSelectedBtn);

		this.confirmDismissAllBtn = ButtonWidget.builder(Text.literal("§cAll"), b -> confirmDismissAllMinions())
			.dimensions(modalX + 92, modalBtnY, 70, 20)
			.build();
		this.confirmDismissAllBtn.visible = false;
		this.addDrawableChild(this.confirmDismissAllBtn);

		this.cancelModalBtn = ButtonWidget.builder(Text.literal("Cancel"), b -> cancelConfirmation())
			.dimensions(modalX + 166, modalBtnY, 64, 20)
			.build();
		this.cancelModalBtn.visible = false;
		this.addDrawableChild(this.cancelModalBtn);
	}

	private static String getSquadLabel(SquadGroup squad) {
		return switch (squad) {
			case ALL -> "All";
			case ALPHA -> "Alpha";
			case BRAVO -> "Bravo";
			case CHARLIE -> "Charlie";
			case DELTA -> "Delta";
		};
	}

	private static Tooltip getSquadTooltip(SquadGroup squad) {
		String desc = switch (squad) {
			case ALL -> "Direct commands and broadcasts to all minions across all squads.";
			case ALPHA -> "Direct commands exclusively to Minions enrolled in Squad Alpha.";
			case BRAVO -> "Direct commands exclusively to Minions enrolled in Squad Bravo.";
			case CHARLIE -> "Direct commands exclusively to Minions enrolled in Squad Charlie.";
			case DELTA -> "Direct commands exclusively to Minions enrolled in Squad Delta.";
		};
		return Tooltip.of(Text.literal("§6✦ Channel: " + squad.getFormattedName() + "\n§7" + desc + "\n§eClick to select this squad channel."));
	}

	private Text getSquadButtonText(SquadGroup squad) {
		boolean isSelected = squad == this.selectedSquad;
		String prefix = isSelected ? "§6▶ " : "";
		return Text.literal(prefix + squad.getColorCode() + getSquadLabel(squad));
	}

	private Text getRoleButtonText(MinionRole role) {
		boolean isSelected = role == this.selectedRole;
		String prefix = isSelected ? "§6▶ " : "";
		return Text.literal(prefix + role.getColorCode() + role.getDisplayName());
	}

	private Tooltip getRoleTooltip(MinionRole role) {
		boolean isSelected = role == this.selectedRole;
		String desc = isSelected
			? "§a[Active Selection] Minions inside rally ring will transform.\n§eClick again to deselect."
			: "§7Click to select this archetype for channeled rally transfiguration.";
		return Tooltip.of(Text.literal("§6✦ Archetype: " + role.getFormattedName() + " §7(" + role.getIcon() + ")\n" + desc));
	}

	private Text getModeButtonText(CommandMode mode) {
		boolean isSelected = mode == this.selectedMode;
		String prefix = isSelected ? "§6▶ " : "";
		return Text.literal(prefix + mode.getColorCode() + mode.getDisplayName());
	}

	private Text getBlueprintButtonText(StructureBlueprint bp) {
		boolean isSelected = bp.getId().equalsIgnoreCase(this.selectedBlueprintId);
		BlockBox box = bp.getBoundingBox();
		int dimX = box.getBlockCountX();
		int dimY = box.getBlockCountY();
		int dimZ = box.getBlockCountZ();

		String prefix = isSelected ? "§6✦ " : "§f";
		SyncResourceEstimationPayload est = ClientResourceEstimatorTracker.getEstimation(bp.getId());
		String readinessBadge = "";
		if (est != null && est.totalBlocks() > 0) {
			int pct = est.getReadinessPercentage();
			readinessBadge = est.isFullySatisfied() ? " §a✔100%" : (pct > 0 ? " §6" + pct + "%" : " §c0%");
		}
		return Text.literal(prefix + bp.getName() + readinessBadge + "\n§8" + dimX + "x" + dimY + "x" + dimZ + " §8| §e" + bp.getBlockCount() + "b");
	}

	private Text getBlueprintTooltip(StructureBlueprint bp) {
		SyncResourceEstimationPayload est = ClientResourceEstimatorTracker.getEstimation(bp.getId());
		StringBuilder sb = new StringBuilder();
		sb.append("§6✦ ").append(bp.getName()).append(" §7(").append(bp.getBlockCount()).append(" blocks)\n");
		if (est != null && est.totalBlocks() > 0) {
			int pct = est.getReadinessPercentage();
			String color = pct >= 100 ? "§a" : (pct >= 50 ? "§6" : "§c");
			sb.append(color).append("Readiness: ").append(pct).append("% (").append(est.totalAvailable()).append("/").append(est.totalBlocks()).append(" blocks)\n");
			sb.append("§8Nearby Minions Contributing: §e").append(est.nearbyMinionsCount()).append("\n");
			sb.append("§8Nearby Chests Contributing: §6").append(est.nearbyContainersCount()).append("\n");
		}
		sb.append("§7Click to select blueprint.\n§e[ 📋 Material BOM ] to inspect item delta.");
		return Text.literal(sb.toString());
	}

	private void selectSquad(SquadGroup squad) {
		this.selectedSquad = squad;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setTargetSquad(this.scepterStack, squad);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	public void toggleRole(MinionRole role) {
		if (this.selectedRole == role) {
			this.selectedRole = null;
		} else {
			this.selectedRole = role;
		}
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setTargetRole(this.scepterStack, this.selectedRole);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	public void setSelectedRole(MinionRole role) {
		this.selectedRole = role;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setTargetRole(this.scepterStack, this.selectedRole);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	public MinionRole getSelectedRole() {
		return this.selectedRole;
	}

	public void updateControlsVisibility() {
		boolean isBuild = this.selectedMode == CommandMode.BUILD;
		boolean isDesign = this.selectedMode == CommandMode.DESIGN;
		boolean isMine = this.selectedMode == CommandMode.MINE;
		boolean isPathway = this.selectedMode == CommandMode.PATHWAY;

		for (ButtonWidget btn : this.roleButtons) {
			btn.visible = !isBuild && !isDesign && !isMine;
		}
		if (this.buildingModeBtn != null) {
			this.buildingModeBtn.visible = isBuild;
		}
		if (this.rotateBtn != null) {
			this.rotateBtn.visible = isBuild && (this.selectedBuildingMode == BuildingMode.BLUEPRINT);
		}
		if (this.materialBomBtn != null) {
			this.materialBomBtn.visible = isBuild && (this.selectedBuildingMode == BuildingMode.BLUEPRINT);
		}
		if (this.explorerBtn != null) {
			this.explorerBtn.visible = isBuild && (this.selectedBuildingMode == BuildingMode.BLUEPRINT);
		}
		if (this.buildConfirmModalBtn != null) {
			this.buildConfirmModalBtn.visible = isBuild && (this.selectedBuildingMode == BuildingMode.AREA);
		}
		if (this.clearBuildCornersBtn != null) {
			this.clearBuildCornersBtn.visible = isBuild && (this.selectedBuildingMode == BuildingMode.AREA);
		}
		if (this.sealBarrierBtn != null) {
			this.sealBarrierBtn.visible = isBuild && (this.selectedBuildingMode == BuildingMode.BARRIER);
		}
		if (this.clearBarrierBtn != null) {
			this.clearBarrierBtn.visible = isBuild && (this.selectedBuildingMode == BuildingMode.BARRIER);
		}
		if (this.captureModalBtn != null) {
			this.captureModalBtn.visible = isDesign;
		}
		if (this.clearCornersBtn != null) {
			this.clearCornersBtn.visible = isDesign;
		}
		if (this.miningModeBtn != null) {
			this.miningModeBtn.visible = false;
		}
		if (this.miningConfirmModalBtn != null) {
			this.miningConfirmModalBtn.visible = isMine;
		}
		if (this.clearMineCornersBtn != null) {
			this.clearMineCornersBtn.visible = isMine;
		}

		boolean hideBlueprints = isPathway || (this.selectedMode == CommandMode.DEFAULT) || (isBuild && (this.selectedBuildingMode == BuildingMode.AREA || this.selectedBuildingMode == BuildingMode.BARRIER));
		for (ButtonWidget btn : this.blueprintButtons) {
			btn.visible = !hideBlueprints;
		}
		for (ButtonWidget btn : this.blueprintDeleteButtons) {
			btn.visible = !hideBlueprints && btn.visible;
		}
		if (this.prevPageBtn != null) {
			this.prevPageBtn.visible = !hideBlueprints;
		}
		if (this.nextPageBtn != null) {
			this.nextPageBtn.visible = !hideBlueprints;
		}

		for (ButtonWidget btn : this.pathwayWidgets) {
			btn.visible = isPathway;
		}
		if (isPathway) {
			updatePathwayButtons();
		}

		boolean isBarrier = isBuild && (this.selectedBuildingMode == BuildingMode.BARRIER);
		for (ButtonWidget btn : this.barrierDashboardWidgets) {
			btn.visible = isBarrier;
		}
		if (isBarrier) {
			updateBarrierButtons();
		}
	}

	public List<ButtonWidget> getPathwayWidgets() {
		return this.pathwayWidgets;
	}

	private Text getRouteChannelButtonText(com.example.patrol.PatrolRoute route) {
		if (route == null) return Text.empty();
		boolean isActive = (route.routeId() == this.activePatrolRouteId);
		int waypointCount = route.waypoints().size();
		String prefix = isActive ? "▶ " : "";
		return Text.literal(prefix + route.name() + " §8[" + waypointCount + "]");
	}

	private Text getRouteChannelButtonText(int routeId) {
		return getRouteChannelButtonText(com.example.client.renderer.ClientPatrolRouteTracker.getRoute(routeId));
	}

	private Tooltip getRouteChannelTooltip(com.example.patrol.PatrolRoute route) {
		if (route == null) return Tooltip.of(Text.empty());
		int waypointCount = route.waypoints().size();
		boolean isActive = (route.routeId() == this.activePatrolRouteId);
		String status = isActive ? "§a[Active Channel]\n" : "§eClick to select as active pathway.\n";
		return Tooltip.of(Text.literal(
			"§6✦ " + route.getFormattedName() + " §8[" + com.example.patrol.PatrolRoute.toHexCode(route.colorRgb()) + "]\n" +
			status +
			"§7Waypoints: §e" + waypointCount + "\n" +
			"§7Hold Scepter & Right-Click blocks to append points."
		));
	}

	private Tooltip getRouteChannelTooltip(int routeId) {
		return getRouteChannelTooltip(com.example.client.renderer.ClientPatrolRouteTracker.getRoute(routeId));
	}

	private Text getRouteModeButtonText(com.example.patrol.PatrolRoute route) {
		com.example.patrol.PatrolRoute.PatrolMode mode = route != null ? route.patrolMode() : com.example.patrol.PatrolRoute.PatrolMode.LOOP;
		return Text.literal(mode == com.example.patrol.PatrolRoute.PatrolMode.LOOP ? "§bLoop" : "§6Ping");
	}

	private Text getRouteModeButtonText(int routeId) {
		return getRouteModeButtonText(com.example.client.renderer.ClientPatrolRouteTracker.getRoute(routeId));
	}

	private Tooltip getRouteModeTooltip(com.example.patrol.PatrolRoute route) {
		com.example.patrol.PatrolRoute.PatrolMode mode = route != null ? route.patrolMode() : com.example.patrol.PatrolRoute.PatrolMode.LOOP;
		String desc = mode == com.example.patrol.PatrolRoute.PatrolMode.LOOP
			? "§bLoop: §7Closed loop traversal (1 → 2 → 3 → 1).\n§eClick to toggle Ping-Pong."
			: "§6Ping-Pong: §7Linear traversal (1 → 2 → 3 → 2 → 1).\n§eClick to toggle Loop.";
		return Tooltip.of(Text.literal(
			"§6✦ Patrol Mode: §f" + mode.getDisplayName() + "\n" + desc
		));
	}

	private Tooltip getRouteModeTooltip(int routeId) {
		return getRouteModeTooltip(com.example.client.renderer.ClientPatrolRouteTracker.getRoute(routeId));
	}

	public int getActivePatrolRouteId() {
		return this.activePatrolRouteId;
	}

	public void selectActiveRoute(int routeId) {
		this.activePatrolRouteId = routeId;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setActivePatrolRoute(this.scepterStack, routeId);
		}
		if (this.client != null && this.client.player != null) {
			com.example.patrol.PatrolRoute route = com.example.client.renderer.ClientPatrolRouteTracker.getRoute(routeId);
			String routeName = route != null ? route.getFormattedName() : ("Route " + (routeId + 1));
			this.client.player.sendMessage(Text.literal("§6✦ Active Patrol Channel: §r" + routeName), true);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	public void toggleRouteMode(int routeId) {
		ModClientNetworking.sendTogglePatrolMode(routeId);
		com.example.patrol.PatrolRoute route = com.example.client.renderer.ClientPatrolRouteTracker.getRoute(routeId);
		if (route != null) {
			com.example.patrol.PatrolRoute updated = route.withPatrolMode(route.patrolMode().toggle());
			com.example.client.renderer.ClientPatrolRouteTracker.updateRoute(updated);
		}
		refreshButtonLabels();
	}

	public CommandMode getSelectedMode() {
		return this.selectedMode;
	}

	public void clearRoute(int routeId) {
		ModClientNetworking.sendClearPatrolRoute(routeId);
		com.example.patrol.PatrolRoute route = com.example.client.renderer.ClientPatrolRouteTracker.getRoute(routeId);
		if (route != null) {
			com.example.patrol.PatrolRoute cleared = route.withClearedWaypoints();
			com.example.client.renderer.ClientPatrolRouteTracker.updateRoute(cleared);
		}
		refreshButtonLabels();
	}

	public void deleteRouteGui(int routeId) {
		com.example.patrol.PatrolRoute route = com.example.client.renderer.ClientPatrolRouteTracker.getRoute(routeId);
		String name = route != null ? route.name() : ("Route " + (routeId + 1));
		com.example.client.renderer.ClientPatrolRouteTracker.removeRoute(routeId);
		ModClientNetworking.sendDeletePatrolRoute(routeId, name);

		if (this.activePatrolRouteId == routeId) {
			List<com.example.patrol.PatrolRoute> remaining = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
			if (!remaining.isEmpty()) {
				this.activePatrolRouteId = remaining.get(0).routeId();
			} else {
				this.activePatrolRouteId = 0;
			}
			if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
				CommandScepterItem.setActivePatrolRoute(this.scepterStack, this.activePatrolRouteId);
			}
			syncToServer(false);
		}

		if (this.client != null && this.client.player != null) {
			this.client.player.sendMessage(Text.literal("§c✦ Deleted Patrol Route: §f" + name + "§r"), true);
		}
		updatePathwayButtons();
		refreshButtonLabels();
	}

	public void openEditRouteModal(com.example.patrol.PatrolRoute route) {
		if (this.client != null && route != null) {
			this.client.setScreen(new PatrolRouteEditModalScreen(route, false, this));
		}
	}

	public void openAddRouteModal() {
		if (this.client != null) {
			int nextId = getNextAvailableRouteId();
			this.client.setScreen(new PatrolRouteEditModalScreen(nextId, true, this));
		}
	}

	public int getNextAvailableRouteId() {
		List<com.example.patrol.PatrolRoute> routes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
		int id = 0;
		boolean found = true;
		while (found) {
			found = false;
			for (com.example.patrol.PatrolRoute r : routes) {
				if (r.routeId() == id) {
					id++;
					found = true;
					break;
				}
			}
		}
		return id;
	}

	public ButtonWidget getRotateButton() {
		return this.rotateBtn;
	}

	public ButtonWidget getMaterialBomBtn() {
		return this.materialBomBtn;
	}

	public void openResourceEstimatorModal() {
		if (this.client != null) {
			this.client.setScreen(new BlueprintResourceEstimatorModalScreen(this.selectedBlueprintId, this.scepterStack, this));
		}
	}

	public ButtonWidget getExplorerBtn() {
		return this.explorerBtn;
	}

	public void openBlueprintExplorer() {
		if (this.client != null) {
			this.client.setScreen(new BlueprintExplorerScreen(this.selectedBlueprintId, this.scepterStack, this));
		}
	}

	public void selectBlueprintFromExplorer(String blueprintId) {
		selectBlueprint(blueprintId);
	}

	public ButtonWidget getCaptureModalButton() {
		return this.captureModalBtn;
	}

	public ButtonWidget getClearCornersButton() {
		return this.clearCornersBtn;
	}

	public ButtonWidget getMiningModeButton() {
		return this.miningModeBtn;
	}

	public ButtonWidget getMiningConfirmModalButton() {
		return this.miningConfirmModalBtn;
	}

	public ButtonWidget getClearMineCornersButton() {
		return this.clearMineCornersBtn;
	}

	public ButtonWidget getBuildingModeButton() {
		return this.buildingModeBtn;
	}

	public ButtonWidget getBuildConfirmModalButton() {
		return this.buildConfirmModalBtn;
	}

	public ButtonWidget getClearBuildCornersButton() {
		return this.clearBuildCornersBtn;
	}

	public BuildingMode getSelectedBuildingMode() {
		return this.selectedBuildingMode;
	}

	public void setSelectedBuildingMode(BuildingMode mode) {
		this.selectedBuildingMode = mode != null ? mode : BuildingMode.BLUEPRINT;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setBuildingMode(this.scepterStack, this.selectedBuildingMode);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.setBuildingMode(held, this.selectedBuildingMode);
			}
		}
		refreshButtonLabels();
		syncToServer(false);
	}

	public void toggleBuildingModeGui() {
		this.selectedBuildingMode = this.selectedBuildingMode.next();
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setBuildingMode(this.scepterStack, this.selectedBuildingMode);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.setBuildingMode(held, this.selectedBuildingMode);
			}
			this.client.player.sendMessage(Text.literal("§3✦ Building Mode: §r" + this.selectedBuildingMode.getFormattedName()), true);
		}
		refreshButtonLabels();
		syncToServer(false);
	}

	public void openBuildAreaConfirmModal() {
		if (this.client != null) {
			this.client.setScreen(new BuildAreaConfirmModalScreen(this.scepterStack, this));
		}
	}

	public void clearBuildCornersGui() {
		ClientBuildCaptureTracker.clear();
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.clearBuildCorners(this.scepterStack);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.clearBuildCorners(held);
			}
			this.client.player.sendMessage(Text.literal("§6✦ Cleared BUILD corner selections (Pos1 & Pos2) - Ready for Pos1!§r"), true);
		}
		refreshButtonLabels();
	}

	public void sealBarrierGui() {
		if (this.client != null) {
			this.client.setScreen(new BarrierConfirmModalScreen(this.scepterStack, this));
		}
	}

	public void clearBarrierNodesGui() {
		com.example.client.renderer.ClientBarrierTracker.clear();
		if (this.client != null && this.client.player != null) {
			this.client.player.sendMessage(Text.literal("§c✦ Cleared Sovereign Barrier boundary nodes.§r"), true);
		}
		refreshButtonLabels();
	}

	private Text getBuildingModeButtonText() {
		if (this.selectedBuildingMode == BuildingMode.AREA) {
			return Text.literal("§3◫ Area");
		} else if (this.selectedBuildingMode == BuildingMode.BARRIER) {
			return Text.literal("§6🛡 Barrier");
		}
		return Text.literal("§b🏛 Build");
	}

	private Tooltip getBuildingModeTooltip() {
		if (this.selectedBuildingMode == BuildingMode.AREA) {
			return Tooltip.of(Text.literal("§3✦ Building Sub-Mode: Volumetric Area Fill\n§7Click to switch to Sovereign Barrier mode."));
		} else if (this.selectedBuildingMode == BuildingMode.BARRIER) {
			return Tooltip.of(Text.literal("§6✦ Sub-Mode: Barrier\n§7Protects territory from minion harvesting.\n§7Click to switch to Blueprint mode."));
		}
		return Tooltip.of(Text.literal("§b✦ Sub-Mode: Build (Blueprint)\n§7Click to switch to Volumetric Area Fill mode."));
	}

	public MiningMode getSelectedMiningMode() {
		return this.selectedMiningMode;
	}

	public void setSelectedMiningMode(MiningMode mode) {
		this.selectedMiningMode = MiningMode.AREA;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setMiningMode(this.scepterStack, this.selectedMiningMode);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.setMiningMode(held, this.selectedMiningMode);
			}
		}
		refreshButtonLabels();
		syncToServer(false);
	}

	public void toggleMiningModeGui() {
		this.selectedMiningMode = MiningMode.AREA;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setMiningMode(this.scepterStack, this.selectedMiningMode);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.setMiningMode(held, this.selectedMiningMode);
			}
			this.client.player.sendMessage(Text.literal("§6✦ Mining Mode: §r" + this.selectedMiningMode.getFormattedName()), true);
		}
		refreshButtonLabels();
		syncToServer(false);
	}

	public void openMiningConfirmModal() {
		if (this.client != null) {
			this.client.setScreen(new MiningConfirmModalScreen(this.scepterStack, this));
		}
	}

	public ButtonWidget getTargetFilterButton() {
		return this.targetFilterBtn;
	}

	/**
	 * Opens the Minion Target Filter modal letting the commander select which hostile mobs
	 * Auto, Sentinel, and Warrior minions may attack on sight.
	 */
	public void openTargetFilterModal() {
		if (this.client != null) {
			this.client.setScreen(new MinionTargetFilterModalScreen(this));
		}
	}

	public void clearMineCornersGui() {
		ClientMiningCaptureTracker.clear();
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.clearMineCorners(this.scepterStack);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.clearMineCorners(held);
			}
			this.client.player.sendMessage(Text.literal("§6✦ Cleared MINE corner selections (Pos1 & Pos2) - Ready for Pos1!§r"), true);
		}
		refreshButtonLabels();
	}

	private Text getMiningModeButtonText() {
		if (this.selectedMiningMode == MiningMode.AREA) {
			return Text.literal("§e🔲 Area");
		}
		return Text.literal("§6⛏ Mode: Direct");
	}

	private Tooltip getMiningModeTooltip() {
		if (this.selectedMiningMode == MiningMode.AREA) {
			return Tooltip.of(Text.literal("§e✦ Mining Sub-Mode: Custom Area Quarry\n§7Click to switch to Direct / Structure Dismantle."));
		}
		return Tooltip.of(Text.literal("§6✦ Mining Sub-Mode: Direct / Structure\n§7Click to switch to Custom Area Boundary Quarry."));
	}

	public List<ButtonWidget> getBlueprintDeleteButtons() {
		return this.blueprintDeleteButtons;
	}

	public ConfirmationType getPendingConfirmation() {
		return this.pendingConfirmation;
	}

	public StructureBlueprint getPendingBlueprintToDelete() {
		return this.pendingBlueprintToDelete;
	}

	public void openCaptureModal() {
		if (this.client != null) {
			this.client.setScreen(new BlueprintCaptureModalScreen(this.scepterStack, this));
		}
	}

	public void clearCornersGui() {
		ClientDesignCaptureTracker.clear();
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.clearDesignCorners(this.scepterStack);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.clearDesignCorners(held);
			}
			this.client.player.sendMessage(Text.literal("§6✦ Cleared DESIGN corner selections (Pos1 & Pos2) - Ready for Pos1!§r"), true);
		}
		refreshButtonLabels();
	}

	public void requestDeleteBlueprintConfirmation(StructureBlueprint bp) {
		if (bp == null) return;
		this.pendingBlueprintToDelete = bp;
		this.pendingConfirmation = ConfirmationType.DELETE_BLUEPRINT;
		updateConfirmationControls();
	}

	public void confirmDeleteCustomBlueprint() {
		if (this.pendingBlueprintToDelete != null) {
			String id = this.pendingBlueprintToDelete.getId();
			try {
				ModClientNetworking.sendDeleteCustomBlueprint(id);
			} catch (Throwable ignored) {}
			BlueprintRegistry.unregisterCustomBlueprint(id);

			if (this.client != null && this.client.player != null && this.client.world != null) {
				this.client.world.playSound(
					null,
					this.client.player.getX(),
					this.client.player.getY(),
					this.client.player.getZ(),
					SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
					SoundCategory.PLAYERS,
					0.9F,
					0.8F
				);
				this.client.player.sendMessage(Text.literal("§c✦ Deleted Custom Blueprint: §f" + id + "§r"), true);
			}

			if (id.equalsIgnoreCase(this.selectedBlueprintId)) {
				Collection<StructureBlueprint> all = BlueprintRegistry.getAll();
				if (!all.isEmpty()) {
					this.selectedBlueprintId = all.iterator().next().getId();
				} else {
					this.selectedBlueprintId = "";
				}
				if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
					CommandScepterItem.setBlueprintId(this.scepterStack, this.selectedBlueprintId);
				}
				syncToServer(false);
			}
		}
		cancelConfirmation();
		refreshButtonLabels();
	}

	public void requestDismissMinionsConfirmation() {
		if (this.nearbyThralls <= 0) return;
		this.pendingConfirmation = ConfirmationType.DISMISS_MINIONS;
		updateConfirmationControls();
	}

	public void confirmDismissSelectedMinions() {
		try {
			ModClientNetworking.sendDismissSelectedMinions();
		} catch (Throwable ignored) {}
		cancelConfirmation();
		this.close();
	}

	public void confirmDismissAllMinions() {
		try {
			ModClientNetworking.sendDismissAllMinions();
		} catch (Throwable ignored) {}
		cancelConfirmation();
		this.close();
	}

	public void cancelConfirmation() {
		this.pendingConfirmation = ConfirmationType.NONE;
		this.pendingBlueprintToDelete = null;
		updateConfirmationControls();
	}

	public void updateConfirmationControls() {
		boolean isConfirming = this.pendingConfirmation != ConfirmationType.NONE;

		for (ButtonWidget b : this.squadButtons) b.active = !isConfirming;
		for (ButtonWidget b : this.roleButtons) b.active = !isConfirming;
		for (ButtonWidget b : this.modeButtons) b.active = !isConfirming;
		for (ButtonWidget b : this.blueprintButtons) b.active = !isConfirming;
		for (ButtonWidget b : this.blueprintDeleteButtons) b.active = !isConfirming;
		for (ButtonWidget b : this.pathwayWidgets) b.active = !isConfirming;
		if (this.buildingModeBtn != null) this.buildingModeBtn.active = !isConfirming;
		if (this.buildConfirmModalBtn != null) this.buildConfirmModalBtn.active = !isConfirming;
		if (this.clearBuildCornersBtn != null) this.clearBuildCornersBtn.active = !isConfirming;
		if (this.rotateBtn != null) this.rotateBtn.active = !isConfirming;
		if (this.materialBomBtn != null) this.materialBomBtn.active = !isConfirming;
		if (this.captureModalBtn != null) this.captureModalBtn.active = !isConfirming;
		if (this.clearCornersBtn != null) this.clearCornersBtn.active = !isConfirming;
		if (this.miningModeBtn != null) this.miningModeBtn.active = !isConfirming;
		if (this.miningConfirmModalBtn != null) this.miningConfirmModalBtn.active = !isConfirming;
		if (this.clearMineCornersBtn != null) this.clearMineCornersBtn.active = !isConfirming;
		if (this.prevPageBtn != null) this.prevPageBtn.active = !isConfirming && this.blueprintPage > 0;
		if (this.nextPageBtn != null) this.nextPageBtn.active = !isConfirming;
		if (this.targetFilterBtn != null) this.targetFilterBtn.active = !isConfirming;
		if (this.explorerBtn != null) {
			this.explorerBtn.visible = this.selectedMode == CommandMode.BUILD && (this.selectedBuildingMode == BuildingMode.BLUEPRINT);
			this.explorerBtn.active = !isConfirming;
		}

		int modalW = 240;
		int modalH = 110;
		int modalX = (this.width - modalW) / 2;
		int modalBtnY = (this.height - modalH) / 2 + modalH - 28;

		if (this.confirmDeleteBpBtn != null) {
			this.confirmDeleteBpBtn.visible = (this.pendingConfirmation == ConfirmationType.DELETE_BLUEPRINT);
			this.confirmDeleteBpBtn.active = this.confirmDeleteBpBtn.visible;
			this.confirmDeleteBpBtn.setPosition(modalX + 24, modalBtnY);
			this.confirmDeleteBpBtn.setWidth(90);
		}
		if (this.confirmDismissSelectedBtn != null) {
			boolean showSelected = (this.pendingConfirmation == ConfirmationType.DISMISS_MINIONS && this.nearbySelectedThralls > 0);
			this.confirmDismissSelectedBtn.visible = showSelected;
			this.confirmDismissSelectedBtn.active = showSelected;
			if (showSelected) {
				this.confirmDismissSelectedBtn.setMessage(Text.literal("§6Selected (" + this.nearbySelectedThralls + ")"));
				this.confirmDismissSelectedBtn.setPosition(modalX + 10, modalBtnY);
				this.confirmDismissSelectedBtn.setWidth(78);
			}
		}
		if (this.confirmDismissAllBtn != null) {
			boolean showAll = (this.pendingConfirmation == ConfirmationType.DISMISS_MINIONS);
			this.confirmDismissAllBtn.visible = showAll;
			this.confirmDismissAllBtn.active = showAll;
			if (showAll) {
				this.confirmDismissAllBtn.setMessage(Text.literal("§cAll (" + this.nearbyThralls + ")"));
				if (this.nearbySelectedThralls > 0) {
					this.confirmDismissAllBtn.setPosition(modalX + 92, modalBtnY);
					this.confirmDismissAllBtn.setWidth(70);
				} else {
					this.confirmDismissAllBtn.setPosition(modalX + 34, modalBtnY);
					this.confirmDismissAllBtn.setWidth(94);
				}
			}
		}
		if (this.cancelModalBtn != null) {
			this.cancelModalBtn.visible = isConfirming;
			this.cancelModalBtn.active = isConfirming;
			if (this.pendingConfirmation == ConfirmationType.DELETE_BLUEPRINT) {
				this.cancelModalBtn.setPosition(modalX + 126, modalBtnY);
				this.cancelModalBtn.setWidth(90);
			} else if (this.nearbySelectedThralls > 0) {
				this.cancelModalBtn.setPosition(modalX + 166, modalBtnY);
				this.cancelModalBtn.setWidth(64);
			} else {
				this.cancelModalBtn.setPosition(modalX + 134, modalBtnY);
				this.cancelModalBtn.setWidth(72);
			}
		}
	}

	public int getSelectedRotation() {
		return this.selectedRotation;
	}

	public void setSelectedRotation(int rotation) {
		this.selectedRotation = Math.floorMod(rotation, 4);
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setRotationIndex(this.scepterStack, this.selectedRotation);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	public void cycleRotationGui() {
		this.selectedRotation = Math.floorMod(this.selectedRotation + 1, 4);
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setRotationIndex(this.scepterStack, this.selectedRotation);
		}
		if (this.client != null && this.client.world != null && this.client.player != null) {
			this.client.world.playSound(
				null,
				this.client.player.getX(),
				this.client.player.getY(),
				this.client.player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_CHIME,
				SoundCategory.PLAYERS,
				0.8F,
				1.0F + (this.selectedRotation * 0.15F)
			);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	private Text getRotateButtonText() {
		return Text.literal("§6↻ " + (this.selectedRotation * 90) + "°");
	}

	private void selectMode(CommandMode mode) {
		this.selectedMode = mode;
		if (mode == CommandMode.PATHWAY) {
			List<com.example.patrol.PatrolRoute> routes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
			boolean activeExists = false;
			for (com.example.patrol.PatrolRoute r : routes) {
				if (r.routeId() == this.activePatrolRouteId) {
					activeExists = true;
					break;
				}
			}
			if (!activeExists && !routes.isEmpty()) {
				this.activePatrolRouteId = routes.get(0).routeId();
			} else if (routes.isEmpty()) {
				this.activePatrolRouteId = 0;
			}
			if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
				CommandScepterItem.setActivePatrolRoute(this.scepterStack, this.activePatrolRouteId);
			}
		}
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setMode(this.scepterStack, mode);
		}
		updateControlsVisibility();
		syncToServer(false);
		refreshButtonLabels();
	}

	private void selectBlueprint(String blueprintId) {
		this.selectedBlueprintId = blueprintId;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setBlueprintId(this.scepterStack, blueprintId);
		}
		ModClientNetworking.sendRequestResourceEstimation(blueprintId);
		syncToServer(false);
		refreshButtonLabels();
	}

	private void executeDirective() {
		syncToServer(true);
		this.closed = true;
		if (this.client != null) {
			super.close();
		}
	}

	private void syncToServer(boolean executeDirective) {
		try {
			ModClientNetworking.sendUpdateScepter(
				this.selectedMode,
				this.selectedBlueprintId,
				this.selectedSquad,
				this.selectedRotation,
				Optional.ofNullable(this.selectedRole),
				this.activePatrolRouteId,
				executeDirective,
				Optional.ofNullable(this.selectedBuildingMode),
				Optional.ofNullable(this.selectedMiningMode)
			);
		} catch (Throwable ignored) {}
	}

	public void refreshButtonLabels() {
		SquadGroup[] squads = SquadGroup.values();
		for (int i = 0; i < squads.length && i < this.squadButtons.size(); i++) {
			this.squadButtons.get(i).setMessage(getSquadButtonText(squads[i]));
			this.squadButtons.get(i).setTooltip(getSquadTooltip(squads[i]));
		}

		MinionRole[] roles = MinionRole.values();
		for (int i = 0; i < roles.length && i < this.roleButtons.size(); i++) {
			this.roleButtons.get(i).setMessage(getRoleButtonText(roles[i]));
			this.roleButtons.get(i).setTooltip(getRoleTooltip(roles[i]));
		}

		if (this.rotateBtn != null) {
			this.rotateBtn.setMessage(getRotateButtonText());
		}

		if (this.buildingModeBtn != null) {
			this.buildingModeBtn.setMessage(getBuildingModeButtonText());
			this.buildingModeBtn.setTooltip(getBuildingModeTooltip());
		}

		if (this.miningModeBtn != null) {
			int startX = (this.width - WINDOW_WIDTH) / 2;
			int pageControlsY = (this.height - WINDOW_HEIGHT) / 2 + 226;
			this.miningModeBtn.setWidth(this.selectedMiningMode == MiningMode.AREA ? 44 : 142);
			this.miningModeBtn.setPosition(startX + 16, pageControlsY);
			this.miningModeBtn.setMessage(getMiningModeButtonText());
			this.miningModeBtn.setTooltip(getMiningModeTooltip());
		}

		CommandMode[] modes = CommandMode.values();
		for (int i = 0; i < modes.length && i < this.modeButtons.size(); i++) {
			this.modeButtons.get(i).setMessage(getModeButtonText(modes[i]));
		}

		if (!this.blueprintButtons.isEmpty()) {
			updateBlueprintButtons();
		}
		if (this.explorerBtn != null) {
			this.explorerBtn.visible = this.selectedMode == CommandMode.BUILD && (this.selectedBuildingMode == BuildingMode.BLUEPRINT);
			this.explorerBtn.active = (this.pendingConfirmation == ConfirmationType.NONE);
		}
		updatePathwayButtons();
		updateBarrierButtons();

		updateControlsVisibility();
	}

	private void updatePathwayButtons() {
		boolean isPathway = this.selectedMode == CommandMode.PATHWAY;
		if (!isPathway) {
			for (ButtonWidget btn : this.pathwayWidgets) {
				btn.visible = false;
				btn.active = false;
			}
			return;
		}

		List<com.example.patrol.PatrolRoute> routes = com.example.client.renderer.ClientPatrolRouteTracker.getAllRoutesList();
		int totalPages = Math.max(1, (routes.size() + PATHWAY_PAGE_SIZE - 1) / PATHWAY_PAGE_SIZE);
		if (this.pathwayPage >= totalPages) {
			this.pathwayPage = totalPages - 1;
		}
		if (this.pathwayPage < 0) {
			this.pathwayPage = 0;
		}

		for (int slot = 0; slot < PATHWAY_PAGE_SIZE; slot++) {
			int index = this.pathwayPage * PATHWAY_PAGE_SIZE + slot;
			boolean hasRoute = index < routes.size();

			ButtonWidget selectBtn = slot < this.pathwaySelectButtons.size() ? this.pathwaySelectButtons.get(slot) : null;
			ButtonWidget modeBtn = slot < this.pathwayModeButtons.size() ? this.pathwayModeButtons.get(slot) : null;
			ButtonWidget editBtn = slot < this.pathwayEditButtons.size() ? this.pathwayEditButtons.get(slot) : null;
			ButtonWidget clearBtn = slot < this.pathwayClearButtons.size() ? this.pathwayClearButtons.get(slot) : null;
			ButtonWidget delBtn = slot < this.pathwayDeleteButtons.size() ? this.pathwayDeleteButtons.get(slot) : null;

			if (hasRoute) {
				com.example.patrol.PatrolRoute r = routes.get(index);
				if (selectBtn != null) {
					selectBtn.visible = true;
					selectBtn.active = (this.pendingConfirmation == ConfirmationType.NONE);
					selectBtn.setMessage(getRouteChannelButtonText(r));
					selectBtn.setTooltip(getRouteChannelTooltip(r));
				}
				if (modeBtn != null) {
					modeBtn.visible = true;
					modeBtn.active = (this.pendingConfirmation == ConfirmationType.NONE);
					modeBtn.setMessage(getRouteModeButtonText(r));
					modeBtn.setTooltip(getRouteModeTooltip(r));
				}
				if (editBtn != null) {
					editBtn.visible = true;
					editBtn.active = (this.pendingConfirmation == ConfirmationType.NONE);
				}
				if (clearBtn != null) {
					clearBtn.visible = true;
					clearBtn.active = (this.pendingConfirmation == ConfirmationType.NONE);
				}
				if (delBtn != null) {
					delBtn.visible = true;
					delBtn.active = (this.pendingConfirmation == ConfirmationType.NONE) && (routes.size() > 1);
				}
			} else {
				if (selectBtn != null) { selectBtn.visible = false; selectBtn.active = false; }
				if (modeBtn != null) { modeBtn.visible = false; modeBtn.active = false; }
				if (editBtn != null) { editBtn.visible = false; editBtn.active = false; }
				if (clearBtn != null) { clearBtn.visible = false; clearBtn.active = false; }
				if (delBtn != null) { delBtn.visible = false; delBtn.active = false; }
			}
		}

		if (this.prevPathwayPageBtn != null) {
			this.prevPathwayPageBtn.visible = true;
			this.prevPathwayPageBtn.active = (this.pendingConfirmation == ConfirmationType.NONE) && this.pathwayPage > 0;
		}
		if (this.nextPathwayPageBtn != null) {
			this.nextPathwayPageBtn.visible = true;
			this.nextPathwayPageBtn.active = (this.pendingConfirmation == ConfirmationType.NONE) && (this.pathwayPage + 1) < totalPages;
		}
		if (this.addRouteBtn != null) {
			this.addRouteBtn.visible = true;
			this.addRouteBtn.active = (this.pendingConfirmation == ConfirmationType.NONE);
		}
	}

	private void updateBarrierButtons() {
		boolean isBarrier = this.selectedMode == CommandMode.BUILD && this.selectedBuildingMode == BuildingMode.BARRIER;
		if (!isBarrier) {
			for (ButtonWidget btn : this.barrierDashboardWidgets) {
				btn.visible = false;
				btn.active = false;
			}
			return;
		}

		List<com.example.barrier.BarrierZone> zones = com.example.client.renderer.ClientBarrierTracker.getConfirmedZones();
		zones.removeIf(z -> z == null || z.getNodeCount() < 2);
		int totalPages = Math.max(1, (zones.size() + BARRIER_PAGE_SIZE - 1) / BARRIER_PAGE_SIZE);
		if (this.barrierPage >= totalPages) {
			this.barrierPage = totalPages - 1;
		}
		if (this.barrierPage < 0) {
			this.barrierPage = 0;
		}

		for (int slot = 0; slot < BARRIER_PAGE_SIZE; slot++) {
			int index = this.barrierPage * BARRIER_PAGE_SIZE + slot;
			boolean hasZone = index < zones.size();

			ButtonWidget selectBtn = slot < this.barrierSelectButtons.size() ? this.barrierSelectButtons.get(slot) : null;
			ButtonWidget editBtn = slot < this.barrierEditButtons.size() ? this.barrierEditButtons.get(slot) : null;
			ButtonWidget delBtn = slot < this.barrierDeleteButtons.size() ? this.barrierDeleteButtons.get(slot) : null;

			if (hasZone) {
				com.example.barrier.BarrierZone z = zones.get(index);
				if (selectBtn != null) {
					selectBtn.visible = true;
					selectBtn.active = (this.pendingConfirmation == ConfirmationType.NONE);
					selectBtn.setMessage(Text.literal("§6" + z.getName() + " §8[" + z.getNodeCount() + "]"));
					selectBtn.setTooltip(Tooltip.of(Text.literal(
						"§6✦ " + z.getName() + "\n" +
						"§7Nodes: §f" + z.getNodeCount() + " boundary points\n" +
						"§7Height: §f" + (z.getMaxY() - z.getMinY() + 1) + " blocks (Y: " + z.getMinY() + " → " + z.getMaxY() + ")\n" +
						"§eClick to resume editing or expand this barrier."
					)));
				}
				if (editBtn != null) {
					editBtn.visible = true;
					editBtn.active = (this.pendingConfirmation == ConfirmationType.NONE);
				}
				if (delBtn != null) {
					delBtn.visible = true;
					delBtn.active = (this.pendingConfirmation == ConfirmationType.NONE);
				}
			} else {
				if (selectBtn != null) { selectBtn.visible = false; selectBtn.active = false; }
				if (editBtn != null) { editBtn.visible = false; editBtn.active = false; }
				if (delBtn != null) { delBtn.visible = false; delBtn.active = false; }
			}
		}

		if (this.prevBarrierPageBtn != null) {
			this.prevBarrierPageBtn.visible = isBarrier;
			this.prevBarrierPageBtn.active = (this.pendingConfirmation == ConfirmationType.NONE) && this.barrierPage > 0;
		}
		if (this.nextBarrierPageBtn != null) {
			this.nextBarrierPageBtn.visible = isBarrier;
			this.nextBarrierPageBtn.active = (this.pendingConfirmation == ConfirmationType.NONE) && (this.barrierPage + 1) < totalPages;
		}
	}

	private void updateBlueprintButtons() {
		if (this.blueprintButtons.isEmpty()) return;

		boolean isPathway = this.selectedMode == CommandMode.PATHWAY;
		boolean isDefault = this.selectedMode == CommandMode.DEFAULT;
		if (isPathway || isDefault) {
			for (ButtonWidget btn : this.blueprintButtons) {
				btn.visible = false;
				btn.active = false;
			}
			for (ButtonWidget btn : this.blueprintDeleteButtons) {
				btn.visible = false;
				btn.active = false;
			}
			if (this.prevPageBtn != null) {
				this.prevPageBtn.visible = false;
				this.prevPageBtn.active = false;
			}
			if (this.nextPageBtn != null) {
				this.nextPageBtn.visible = false;
				this.nextPageBtn.active = false;
			}
			return;
		}

		List<StructureBlueprint> blueprints = new ArrayList<>(BlueprintRegistry.getAll());
		int totalPages = Math.max(1, (blueprints.size() + BLUEPRINT_PAGE_SIZE - 1) / BLUEPRINT_PAGE_SIZE);

		if (this.blueprintPage >= totalPages) {
			this.blueprintPage = totalPages - 1;
		}
		if (this.blueprintPage < 0) {
			this.blueprintPage = 0;
		}

		for (int slot = 0; slot < BLUEPRINT_PAGE_SIZE && slot < this.blueprintButtons.size(); slot++) {
			int index = this.blueprintPage * BLUEPRINT_PAGE_SIZE + slot;
			ButtonWidget btn = this.blueprintButtons.get(slot);
			ButtonWidget delBtn = slot < this.blueprintDeleteButtons.size() ? this.blueprintDeleteButtons.get(slot) : null;

			if (index < blueprints.size()) {
				StructureBlueprint bp = blueprints.get(index);
				boolean isCustom = BlueprintRegistry.isCustom(bp.getId());
				btn.visible = true;
				btn.active = (this.pendingConfirmation == ConfirmationType.NONE);
				btn.setWidth(isCustom ? 134 : 160);
				btn.setMessage(getBlueprintButtonText(bp));
				btn.setTooltip(Tooltip.of(getBlueprintTooltip(bp)));

				if (delBtn != null) {
					delBtn.visible = isCustom;
					delBtn.active = isCustom && (this.pendingConfirmation == ConfirmationType.NONE);
				}
			} else {
				btn.visible = false;
				btn.active = false;
				if (delBtn != null) {
					delBtn.visible = false;
					delBtn.active = false;
				}
			}
		}

		if (this.prevPageBtn != null) {
			this.prevPageBtn.visible = true;
			this.prevPageBtn.active = (this.pendingConfirmation == ConfirmationType.NONE) && this.blueprintPage > 0;
		}
		if (this.nextPageBtn != null) {
			this.nextPageBtn.visible = true;
			this.nextPageBtn.active = (this.pendingConfirmation == ConfirmationType.NONE) && (this.blueprintPage + 1) < totalPages;
		}
	}

	@Override
	protected void applyBlur(float delta) {
		// Disable background world blur post-processing shader while keeping screen darkening and UI elements crisp
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (this.shiftHeldOnOpen && !isShiftOrSneakDown()) {
			this.shiftHeldOnOpen = false;
		}

		this.renderBackground(context, mouseX, mouseY, delta);

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// Main background plate with gold border
		context.fill(startX, startY, startX + WINDOW_WIDTH, startY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(startX, startY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFE2B007);

		// Header Plate
		context.fill(startX + 1, startY + 1, startX + WINDOW_WIDTH - 1, startY + 30, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§6✦ SOVEREIGN COMMAND HUB ✦"),
			this.width / 2,
			startY + 6,
			0xFFFFFF
		);

		Text shiftCloseText = Text.literal("§e[Shift] §7Close");
		context.drawTextWithShadow(
			this.textRenderer,
			shiftCloseText,
			startX + WINDOW_WIDTH - this.textRenderer.getWidth(shiftCloseText) - 10,
			startY + 6,
			0xE0E0E0
		);

		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§7Thralls: " + (this.nearbyThralls > 0 ? "§a" : "§c") + this.nearbyThralls +
				" §8| §7Selected: " + (this.nearbySelectedThralls > 0 ? "§e" : "§7") + this.nearbySelectedThralls +
				" §8| §7Channel: " + this.selectedSquad.getFormattedName()),
			this.width / 2,
			startY + 18,
			0xAAAAAA
		);

		// Section: Squad Channel Bar
		context.drawTextWithShadow(this.textRenderer, Text.literal("§eTarget Squad Channel:"), startX + 16, startY + 35, 0xFFD700);

		int selectedSquadIndex = this.selectedSquad.ordinal();
		SquadGroup[] squads = SquadGroup.values();
		if (selectedSquadIndex >= 0 && selectedSquadIndex < squads.length) {
			int indX = startX + 15 + selectedSquadIndex * 63;
			Integer colorVal = this.selectedSquad.getFormatting().getColorValue();
			int barColor = colorVal != null ? (0xFF000000 | colorVal) : 0xFFFFD700;
			context.fill(indX, startY + 66, indX + 58, startY + 68, barColor);
		}

		// Section: Mode-specific Sub-header (Architecture Style or Mass Role Archetype or Design or Mining)
		if (this.selectedMode == CommandMode.BUILD) {
			if (this.selectedBuildingMode == BuildingMode.BLUEPRINT) {
				context.drawTextWithShadow(this.textRenderer, Text.literal("§b✦ Active Blueprint Construction Directives"), startX + 16, startY + 69, 0x55FFFF);
				context.drawTextWithShadow(this.textRenderer, Text.literal("§7Constructs 100% exact captured blocks - zero substitutions."), startX + 16, startY + 84, 0xAAAAAA);
			} else if (this.selectedBuildingMode == BuildingMode.BARRIER) {
				context.drawTextWithShadow(this.textRenderer, Text.literal("§6✦ Sovereign Territory Barrier Protection"), startX + 16, startY + 69, 0xFFA500);
				context.drawTextWithShadow(this.textRenderer, Text.literal("§7Multi-point dynamic perimeter to protect base/village from harvesting."), startX + 16, startY + 84, 0xAAAAAA);
			} else {
				context.drawTextWithShadow(this.textRenderer, Text.literal("§3✦ Volumetric Area Fill Construction"), startX + 16, startY + 69, 0x00E5FF);
				BlockPos p1 = ClientBuildCaptureTracker.getPos1();
				BlockPos p2 = ClientBuildCaptureTracker.getPos2();
				String p1Str = p1 != null ? ("[" + p1.getX() + "," + p1.getY() + "," + p1.getZ() + "]") : "Not Set";
				String p2Str = p2 != null ? ("[" + p2.getX() + "," + p2.getY() + "," + p2.getZ() + "]") : "Not Set";
				context.drawTextWithShadow(this.textRenderer, Text.literal("§7Pos1: §f" + p1Str + "  §7Pos2: §f" + p2Str + " §8(L-Click Step, R-Click Confirm)"), startX + 16, startY + 84, 0xAAAAAA);
			}
		} else if (this.selectedMode == CommandMode.DESIGN) {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§d✦ In-World Spatial Design Studio"), startX + 16, startY + 69, 0xFF55FF);
			BlockPos p1 = ClientDesignCaptureTracker.getPos1();
			BlockPos p2 = ClientDesignCaptureTracker.getPos2();
			String p1Str = p1 != null ? ("[" + p1.getX() + "," + p1.getY() + "," + p1.getZ() + "]") : "Not Set";
			String p2Str = p2 != null ? ("[" + p2.getX() + "," + p2.getY() + "," + p2.getZ() + "]") : "Not Set";
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Pos1: §f" + p1Str + "  §7Pos2: §f" + p2Str + " §8(L-Click Step, R-Click Save)"), startX + 16, startY + 84, 0xAAAAAA);
		} else if (this.selectedMode == CommandMode.MINE) {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§e✦ Custom Area Boundary Quarry"), startX + 16, startY + 69, 0xFFFF55);
			BlockPos p1 = ClientMiningCaptureTracker.getPos1();
			BlockPos p2 = ClientMiningCaptureTracker.getPos2();
			String p1Str = p1 != null ? ("[" + p1.getX() + "," + p1.getY() + "," + p1.getZ() + "]") : "Not Set";
			String p2Str = p2 != null ? ("[" + p2.getX() + "," + p2.getY() + "," + p2.getZ() + "]") : "Not Set";
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Pos1: §f" + p1Str + "  §7Pos2: §f" + p2Str + " §8(L-Click Step, R-Click Confirm)"), startX + 16, startY + 84, 0xAAAAAA);
		} else {
			String roleLabelSuffix = this.selectedRole != null
				? " " + this.selectedRole.getFormattedName() + " §8(Rally Transform)"
				: " §7[None]";
			context.drawTextWithShadow(this.textRenderer, Text.literal("§eMass Role Archetype:" + roleLabelSuffix), startX + 16, startY + 69, 0xFFD700);

			if (this.selectedRole != null) {
				int selectedRoleIndex = this.selectedRole.ordinal();
				MinionRole[] roles = MinionRole.values();
				if (selectedRoleIndex >= 0 && selectedRoleIndex < roles.length) {
					int indX = startX + 17 + selectedRoleIndex * 78;
					Integer colorVal = this.selectedRole.getFormatting().getColorValue();
					int barColor = colorVal != null ? (0xFF000000 | colorVal) : 0xFFFFD700;
					context.fill(indX, startY + 100, indX + 72, startY + 102, barColor);
				}
			}
		}

		// Section: Command Modes & Blueprint Catalog / Pathway Dashboard
		context.drawTextWithShadow(this.textRenderer, Text.literal("§eCommand Mode:"), startX + 16, startY + 106, 0xFFD700);

		if (this.selectedMode == CommandMode.PATHWAY) {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§d✦ Patrol Route Dashboard:"), startX + 165, startY + 106, 0xFF55FF);
		} else if (this.selectedMode == CommandMode.BUILD && this.selectedBuildingMode == BuildingMode.AREA) {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§3✦ Area Fill Volume:"), startX + 165, startY + 106, 0x00E5FF);
			BlockPos p1 = ClientBuildCaptureTracker.getPos1();
			BlockPos p2 = ClientBuildCaptureTracker.getPos2();
			if (p1 != null && p2 != null) {
				int sx = Math.abs(p1.getX() - p2.getX()) + 1;
				int sy = Math.abs(p1.getY() - p2.getY()) + 1;
				int sz = Math.abs(p1.getZ() - p2.getZ()) + 1;
				long vol = (long) sx * sy * sz;
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§3" + sx + "x" + sy + "x" + sz + " §8(§e" + vol + "b§8)"), startX + 245, startY + 140, 0x00E5FF);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§a✔ Ready to Fill!"), startX + 245, startY + 158, 0x55FF55);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Click §3[✦ Fill] §7below"), startX + 245, startY + 174, 0xAAAAAA);
			} else {
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8[ No Selection ]"), startX + 245, startY + 140, 0x888888);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Left-click 2 blocks in world"), startX + 245, startY + 158, 0xAAAAAA);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7to set Pos1 & Pos2 corners"), startX + 245, startY + 174, 0xAAAAAA);
			}
		} else if (this.selectedMode == CommandMode.BUILD && this.selectedBuildingMode == BuildingMode.BARRIER) {
			List<com.example.barrier.BarrierZone> zones = com.example.client.renderer.ClientBarrierTracker.getConfirmedZones();
			if (zones.isEmpty()) {
				context.drawTextWithShadow(this.textRenderer, Text.literal("§6✦ Sovereign Barriers:"), startX + 165, startY + 106, 0xFFA500);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Left-click ground to place points"), startX + 245, startY + 155, 0xAAAAAA);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§eLeft-click Point 1 to Close & Seal"), startX + 245, startY + 170, 0xFFD700);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§cRight-click waypoint to remove"), startX + 245, startY + 185, 0xFF8888);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8Right-click elsewhere to open Seal modal"), startX + 245, startY + 200, 0x666666);
			} else {
				int totalPages = Math.max(1, (zones.size() + BARRIER_PAGE_SIZE - 1) / BARRIER_PAGE_SIZE);
				context.drawTextWithShadow(this.textRenderer, Text.literal("§6✦ Sovereign Barriers (" + zones.size() + "):"), startX + 165, startY + 106, 0xFFA500);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Page " + (this.barrierPage + 1) + " / " + totalPages), startX + 245, startY + 231, 0xAAAAAA);
			}
		} else if (this.selectedMode == CommandMode.DEFAULT) {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7✦ Neutral Standby Mode"), startX + 165, startY + 106, 0xAAAAAA);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§fDefault Minecraft Interactions"), startX + 245, startY + 135, 0xFFFFFF);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Scepter does not intercept actions."), startX + 245, startY + 150, 0xAAAAAA);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Use doors, chests & mobs normally."), startX + 245, startY + 165, 0xAAAAAA);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§ePress [V] or Sneak + R-Click in air"), startX + 245, startY + 185, 0xFFFF55);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7to reconfigure command modes."), startX + 245, startY + 198, 0x888888);
		} else {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§bBlueprint Catalog:"), startX + 165, startY + 106, 0x55FFFF);
			Collection<StructureBlueprint> allBlueprints = BlueprintRegistry.getAll();
			if (allBlueprints.isEmpty()) {
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8[ No Custom Blueprints ]"), startX + 245, startY + 145, 0x888888);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Switch to §dDESIGN §7mode"), startX + 245, startY + 160, 0xAAAAAA);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7to capture your first structure!"), startX + 245, startY + 175, 0xAAAAAA);
			} else {
				int totalPages = Math.max(1, (allBlueprints.size() + BLUEPRINT_PAGE_SIZE - 1) / BLUEPRINT_PAGE_SIZE);
				context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal("§7Page §f" + (this.blueprintPage + 1) + "§7/§f" + totalPages),
					startX + 245,
					startY + 231,
					0xAAAAAA
				);
			}
		}

		// Selected Mode Description Box
		renderModeDescription(context, startX, startY);

		super.render(context, mouseX, mouseY, delta);

		// Render Confirmation Modal Overlay if active
		if (this.pendingConfirmation != ConfirmationType.NONE) {
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 400.0F);

			context.fill(0, 0, this.width, this.height, 0xAA080C12);

			int modalW = 240;
			int modalH = 110;
			int modalX = (this.width - modalW) / 2;
			int modalY = (this.height - modalH) / 2;

			context.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFA141E2B);
			context.drawBorder(modalX, modalY, modalW, modalH, 0xFFE2B007);

			if (this.pendingConfirmation == ConfirmationType.DELETE_BLUEPRINT && this.pendingBlueprintToDelete != null) {
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c✦ CONFIRM DELETION ✦"), modalX + modalW / 2, modalY + 10, 0xFF5555);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Delete custom blueprint:"), modalX + modalW / 2, modalY + 28, 0xAAAAAA);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e\"" + this.pendingBlueprintToDelete.getName() + "\""), modalX + modalW / 2, modalY + 42, 0xFFFF55);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8This action cannot be undone."), modalX + modalW / 2, modalY + 58, 0x888888);
			} else if (this.pendingConfirmation == ConfirmationType.DISMISS_MINIONS) {
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c✦ DECOMMISSION MINIONS ✦"), modalX + modalW / 2, modalY + 10, 0xFF5555);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Choose scope of minions to destroy:"), modalX + modalW / 2, modalY + 28, 0xAAAAAA);
				if (this.nearbySelectedThralls > 0) {
					context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§eSelected: §f" + this.nearbySelectedThralls + " §8| §cTotal Nearby: §f" + this.nearbyThralls), modalX + modalW / 2, modalY + 44, 0xFFFFFF);
				} else {
					context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§cAll Nearby Thralls: §f" + this.nearbyThralls), modalX + modalW / 2, modalY + 44, 0xFFFFFF);
				}
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8All dropped items will fall to the ground."), modalX + modalW / 2, modalY + 58, 0x888888);
			}

			if (this.confirmDeleteBpBtn != null && this.confirmDeleteBpBtn.visible) {
				this.confirmDeleteBpBtn.render(context, mouseX, mouseY, delta);
			}
			if (this.confirmDismissSelectedBtn != null && this.confirmDismissSelectedBtn.visible) {
				this.confirmDismissSelectedBtn.render(context, mouseX, mouseY, delta);
			}
			if (this.confirmDismissAllBtn != null && this.confirmDismissAllBtn.visible) {
				this.confirmDismissAllBtn.render(context, mouseX, mouseY, delta);
			}
			if (this.cancelModalBtn != null && this.cancelModalBtn.visible) {
				this.cancelModalBtn.render(context, mouseX, mouseY, delta);
			}

			context.getMatrices().pop();
		}
	}

	private void renderModeDescription(DrawContext context, int startX, int startY) {
		int descY = startY + 203;
		boolean hasBottomControls = (this.selectedMode == CommandMode.BUILD || this.selectedMode == CommandMode.DESIGN || this.selectedMode == CommandMode.MINE);
		int boxHeight = hasBottomControls ? 20 : 44;

		context.fill(startX + 14, descY, startX + 158, descY + boxHeight, 0x880D151D);
		context.drawBorder(startX + 14, descY, 144, boxHeight, 0xFF3A4E63);

		String modeDesc = switch (this.selectedMode) {
			case FOLLOW -> "§aMinions actively follow & guard master.";
			case STAY -> "§eMinions hold positions & guard zone.";
			case MINE -> "§6Minions harvest ores & break blocks.";
			case BUILD -> "§bMinions construct selected blueprint.";
			case DESIGN -> "§7Capture spatial volume.";
			case RECRUIT -> "§dEnthrall living mobs into thralls.";
			case PATHWAY -> "§3Define & patrol waypoint routes.";
			case DEFAULT -> "§7Neutral standby mode. Scepter will not intercept interactions.";
		};

		float scale = 0.82F;
		int availableWidth = (int) ((144 - 8) / scale);

		context.getMatrices().push();
		context.getMatrices().translate(startX + 18, descY + 4, 0);

		if (hasBottomControls) {
			Text bannerText = Text.literal("§f" + this.selectedMode.getFormattedName() + " §8| " + modeDesc);
			if (this.selectedMode == CommandMode.BUILD) {
				if (this.selectedBuildingMode == BuildingMode.AREA) {
					bannerText = Text.literal("§3BUILD AREA §8| §fVolumetric Fill §7[✦Fill]");
				} else if (this.selectedBuildingMode == BuildingMode.BARRIER) {
					int count = com.example.client.renderer.ClientBarrierTracker.getPendingNodeCount();
					bannerText = Text.literal("§6BARRIER §8| §f" + count + " Nodes §7[✦Seal]");
				} else {
					SyncResourceEstimationPayload est = ClientResourceEstimatorTracker.getEstimation(this.selectedBlueprintId);
					if (est != null && est.totalBlocks() > 0) {
						int pct = est.getReadinessPercentage();
						String color = pct >= 100 ? "§a" : (pct >= 50 ? "§6" : "§c");
						bannerText = Text.literal("§bBUILD §8| " + color + pct + "% Ready §7(" + est.totalAvailable() + "/" + est.totalBlocks() + "b) §8[📋BOM]");
					}
				}
			}
			int rawWidth = this.textRenderer.getWidth(bannerText);
			int maxBoxWidth = 144 - 8;
			float fittedScale = (rawWidth * scale > maxBoxWidth) ? ((float) maxBoxWidth / (float) rawWidth) : scale;
			context.getMatrices().scale(fittedScale, fittedScale, 1.0F);
			context.drawTextWithShadow(this.textRenderer, bannerText, 0, 2, 0xFFFFFF);
		} else {
			context.getMatrices().scale(scale, scale, 1.0F);
			Text titleText = Text.literal("§fMode: " + this.selectedMode.getFormattedName() + " §8[" + this.selectedSquad.getFormattedName() + "§8]");
			context.drawTextWithShadow(this.textRenderer, titleText, 0, 0, 0xFFFFFF);

			List<net.minecraft.text.OrderedText> lines = this.textRenderer.wrapLines(Text.literal(modeDesc), availableWidth);
			int lineY = 12;
			for (net.minecraft.text.OrderedText line : lines) {
				context.drawTextWithShadow(this.textRenderer, line, 0, lineY, 0xCCCCCC);
				lineY += this.textRenderer.fontHeight + 1;
			}
		}

		context.getMatrices().pop();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.shiftHeldOnOpen && !isShiftOrSneakDown()) {
			this.shiftHeldOnOpen = false;
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.pendingConfirmation != ConfirmationType.NONE) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				cancelConfirmation();
				return true;
			}
			return true;
		}

		if (isShiftOrSneakKey(keyCode, scanCode)) {
			if (this.shiftHeldOnOpen) {
				return true;
			}
			this.close();
			return true;
		}

		if (isCommandHubKey(keyCode, scanCode) || isInventoryKey(keyCode, scanCode)) {
			this.close();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (isShiftOrSneakKey(keyCode, scanCode) || !isShiftOrSneakDown()) {
			this.shiftHeldOnOpen = false;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		this.closed = true;
		syncToServer(false);
		if (this.client != null) {
			super.close();
		}
	}

	public boolean isShiftOrSneakDown() {
		try {
			MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
			if (mc != null && mc.getWindow() != null) {
				long handle = mc.getWindow().getHandle();
				if (handle != 0L) {
					if (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
						return true;
					}
					if (mc.options != null && mc.options.sneakKey != null) {
						InputUtil.Key boundKey = KeyBindingHelper.getBoundKeyOf(mc.options.sneakKey);
						if (boundKey != null && boundKey.getCategory() == InputUtil.Type.KEYSYM) {
							int code = boundKey.getCode();
							if (code > 0 && InputUtil.isKeyPressed(handle, code)) {
								return true;
							}
						}
					}
				}
			}
		} catch (Throwable ignored) {}
		return false;
	}

	public boolean isShiftOrSneakKey(int keyCode, int scanCode) {
		if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			return true;
		}
		try {
			MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
			if (mc != null && mc.options != null && mc.options.sneakKey != null && mc.options.sneakKey.matchesKey(keyCode, scanCode)) {
				return true;
			}
		} catch (Throwable ignored) {}
		return false;
	}

	public boolean isCommandHubKey(int keyCode, int scanCode) {
		try {
			if (ExampleModClient.commandHubKey != null && ExampleModClient.commandHubKey.matchesKey(keyCode, scanCode)) {
				return true;
			}
		} catch (Throwable ignored) {}
		return keyCode == GLFW.GLFW_KEY_V;
	}

	public boolean isInventoryKey(int keyCode, int scanCode) {
		try {
			MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
			if (mc != null && mc.options != null && mc.options.inventoryKey != null && mc.options.inventoryKey.matchesKey(keyCode, scanCode)) {
				return true;
			}
		} catch (Throwable ignored) {}
		return keyCode == GLFW.GLFW_KEY_E;
	}

	public boolean isShiftHeldOnOpen() {
		return this.shiftHeldOnOpen;
	}

	public void setShiftHeldOnOpen(boolean shiftHeldOnOpen) {
		this.shiftHeldOnOpen = shiftHeldOnOpen;
		this.initializedOpenState = true;
	}

	public boolean isClosed() {
		return this.closed;
	}

	public void setClosed(boolean closed) {
		this.closed = closed;
	}

	public boolean isInitializedOpenState() {
		return this.initializedOpenState;
	}

	public void setInitializedOpenState(boolean initializedOpenState) {
		this.initializedOpenState = initializedOpenState;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}