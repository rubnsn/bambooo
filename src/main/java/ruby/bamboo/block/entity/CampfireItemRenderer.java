package ruby.bamboo.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 囲炉裏のインベントリアイコン用 BEWLR (旧 RenderCampfire.renderInv 相当)。
 * <p>
 * 旧仕様: 薪台のみを 1.68倍 scale で表示。
 * インベントリモデルは {@code builtin/entity} を参照する。
 */
public class CampfireItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final CampfireItemRenderer INSTANCE = new CampfireItemRenderer();

    public static CampfireItemRenderer getInstance() {
        return INSTANCE;
    }

    private CampfireItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        // 旧 renderInv: translate(1,0,0) + scale(1.68)
        poseStack.translate(1.0D, 0.0D, 0.0D);
        poseStack.scale(1.68F, 1.68F, 1.68F);

        VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(CampfireBlockRenderer.TEXTURE));
        CampfireBlockRenderer.getInstance().renderWood(poseStack, vc, packedLight, packedOverlay);

        poseStack.popPose();
    }
}