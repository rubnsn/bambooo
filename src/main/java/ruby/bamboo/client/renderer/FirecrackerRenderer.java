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
import ruby.bamboo.entity.FirecrackerEntity;

/**
 * かんしゃく玉レンダラ (旧 RenderFirecracker の 1.20.1 移植)。
 * <p>
 * 旧版は {@code RenderThrowable} (クロススプライト) だったため、
 * カメラ対面のビルボード描画 + 速度に応じた回転で「球体として転がる」を表現する。
 * 大きさは種類で変える (旧版のLVスケール相当: S 0.5 / M 0.75 / L 1.0)。
 */
public class FirecrackerRenderer extends EntityRenderer<FirecrackerEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(BambooMod.MODID, "textures/item/firecracker_m.png");
    private final ItemRenderer itemRenderer;

    public FirecrackerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(FirecrackerEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float scale = entity.getFirecrackerType().renderScale;
        poseStack.scale(scale, scale, scale);
        // カメラ対面 (クロススプライト相当)
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        // 速度に応じた回転 (転がり表現)。静止・固着中はゆっくり回るだけ。
        double speed = entity.getDeltaMovement().length();
        float spin = (entity.tickCount + partialTicks) * (float) (8.0D + speed * 40.0D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));

        ItemStack stack = entity.getItem();
        if (!stack.isEmpty()) {
            this.itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY,
                    poseStack, buffer, entity.level(), entity.getId());
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FirecrackerEntity entity) {
        return TEXTURE;
    }
}
