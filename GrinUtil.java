package com.grin;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

public final class GrinUtil {
    private GrinUtil() {}

    public static void send(ServerPlayerEntity p, CustomPayload payload) {
        if (ServerPlayNetworking.canSend(p, payload.getId())) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    /** True if the point is inside the viewer's cone of vision (no line-of-sight test). */
    public static boolean inView(PlayerEntity viewer, Vec3d point, double minDot) {
        Vec3d look = viewer.getRotationVec(1.0f).normalize();
        Vec3d to = point.subtract(viewer.getEyePos()).normalize();
        return look.dotProduct(to) > minDot;
    }

    public static boolean watchedBy(PlayerEntity p, Entity e) {
        return inView(p, e.getEyePos(), 0.6) && p.canSee(e);
    }

    public static List<ServerPlayerEntity> watchers(Entity e, double maxDist) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        if (!(e.getWorld() instanceof ServerWorld sw)) return out;
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.isSpectator() || p.distanceTo(e) > maxDist) continue;
            if (watchedBy(p, e)) out.add(p);
        }
        return out;
    }

    private static boolean passable(ServerWorld w, BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        return s.getCollisionShape(w, pos).isEmpty() && s.getFluidState().isEmpty();
    }

    /** Finds a spot with 3 free blocks of headroom and solid ground, searching a column. */
    public static BlockPos findStandable(ServerWorld w, int x, int y, int z, int up, int down) {
        for (int dy = up; dy >= -down; dy--) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            if (!passable(w, pos) || !passable(w, pos.up()) || !passable(w, pos.up(2))) continue;
            BlockPos below = pos.down();
            if (!w.getBlockState(below).isSolidBlock(w, below)) continue;
            return pos;
        }
        return null;
    }

    /** Picks a spot around the player, behind or in front of where they are facing. */
    public static BlockPos findSpot(ServerWorld w, ServerPlayerEntity p, boolean behind, double minD, double maxD) {
        Random r = w.random;
        for (int i = 0; i < 24; i++) {
            double yaw = Math.toRadians(p.getYaw() + (behind ? 180.0 : 0.0) + (r.nextDouble() - 0.5) * 100.0);
            double dist = minD + r.nextDouble() * (maxD - minD);
            int x = MathHelper.floor(p.getX() - Math.sin(yaw) * dist);
            int z = MathHelper.floor(p.getZ() + Math.cos(yaw) * dist);
            BlockPos spot = findStandable(w, x, p.getBlockY(), z, 8, 10);
            if (spot != null) return spot;
        }
        return null;
    }
}
