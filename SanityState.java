package com.grin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** Saves every player's sanity value with the world. */
public class SanityState extends PersistentState {
    public final Map<UUID, Float> sanity = new HashMap<>();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound players = new NbtCompound();
        sanity.forEach((id, v) -> players.putFloat(id.toString(), v));
        nbt.put("players", players);
        return nbt;
    }

    public static SanityState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        SanityState state = new SanityState();
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            try {
                state.sanity.put(UUID.fromString(key), players.getFloat(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }

    private static final PersistentState.Type<SanityState> TYPE =
            new PersistentState.Type<>(SanityState::new, SanityState::fromNbt, null);

    public static SanityState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, GrinMod.MOD_ID + "_sanity");
    }
}
