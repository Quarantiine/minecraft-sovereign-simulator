package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import com.example.targeting.MinionTargetFilterManager;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;

/**
 * Dedicated hostile target acquisition goal for minion thralls.
 * Active for {@link MinionRole#WARRIOR} units (and Auto-role minions currently adapted to
 * Warrior/Sentinel), scanning a 24-block bounding box for hostile mobs threatening the master
 * or perimeter. Which hostile mob types may be engaged on sight is controlled per commander
 * through {@link MinionTargetFilterManager} (Command Hub "Targets" modal).
 * <p>
 * <b>Sentinel Wall-Penetrating Sentry Senses</b>:
 * Units matching {@link MinionRole#SENTINEL} possess specialized sentry hearing and vibration detection,
 * allowing them to penetrate solid walls, ceilings, and obstacles within their 24-block proximity.
 * When a hidden threat is detected behind a wall, the Sentinel emits a sculk/resonance ping and alerts
 * nearby allied combatants to intercept.
 * Warriors and other roles require standard direct line of sight.
 */
public class MinionActiveTargetGoal extends ActiveTargetGoal<LivingEntity> {

	private final MinionEntity minion;
	private final double scanDistance;
	private final TargetPredicate warriorPredicate;
	private final TargetPredicate sentinelSentryPredicate;
	private int warriorUnseenTicks = 0;

	public MinionActiveTargetGoal(MinionEntity minion, double scanDistance) {
		super(minion, LivingEntity.class, 10, false, false, target -> isEngageable(minion, target));
		this.minion = minion;
		this.scanDistance = scanDistance;

		// Standard vision for Warriors: enforces line-of-sight visibility check
		this.warriorPredicate = TargetPredicate.createAttackable()
			.setBaseMaxDistance(scanDistance)
			.setPredicate(target -> isEngageable(minion, target));

		// Wall-penetrating sentry sense for Sentinels: ignores obstruction visibility to detect hostiles through walls
		this.sentinelSentryPredicate = TargetPredicate.createAttackable()
			.setBaseMaxDistance(scanDistance)
			.ignoreVisibility()
			.setPredicate(target -> isEngageable(minion, target));
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
		return minion.isTargetAllowed(target.getType());
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		if (this.minion.getCombatPolicy() == com.example.entity.custom.MinionCombatPolicy.PASSIVE
				|| this.minion.getCombatPolicy() == com.example.entity.custom.MinionCombatPolicy.DEFENSIVE) {
			return false;
		}
		if (this.minion.matchesRole(MinionRole.BUILDER)) {
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
		this.warriorUnseenTicks = 0;
		return super.canStart();
	}

	@Override
	protected void findClosestTarget() {
		boolean isSentinel = this.minion.matchesRole(MinionRole.SENTINEL);
		this.targetPredicate = isSentinel ? this.sentinelSentryPredicate : this.warriorPredicate;
		super.findClosestTarget();
	}

	@Override
	public void start() {
		this.warriorUnseenTicks = 0;
		super.start();
		if (this.targetEntity != null && this.minion.matchesRole(MinionRole.SENTINEL)
				&& this.minion.getWorld() instanceof ServerWorld serverWorld) {
			boolean behindWall = !this.minion.getVisibilityCache().canSee(this.targetEntity);
			if (behindWall) {
				triggerSentinelSentrySenseAlert(serverWorld, this.targetEntity);
			}
		}
	}

	@Override
	public boolean shouldContinue() {
		if (this.minion.matchesRole(MinionRole.SENTINEL) && this.minion.hasNearbyAlliesNeedingHealing()) {
			return false;
		}
		LivingEntity target = this.minion.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		if (target.getWorld() != null && !target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
			return false;
		}
		if (!this.minion.matchesRole(MinionRole.SENTINEL)) {
			// Warriors require direct line of sight; drop target if hidden behind walls for > 60 ticks (3s)
			if (this.minion.getVisibilityCache().canSee(target)) {
				this.warriorUnseenTicks = 0;
			} else if (++this.warriorUnseenTicks > 60) {
				return false;
			}
		}
		return super.shouldContinue();
	}

	@Override
	public void tick() {
		super.tick();
		LivingEntity target = this.minion.getTarget();
		if (target != null && this.minion.matchesRole(MinionRole.SENTINEL)
				&& this.minion.age % 20 == 0 && this.minion.getWorld() instanceof ServerWorld serverWorld) {
			boolean behindWall = !this.minion.getVisibilityCache().canSee(target);
			if (behindWall) {
				// Subtle echolocation vibration feedback while closing in through obstacles
				serverWorld.spawnParticles(
					ParticleTypes.SCULK_SOUL,
					this.minion.getX(),
					this.minion.getEyeY() + 0.1D,
					this.minion.getZ(),
					2,
					0.15D,
					0.1D,
					0.15D,
					0.01D
				);
				this.minion.getLookControl().lookAt(target, 30.0F, 30.0F);
			}
		}
	}

	@Override
	public void stop() {
		super.stop();
		this.warriorUnseenTicks = 0;
	}

	private void triggerSentinelSentrySenseAlert(ServerWorld serverWorld, LivingEntity target) {
		// Echolocation / sentry vibration particles at head
		serverWorld.spawnParticles(
			ParticleTypes.SCULK_SOUL,
			this.minion.getX(),
			this.minion.getEyeY() + 0.15D,
			this.minion.getZ(),
			6,
			0.2D,
			0.15D,
			0.2D,
			0.02D
		);
		serverWorld.spawnParticles(
			ParticleTypes.ENCHANT,
			this.minion.getX(),
			this.minion.getEyeY() + 0.1D,
			this.minion.getZ(),
			8,
			0.25D,
			0.2D,
			0.25D,
			0.05D
		);

		// Resonant sculk click and amethyst chime ping
		serverWorld.playSound(
			null,
			this.minion.getX(),
			this.minion.getEyeY(),
			this.minion.getZ(),
			SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
			SoundCategory.NEUTRAL,
			0.85F,
			1.4F
		);
		serverWorld.playSound(
			null,
			this.minion.getX(),
			this.minion.getEyeY(),
			this.minion.getZ(),
			SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
			SoundCategory.NEUTRAL,
			0.65F,
			1.8F
		);

		// Snap gaze in direction of concealed vibration source
		this.minion.getLookControl().lookAt(target, 30.0F, 30.0F);

		// Alert nearby allied combat minions within 16m who lack wall-penetrating hearing
		if (this.minion.getOwnerUuid() != null) {
			List<MinionEntity> nearbyAllies = serverWorld.getEntitiesByClass(
				MinionEntity.class,
				this.minion.getBoundingBox().expand(16.0D),
				ally -> ally.isAlive() && ally != this.minion
					&& java.util.Objects.equals(ally.getOwnerUuid(), this.minion.getOwnerUuid())
					&& !ally.matchesRole(MinionRole.BUILDER)
					&& !ally.isActivelyBuilding()
			);
			for (MinionEntity ally : nearbyAllies) {
				if (ally.getTarget() == null || !ally.getTarget().isAlive()) {
					ally.setTarget(target);
					ally.setSitting(false);
				}
			}
		}
	}

	@Override
	protected Box getSearchBox(double distance) {
		double yExpand = this.minion.matchesRole(MinionRole.SENTINEL) ? 12.0D : 4.0D;
		return this.mob.getBoundingBox().expand(this.scanDistance, yExpand, this.scanDistance);
	}
}
