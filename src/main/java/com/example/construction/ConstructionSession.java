package com.example.construction;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.StructureBlueprint;
import com.example.entity.custom.MinionEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.AbstractCauldronBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Represents an active multiblock construction session coordinated by the player.
 * Manages topological task distribution to minion thralls, dependency validation,
 * construction lifecycle status, and completion state.
 */
public class ConstructionSession {

	/**
	 * State of the construction session.
	 */
	public enum SessionStatus {
		ACTIVE,
		PAUSED,
		COMPLETED,
		CANCELLED
	}

	/**
	 * Operational mode of the construction session: erecting a structure or tearing it down.
	 */
	public enum SessionMode {
		BUILD,
		DISMANTLE
	}

	private final UUID id;
	private final UUID ownerUuid;
	private final RegistryKey<World> dimension;
	private final BlockPos anchorPos;
	private final StructureBlueprint blueprint;
	private final boolean creative;
	private final SessionMode mode;
	private final List<ConstructionTask> tasks;
	private final BlockBox worldBoundingBox;
	private final Set<Identifier> curatedBlockIds;

	private SessionStatus status = SessionStatus.ACTIVE;
	private int completedCount = 0;
	private final long createdTick;
	private long lastActivityTick;
	private final Set<UUID> participatingMinions = ConcurrentHashMap.newKeySet();


	/**
	 * Creates a new ConstructionSession anchored at the specified world coordinate in default BUILD mode.
	 *
	 * @param ownerUuid   UUID of the player who initiated construction.
	 * @param dimension   World dimension key where construction is located.
	 * @param anchorPos   The world origin block position for this structure.
	 * @param blueprint   The structure blueprint to construct.
	 * @param creative    True if free instant placement (creative mode), false for survival inventory constraints.
	 * @param currentTick Current server tick count at session creation.
	 */
	public ConstructionSession(
		UUID ownerUuid,
		RegistryKey<World> dimension,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		boolean creative,
		long currentTick
	) {
		this(ownerUuid, dimension, anchorPos, blueprint, creative, currentTick, SessionMode.BUILD);
	}

	/**
	 * Creates a new ConstructionSession anchored at the specified world coordinate with an explicit session mode.
	 *
	 * @param ownerUuid   UUID of the player who initiated construction.
	 * @param dimension   World dimension key where construction is located.
	 * @param anchorPos   The world origin block position for this structure.
	 * @param blueprint   The structure blueprint to construct or dismantle.
	 * @param creative    True if free instant placement/breaking, false for survival inventory constraints.
	 * @param currentTick Current server tick count at session creation.
	 * @param mode        The operational mode: BUILD (bottom-up) or DISMANTLE (top-down reverse topological).
	 */
	public ConstructionSession(
		UUID ownerUuid,
		RegistryKey<World> dimension,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		boolean creative,
		long currentTick,
		SessionMode mode
	) {
		this(ownerUuid, dimension, anchorPos, blueprint, creative, currentTick, mode, null);
	}

	public ConstructionSession(
		UUID ownerUuid,
		RegistryKey<World> dimension,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		boolean creative,
		long currentTick,
		SessionMode mode,
		World world
	) {
		this(ownerUuid, dimension, anchorPos, blueprint, creative, currentTick, mode, world, Collections.emptySet());
	}

