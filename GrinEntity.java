package com.grin;

import java.util.List;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class GrinEntity extends HostileEntity {
    private static final TrackedData<Boolean> ENRAGED =
            DataTracker.registerData(GrinEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final double BASE_SPEED = 0.30;
    private static final int GLANCE_TICKS = 10;      // eye contact shorter than this is ignored
    private static final int ENRAGE_TICKS = 80;      // 4 seconds of unbroken staring
    private static final int RAGE_DURATION = 500;    // 25 seconds
    private static final int IDLE_LIFETIME = 2400;   // 2 minutes if nobody ever sees it

    private boolean frozen = false;
    private int stareTicks = 0;
    private int rageTicks = 0;

    public GrinEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ENRAGED, false);
    }

    public boolean isEnraged() {
        return this.dataTracker.get(ENRAGED);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_SPEED)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 32.0f));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // ------------------------------------------------------------------ brain

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld sw)) return;

        if (isEnraged()) {
            tickRage(sw);
            return;
        }

        List<ServerPlayerEntity> watchers = GrinUtil.watchers(this, 64);
        boolean watched = !watchers.isEmpty();
        frozen = watched;

        if (watched) {
            stareTicks++;
            for (ServerPlayerEntity p : watchers) SanityManager.add(p, 0.08f);
            if (stareTicks >= ENRAGE_TICKS) {
                enrage(sw);
                return;
            }
        } else {
            if (stareTicks >= GLANCE_TICKS) {
                vanishAway(sw, true);
                return;
            }
            stareTicks = 0;
        }

        if (this.age > IDLE_LIFETIME) {
            vanishAway(sw, false);
            return;
        }
        haunt(sw);
    }

    private void haunt(ServerWorld sw) {
        PlayerEntity near = sw.getClosestPlayer(this, 32);
        if (near == null) return;

        if (this.age % 90 == 0) {
            sw.playSound(null, getX(), getY(), getZ(), GrinSounds.GRIN_AMBIENT, SoundCategory.HOSTILE, 1.4f, 1.0f);
        }
        if (this.age % 40 == 0 && distanceTo(near) < 16) {
            sw.playSound(null, near.getX(), near.getY(), near.getZ(), GrinSounds.HEARTBEAT,
                    SoundCategory.HOSTILE, 1.0f, 1.0f);
        }
        if (this.age % 120 == 60 && this.random.nextInt(2) == 0) {
            sw.playSound(null, getX(), getY(), getZ(), GrinSounds.GRIN_WHISPER, SoundCategory.HOSTILE,
                    1.0f, 0.9f + this.random.nextFloat() * 0.2f);
        }
        if (this.age % 40 == 0 && distanceTo(near) < 10) snuffTorch(sw);
    }

    private void snuffTorch(ServerWorld sw) {
        for (BlockPos pos : BlockPos.iterate(getBlockPos().add(-6, -3, -6), getBlockPos().add(6, 3, 6))) {
            BlockState s = sw.getBlockState(pos);
            if (s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH)) {
                sw.breakBlock(pos.toImmutable(), true);
                break;
            }
        }
    }

    private void darken(PlayerEntity p, int ticks) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, ticks, 0, false, false));
    }

    // ------------------------------------------------------------------ rage

    private void enrage(ServerWorld sw) {
        this.dataTracker.set(ENRAGED, true);
        rageTicks = 0;
        frozen = false;

        EntityAttributeInstance speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(BASE_SPEED * 2.0);
        EntityAttributeInstance dmg = getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(9.0);

        sw.playSound(null, getX(), getY(), getZ(), GrinSounds.GRIN_SCREAM, SoundCategory.HOSTILE, 3.0f, 1.0f);
        sw.playSound(null, getX(), getY(), getZ(), GrinSounds.STINGER, SoundCategory.HOSTILE, 2.0f, 0.9f);

        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.isSpectator() || p.distanceTo(this) > 48) continue;
            GrinUtil.send(p, new ScarePayload(0, 14));
            darken(p, 120);
            SanityManager.bump(p, 25f);
        }
        PlayerEntity near = sw.getClosestPlayer(this, 64);
        if (near != null) setTarget(near);
    }

    private void tickRage(ServerWorld sw) {
        rageTicks++;
        frozen = false;

        if (rageTicks >= RAGE_DURATION) {
            vanishAway(sw, false);
            return;
        }
        if (rageTicks % 10 == 0) {
            PlayerEntity near = sw.getClosestPlayer(this, 64);
            if (near != null) setTarget(near);
        }
        if (rageTicks % 20 == 0) {
            for (ServerPlayerEntity p : sw.getPlayers()) {
                if (p.isSpectator() || p.distanceTo(this) > 40) continue;
                darken(p, 100);
                SanityManager.add(p, 1.5f);
            }
        }
        if (rageTicks % 14 == 0) {
            PlayerEntity near = sw.getClosestPlayer(this, 40);
            if (near != null) {
                sw.playSound(null, near.getX(), near.getY(), near.getZ(), GrinSounds.HEARTBEAT,
                        SoundCategory.HOSTILE, 1.0f, 1.3f);
            }
        }
        if (rageTicks % 100 == 50) {
            sw.playSound(null, getX(), getY(), getZ(), GrinSounds.GRIN_SCREAM, SoundCategory.HOSTILE, 1.6f, 0.85f);
        }
        if (rageTicks % 3 == 0) {
            sw.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 1.2, getZ(), 3, 0.3, 0.6, 0.3, 0.01);
        }
    }

    private void vanishAway(ServerWorld sw, boolean glance) {
        sw.playSound(null, getX(), getY(), getZ(), glance ? GrinSounds.GRIN_LAUGH : GrinSounds.GRIN_VANISH,
                SoundCategory.HOSTILE, 1.6f, 1.0f);
        sw.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.2, getZ(), 40, 0.4, 0.8, 0.4, 0.05);

        PlayerEntity near = sw.getClosestPlayer(this, 48);
        if (glance && near instanceof ServerPlayerEntity sp) {
            SanityManager.bump(sp, 8f);
            if (this.random.nextFloat() < 0.30f) {
                final UUID id = sp.getUuid();
                final MinecraftServer server = sw.getServer();
                Scheduler.later(160 + this.random.nextInt(240), () -> {
                    ServerPlayerEntity again = server.getPlayerManager().getPlayer(id);
                    if (again != null && again.isAlive()) GrinMod.spawnGrin(again.getServerWorld(), again);
                });
            }
        }
        discard();
    }

    // ------------------------------------------------------------------ movement / combat

    @Override
    public void travel(Vec3d movementInput) {
        if (frozen && !isEnraged()) {
            super.travel(Vec3d.ZERO);
            return;
        }
        super.travel(movementInput);
    }

    @Override
    public boolean tryAttack(Entity target) {
        if (frozen && !isEnraged()) return false;
        boolean hit = super.tryAttack(target);
        if (hit && target instanceof ServerPlayerEntity p && getWorld() instanceof ServerWorld sw) {
            darken(p, isEnraged() ? 200 : 100);
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), GrinSounds.GRIN_SCREAM, SoundCategory.HOSTILE, 1.5f, 1.2f);
            GrinUtil.send(p, new ScarePayload(0, 6));
            SanityManager.bump(p, 15f);
        }
        return hit;
    }
}
