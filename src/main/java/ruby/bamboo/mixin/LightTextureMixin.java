package ruby.bamboo.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.LightTexture;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase C: LightTexture scaffolding.
 * OptiFine が入っている場合は無効化し、それ以外はログのみ (Phase D で頂点bake実装予定)。
 */
@Pseudo
@Mixin(value = LightTexture.class, priority = 900)
public class LightTextureMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedOnce = false;

    @Inject(method = "updateLightTexture", at = @At("RETURN"), require = 0, remap = false)
    private void bamboomod$tail(float partialTick, CallbackInfo ci) {
        if (ModList.get().isLoaded("optifine")) {
            return;
        }
        // Phase Cではtint乗算は行わずログのみ。Client側 capability map があれば取得できることを確認する。
        // 実際の乗算は Phase D で頂点 bake / lightmap に適用する。
        if (!loggedOnce) {
            loggedOnce = true;
            LOGGER.info("[bamboomod] LightTextureMixin injected (Phase C scaffolding, tint deferred to Phase D)");
        }
        // 将来実装例:
        // Minecraft mc = Minecraft.getInstance();
        // if (mc.level != null && mc.player != null) {
        //     Vector3f tint = ColoredLightUtil.getTint(mc.player.blockPosition(), mc.level);
        //     // lightPixels への乗算は Phase D で行う
        // }
    }
}
