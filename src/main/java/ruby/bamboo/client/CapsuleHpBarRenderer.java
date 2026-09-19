package ruby.bamboo.client;

import java.util.ArrayList;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.client.handler.ClientCapsuleHandler;

/**
 * 解放中カプセル個体の頭上HPバー (docs §6)。
 * ダメージ時 (10秒) のみ表示。ただしHP半分未満は常時表示 (ユーザー決定 §9-5)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CapsuleHpBarRenderer {

    private CapsuleHpBarRenderer() {
    }

    private static final long SHOW_TICKS = 100L;
    private static final double MAX_DIST_SQ = 48.0D * 48.0D;

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (ClientCapsuleHandler.states().isEmpty()) return;
        long now = mc.level.getGameTime();
        var pose = event.getPoseStack();
        var camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        var snapshot = new ArrayList<>(ClientCapsuleHandler.states().entrySet());
        boolean drewAnything = false;
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        for (var e : snapshot) {
            var entity = mc.level.getEntity(e.getKey());
            var info = e.getValue();
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                ClientCapsuleHandler.states().remove(e.getKey());
                continue;
            }
            if (living.distanceToSqr(camPos) > MAX_DIST_SQ) continue;
            float max = info.maxHp > 0.0F ? info.maxHp : living.getMaxHealth();
            float hp = Math.min(living.getHealth(), max);
            boolean show;
            if (hp < max * 0.5F) {
                show = true;
            } else {
                if (hp < info.lastHp - 0.01F) info.lastHurtTime = now;
                show = now - info.lastHurtTime < SHOW_TICKS;
            }
            info.lastHp = hp;
            if (!show || max <= 0.0F) continue;
            drawBar(pose, camera, living, camPos, hp / max);
            drewAnything = true;
        }
        if (drewAnything) {
            // Tesselator は描画都度 end しているためここでの後始末は状態復帰のみ
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void drawBar(com.mojang.blaze3d.vertex.PoseStack pose,
            net.minecraft.client.Camera camera, LivingEntity living, Vec3 camPos, float ratio) {
        float width = 40.0F;
        float height = 5.0F;
        pose.pushPose();
        pose.translate(living.getX() - camPos.x, living.getY() - camPos.y + living.getBbHeight() + 0.6D,
                living.getZ() - camPos.z);
        pose.mulPose(camera.rotation());
        float scale = 0.025F;
        pose.scale(-scale, -scale, scale);
        var matrix = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        // 背景 (黒半透明)
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(buf, matrix, -width / 2.0F - 1.0F, -1.0F, width + 2.0F, height + 2.0F, 0, 0, 0, 110);
        tesselator.end();
        // 本体 (残量色: 緑→橙→赤)
        int r, g;
        if (ratio > 0.5F) {
            r = 60;
            g = 200;
        } else if (ratio > 0.25F) {
            r = 230;
            g = 140;
        } else {
            r = 220;
            g = 40;
        }
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(buf, matrix, -width / 2.0F, 0.0F, width * Math.max(0.0F, Math.min(1.0F, ratio)), height,
                r, g, 40, 255);
        tesselator.end();
        pose.popPose();
    }

    private static void quad(BufferBuilder buf, org.joml.Matrix4f matrix,
            float x, float y, float w, float h, int r, int g, int b, int a) {
        buf.vertex(matrix, x, y, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x, y + h, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x + w, y + h, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x + w, y, 0.0F).color(r, g, b, a).endVertex();
    }
}
