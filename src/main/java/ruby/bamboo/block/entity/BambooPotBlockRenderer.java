package ruby.bamboo.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.block.BambooPotBlock;

/**
 * 竹鉢の BER — 最大16本をオフセット+スケールで描画。
 * 各エントリの offsetX/Z は中心からの -0.5〜0.5、スケールはエントリ毎にばらつき。
 * バニラ鉢植えサイズを参照しつつ、3個で幅が埋まるように調整（GRID_SCALE）。
 */
public class BambooPotBlockRenderer implements BlockEntityRenderer<BambooPotBlockEntity> {

    private final ItemRenderer itemRenderer;

    public BambooPotBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(BambooPotBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var plants = be.getPlants();
        if (plants.isEmpty()) return;
        // Minecraft instance for isGui3d check
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer ir = mc.getItemRenderer();

        var blockRenderer = mc.getBlockRenderer();
        Direction facing = Direction.NORTH;
        try {
            BlockState st = be.getLevel() != null ? be.getLevel().getBlockState(be.getBlockPos()) : null;
            if (st != null && st.hasProperty(BambooPotBlock.FACING)) facing = st.getValue(BambooPotBlock.FACING);
        } catch (Exception ignored) {}
        for (var e : plants) {
            if (e.stack.isEmpty()) continue;
            float[] worldOff = BambooPotBlock.localToWorld(e.offsetX, e.offsetZ, facing);
            float wx = worldOff[0];
            float wz = worldOff[1];
            // BlockItemはcross等のブロックモデルで描画（花をCrossで表示）
            if (e.stack.getItem() instanceof BlockItem bi) {
                BlockState plantState = bi.getBlock().defaultBlockState();
                double y = 6.0D / 16.0D;
                poseStack.pushPose();
                float scale = e.scale;
                poseStack.translate(0.5D + wx - 0.5D * scale, y, 0.5D + wz - 0.5D * scale);
                poseStack.scale(scale, scale, scale);
                blockRenderer.renderSingleBlock(plantState, poseStack, buffer, packedLight, packedOverlay);
                poseStack.popPose();
                continue;
            }
            poseStack.pushPose();
            boolean is3D = false;
            try {
                is3D = ir.getModel(e.stack, be.getLevel(), null, 0).isGui3d();
            } catch (Exception ignored) {}

            double y = is3D ? 6.0D / 16.0D + 0.02D : 6.0D / 16.0D + 0.04D;
            poseStack.translate(0.5D + wx, y, 0.5D + wz);
            float scale = e.scale;
            poseStack.scale(scale, scale, scale);
            ir.renderStatic(e.stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer, be.getLevel(), (int) be.getBlockPos().asLong());
            poseStack.popPose();
        }
    }
}
