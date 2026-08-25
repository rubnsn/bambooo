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
                    // 汎用描画: RenderShape に応じて BakedModel と BER を組み合わせる
                    // - EnchantmentTable は MODEL + BER(book) の両方が必要 (旧実装は BERのみで本だけ表示)
                    // - Bed/Chest は ENTITYBLOCK_ANIMATED なので BER のみ
                    // - 通常ブロックは MODEL のみ
                    net.minecraft.world.level.block.RenderShape shape = state.getRenderShape();
                    boolean hasBE = state.hasBlockEntity();
                    // 1) BakedModel 描画 (MODEL の場合のみ。INVISIBLE/ENTITYBLOCK_ANIMATED はスキップ)
                    boolean shouldTesselate = (shape == net.minecraft.world.level.block.RenderShape.MODEL);
                    // EnchantmentTable のように hasBE かつ MODEL の場合は両方必要
                    // Bed/Chest は ENTITYBLOCK_ANIMATED なので tesselate 不要
                    if (shouldTesselate) {
                        net.minecraft.client.resources.model.BakedModel model = this.blockRenderer.getBlockModel(state);
                        ModelData md;
                        try {
                            md = model.getModelData(be.getLevel(), bePos, state, ModelData.EMPTY);
                        } catch (Exception e) {
                            md = ModelData.EMPTY;
                        }
                        poseStack.pushPose();
                        poseStack.translate(x, y, z);
                        for (RenderType rt : model.getRenderTypes(state, rand, md)) {
                            VertexConsumer vc = bufferSource.getBuffer(rt);
                            try {
                                BlockPos cellPos = new BlockPos(x, y, z);
                                this.blockRenderer.getModelRenderer().tesselateBlock(
                                        be.getLevel(), model, state, cellPos,
                                        poseStack, vc, false, rand, state.getSeed(cellPos), packedOverlay, md, rt);
                            } catch (Exception e) {
                            }
                        }
                        poseStack.popPose();
                    }
                    // 2) BER 描画 (hasBlockEntity なら汎用的に EntityBlock で試行。BaseEntityBlock に限定しない)
                    if (hasBE) {
                        try {
                            net.minecraft.world.level.block.entity.BlockEntity te = null;
                            net.minecraft.world.level.block.Block blk = state.getBlock();
                            if (blk instanceof net.minecraft.world.level.block.EntityBlock eb) {
                                te = eb.newBlockEntity(bePos, state);
                            } else if (blk instanceof net.minecraft.world.level.block.BaseEntityBlock base) {
                                te = base.newBlockEntity(bePos, state);
                            }
                            if (te != null) {
                                te.setLevel(be.getLevel());
                                var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
                                var renderer = dispatcher.getRenderer(te);
                                if (renderer != null) {
                                    poseStack.pushPose();
                                    poseStack.translate(x, y, z);
                                    //noinspection unchecked
                                    ((net.minecraft.client.renderer.blockentity.BlockEntityRenderer) renderer)
                                            .render(te, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
                                    poseStack.popPose();
                                }
                            }
                        } catch (Exception e) {
                            // BER 失敗は無視 (モデル側は既に描画済みなら残る)
                        }
                    }
                    // 3) 旧フォールバック: MODEL でも ENTITYBLOCK_ANIMATED でもないが hasBE でもない場合
                    // 上で shouldTesselate=false かつ hasBE=false の block (例: INVISIBLE) は何も描画しない
                    // ただし RenderShape が INVISIBLE で model が必要なケースは稀なのでスキップ
                    if (!shouldTesselate && !hasBE) {
                        // 念のため MODEL 以外でも BakedModel が存在すれば描画を試みる (フェンス等の特殊モデル対応)
                        // ただし ENTITYBLOCK_ANIMATED は既に BER で処理済みなので除外
                        if (shape != net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED
                                && shape != net.minecraft.world.level.block.RenderShape.INVISIBLE) {
                            try {
                                net.minecraft.client.resources.model.BakedModel model = this.blockRenderer.getBlockModel(state);
                                ModelData md = ModelData.EMPTY;
                                try {
                                    md = model.getModelData(be.getLevel(), bePos, state, ModelData.EMPTY);
                                } catch (Exception e) {}
                                poseStack.pushPose();
                                poseStack.translate(x, y, z);
                                for (RenderType rt : model.getRenderTypes(state, rand, md)) {
                                    VertexConsumer vc = bufferSource.getBuffer(rt);
                                    try {
                                        BlockPos cellPos = new BlockPos(x, y, z);
                                        this.blockRenderer.getModelRenderer().tesselateBlock(
                                                be.getLevel(), model, state, cellPos,
                                                poseStack, vc, false, rand, state.getSeed(cellPos), packedOverlay, md, rt);
                                    } catch (Exception e) {}
                                }
                                poseStack.popPose();
                            } catch (Exception e) {}
                        }
                    }
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
