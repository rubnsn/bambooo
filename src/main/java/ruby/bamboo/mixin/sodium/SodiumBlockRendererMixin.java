package ruby.bamboo.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Phase D: Sodium bake hook scaffolding.
 *
 * <p>対象: {@code me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer}
 * Sodium 0.5.x / Embeddium fork 前のオリジナル座標。Forge 1.20.1 では通常 Embeddium 側が使われるが、
 * 互換のため両方に別 Mixin を用意する。</p>
 *
 * <p>本来の処理: chunk bake 時の頂点光計算直後 (AoLight や packedLight 生成後) に
 * {@link ruby.bamboo.util.ColoredLightUtil#getTint(net.minecraft.core.BlockPos, net.minecraft.world.level.BlockGetter)}
 * を呼び、RGB を加算クランプで頂点 color / packedLight に乗算する。</p>
 *
 * <p>TODO: 実フィールド名は decompiled class を javap で確認してから Inject すること。
 * 手元に Sodium/Embeddium jar が無いため本 Phase ではコメントアウトした雛形のみを残す。
 * 確認コマンド例:</p>
 * <pre>
 * javap -c -p -classpath sodium-0.5.8.jar me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer
 * javap -c -p -classpath embeddium-0.3.31.jar org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderer
 * // 出力から AoLight / quad / packedLight / vertex color フィールド名と
 * // LightDataAccess.getLightmapData / calculateAoLight 等の呼び出し位置を特定する
 * </pre>
 *
 * <p>確認できない間は本 Mixin は何もしない (require=0, priority=900, @Pseudo で安全)。</p>
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", priority = 900)
public abstract class SodiumBlockRendererMixin {

    // ========================================================================
    // TODO: javap 確認後に有効化する Inject 雛形 (現在はコメントアウト)
    // ========================================================================
    //
    // 想定箇所: Sodium BlockRenderer 内で quad 毎に AoLight/packedLight を計算するメソッド
    // (例: renderQuad / processQuad / emitQuad 等。バージョンで名前が異なる)
    //
    // 例1: AoLight 計算直後で頂点カラーに乗算するパターン
    // --------------------------------------------------
    // @Inject(
    //     method = "renderQuad",
    //     at = @At(
    //         value = "INVOKE",
    //         target = "Lme/jellysquid/mods/sodium/client/model/light/data/LightDataAccess;getLightmapData(Lnet/minecraft/core/BlockPos;)I",
    //         shift = At.Shift.AFTER
    //     ),
    //     require = 0,
    //     remap = false
    // )
    // private void bamboomod$afterAoLight(
    //     net.minecraft.world.level.BlockAndTintGetter level,
    //     net.minecraft.core.BlockPos pos,
    //     net.minecraft.world.level.block.state.BlockState state,
    //     CallbackInfo ci) {
    //     // level と pos は実際のメソッドシグネチャに合わせて取得すること。
    //     // @Local や @Share, MixinExtras 等で pos/level を捕捉するのが望ましい。
    //     try {
    //         org.joml.Vector3f tint = ruby.bamboo.util.ColoredLightUtil.getTint(pos, level);
    //         // 例: quad.getColor(i) や AoLight.r/g/b に tint を乗算し 0..1 でクランプ
    //         // aoLight.r = Math.min(1.0f, aoLight.r * tint.x());
    //         // aoLight.g = Math.min(1.0f, aoLight.g * tint.y());
    //         // aoLight.b = Math.min(1.0f, aoLight.b * tint.z());
    //         // または packedLight の RGB 成分に乗算 (LightTexture 同様に加算クランプ)
    //     } catch (Exception ignored) {
    //     }
    // }
    //
    // 例2: packedLight 生成直後で乗算するパターン (Truly Bright と同位置)
    // --------------------------------------------------
    // @Inject(
    //     method = "getLightMap",
    //     at = @At("RETURN"),
    //     require = 0,
    //     remap = false
    // )
    // private void bamboomod$onPackedLight(int packedLight, CallbackInfoReturnable<Integer> cir) {
    //     // BlockPos と Level を Mixin フィールドまたは ThreadLocal から取得し
    //     // Vector3f tint = ColoredLightUtil.getTint(pos, level);
    //     // int r = (packedLight >> 16) & 0xFF; // 実際の packed 形式に合わせる
    //     // r = Math.min(255, Math.round(r * tint.x()));
    //     // cir.setReturnValue((r << 16) | ...);
    // }
    //
    // 注意:
    // - 存在しないフィールド/メソッドを直接参照するとコンパイルエラーになるため、
    //   必ず javap で実フィールド名を確認してから有効化すること。
    // - 本雛形はコンパイルを通すため全てコメントアウトしてある。
    // - 有効化時は priority=900, require=0, remap=false を維持し、Sodium が無い環境でもクラッシュしないこと。
}
