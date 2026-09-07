package ruby.bamboo.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.block.BambooPotBlock;
import ruby.bamboo.compat.dcs.DcsClimateCompat;

/**
 * 花壇の BER — プランター描画をフルキューブ天面 (y=1.0) 用に移植したもの。
 * 植物の描画は {@code BlockRenderer#renderSingleBlock} のバニラ経路のみを使う。
 */
public class FlowerBedBlockRenderer implements BlockEntityRenderer<FlowerBedBlockEntity> {

    /** 花壇天面Y (フルキューブ上面)。 */
    private static final double BED_TOP_Y = 1.0D;

    private final ItemRenderer itemRenderer;

    public FlowerBedBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(FlowerBedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var plants = be.getPlants();
        if (plants.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        var blockRenderer = mc.getBlockRenderer();

        // 花壇本体はフルキューブ不透明のため、BE位置の packedLight は遮光で暗くなる。
        // 植物は天面より上に描画されるので、1マス上の明るさを使う (鉢はnoOcclusionのため不要だった対応)。
        int plantLight = packedLight;
        try {
            if (be.getLevel() != null) {
                plantLight = net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
            }
        } catch (Exception ignored) {
        }

        for (var e : plants) {
            if (e.stack.isEmpty()) {
                continue;
            }
            float wx = e.offsetX;
            float wz = e.offsetZ;
            if (e.stack.getItem() instanceof BlockItem bi) {
                BlockState plantState = DcsClimateCompat.getPottedFlowerState(e.stack)
                        .orElseGet(() -> bi.getBlock().defaultBlockState());
                renderPlantBlock(blockRenderer, plantState, poseStack, buffer, plantLight, packedOverlay, wx, wz, leafyAdjustedScale(e.scale, plantState));
                continue;
            }
            BlockState hacFlower = DcsClimateCompat.getPottedFlowerState(e.stack).orElse(null);
            if (hacFlower != null) {
                renderPlantBlock(blockRenderer, hacFlower, poseStack, buffer, plantLight, packedOverlay, wx, wz, leafyAdjustedScale(e.scale, hacFlower));
                continue;
            }
            poseStack.pushPose();
            boolean is3D = false;
            try {
                is3D = ir.getModel(e.stack, be.getLevel(), null, 0).isGui3d();
            } catch (Exception ignored) {
            }

            double y = is3D ? BED_TOP_Y + 0.02D : BED_TOP_Y + 0.04D;
            poseStack.translate(0.5D + wx, y, 0.5D + wz);
            float scale = e.scale;
            poseStack.scale(scale, scale, scale);
            ir.renderStatic(e.stack, ItemDisplayContext.FIXED, plantLight, packedOverlay, poseStack, buffer, be.getLevel(), (int) be.getBlockPos().asLong());
            poseStack.popPose();
        }
    }

    /** HaC葉物花は花瓶準拠で縮小 (プランターと同一倍率)。 */
    private float leafyAdjustedScale(float scale, BlockState plantState) {
        if (DcsClimateCompat.isLeafyFlower(plantState)) {
            return scale * BambooPotBlock.HAC_LEAFY_SCALE_MUL;
        }
        return scale;
    }

    /** ブロック経路の描画本体 (天面直上、scale補正)。 */
    private void renderPlantBlock(net.minecraft.client.renderer.block.BlockRenderDispatcher blockRenderer,
            BlockState plantState, PoseStack poseStack, MultiBufferSource buffer,
            int packedLight, int packedOverlay, float wx, float wz, float scale) {
        poseStack.pushPose();
        poseStack.translate(0.5D + wx - 0.5D * scale, BED_TOP_Y, 0.5D + wz - 0.5D * scale);
        poseStack.scale(scale, scale, scale);
        blockRenderer.renderSingleBlock(plantState, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
