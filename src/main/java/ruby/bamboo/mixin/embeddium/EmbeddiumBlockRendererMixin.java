package ruby.bamboo.mixin.embeddium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Phase D: Embeddium bake hook scaffolding.
 *
 * <p>対象: {@code org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderer}
 * Forge 1.20.1 + Embeddium 0.3.x (Sodium fork) の実際のクラス。Sodium オリジナルとは
 * パッケージが異なるため別 Mixin が必要。両方に {@code @Pseudo} を付与し、存在しない
 * 環境でもクラッシュしないようにする。</p>
 *
 * <p>本来の処理: chunk bake 時の頂点光計算直後 (AoLight や packedLight 生成後) に
 * {@link ruby.bamboo.util.ColoredLightUtil#getTint(net.minecraft.core.BlockPos, net.minecraft.world.level.BlockGetter)}
 * を呼び、RGB を加算クランプで頂点 color / packedLight に乗算する。詳細は
 * {@link ruby.bamboo.mixin.sodium.SodiumBlockRendererMixin} と同様。</p>
 *
 * <p>TODO: 実フィールド名は decompiled class を javap で確認してから Inject すること。
 * 手元に Embeddium jar が無いため本 Phase ではコメントアウトした雛形のみを残す。
 * 確認コマンド例:</p>
 * <pre>
 * javap -c -p -classpath embeddium-0.3.31.jar org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderer
 * // Sodium 側と同様に AoLight / quad / LightDataAccess の位置を特定
 * </pre>
 *
 * <p>確認できない間は本 Mixin は何もしない (require=0, priority=900, @Pseudo で安全)。</p>
 */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderer", priority = 900)
public abstract class EmbeddiumBlockRendererMixin {

    // ========================================================================
    // TODO: javap 確認後に有効化する Inject 雛形 (現在はコメントアウト)
    // ========================================================================
    //
    // 想定箇所: Embeddium BlockRenderer 内で quad 毎に AoLight/packedLight を計算するメソッド
    // (Sodium と同等のロジックだが、Embeddium ではクラス名・メソッド名が微修正されている可能性あり)
    //
    // 例1: AoLight 計算直後で頂点カラーに乗算するパターン
    // --------------------------------------------------
    // @Inject(
    //     method = "renderQuad",
    //     at = @At(
    //         value = "INVOKE",
    //         target = "Lorg/embeddedt/embeddium/impl/model/light/data/LightDataAccess;getLightmapData(Lnet/minecraft/core/BlockPos;)I",
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
    //     try {
    //         org.joml.Vector3f tint = ruby.bamboo.util.ColoredLightUtil.getTint(pos, level);
    //         // aoLight.r = Math.min(1.0f, aoLight.r * tint.x());
    //         // aoLight.g = Math.min(1.0f, aoLight.g * tint.y());
    //         // aoLight.b = Math.min(1.0f, aoLight.b * tint.z());
    //     } catch (Exception ignored) {
    //     }
    // }
    //
    // 例2: packedLight 生成直後で乗算するパターン
    // --------------------------------------------------
    // @Inject(
    //     method = "getLightMap",
    //     at = @At("RETURN"),
    //     require = 0,
    //     remap = false
    // )
    // private void bamboomod$onPackedLight(int packedLight, CallbackInfoReturnable<Integer> cir) {
    //     // Vector3f tint = ColoredLightUtil.getTint(pos, level);
    //     // int r = ...; r = Math.min(255, Math.round(r * tint.x()));
    //     // cir.setReturnValue(...);
    // }
    //
    // 注意:
    // - 存在しないフィールド/メソッドを直接参照するとコンパイルエラーになるため、
    //   必ず javap で実フィールド名を確認してから有効化すること。
    // - 本雛形はコンパイルを通すため全てコメントアウトしてある。
    // - 有効化時は priority=900, require=0, remap=false を維持し、Embeddium が無い環境でもクラッシュしないこと。
    // - 将来 Truly Bright の Sodium-native path / Flywheel hook を参考に拡張可能。
}
