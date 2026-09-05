package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.DolphinModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.DolphinRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.item.DyeColor;
import ruby.bamboo.entity.companion.DolphinCompanionEntity;

/**
 * イルカ仲間レンダラー - グレースケール化した白テクスチャを染料で乗算。
 * 要求: 白にしてから染料乗算 / グレースケール対応。
 */
public class DolphinCompanionRenderer extends DolphinRenderer {

    private static final ResourceLocation GRAY_TEXTURE = ResourceLocation.fromNamespaceAndPath("bamboomod", "textures/entity/dolphin_grayscale.png");
    private static final ResourceLocation VANILLA_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/dolphin.png");

    public DolphinCompanionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(Dolphin entity) {
        if (entity instanceof DolphinCompanionEntity) {
            return GRAY_TEXTURE;
        }
        return VANILLA_TEXTURE;
    }

    @Override
    public void render(Dolphin entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!(entity instanceof DolphinCompanionEntity dolphin)) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<>(entity, this, partialTicks, poseStack, buffer, packedLight)).isCanceled()) return;
        // グレースケール白テクスチャに染料を乗算
        DyeColor dye = dolphin.getDolphinColor();
        int dyeColor = dye.getTextureDiffuseColor();
        float[] cols = new float[] {
                (float) (dyeColor >> 16 & 255) / 255.0F,
                (float) (dyeColor >> 8 & 255) / 255.0F,
                (float) (dyeColor & 255) / 255.0F };
        // LivingEntityRenderer の render を複製して色だけ差し替え
        poseStack.pushPose();
        DolphinModel<Dolphin> model = this.getModel();
        model.attackTime = this.getAttackAnim(entity, partialTicks);
        boolean shouldSit = entity.isPassenger() && entity.getVehicle() != null && entity.getVehicle().shouldRiderSit();
        model.riding = shouldSit;
        model.young = entity.isBaby();
        float f = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float f1 = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float f2 = f1 - f;
        if (shouldSit && entity.getVehicle() instanceof LivingEntity living) {
            f = Mth.rotLerp(partialTicks, living.yBodyRotO, living.yBodyRot);
            f2 = f1 - f;
            float f3 = Mth.wrapDegrees(f2);
            if (f3 < -85.0F) f3 = -85.0F;
            if (f3 >= 85.0F) f3 = 85.0F;
            f = f1 - f3;
            if (f3 * f3 > 2500.0F) f += f3 * 0.2F;
            f2 = f1 - f;
        }
        float f6 = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        if (isEntityUpsideDown(entity)) {
            f6 *= -1.0F;
            f2 *= -1.0F;
        }
        if (entity.hasPose(Pose.SLEEPING)) {
            Direction dir = entity.getBedOrientation();
            if (dir != null) {
                float f4 = entity.getEyeHeight(Pose.STANDING) - 0.1F;
                poseStack.translate((float)(-dir.getStepX()) * f4, 0.0F, (float)(-dir.getStepZ()) * f4);
            }
        }
        float f7 = this.getBob(entity, partialTicks);
        float f8 = 0.0F;
        float f5 = 0.0F;
        if (!shouldSit && entity.isAlive()) {
            f8 = entity.walkAnimation.speed(partialTicks);
            f5 = entity.walkAnimation.position(partialTicks);
            if (entity.isBaby()) f5 *= 3.0F;
            if (f8 > 1.0F) f8 = 1.0F;
        }
        this.setupRotations(entity, poseStack, f7, f, partialTicks, f8);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.scale(entity, poseStack, partialTicks);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.prepareMobModel(entity, f5, f8, partialTicks);
        model.setupAnim(entity, f5, f8, f7, f2, f6);
        Minecraft mc = Minecraft.getInstance();
        boolean flag = this.isBodyVisible(entity);
        boolean flag1 = !flag && !entity.isInvisibleTo(mc.player);
        boolean flag2 = mc.shouldEntityAppearGlowing(entity);
        RenderType rendertype = this.getRenderType(entity, flag, flag1, flag2);
        if (rendertype != null) {
            VertexConsumer vc = buffer.getBuffer(rendertype);
            int overlay = getOverlayCoords(entity, this.getWhiteOverlayProgress(entity, partialTicks));
            // 白テクスチャに染料を乗算 (グレースケール対応)。1.21 は ARGB int で渡す
            float r = cols[0];
            float g = cols[1];
            float b = cols[2];
            float a = flag1 ? 0.15F : 1.0F;
            int color = ((int) (a * 255.0F) << 24) | ((int) (r * 255.0F) << 16)
                    | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
            model.renderToBuffer(poseStack, vc, packedLight, overlay, color);
        }
        if (!entity.isSpectator()) {
            for (var layer : this.layers) {
                layer.render(poseStack, buffer, packedLight, entity, f5, f8, partialTicks, f7, f2, f6);
            }
        }
        poseStack.popPose();
        // nameTag 等は EntityRenderer 側で処理 (LivingEntityRenderer の super は呼ばず直接 EntityRenderer 相当)
        if (this.shouldShowName(entity)) {
            this.renderNameTag(entity, entity.getDisplayName(), poseStack, buffer, packedLight, partialTicks);
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.RenderLivingEvent.Post<>(entity, this, partialTicks, poseStack, buffer, packedLight));
    }

    @Override
    protected boolean shouldShowName(Dolphin entity) {
        return super.shouldShowName(entity);
    }
}
