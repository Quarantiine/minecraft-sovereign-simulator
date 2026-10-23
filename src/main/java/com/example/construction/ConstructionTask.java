package com.example.construction;

import com.example.blueprint.BlueprintBlock;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;

/**
 * Represents an individual block placement task within an active {@link ConstructionSession}.
 * Tracks topological ordering, target world coordinates, assignment status,
 * and minion worker leases.
 */
public class ConstructionTask {

	/**
	 * State of the task in the construction lifecycle.
	 */
	public enum Status {
		PENDING,
		CLAIMED,
		COMPLETED
	}

	private final int id;
	private final BlueprintBlock blueprintBlock;
	private final BlockPos worldPos;
	private Status status = Status.PENDING;
	private UUID claimedBy = null;
	private long claimTick = 0L;
	private final java.util.Map<UUID, Long> deficiencyCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Constructs a new ConstructionTask.
	 *
	 * @param id             Sequential index of this task in topological blueprint order.
	 * @param blueprintBlock The blueprint block specification.
	 * @param anchorPos      The world anchor position of the construction session.
	 */
	public ConstructionTask(int id, BlueprintBlock blueprintBlock, BlockPos anchorPos) {
		this.id = id;
		this.blueprintBlock = Objects.requireNonNull(blueprintBlock, "blueprintBlock cannot be null");
		this.worldPos = blueprintBlock.toWorldPos(anchorPos).toImmutable();
	}

	public int getId() {
		return this.id;
	}

	public BlueprintBlock getBlueprintBlock() {
		return this.blueprintBlock;
	}

	public BlockPos getWorldPos() {
		return this.worldPos;
	}

	public Status getStatus() {
		return this.status;
	}

	public UUID getClaimedBy() {
		return this.claimedBy;
	}

	public long getClaimTick() {
		return this.claimTick;
	}

	public boolean isPending() {
		return this.status == Status.PENDING;
	}

	public boolean isClaimed() {
		return this.status == Status.CLAIMED;
	}

	public boolean isCompleted() {
		return this.status == Status.COMPLETED;
	}

	/**
	 * Claims this task for the specified minion worker.
	 *
	 * @param minionUuid  UUID of the minion claiming the task.
	 * @param currentTick Current world tick for timeout tracking.
	 * @return True if successfully claimed, false if already claimed or completed.
	 */
	public synchronized boolean claim(UUID minionUuid, long currentTick) {
		if (this.status != Status.PENDING && !Objects.equals(this.claimedBy, minionUuid)) {
			return false;
		}
		this.status = Status.CLAIMED;
		this.claimedBy = minionUuid;
		this.claimTick = currentTick;
		return true;
	}

	/**
	 * Releases this task back to the pending pool, allowing other minions to claim it.
	 */
	public synchronized void release() {
		if (this.status == Status.CLAIMED) {
			this.status = Status.PENDING;
			this.claimedBy = null;
			this.claimTick = 0L;
		}
	}

	public boolean hasDeficiencyCooldown(UUID minionUuid, long currentTick) {
		if (minionUuid == null) return false;
		Long until = this.deficiencyCooldowns.get(minionUuid);
		return until != null && currentTick < until;
	}

	public void setDeficiencyCooldown(UUID minionUuid, long untilTick) {
		if (minionUuid != null) {
			this.deficiencyCooldowns.put(minionUuid, untilTick);
		}
	}

	public void clearDeficiencyCooldown(UUID minionUuid) {
		if (minionUuid != null) {
			this.deficiencyCooldowns.remove(minionUuid);
		}
	}

	/**
	 * Releases this task only if it is still claimed by the given minion. Prevents a minion whose
	 * task was reassigned (stolen by an on-site peer) from un-claiming the new owner's lease.
	 *
	 * @param minionUuid The minion attempting to release.
	 * @return True if the task was released.
	 */
	public synchronized boolean releaseIfOwnedBy(UUID minionUuid) {
		if (this.status == Status.CLAIMED && Objects.equals(this.claimedBy, minionUuid)) {
			release();
			return true;
		}
		return false;
	}

	/**
	 * Reassigns a claimed task from an absent claimant to an on-site peer minion.
	 * The original claimant detects the lost lease on its next tick and forgets the task.
	 *
	 * @param newClaimant UUID of the minion taking over.
	 * @param currentTick Current world tick (resets the lease timer).
	 * @return True if the lease was transferred.
	 */
	public synchronized boolean reassign(UUID newClaimant, long currentTick) {
		if (this.status != Status.CLAIMED || newClaimant == null || Objects.equals(this.claimedBy, newClaimant)) {
			return false;
		}
		this.claimedBy = newClaimant;
		this.claimTick = currentTick;
		return true;
	}

	/**
	 * @param minionUuid Minion to check.
	 * @return True if the task is currently claimed by the given minion.
	 */
	public boolean isClaimedBy(UUID minionUuid) {
		return this.status == Status.CLAIMED && Objects.equals(this.claimedBy, minionUuid);
	}

	/**
	 * Marks this task as permanently completed.
	 */
	public synchronized void complete() {
		this.status = Status.COMPLETED;
		this.claimedBy = null;
		this.claimTick = 0L;
	}

	@Override
	public String toString() {
		return "ConstructionTask{" +
			"id=" + id +
			", block=" + (blueprintBlock.state() != null && blueprintBlock.state().getBlock() != null ? blueprintBlock.state().getBlock().getName().getString() : "block") +
			", pos=" + worldPos.toShortString() +
			", status=" + status +
			(claimedBy != null ? ", claimedBy=" + claimedBy : "") +
			'}';
	}
}
