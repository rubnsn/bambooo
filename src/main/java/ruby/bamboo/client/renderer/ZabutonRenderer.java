package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.ZabutonColor;
import ruby.bamboo.entity.ZabutonEntity;

/**
 * 座布団レンダラ (旧 RenderZabuton の1.20.1移植)。
 * <p>
 * 旧 ModelZabuton (14x2x14 の箱 + 模様プレート) 相当を ModelPart で再現。
 * 本体は EntityData の色で tint、白い中敷きプレートを重ねる。
 * <p>
 * 1.21: {@code ResourceLocation.fromNamespaceAndPath}、
 * {@code ModelPart.render} の色指定は ARGB int (§11)。
 */
public class ZabutonRenderer extends EntityRenderer<ZabutonEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID,
            "textures/entity/zabuton.png");

    private final ModelPart box;
    private final ModelPart plate;

    public ZabutonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        ModelPart root = LayerDefinition.create(createMesh(), 64, 32).bakeRoot();
        this.box = root.getChild("box");
        this.plate = root.getChild("plate");
        this.shadowRadius = 0.25F;
    }

    private static MeshDefinition createMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("box",
                CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, 0.0F, -7.0F, 14.0F, 2.0F, 14.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("plate",
                CubeListBuilder.create().texOffs(0, 17).addBox(-5.0F, 1.0F, -5.0F, 10.0F, 1.0F, 10.0F),
                PartPose.ZERO);
        return mesh;
    }

    @Override
    public void render(ZabutonEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // 旧 RenderZabuton: 180 - yaw でY回転 (モデル単位は 1/16 ブロック)。
        // 投擲中の高速回転はフレーム補間で滑らかにする
        float yaw = entity.yRotO + Mth.wrapDegrees(entity.getYRot() - entity.yRotO) * partialTicks;
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        ZabutonColor color = entity.getColor();
        // 1.21 は ARGB int (alpha 固定 FF、rgb は ZabutonColor の 0xRRGGBB)
        int tint = 0xFF000000 | (color.rgb & 0x00FFFFFF);
        this.box.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, tint);
        this.plate.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ZabutonEntity entity) {
        return TEXTURE;
    }
}
