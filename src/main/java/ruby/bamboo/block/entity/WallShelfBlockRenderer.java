package ruby.bamboo.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import ruby.bamboo.block.WallShelfBlock;

/**
 * 壁棚の BER (sakura-master WallShelfItemRender 移植)。
 * 旧 GlStateManager → PoseStack + ItemRenderer.renderStatic。
 * 2スロットを FACING別 ±0.25 offsetで棚上に描画、回転は EAST/WEST 90°。
 */
public class WallShelfBlockRenderer implements BlockEntityRenderer<WallShelfBlockEntity> {

    public WallShelfBlockRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(WallShelfBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var level = be.getLevel();
        if (level == null) return;
        Direction dir = be.getBlockState().getValue(WallShelfBlock.FACING);
        int rotateYaw = getRotation(dir);

        // RIGHT slot
        ItemStack right = be.getItem(WallShelfBlockEntity.SLOT_RIGHT);
        if (!right.isEmpty()) {
            double ox = getRightOffsetX(dir);
            double oz = getRightOffsetZ(dir);
            renderItem(right, poseStack, buffer, packedLight, packedOverlay, ox, oz, rotateYaw);
        }
        // LEFT slot
        ItemStack left = be.getItem(WallShelfBlockEntity.SLOT_LEFT);
        if (!left.isEmpty()) {
            double ox = getLeftOffsetX(dir);
            double oz = getLeftOffsetZ(dir);
            renderItem(left, poseStack, buffer, packedLight, packedOverlay, ox, oz, rotateYaw);
        }
    }

    private void renderItem(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer,
            int packedLight, int packedOverlay, double offsetX, double offsetZ, int rotateYaw) {
        if (stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        boolean is3D = itemRenderer.getModel(stack, null, null, 0).isGui3d();

        poseStack.pushPose();
        // 旧: x+0.5+offset, y+(is3D?0.5:0.6), z+0.5+offset  + rotate 180-rotateYaw
        double y = is3D ? 0.5D : 0.6D;
        // 棚板上面は y=5/16=0.3125〜6/16=0.375。アイテム中心をやや上に浮かせるため 0.5基準でOK (旧と同じ)
        poseStack.translate(0.5D + offsetX, y, 0.5D + offsetZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - rotateYaw));
        poseStack.scale(0.5F, 0.5F, 0.5F);
        // 1.20 ItemRendererは FIXED ではなく FIXED 相当の ItemDisplayContext.FIXED
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer, null, 0);
        poseStack.popPose();
    }

    // 旧 WallShelfItemRender からの移植
    public int getRotation(Direction dir) {
        switch (dir) {
            case EAST:
            case WEST:
                return 90;
            default:
                return 0;
        }
    }

    public double getRightOffsetX(Direction dir) {
        switch (dir) {
            case EAST:
            case NORTH:
                return 0.25;
            case SOUTH:
            case WEST:
                return -0.25;
            default:
                return 0;
        }
    }

    public double getRightOffsetZ(Direction dir) {
        switch (dir) {
            case EAST:
            case SOUTH:
                return 0.25;
            case NORTH:
            case WEST:
                return -0.25;
            default:
                return 0;
        }
    }

    public double getLeftOffsetX(Direction dir) {
        switch (dir) {
            case EAST:
            case SOUTH:
                return 0.25;
            case NORTH:
            case WEST:
                return -0.25;
            default:
                return 0;
        }
    }

    public double getLeftOffsetZ(Direction dir) {
        switch (dir) {
            case SOUTH:
            case WEST:
                return 0.25;
            case EAST:
            case NORTH:
                return -0.25;
            default:
                return 0;
        }
    }
}
