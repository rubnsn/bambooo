package ruby.bamboo.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * バニラ村人帽子の写し。VillagerModel.java:40-41 と同一数値で 64x64 焼きし、
 * バニラ職業テクスチャ (64x64) をそのまま被せるための器。
 * head ピボットのみメイド頭 (0,8,0) に合わせている。回転・位置は毎フレーム
 * MaidRenderer が maid 頭から複写する (行列操作なし・float 6個のコピーのみ)。
 */
public final class VillagerHatModel {

    public final ModelPart head;

    public VillagerHatModel(ModelPart root) {
        this.head = root.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create(), PartPose.offset(0F, 8F, 0F));
        // 被りは頭と同心 (上端一致の +2)。12px浮きの真因は maidModel.young 未代入で
        // 成人も baby 分岐描画 (頭+16) されていたこと。ここで +12 すると回転中心が
        // 頭 (22) と帽子 (8) に分離しピッチでずれるため、嵩上げはしない。鍔 -2 維持
        PartDefinition hat = head.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4F, -10F, -4F, 8F, 10F, 8F,
                        new CubeDeformation(0.51F)),
                PartPose.offset(0F, 2F, 0F));
        hat.addOrReplaceChild("hat_rim",
                CubeListBuilder.create().texOffs(30, 47).addBox(-8F, -8F, -6F, 16F, 16F, 1F),
                PartPose.offsetAndRotation(0F, -2F, 0F, -(float) Math.PI / 2F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
