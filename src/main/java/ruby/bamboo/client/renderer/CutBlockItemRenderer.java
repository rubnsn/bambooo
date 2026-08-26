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
            INSTANCE = new CutBlockItemRenderer();
        }
        return INSTANCE;
    }

    private CutBlockItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
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
        byte yLevel = data.yLevel();
        byte hLevel = data.hLevel();
        // インベントリでは向きが無いためデフォルト NORTH で Bounds を求める
        // （NORTH: X= hSize, 仕様の基準向き）
        Direction facing = Direction.NORTH;
        int[] bounds = computeBoundsForItem(yLevel, hLevel, facing);
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

    private static int[] computeBoundsForItem(byte yLevel, byte hLevel, Direction facing) {
        int ySize = CutBlockEntity.levelToSize(yLevel);
        int hSize = CutBlockEntity.levelToSize(hLevel);
        int minX = 0, minY = 0, minZ = 0;
        int maxX = 16, maxY = ySize, maxZ = 16;
        if (hSize != 16) {
            switch (facing) {
                case NORTH -> {
                    maxX = hSize;
                    maxZ = 16;
                }
                case SOUTH -> {
                    minX = 16 - hSize;
                    maxX = 16;
                    maxZ = 16;
                }
                case EAST -> {
                    maxX = 16;
                    maxZ = hSize;
                }
                case WEST -> {
                    minZ = 16 - hSize;
                    maxX = 16;
                    maxZ = 16;
                }
                default -> {
                    maxX = hSize;
                    maxZ = 16;
                }
            }
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
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

        drawQuad(vc, mat, normal, spriteDown,
                minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                0, -1, 0, packedLight, packedOverlay);
        drawQuad(vc, mat, normal, spriteUp,
                minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ,
                0, 1, 0, packedLight, packedOverlay);
        drawQuad(vc, mat, normal, spriteNorth,
                maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ,
                0, 0, -1, packedLight, packedOverlay);
        drawQuad(vc, mat, normal, spriteSouth,
                minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
                0, 0, 1, packedLight, packedOverlay);
        drawQuad(vc, mat, normal, spriteWest,
                minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
                -1, 0, 0, packedLight, packedOverlay);
        drawQuad(vc, mat, normal, spriteEast,
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

    private void drawQuad(VertexConsumer vc, Matrix4f mat, Matrix3f normal, TextureAtlasSprite sprite,
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
        vc.vertex(mat, x1, y1, z1).color(1f, 1f, 1f, 1f).uv(us[0], vs[0]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x2, y2, z2).color(1f, 1f, 1f, 1f).uv(us[1], vs[1]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x3, y3, z3).color(1f, 1f, 1f, 1f).uv(us[2], vs[2]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x4, y4, z4).color(1f, 1f, 1f, 1f).uv(us[3], vs[3]).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
