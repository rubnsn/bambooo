package ruby.bamboo.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import ruby.bamboo.block.SlideDoorBlock;

/**
 * 引き戸の BlockEntityRenderer — sakura-master `SlideDoorRender` の 1.20.1 移植。
 * <p>
 * 旧 sakura は {@code TileEntityRendererFast} で {@code BlockRendererDispatcher.getBlockModelShapes().getModel(state)} を
 * {@code buffer.setTranslation(renderX,renderY,renderZ)} で隣接位置へ描画していた。
 * 1.20.1 では {@link BlockEntityRenderer} + {@link PoseStack} で同等のスライド描画を再現する。
 * <p>
 * 参考: {@link ruby.bamboo.block.entity.CampfireBlockRenderer} (ModelPart方式) とは異なり、本クラスは BlockModel を直接描画する。
 */
public class SlideDoorBlockRenderer implements BlockEntityRenderer<SlideDoorBlockEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public SlideDoorBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        this.blockRenderer = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(SlideDoorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (state == null || !state.hasProperty(SlideDoorBlock.FACING) || be.getLevel() == null) {
            return;
        }
        float x = Mth.lerp(partialTick, be.prevPosX, be.posX);
        float z = Mth.lerp(partialTick, be.prevPosZ, be.posZ);
        if (x == 0 && z == 0 && !state.getValue(SlideDoorBlock.OPEN) && !state.getValue(SlideDoorBlock.MOVED)) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x, 0, z);
        // packedLight はスライド先の明るさで取り直す — tesselateBlock は内部で Level から取得するため明示渡し不要だが、
        // 移動ブロックでは chunk 用 translucent と moving 用 translucentMovingBlock を使い分ける必要がある。
        // ForgeHooksClient.renderPistonMovedBlocks と同等のループで透過を正しく保つ。
        net.minecraft.client.resources.model.BakedModel model = this.blockRenderer.getBlockModel(state);
        for (net.minecraft.client.renderer.RenderType chunkRt : model.getRenderTypes(state, RandomSource.create(state.getSeed(be.getBlockPos())), ModelData.EMPTY)) {
            net.minecraft.client.renderer.RenderType movingRt = RenderTypeHelper.getMovingBlockRenderType(chunkRt);
            VertexConsumer vc = bufferSource.getBuffer(movingRt);
            this.blockRenderer.getModelRenderer().tesselateBlock(be.getLevel(), model, state, be.getBlockPos(), poseStack, vc, false, RandomSource.create(), state.getSeed(be.getBlockPos()), packedOverlay, ModelData.EMPTY, chunkRt);
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SlideDoorBlockEntity be) {
        // スライド時に隣接ブロックへはみ出すため offScreen でも描画
        return true;
    }
}
