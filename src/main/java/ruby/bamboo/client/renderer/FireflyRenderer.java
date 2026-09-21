package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.FireflyEntity;

/**
 * ホタルレンダラ。発光体のためライティングはFULL_BRIGHT固定、
 * 透過度を時間で脈動させて明滅感を出す。瓶内演出からも共用する。
 */
public class FireflyRenderer extends EntityRenderer<FireflyEntity> {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "textures/entity/firefly.png");

    public FireflyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(FireflyEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(FireflyEntity entity, float entityYaw, float partialTick, PoseStack pose,
            MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float age = entity.tickCount + partialTick;
        pose.pushPose();
        renderGlowQuad(pose, buffer, 0.35F, blinkAlpha(age));
        pose.popPose();
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    /** 明滅 (約3秒周期)。消灯相を長めに取ったホタルらしい脈動。0.0〜1.0 */
    public static float blinkAlpha(float age) {
        float s = 0.5F + 0.5F * (float) Math.sin(age * 0.1047F);
        return s * s;
    }

    /** ビルボード済みを前提に、中心原点の一枚 quad を描く。
     * 発光/emissive系 (深度書き込みなし・深度テストあり) で、
     * 壁越し透視や後方透過の破綻が出ない */
    public static void renderGlowQuad(PoseStack pose, MultiBufferSource buffer, float size, float alpha) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        PoseStack.Pose last = pose.last();
        float h = size / 2.0F;
        consumer.vertex(last.pose(), -h, -h, 0.0F).color(1.0F, 1.0F, 1.0F, alpha)
                .uv(0.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(last.pose(), h, -h, 0.0F).color(1.0F, 1.0F, 1.0F, alpha)
                .uv(1.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(last.pose(), h, h, 0.0F).color(1.0F, 1.0F, 1.0F, alpha)
                .uv(1.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(last.pose(), -h, h, 0.0F).color(1.0F, 1.0F, 1.0F, alpha)
                .uv(0.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 1.0F, 0.0F).endVertex();
    }
}
