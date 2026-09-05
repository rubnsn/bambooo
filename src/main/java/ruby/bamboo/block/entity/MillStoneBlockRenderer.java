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

/**
 * 石臼の BlockEntityRenderer (旧 RenderMillStone / ModelMillStone の移植)。
 * <p>
 * 旧モデル構成を ModelPart で再現:
 * <ul>
 * <li>下半分 16x8x16 (テクスチャUV 0,25): <b>Y軸まわりに roll 角度で回転する石車</b></li>
 * <li>上半分 16x8x16 (テクスチャUV 0,0): 固定</li>
 * </ul>
 * テクスチャ: textures/entity/millstone.png (64x64、旧 entitys/millstone.png)
 * <p>
 * インベントリアイコンはフラット(1枚絵: item/generated + item/millstone.png)のため
 * BEWLR は不要。
 */
public class MillStoneBlockRenderer implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<MillStoneBlockEntity> {

    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "textures/entity/millstone.png");

    /** 下半分 (回転する石車)。旧 bottm: texOffs(0,25), addBox(-8,0,-8,16,8,16), mirror */
    private static final ModelPart BOTTOM = bake(
            CubeListBuilder.create().texOffs(0, 25).mirror()
                    .addBox(-8.0F, 0.0F, -8.0F, 16.0F, 8.0F, 16.0F));

    /** 上半分 (固定)。旧 top: texOffs(0,0), addBox(-8,-8,-8,16,8,16), mirror */
    private static final ModelPart TOP = bake(
            CubeListBuilder.create().texOffs(0, 0).mirror()
                    .addBox(-8.0F, -8.0F, -8.0F, 16.0F, 8.0F, 16.0F));

    /** CubeListBuilder をテクスチャサイズ64x64でベイクする */
    private static ModelPart bake(CubeListBuilder builder) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("part", builder, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("part");
    }

    public MillStoneBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MillStoneBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // partialTicks補間で滑らかな回転に
        float angle = be.prevRoll + (be.roll - be.prevRoll) * partialTick;

        int light = be.getLevel() != null
                ? LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())
                : LightTexture.FULL_BRIGHT;

        VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(TEXTURE));

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);

        // 上半分 (固定)
        TOP.render(poseStack, vc, light, packedOverlay);

        // 下半分 (roll角でY回転)
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        BOTTOM.render(poseStack, vc, light, packedOverlay);
        poseStack.popPose();

        poseStack.popPose();
    }
}