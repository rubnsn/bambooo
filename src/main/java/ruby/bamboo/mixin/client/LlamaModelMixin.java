package ruby.bamboo.mixin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ruby.bamboo.client.FigurePoseState;

/**
 * ラマ装飾モデルへの可動部ポーズ適用 (SheepFurModelMixin と同趣旨)。
 * LlamaModel は本体と装飾で共用のため、本体には同じ値が二重適用されるが
 * 同値の上書きで無害。通常描画には干渉しない。
 */
@Mixin(net.minecraft.client.model.LlamaModel.class)
public abstract class LlamaModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/horse/AbstractChestedHorse;FFFFF)V",
            at = @At("TAIL"))
    private void bamboomod$applyFigurePoseToDecor(AbstractChestedHorse llama, float limbSwing,
            float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci) {
        try {
            FigurePoseState.applyPending((EntityModel<?>)(Object) this);
        } catch (Exception ignored) {
        }
    }
}