	/**
	 * Creates a new ConstructionSession anchored at the specified world coordinate with an explicit session mode,
	 * world reference for terrain inspection, and an optional curated set of target block identifiers for selective dismantling.
	 *
	 * @param ownerUuid       UUID of the player who initiated construction.
	 * @param dimension       World dimension key where construction is located.
	 * @param anchorPos       The world origin block position for this structure.
	 * @param blueprint       The structure blueprint to construct or dismantle.
	 * @param creative        True if free instant placement/breaking, false for survival inventory constraints.
	 * @param currentTick     Current server tick count at session creation.
	 * @param mode            The operational mode: BUILD (bottom-up) or DISMANTLE (top-down reverse topological).
	 * @param world           Optional server world context for block hardness inspection.
	 * @param curatedBlockIds Optional curated set of block identifiers to dismantle (empty = all non-air blocks).
	 */
	public ConstructionSession(
		UUID ownerUuid,
		RegistryKey<World> dimension,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		boolean creative,
		long currentTick,
		SessionMode mode,
		World world,
		Set<Identifier> curatedBlockIds
	) {
		this.id = UUID.randomUUID();
		this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid cannot be null");
		this.dimension = Objects.requireNonNull(dimension, "dimension cannot be null");
		this.anchorPos = Objects.requireNonNull(anchorPos, "anchorPos cannot be null").toImmutable();
		this.blueprint = Objects.requireNonNull(blueprint, "blueprint cannot be null");
		this.creative = creative;
		this.createdTick = currentTick;
		this.lastActivityTick = currentTick;
		this.mode = mode != null ? mode : SessionMode.BUILD;
		this.curatedBlockIds = (curatedBlockIds != null && !curatedBlockIds.isEmpty())
			? Collections.unmodifiableSet(new java.util.LinkedHashSet<>(curatedBlockIds))
			: Collections.emptySet();

		// Calculate world-space bounding box
		BlockBox localBox = blueprint.getBoundingBox();
		this.worldBoundingBox = new BlockBox(
			this.anchorPos.getX() + localBox.getMinX(),
			this.anchorPos.getY() + localBox.getMinY(),
			this.anchorPos.getZ() + localBox.getMinZ(),
			this.anchorPos.getX() + localBox.getMaxX(),
			this.anchorPos.getY() + localBox.getMaxY(),
			this.anchorPos.getZ() + localBox.getMaxZ()
		);

		List<ConstructionTask> taskList = new ArrayList<>();
		if (this.mode == SessionMode.DISMANTLE && world != null) {
			// In DISMANTLE mode with active world:
			// Scan the entire selected/highlighted 3D area for real non-air blocks matching curated filter.
			// Miners must mine EVERYTHING matching the curation, but NEVER mine air!
			Map<BlockPos, BlockState> blocksToMine = new java.util.LinkedHashMap<>();

			// 1. Scan the full worldBoundingBox from top to bottom (highest Y to lowest Y)
			for (int y = this.worldBoundingBox.getMaxY(); y >= this.worldBoundingBox.getMinY(); y--) {
				for (int x = this.worldBoundingBox.getMinX(); x <= this.worldBoundingBox.getMaxX(); x++) {
					for (int z = this.worldBoundingBox.getMinZ(); z <= this.worldBoundingBox.getMaxZ(); z++) {
						BlockPos pos = new BlockPos(x, y, z);
						BlockState state = world.getBlockState(pos);
						if (!state.isAir() && !isIndestructible(state, world, pos)) {
							if (isFluid(state) && !state.getFluidState().isStill()) {
								continue;
							}
							try {
								Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
								if (this.curatedBlockIds.isEmpty() || (blockId != null && this.curatedBlockIds.contains(blockId))) {
									blocksToMine.put(pos, state);
								}
							} catch (Throwable ignored) {
								if (this.curatedBlockIds.isEmpty()) {
									blocksToMine.put(pos, state);
								}
							}
						}
					}
				}
			}

			// 2. Also ensure any non-air blocks specified in blueprint are included if outside box
			for (BlueprintBlock bpBlock : blueprint.getBlocks()) {
				BlockPos pos = this.anchorPos.add(bpBlock.offset().getX(), bpBlock.offset().getY(), bpBlock.offset().getZ());
				BlockState state = world.getBlockState(pos);
				if (!state.isAir() && !isIndestructible(state, world, pos)) {
					if (isFluid(state) && !state.getFluidState().isStill()) {
						continue;
					}
					try {
						Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
						if (this.curatedBlockIds.isEmpty() || (blockId != null && this.curatedBlockIds.contains(blockId))) {
							blocksToMine.putIfAbsent(pos, state);
						}
					} catch (Throwable ignored) {
						if (this.curatedBlockIds.isEmpty()) {
							blocksToMine.putIfAbsent(pos, state);
						}
					}
				}
			}

			// Sort strictly top-down (highest Y first, then X and Z)
			List<Map.Entry<BlockPos, BlockState>> sortedEntries = new ArrayList<>(blocksToMine.entrySet());
			sortedEntries.sort((a, b) -> {
				int cmpY = Integer.compare(b.getKey().getY(), a.getKey().getY());
				if (cmpY != 0) return cmpY;
				int cmpX = Integer.compare(a.getKey().getX(), b.getKey().getX());
				if (cmpX != 0) return cmpX;
				return Integer.compare(a.getKey().getZ(), b.getKey().getZ());
			});

			for (int i = 0; i < sortedEntries.size(); i++) {
				Map.Entry<BlockPos, BlockState> entry = sortedEntries.get(i);
				BlockPos pos = entry.getKey();
				BlockState state = entry.getValue();
				BlockPos localOffset = pos.subtract(this.anchorPos);
				BlueprintBlock bpBlock = new BlueprintBlock(localOffset, state);
				taskList.add(new ConstructionTask(i, bpBlock, this.anchorPos));
			}
		} else {
			// BUILD mode or offline/mock tests where world is null
			List<BlueprintBlock> blueprintBlocks = new ArrayList<>(blueprint.getBlocks());
			if (this.mode == SessionMode.DISMANTLE) {
				Collections.reverse(blueprintBlocks);
				blueprintBlocks.removeIf(bpBlock -> {
					BlockPos targetPos = this.anchorPos.add(bpBlock.offset().getX(), bpBlock.offset().getY(), bpBlock.offset().getZ());
					if (isIndestructible(bpBlock.state(), world, targetPos)) {
						return true;
					}
					if (!this.curatedBlockIds.isEmpty() && bpBlock.state() != null && bpBlock.state().getBlock() != null) {
						try {
							Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(bpBlock.state().getBlock());
							if (blockId != null) {
								return !this.curatedBlockIds.contains(blockId);
							}
						} catch (Throwable ignored) {}
					}
					return false;
				});
			}
			for (int i = 0; i < blueprintBlocks.size(); i++) {
				taskList.add(new ConstructionTask(i, blueprintBlocks.get(i), this.anchorPos));
			}
		}

		this.tasks = Collections.unmodifiableList(taskList);

		if (this.tasks.isEmpty() && this.mode == SessionMode.DISMANTLE) {
			this.status = SessionStatus.COMPLETED;
		}
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	public RegistryKey<World> getDimension() {
		return this.dimension;
	}

	public BlockPos getAnchorPos() {
		return this.anchorPos;
	}

	public StructureBlueprint getBlueprint() {
		return this.blueprint;
	}

	public boolean isCreative() {
		return this.creative;
	}

	public SessionMode getMode() {
		return this.mode;
	}

	public boolean isDismantle() {
		return this.mode == SessionMode.DISMANTLE;
	}

	public boolean isBuild() {
		return this.mode == SessionMode.BUILD;
	}

	public SessionStatus getStatus() {
		return this.status;
	}

	public boolean isActive() {
		return this.status == SessionStatus.ACTIVE;
	}

	public boolean isCompleted() {
		return this.status == SessionStatus.COMPLETED;
	}

	public void markCompleted() {
		this.status = SessionStatus.COMPLETED;
	}

	public int getTotalBlocks() {
		return this.tasks.size();
	}

	public int getCompletedBlocks() {
		return this.completedCount;
	}

	public float getProgress() {
		if (this.tasks.isEmpty()) {
			return 1.0F;
		}
		return (float) this.completedCount / (float) this.tasks.size();
	}

	public int getProgressPercent() {
		return Math.round(getProgress() * 100.0F);
	}

	public List<ConstructionTask> getTasks() {
		return this.tasks;
	}

	public BlockBox getWorldBoundingBox() {
		return this.worldBoundingBox;
	}

	public boolean containsBlockPos(BlockPos pos) {
		if (pos == null) return false;
		if (this.worldBoundingBox != null && this.worldBoundingBox.contains(pos)) {
			return true;
		}
		if (this.tasks != null) {
			for (ConstructionTask task : this.tasks) {
				if (task.getWorldPos().equals(pos)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Checks whether any non-air, destructible blocks still remain within this session's
	 * 3D bounding box in the world. Used by DISMANTLE mode to verify complete area clearance.
	 *
	 * @param world The world to inspect.
	 * @return True if at least one non-air, non-indestructible block remains; false if completely cleared.
	 */
	public static boolean isFluid(BlockState state) {
		return state != null && (state.isLiquid() || state.getBlock() instanceof net.minecraft.block.FluidBlock || !state.getFluidState().isEmpty());
	}

	public boolean hasRemainingBlocksInWorld(World world) {
		if (world == null) {
			return this.completedCount < this.tasks.size();
		}
		int minChunkX = this.worldBoundingBox.getMinX() >> 4;
		int maxChunkX = this.worldBoundingBox.getMaxX() >> 4;
		int minChunkZ = this.worldBoundingBox.getMinZ() >> 4;
		int maxChunkZ = this.worldBoundingBox.getMaxZ() >> 4;
		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				if (!world.isChunkLoaded(cx, cz)) {
					// Chunk is unloaded; assume remaining blocks exist so session is not falsely completed
					return true;
				}
			}
		}
		for (int y = this.worldBoundingBox.getMinY(); y <= this.worldBoundingBox.getMaxY(); y++) {
			for (int x = this.worldBoundingBox.getMinX(); x <= this.worldBoundingBox.getMaxX(); x++) {
				for (int z = this.worldBoundingBox.getMinZ(); z <= this.worldBoundingBox.getMaxZ(); z++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = world.getBlockState(pos);
					if (!state.isAir() && !isIndestructible(state, world, pos)) {
						boolean isIgnoredFluid = isFluid(state) && (!state.getFluidState().isStill() || (!this.curatedBlockIds.isEmpty() && !this.curatedBlockIds.contains(net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()))));
						if (!isFluid(state) || !isIgnoredFluid) {
							if (this.curatedBlockIds.isEmpty() || this.curatedBlockIds.contains(net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()))) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	public Set<Identifier> getCuratedBlockIds() {
		return this.curatedBlockIds;
	}

	public long getCreatedTick() {
		return this.createdTick;
	}

	public long getLastActivityTick() {
		return this.lastActivityTick;
	}

	/**
	 * Attempts to claim the next available task in topological order for a minion worker.
	 *
	 * @param minionUuid  UUID of the minion requesting work.
	 * @param currentTick Current server world tick.
	 * @param world       The server world instance for structural support validation.
	 * @return The claimed ConstructionTask, or null if no tasks are currently ready or available.
	 */
	public synchronized ConstructionTask claimNextTask(UUID minionUuid, long currentTick, World world) {
		if (this.status != SessionStatus.ACTIVE) {
			return null;
		}

		if (ConstructionManager.getInstance().isBlockedByOverlappingOlderSession(this)) {
			return null;
		}

		// First check if this minion already has a claimed task in this session
		for (ConstructionTask task : this.tasks) {
			if (task.isClaimed() && Objects.equals(task.getClaimedBy(), minionUuid)) {
				return task;
			}
		}

		// Find the next ready pending task in topological sequence
		for (ConstructionTask task : this.tasks) {
			if (task.isPending()) {
				// Strict safeguard: if the target block is already air or indestructible/bedrock in DISMANTLE mode,
				// auto-complete it immediately so minions never mine empty air and the session completes cleanly!
				if (this.mode == SessionMode.DISMANTLE) {
					BlueprintBlock bpBlock = task.getBlueprintBlock();
					BlockPos targetPos = task.getWorldPos();
					if (world != null) {
						if (!world.isChunkLoaded(targetPos.getX() >> 4, targetPos.getZ() >> 4)) {
							// Chunk is unloaded; do not inspect or auto-complete based on virtual air
							continue;
						}
						BlockState worldState = world.getBlockState(targetPos);
						boolean isFlowing = isFluid(worldState) && !worldState.getFluidState().isStill();
						boolean isNotCurated = !this.curatedBlockIds.isEmpty() && !this.curatedBlockIds.contains(net.minecraft.registry.Registries.BLOCK.getId(worldState.getBlock()));
						if (worldState.isAir() || isFlowing || isIndestructible(worldState, world, targetPos) || isNotCurated) {
							task.complete();
							this.completedCount++;
							if (this.completedCount >= this.tasks.size() || !hasRemainingBlocksInWorld(world)) {
								this.status = SessionStatus.COMPLETED;
								if (world instanceof ServerWorld serverWorld) {
									ConstructionManager.getInstance().completeSession(this, serverWorld);
								}
								return null;
							}
							continue;
						}
					} else if (isIndestructible(bpBlock.state(), null, targetPos) || (isFluid(bpBlock.state()) && !bpBlock.state().getFluidState().isStill())) {
						task.complete();
						this.completedCount++;
						if (this.completedCount >= this.tasks.size()) {
							this.status = SessionStatus.COMPLETED;
							return null;
						}
						continue;
					}
				}

				// BUILD mode: if the block is already in place (placed by a peer minion, the player, or a
				// stolen task finished elsewhere), mark it done so nobody walks over to re-place it.
				boolean isChunkLoaded = world != null && world.isChunkLoaded(task.getWorldPos().getX() >> 4, task.getWorldPos().getZ() >> 4);
				boolean isMatchingBlock = isChunkLoaded && (
					world.getBlockState(task.getWorldPos()).equals(task.getBlueprintBlock().state())
					|| (isConnectableBlock(task.getBlueprintBlock().state()) && isConnectableMatching(world.getBlockState(task.getWorldPos()), task.getBlueprintBlock().state()))
					|| isSatisfiedBlockState(world.getBlockState(task.getWorldPos()), task.getBlueprintBlock().state())
				);
				boolean isMatchingFluid = isChunkLoaded && (
					(task.getBlueprintBlock().state().isOf(Blocks.WATER) && world.getBlockState(task.getWorldPos()).isOf(Blocks.WATER) && world.getFluidState(task.getWorldPos()).isStill())
					|| (task.getBlueprintBlock().state().isOf(Blocks.LAVA) && world.getBlockState(task.getWorldPos()).isOf(Blocks.LAVA) && world.getFluidState(task.getWorldPos()).isStill())
				);
				if (this.mode == SessionMode.BUILD && isChunkLoaded && (isMatchingBlock || isMatchingFluid)) {
					task.complete();
					this.completedCount++;
					if (this.completedCount >= this.tasks.size()) {
						this.status = SessionStatus.COMPLETED;
						if (world instanceof ServerWorld serverWorld) {
							ConstructionManager.getInstance().completeSession(this, serverWorld);
						}
						return null;
					}
					continue;
				}

				if (isTaskReady(task, world)) {
					// Non-blocking survival queue: if this minion recently lacked materials for this task,
					// skip it temporarily to let other ready tasks proceed without deadlocking the session!
					if (task.hasDeficiencyCooldown(minionUuid, currentTick)) {
						continue;
					}
					if (task.claim(minionUuid, currentTick)) {
						this.lastActivityTick = currentTick;
						this.participatingMinions.add(minionUuid);
						return task;
					}
				}
			}
		}

		// No free task: take over a ready task whose claimant is absent, stalled, or far away.
		// The original claimant forgets the task on its next tick as if it had completed it.
		if (world instanceof ServerWorld serverWorld) {
			net.minecraft.entity.Entity requester = serverWorld.getEntity(minionUuid);
			if (requester != null) {
				for (ConstructionTask task : this.tasks) {
					if (task.isClaimed() && !task.isClaimedBy(minionUuid)
							&& isClaimantAbsent(serverWorld, task, requester, currentTick)
							&& isTaskReady(task, world)
							&& task.reassign(minionUuid, currentTick)) {
						this.lastActivityTick = currentTick;
						this.participatingMinions.add(minionUuid);
						return task;
					}
				}
			}
		}

		return null;
	}

	/** Minimum lease age before a task may be taken over (prevents claim thrashing). */
	public static final long STEAL_GRACE_TICKS = 60L;
	/** Lease age after which a closer peer may take over even if the claimant is still working. */
	public static final long STALLED_LEASE_TICKS = 200L;
	/** Claimants farther than this from their task are considered away from the job site. */
	public static final double ABSENT_DISTANCE = 24.0D;

	/**
	 * Decides whether a task's current claimant is effectively absent so an on-site peer may finish it.
	 * A claimant is absent when it is unloaded/dead, no longer building (sitting, fighting, recalled),
	 * stranded far from the task, or has held the lease too long while the requester is closer.
	 */
	public static boolean isClaimantAbsent(ServerWorld world, ConstructionTask task, net.minecraft.entity.Entity requester, long currentTick) {
		long age = currentTick - task.getClaimTick();
		if (age < STEAL_GRACE_TICKS) {
			return false;
		}
		UUID claimantId = task.getClaimedBy();
		if (claimantId == null) {
			return true;
		}
		net.minecraft.entity.Entity claimantEntity = world.getEntity(claimantId);
		if (!(claimantEntity instanceof MinionEntity claimant) || !claimant.isAlive()) {
			return true;
		}
		if (claimant.isSitting() || !claimant.isActivelyBuilding()) {
			return true;
		}
		// 1. If stalled for over STALLED_LEASE_TICKS (10 seconds), an active peer may take over immediately
		if (age > STALLED_LEASE_TICKS) {
			return true;
		}
		net.minecraft.util.math.Vec3d center = task.getWorldPos().toCenterPos();
		double claimantDistSq = claimant.squaredDistanceTo(center);
		double requesterDistSq = requester.squaredDistanceTo(center);
		if (requesterDistSq >= claimantDistSq) {
			return false;
		}
		return claimantDistSq > ABSENT_DISTANCE * ABSENT_DISTANCE;
	}

	/**
	 * Determines whether a construction or deconstruction task's physical prerequisites are satisfied.
	 *
	 * @param task  The task to evaluate.
	 * @param world The server world to inspect.
	 * @return True if the block can be physically operated on now.
	 */
	public boolean isTaskReady(ConstructionTask task, World world) {
		if (this.mode == SessionMode.DISMANTLE) {
			return isDismantleTaskReady(task, world);
		}
		return isBuildTaskReady(task, world);
	}

	/**
	 * Determines whether a construction task's physical prerequisites are satisfied.
	 * Ensures hanging blocks have ceiling support and elevated blocks have foundations or prior layers placed.
	 *
	 * @param task  The task to evaluate.
	 * @param world The server world to inspect.
	 * @return True if the block can be physically placed now.
	 */
	public boolean isBuildTaskReady(ConstructionTask task, World world) {
		if (world == null) {
			return true;
		}
		BlockPos targetPos = task.getWorldPos();
		if (!world.isChunkLoaded(targetPos.getX() >> 4, targetPos.getZ() >> 4)) {
			return false;
		}
		BlueprintBlock bpBlock = task.getBlueprintBlock();
		BlockState targetState = bpBlock.state();

		// 1. Hanging blocks MUST have overhead solid support already in the world
		if (bpBlock.isHanging()) {
			BlockPos overhead = targetPos.up();
			return !world.getBlockState(overhead).isAir();
		}

		// 1.5 Physical Support Dependencies for fragile / gravity-affected blocks
		if (targetState != null) {
			// Gravity/Falling blocks (Sand, Gravel, Concrete Powder, Anvil) must have solid support beneath
			if (targetState.getBlock() instanceof net.minecraft.block.FallingBlock) {
				BlockPos underneath = targetPos.down();
				BlockState underState = world.getBlockState(underneath);
				if (underState.isAir() || !underState.isSolidBlock(world, underneath)) {
					return false;
				}
			}

			// Double-block upper halves: require lower half placed underneath
			if (targetState.contains(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)
					&& targetState.get(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF) == net.minecraft.block.enums.DoubleBlockHalf.UPPER) {
				BlockPos underneath = targetPos.down();
				if (world.getBlockState(underneath).isAir()) {
					return false;
				}
			}

			// Bed head: requires foot block placed
			if (targetState.contains(net.minecraft.block.BedBlock.PART)
					&& targetState.get(net.minecraft.block.BedBlock.PART) == net.minecraft.block.enums.BedPart.HEAD) {
				Direction facing = targetState.get(net.minecraft.block.BedBlock.FACING);
				BlockPos footPos = targetPos.offset(facing.getOpposite());
				if (world.getBlockState(footPos).isAir()) {
					return false;
				}
			}

			// Potted plants: require solid support underneath
			if (targetState.getBlock() instanceof net.minecraft.block.FlowerPotBlock) {
				BlockPos underneath = targetPos.down();
				if (world.getBlockState(underneath).isAir()) {
					return false;
				}
			}

			// Crops (Wheat, Carrots, Potatoes, Beetroots, Stems) MUST have Farmland directly underneath
			if (targetState.getBlock() instanceof net.minecraft.block.CropBlock
					|| targetState.getBlock() instanceof net.minecraft.block.StemBlock
					|| targetState.getBlock() instanceof net.minecraft.block.AttachedStemBlock) {
				BlockPos underneath = targetPos.down();
				if (!world.getBlockState(underneath).isOf(net.minecraft.block.Blocks.FARMLAND)) {
					return false;
				}
			}

			// Nether wart must have Soul Sand directly underneath
			if (targetState.isOf(net.minecraft.block.Blocks.NETHER_WART)) {
				BlockPos underneath = targetPos.down();
				if (!world.getBlockState(underneath).isOf(net.minecraft.block.Blocks.SOUL_SAND)) {
					return false;
				}
			}

			// Cocoa Beans must have a Jungle Log/Wood on attached face
			if (targetState.getBlock() instanceof net.minecraft.block.CocoaBlock) {
				net.minecraft.util.math.Direction facing = targetState.get(net.minecraft.block.CocoaBlock.FACING);
				BlockPos logPos = targetPos.offset(facing);
				BlockState logState = world.getBlockState(logPos);
				if (!logState.isOf(net.minecraft.block.Blocks.JUNGLE_LOG)
						&& !logState.isOf(net.minecraft.block.Blocks.STRIPPED_JUNGLE_LOG)
						&& !logState.isOf(net.minecraft.block.Blocks.JUNGLE_WOOD)
						&& !logState.isOf(net.minecraft.block.Blocks.STRIPPED_JUNGLE_WOOD)) {
					return false;
				}
			}
		}

		// 2. Base layer (effective Y <= 0) can always be placed
		if (bpBlock.getEffectiveY() <= 0) {
			return true;
		}

		// 3. Elevated non-hanging blocks: either the block below is not air, or all tasks on lower effective layers are completed
		if (!bpBlock.isHanging()) {
			BlockPos underneath = targetPos.down();
			if (!world.getBlockState(underneath).isAir()) {
				return true;
			}
		}

		// Check if any unfinished structural tasks exist at strictly lower effective Y offsets
		int targetEffectiveY = bpBlock.getEffectiveY();
		for (ConstructionTask other : this.tasks) {
			if (other.getBlueprintBlock().getEffectiveY() < targetEffectiveY && !other.isCompleted()) {
				// Non-structural interior furniture/decorations (e.g. Anvil, Grindstone, Chest) do not block structural layers
				if (isNonStructuralDetail(other.getBlueprintBlock().state())) {
					continue;
				}
				return false;
			}
		}

		return true;
	}

	private static boolean isNonStructuralDetail(BlockState state) {
		if (state == null) return false;
		net.minecraft.block.Block b = state.getBlock();
		return b instanceof net.minecraft.block.AnvilBlock
			|| b instanceof net.minecraft.block.CropBlock
			|| b instanceof net.minecraft.block.StemBlock
			|| b instanceof net.minecraft.block.AttachedStemBlock
			|| b instanceof net.minecraft.block.CocoaBlock
			|| b instanceof net.minecraft.block.SugarCaneBlock
			|| b instanceof net.minecraft.block.BlastFurnaceBlock
			|| b instanceof net.minecraft.block.GrindstoneBlock
			|| b instanceof net.minecraft.block.SmithingTableBlock
			|| b instanceof net.minecraft.block.ChestBlock
			|| b instanceof net.minecraft.block.BarrelBlock
			|| b instanceof net.minecraft.block.BedBlock
			|| b instanceof net.minecraft.block.CraftingTableBlock
			|| b instanceof net.minecraft.block.FurnaceBlock
			|| b instanceof net.minecraft.block.SmokerBlock
			|| b instanceof net.minecraft.block.FletchingTableBlock
			|| b instanceof net.minecraft.block.CartographyTableBlock
			|| b instanceof net.minecraft.block.BrewingStandBlock
			|| b instanceof net.minecraft.block.CauldronBlock
			|| b instanceof net.minecraft.block.LanternBlock
			|| b instanceof net.minecraft.block.TorchBlock
			|| b instanceof net.minecraft.block.FlowerPotBlock
			|| b instanceof net.minecraft.block.CampfireBlock;
	}

	/**
	 * Determines whether a block state is indestructible / unbreakable (e.g. Bedrock, barrier, end portal,
	 * or any block with negative hardness < 0.0F) and must never be broken or dismantled.
	 *
	 * @param state The block state to evaluate.
	 * @param world The world instance (may be null).
	 * @param pos   The block position (may be null).
	 * @return True if the block is indestructible or bedrock.
	 */
	public static boolean isIndestructible(BlockState state, World world, BlockPos pos) {
		if (state == null) {
			return false;
		}
		try {
			if (state.isOf(Blocks.BEDROCK)) {
				return true;
			}
		} catch (Throwable ignored) {}
		try {
			if (world != null && pos != null) {
				return state.getHardness(world, pos) < 0.0F;
			}
			return state.getBlock().getHardness() < 0.0F;
		} catch (Throwable e) {
			return false;
		}
	}

	/**
	 * Determines whether a block at the given position in the world is indestructible or bedrock.
	 *
	 * @param world The server world.
	 * @param pos   The world position to check.
	 * @return True if the block at pos is indestructible or bedrock.
	 */
	public static boolean isIndestructible(World world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		return isIndestructible(world.getBlockState(pos), world, pos);
	}

	/**
	 * Determines whether a deconstruction task's prerequisites are met.
	 * Executes in top-down reverse topological order, verifying that all upper
	 * blocks and hanging decorations are cleared before supporting structures are removed.
	 *
	 * @param task  The deconstruction task to evaluate.
	 * @param world The server world to inspect.
	 * @return True if the block is physically safe to dismantle now.
	 */
	public boolean isDismantleTaskReady(ConstructionTask task, World world) {
		if (task == null) {
			return false;
		}

		BlueprintBlock bpBlock = task.getBlueprintBlock();
		BlockPos targetPos = task.getWorldPos();

		// Safeguard: Indestructible blocks (hardness < 0.0F or Blocks.BEDROCK) and flowing fluids must never be marked ready for dismantling
		boolean isFlowingBp = isFluid(bpBlock.state()) && !bpBlock.state().getFluidState().isStill();
		if (isIndestructible(bpBlock.state(), world, targetPos) || isFlowingBp) {
			return false;
		}
		if (world != null) {
			if (!world.isChunkLoaded(targetPos.getX() >> 4, targetPos.getZ() >> 4)) {
				return false;
			}
			BlockState worldState = world.getBlockState(targetPos);
			boolean isFlowingWorld = isFluid(worldState) && !worldState.getFluidState().isStill();
			if (isIndestructible(worldState, world, targetPos) || isFlowingWorld) {
				return false;
			}
		}

		// 1. If any non-hanging block resting directly above this block in the blueprint is not yet completed (dismantled),
		// we cannot dismantle this supporting block.
		if (!bpBlock.isHanging()) {
			BlockPos abovePos = targetPos.up();
			for (ConstructionTask other : this.tasks) {
				if (!other.isCompleted() && !other.getBlueprintBlock().isHanging() && other.getWorldPos().equals(abovePos)) {
					if (world != null) {
						BlockState aboveState = world.getBlockState(abovePos);
						boolean isFlowingAbove = isFluid(world.getBlockState(abovePos)) && !aboveState.getFluidState().isStill();
						boolean isNotCuratedAbove = !this.curatedBlockIds.isEmpty() && !this.curatedBlockIds.contains(net.minecraft.registry.Registries.BLOCK.getId(aboveState.getBlock()));
						if (aboveState.isAir() || isFlowingAbove || isIndestructible(world, abovePos) || isNotCuratedAbove) {
							other.complete();
							this.completedCount++;
							continue;
						}
					}
					return false;
				}
			}
		}

		// 2. If any hanging block (e.g. lantern) is attached underneath this block and not yet dismantled,
		// the hanging decoration must be removed first before the ceiling block is broken.
		BlockPos belowPos = targetPos.down();
		for (ConstructionTask other : this.tasks) {
			if (!other.isCompleted() && other.getBlueprintBlock().isHanging() && other.getWorldPos().equals(belowPos)) {
				if (world != null) {
					BlockState belowState = world.getBlockState(belowPos);
					boolean isFlowingBelow = isFluid(world.getBlockState(belowPos)) && !belowState.getFluidState().isStill();
					boolean isNotCuratedBelow = !this.curatedBlockIds.isEmpty() && !this.curatedBlockIds.contains(net.minecraft.registry.Registries.BLOCK.getId(belowState.getBlock()));
					if (belowState.isAir() || isFlowingBelow || isIndestructible(world, belowPos) || isNotCuratedBelow) {
						other.complete();
						this.completedCount++;
						continue;
					}
				}
				return false;
			}
		}

		// 3. Enforce top-down column clearance:
		// Any unfinished tasks directly above in the same vertical column must be completed first.
		// Minions working in independent columns do not stall waiting for peers to finish the entire layer.
		int targetEffectiveY = bpBlock.getEffectiveY();
		for (ConstructionTask other : this.tasks) {
			if (!other.isCompleted()
					&& other.getWorldPos().getX() == targetPos.getX()
					&& other.getWorldPos().getZ() == targetPos.getZ()
					&& other.getWorldPos().getY() > targetPos.getY()) {
				if (world != null) {
					BlockState otherState = world.getBlockState(other.getWorldPos());
					boolean isFlowingOther = isFluid(otherState) && !otherState.getFluidState().isStill();
					boolean isNotCuratedOther = !this.curatedBlockIds.isEmpty() && !this.curatedBlockIds.contains(net.minecraft.registry.Registries.BLOCK.getId(otherState.getBlock()));
					if (otherState.isAir() || isFlowingOther || isIndestructible(world, other.getWorldPos()) || isNotCuratedOther) {
						other.complete();
						this.completedCount++;
						continue;
					}
				}
				return false;
			}
		}

		// 4. On the same effective Y layer, hanging blocks must be dismantled before non-hanging ceiling blocks
		if (!bpBlock.isHanging()) {
			for (ConstructionTask other : this.tasks) {
				if (!other.isCompleted() && other.getBlueprintBlock().isHanging()
						&& other.getBlueprintBlock().getEffectiveY() == targetEffectiveY) {
					if (other.getWorldPos().getX() == targetPos.getX() && other.getWorldPos().getZ() == targetPos.getZ()) {
						if (world != null) {
							BlockState otherPosState = world.getBlockState(other.getWorldPos());
							boolean isFlowingOtherPos = isFluid(world.getBlockState(other.getWorldPos())) && !otherPosState.getFluidState().isStill();
							boolean isNotCuratedOtherPos = !this.curatedBlockIds.isEmpty() && !this.curatedBlockIds.contains(net.minecraft.registry.Registries.BLOCK.getId(otherPosState.getBlock()));
							if (otherPosState.isAir() || isFlowingOtherPos || isIndestructible(world, other.getWorldPos()) || isNotCuratedOtherPos) {
								other.complete();
								this.completedCount++;
								continue;
							}
						}
						return false;
					}
				}
			}
		}

		return true;
	}

	/**
	 * Releases a previously claimed task back to the pending queue.
	 *
	 * @param task The task to release.
	 */
	public synchronized void releaseTask(ConstructionTask task) {
		if (task != null && this.tasks.contains(task)) {
			task.release();
		}
	}

	/**
	 * Releases a task only if the given minion still holds its lease (a peer may have taken it over).
	 *
	 * @param task       The task to release.
	 * @param minionUuid The minion releasing it.
	 */
	public synchronized void releaseTask(ConstructionTask task, UUID minionUuid) {
		if (task != null && this.tasks.contains(task)) {
			task.releaseIfOwnedBy(minionUuid);
		}
	}

	/**
	 * Marks a construction task as completed and checks for overall session completion.
	 *
	 * @param task  The completed construction task.
	 * @param world The server world where construction occurred.
	 */
	public synchronized void completeTask(ConstructionTask task, ServerWorld world) {
		if (task != null && this.tasks.contains(task) && !task.isCompleted()) {
			if (task.getClaimedBy() != null) {
				this.participatingMinions.add(task.getClaimedBy());
			}
			task.complete();
			this.completedCount++;
			this.lastActivityTick = world.getTime();

			// If completing the lower half of a door, auto-complete the corresponding upper half task
			BlockState state = task.getBlueprintBlock().state();
			if (this.mode == SessionMode.BUILD && state.getBlock() instanceof DoorBlock && state.contains(DoorBlock.HALF) && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				BlockPos upperPos = task.getWorldPos().up();
				for (ConstructionTask other : this.tasks) {
					if (!other.isCompleted() && other.getWorldPos().equals(upperPos)) {
						BlockState otherState = other.getBlueprintBlock().state();
						if (otherState.getBlock() instanceof DoorBlock && otherState.contains(DoorBlock.HALF) && otherState.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
							other.complete();
							this.completedCount++;
							break;
						}
					}
				}
			}

			// If completing a bed part, auto-complete the corresponding other bed part task
			if (state.getBlock() instanceof BedBlock && state.contains(BedBlock.PART)) {
				Direction facing = state.get(BedBlock.FACING);
				net.minecraft.block.enums.BedPart part = state.get(BedBlock.PART);
				BlockPos otherPos = (part == net.minecraft.block.enums.BedPart.FOOT) ? task.getWorldPos().offset(facing) : task.getWorldPos().offset(facing.getOpposite());
				for (ConstructionTask other : this.tasks) {
					if (!other.isCompleted() && other.getWorldPos().equals(otherPos)) {
						BlockState otherState = other.getBlueprintBlock().state();
						if (otherState.getBlock() instanceof BedBlock) {
							other.complete();
							this.completedCount++;
							break;
						}
					}
				}
			}

			boolean allDone = this.completedCount >= this.tasks.size();
			if (!allDone && this.mode == SessionMode.DISMANTLE) {
				allDone = !hasRemainingBlocksInWorld(world);
			}

			if (allDone) {
				this.status = SessionStatus.COMPLETED;
				ConstructionManager.getInstance().completeSession(this, world);
			}
		}
	}

	/**
	 * Re-queues tasks that have been claimed longer than the given timeout duration
	 * (e.g. minion worker was killed, fell into lava, or became unreachable).
	 *
	 * @param currentTick  Current server world tick.
	 * @param timeoutTicks Maximum tick lease before reclaiming (e.g. 300 ticks = 15s).
	 */
	public synchronized void cleanStaleClaims(long currentTick, long timeoutTicks) {
		if (this.status != SessionStatus.ACTIVE) {
			return;
		}

		for (ConstructionTask task : this.tasks) {
			if (task.isClaimed() && (currentTick - task.getClaimTick() > timeoutTicks)) {
				task.release();
			}
		}
	}

	/**
	 * Cancels this construction session.
	 */
	public synchronized void cancel() {
		this.status = SessionStatus.CANCELLED;
		for (ConstructionTask task : this.tasks) {
			if (task.isClaimed()) {
				task.release();
			}
		}
	}

	/**
	 * Pauses this construction session.
	 */
	public synchronized void pause() {
		if (this.status == SessionStatus.ACTIVE) {
			this.status = SessionStatus.PAUSED;
		}
	}

	/**
	 * Resumes a paused construction session.
	 */
	public synchronized void resume() {
		if (this.status == SessionStatus.PAUSED) {
			this.status = SessionStatus.ACTIVE;
		}
	}



	/**
	 * Checks if a minion thrall is actively engaged in this session by holding a claimed
	 * task.
	 *
	 * @param minionUuid The minion's unique ID.
	 * @return True if actively engaged in this session.
	 */
	public synchronized boolean isMinionEngaged(UUID minionUuid) {
		if (minionUuid == null || this.status != SessionStatus.ACTIVE) {
			return false;
		}
		for (ConstructionTask task : this.tasks) {
			if (task.isClaimed() && minionUuid.equals(task.getClaimedBy())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Registers a minion UUID as a verified contributor/worker for this session.
	 *
	 * @param minionUuid The minion UUID to register.
	 */
	public void registerParticipant(UUID minionUuid) {
		if (minionUuid != null) {
			this.participatingMinions.add(minionUuid);
		}
	}

	/**
	 * Checks whether the specified minion actively claimed a task or contributed work to this session.
	 *
	 * @param minionUuid The minion UUID to test.
	 * @return True if the minion contributed to this session.
	 */
	public boolean isParticipant(UUID minionUuid) {
		return minionUuid != null && this.participatingMinions.contains(minionUuid);
	}

	/**
	 * Checks whether any minions have been registered as participants in this session.
	 *
	 * @return True if at least one minion participated.
	 */
	public boolean hasParticipants() {
		return !this.participatingMinions.isEmpty();
	}

	/**
	 * Returns an unmodifiable set of all minion UUIDs that participated in this session.
	 *
	 * @return The set of participant minion UUIDs.
	 */
	public Set<UUID> getParticipatingMinions() {
		return Collections.unmodifiableSet(this.participatingMinions);
	}

	/**
	 * Determines whether a block state represents a connectable structure whose shape or connections
	 * adapt dynamically to adjacent neighbors (fences, panes, walls, stairs, gates, wire).
	 *
	 * @param state The block state to evaluate.
	 * @return True if this block's connections or shapes dynamically adapt to neighbors.
	 */
	public static boolean isConnectableBlock(BlockState state) {
		if (state == null) {
			return false;
		}
		Block block = state.getBlock();
		return block instanceof HorizontalConnectingBlock
			|| block instanceof WallBlock
			|| block instanceof FenceGateBlock
			|| block instanceof StairsBlock
			|| block instanceof ConnectingBlock
			|| block instanceof RedstoneWireBlock
			|| block instanceof TripwireBlock;
	}

	/**
	 * Determines whether an existing world state matches the target blueprint state for a connectable block,
	 * accounting for dynamic neighbor-dependent connection properties (e.g. north/south/east/west flags,
	 * wall shapes, or stair corner shapes).
	 *
	 * @param worldState  The existing block state in the world.
	 * @param targetState The desired blueprint block state.
	 * @return True if the world block matches the target blueprint block type and required orientation.
	 */
	public static boolean isConnectableMatching(BlockState worldState, BlockState targetState) {
		if (worldState == null || targetState == null) {
			return false;
		}
		if (!worldState.isOf(targetState.getBlock())) {
			return false;
		}
		Block block = targetState.getBlock();
		if (block instanceof StairsBlock) {
			return worldState.get(StairsBlock.FACING) == targetState.get(StairsBlock.FACING)
				&& worldState.get(StairsBlock.HALF) == targetState.get(StairsBlock.HALF);
		}
		if (block instanceof FenceGateBlock) {
			return worldState.get(FenceGateBlock.FACING) == targetState.get(FenceGateBlock.FACING);
		}
		return true;
	}

	/**
	 * Determines whether an in-world block state legitimately satisfies the required blueprint block state.
	 * Accounts for:
	 * - Exact match
	 * - Still fluid sources
	 * - Dynamic connectables (fences, walls, panes, stairs)
	 * - Organic substrates (DIRT placed for GRASS_BLOCK or MYCELIUM as growing precursor; NETHERRACK for NYLIUM)
	 * - Dynamic runtime properties (leaves distance, composter level, chest double-linkage, wire power)
	 * - Compound & two-block structures (double doors, bed heads, piston heads, flower pots)
	 */
	public static boolean isSatisfiedBlockState(BlockState worldState, BlockState blueprintState) {
		if (worldState == null || blueprintState == null) {
			return false;
		}
		if (worldState.equals(blueprintState)) {
			return true;
		}
		if (blueprintState.isOf(Blocks.WATER)) {
			return worldState.isOf(Blocks.WATER) && worldState.getFluidState().isStill();
		}
		if (blueprintState.isOf(Blocks.LAVA)) {
			return worldState.isOf(Blocks.LAVA) && worldState.getFluidState().isStill();
		}
		if (isConnectableBlock(blueprintState)) {
			return isConnectableMatching(worldState, blueprintState);
		}

		// Living / Organic Substrates: DIRT satisfies GRASS_BLOCK and MYCELIUM (seeded precursor)
		if (blueprintState.isOf(Blocks.GRASS_BLOCK)) {
			return worldState.isOf(Blocks.GRASS_BLOCK) || worldState.isOf(Blocks.DIRT);
		}
		if (blueprintState.isOf(Blocks.MYCELIUM)) {
			return worldState.isOf(Blocks.MYCELIUM) || worldState.isOf(Blocks.DIRT);
		}
		if (blueprintState.isOf(Blocks.CRIMSON_NYLIUM)) {
			return worldState.isOf(Blocks.CRIMSON_NYLIUM) || worldState.isOf(Blocks.NETHERRACK);
		}
		if (blueprintState.isOf(Blocks.WARPED_NYLIUM)) {
			return worldState.isOf(Blocks.WARPED_NYLIUM) || worldState.isOf(Blocks.NETHERRACK);
		}

		// Leaves: dynamic distance from logs does not invalidate placement
		if (blueprintState.getBlock() instanceof LeavesBlock && worldState.isOf(blueprintState.getBlock())) {
			return true;
		}
		// Redstone wire: dynamic power level does not invalidate placement
		if (blueprintState.isOf(Blocks.REDSTONE_WIRE) && worldState.isOf(Blocks.REDSTONE_WIRE)) {
			return true;
		}
		// Composter: compost level does not invalidate placement
		if (blueprintState.isOf(Blocks.COMPOSTER) && worldState.isOf(Blocks.COMPOSTER)) {
			return true;
		}
		// Chests: dynamic left/right double linkage does not invalidate placement
		if (blueprintState.getBlock() instanceof ChestBlock && worldState.isOf(blueprintState.getBlock())) {
			if (blueprintState.contains(ChestBlock.FACING) && worldState.contains(ChestBlock.FACING)) {
				return worldState.get(ChestBlock.FACING) == blueprintState.get(ChestBlock.FACING);
			}
			return true;
		}
		// Cauldrons: liquid level does not invalidate placement
		if (blueprintState.getBlock() instanceof AbstractCauldronBlock && worldState.getBlock() instanceof AbstractCauldronBlock) {
			return true;
		}
		// Double-block structures: upper halves
		if (blueprintState.contains(Properties.DOUBLE_BLOCK_HALF) && worldState.isOf(blueprintState.getBlock())) {
			return worldState.get(Properties.DOUBLE_BLOCK_HALF) == blueprintState.get(Properties.DOUBLE_BLOCK_HALF);
		}
		// Beds: head half
		if (blueprintState.contains(BedBlock.PART) && worldState.isOf(blueprintState.getBlock())) {
			return worldState.get(BedBlock.PART) == blueprintState.get(BedBlock.PART);
		}
		// Piston head: satisfied if piston head or extended
		if (blueprintState.isOf(Blocks.PISTON_HEAD) && (worldState.isOf(Blocks.PISTON_HEAD) || worldState.isOf(Blocks.MOVING_PISTON))) {
			return true;
		}
		// Potted plants: satisfied if flower pot block is in place
		if (blueprintState.getBlock() instanceof FlowerPotBlock && worldState.getBlock() instanceof FlowerPotBlock) {
			return true;
		}

		return false;
	}
}