package com.grin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrinMod implements ModInitializer {
    public static final String MOD_ID = "grinmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final EntityType<GrinEntity> GRIN = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "grin"),
            EntityType.Builder.create(GrinEntity::new, SpawnGroup.MONSTER)
                    .dimensions(1.0f, 2.4f)
                    .maxTrackingRange(10)
                    .build("grin"));

    public static final EntityType<HollowEntity> HOLLOW = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "hollow"),
            EntityType.Builder.create(HollowEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.9f, 3.0f)
                    .maxTrackingRange(16)
                    .build("hollow"));

    public static final Item GRIN_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "grin_spawn_egg"),
            new SpawnEggItem(GRIN, 0x000000, 0xFFFFFF, new Item.Settings()));

    public static final Item HOLLOW_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "hollow_spawn_egg"),
            new SpawnEggItem(HOLLOW, 0x000000, 0x8B0000, new Item.Settings()));

    @Override
    public void onInitialize() {
        GrinSounds.init();

        PayloadTypeRegistry.playS2C().register(SanityPayload.ID, SanityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScarePayload.ID, ScarePayload.CODEC);

        FabricDefaultAttributeRegistry.register(GRIN, GrinEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(HOLLOW, HollowEntity.createAttributes());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(GRIN_SPAWN_EGG);
            entries.add(HOLLOW_SPAWN_EGG);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Scheduler.tick();
            SanityManager.tick(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Scheduler.clear());

        // Dying calms you down a little.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) SanityManager.set(newPlayer, 25f);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("grin")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("sanity")
                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> {
                                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                            int v = IntegerArgumentType.getInteger(ctx, "value");
                                            SanityManager.set(p, v);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Sanity bar set to " + v), false);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("spawn")
                                .then(CommandManager.literal("grin").executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    boolean ok = spawnGrin(p.getServerWorld(), p);
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal(ok ? "Spawned." : "No room found, try again."), false);
                                    return ok ? 1 : 0;
                                }))
                                .then(CommandManager.literal("hollow").executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    boolean ok = spawnHollow(p.getServerWorld(), p);
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal(ok ? "Spawned." : "No room found, try again."), false);
                                    return ok ? 1 : 0;
                                })))));
    }

    /** Puts a Grin 16-26 blocks behind the player. */
    public static boolean spawnGrin(ServerWorld w, ServerPlayerEntity p) {
        BlockPos spot = GrinUtil.findSpot(w, p, true, 16, 26);
        if (spot == null) return false;
        GrinEntity g = GRIN.create(w);
        if (g == null) return false;
        g.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, w.random.nextFloat() * 360f, 0f);
        w.spawnEntity(g);
        return true;
    }

    /** Puts a Hollow far out in front of the player. */
    public static boolean spawnHollow(ServerWorld w, ServerPlayerEntity p) {
        BlockPos spot = GrinUtil.findSpot(w, p, false, 28, 38);
        if (spot == null) return false;
        HollowEntity h = HOLLOW.create(w);
        if (h == null) return false;
        h.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
        w.spawnEntity(h);
        return true;
    }
}
