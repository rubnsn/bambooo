package ruby.bamboo.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;

/**
 * ガチャポンのフルBER (docs/port-spec-gacha.md §3)。
 * <p>
 * 本物のガチャマシン風の2ブロック背丈: 台座 + 赤ボディ (コイン投入口・ツマミ・取出口) +
 * 白カラー + 透明ガチャ球 (単一箱・カプセル5個) + 赤蓋。LOWER 側の BE から2マス分まとめて描画する。
 * {@link #renderMachine} は GUI 内モデル用に Screen からも呼ばれる共用部。
 * <p>
 * 注意: 積み重ねる箱の境界面は完全同一平面にしないこと (Zファイティングで内側がちらつく)。
 * ガチャ球は3段重ねをやめ単一箱にし、接合部は 0.02 だけ食い込ませている。
 */
public class GachaBlockRenderer implements BlockEntityRenderer<GachaBlockEntity> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BambooMod.MODID, "textures/entity/gacha.png");
    /** 青バリエーション用テクスチャ (赤ボディ・蓋・扉のみ青化) */
    public static final ResourceLocation TEXTURE_BLUE = ResourceLocation.fromNamespaceAndPath(
            BambooMod.MODID, "textures/entity/gacha_blue.png");

    /** 境界面の食い込ませ量 (同一平面回避用) */
    private static final float EPS = 0.02F;

    // ===== 台座・ボディ (y0-17) =====
    /** 台座 (dark): 14x3x14 */
    private static final ModelPart BASE = bake(
            CubeListBuilder.create().texOffs(0, 26).addBox(-7.0F, 0.0F, -7.0F, 14.0F, 3.0F, 14.0F));
    /** 赤ボディ: 12x12x12 (底面は台座に食い込ませる) */
    private static final ModelPart BODY = bake(
            CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, 3.0F - EPS, -6.0F, 12.0F, 12.0F, 12.0F));
    /** 白カラー: 13x2x13 (底面はボディに食い込ませる) */
    private static final ModelPart COLLAR = bake(
            CubeListBuilder.create().texOffs(0, 46).addBox(-6.5F, 15.0F - EPS, -6.5F, 13.0F, 2.0F, 13.0F));

    // ===== 前面 (+z・ボディ表面 z=6。裏面の同一平面回避で +EPS 浮かせる) =====
    // 右上=コイン投入口、左=ツマミ、下=大型排出口の3分割レイアウト。
    // ボディ前面は x -6..6 / y 3..15。
    /** コイン投入口の銀プレート: 3.5x3.5 (右上・x1.5-5.0/y10.5-14.0) */
    private static final ModelPart COIN_PLATE = bake(
            CubeListBuilder.create().texOffs(0, 62).addBox(1.5F, 10.5F, 6.0F + EPS, 3.5F, 3.5F, 0.5F));
    /** コインスロット (暗スリット・プレートに半埋め) */
    private static final ModelPart COIN_SLOT = bake(
            CubeListBuilder.create().texOffs(0, 26).addBox(2.65F, 11.5F, 6.42F, 1.2F, 1.8F, 0.2F));
    /** ツマミの軸: 2.2x2.2 (プレート左隣・中心x-2.6/y11.7) */
    private static final ModelPart KNOB_AXLE = bake(
            CubeListBuilder.create().texOffs(0, 66).addBox(-3.7F, 10.6F, 6.0F + EPS, 2.2F, 2.2F, 1.0F));
    /** ツマミのハンドル: 5.6x1.2 (Z軸回転でひねる) */
    private static final ModelPart KNOB_HANDLE = bake(
            CubeListBuilder.create().texOffs(0, 70).addBox(-5.4F, 11.1F, 7.04F, 5.6F, 1.2F, 0.8F));
    /** 取出口の暗枠 (大型・x-4.3-4.3/y3.4-6.8) */
    private static final ModelPart DISPENSER = bake(
            CubeListBuilder.create().texOffs(0, 26).addBox(-4.3F, 3.4F, 6.0F + EPS, 8.6F, 3.4F, 0.2F));
    /** 取出口のフラップ扉 (閉状態・大型) */
    private static final ModelPart FLAP = bake(
            CubeListBuilder.create().texOffs(0, 74).addBox(-3.8F, 3.7F, 6.24F, 7.6F, 2.8F, 0.25F));

    // ===== ガチャ球 (透明・天面/底面なしの4枚パネル。上下はカラー/蓋に埋める) =====
    // 箱だと底面の半透明quadがカラー天面と重なって衝突して見えるため、側面4枚のみ。
    // パネル端の角は 0.02 の隙間で突き合わせ (同一平面にしない)。
    /** 前面パネル: 12x9 */
    private static final ModelPart GLASS_FRONT = bake(
            CubeListBuilder.create().texOffs(0, 78).addBox(-6.0F, 16.5F, 5.7F, 12.0F, 9.0F, 0.3F));
    /** 背面パネル: 12x9 */
    private static final ModelPart GLASS_BACK = bake(
            CubeListBuilder.create().texOffs(0, 78).addBox(-6.0F, 16.5F, -6.0F, 12.0F, 9.0F, 0.3F));
    /** 左パネル */
    private static final ModelPart GLASS_LEFT = bake(
            CubeListBuilder.create().texOffs(0, 78).addBox(-6.0F, 16.5F, -5.68F, 0.3F, 9.0F, 11.36F));
    /** 右パネル */
    private static final ModelPart GLASS_RIGHT = bake(
            CubeListBuilder.create().texOffs(0, 78).addBox(5.7F, 16.5F, -5.68F, 0.3F, 9.0F, 11.36F));

    // ===== 赤蓋 (y25-31。ガラス上端を覆い隠す) =====
    private static final ModelPart LID = bake(
            CubeListBuilder.create().texOffs(0, 0).addBox(-6.5F, 25.0F, -6.5F, 13.0F, 2.0F, 13.0F));
    private static final ModelPart LID_TOP = bake(
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, 27.0F - EPS, -4.5F, 9.0F, 2.0F, 8.0F));
    /** 蓋つまみ */
    private static final ModelPart FINIAL = bake(
            CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, 29.0F - EPS, -1.5F, 3.0F, 2.0F, 3.0F));

    /** 四角カプセル下半分 (白) */
    private static final ModelPart CAPSULE_LO = bake(
            CubeListBuilder.create().texOffs(0, 46).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F));
    /** 四角カプセル上半分 (レアリティ色) */
    private static final ModelPart CAPSULE_HI = bake(
            CubeListBuilder.create().texOffs(0, 46).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 2.0F, 4.0F));

    /** ツマミ回転中心 (プレート左隣・中心x-2.6/y11.7) */
    private static final float KNOB_X = -2.6F;
    private static final float KNOB_Y = 11.7F;
    private static final float KNOB_Z = 7.4F;

    /** ガチャ球内のリングカプセル4色 (パステル) */
    private static final float[][] PASTEL = {
            { 1.0F, 0.55F, 0.65F },
            { 1.0F, 0.88F, 0.35F },
            { 0.45F, 0.75F, 1.0F },
            { 0.55F, 1.0F, 0.55F },
    };

    private final BlockEntityRendererProvider.Context ctx;

    public GachaBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        this.ctx = ctx;
    }

    private static ModelPart bake(CubeListBuilder cubes) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("box", cubes, net.minecraft.client.model.geom.PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128).bakeRoot().getChild("box");
    }

    @Override
    public void render(GachaBlockEntity be, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        pose.pushPose();
        // ブロック中央・底面基準 (LOWER 側から2マス分描画)
        pose.translate(0.5, 0.0, 0.5);
        float facing = be.getBlockState().getValue(ruby.bamboo.block.GachaBlock.FACING).toYRot();
        // モデル前面は +z (南) 造形。FACING へそのまま向ける (-facing。+180の余分で裏返っていた)
        pose.mulPose(Axis.YP.rotationDegrees(-facing));
        // 青バリエーションはテクスチャのみ差し替え (BE・テーブルは赤と共用)
        ResourceLocation tex = be.getBlockState()
                .is(ruby.bamboo.core.init.BambooBlocks.GACHA_BLUE.get()) ? TEXTURE_BLUE
                        : TEXTURE;
        renderMachine(tex, pose, buffers, packedLight, packedOverlay,
                net.minecraft.util.Mth.lerp(partialTicks, be.prevRotor, be.rotor),
                net.minecraft.util.Mth.lerp(partialTicks, be.prevLever, be.lever),
                0xF2F2F2, false, 1.0F);
        pose.popPose();
    }

    /**
     * 本体描画 (ワールド用)。BE から呼ばれる。カリング有効の通常描画。
     *
     * @param rotorDeg 回転台の角度
     * @param leverDeg ツマミのひねり角 (0=待機)
     * @param capsuleColor 中央カプセルの色 (RGB)
     * @param capsuleOpen 中央カプセル開封済みか (上半分を跳ね上げ)
     * @param scale 全体スケール
     */
    public static void renderMachine(ResourceLocation texture, PoseStack pose,
            MultiBufferSource buffers, int packedLight,
            int packedOverlay, float rotorDeg, float leverDeg, int capsuleColor,
            boolean capsuleOpen, float scale) {
        renderAssembled(pose, buffers,
                buffers.getBuffer(RenderType.entityCutout(texture)),
                packedLight, packedOverlay, rotorDeg, leverDeg, capsuleColor, capsuleOpen, scale);
    }

    /**
     * 本体描画 (GUI用)。ワールドとは独立した組み立てで、反転行列でも確実に
     * 表示されるようカリングなし。見た目の部品構成はワールドと同一。
     * 将来のGUI演出 (排出アニメ等) はこちらに足す。
     */
    public static void renderMachineGui(PoseStack pose, MultiBufferSource buffers, int packedLight,
            int packedOverlay, float rotorDeg, float leverDeg, int capsuleColor,
            boolean capsuleOpen, float scale) {
        renderAssembled(pose, buffers,
                buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                packedLight, packedOverlay, rotorDeg, leverDeg, capsuleColor, capsuleOpen, scale);
    }

    /** 部品組み立て本体 (ワールド/GUI共用。 opaque バッファだけ呼び出し側が選ぶ)。 */
    private static void renderAssembled(PoseStack pose, MultiBufferSource buffers,
            VertexConsumer body, int packedLight, int packedOverlay, float rotorDeg,
            float leverDeg, int capsuleColor, boolean capsuleOpen, float scale) {
        pose.pushPose();
        pose.scale(scale, scale, scale);
        BASE.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        BODY.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        COLLAR.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        COIN_PLATE.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        COIN_SLOT.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        KNOB_AXLE.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        DISPENSER.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        FLAP.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        LID.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        LID_TOP.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        FINIAL.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);

        // ツマミのハンドル (前面・Z軸でひねる。本物と同じ回し心地)
        pose.pushPose();
        pose.translate(KNOB_X / 16.0, KNOB_Y / 16.0, KNOB_Z / 16.0);
        pose.mulPose(Axis.ZP.rotationDegrees(leverDeg * 1.5F));
        pose.translate(-KNOB_X / 16.0, -KNOB_Y / 16.0, -KNOB_Z / 16.0);
        KNOB_HANDLE.render(pose, body, packedLight, packedOverlay, 1, 1, 1, 1);
        pose.popPose();

        // 球内のカプセル5個 (リング4色+中央レアリティ色・回転。不透明なので球より先に描く)
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.translate(0.0, 19.6 / 16.0, 0.0);
            pose.mulPose(Axis.YP.rotationDegrees(rotorDeg + i * 90.0F));
            pose.translate(3.4 / 16.0, 0.0, 0.0);
            float[] c = PASTEL[i];
            renderCapsule(pose, buffers, packedLight, packedOverlay, c[0], c[1], c[2], false);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(0.0, 20.2 / 16.0, 0.0);
        // リングと逆回転 (-360≡0 のため周回で跳ばない。*0.5等の半端な倍率は跳ぶので使わない)
        pose.mulPose(Axis.YP.rotationDegrees(-rotorDeg));
        float r = (capsuleColor >> 16 & 0xFF) / 255.0F;
        float g = (capsuleColor >> 8 & 0xFF) / 255.0F;
        float b = (capsuleColor & 0xFF) / 255.0F;
        renderCapsule(pose, buffers, packedLight, packedOverlay, r, g, b, capsuleOpen);
        pose.popPose();

        // 透明ガチャ球 (側面4枚・最後に描画)
        VertexConsumer dome = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        GLASS_FRONT.render(pose, dome, packedLight, packedOverlay, 1, 1, 1, 0.4F);
        GLASS_BACK.render(pose, dome, packedLight, packedOverlay, 1, 1, 1, 0.4F);
        GLASS_LEFT.render(pose, dome, packedLight, packedOverlay, 1, 1, 1, 0.4F);
        GLASS_RIGHT.render(pose, dome, packedLight, packedOverlay, 1, 1, 1, 0.4F);

        pose.popPose();
    }

    /**
     * 四角カプセル単体。下半分=白、上半分=指定色 (本物の2色カプセル風)。
     */
    public static void renderCapsule(PoseStack pose, MultiBufferSource buffers, int packedLight,
            int packedOverlay, float r, float g, float b, boolean open) {
        VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        CAPSULE_LO.render(pose, v, packedLight, packedOverlay, 1, 1, 1, 1);
        pose.pushPose();
        if (open) {
            // 上半分を後方へパカッと開く
            pose.translate(0.0, 2.0 / 16.0, -2.0 / 16.0);
            pose.mulPose(Axis.XP.rotationDegrees(-110.0F));
            pose.translate(0.0, -2.0 / 16.0, 2.0 / 16.0);
        }
        CAPSULE_HI.render(pose, v, packedLight, packedOverlay, r, g, b, 1);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(GachaBlockEntity be) {
        return true;
    }
}
