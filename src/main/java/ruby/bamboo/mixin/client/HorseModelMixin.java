package ruby.bamboo.mixin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ruby.bamboo.client.FigurePoseState;

/**
 * ウマモデルへの可動部ポーズ適用。本体と鎧層でモデルを共用のため、
 * 同値の二重適用になるが無害。通常描画には干渉しない。
 */
@Mixin(net.minecraft.client.model.HorseModel.class)
public abstract class HorseModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/horse/AbstractHorse;FFFFF)V",
            at = @At("TAIL"))
    private void bamboomod$applyFigurePose(AbstractHorse horse, float limbSwing,
            float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci) {
        try {
            FigurePoseState.applyPending((EntityModel<?>)(Object) this);
        } catch (Exception ignored) {
        }
    }
}
