package ruby.bamboo.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ruby.bamboo.client.FigurePoseState;

/**
 * 鞍層モデルへの可動部ポーズ適用。鞍は本体と同クラスの別インスタンスで
 * 独自に setupAnim するため FigurePoseMixin の対象外になる。
 * ブタ・ストライダー等の鞍つき捕獲物が対象。通常描画には干渉しない。
 */
@Mixin(net.minecraft.client.renderer.entity.layers.SaddleLayer.class)
public abstract class SaddleLayerMixin {

    @Shadow
    private net.minecraft.client.model.EntityModel model;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void bamboomod$applyFigurePoseToSaddle(PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, Entity entity, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch, CallbackInfo ci) {
        try {
            FigurePoseState.applyPending(this.model);
        } catch (Exception ignored) {
        }
    }
}
