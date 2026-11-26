package com.grin;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * The bar above the hotbar. It fills as the player's grip slips (0 = calm, 100 = gone).
 * Every 30 seconds a roll is made, and the fuller the bar, the likelier something shows up.
 */
public final class SanityManager {
    // ---- tuning knobs (per second unless noted) ----
    public static final float MAX_SPAWN_CHANCE = 0.60f;  // chance at a full bar, rolled every 30 s
    private static final float DARK_GAIN = 0.5f;          // light level 0-3
    private static final float DIM_GAIN = 0.2f;           // light level 4-7
    private static final float CAVE_GAIN = 0.1f;          // no sky overhead
    private static final float BRIGHT_LOSS = 0.5f;        // light level 12+
    private static final float COMPANY_LOSS = 0.5f;       // another player within 8 blocks
    private static final float SLEEP_LOSS = 4.0f;

    private static final String[] LINES = {
            "behind you", "don't turn around", "i can see you", "still awake?", "smile",
            "look at me", "you're not alone", "it's close", "keep looking"
    };

    private SanityManager() {}

    public static float get(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        if (server == null) return 0f;
        return SanityState.get(server).sanity.getOrDefault(p.getUuid(), 0f);
    }

    private static void store(ServerPlayerEntity p, float v) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        SanityState state = SanityState.get(server);
        state.sanity.put(p.getUuid(), MathHelper.clamp(v, 0f, 100f));
        state.markDirty();
    }

    /** Set and sync to the client right away. */
    public static void set(ServerPlayerEntity p, float v) {
        store(p, v);
        sync(p);
    }

    /** Change quietly; the client hears about it within a second. */
    public static void add(ServerPlayerEntity p, float d) {
        store(p, get(p) + d);
    }

    /** Change and sync right away. */
    public static void bump(ServerPlayerEntity p, float d) {
        set(p, get(p) + d);
    }

    public static void sync(ServerPlayerEntity p) {
        GrinUtil.send(p, new SanityPayload(get(p)));
    }

    public static void tick(MinecraftServer server) {
        int t = server.getTicks();
        if (t % 20 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            secondTick(p, t);
        }
    }

    private static void secondTick(ServerPlayerEntity p, int t) {
        ServerWorld w = p.getServerWorld();
        BlockPos pos = p.getBlockPos();
        float d = 0f;

        if (p.isSleeping()) {
            d -= SLEEP_LOSS;
        } else {
            int light = w.getLightLevel(pos);
            if (light <= 3) d += DARK_GAIN;
            else if (light <= 7) d += DIM_GAIN;
            else if (light >= 12) d -= BRIGHT_LOSS;

            if (!w.isSkyVisible(pos)) d += CAVE_GAIN;
            if (p.getHealth() <= 6.0f) d += 0.3f;

            for (ServerPlayerEntity o : w.getPlayers()) {
                if (o != p && !o.isSpectator() && o.squaredDistanceTo(p) < 64.0) {
                    d -= COMPANY_LOSS;
                    break;
                }
            }
        }

        float s = MathHelper.clamp(get(p) + d, 0f, 100f);
        set(p, s);

        hallucinate(w, p, s);
        if (t % 600 == 0) rollSpawn(w, p, s);
    }

    // ------------------------------------------------------------------ spawn roll

    private static void rollSpawn(ServerWorld w, ServerPlayerEntity p, float s) {
        if (w.getRegistryKey() != World.OVERWORLD) return;
        float chance = (s / 100f) * MAX_SPAWN_CHANCE;
        if (w.random.nextFloat() >= chance) return;

        Box box = p.getBoundingBox().expand(128);
        if (!w.getEntitiesByType(GrinMod.GRIN, box, e -> true).isEmpty()) return;
        if (!w.getEntitiesByType(GrinMod.HOLLOW, box, e -> true).isEmpty()) return;

        boolean rare = s >= 60f && w.random.nextFloat() < 0.12f;
        if (rare) {
            if (!GrinMod.spawnHollow(w, p)) GrinMod.spawnGrin(w, p);
        } else {
            GrinMod.spawnGrin(w, p);
        }
    }

    // ------------------------------------------------------------------ in-your-head effects

    private static void hallucinate(ServerWorld w, ServerPlayerEntity p, float s) {
        if (s < 30f) return;
        Random r = w.random;
        float k = (s - 30f) / 70f;

        if (r.nextFloat() < 0.006f + 0.05f * k) phantomSound(w, p, r, s);
        if (s >= 60f && r.nextFloat() < 0.004f + 0.02f * k) whisper(w, p, r, s);
        if (s >= 70f && r.nextFloat() < 0.008f) {
            GrinUtil.send(p, new ScarePayload(r.nextBoolean() ? 0 : 1, 3));
        }
        if (s >= 85f && r.nextFloat() < 0.03f) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0, false, false));
        }
        if (s >= 75f) {
            w.playSound(null, p.getX(), p.getY(), p.getZ(), GrinSounds.HEARTBEAT,
                    SoundCategory.AMBIENT, 0.6f + 0.4f * k, 1.0f);
        }
    }

    private static void whisper(ServerWorld w, ServerPlayerEntity p, Random r, float s) {
        String line = LINES[r.nextInt(LINES.length)];
        Text text = Text.literal(line).formatted(Formatting.DARK_RED, s > 90f ? Formatting.OBFUSCATED : Formatting.ITALIC);
        p.sendMessage(text, true);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), GrinSounds.GRIN_WHISPER,
                SoundCategory.AMBIENT, 0.8f, 0.8f + r.nextFloat() * 0.4f);
    }

    private static void phantomSound(ServerWorld w, ServerPlayerEntity p, Random r, float s) {
        double yaw = Math.toRadians(p.getYaw() + 180.0 + (r.nextDouble() - 0.5) * 120.0);
        double dist = 4.0 + r.nextDouble() * 8.0;
        final double x = p.getX() - Math.sin(yaw) * dist;
        final double z = p.getZ() + Math.cos(yaw) * dist;
        final double y = p.getY();
        final double nx = p.getX() - Math.sin(yaw) * 2.5;
        final double nz = p.getZ() + Math.cos(yaw) * 2.5;

        switch (r.nextInt(s >= 80f ? 8 : 6)) {
            case 0 -> footsteps(w, p, x, y, z);
            case 1 -> {
                w.playSound(null, x, y, z, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.HOSTILE, 1.0f, 0.9f);
                Scheduler.later(30, () -> w.playSound(null, x, y, z, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE,
                        SoundCategory.HOSTILE, 1.0f, 0.9f));
            }
            case 2 -> {
                w.playSound(null, x, y, z, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.HOSTILE, 1.0f, 0.8f);
                Scheduler.later(40, () -> w.playSound(null, x, y, z, SoundEvents.BLOCK_CHEST_CLOSE,
                        SoundCategory.HOSTILE, 1.0f, 0.8f));
            }
            case 3 -> w.playSound(null, x, y, z, SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 1.0f, 0.5f);
            case 4 -> w.playSound(null, x, y, z, GrinSounds.GRIN_WHISPER, SoundCategory.HOSTILE, 1.0f, 0.7f);
            case 5 -> w.playSound(null, nx, y, nz, GrinSounds.HOLLOW_BREATH, SoundCategory.HOSTILE, 1.2f, 1.0f);
            case 6 -> w.playSound(null, nx, y, nz, SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.0f, 1.0f);
            default -> w.playSound(null, x, y, z, GrinSounds.GRIN_LAUGH, SoundCategory.HOSTILE, 1.2f, 0.8f);
        }
    }

    private static void footsteps(ServerWorld w, ServerPlayerEntity p, double x, double y, double z) {
        final SoundEvent step = w.isSkyVisible(p.getBlockPos()) ? SoundEvents.BLOCK_GRASS_STEP : SoundEvents.BLOCK_STONE_STEP;
        final double px = p.getX();
        final double pz = p.getZ();
        for (int i = 0; i < 5; i++) {
            final double f = i / 6.0;
            final double sx = x + (px - x) * f;
            final double sz = z + (pz - z) * f;
            Scheduler.later(i * 9, () -> w.playSound(null, sx, y, sz, step, SoundCategory.HOSTILE, 1.0f, 0.85f));
        }
    }
}
