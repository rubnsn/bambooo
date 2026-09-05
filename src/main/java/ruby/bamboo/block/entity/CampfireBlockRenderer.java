package ruby.bamboo.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.entity.CampfireBlockEntity.BakeType;

/**
 * 囲炉裏の BlockEntityRenderer (旧 RenderCampfire / ModelCampfire の移植)。
 * <p>
 * 旧モデル構成を ModelPart で再現:
 * <ul>
 * <li>wood: 2×2×8 の薪棒を7本、rotateY=360/7*i度、rotateX=交互10°/20°で組み合わせた焚き火台 (常時描画)</li>
 * <li>ATHER: rod×2(縦棒)+ potRod + pot(10×8×10 鍋)</li>
 * <li>MEAT: meat(10×6×6 肉塊)+ bone(16×1×1 串)。meat.rotateAngleX=meatroll 度で回転アニメ</li>
 * <li>FISH: fish(3×13×0) を45°刻み4本 (rotateY=90*i+45°)</li>
 * </ul>
 * テクスチャ: textures/entity/campfire.png (旧 entitys/campfire.png)
 * <p>
 * インベントリアイコンはフラット(1枚絵: item/generated + item/campfire.png)のため
 * BEWLR は不要。
 */
public class CampfireBlockRenderer implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<CampfireBlockEntity> {

    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "textures/entity/campfire.png");

    /** シングルトン (BEWLR から renderWood を呼ぶため) */
    private static CampfireBlockRenderer instance;

    public static CampfireBlockRenderer getInstance() {
        return instance;
    }

    /** 薪棒 (旧 wood: texOffs(44,22), addBox(-1,0,-1,2,2,8)) */
    private static final ModelPart WOOD = bake(
            CubeListBuilder.create().texOffs(44, 22)
                    .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 8.0F));

    /** 縦棒 (旧 rod: texOffs(0,0), addBox(-7,0,-1,1,15,2)) */
    private static final ModelPart ROD = bake(
            CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-7.0F, 0.0F, -1.0F, 1.0F, 15.0F, 2.0F));

    /** 縦棒2 (旧 rod2: rod をY180°回転) */
    private static final ModelPart ROD2 = bake(
            CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-7.0F, 0.0F, -1.0F, 1.0F, 15.0F, 2.0F));

    /** 鍋吊り棒 (旧 potRod: texOffs(0,30), addBox(-8,9,-0.5,16,1,1)) */
    private static final ModelPart POT_ROD = bake(
            CubeListBuilder.create().texOffs(0, 30)
                    .addBox(-8.0F, 9.0F, -0.5F, 16.0F, 1.0F, 1.0F));

    /** 鍋 (旧 pot: texOffs(6,0), addBox(-5,4,-5,10,8,10)) */
    private static final ModelPart POT = bake(
            CubeListBuilder.create().texOffs(6, 0)
                    .addBox(-5.0F, 4.0F, -5.0F, 10.0F, 8.0F, 10.0F));

    /** 肉 (旧 meat: texOffs(8,18), addBox(-5,-3,-3,10,6,6)) */
    private static final ModelPart MEAT = bake(
            CubeListBuilder.create().texOffs(8, 18)
                    .addBox(-5.0F, -3.0F, -3.0F, 10.0F, 6.0F, 6.0F));

    /** 串 (旧 bone: texOffs(0,30), addBox(-8,-0.5,-0.5,16,1,1)) */
    private static final ModelPart BONE = bake(
            CubeListBuilder.create().texOffs(0, 30)
                    .addBox(-8.0F, -0.5F, -0.5F, 16.0F, 1.0F, 1.0F));

    /** 魚 (旧 fish: texOffs(0,17), addBox(-1,-12,5,3,13,0)) */
    private static final ModelPart FISH = bake(
            CubeListBuilder.create().texOffs(0, 17)
                    .addBox(-1.0F, -12.0F, 5.0F, 3.0F, 13.0F, 0.0F));

    /** 薪棒の回転角テーブル (旧 rotY/rotX) */
    private static final float[] WOOD_ROT_Y = new float[7];
    private static final float[] WOOD_ROT_X = new float[7];

    static {
        for (int i = 0; i < 7; i++) {
            WOOD_ROT_Y[i] = (float) (Math.PI * 360 / 7 * i) / 180;
            WOOD_ROT_X[i] = (i % 2 == 0) ? (float) (Math.PI * 10) / 180 : (float) (Math.PI * 20) / 180;
        }
    }

    /**
     * CubeListBuilder をテクスチャサイズ64x32でベイクする。
     * 旧 ModelBase のデフォルトテクスチャサイズ(64x32)に一致させ、campfire.png(64x32)の
     * UV座標を正しくサンプリングする。テクスチャサイズを64x64にすると V 座標が2倍になり、
     * ワールド表示(entityCutout)では透明、手持ち(entitySolid)では白く表示されてしまう。
     */
    private static ModelPart bake(CubeListBuilder builder) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("part", builder, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("part");
    }

    public CampfireBlockRenderer(BlockEntityRendererProvider.Context context) {
        instance = this;
    }

    @Override
    public void render(CampfireBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        int light = be.getLevel() != null
                ? LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())
                : LightTexture.FULL_BRIGHT;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(TEXTURE));

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        // 旧 getRotate: FACING別テーブル (NORTH=0/EAST=270/SOUTH=180/WEST=90)
        poseStack.mulPose(Axis.YP.rotationDegrees(be.getRotate()));

        // 薪台 (常時描画)
        renderWood(poseStack, vc, light, packedOverlay);

        // BakeType 別描画
        switch (be.getBakeType()) {
            case ATHER:
                renderRods(poseStack, vc, light, packedOverlay);
                renderPot(poseStack, vc, light, packedOverlay);
                break;
            case FISH:
                renderFish(poseStack, vc, light, packedOverlay);
                break;
            case MEAT:
                renderRods(poseStack, vc, light, packedOverlay);
                renderMeat(be.getMeatroll(), poseStack, vc, light, packedOverlay);
                break;
            case NONE:
            default:
                break;
        }

        poseStack.popPose();
    }

    /** 薪台 (旧 renderWood 相当) */
    public void renderWood(PoseStack poseStack, VertexConsumer vc, int light, int overlay) {
        for (int i = 0; i < 7; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0D, 1.0D / 16.0D, 0.0D);
            poseStack.mulPose(Axis.YP.rotation(WOOD_ROT_Y[i]));
            poseStack.mulPose(Axis.XP.rotation(WOOD_ROT_X[i]));
            WOOD.render(poseStack, vc, light, overlay);
            poseStack.popPose();
        }
    }

    /** 縦棒×2 (旧 renderRods 相当) */
    private void renderRods(PoseStack poseStack, VertexConsumer vc, int light, int overlay) {
        ROD.render(poseStack, vc, light, overlay);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        ROD2.render(poseStack, vc, light, overlay);
        poseStack.popPose();
    }

    /** 鍋 (旧 renderPot 相当) */
    private void renderPot(PoseStack poseStack, VertexConsumer vc, int light, int overlay) {
        POT_ROD.render(poseStack, vc, light, overlay);
        POT.render(poseStack, vc, light, overlay);
    }

    /** 肉+串 (旧 renderMeat 相当) */
    private void renderMeat(int roll, PoseStack poseStack, VertexConsumer vc, int light, int overlay) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 11.0D / 16.0D, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(roll));
        MEAT.render(poseStack, vc, light, overlay);
        BONE.render(poseStack, vc, light, overlay);
        poseStack.popPose();
    }

    /** 魚×4 (旧 renderFish 相当) */
    private void renderFish(PoseStack poseStack, VertexConsumer vc, int light, int overlay) {
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0D, 10.0D / 16.0D, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(90 * i + 45));
            poseStack.mulPose(Axis.XP.rotationDegrees(-20));
            FISH.render(poseStack, vc, light, overlay);
            poseStack.popPose();
        }
    }
}