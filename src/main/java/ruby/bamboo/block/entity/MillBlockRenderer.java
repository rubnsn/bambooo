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
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.MillBlock;

/**
 * 風車・水車の BlockEntityRenderer (旧 RenderWindmill / RenderWaterwheel /
 * ModelWindmill / ModelWaterwheel の移植)。
 * <p>
 * 旧モデル構成を ModelPart で再現 (テクスチャ 64x64、前提は旧 entitys テクスチャ):
 * <ul>
 * <li>風車: ハブ (0,0: -3,-3,-2,6,6,4) + 軸 (0,12: -2,-2,0,4,4,8) +
 * 羽根4枚 [支柱 (55,0: -1,3,-1,2,30,2) + 帆 (24,0: 1,3,-1,10,30,1, Y傾き-12度)]。
 * 旧 BLADE 可変 (4-8枚) は4枚固定に簡略化</li>
 * <li>水車: 軸 (0,0: -8,-3,-3,14,6,6) + スポーク12組
 * [柱A/B (58,0) + 羽板 (45,10) + 水受け左右 (26,7、X傾き-20度)]</li>
 * </ul>
 * サイズ SIZE 0/1/2 (S/M/L) は描画スケール 0.5/1.0/1.5。M/L は1ブロックを
 * 超えるが、BE 側の {@code getRenderBoundingBox() = INFINITE} +
 * {@code shouldRenderOffScreen() = true} で描画される。
 */
public class MillBlockRenderer implements BlockEntityRenderer<MillBlockEntity> {

    public static final ResourceLocation WINDMILL_TEXTURE = new ResourceLocation(BambooMod.MODID,
            "textures/entity/windmill.png");
    public static final ResourceLocation WINDMILL_CLOTH_TEXTURE = new ResourceLocation(BambooMod.MODID,
            "textures/entity/windmill_cloth.png");
    public static final ResourceLocation WATERWHEEL_TEXTURE = new ResourceLocation(BambooMod.MODID,
            "textures/entity/waterwheel.png");

