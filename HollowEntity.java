package com.grin;

import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/** The rare one. It has no goals and never walks. */
public class HollowEntity extends HostileEntity {
    private static final int HOP_INTERVAL = 20;
    private static final int LIFETIME = 3600;
    private static final double CLOSE = 6.0;
    private static final int SMILE_TICKS = 100;
    private static final int LOOKAWAY_TICKS = 12;

    private int hopTimer = 0;
    private int faceTicks = 0;
    private int awayTicks = 0;
    private int doneTicks = 0;
    private int lonelyTicks = 0;
    private boolean done = false;

    public HollowEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.setInvulnerable(true);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 90.0);
    }

    @Override
    protected void initGoals() {
        // intentionally empty
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld sw)) return;

        if (done) {
            if (++doneTicks > 25) poof(sw);
            return;
        }
        if (this.age > LIFETIME) {
            poof(sw);
            return;
        }

        ServerPlayerEntity target = null;
        PlayerEntity near = sw.getClosestPlayer(this, 90);
        if (near instanceof ServerPlayerEntity sp && !sp.isSpectator()) target = sp;
        if (target == null) {
            if (++lonelyTicks > 200) poof(sw);
            return;
        }
        lonelyTicks = 0;

        double dist = distanceTo(target);
        List<ServerPlayerEntity> watchers = GrinUtil.watchers(this, 90);
        boolean watched = !watchers.isEmpty();

        presence(sw, target, dist, watched);

        if (dist > CLOSE) {
            faceTicks = 0;
            awayTicks = 0;
            if (watched) {
                hopTimer = Math.min(hopTimer, 10);
            } else if (++hopTimer >= HOP_INTERVAL) {
                hopTimer = 0;
                hop(sw, target, dist);
            }
        } else if (watched) {
            awayTicks = 0;
            faceTicks++;
            if (faceTicks % 25 == 0) {
                sw.playSound(null, target.getX(), target.getY(), target.getZ(), GrinSounds.HEARTBEAT,
                        SoundCategory.HOSTILE, 1.2f, 1.3f);
            }
            if (faceTicks == 40) {
                sw.playSound(null, getX(), getY(), getZ(), GrinSounds.GRIN_WHISPER, SoundCategory.HOSTILE, 1.2f, 0.6f);
            }
            if (faceTicks >= SMILE_TICKS) smile(sw, target);
        } else if (++awayTicks >= LOOKAWAY_TICKS) {
            lunge(sw, target);
        }
    }

    private void presence(ServerWorld sw, ServerPlayerEntity target, double dist, boolean watched) {
        if (dist < 45) SanityManager.add(target, watched ? 0.15f : 0.06f);

        if (this.age % 80 == 0 && dist < 60) {
            sw.playSound(null, target.getX(), target.getY(), target.getZ(), GrinSounds.HOLLOW_DRONE,
                    SoundCategory.HOSTILE, 1.0f, 1.0f);
        }
        int beat = dist < 10 ? 14 : (dist < 20 ? 22 : 36);
        if (this.age % beat == 0 && dist < 45) {
            sw.playSound(null, target.getX(), target.getY(), target.getZ(), GrinSounds.HEARTBEAT,
                    SoundCategory.HOSTILE, 0.9f, dist < 10 ? 1.25f : 1.0f);
        }
        if (this.age % 70 == 0 && dist < 14) {
            sw.playSound(null, getX(), getY(), getZ(), GrinSounds.HOLLOW_BREATH, SoundCategory.HOSTILE, 1.3f, 0.9f);
        }
        if (dist < 16 && this.age % 40 == 0) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0, false, false));
        }
    }

    private void hop(ServerWorld sw, ServerPlayerEntity target, double dist) {
        double dx = getX() - target.getX();
        double dz = getZ() - target.getZ();
        double base = Math.atan2(dz, dx);
        double newDist = Math.max(4.5, dist - (4.0 + this.random.nextDouble() * 3.0));

        for (int i = 0; i < 8; i++) {
            double ang = base + (this.random.nextDouble() - 0.5) * 0.7;
            int x = MathHelper.floor(target.getX() + Math.cos(ang) * newDist);
            int z = MathHelper.floor(target.getZ() + Math.sin(ang) * newDist);

            BlockPos spot = GrinUtil.findStandable(sw, x, target.getBlockY(), z, 6, 8);
            if (spot == null) continue;
            if (sw.getLightLevel(LightType.BLOCK, spot) >= 8) continue;

            Vec3d dest = new Vec3d(spot.getX() + 0.5, spot.getY() + 1.5, spot.getZ() + 0.5);
            boolean seen = false;
            for (ServerPlayerEntity p : sw.getPlayers()) {
                if (!p.isSpectator() && GrinUtil.inView(p, dest, 0.45)) {
                    seen = true;
                    break;
                }
            }
            if (seen) continue;

            sw.playSound(null, getX(), getY(), getZ(), GrinSounds.HOLLOW_STATIC, SoundCategory.HOSTILE, 1.0f, 1.0f);
            sw.spawnParticles(ParticleTypes.ASH, getX(), getY() + 1.5, getZ(), 20, 0.3, 0.8, 0.3, 0.02);
            refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, getYaw(), 0.0f);
            sw.playSound(null, getX(), getY(), getZ(), GrinSounds.HOLLOW_STATIC, SoundCategory.HOSTILE, 1.0f, 0.8f);
            return;
        }
    }

    private void smile(ServerWorld sw, ServerPlayerEntity target) {
        done = true;
        doneTicks = 0;
        sw.playSound(null, target.getX(), target.getY(), target.getZ(), GrinSounds.HOLLOW_SMILE,
                SoundCategory.HOSTILE, 3.0f, 1.0f);
        sw.playSound(null, target.getX(), target.getY(), target.getZ(), GrinSounds.STINGER,
                SoundCategory.HOSTILE, 2.5f, 0.7f);
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.isSpectator() || p.distanceTo(this) > 24) continue;
            GrinUtil.send(p, new ScarePayload(1, 30));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 300, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0));
            SanityManager.set(p, 100f);
        }
    }

    private void lunge(ServerWorld sw, ServerPlayerEntity target) {
        done = true;
        doneTicks = 0;
        Vec3d look = target.getRotationVec(1.0f);
        refreshPositionAndAngles(target.getX() + look.x * 1.3, target.getY(), target.getZ() + look.z * 1.3,
                getYaw(), 0.0f);
        sw.playSound(null, target.getX(), target.getY(), target.getZ(), GrinSounds.STINGER,
                SoundCategory.HOSTILE, 3.0f, 1.0f);
        sw.playSound(null, target.getX(), target.getY(), target.getZ(), GrinSounds.HOLLOW_SMILE,
                SoundCategory.HOSTILE, 2.0f, 1.3f);
        GrinUtil.send(target, new ScarePayload(1, 24));
        target.damage(getWorld().getDamageSources().mobAttack(this), 12.0f);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 200, 0));
        SanityManager.set(target, 100f);
    }

    private void poof(ServerWorld sw) {
        sw.playSound(null, getX(), getY(), getZ(), GrinSounds.GRIN_VANISH, SoundCategory.HOSTILE, 1.4f, 0.7f);
        sw.spawnParticles(ParticleTypes.ASH, getX(), getY() + 1.5, getZ(), 40, 0.4, 1.0, 0.4, 0.03);
        discard();
    }
}
