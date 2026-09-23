package ruby.bamboo.client.model;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * メイド服モデルの新規起こし。バニラ HumanoidModel 準拠。
 * LMM 系コードは非参照。UV 配置 (64x32) のみ互換表に合わせ、形状・アニメは独自。
 * 原点=頭頂、足裏 +24px (1.5相当)。HumanoidModel が毎フレーム絶対ピボットを
 * バニラ体型値で上書きするため setupAnim で復元する。
 */
public class MaidModel<T extends LivingEntity> extends HumanoidModel<T> {

    public final ModelPart skirt;
    /** 瞬き板 (北面のみの単面箱。裏面・側面を持たないため別UV事故なし。通常不可視) */
    public final ModelPart eyeR;
    public final ModelPart eyeL;
    /** 付属品 (head 直下。別テクスチャのためレイヤーでなく2パス描画。本体パスでは不可視) */
    public final ModelPart ahoge;
    public final ModelPart monocle;
    public final ModelPart lens;
    public final ModelPart glasses;
    /** head 子のうち付属品以外 (髪・目板・基点)。付属品パスで隠す */
    private final List<ModelPart> headDecor;
    /** 装備基点 (描画なし)。将来の帽子・手持ち受け用 */
    public final ModelPart headMount;
    public final ModelPart headTop;

