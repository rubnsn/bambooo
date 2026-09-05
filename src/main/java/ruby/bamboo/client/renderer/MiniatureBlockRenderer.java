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
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ruby.bamboo.block.entity.MiniatureBlockEntity;
import ruby.bamboo.block.entity.MiniatureFakeLevelReader;
import ruby.bamboo.core.config.MiniatureConfig;

/**
 * Miniature BER - 1/size scaled rendering with face culling, budget and fluid support.
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
        if (!be.isRenderActive()) {
            MiniatureConfig.PlaceholderMode mode = MiniatureConfig.PlaceholderMode.WIREFRAME;
            try {
                mode = MiniatureConfig.CLIENT.placeholderMode.get();
            } catch (Exception e) {
                mode = MiniatureConfig.PlaceholderMode.WIREFRAME;
            }
            if (mode == MiniatureConfig.PlaceholderMode.HIDDEN) {
                return;
            }
            if (mode == MiniatureConfig.PlaceholderMode.TRANSLUCENT) {
                renderTranslucentPlaceholder(poseStack, bufferSource, packedLight, packedOverlay);
            } else {
                renderWireframePlaceholder(poseStack, bufferSource);
            }
            return;
        }

        boolean shellOnly = be.isRenderShellOnly();
        int size = be.getSize();
        float scale = 1.0f / size;

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);

        RandomSource rand = RandomSource.create();
        BlockPos bePos = be.getBlockPos();
        MiniatureFakeLevelReader fake = null;
        try {
            fake = new MiniatureFakeLevelReader(be, be.getLevel(), bePos);
        } catch (Exception e) {
            fake = null;
        }
        net.minecraft.world.level.BlockAndTintGetter getter = fake != null ? fake : be.getLevel();

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    if (shellOnly && !be.isShellCell(x, y, z)) continue;
                    BlockState state = be.getCell(x, y, z);
                    if (state == null || state.isAir() || state.getBlock() == Blocks.AIR) continue;
                    net.minecraft.world.level.block.RenderShape shape = state.getRenderShape();
                    boolean hasBE = state.hasBlockEntity();
                    boolean shouldTesselate = (shape == net.minecraft.world.level.block.RenderShape.MODEL);
                    if (shouldTesselate) {
                        net.minecraft.client.resources.model.BakedModel model = this.blockRenderer.getBlockModel(state);
                        ModelData md;
                        try {
                            md = model.getModelData(getter, new BlockPos(x, y, z), state, ModelData.EMPTY);
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
                                        getter, model, state, cellPos,
                                        poseStack, vc, false, rand, state.getSeed(cellPos), packedOverlay, md, rt);
                            } catch (Exception e) {
                            }
                        }
                        poseStack.popPose();
                    }
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
                        }
                    }
                    if (!shouldTesselate && !hasBE) {
                        if (shape != net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED
                                && shape != net.minecraft.world.level.block.RenderShape.INVISIBLE) {
                            try {
                                net.minecraft.client.resources.model.BakedModel model = this.blockRenderer.getBlockModel(state);
                                ModelData md = ModelData.EMPTY;
                                try {
                                    md = model.getModelData(getter, new BlockPos(x, y, z), state, ModelData.EMPTY);
                                } catch (Exception e) {}
                                poseStack.pushPose();
                                poseStack.translate(x, y, z);
                                for (RenderType rt : model.getRenderTypes(state, rand, md)) {
                                    VertexConsumer vc = bufferSource.getBuffer(rt);
                                    try {
                                        BlockPos cellPos = new BlockPos(x, y, z);
                                        this.blockRenderer.getModelRenderer().tesselateBlock(
                                                getter, model, state, cellPos,
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
        // 流体描画 — 再実装 (2026-08-26)
        // 水はフルキューブではない (height 可変、流動時は 0.888…等)、アニメは still/flow sprite の Atlas tick、
        // RenderType は translucent (ItemBlockRenderTypes.getRenderLayer) が正しいパス。旧実装の固定色/全側面常時/
        // Atlas毎フレーム取得/LEVEL 段階操作は廃止し、LiquidBlockRenderer 相当の height 平均化・フローUV・tint を
        // PoseStack(1/size) の Matrix でスケール描画する。
        java.util.List<BlockPos> fluidCells = new java.util.ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    if (shellOnly && !be.isShellCell(x, y, z)) continue;
                    BlockState st = be.getCell(x, y, z);
                    if (st == null || st.isAir()) continue;
                    if (st.getFluidState().isEmpty()) continue;
                    fluidCells.add(new BlockPos(x, y, z));
                }
            }
        }
        if (!fluidCells.isEmpty()) {
            // translucent の重なり対策: 奥から手前へソート (必要最小限)
            try {
                net.minecraft.world.phys.Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                fluidCells.sort((a, b) -> {
                    double ax = bePos.getX() + (a.getX() + 0.5) * scale;
                    double ay = bePos.getY() + (a.getY() + 0.5) * scale;
                    double az = bePos.getZ() + (a.getZ() + 0.5) * scale;
                    double bx = bePos.getX() + (b.getX() + 0.5) * scale;
                    double by = bePos.getY() + (b.getY() + 0.5) * scale;
                    double bz = bePos.getZ() + (b.getZ() + 0.5) * scale;
                    double da = (ax - cam.x) * (ax - cam.x) + (ay - cam.y) * (ay - cam.y) + (az - cam.z) * (az - cam.z);
                    double db = (bx - cam.x) * (bx - cam.x) + (by - cam.y) * (by - cam.y) + (bz - cam.z) * (bz - cam.z);
                    return Double.compare(db, da);
                });
            } catch (Exception e) {}
            for (BlockPos p : fluidCells) {
                BlockState st = be.getCell(p.getX(), p.getY(), p.getZ());
                FluidState fs = st.getFluidState();
                poseStack.pushPose();
                poseStack.translate(p.getX(), p.getY(), p.getZ());
                renderFluidScaled(poseStack, bufferSource, packedLight, packedOverlay, be, p, getter, st, fs);
                poseStack.popPose();
            }
        }
        poseStack.popPose();
    }

    private void renderWireframePlaceholder(PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer vc = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normal = pose.normal();
        float r = 0.6f, g = 0.8f, b = 1.0f, a = 1.0f;
        line(vc, pose, 0, 0, 0, 1, 0, 0, r, g, b, a);
        line(vc, pose, 1, 0, 0, 1, 0, 1, r, g, b, a);
        line(vc, pose, 1, 0, 1, 0, 0, 1, r, g, b, a);
        line(vc, pose, 0, 0, 1, 0, 0, 0, r, g, b, a);
        line(vc, pose, 0, 1, 0, 1, 1, 0, r, g, b, a);
        line(vc, pose, 1, 1, 0, 1, 1, 1, r, g, b, a);
        line(vc, pose, 1, 1, 1, 0, 1, 1, r, g, b, a);
        line(vc, pose, 0, 1, 1, 0, 1, 0, r, g, b, a);
        line(vc, pose, 0, 0, 0, 0, 1, 0, r, g, b, a);
        line(vc, pose, 1, 0, 0, 1, 1, 0, r, g, b, a);
        line(vc, pose, 1, 0, 1, 1, 1, 1, r, g, b, a);
        line(vc, pose, 0, 0, 1, 0, 1, 1, r, g, b, a);
    }

    private void line(VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float r, float g, float b, float a) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }

    private void renderTranslucentPlaceholder(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        VertexConsumer vc = bufferSource.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f norm = pose.normal();
        float r = 0.85f, g = 0.9f, b = 1.0f, alpha = 0.25f;
        int colR = (int) (r * 255), colG = (int) (g * 255), colB = (int) (b * 255), colA = (int) (alpha * 255);
        quad(vc, pose, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0, colR, colG, colB, colA, packedLight, packedOverlay);
        quad(vc, pose, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0, colR, colG, colB, colA, packedLight, packedOverlay);
        quad(vc, pose, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, -1, colR, colG, colB, colA, packedLight, packedOverlay);
        quad(vc, pose, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1, colR, colG, colB, colA, packedLight, packedOverlay);
        quad(vc, pose, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, -1, 0, 0, colR, colG, colB, colA, packedLight, packedOverlay);
        quad(vc, pose, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, colR, colG, colB, colA, packedLight, packedOverlay);
    }

    private void quad(VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      float nx, float ny, float nz,
                      int r, int g, int b, int a,
                      int packedLight, int packedOverlay) {
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(0, 0).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(1, 0).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(1, 1).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x4, y4, z4).setColor(r, g, b, a).setUv(0, 1).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
    }

    // ===== 流体描画 — LiquidBlockRenderer 相当を PoseStack スケールで再実装 =====
    // 注意: 水はフルキューブでなく height 可変 (getHeight + corner平均)、スプライト still/flow は Atlas アニメ、
    // RenderType は ItemBlockRenderTypes.getRenderLayer が正しいパス。
    private void renderFluidScaled(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                                   MiniatureBlockEntity be, BlockPos cellPos, net.minecraft.world.level.BlockAndTintGetter getter,
                                   BlockState blockState, FluidState fluidState) {
        // 旧実装の LEVEL 手動/固定色/Atlas毎フレーム取得/shouldCull=false は廃止。
        net.minecraft.world.level.material.Fluid fluid = fluidState.getType();
        net.minecraft.client.renderer.texture.TextureAtlasSprite[] sprites;
        try {
            var fluidExt = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluidState);
            var blockAtlas = net.minecraft.client.Minecraft.getInstance()
                    .getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
            sprites = new net.minecraft.client.renderer.texture.TextureAtlasSprite[] {
                    blockAtlas.apply(fluidExt.getStillTexture()),
                    blockAtlas.apply(fluidExt.getFlowingTexture()) };
        } catch (Exception e) {
            return;
        }
        if (sprites == null || sprites.length < 2 || sprites[0] == null) return;
        int tint;
        try {
            tint = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluidState).getTintColor(fluidState, getter, cellPos);
        } catch (Exception e) {
            tint = 0xFFFFFFFF;
        }
        float alpha = (float)(tint >> 24 & 255) / 255.0F;
        float rf = (float)(tint >> 16 & 255) / 255.0F;
        float gf = (float)(tint >> 8 & 255) / 255.0F;
        float bf = (float)(tint & 255) / 255.0F;
        // ラバ/水以外でも tint が 0 なら白に
        if (tint == 0) { rf = gf = bf = 1.0f; alpha = 1.0f; }
        // 非水でも半透明が必要なら alpha を流体側で保持。水は 0.8 相当だが tint の alpha を尊重。
        // 旧実装は固定 180/255 にしていたが、正しくは tint の alpha を使う。

        net.minecraft.client.renderer.RenderType rt;
        try {
            rt = net.minecraft.client.renderer.ItemBlockRenderTypes.getRenderLayer(fluidState);
        } catch (Exception e) {
            rt = net.minecraft.client.renderer.RenderType.translucent();
        }
        net.minecraft.client.renderer.texture.TextureAtlasSprite spriteStill = sprites[0];
        net.minecraft.client.renderer.texture.TextureAtlasSprite spriteFlow = sprites.length > 1 && sprites[1] != null ? sprites[1] : spriteStill;
        net.minecraft.client.renderer.texture.TextureAtlasSprite spriteOverlay = sprites.length > 2 ? sprites[2] : null;

        VertexConsumer vc = bufferSource.getBuffer(rt);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f norm = pose.normal();

        // 高さ計算 (LiquidBlockRenderer.getHeight 相当)
        float baseH = getHeight(getter, fluid, cellPos, blockState, fluidState);
        // 上が同流体なら 1.0 で埋まる扱い
        float h;
        if (baseH >= 1.0F) h = 1.0F;
        else if (baseH < 0) h = 0.0F;
        else h = baseH;
        // corner 平均 (water のみ行う。lava は平坦)
        boolean isWater = fluidState.is(Fluids.WATER);
        float h00, h10, h01, h11;
        if (isWater) {
            // 上が source なら上部は 1.0
            float northH = getHeight(getter, fluid, cellPos.north());
            float southH = getHeight(getter, fluid, cellPos.south());
            float eastH = getHeight(getter, fluid, cellPos.east());
            float westH = getHeight(getter, fluid, cellPos.west());
            // 中央が 1.0 なら全て 1.0
            if (h >= 1.0F) {
                h00 = h10 = h01 = h11 = 1.0F;
            } else {
                h00 = calculateAverageHeight(getter, fluid, h, northH, westH, cellPos.north().west());
                h10 = calculateAverageHeight(getter, fluid, h, northH, eastH, cellPos.north().east());
                h01 = calculateAverageHeight(getter, fluid, h, southH, westH, cellPos.south().west());
                h11 = calculateAverageHeight(getter, fluid, h, southH, eastH, cellPos.south().east());
            }
            // 上に source があれば 1.0 で上塗り (vanilla と同様)
            // calculateAverage 内で上段 source チェック済みだが、念のため
            if (h00 < 0) h00 = h;
            if (h10 < 0) h10 = h;
            if (h01 < 0) h01 = h;
            if (h11 < 0) h11 = h;
        } else {
            // lava 等は平坦
            h00 = h10 = h01 = h11 = h;
            if (h00 < 0.01f) h00 = h10 = h01 = h11 = 0.875f; // 旧フォールバック互換
        }
        if (h00 < 0) h00 = h;
        if (h10 < 0) h10 = h;
        if (h01 < 0) h01 = h;
        if (h11 < 0) h11 = h;

        // 遮蔽判定用 BlockState 取得
        BlockState downState = getter.getBlockState(cellPos.below());
        BlockState upState = getter.getBlockState(cellPos.above());
        FluidState upFluid = upState.getFluidState();
        boolean isUpSame = upFluid.getType().isSame(fluid);

        // 上下面の render 判定 (LiquidBlockRenderer.shouldRenderFace + occlusion)
        boolean renderUp = !isUpSame;
        if (renderUp) {
            // 上が同流体なら上は隠れる。加えて occlusion チェック (上面が固体で覆われる場合)
            // 1/size スケールでは 1.0 が天面。occlusion は height <1 なら false
            float minH = Math.min(Math.min(h00, h10), Math.min(h01, h11));
            if (isFaceOccludedByNeighbor(getter, cellPos, Direction.UP, minH, upState)) renderUp = false;
        }
        boolean renderDown = shouldRenderFace(getter, cellPos, fluidState, blockState, Direction.DOWN, downState.getFluidState())
                && !isFaceOccludedByNeighbor(getter, cellPos, Direction.DOWN, 0.8888889F, downState);
        // 水平 4面
        BlockState northSt = getter.getBlockState(cellPos.north());
        BlockState southSt = getter.getBlockState(cellPos.south());
        BlockState westSt = getter.getBlockState(cellPos.west());
        BlockState eastSt = getter.getBlockState(cellPos.east());
        boolean renderNorth = shouldRenderFace(getter, cellPos, fluidState, blockState, Direction.NORTH, northSt.getFluidState());
        boolean renderSouth = shouldRenderFace(getter, cellPos, fluidState, blockState, Direction.SOUTH, southSt.getFluidState());
        boolean renderWest = shouldRenderFace(getter, cellPos, fluidState, blockState, Direction.WEST, westSt.getFluidState());
        boolean renderEast = shouldRenderFace(getter, cellPos, fluidState, blockState, Direction.EAST, eastSt.getFluidState());

        // 透過しないなら早期 return
        if (!renderUp && !renderDown && !renderNorth && !renderSouth && !renderWest && !renderEast) return;

        // ライティング: lava は発光、水は外側 packedLight を用いる (Miniature は外部光借用)
        int light = packedLight;
        if (fluidState.is(net.minecraft.tags.FluidTags.LAVA)) light = 0xF000F0;

        // シェーディング (LiquidBlockRenderer と同様に shade を tint に乗算)
        float shadeDown = getter.getShade(Direction.DOWN, true);
        float shadeUp = getter.getShade(Direction.UP, true);
        float shadeNorth = getter.getShade(Direction.NORTH, true);
        float shadeWest = getter.getShade(Direction.WEST, true);

        //上面: flow に応じて still/flow を切り替え (アニメーションは sprite tick で自動)
        if (renderUp) {
            // 上面は 0.001 だけ下げて z-fighting 防止 (vanilla 同様)
            float uf = 0.001F;
            float rh00 = h00 - uf;
            float rh10 = h10 - uf;
            float rh01 = h01 - uf;
            float rh11 = h11 - uf;
            if (rh00 < 0) rh00 = 0;
            if (rh10 < 0) rh10 = 0;
            if (rh01 < 0) rh01 = 0;
            if (rh11 < 0) rh11 = 0;
            net.minecraft.world.phys.Vec3 flow = fluidState.getFlow(getter, cellPos);
            float u0, v0, u1, v1, u2, v2, u3, v3;
            net.minecraft.client.renderer.texture.TextureAtlasSprite topSprite;
            if (flow.x == 0.0D && flow.z == 0.0D) {
                topSprite = spriteStill;
                u0 = topSprite.getU((float)(0.0D));
                v0 = topSprite.getV((float)(0.0D));
                u1 = topSprite.getU((float)(16.0D));
                v1 = topSprite.getV((float)(0.0D));
                u2 = topSprite.getU((float)(16.0D));
                v2 = topSprite.getV((float)(16.0D));
                u3 = topSprite.getU((float)(0.0D));
                v3 = topSprite.getV((float)(16.0D));
                // uvShrink を考慮 (vanilla lerp)
                float shrink = topSprite.uvShrinkRatio();
                float cu = (u0 + u1 + u2 + u3) / 4.0F;
                float cv = (v0 + v1 + v2 + v3) / 4.0F;
                u0 = net.minecraft.util.Mth.lerp(shrink, u0, cu);
                u1 = net.minecraft.util.Mth.lerp(shrink, u1, cu);
                u2 = net.minecraft.util.Mth.lerp(shrink, u2, cu);
                u3 = net.minecraft.util.Mth.lerp(shrink, u3, cu);
                v0 = net.minecraft.util.Mth.lerp(shrink, v0, cv);
                v1 = net.minecraft.util.Mth.lerp(shrink, v1, cv);
                v2 = net.minecraft.util.Mth.lerp(shrink, v2, cv);
                v3 = net.minecraft.util.Mth.lerp(shrink, v3, cv);
            } else {
                topSprite = spriteFlow;
                float angle = (float)net.minecraft.util.Mth.atan2(flow.z, flow.x) - ((float)Math.PI / 2F);
                float s = net.minecraft.util.Mth.sin(angle) * 0.25F;
                float c = net.minecraft.util.Mth.cos(angle) * 0.25F;
                u0 = topSprite.getU((float)(8.0 + (-c - s) * 16.0));
                v0 = topSprite.getV((float)(8.0 + (-c + s) * 16.0));
                u1 = topSprite.getU((float)(8.0 + (-c + s) * 16.0));
                v1 = topSprite.getV((float)(8.0 + (c + s) * 16.0));
                u2 = topSprite.getU((float)(8.0 + (c + s) * 16.0));
                v2 = topSprite.getV((float)(8.0 + (c - s) * 16.0));
                u3 = topSprite.getU((float)(8.0 + (c - s) * 16.0));
                v3 = topSprite.getV((float)(8.0 + (-c - s) * 16.0));
                float shrink = topSprite.uvShrinkRatio();
                float cu = (u0 + u1 + u2 + u3) / 4.0F;
                float cv = (v0 + v1 + v2 + v3) / 4.0F;
                u0 = net.minecraft.util.Mth.lerp(shrink, u0, cu);
                u1 = net.minecraft.util.Mth.lerp(shrink, u1, cu);
                u2 = net.minecraft.util.Mth.lerp(shrink, u2, cu);
                u3 = net.minecraft.util.Mth.lerp(shrink, u3, cu);
                v0 = net.minecraft.util.Mth.lerp(shrink, v0, cv);
                v1 = net.minecraft.util.Mth.lerp(shrink, v1, cv);
                v2 = net.minecraft.util.Mth.lerp(shrink, v2, cv);
                v3 = net.minecraft.util.Mth.lerp(shrink, v3, cv);
            }
            float rUp = shadeUp * rf;
            float gUp = shadeUp * gf;
            float bUp = shadeUp * bf;
            // 0,0 -> 0,1 -> 1,1 ->1,0 の順 (vanilla と同じ winding)
            vc.addVertex(pose, 0, rh00, 0).setColor(rUp, gUp, bUp, alpha).setUv(u0, v0).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, 1, 0);
            vc.addVertex(pose, 0, rh01, 1).setColor(rUp, gUp, bUp, alpha).setUv(u3, v3).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, 1, 0);
            vc.addVertex(pose, 1, rh11, 1).setColor(rUp, gUp, bUp, alpha).setUv(u2, v2).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, 1, 0);
            vc.addVertex(pose, 1, rh10, 0).setColor(rUp, gUp, bUp, alpha).setUv(u1, v1).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, 1, 0);
            // 裏面 (shouldRenderBackwardUpFace) — 水面の裏を見せるため vanilla では両面描画
            if (fluidState.shouldRenderBackwardUpFace(getter, cellPos.above())) {
                vc.addVertex(pose, 0, rh00, 0).setColor(rUp, gUp, bUp, alpha).setUv(u0, v0).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, 1, 0);
                vc.addVertex(pose, 1, rh10, 0).setColor(rUp, gUp, bUp, alpha).setUv(u1, v1).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, 1, 0);
                vc.addVertex(pose, 1, rh11, 1).setColor(rUp, gUp, bUp, alpha).setUv(u2, v2).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, 1, 0);
                vc.addVertex(pose, 0, rh01, 1).setColor(rUp, gUp, bUp, alpha).setUv(u3, v3).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, 1, 0);
            }
        }
        if (renderDown) {
            float rDown = shadeDown * rf;
            float gDown = shadeDown * gf;
            float bDown = shadeDown * bf;
            float u0 = spriteStill.getU0();
            float u1 = spriteStill.getU1();
            float v0 = spriteStill.getV0();
            float v1 = spriteStill.getV1();
            // vanilla down は y=0.001
            float y = 0.001F;
            vc.addVertex(pose, 0, y, 1).setColor(rDown, gDown, bDown, alpha).setUv(u0, v1).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, -1, 0);
            vc.addVertex(pose, 0, y, 0).setColor(rDown, gDown, bDown, alpha).setUv(u0, v0).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, -1, 0);
            vc.addVertex(pose, 1, y, 0).setColor(rDown, gDown, bDown, alpha).setUv(u1, v0).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, -1, 0);
            vc.addVertex(pose, 1, y, 1).setColor(rDown, gDown, bDown, alpha).setUv(u1, v1).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, -1, 0);
        }
        // 側面: 高さに応じた UV 縦伸縮 + overlay 対応
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            boolean shouldDir;
            float ha, hb;
            double x0, z0, x1, z1;
            float shadeH;
            BlockState neighborSt;
            switch (dir) {
                case NORTH:
                    shouldDir = renderNorth; ha = h00; hb = h10; x0 = 0; z0 = 0.001; x1 = 1; z1 = 0.001; shadeH = shadeNorth; neighborSt = northSt; break;
                case SOUTH:
                    shouldDir = renderSouth; ha = h11; hb = h01; x0 = 1; z0 = 0.999; x1 = 0; z1 = 0.999; shadeH = shadeNorth; neighborSt = southSt; break;
                case WEST:
                    shouldDir = renderWest; ha = h01; hb = h00; x0 = 0.001; z0 = 1; x1 = 0.001; z1 = 0; shadeH = shadeWest; neighborSt = westSt; break;
                default: // EAST
                    shouldDir = renderEast; ha = h10; hb = h11; x0 = 0.999; z0 = 0; x1 = 0.999; z1 = 1; shadeH = shadeWest; neighborSt = eastSt; break;
            }
            if (!shouldDir) continue;
            if (isFaceOccludedByNeighbor(getter, cellPos, dir, Math.max(ha, hb), neighborSt)) continue;
            net.minecraft.client.renderer.texture.TextureAtlasSprite sideSprite = spriteFlow;
            if (spriteOverlay != null && neighborSt.shouldDisplayFluidOverlay(getter, cellPos.relative(dir), fluidState)) {
                sideSprite = spriteOverlay;
            }
            float u0 = sideSprite.getU((float)(0));
            float u1 = sideSprite.getU((float)(8.0));
            float v0 = sideSprite.getV((float)((1.0F - ha) * 8.0));
            float v1 = sideSprite.getV((float)((1.0F - hb) * 8.0));
            float v2 = sideSprite.getV((float)(8.0));
            float rr = shadeH * rf;
            float gg = shadeH * gf;
            float bb = shadeH * bf;
            float nx = dir.getStepX();
            float ny = 0;
            float nz = dir.getStepZ();
            // 前面
            vc.addVertex(pose, (float)x0, ha, (float)z0).setColor(rr, gg, bb, alpha).setUv(u0, v0).setOverlay(packedOverlay).setLight(light).setNormal(pose, nx, ny, nz);
            vc.addVertex(pose, (float)x1, hb, (float)z1).setColor(rr, gg, bb, alpha).setUv(u1, v1).setOverlay(packedOverlay).setLight(light).setNormal(pose, nx, ny, nz);
            vc.addVertex(pose, (float)x1, 0.001F, (float)z1).setColor(rr, gg, bb, alpha).setUv(u1, v2).setOverlay(packedOverlay).setLight(light).setNormal(pose, nx, ny, nz);
            vc.addVertex(pose, (float)x0, 0.001F, (float)z0).setColor(rr, gg, bb, alpha).setUv(u0, v2).setOverlay(packedOverlay).setLight(light).setNormal(pose, nx, ny, nz);
            // 裏面は overlay 以外は両面 (vanilla と同様に裏も描画して透け対策)
            if (sideSprite != spriteOverlay) {
                vc.addVertex(pose, (float)x0, 0.001F, (float)z0).setColor(rr, gg, bb, alpha).setUv(u0, v2).setOverlay(packedOverlay).setLight(light).setNormal(pose, nx, ny, nz);
                vc.addVertex(pose, (float)x1, 0.001F, (float)z1).setColor(rr, gg, bb, alpha).setUv(u1, v2).setOverlay(packedOverlay).setLight(light).setNormal(pose, nx, ny, nz);
                vc.addVertex(pose, (float)x1, hb, (float)z1).setColor(rr, gg, bb, alpha).setUv(u1, v1).setOverlay(packedOverlay).setLight(light).setNormal(pose, nx, ny, nz);
                vc.addVertex(pose, (float)x0, ha, (float)z0).setColor(rr, gg, bb, alpha).setUv(u0, v0).setOverlay(packedOverlay).setLight(light).setNormal(pose, nx, ny, nz);
            }
        }
    }

    private static float getHeight(net.minecraft.world.level.BlockAndTintGetter getter, net.minecraft.world.level.material.Fluid fluid, BlockPos pos) {
        BlockState bs = getter.getBlockState(pos);
        return getHeight(getter, fluid, pos, bs, bs.getFluidState());
    }

    private static float getHeight(net.minecraft.world.level.BlockAndTintGetter getter, net.minecraft.world.level.material.Fluid fluid, BlockPos pos, BlockState blockState, FluidState fluidState) {
        if (fluid.isSame(fluidState.getType())) {
            BlockState upBS = getter.getBlockState(pos.above());
            return fluid.isSame(upBS.getFluidState().getType()) ? 1.0F : fluidState.getOwnHeight();
        } else {
            return !blockState.isSolid() ? 0.0F : -1.0F;
        }
    }

    private static float calculateAverageHeight(net.minecraft.world.level.BlockAndTintGetter getter, net.minecraft.world.level.material.Fluid fluid, float selfH, float other1, float other2, BlockPos diagPos) {
        if (!(other2 >= 1.0F) && !(other1 >= 1.0F)) {
            float[] w = new float[2];
            if (other2 > 0.0F || other1 > 0.0F) {
                float d = getHeight(getter, fluid, diagPos);
                if (d >= 1.0F) return 1.0F;
                addWeightedHeight(w, d);
            }
            addWeightedHeight(w, selfH);
            addWeightedHeight(w, other2);
            addWeightedHeight(w, other1);
            return w[0] / w[1];
        } else {
            return 1.0F;
        }
    }

    private static void addWeightedHeight(float[] w, float h) {
        if (h >= 0.8F) {
            w[0] += h * 10.0F;
            w[1] += 10.0F;
        } else if (h >= 0.0F) {
            w[0] += h;
            w[1] += 1.0F;
        }
    }

    private static boolean isFaceOccludedByState(net.minecraft.world.level.BlockGetter getter, Direction dir, float height, BlockPos pos, BlockState state) {
        if (state.canOcclude()) {
            net.minecraft.world.phys.shapes.VoxelShape vs = net.minecraft.world.phys.shapes.Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, (double)height, 1.0D);
            net.minecraft.world.phys.shapes.VoxelShape vs1 = state.getOcclusionShape(getter, pos);
            return net.minecraft.world.phys.shapes.Shapes.blockOccudes(vs, vs1, dir);
        }
        return false;
    }

    private static boolean isFaceOccludedByNeighbor(net.minecraft.world.level.BlockGetter getter, BlockPos pos, Direction dir, float height, BlockState neighborState) {
        return isFaceOccludedByState(getter, dir, height, pos.relative(dir), neighborState);
    }

    private static boolean isFaceOccludedBySelf(net.minecraft.world.level.BlockGetter getter, BlockPos pos, BlockState state, Direction dir) {
        return isFaceOccludedByState(getter, dir.getOpposite(), 1.0F, pos, state);
    }

    private static boolean shouldRenderFace(net.minecraft.world.level.BlockAndTintGetter getter, BlockPos pos, FluidState fluidState, BlockState blockState, Direction dir, FluidState neighborFluid) {
        return !isFaceOccludedBySelf(getter, pos, blockState, dir) && !fluidState.getType().isSame(neighborFluid.getType());
    }

    private void fluidQuad(VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                           float x1, float y1, float z1,
                           float x2, float y2, float z2,
                           float x3, float y3, float z3,
                           float x4, float y4, float z4,
                           float nx, float ny, float nz,
                           int r, int g, int b, int a,
                           int packedLight, int packedOverlay,
                           float u0, float v0, float u1, float v1) {
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u0, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(u1, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(u1, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x4, y4, z4).setColor(r, g, b, a).setUv(u0, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(MiniatureBlockEntity be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