    /** 風車ハブ。旧 box: texOffs(0,0), addBox(-3,-3,-2,6,6,4) */
    private static final ModelPart HUB = bake(
            CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-3.0F, -3.0F, -2.0F, 6.0F, 6.0F, 4.0F));
    /** 風車の羽根支柱。旧 box1: texOffs(55,0), addBox(-1,3,-1,2,30,2) */
    private static final ModelPart POLE = bake(
            CubeListBuilder.create().texOffs(55, 0)
                    .addBox(-1.0F, 3.0F, -1.0F, 2.0F, 30.0F, 2.0F));
    /** 風車の帆。旧 box4: texOffs(24,0), addBox(1,3,-1,10,30,1)、Y傾き-12度 */
    private static final ModelPart SAIL = bake(
            CubeListBuilder.create().texOffs(24, 0)
                    .addBox(1.0F, 3.0F, -1.0F, 10.0F, 30.0F, 1.0F));
    /** 風車の軸。旧 box8: texOffs(0,12), addBox(-2,-2,0,4,4,8) */
    private static final ModelPart WIND_AXLE = bake(
            CubeListBuilder.create().texOffs(0, 12)
                    .addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 8.0F));

    /** 水車の軸。旧 box: texOffs(0,0), addBox(-8,-3,-3,14,6,6) */
    private static final ModelPart AXLE = bake(
            CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-8.0F, -3.0F, -3.0F, 14.0F, 6.0F, 6.0F));
    /** 水車の柱A。旧 box0: texOffs(58,0), addBox(4,2,-1,1,30,2) */
    private static final ModelPart SPOKE_A = bake(
            CubeListBuilder.create().texOffs(58, 0)
                    .addBox(4.0F, 2.0F, -1.0F, 1.0F, 30.0F, 2.0F));
    /** 水車の柱B。旧 box1: texOffs(58,0), addBox(-4,2,-1,1,30,2) */
    private static final ModelPart SPOKE_B = bake(
            CubeListBuilder.create().texOffs(58, 0)
                    .addBox(-4.0F, 2.0F, -1.0F, 1.0F, 30.0F, 2.0F));
    /** 水車の羽板。旧 box2: texOffs(45,10), addBox(-2,21,-1,5,10,1) */
    private static final ModelPart PADDLE = bake(
            CubeListBuilder.create().texOffs(45, 10)
                    .addBox(-2.0F, 21.0F, -1.0F, 5.0F, 10.0F, 1.0F));
    /** 水車の水受け左。旧 box3: texOffs(26,7), addBox(3,20,-5,1,10,15) */
    private static final ModelPart BUCKET_L = bake(
            CubeListBuilder.create().texOffs(26, 7)
                    .addBox(3.0F, 20.0F, -5.0F, 1.0F, 10.0F, 15.0F));
    /** 水車の水受け右。旧 box4: texOffs(26,7), addBox(-3,20,-5,1,10,15) */
    private static final ModelPart BUCKET_R = bake(
            CubeListBuilder.create().texOffs(26, 7)
                    .addBox(-3.0F, 20.0F, -5.0F, 1.0F, 10.0F, 15.0F));

    /** 帆のY傾き (旧 box4.rotateAngleY = -12度) */
    private static final float SAIL_TILT_Y = (float) Math.toRadians(-12.0D);
    /** 水受けのX傾き (旧 box3/box4.rotateAngleX = 20度)。回転方向に合わせ -20度で保持 */
    private static final int SPOKES = 12;

    /** CubeListBuilder をベイクする。entity テクスチャは 64x32 (要実測) */
    private static ModelPart bake(CubeListBuilder builder) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("part", builder, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("part");
    }

    public MillBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MillBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof MillBlock mill)) {
            return;
        }
        // partialTicks補間で滑らかな回転に。roll は 360→0 で巻き戻るため、
        // 差分を wrapDegrees で -180〜180 に正規化してから補間する
        // (素直な lerp だと巻き戻し時に359→1を逆方向へ大きく sweep してカクつく)
        float delta = Mth.wrapDegrees(be.roll - be.prevRoll);
        float angle = be.prevRoll + delta * partialTick;

        int light = be.getLevel() != null
                ? LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())
                : LightTexture.FULL_BRIGHT;

        float scale = 0.5F * (state.getValue(MillBlock.SIZE) + 1);
        // 車輪の軸向きは180度反転させる (ユーザー確認。帆/羽根面の表裏が逆だったため)
        float yaw = state.getValue(MillBlock.FACING).toYRot() + 180.0F;

        if (mill.getType().waterwheel) {
            // 水車モデルは軸がX方向のため、車輪面を設置者と正対させるにはYに+90度が必要
            // (旧 RenderWaterwheel の dir*90 前の +1/-1 補正相当。無しだと真横から見る形になる)
            renderWaterwheel(poseStack, buffer, light, packedOverlay, yaw + 90.0F, scale, angle);
        } else {
            renderWindmill(poseStack, buffer, light, packedOverlay, yaw, scale, angle, mill.getType().cloth);
        }
    }

    /**
     * 風車描画。旧 RenderWindmill 相当: Y回転 (向き) → Z回転 (roll)。
     * 羽根は4枚固定 (旧 BLADE 0-4 → 4-8枚から簡略化)。
     */
    private static void renderWindmill(PoseStack poseStack, MultiBufferSource buffer, int light, int packedOverlay,
            float yaw, float scale, float angle, boolean cloth) {
        VertexConsumer vc = buffer.getBuffer(
                RenderType.entityCutout(cloth ? WINDMILL_CLOTH_TEXTURE : WINDMILL_TEXTURE));

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angle));

        WIND_AXLE.render(poseStack, vc, light, packedOverlay);
        HUB.render(poseStack, vc, light, packedOverlay);

        POLE.xRot = 0.0F;
        POLE.yRot = 0.0F;
        SAIL.xRot = 0.0F;
        SAIL.yRot = SAIL_TILT_Y;
        for (int i = 0; i < 4; i++) {
            float blade = (float) (Math.PI * 2.0D * i / 4.0D);
            POLE.zRot = blade;
            SAIL.zRot = blade;
            POLE.render(poseStack, vc, light, packedOverlay);
            SAIL.render(poseStack, vc, light, packedOverlay);
        }
        POLE.zRot = 0.0F;
        SAIL.zRot = 0.0F;
        SAIL.yRot = 0.0F;

        poseStack.popPose();
    }

    /**
     * 水車描画。旧 RenderWaterwheel 相当: Y回転 (向き) → X回転 (roll)。
     * スポーク12組を30度刻みで、水受けは -20度ずらす (旧 baqet 配列相当)。
     */
    private static void renderWaterwheel(PoseStack poseStack, MultiBufferSource buffer, int light, int packedOverlay,
            float yaw, float scale, float angle) {
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(WATERWHEEL_TEXTURE));

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));

        AXLE.render(poseStack, vc, light, packedOverlay);

        SPOKE_A.yRot = 0.0F;
        SPOKE_A.zRot = 0.0F;
        SPOKE_B.yRot = 0.0F;
        SPOKE_B.zRot = 0.0F;
        PADDLE.yRot = 0.0F;
        PADDLE.zRot = 0.0F;
        BUCKET_L.yRot = 0.0F;
        BUCKET_L.zRot = 0.0F;
        BUCKET_R.yRot = 0.0F;
        BUCKET_R.zRot = 0.0F;
        for (int i = 0; i < SPOKES; i++) {
            float rot = (float) Math.toRadians(i * 30.0D);
            float bucket = (float) Math.toRadians(i * 30.0D - 20.0D);
            SPOKE_A.xRot = rot;
            SPOKE_B.xRot = rot;
            PADDLE.xRot = rot;
            BUCKET_L.xRot = bucket;
            BUCKET_R.xRot = bucket;
            SPOKE_A.render(poseStack, vc, light, packedOverlay);
            SPOKE_B.render(poseStack, vc, light, packedOverlay);
            PADDLE.render(poseStack, vc, light, packedOverlay);
            BUCKET_L.render(poseStack, vc, light, packedOverlay);
            BUCKET_R.render(poseStack, vc, light, packedOverlay);
        }
        SPOKE_A.xRot = 0.0F;
        SPOKE_B.xRot = 0.0F;
        PADDLE.xRot = 0.0F;
        BUCKET_L.xRot = 0.0F;
        BUCKET_R.xRot = 0.0F;

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(MillBlockEntity be) {
        // サイズ M/L は1ブロックからはみ出すためフラスタムカリングを無効化
        return true;
    }
}
