package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import com.example.targeting.MinionTargetFilterManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.math.Box;

/**
 * Dedicated hostile target acquisition goal for minion thralls.
 * Active for {@link MinionRole#WARRIOR} units (and Auto-role minions currently adapted to
 * Warrior/Sentinel), scanning a 24-block bounding box for hostile mobs threatening the master
 * or perimeter. Which hostile mob types may be engaged on sight is controlled per commander
 * through {@link MinionTargetFilterManager} (Command Hub "Targets" modal).
 */
public class MinionActiveTargetGoal extends ActiveTargetGoal<LivingEntity> {

	private final MinionEntity minion;
	private final double scanDistance;

	public MinionActiveTargetGoal(MinionEntity minion, double scanDistance) {
		super(minion, LivingEntity.class, 10, true, false, target -> isEngageable(minion, target));
		this.minion = minion;
		this.scanDistance = scanDistance;
	}

	/**
	 * Determines whether a candidate may be acquired as an on-sight target:
	 * it must be a hostile {@link Monster} (never another minion) whose entity type
	 * is allowed by the owner's target filter.
	 *
	 * @param minion The scanning minion.
	 * @param target The candidate target.
	 * @return True if the minion may attack the candidate on sight.
	 */
	public static boolean isEngageable(MinionEntity minion, LivingEntity target) {
		if (target == null || target instanceof MinionEntity || !(target instanceof Monster)) {
			return false;
		}
		return MinionTargetFilterManager.getInstance().isAllowed(minion.getOwnerUuid(), target.getType());
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		// Warriors actively scan and engage hostiles across the tactical zone.
		// Sentinels fight like warriors ONLY if no minions or iron golems near them need zero healing.
		boolean isWarrior = this.minion.matchesRole(MinionRole.WARRIOR);
		boolean isSentinelWarriorMode = this.minion.matchesRole(MinionRole.SENTINEL)
				&& !this.minion.hasNearbyAlliesNeedingHealing();
		if (!isWarrior && !isSentinelWarriorMode) {
			return false;
		}
		if (this.minion.hasLeader()) {
			MinionEntity leader = this.minion.resolveLeader();
			if (leader == null || !leader.isAlive() || this.minion.squaredDistanceTo(leader) > 256.0D) {
				return false;
			}
		}
		return super.canStart();
	}

	@Override
	public boolean shouldContinue() {
		if (this.minion.matchesRole(MinionRole.SENTINEL) && this.minion.hasNearbyAlliesNeedingHealing()) {
			return false;
		}
		return super.shouldContinue();
	}

	@Override
	protected Box getSearchBox(double distance) {
		return this.mob.getBoundingBox().expand(this.scanDistance, 4.0D, this.scanDistance);
	}
}
