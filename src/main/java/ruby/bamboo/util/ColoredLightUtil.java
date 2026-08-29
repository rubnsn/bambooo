package ruby.bamboo.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Vector3f;
import ruby.bamboo.api.ILightColor;
import ruby.bamboo.core.init.BambooCapabilities;

import java.util.List;

/**
 * Phase C: 加算混色ユーティリティ。
 * blendAdditive で RGB 加算クランプ、getTint で半径12の sparse 走査 (capability優先・fallback走査)。
 */
public final class ColoredLightUtil {

    private static final int RADIUS = 12;
    private static final float RADIUS_F = 12.0f;

    private ColoredLightUtil() {
    }

    /**
     * 加算合成後に 0-255 でクランプして 0xRRGGBB を返す。
     *
     * @param colors  0xRRGGBB リスト
     * @param weights 対応する重み (0..1)
     */
    public static int blendAdditive(List<Integer> colors, List<Float> weights) {
        if (colors == null || weights == null || colors.isEmpty() || colors.size() != weights.size()) {
            return 0xFFFFFF;
        }
        float r = 0f;
        float g = 0f;
        float b = 0f;
        for (int i = 0; i < colors.size(); i++) {
            int c = colors.get(i) & 0xFFFFFF;
            float w = weights.get(i);
            r += ((c >> 16) & 0xFF) * w;
            g += ((c >> 8) & 0xFF) * w;
            b += (c & 0xFF) * w;
        }
        int ri = Math.min(255, Math.max(0, Math.round(r)));
        int gi = Math.min(255, Math.max(0, Math.round(g)));
        int bi = Math.min(255, Math.max(0, Math.round(b)));
        return (ri << 16) | (gi << 8) | bi;
    }

    /**
     * 指定位置の tint を取得する。
     * capability sparse を半径12で走査し、距離減衰 weight=1/(1+dist)*(lightLevel/15f) で加算混色。
     * capability が無い場合は BlockGetter 上の ILightColor 走査にフォールバックする。
     *
     * @return 1.0基準の乗算係数 (白=1,1,1)。光源が無ければ 1,1,1 を返す。
     */
    public static Vector3f getTint(BlockPos shadedPos, BlockGetter level) {
        if (shadedPos == null || level == null) {
            return new Vector3f(1f, 1f, 1f);
        }

        java.util.ArrayList<Integer> colors = new java.util.ArrayList<>();
        java.util.ArrayList<Float> weights = new java.util.ArrayList<>();

        boolean usedCap = false;

        // capability sparse 走査 (Level の場合のみ)
        if (level instanceof Level lvl) {
            // 半径12は最大で chunk 3x3 を跨ぐため、周辺チャンクを取得
            int cx0 = (shadedPos.getX() - RADIUS) >> 4;
            int cx1 = (shadedPos.getX() + RADIUS) >> 4;
            int cz0 = (shadedPos.getZ() - RADIUS) >> 4;
            int cz1 = (shadedPos.getZ() + RADIUS) >> 4;
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    if (!lvl.hasChunk(cx, cz)) {
                        continue;
                    }
                    LevelChunk chunk = lvl.getChunk(cx, cz);
                    var opt = chunk.getCapability(BambooCapabilities.COLORED_LIGHT);
                    if (!opt.isPresent()) {
                        continue;
                    }
                    var storage = opt.orElse(null);
                    if (storage == null) {
                        continue;
                    }
                    Object2IntMap<Long> map = storage.getMap();
                    if (map == null || map.isEmpty()) {
                        continue;
                    }
                    usedCap = true;
                    for (Object2IntMap.Entry<Long> e : map.object2IntEntrySet()) {
                        long packed = e.getKey().longValue();
                        BlockPos lightPos = BlockPos.of(packed);
                        double dx = lightPos.getX() + 0.5 - (shadedPos.getX() + 0.5);
                        double dy = lightPos.getY() + 0.5 - (shadedPos.getY() + 0.5);
                        double dz = lightPos.getZ() + 0.5 - (shadedPos.getZ() + 0.5);
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist > RADIUS_F) {
                            continue;
                        }
                        int color = e.getIntValue() & 0xFFFFFF;
                        // lightLevel は保存時は 15 だが、汎用に BlockState から取得を試みる
                        int lightLevel = 15;
                        try {
                            if (lvl.hasChunkAt(lightPos)) {
                                BlockState st = lvl.getBlockState(lightPos);
                                lightLevel = st.getLightEmission(lvl, lightPos);
                                if (lightLevel <= 0) {
                                    lightLevel = 15;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        float w = (float) (1.0 / (1.0 + dist) * (lightLevel / 15.0f));
                        if (w <= 0.001f) {
                            continue;
                        }
                        colors.add(color);
                        weights.add(w);
                    }
                }
            }
        }

        // capability が空 or 非Level の場合はフォールバック走査 (周辺ブロックを直接見る)
        if (!usedCap) {
            // 走査 fallback: 半径12 立方体を全走査するのは重いため、capability 無し時のみ実行
            // BlockGetter が Level でない場合でも動作する (クライアント World で capability が未同期の場合など)
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist > RADIUS_F) {
                            continue;
                        }
                        BlockPos p = shadedPos.offset(dx, dy, dz);
                        // 範囲外チャンクはスキップ (hasChunkAt 相当のチェック)
                        if (level instanceof Level lvl2 && !lvl2.hasChunkAt(p)) {
                            continue;
                        }
                        BlockState st;
                        try {
                            st = level.getBlockState(p);
                        } catch (Exception ignored) {
                            continue;
                        }
                        if (!(st.getBlock() instanceof ILightColor light)) {
                            continue;
                        }
                        int color = light.getLightColor(st, level, p) & 0xFFFFFF;
                        int lightLevel = st.getLightEmission(level, p);
                        if (lightLevel <= 0) {
                            lightLevel = 15;
                        }
                        float w = (float) (1.0 / (1.0 + dist) * (lightLevel / 15.0f));
                        if (w <= 0.001f) {
                            continue;
                        }
                        colors.add(color);
                        weights.add(w);
                    }
                }
            }
        }

        if (colors.isEmpty()) {
            return new Vector3f(1f, 1f, 1f);
        }

        int blended = blendAdditive(colors, weights);
        float r = ((blended >> 16) & 0xFF) / 255f;
        float g = ((blended >> 8) & 0xFF) / 255f;
        float b = (blended & 0xFF) / 255f;
        // 白(1,1,1)を基準に色を乗算する係数として返す。
        // 現状 additive のため暗い場所でも色が乗るが、Phase D で頂点 bake 時に intensity で再調整する。
        // ここでは純粋な blended/255 を返す (白飛びを避けるため 0..1 クランプ済み)
        // 白光のみなら 1,1,1 に近づくよう補正: lerp(1, blended/255, weightSum clamped) でも良いが Phase C は単純に返す
        // ただし何も無い時は 1,1,1 を返すため、 blended が白に近い場合は tint も白になる
        // additive で薄まるのを防ぐため、最大 weight が小さいときは白に寄せる
        // 簡易: 最大 weight で lerp
        float maxW = 0f;
        for (float w : weights) {
            if (w > maxW) {
                maxW = w;
            }
        }
        float t = Math.min(1f, maxW * 2f); // 近距離で t=1, 遠距離で 0 に近づく
        return new Vector3f(
                1f + (r - 1f) * t,
                1f + (g - 1f) * t,
                1f + (b - 1f) * t
        );
    }
}
