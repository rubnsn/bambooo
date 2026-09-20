package ruby.bamboo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import ruby.bamboo.client.renderer.FigureRenderer;
import ruby.bamboo.item.MonsterFigureItem;

/**
 * フィギュアのインベントリ用 BEWLR。GUI内の見た目は捕獲モンスターそのものにする。
 * GUIではゆっくり回転、手持ち等では等身のまま小さく描画する。
 */
public class FigureItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static FigureItemRenderer INSTANCE;

    public static FigureItemRenderer getInstance() {
        if (INSTANCE == null) {
            try {
                INSTANCE = new FigureItemRenderer();
            } catch (Exception e) {
                try {
                    INSTANCE = new FigureItemRenderer(true);
                } catch (Exception e2) {
                    return null;
                }
            }
        }
        return INSTANCE;
    }

    private FigureItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    private FigureItemRenderer(boolean dummy) {
        super(null, null);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        MonsterFigureItem.FigureData figure = MonsterFigureItem.read(stack);
        Minecraft mc = Minecraft.getInstance();
        if (figure == null || mc.level == null) {
            // 中身なし (通常は流通しない) は卵表示にフォールバック。
            // renderStatic内でもItemRenderer.renderの-0.5平行移動が掛かるため、
            // 通常表示と同じtranslateを先に掛けて枠内に収める
            try {
                pose.pushPose();
                if (context == ItemDisplayContext.GUI) {
                    pose.translate(0.5F, 0.5F, 0.0F);
                } else {
                    pose.translate(0.5F, 0.0F, 0.5F);
                }
                mc.getItemRenderer().renderStatic(
                        new ItemStack(net.minecraft.world.item.Items.EGG), context,
                        packedLight, packedOverlay, pose, buffer, mc.level, 0);
                pose.popPose();
            } catch (Exception ignored) {
            }
            return;
        }
        LivingEntity dummy;
        try {
            dummy = FigureRenderer.getItemDummy(mc.level, figure.entityId(), figure.data());
        } catch (Exception e) {
            return;
        }
        if (dummy == null) return;
        float realH = MonsterFigureItem.realHeight(figure.entityId());
        float heightBlocks = Math.max(0.05F, realH * figure.scale());
        pose.pushPose();
        if (context == ItemDisplayContext.GUI) {
            // アイテム空間: GuiGraphicsでslot中心+16倍、vanilla render()で-0.5済み。
            // よって受取時点の原点はスロット左下すみ (-0.5,-0.5)。足を底辺中央 (0,-0.4) へ移す
            float fit = 0.8F / heightBlocks;
            pose.pushPose();
            pose.translate(0.5F, 0.1F, 0.0F);
            pose.scale(fit, fit, fit);
            float spin = 45F;
            FigureRenderer.renderDummy(pose, buffer, dummy, figure.scale(), spin, figure.pose(),
                    LightTexture.FULL_BRIGHT);
            pose.popPose();
        } else {
            // 手持ち・地面等: 実寸のまま (足元原点)
            pose.translate(0.5F, 0.0F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(45.0F));
            FigureRenderer.renderDummy(pose, buffer, dummy, figure.scale(), 45.0F, figure.pose(),
                    packedLight);
        }
        pose.popPose();
    }
}
