package com.grin;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class GrinSounds {
    public static final SoundEvent GRIN_AMBIENT = register("grin.ambient");
    public static final SoundEvent GRIN_WHISPER = register("grin.whisper");
    public static final SoundEvent GRIN_SCREAM = register("grin.scream");
    public static final SoundEvent GRIN_LAUGH = register("grin.laugh");
    public static final SoundEvent GRIN_VANISH = register("grin.vanish");
    public static final SoundEvent HEARTBEAT = register("heartbeat");
    public static final SoundEvent STINGER = register("stinger");
    public static final SoundEvent HOLLOW_DRONE = register("hollow.drone");
    public static final SoundEvent HOLLOW_STATIC = register("hollow.static");
    public static final SoundEvent HOLLOW_BREATH = register("hollow.breath");
    public static final SoundEvent HOLLOW_SMILE = register("hollow.smile");

    private GrinSounds() {}

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(GrinMod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /** Forces the class to load so every sound gets registered. */
    public static void init() {}
}
