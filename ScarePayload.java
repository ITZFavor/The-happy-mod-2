package com.grin;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ScarePayload(int kind, int ticks) implements CustomPayload {
    public static final CustomPayload.Id<ScarePayload> ID =
            new CustomPayload.Id<>(Identifier.of(GrinMod.MOD_ID, "scare"));
    public static final PacketCodec<RegistryByteBuf, ScarePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.INTEGER, ScarePayload::kind,
                    PacketCodecs.INTEGER, ScarePayload::ticks, ScarePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
