package ruby.bamboo.mixin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ruby.bamboo.client.FigurePoseState;

/**
 * マグマキューブモデルへの可動部ポーズ適用 (SlimeModel とは別系統のため個別)。
 * 通常描画には干渉しない。
 */
@Mixin(net.minecraft.client.model.LavaSlimeModel.class)
public abstract class LavaSlimeModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/Slime;FFFFF)V",
            at = @At("TAIL"))
    private void bamboomod$applyFigurePose(Slime magma, float limbSwing,
            float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci) {
        try {
            FigurePoseState.applyPending((EntityModel<?>)(Object) this);
        } catch (Exception ignored) {
        }
    }
}
