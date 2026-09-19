package ruby.bamboo.mixin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.animal.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ruby.bamboo.client.FigurePoseState;

/**
 * 羊毛モデルへの可動部ポーズ適用。羊毛は別モデル (SheepFurModel) で
 * 独自に setupAnim するため FigurePoseMixin の対象外になる。
 * モデル自身に注入する (@Shadow 不要)。通常描画には干渉しない。
 */
@Mixin(net.minecraft.client.model.SheepFurModel.class)
public abstract class SheepFurModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/Sheep;FFFFF)V",
            at = @At("TAIL"))
    private void bamboomod$applyFigurePoseToWool(Sheep sheep, float limbSwing,
            float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci) {
        try {
            FigurePoseState.applyPending((EntityModel<?>)(Object) this);
        } catch (Exception ignored) {
        }
    }
}
