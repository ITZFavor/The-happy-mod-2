package com.grin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public class GrinRenderer extends EntityRenderer<GrinEntity> {
    private static final Identifier TEXTURE = Identifier.of(GrinMod.MOD_ID, "textures/entity/grin.png");
    private static final float SIZE = 2.6f;

    public GrinRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(GrinEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        boolean rage = entity.isEnraged();
        float t = entity.age + tickDelta;
        float size = rage ? SIZE * 1.3f : SIZE;
        float jx = rage ? (float) Math.sin(t * 47.0) * 0.07f : 0.0f;
        float jy = rage ? (float) Math.cos(t * 61.0) * 0.05f : 0.0f;
        int gb = rage ? 60 : 255;

        matrices.push();
        matrices.multiply(this.dispatcher.getRotation());
        matrices.translate(jx, size / 2.0f - 0.1f + jy, 0.0f);

        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        float h = size / 2.0f;

        vertex(vc, m, matrices, -h, -h, 0f, 1f, full, gb);
        vertex(vc, m, matrices, h, -h, 1f, 1f, full, gb);
        vertex(vc, m, matrices, h, h, 1f, 0f, full, gb);
        vertex(vc, m, matrices, -h, h, 0f, 0f, full, gb);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, MatrixStack matrices,
                               float x, float y, float u, float v, int light, int gb) {
        vc.vertex(m, x, y, 0.0f)
                .color(255, gb, gb, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(matrices.peek(), 0.0f, 1.0f, 0.0f);
    }

    @Override
    public Identifier getTexture(GrinEntity entity) {
        return TEXTURE;
    }
}
