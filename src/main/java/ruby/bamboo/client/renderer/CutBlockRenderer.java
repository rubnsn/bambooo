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
 * 3軸絶対 Bounds で描画。FACING依存なし。
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
        if (!be.getEntries().isEmpty()) {
            for (CutBlockEntity.CutEntry entry : be.getEntries()) {
                BlockState state = entry.state;
                if (state == null || state.isAir()) continue;
                int[] b = entry.bounds;
                float minX = b[0] / 16f;
                float minY = b[1] / 16f;
                float minZ = b[2] / 16f;
                float maxX = b[3] / 16f;
                float maxY = b[4] / 16f;
                float maxZ = b[5] / 16f;
                if (minX == 0 && minY == 0 && minZ == 0 && maxX == 1 && maxY == 1 && maxZ == 1) {
                    renderFull(be, state, poseStack, bufferSource, packedLight, packedOverlay);
                } else {
                    renderClipped(be, entry, minX, minY, minZ, maxX, maxY, maxZ, poseStack, bufferSource, packedLight, packedOverlay);
                }
            }
            return;
        }
        BlockState cutState = be.getCutState();
        if (cutState.isAir()) return;

        int[] bounds = be.getBoundsAbsolute();
        float minX = bounds[0] / 16f;
        float minY = bounds[1] / 16f;
        float minZ = bounds[2] / 16f;
        float maxX = bounds[3] / 16f;
        float maxY = bounds[4] / 16f;
        float maxZ = bounds[5] / 16f;

        if (minX == 0 && minY == 0 && minZ == 0 && maxX == 1 && maxY == 1 && maxZ == 1) {
            renderFull(be, cutState, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

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
        TextureAtlasSprite spriteUp = getSpriteForDir(model, cutState, Direction.UP, be);
        TextureAtlasSprite spriteDown = getSpriteForDir(model, cutState, Direction.DOWN, be);
        TextureAtlasSprite spriteNorth = getSpriteForDir(model, cutState, Direction.NORTH, be);
        TextureAtlasSprite spriteSouth = getSpriteForDir(model, cutState, Direction.SOUTH, be);
        TextureAtlasSprite spriteWest = getSpriteForDir(model, cutState, Direction.WEST, be);
        TextureAtlasSprite spriteEast = getSpriteForDir(model, cutState, Direction.EAST, be);

        TextureAtlasSprite fallback = model.getParticleIcon(ModelData.EMPTY);
        if (spriteUp == null) spriteUp = fallback;
        if (spriteDown == null) spriteDown = fallback;
        if (spriteNorth == null) spriteNorth = fallback;
        if (spriteSouth == null) spriteSouth = fallback;
        if (spriteWest == null) spriteWest = fallback;
        if (spriteEast == null) spriteEast = fallback;

        int colorDown = getColorForDir(model, cutState, Direction.DOWN, be);
        int colorUp = getColorForDir(model, cutState, Direction.UP, be);
        int colorNorth = getColorForDir(model, cutState, Direction.NORTH, be);
        int colorSouth = getColorForDir(model, cutState, Direction.SOUTH, be);
        int colorWest = getColorForDir(model, cutState, Direction.WEST, be);
        int colorEast = getColorForDir(model, cutState, Direction.EAST, be);

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

        drawQuad(vc, mat, normal, spriteDown, colorDown,
                minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                0, -1, 0, packedLight, packedOverlay);
        drawQuad(vc, mat, normal, spriteUp, colorUp,
                minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ,
                0, 1, 0, packedLight, packedOverlay);
        drawQuad(vc, mat, normal, spriteNorth, colorNorth,
                maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ,
                0, 0, -1, packedLight, packedOverlay);
        drawQuad(vc, mat, normal, spriteSouth, colorSouth,
                minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
                0, 0, 1, packedLight, packedOverlay);
        drawQuad(vc, mat, normal, spriteWest, colorWest,
                minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
                -1, 0, 0, packedLight, packedOverlay);
        drawQuad(vc, mat, normal, spriteEast, colorEast,
                maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ,
                1, 0, 0, packedLight, packedOverlay);
    }

    private void renderClipped(CutBlockEntity be, CutBlockEntity.CutEntry entry,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState cutState = entry.state;
        BakedModel model = this.blockRenderer.getBlockModel(cutState);
        TextureAtlasSprite spriteUp = getSpriteForDir(model, cutState, Direction.UP, be);
        TextureAtlasSprite spriteDown = getSpriteForDir(model, cutState, Direction.DOWN, be);
        TextureAtlasSprite spriteNorth = getSpriteForDir(model, cutState, Direction.NORTH, be);
        TextureAtlasSprite spriteSouth = getSpriteForDir(model, cutState, Direction.SOUTH, be);
        TextureAtlasSprite spriteWest = getSpriteForDir(model, cutState, Direction.WEST, be);
        TextureAtlasSprite spriteEast = getSpriteForDir(model, cutState, Direction.EAST, be);

        TextureAtlasSprite fallback = model.getParticleIcon(ModelData.EMPTY);
        if (spriteUp == null) spriteUp = fallback;
        if (spriteDown == null) spriteDown = fallback;
        if (spriteNorth == null) spriteNorth = fallback;
        if (spriteSouth == null) spriteSouth = fallback;
        if (spriteWest == null) spriteWest = fallback;
        if (spriteEast == null) spriteEast = fallback;

        int colorDown = getColorForDir(model, cutState, Direction.DOWN, be);
        int colorUp = getColorForDir(model, cutState, Direction.UP, be);
        int colorNorth = getColorForDir(model, cutState, Direction.NORTH, be);
        int colorSouth = getColorForDir(model, cutState, Direction.SOUTH, be);
        int colorWest = getColorForDir(model, cutState, Direction.WEST, be);
        int colorEast = getColorForDir(model, cutState, Direction.EAST, be);

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

        // 内部隣接面カリング: 相手が不透明かつ自身を覆うならスキップ
        if (!shouldCull(be, entry, Direction.DOWN))
            drawQuad(vc, mat, normal, spriteDown, colorDown,
                    minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                    0, -1, 0, packedLight, packedOverlay);
        if (!shouldCull(be, entry, Direction.UP))
            drawQuad(vc, mat, normal, spriteUp, colorUp,
                    minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ,
                    0, 1, 0, packedLight, packedOverlay);
        if (!shouldCull(be, entry, Direction.NORTH))
            drawQuad(vc, mat, normal, spriteNorth, colorNorth,
                    maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ,
                    0, 0, -1, packedLight, packedOverlay);
        if (!shouldCull(be, entry, Direction.SOUTH))
            drawQuad(vc, mat, normal, spriteSouth, colorSouth,
                    minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
                    0, 0, 1, packedLight, packedOverlay);
        if (!shouldCull(be, entry, Direction.WEST))
            drawQuad(vc, mat, normal, spriteWest, colorWest,
                    minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
                    -1, 0, 0, packedLight, packedOverlay);
        if (!shouldCull(be, entry, Direction.EAST))
            drawQuad(vc, mat, normal, spriteEast, colorEast,
                    maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ,
                    1, 0, 0, packedLight, packedOverlay);
    }

    private boolean shouldCull(CutBlockEntity be, CutBlockEntity.CutEntry self, Direction dir) {
        if (be == null || self == null || be.getEntries().isEmpty()) return false;
        int[] b = self.bounds;
        // 隣接面を覆うかは複数小片の合算で判定（ハーフにEIGHT4個等のケース）
        // 16x16グリッドで自己面をカバーするかをチェック
        boolean[][] covered = new boolean[16][16];
        boolean hasAdjacent = false;
        for (CutBlockEntity.CutEntry other : be.getEntries()) {
            if (other == self) continue;
            int[] o = other.bounds;
            boolean adjacent = false;
            switch (dir) {
                case DOWN -> adjacent = b[1] == o[4];
                case UP -> adjacent = b[4] == o[1];
                case NORTH -> adjacent = b[2] == o[5];
                case SOUTH -> adjacent = b[5] == o[2];
                case WEST -> adjacent = b[0] == o[3];
                case EAST -> adjacent = b[3] == o[0];
            }
            if (!adjacent) continue;
            // 同種判定は内部ブロックの実際のブロックで比較。透過でも同ブロックならカリング可
            boolean sameBlock = self.state.getBlock() == other.state.getBlock();
            boolean otherTranslucent = isTranslucent(other.state, be);
            boolean selfTranslucent = isTranslucent(self.state, be);
            boolean otherOpaque = isOpaque(other.state, be);
            boolean canCullThisOther;
            if (otherTranslucent || selfTranslucent) {
                if (sameBlock && otherTranslucent && selfTranslucent) {
                    canCullThisOther = true;
                } else {
                    canCullThisOther = otherOpaque;
                }
            } else {
                canCullThisOther = otherOpaque;
            }
            if (!canCullThisOther) continue;
            hasAdjacent = true;
            // 2軸での重なりをcoveredにマーク
            int minA = 0, maxA = 0, minB = 0, maxB = 0;
            switch (dir) {
                case DOWN, UP -> { minA = Math.max(b[0], o[0]); maxA = Math.min(b[3], o[3]); minB = Math.max(b[2], o[2]); maxB = Math.min(b[5], o[5]); }
                case NORTH, SOUTH -> { minA = Math.max(b[0], o[0]); maxA = Math.min(b[3], o[3]); minB = Math.max(b[1], o[1]); maxB = Math.min(b[4], o[4]); }
                case WEST, EAST -> { minA = Math.max(b[1], o[1]); maxA = Math.min(b[4], o[4]); minB = Math.max(b[2], o[2]); maxB = Math.min(b[5], o[5]); }
            }
            if (minA >= maxA || minB >= maxB) continue;
            for (int a = minA; a < maxA; a++) for (int bb = minB; bb < maxB; bb++) covered[a][bb] = true;
        }
        if (!hasAdjacent) return false;
        // 自己面の全セルが覆われているか
        switch (dir) {
            case DOWN, UP -> {
                for (int x = b[0]; x < b[3]; x++) for (int z = b[2]; z < b[5]; z++) if (!covered[x][z]) return false;
            }
            case NORTH, SOUTH -> {
                for (int x = b[0]; x < b[3]; x++) for (int y = b[1]; y < b[4]; y++) if (!covered[x][y]) return false;
            }
            case WEST, EAST -> {
                for (int y = b[1]; y < b[4]; y++) for (int z = b[2]; z < b[5]; z++) if (!covered[y][z]) return false;
            }
        }
        return true;
    }

    private boolean isTranslucent(BlockState state, CutBlockEntity be) {
        if (state == null) return false;
        try {
            BakedModel m = this.blockRenderer.getBlockModel(state);
            ModelData md = ModelData.EMPTY;
            try { md = m.getModelData(be.getLevel(), be.getBlockPos(), state, ModelData.EMPTY); } catch (Exception e) {}
            RandomSource rand = RandomSource.create(state.getSeed(be.getBlockPos()));
            var types = m.getRenderTypes(state, rand, md);
            for (RenderType rt : types) {
                if (rt == RenderType.translucent()) return true;
            }
        } catch (Exception e) {}
        return false;
    }

    private boolean isOpaque(BlockState state, CutBlockEntity be) {
        if (state == null || state.isAir()) return false;
        // 色付きガラス等の透過はスキップ対象外（同色ガラスはshouldCullで例外扱い）
        if (isTranslucent(state, be)) return false;
        try {
            if (!state.canOcclude()) return false;
        } catch (Exception e) {}
        try {
            // フルキューブ形状かつ固体なら不透明。階段等の非フルはここでfalse
            if (be.getLevel() != null) {
                if (!CutBlockEntity.isFullCubeState(state, be.getLevel(), be.getBlockPos())) return false;
            } else {
                if (!CutBlockEntity.isFullCubeState(state)) return false;
            }
        } catch (Exception e) { return false; }
        return true;
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

    private int getTintForDir(BakedModel model, BlockState state, Direction dir, CutBlockEntity be) {
        try {
            RandomSource rand = RandomSource.create(state.getSeed(be.getBlockPos()));
            ModelData md = ModelData.EMPTY;
            try {
                md = model.getModelData(be.getLevel(), be.getBlockPos(), state, ModelData.EMPTY);
            } catch (Exception e) {}
            var quads = model.getQuads(state, dir, rand, md, null);
            if (quads != null && !quads.isEmpty()) {
                return quads.get(0).getTintIndex();
            }
            var general = model.getQuads(state, null, rand, md, null);
            if (general != null) {
                for (var q : general) {
                    if (q.getDirection() == dir) {
                        return q.getTintIndex();
                    }
                }
            }
        } catch (Exception e) {
        }
        return -1;
    }

    private int getColorForDir(BakedModel model, BlockState state, Direction dir, CutBlockEntity be) {
        int tint = getTintForDir(model, state, dir, be);
        if (tint == -1) return -1;
        try {
            // 汎用的にBlockColors経由で取得。対応していないブロックは-1を返し乗算しない
            return Minecraft.getInstance().getBlockColors().getColor(state, be.getLevel(), be.getBlockPos(), tint);
        } catch (Exception e) {
            return -1;
        }
    }

    private void drawQuad(VertexConsumer vc, Matrix4f mat, Matrix3f normal, TextureAtlasSprite sprite, int tintColor,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float nx, float ny, float nz,
            int packedLight, int packedOverlay) {
        float u1 = sprite.getU0();
        float u2 = sprite.getU1();
        float v1 = sprite.getV0();
        float v2 = sprite.getV1();
        float[] us = new float[4];
        float[] vs = new float[4];
        if (ny != 0) {
            us[0] = lerp(u1, u2, x1);
            vs[0] = lerp(v1, v2, z1);
            us[1] = lerp(u1, u2, x2);
            vs[1] = lerp(v1, v2, z2);
            us[2] = lerp(u1, u2, x3);
            vs[2] = lerp(v1, v2, z3);
            us[3] = lerp(u1, u2, x4);
            vs[3] = lerp(v1, v2, z4);
        } else if (nz != 0) {
            us[0] = lerp(u1, u2, x1);
            vs[0] = lerp(v1, v2, 1 - y1);
            us[1] = lerp(u1, u2, x2);
            vs[1] = lerp(v1, v2, 1 - y2);
            us[2] = lerp(u1, u2, x3);
            vs[2] = lerp(v1, v2, 1 - y3);
            us[3] = lerp(u1, u2, x4);
            vs[3] = lerp(v1, v2, 1 - y4);
        } else {
            us[0] = lerp(u1, u2, z1);
            vs[0] = lerp(v1, v2, 1 - y1);
            us[1] = lerp(u1, u2, z2);
            vs[1] = lerp(v1, v2, 1 - y2);
            us[2] = lerp(u1, u2, z3);
            vs[2] = lerp(v1, v2, 1 - y3);
            us[3] = lerp(u1, u2, z4);
            vs[3] = lerp(v1, v2, 1 - y4);
        }
        float r = 1f, g = 1f, b = 1f;
        if (tintColor != -1) {
            r = ((tintColor >> 16) & 0xFF) / 255f;
            g = ((tintColor >> 8) & 0xFF) / 255f;
            b = (tintColor & 0xFF) / 255f;
        }
        vc.vertex(mat, x1, y1, z1).color(r, g, b, 1f).uv(us[0], vs[0]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, 1f).uv(us[1], vs[1]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x3, y3, z3).color(r, g, b, 1f).uv(us[2], vs[2]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x4, y4, z4).color(r, g, b, 1f).uv(us[3], vs[3]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
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
