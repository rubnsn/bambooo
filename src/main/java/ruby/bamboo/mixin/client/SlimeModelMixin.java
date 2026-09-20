package ruby.bamboo.mixin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ruby.bamboo.client.FigurePoseState;

/**
 * スライム外層モデルへの可動部ポーズ適用 (本体と外層で共用のため同値の二重適用)。
 * 通常描画には干渉しない。
 */
@Mixin(net.minecraft.client.model.SlimeModel.class)
public abstract class SlimeModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
            at = @At("TAIL"))
    private void bamboomod$applyFigurePose(Entity slime, float limbSwing,
            float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci) {
        try {
            FigurePoseState.applyPending((EntityModel<?>)(Object) this);
        } catch (Exception ignored) {
        }
    }
}
