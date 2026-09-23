package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.client.model.MaidModel;

/**
 * メイドモデル用手持ちレイヤー。バニラ ItemInHandLayer と同配置で、
 * 腕の取得先だけ MaidModel に差し替えたもの (親の VillagerModel では腕が合わないため)。
 */
@OnlyIn(Dist.CLIENT)
public class MaidHandLayer extends RenderLayer<Villager, VillagerModel<Villager>> {

    private final MaidModel<Villager> maidModel;
    private final ItemInHandRenderer itemInHandRenderer;

    public MaidHandLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent,
            MaidModel<Villager> maidModel, ItemInHandRenderer itemInHandRenderer) {
        super(parent);
        this.maidModel = maidModel;
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, Villager entity,
            float limbSwing, float limbSwingAmt, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!MaidRenderer.isMaid(entity)) return;
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
        ItemStack off = right ? entity.getOffhandItem() : entity.getMainHandItem();
        ItemStack main = right ? entity.getMainHandItem() : entity.getOffhandItem();
        if (off.isEmpty() && main.isEmpty()) return;
        pose.pushPose();
        if (this.maidModel.young) {
            pose.translate(0F, 0.75F, 0F);
            pose.scale(0.5F, 0.5F, 0.5F);
        }
        renderArmWithItem(entity, main, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT, pose, buffer, light);
        renderArmWithItem(entity, off, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT, pose, buffer, light);
        pose.popPose();
    }

    private void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
            HumanoidArm arm, PoseStack pose, MultiBufferSource buffer, int light) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        ModelPart armPart = arm == HumanoidArm.LEFT ? this.maidModel.leftArm : this.maidModel.rightArm;
        armPart.translateAndRotate(pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90F));
        pose.mulPose(Axis.YP.rotationDegrees(180F));
        pose.translate((arm == HumanoidArm.LEFT ? -1F : 1F) / 16F, 0.125F, -0.625F);
        this.itemInHandRenderer.renderItem(entity, stack, context, arm == HumanoidArm.LEFT, pose, buffer, light);
        pose.popPose();
    }
}
