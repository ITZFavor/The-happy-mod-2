package com.grin;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SanityPayload(float value) implements CustomPayload {
    public static final CustomPayload.Id<SanityPayload> ID =
            new CustomPayload.Id<>(Identifier.of(GrinMod.MOD_ID, "sanity"));
    public static final PacketCodec<RegistryByteBuf, SanityPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.FLOAT, SanityPayload::value, SanityPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
