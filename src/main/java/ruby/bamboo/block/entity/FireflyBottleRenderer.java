package ruby.bamboo.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import ruby.bamboo.client.renderer.FireflyRenderer;

/**
 * ホタル瓶の BER。瓶内でホタルがゆっくり漂う (リサージュ軌道 + 明滅)。
 */
public class FireflyBottleRenderer implements BlockEntityRenderer<FireflyBottleBlockEntity> {

    public FireflyBottleRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(FireflyBottleBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        // 瓶ごとに位相をずらす
        float offset = (be.getBlockPos().getX() * 7 + be.getBlockPos().getY() * 13
                + be.getBlockPos().getZ() * 5) % 100 * 0.1F;
        float t = (level.getGameTime() + partialTick) * 0.03F + offset;
        float cx = 0.5F + 0.15F * Mth.sin(t);
        float cy = 0.42F + 0.12F * Mth.sin(t * 1.7F);
        float cz = 0.5F + 0.15F * Mth.cos(t * 0.8F);
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        FireflyRenderer.renderGlowQuad(pose, buffer, 0.22F, FireflyRenderer.blinkAlpha(t * 33.0F));
        pose.popPose();
    }
}
