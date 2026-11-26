package com.grin;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class GrinClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(GrinMod.GRIN, GrinRenderer::new);
        EntityRendererRegistry.register(GrinMod.HOLLOW, HollowRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(SanityPayload.ID,
                (payload, context) -> SanityHud.setTarget(payload.value()));
        ClientPlayNetworking.registerGlobalReceiver(ScarePayload.ID,
                (payload, context) -> SanityHud.startScare(payload.kind(), payload.ticks()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> SanityHud.tick());
        HudRenderCallback.EVENT.register(SanityHud::render);
    }
}
