package ruby.bamboo.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ruby.bamboo.client.FigurePoseState;

/**
 * フィギュアの可動部ポーズ適用。
 * FigureRenderer が vanilla 描画へ委譲する際、モデルの setupAnim 直後に
 * ThreadLocal の角度を上書きする。ThreadLocal が空の通常描画には干渉しない。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class FigurePoseMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void bamboomod$applyFigurePose(LivingEntity entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        try {
            EntityModel<?> model = (EntityModel<?>) ((LivingEntityRenderer<?, ?>) (Object) this)
                    .getModel();
            FigurePoseState.applyPending(model);
        } catch (Exception ignored) {
        }
    }
}
