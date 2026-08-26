package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ruby.bamboo.block.entity.CutBlockEntity;

/**
 * カットブロックの BER — AABBに合わせた6面Quad再生成。
 * cutStateの各面のSpriteを流用し、Boundsサイズに合わせたCubeを描画する。
 */
public class CutBlockRenderer implements BlockEntityRenderer<CutBlockEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public CutBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        this.blockRenderer = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(CutBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be == null || be.getLevel() == null || be.isEmpty()) {
            return;
        }
        BlockState cutState = be.getCutState();
        if (cutState.isAir()) return;

        // FACING取得
        Direction facing = Direction.NORTH;
        try {
            BlockState outer = be.getBlockState();
            if (outer.hasProperty(ruby.bamboo.block.CutBlock.FACING)) {
                facing = outer.getValue(ruby.bamboo.block.CutBlock.FACING);
            }
        } catch (Exception e) {
        }

        int[] bounds = be.getBounds(facing);
        float minX = bounds[0] / 16f;
        float minY = bounds[1] / 16f;
        float minZ = bounds[2] / 16f;
        float maxX = bounds[3] / 16f;
        float maxY = bounds[4] / 16f;
        float maxZ = bounds[5] / 16f;

        // フルなら通常のtesselateで描画（高速パス、切断面なし）
        if (minX == 0 && minY == 0 && minZ == 0 && maxX == 1 && maxY == 1 && maxZ == 1) {
            renderFull(be, cutState, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        // 部分サイズ: Spriteを取得して6面を自前描画
        renderClipped(be, cutState, minX, minY, minZ, maxX, maxY, maxZ, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private void renderFull(CutBlockEntity be, BlockState cutState, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BakedModel model = this.blockRenderer.getBlockModel(cutState);
        ModelData md;
        try {
            md = model.getModelData(be.getLevel(), be.getBlockPos(), cutState, ModelData.EMPTY);
        } catch (Exception e) {
            md = ModelData.EMPTY;
        }
        RandomSource rand = RandomSource.create(cutState.getSeed(be.getBlockPos()));
        for (RenderType rt : model.getRenderTypes(cutState, rand, md)) {
            VertexConsumer vc = bufferSource.getBuffer(rt);
            try {
                this.blockRenderer.getModelRenderer().tesselateBlock(
                        be.getLevel(), model, cutState, be.getBlockPos(),
                        poseStack, vc, false, rand, cutState.getSeed(be.getBlockPos()), packedOverlay, md, rt);
            } catch (Exception e) {
            }
        }
    }

    private void renderClipped(CutBlockEntity be, BlockState cutState,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BakedModel model = this.blockRenderer.getBlockModel(cutState);
        // 各方向のSpriteを取得
        TextureAtlasSprite spriteUp = getSpriteForDir(model, cutState, Direction.UP, be);
        TextureAtlasSprite spriteDown = getSpriteForDir(model, cutState, Direction.DOWN, be);
        TextureAtlasSprite spriteNorth = getSpriteForDir(model, cutState, Direction.NORTH, be);
        TextureAtlasSprite spriteSouth = getSpriteForDir(model, cutState, Direction.SOUTH, be);
        TextureAtlasSprite spriteWest = getSpriteForDir(model, cutState, Direction.WEST, be);
        TextureAtlasSprite spriteEast = getSpriteForDir(model, cutState, Direction.EAST, be);

        // フォールバック: いずれかがnullならパーティクルアイコンを使う
        TextureAtlasSprite fallback = model.getParticleIcon(ModelData.EMPTY);
        if (spriteUp == null) spriteUp = fallback;
        if (spriteDown == null) spriteDown = fallback;
        if (spriteNorth == null) spriteNorth = fallback;
        if (spriteSouth == null) spriteSouth = fallback;
        if (spriteWest == null) spriteWest = fallback;
        if (spriteEast == null) spriteEast = fallback;

        // RenderTypeはcutStateのモデルから取得、なければcutout
        RenderType rt = RenderType.cutout();
        try {
            RandomSource rand = RandomSource.create();
            ModelData md = ModelData.EMPTY;
            try {
                md = model.getModelData(be.getLevel(), be.getBlockPos(), cutState, ModelData.EMPTY);
            } catch (Exception e) {}
            var types = model.getRenderTypes(cutState, rand, md);
            if (!types.isEmpty()) {
                rt = types.iterator().next();
                // translucentが含まれていればそちらを優先
                for (RenderType t : types) {
                    if (t == RenderType.translucent() || t == RenderType.cutoutMipped()) {
                        rt = t;
                        break;
                    }
                }
            }
        } catch (Exception e) {}

        VertexConsumer vc = bufferSource.getBuffer(rt);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normal = pose.normal();

        // 6面を描画。各面のUVはBounds比率で補間
        // 下面 Y-
        drawQuad(vc, mat, normal, spriteDown,
                minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                0, -1, 0, packedLight, packedOverlay);
        // 上面 Y+
        drawQuad(vc, mat, normal, spriteUp,
                minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ,
                0, 1, 0, packedLight, packedOverlay);
        // 北 Z-
        drawQuad(vc, mat, normal, spriteNorth,
                maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ,
                0, 0, -1, packedLight, packedOverlay);
        // 南 Z+
        drawQuad(vc, mat, normal, spriteSouth,
                minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
                0, 0, 1, packedLight, packedOverlay);
        // 西 X-（裏面カリングで透けないよう反時計回りに修正）
        drawQuad(vc, mat, normal, spriteWest,
                minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
                -1, 0, 0, packedLight, packedOverlay);
        // 東 X+（裏面カリングで透けないよう反時計回りに修正）
        drawQuad(vc, mat, normal, spriteEast,
                maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ,
                1, 0, 0, packedLight, packedOverlay);
    }

    private TextureAtlasSprite getSpriteForDir(BakedModel model, BlockState state, Direction dir, CutBlockEntity be) {
        try {
            RandomSource rand = RandomSource.create(state.getSeed(be.getBlockPos()));
            ModelData md = ModelData.EMPTY;
            try {
                md = model.getModelData(be.getLevel(), be.getBlockPos(), state, ModelData.EMPTY);
            } catch (Exception e) {}
            var quads = model.getQuads(state, dir, rand, md, null);
            if (quads != null && !quads.isEmpty()) {
                return quads.get(0).getSprite();
            }
            // null dir（カリングなし）のquadからも探す
            var general = model.getQuads(state, null, rand, md, null);
            if (general != null) {
                for (var q : general) {
                    if (q.getDirection() == dir) {
                        return q.getSprite();
                    }
                }
                if (!general.isEmpty()) {
                    return general.get(0).getSprite();
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    private void drawQuad(VertexConsumer vc, Matrix4f mat, Matrix3f normal, TextureAtlasSprite sprite,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float nx, float ny, float nz,
            int packedLight, int packedOverlay) {
        // UVは 0..16 を spriteのU0..U1にマッピング
        // 各面のUV軸を考慮: Y面はX/Z、Z面はX/Y、X面はZ/Y
        // 簡易: 四隅の座標からUVを計算（0..1を16倍してspriteに変換）
        float u1 = sprite.getU0();
        float u2 = sprite.getU1();
        float v1 = sprite.getV0();
        float v2 = sprite.getV1();
        // 面ごとのUV計算: 正面から見た2D座標で補間
        // 下/上面: X→U, Z→V
        // 北/南面: X→U, Y→V
        // 西/東面: Z→U, Y→V
        // ここでは簡易に、 quadの4頂点のワールド座標からUVを線形補間
        // ただし、Boundsが部分的な場合、UVも部分的にする必要がある
        // 例: minX=0.5なら Uは0.5..1.0 の範囲のみを使う
        // そのため、頂点座標(0..1)をそのままUV比率として使う
        float[] us = new float[4];
        float[] vs = new float[4];
        if (ny != 0) {
            // Y面
            us[0] = lerp(u1, u2, x1);
            vs[0] = lerp(v1, v2, z1);
            us[1] = lerp(u1, u2, x2);
            vs[1] = lerp(v1, v2, z2);
            us[2] = lerp(u1, u2, x3);
            vs[2] = lerp(v1, v2, z3);
            us[3] = lerp(u1, u2, x4);
            vs[3] = lerp(v1, v2, z4);
        } else if (nz != 0) {
            // Z面
            us[0] = lerp(u1, u2, x1);
            vs[0] = lerp(v1, v2, 1 - y1);
            us[1] = lerp(u1, u2, x2);
            vs[1] = lerp(v1, v2, 1 - y2);
            us[2] = lerp(u1, u2, x3);
            vs[2] = lerp(v1, v2, 1 - y3);
            us[3] = lerp(u1, u2, x4);
            vs[3] = lerp(v1, v2, 1 - y4);
        } else {
            // X面
            us[0] = lerp(u1, u2, z1);
            vs[0] = lerp(v1, v2, 1 - y1);
            us[1] = lerp(u1, u2, z2);
            vs[1] = lerp(v1, v2, 1 - y2);
            us[2] = lerp(u1, u2, z3);
            vs[2] = lerp(v1, v2, 1 - y3);
            us[3] = lerp(u1, u2, z4);
            vs[3] = lerp(v1, v2, 1 - y4);
        }
        vc.vertex(mat, x1, y1, z1).color(1f, 1f, 1f, 1f).uv(us[0], vs[0]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x2, y2, z2).color(1f, 1f, 1f, 1f).uv(us[1], vs[1]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x3, y3, z3).color(1f, 1f, 1f, 1f).uv(us[2], vs[2]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x4, y4, z4).color(1f, 1f, 1f, 1f).uv(us[3], vs[3]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public boolean shouldRenderOffScreen(CutBlockEntity be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public boolean shouldRender(CutBlockEntity be, net.minecraft.world.phys.Vec3 cameraPos) {
        return net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()).closerThan(cameraPos, getViewDistance());
    }
}