    public MaidModel(ModelPart root) {
        super(root);
        this.skirt = root.getChild("skirt");
        ModelPart head = root.getChild("head");
        this.eyeR = head.getChild("eye_r");
        this.eyeL = head.getChild("eye_l");
        this.ahoge = head.getChild("ahoge");
        this.monocle = head.getChild("monocle");
        this.lens = head.getChild("lens");
        this.glasses = head.getChild("glasses");
        this.headDecor = ImmutableList.of(head.getChild("hire"), head.getChild("chignon_r"),
                head.getChild("chignon_l"), head.getChild("chignon_b"), head.getChild("tail"),
                head.getChild("sidetail_r"), head.getChild("sidetail_l"), this.eyeR, this.eyeL,
                head.getChild("head_mount"), head.getChild("head_top"));
        this.headMount = head.getChild("head_mount");
        this.headTop = head.getChild("head_top");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, CubeDeformation.NONE),
                PartPose.offset(0F, 8F, 0F));
        head.addOrReplaceChild("hire",
                CubeListBuilder.create().texOffs(24, 0).addBox(-4F, 0F, 1F, 8F, 4F, 3F, CubeDeformation.NONE),
                PartPose.ZERO);
        head.addOrReplaceChild("chignon_r",
                CubeListBuilder.create().texOffs(24, 18).addBox(-5F, -7F, 0.2F, 1F, 3F, 3F, CubeDeformation.NONE),
                PartPose.ZERO);
        head.addOrReplaceChild("chignon_l",
                CubeListBuilder.create().texOffs(24, 18).addBox(4F, -7F, 0.2F, 1F, 3F, 3F, CubeDeformation.NONE),
                PartPose.ZERO);
        head.addOrReplaceChild("chignon_b",
                CubeListBuilder.create().texOffs(52, 10).addBox(-2F, -7.2F, 4F, 4F, 4F, 2F, CubeDeformation.NONE),
                PartPose.ZERO);
        head.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(46, 20).addBox(-1.5F, -6.8F, 4F, 3F, 9F, 3F, CubeDeformation.NONE),
                PartPose.ZERO);
        head.addOrReplaceChild("sidetail_r",
                CubeListBuilder.create().texOffs(58, 21).addBox(-5.5F, -6.8F, 0.9F, 1F, 8F, 2F, CubeDeformation.NONE),
                PartPose.ZERO);
        head.addOrReplaceChild("sidetail_l",
                CubeListBuilder.create().texOffs(58, 21).addBox(4.5F, -6.8F, 0.9F, 1F, 8F, 2F, CubeDeformation.NONE),
                PartPose.ZERO);
        // 目板は北面のみの単面箱 (裏面・側面なし。四辺形直描画は不可視化したため不採用)
        // UV 契約: 左上 (0,0,4,8)/(4,0,4,8) の閉眼画を顔面左右半分に重ねる
        head.addOrReplaceChild("eye_r",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4.02F, 4F, 8F, 0F,
                        EnumSet.of(Direction.NORTH)),
                PartPose.ZERO);
        head.addOrReplaceChild("eye_l",
                CubeListBuilder.create().texOffs(4, 0).addBox(0F, -8F, -4.02F, 4F, 8F, 0F,
                        EnumSet.of(Direction.NORTH)),
                PartPose.ZERO);
        // 付属品 (head 直下。UV は maid_acc_XX.png の割り当て。本体パスでは不可視にし2パス描画)
        // アホ毛: YZ面 (x厚0・y5・z5)。東西両面のみ有効。根元ピボットで揺らす
        head.addOrReplaceChild("ahoge",
                CubeListBuilder.create().texOffs(0, 0).addBox(0F, -5F, -2.5F, 0F, 5F, 5F),
                PartPose.offset(0F, -8F, -4F));
        // 獣耳はオミット中 (形状検討のため一時撤去)
        head.addOrReplaceChild("monocle",
                CubeListBuilder.create().texOffs(36, 0).addBox(0F, -4F, -4.08F, 4F, 4F, 0F,
                        EnumSet.of(Direction.NORTH)),
                PartPose.ZERO);
        head.addOrReplaceChild("lens",
                CubeListBuilder.create().texOffs(40, 0).addBox(0F, -4F, -4.10F, 4F, 4F, 0F,
                        EnumSet.of(Direction.NORTH)),
                PartPose.ZERO);
        head.addOrReplaceChild("glasses",
                CubeListBuilder.create().texOffs(44, 0).addBox(-4F, -4F, -4.07F, 8F, 4F, 0F,
                        EnumSet.of(Direction.NORTH)),
                PartPose.ZERO);
        head.addOrReplaceChild("head_mount", CubeListBuilder.create(), PartPose.offset(0F, -4F, 0F));
        head.addOrReplaceChild("head_top", CubeListBuilder.create(), PartPose.offset(0F, -13F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offset(0F, 8F, 0F));
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(32, 8).addBox(-3F, 0F, -2F, 6F, 7F, 4F, CubeDeformation.NONE),
                PartPose.offset(0F, 8F, 0F));
        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(48, 0).addBox(-2F, -1F, -1F, 2F, 8F, 2F, CubeDeformation.NONE),
                PartPose.offset(-3F, 9.5F, 0F));
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(56, 0).addBox(0F, -1F, -1F, 2F, 8F, 2F, CubeDeformation.NONE),
                PartPose.offset(3F, 9.5F, 0F));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(32, 19).addBox(-2F, 0F, -2F, 3F, 9F, 4F, CubeDeformation.NONE),
                PartPose.offset(-1F, 15F, 0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(32, 19).mirror().addBox(-1F, 0F, -2F, 3F, 9F, 4F, CubeDeformation.NONE),
                PartPose.offset(1F, 15F, 0F));
        root.addOrReplaceChild("skirt",
                CubeListBuilder.create().texOffs(0, 16).addBox(-4F, -2F, -4F, 8F, 8F, 8F, CubeDeformation.NONE),
                PartPose.offset(0F, 15F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg, this.skirt);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmt, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmt, ageInTicks, netHeadYaw, headPitch);
        // HumanoidModel がバニラ体型の絶対ピボットを書き込むため復元する
        this.head.setPos(0F, 8F, 0F);
        this.hat.setPos(0F, 8F, 0F);
        this.body.setPos(0F, 8F, 0F);
        this.rightArm.setPos(-3F, 9.5F, 0F);
        this.leftArm.setPos(3F, 9.5F, 0F);
        this.rightLeg.setPos(-1F, 15F, 0F);
        this.leftLeg.setPos(1F, 15F, 0F);
        this.skirt.setPos(0F, 15F, 0F);
        // スカート揺れ
        this.skirt.xRot = Mth.cos(limbSwing * 0.6662F) * 0.5F * limbSwingAmt;
        if (this.crouching) {
            this.body.y += 3.2F;
            this.head.y += 4.2F;
            this.hat.y += 4.2F;
            this.rightArm.y += 3.2F;
            this.leftArm.y += 3.2F;
            this.rightLeg.z = 4F;
            this.leftLeg.z = 4F;
            this.rightLeg.y += 0.2F;
            this.leftLeg.y += 0.2F;
            this.skirt.xRot += 0.4F;
        }
        // 瞬き (約4秒周期で3tickだけ閉眼板を出す。原本SR2と同極性: 通常不可視・瞬き時可視)
        boolean blink = (ageInTicks % 61F) < 3F;
        this.eyeR.visible = blink;
        this.eyeL.visible = blink;
        // アホ毛揺らし (根元ピボット)。付属品は本体パスでは出さない
        this.ahoge.xRot = Mth.sin(ageInTicks * 0.09F) * 0.07F;
        this.ahoge.zRot = Mth.cos(ageInTicks * 0.11F) * 0.07F;
        this.setAccessoriesVisible(false, false);
    }

    /** 付属品パス準備。本体を隠し head の箱も skipDraw し、付属品だけ出す */
    public void prepareAccessoryPass(boolean lensOnly) {
        this.setAllVisible(false);
        this.skirt.visible = false;
        this.head.visible = true;
        this.head.skipDraw = true;
        for (ModelPart p : this.headDecor) p.visible = false;
        this.ahoge.visible = !lensOnly;
        this.monocle.visible = !lensOnly;
        this.glasses.visible = !lensOnly;
        this.lens.visible = lensOnly;
    }

    /** 本体可視へ復元 (付属品パス後に呼ぶ) */
    public void restoreBodyVisible() {
        this.setAllVisible(true);
        this.skirt.visible = true;
        this.head.skipDraw = false;
        for (ModelPart p : this.headDecor) p.visible = true;
        this.setAccessoriesVisible(false, false);
    }

    private void setAccessoriesVisible(boolean cutout, boolean lens) {
        this.ahoge.visible = cutout;
        this.monocle.visible = cutout;
        this.glasses.visible = cutout;
        this.lens.visible = lens;
    }
}
