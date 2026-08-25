package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import ruby.bamboo.block.entity.MiniatureBlockEntity;

/**
 * ミニチュアの BER — 内部 cells を 1/size 縮小して描画。
 * <p>
 * SlideDoorBlockRenderer の tesselateBlock パターンを流用し、全体を 1/size でスケール後に
 * 各セルを translate して描画する。RenderType 分離はモデル毎に取得した RenderType で行う。
 * キャッシュは Phase D 初版では無し (毎フレーム再描画)。将来の quad キャッシュは §2.7.1 の
 * 遅延ロードと共に追加する。
 */
public class MiniatureBlockRenderer implements BlockEntityRenderer<MiniatureBlockEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public MiniatureBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        this.blockRenderer = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(MiniatureBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be == null || be.getLevel() == null || be.isEmpty()) {
            return;
        }
        int size = be.getSize();
        float scale = 1.0f / size;

        poseStack.pushPose();
        // 全体縮小 (ブロック1mを size分割)
        poseStack.scale(scale, scale, scale);

        RandomSource rand = RandomSource.create();
        BlockPos bePos = be.getBlockPos();
        // 外部レベルをそのまま利用 (光源は外部借用。厳密な内部光伝播はスコープ外)
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    BlockState state = be.getCell(x, y, z);
                    if (state == null || state.isAir() || state.getBlock() == Blocks.AIR) {
                        continue;
                    }
                    // TE所持ブロックも見た目のみ描画 (GUI/tick 無し)
                    // BlockModel を取得
                    net.minecraft.client.resources.model.BakedModel model = this.blockRenderer.getBlockModel(state);
                    // ModelData は外部レベルで取得を試みるが、必要なら EMPTY でフォールバック
                    ModelData md;
                    try {
                        md = model.getModelData(be.getLevel(), bePos, state, ModelData.EMPTY);
                    } catch (Exception e) {
                        md = ModelData.EMPTY;
                    }
                    // セル位置へ translate
                    poseStack.pushPose();
                    poseStack.translate(x, y, z);
                    // RenderType ごとに tesselate
                    // model.getRenderTypes は state + rand + md で取得
                    for (RenderType rt : model.getRenderTypes(state, rand, md)) {
                        VertexConsumer vc = bufferSource.getBuffer(rt);
                        try {
                            // cellPos は (x,y,z) ではなく bePos で AO を取るか、0,0,0 でも可。
                            // ここでは cell 自身の座標を渡すことで AO が内部セル間で正しく働くようにするが、
                            // 外部レベルの neighbour に依存する場合は外側になるため bePos を基準にする。
                            // 簡易では bePos を使用し、poseStack の translate で位置合わせ。
                            BlockPos cellPos = new BlockPos(x, y, z);
                            this.blockRenderer.getModelRenderer().tesselateBlock(
                                    be.getLevel(), model, state, cellPos,
                                    poseStack, vc, false, rand, state.getSeed(cellPos), packedOverlay, md, rt);
                        } catch (Exception e) {
                            // 例外は握りつぶし (MODブロックのモデルが壊れていてもクラッシュさせない)
                        }
                    }
                    // 流体は未対応 (Phase F以降で IFluidState 反映)
                    poseStack.popPose();
                }
            }
        }
        poseStack.popPose();

        // デバッグ: dirty 時に軽いワイヤーフレームを表示する案は将来 (§2.7.1インジケータ)
    }

    @Override
    public boolean shouldRenderOffScreen(MiniatureBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
