package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ruby.bamboo.block.entity.CutBlockEntity;

/**
 * カットブロックのインベントリ用 BEWLR。
 * ItemStackの CutState/YLevel/HLevel を読み取り、原料ブロックのテクスチャを
 * Boundsサイズにクリップして描画する。BEが無いインベントリでも
 * 「原料 + 現在形状」が視覚的に分かるようにする。
 */
public class CutBlockItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static CutBlockItemRenderer INSTANCE;

    public static CutBlockItemRenderer getInstance() {
        if (INSTANCE == null) {
            try {
                INSTANCE = new CutBlockItemRenderer();
            } catch (Exception e) {
                // Minecraft未初期化時のフォールバック (サーバ側や早期ロードでのNPE防止)
                try {
                    INSTANCE = new CutBlockItemRenderer(true);
                } catch (Exception e2) {
                    return null;
                }
            }
        }
        return INSTANCE;
    }

    private CutBlockItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    // フォールバック用 (dispatcher/modelsがnullでもsuper呼び出しを通す)
    private CutBlockItemRenderer(boolean dummy) {
        super(null, null);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
        BlockState cutState = data.state();
        if (cutState == null || cutState.isAir() || cutState.is(Blocks.AIR)) {
            // 空のcut_blockはダミーなので何も描かない（通常入手不能）
            return;
        }
        byte xLevel = data.xLevel();
        byte yLevel = data.yLevel();
        byte zLevel = data.zLevel();
        int[] bounds = computeBoundsForItem(xLevel, yLevel, zLevel);
        float minX = bounds[0] / 16f;
        float minY = bounds[1] / 16f;
        float minZ = bounds[2] / 16f;
        float maxX = bounds[3] / 16f;
        float maxY = bounds[4] / 16f;
        float maxZ = bounds[5] / 16f;

        poseStack.pushPose();
        // ItemDisplayContext に応じた変形
        applyTransform(poseStack, context);

        // フルサイズなら通常tesselate、部分なら6面Quad再生成
        if (minX == 0 && minY == 0 && minZ == 0 && maxX == 1 && maxY == 1 && maxZ == 1) {
            renderFull(cutState, poseStack, buffer, packedLight, packedOverlay);
        } else {
            renderClipped(cutState, minX, minY, minZ, maxX, maxY, maxZ, poseStack, buffer, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private static int[] computeBoundsForItem(byte xLevel, byte yLevel, byte zLevel) {
        int xSize = CutBlockEntity.levelToSize(xLevel);
        int ySize = CutBlockEntity.levelToSize(yLevel);
        int zSize = CutBlockEntity.levelToSize(zLevel);
        // 残っている実体部分を元にセンタリング: 各軸で (16 - size)/2 をオフセット
        int xOff = (16 - xSize) / 2;
        int yOff = (16 - ySize) / 2;
        int zOff = (16 - zSize) / 2;
        return new int[]{xOff, yOff, zOff, xOff + xSize, yOff + ySize, zOff + zSize};
    }

    private void applyTransform(PoseStack poseStack, ItemDisplayContext context) {
        // GUI/インベントリではブロックアイテムらしく 30度傾けて中央配置
        // 他コンテキストでは必要最小限の変形
        switch (context) {
            case GUI, FIXED -> {
                poseStack.translate(0.5D, 0.5D, 0.5D);
                poseStack.mulPose(Axis.YP.rotationDegrees(45));
                poseStack.mulPose(Axis.XP.rotationDegrees(30));
                poseStack.translate(-0.5D, -0.5D, -0.5D);
                // GUIでは少し小さめに
                poseStack.scale(0.95f, 0.95f, 0.95f);
                poseStack.translate(0.025f, 0.025f, 0.025f);
            }
            case GROUND -> {
                poseStack.translate(0.5D, 0.25D, 0.5D);
                poseStack.scale(0.5f, 0.5f, 0.5f);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                poseStack.translate(0.5D, 0.5D, 0.5D);
                poseStack.mulPose(Axis.YP.rotationDegrees(45));
                poseStack.translate(-0.5D, -0.5D, -0.5D);
                poseStack.scale(0.5f, 0.5f, 0.5f);
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                poseStack.translate(0.5D, 0.5D, 0.5D);
                poseStack.mulPose(Axis.YP.rotationDegrees(45));
                poseStack.translate(-0.5D, -0.5D, -0.5D);
            }
            default -> {
                poseStack.translate(0.5D, 0.5D, 0.5D);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
            }
        }
    }

    private void renderFull(BlockState cutState, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(cutState);
        ModelData md = ModelData.EMPTY;
        try {
            md = model.getModelData(null, BlockPos.ZERO, cutState, ModelData.EMPTY);
        } catch (Exception e) {
        }
        RandomSource rand = RandomSource.create(cutState.getSeed(BlockPos.ZERO));
        for (RenderType rt : model.getRenderTypes(cutState, rand, md)) {
            VertexConsumer vc = buffer.getBuffer(rt);
            try {
                dispatcher.getModelRenderer().tesselateBlock(
                        null, model, cutState, BlockPos.ZERO,
                        poseStack, vc, false, rand, cutState.getSeed(BlockPos.ZERO), packedOverlay, md, rt);
            } catch (Exception e) {
            }
        }
    }

    private void renderClipped(BlockState cutState,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(cutState);
        TextureAtlasSprite spriteUp = getSpriteForDir(model, cutState, Direction.UP);
        TextureAtlasSprite spriteDown = getSpriteForDir(model, cutState, Direction.DOWN);
        TextureAtlasSprite spriteNorth = getSpriteForDir(model, cutState, Direction.NORTH);
        TextureAtlasSprite spriteSouth = getSpriteForDir(model, cutState, Direction.SOUTH);
        TextureAtlasSprite spriteWest = getSpriteForDir(model, cutState, Direction.WEST);
        TextureAtlasSprite spriteEast = getSpriteForDir(model, cutState, Direction.EAST);
        TextureAtlasSprite fallback = model.getParticleIcon(ModelData.EMPTY);
        if (spriteUp == null) spriteUp = fallback;
        if (spriteDown == null) spriteDown = fallback;
        if (spriteNorth == null) spriteNorth = fallback;
        if (spriteSouth == null) spriteSouth = fallback;
        if (spriteWest == null) spriteWest = fallback;
        if (spriteEast == null) spriteEast = fallback;

        int colorDown = getColorForDir(model, cutState, Direction.DOWN);
        int colorUp = getColorForDir(model, cutState, Direction.UP);
        int colorNorth = getColorForDir(model, cutState, Direction.NORTH);
        int colorSouth = getColorForDir(model, cutState, Direction.SOUTH);
        int colorWest = getColorForDir(model, cutState, Direction.WEST);
        int colorEast = getColorForDir(model, cutState, Direction.EAST);

        RenderType rt = RenderType.cutout();
        try {
            RandomSource rand = RandomSource.create();
            ModelData md = ModelData.EMPTY;
            try {
                md = model.getModelData(null, BlockPos.ZERO, cutState, ModelData.EMPTY);
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

        VertexConsumer vc = buffer.getBuffer(rt);
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

    private TextureAtlasSprite getSpriteForDir(BakedModel model, BlockState state, Direction dir) {
        try {
            RandomSource rand = RandomSource.create(state.getSeed(BlockPos.ZERO));
            ModelData md = ModelData.EMPTY;
            try {
                md = model.getModelData(null, BlockPos.ZERO, state, ModelData.EMPTY);
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

    private int getTintForDir(BakedModel model, BlockState state, Direction dir) {
        try {
            RandomSource rand = RandomSource.create(state.getSeed(BlockPos.ZERO));
            ModelData md = ModelData.EMPTY;
            try {
                md = model.getModelData(null, BlockPos.ZERO, state, ModelData.EMPTY);
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

    private int getColorForDir(BakedModel model, BlockState state, Direction dir) {
        int tint = getTintForDir(model, state, dir);
        if (tint == -1) return -1;
        try {
            // 汎用的にBlockColors経由。対応していないブロックは-1で乗算しない
            return Minecraft.getInstance().getBlockColors().getColor(state, null, null, tint);
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
}
