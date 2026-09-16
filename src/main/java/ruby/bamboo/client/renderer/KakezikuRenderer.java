package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.KakezikuEntity;
import ruby.bamboo.entity.KakezikuMotive;

/**
 * 掛け軸レンダラ (旧 RenderKakeziku の1.20.1移植)。
 * <p>
 * 旧は GL11 即時モードで単一テクスチャ ({@code textures/entity/kakeziku.png})
 * から柄ごとにUV切替していた。本移植も同テクスチャ (256x256) のUV切替を踏襲し、
 * 表面に柄・裏面と縁は額縁色の無地で描画する。
 * <p>
 * 1.21: {@code ResourceLocation.fromNamespaceAndPath}、
 * 頂点連鎖は {@code addVertex} 形式 (§11)。
 */
public class KakezikuRenderer extends EntityRenderer<KakezikuEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID,
            "textures/entity/kakeziku.png");
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    /** 掛け軸の厚み 1px の半分 (ブロック単位) */
    private static final float HALF_THICK = 0.03125F;
    /** 額縁色 */
    private static final float FRAME_R = 0.30F;
    private static final float FRAME_G = 0.20F;
    private static final float FRAME_B = 0.14F;

    public KakezikuRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(KakezikuEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // ローカル +Z が壁の反対側 (部屋側) を向くよう回転
        Direction dir = entity.getDirection();
        poseStack.mulPose(Axis.YP.rotationDegrees(-dir.toYRot()));

        KakezikuMotive motive = entity.getMotive();
        float hw = motive.sizeX / 32.0F;
        float hh = motive.sizeY / 32.0F;
        float u0 = motive.offsetX / 256.0F;
        float u1 = (motive.offsetX + motive.sizeX) / 256.0F;
        float v0 = motive.offsetY / 256.0F;
        float v1 = (motive.offsetY + motive.sizeY) / 256.0F;

        // 表面 (柄)
        VertexConsumer front = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        quad(front, poseStack, packedLight, 1.0F, 1.0F, 1.0F,
                -hw, -hh, HALF_THICK, u0, v1,
                hw, -hh, HALF_THICK, u1, v1,
                hw, hh, HALF_THICK, u1, v0,
                -hw, hh, HALF_THICK, u0, v0,
                0.0F, 0.0F, 1.0F);

        // 裏面・縁 (額縁色の無地)
        VertexConsumer frame = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        // 裏面
        quad(frame, poseStack, packedLight, FRAME_R, FRAME_G, FRAME_B,
                hw, -hh, -HALF_THICK, 0.0F, 1.0F,
                -hw, -hh, -HALF_THICK, 1.0F, 1.0F,
                -hw, hh, -HALF_THICK, 1.0F, 0.0F,
                hw, hh, -HALF_THICK, 0.0F, 0.0F,
                0.0F, 0.0F, -1.0F);
        // 上縁
        quad(frame, poseStack, packedLight, FRAME_R, FRAME_G, FRAME_B,
                -hw, hh, HALF_THICK, 0.0F, 0.0F,
                hw, hh, HALF_THICK, 1.0F, 0.0F,
                hw, hh, -HALF_THICK, 1.0F, 1.0F,
                -hw, hh, -HALF_THICK, 0.0F, 1.0F,
                0.0F, 1.0F, 0.0F);
        // 下縁
        quad(frame, poseStack, packedLight, FRAME_R, FRAME_G, FRAME_B,
                -hw, -hh, -HALF_THICK, 0.0F, 0.0F,
                hw, -hh, -HALF_THICK, 1.0F, 0.0F,
                hw, -hh, HALF_THICK, 1.0F, 1.0F,
                -hw, -hh, HALF_THICK, 0.0F, 1.0F,
                0.0F, -1.0F, 0.0F);
        // 左縁
        quad(frame, poseStack, packedLight, FRAME_R, FRAME_G, FRAME_B,
                -hw, -hh, -HALF_THICK, 0.0F, 0.0F,
                -hw, -hh, HALF_THICK, 1.0F, 0.0F,
                -hw, hh, HALF_THICK, 1.0F, 1.0F,
                -hw, hh, -HALF_THICK, 0.0F, 1.0F,
                -1.0F, 0.0F, 0.0F);
        // 右縁
        quad(frame, poseStack, packedLight, FRAME_R, FRAME_G, FRAME_B,
                hw, -hh, HALF_THICK, 0.0F, 0.0F,
                hw, -hh, -HALF_THICK, 1.0F, 0.0F,
                hw, hh, -HALF_THICK, 1.0F, 1.0F,
                hw, hh, HALF_THICK, 0.0F, 1.0F,
                1.0F, 0.0F, 0.0F);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, int light,
            float r, float g, float b,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float nx, float ny, float nz) {
        PoseStack.Pose pose = poseStack.last();
        vertex(consumer, pose, light, r, g, b, x0, y0, z0, u0, v0, nx, ny, nz);
        vertex(consumer, pose, light, r, g, b, x1, y1, z1, u1, v1, nx, ny, nz);
        vertex(consumer, pose, light, r, g, b, x2, y2, z2, u2, v2, nx, ny, nz);
        vertex(consumer, pose, light, r, g, b, x3, y3, z3, u3, v3, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light,
            float r, float g, float b, float x, float y, float z, float u, float v,
            float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(KakezikuEntity entity) {
        return TEXTURE;
    }
}
