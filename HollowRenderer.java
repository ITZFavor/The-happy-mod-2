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

public class HollowRenderer extends EntityRenderer<HollowEntity> {
    private static final Identifier TEXTURE = Identifier.of(GrinMod.MOD_ID, "textures/entity/hollow.png");
    private static final float HEIGHT = 3.6f;
    private static final float WIDTH = HEIGHT * 512f / 605f;

    public HollowRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(HollowEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        float t = entity.age + tickDelta;

        // constant faint tremble, plus a violent glitch now and then
        float jx = (float) Math.sin(t * 61.0) * 0.02f;
        if (entity.age % 37 < 2) jx += 0.18f;
        boolean flicker = (Math.sin(t * 0.9) + Math.sin(t * 2.3)) > 1.6;
        int alpha = flicker ? 80 : 255;

        matrices.push();
        matrices.multiply(this.dispatcher.getRotation());
        matrices.translate(jx, HEIGHT / 2.0f - 0.1f, 0.0f);

        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        float hw = WIDTH / 2.0f;
        float hh = HEIGHT / 2.0f;

        vertex(vc, m, matrices, -hw, -hh, 0f, 1f, full, alpha);
        vertex(vc, m, matrices, hw, -hh, 1f, 1f, full, alpha);
        vertex(vc, m, matrices, hw, hh, 1f, 0f, full, alpha);
        vertex(vc, m, matrices, -hw, hh, 0f, 0f, full, alpha);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, MatrixStack matrices,
                               float x, float y, float u, float v, int light, int alpha) {
        vc.vertex(m, x, y, 0.0f)
                .color(255, 255, 255, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(matrices.peek(), 0.0f, 1.0f, 0.0f);
    }

    @Override
    public Identifier getTexture(HollowEntity entity) {
        return TEXTURE;
    }
}
