package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.ShurikenEntity;

/**
 * 手裏剣レンダラ (sakura ShurikenRender の 1.20.1 移植)。
 * アイテムを 0.25スケールで回転描画。
 */
public class ShurikenRenderer extends EntityRenderer<ShurikenEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(BambooMod.MODID, "textures/entity/shuriken.png");
    private final ItemRenderer itemRenderer;

    public ShurikenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(ShurikenEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0, 0.25D, 0);
        // Yaw: -90補正 + xAngle + pitch回転 (sakura踏襲)
        float yaw = net.minecraft.util.Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F;
        float pitch = net.minecraft.util.Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.xAngle));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
        float scale = 0.25F;
        poseStack.scale(scale, scale, scale);

        ItemStack stack = entity.getItem();
        if (!stack.isEmpty()) {
            this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        }
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ShurikenEntity entity) {
        return TEXTURE;
    }
}
