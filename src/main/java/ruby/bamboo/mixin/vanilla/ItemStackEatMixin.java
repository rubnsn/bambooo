package ruby.bamboo.mixin.vanilla;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ruby.bamboo.client.TransformEatClientHandler;

/**
 * 変身中の非食料摂取だけ口元モーション(EAT)にする。
 * 対象はローカルが長押し使用中の pending 品のみで、通常の弓・盾等の構えは変えない。
 * 音・粒子も client の triggerItemUseEffects が EAT 扱いで出す (サーバは出さない)。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackEatMixin {

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void bamboomod$transformEatAnimation(CallbackInfoReturnable<UseAnim> cir) {
        try {
            if (TransformEatClientHandler.shouldForceEatAnimation((ItemStack) (Object) this)) {
                cir.setReturnValue(UseAnim.EAT);
            }
        } catch (Exception ignored) {
        }
    }
}
