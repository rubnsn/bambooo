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
        if (figure == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
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
            // 16px枠に収める (1ブロック=fit px)。ゆっくり回転させて立体感を出す
            float fit = Math.min(64.0F, 13.0F / heightBlocks);
            pose.translate(8.0F, 14.0F, 8.0F);
            pose.scale(fit, fit, fit);
            float spin = (System.currentTimeMillis() / 100L) % 360L;
            FigureRenderer.renderDummy(pose, buffer, dummy, figure.scale(), spin, figure.pose(),
                    LightTexture.FULL_BRIGHT);
            // 右下に小さなカプセルを重ねる
            try {
                ItemStack capsule = new ItemStack(
                        ruby.bamboo.core.init.BambooItems.CAPSULE_BALL.get());
                pose.pushPose();
                pose.translate(12.0F, 12.0F, 10.0F);
                pose.scale(0.45F, 0.45F, 0.45F);
                mc.getItemRenderer().renderStatic(capsule, ItemDisplayContext.GUI,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffer,
                        mc.level, 0);
                pose.popPose();
            } catch (Exception e) {
                // カプセル描画失敗時は本体のみ
            }
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
