package ruby.bamboo.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import me.jellysquid.mods.sodium.client.model.color.ColorProvider;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import ruby.bamboo.util.ColoredLightUtil;

/**
 * Embeddium/Sodium 系チャンク焼き込みへの色付き光適用 (ワンフック)。
 * <p>
 * Sodium の {@code BlockRenderer#getVertexColors} は全クアッド無条件で呼ばれ、
 * 無tintクアッドには白埋め {@code [-1,-1,-1,-1]} を返す。RETURN時に同一
 * {@link ColoredLightUtil#getTint} でRGB乗算するため、バニラ焼き込みtint
 * ({@code ModelBlockRendererMixin}) と完全同値になる。Sodium側の面別明度・AOは
 * 後段で掛かるため、裏面への回り込みは起きない。
 * <p>
 * 依存安全策: {@code @Pseudo}＋{@code targets}文字列指定のため、Embeddium不在時は
 * 適用自体がスキップされる (配布jarへの同梱・mods.toml依存なし、compileOnly参照のみ)。
 * 白 (1,1,1) の99.9%は早期リターンで無変更のため、通常ブロックの焼き込みは不変。
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin {

    /** quad単位連打の緩和: 同一pos連続時はtint再計算しない (MutableBlockPos再利用のため値比較) */
    @Unique
    private static final ThreadLocal<Object[]> bamboomod$lastPosTint = new ThreadLocal<>();

    @Inject(method = "getVertexColors", at = @At("RETURN"), remap = false)
    private void bamboomod$tintSpill(BlockRenderContext ctx, ColorProvider<BlockState> provider,
            BakedQuadView quad, CallbackInfoReturnable<int[]> cir) {
        if (ctx == null) return;
        int[] colors;
        try {
            colors = cir.getReturnValue();
        } catch (Exception e) {
            return;
        }
        if (colors == null || colors.length == 0) return;
        Vector3f tint;
        try {
            long key;
            try {
                key = ctx.pos().asLong();
            } catch (Exception e) {
                return;
            }
            Object[] box = bamboomod$lastPosTint.get();
            if (box != null && box[0] instanceof Long k && k == key && box[1] instanceof Vector3f t) {
                if (t.x() == 1 && t.y() == 1 && t.z() == 1) return;
                tint = t;
            } else {
                tint = ColoredLightUtil.getTint(ctx.pos(), ctx.localSlice());
                if (tint == null) return;
                bamboomod$lastPosTint.set(new Object[] { key, new Vector3f(tint) });
                if (tint.x() == 1 && tint.y() == 1 && tint.z() == 1) return;
            }
        } catch (Exception e) {
            return;
        }
        float r = tint.x(), g = tint.y(), b = tint.z();
        for (int i = 0; i < colors.length; i++) {
            int c = colors[i];
            int rr = Math.min(255, Math.round(((c >> 16) & 0xFF) * r));
            int gg = Math.min(255, Math.round(((c >> 8) & 0xFF) * g));
            int bb = Math.min(255, Math.round((c & 0xFF) * b));
            colors[i] = (c & 0xFF000000) | (rr << 16) | (gg << 8) | bb;
        }
    }
}
