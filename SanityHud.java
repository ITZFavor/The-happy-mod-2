package com.grin;

import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Client-side: the bar above the hotbar, the screen-edge darkening, and the full-screen scares. */
public final class SanityHud {
    private static final Identifier SCARE_GRIN = Identifier.of(GrinMod.MOD_ID, "textures/gui/scare_grin.png");
    private static final Identifier SCARE_HOLLOW = Identifier.of(GrinMod.MOD_ID, "textures/gui/scare_hollow.png");
    private static final Random RNG = new Random();

    private static float displayed = 0f;
    private static float target = 0f;
    private static int scareTicks = 0;
    private static int scareTotal = 0;
    private static int scareKind = 0;

    private SanityHud() {}

    public static void setTarget(float v) {
        target = v;
    }

    public static void startScare(int kind, int ticks) {
        scareKind = kind;
        scareTicks = ticks;
        scareTotal = Math.max(1, ticks);
    }

    public static void tick() {
        displayed += (target - displayed) * 0.1f;
        if (scareTicks > 0) scareTicks--;
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        if (scareTicks > 0) {
            drawScare(ctx, w, h);
            return;
        }
        if (mc.options.hudHidden) return;

        drawVignette(ctx, w, h);
        drawBar(ctx, w, h);
    }

    private static void drawBar(DrawContext ctx, int w, int h) {
        float s = MathHelper.clamp(displayed, 0f, 100f);
        int bw = 100;
        int x = w / 2 - bw / 2;
        int y = h - 66;
        if (s > 70f) {
            x += RNG.nextInt(3) - 1;
            y += RNG.nextInt(3) - 1;
        }
        float pulse = 0.8f + 0.2f * (float) Math.sin(System.currentTimeMillis() / (s > 70f ? 120.0 : 400.0));
        float f = s / 100f;
        int r = (int) (MathHelper.lerp(f, 70f, 190f) * pulse);
        int g = (int) (MathHelper.lerp(f, 70f, 16f) * pulse);
        int b = (int) (MathHelper.lerp(f, 100f, 16f) * pulse);

        ctx.fill(x - 1, y - 1, x + bw + 1, y + 5, 0xFF000000);
        ctx.fill(x, y, x + bw, y + 4, 0xFF15151C);
        ctx.fill(x, y, x + Math.round(s), y + 4, 0xFF000000 | (r << 16) | (g << 8) | b);
    }

    private static void drawVignette(DrawContext ctx, int w, int h) {
        if (displayed <= 45f) return;
        int a = (int) (Math.min(1f, (displayed - 45f) / 55f) * 150f);
        int dark = a << 24;
        ctx.fillGradient(0, 0, w, h / 3, dark, 0x00000000);
        ctx.fillGradient(0, h - h / 3, w, h, 0x00000000, dark);
    }

    private static void drawScare(DrawContext ctx, int w, int h) {
        float prog = scareTicks / (float) scareTotal;
        boolean hollow = scareKind == 1;
        int texW = 512;
        int texH = hollow ? 605 : 512;

        ctx.fill(0, 0, w, h, 0xFF000000);
        float zoom = 1.0f + 0.35f * (1.0f - prog);
        int th = (int) (h * zoom);
        int tw = (int) (th * (texW / (float) texH));
        int x = (w - tw) / 2 + RNG.nextInt(9) - 4;
        int y = (h - th) / 2 + RNG.nextInt(9) - 4;
        ctx.drawTexture(hollow ? SCARE_HOLLOW : SCARE_GRIN, x, y, tw, th, 0f, 0f, texW, texH, texW, texH);
    }
}
