package ruby.bamboo.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 竹鉢の BER — 中央に植物を scale 0.5 で表示。
 */
public class BambooPotBlockRenderer implements BlockEntityRenderer<BambooPotBlockEntity> {

    private final ItemRenderer itemRenderer;

    public BambooPotBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(BambooPotBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack stack = be.getItem(0);
        if (stack.isEmpty()) return;
        poseStack.pushPose();
        // 鉢の中央、天面付近 (y=4/16=0.25 に少し上げて 0.3)
        poseStack.translate(0.5D, 0.28D, 0.5D);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        // 固定表示でアイテムを中央に
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer, be.getLevel(), (int) be.getBlockPos().asLong());
        poseStack.popPose();
    }
}
