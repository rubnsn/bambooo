package ruby.bamboo.util;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
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

    private static final int RADIUS = 16;
    private static final float RADIUS_F = 16.0f;

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
        // スクリーン合成: 1-(1-a)(1-b) で多色ほど白に近づく。重みwは寄与度として c*w をスクリーンする
        float r = 0f;
        float g = 0f;
        float b = 0f;
        for (int i = 0; i < colors.size(); i++) {
            int c = colors.get(i) & 0xFFFFFF;
            float w = weights.get(i);
            float cr = ((c >> 16) & 0xFF) * w;
            float cg = ((c >> 8) & 0xFF) * w;
            float cb = (c & 0xFF) * w;
            r = 255f - (255f - r) * (255f - cr) / 255f;
            g = 255f - (255f - g) * (255f - cg) / 255f;
            b = 255f - (255f - b) * (255f - cb) / 255f;
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
    private static java.lang.reflect.Field LEVEL_FIELD = null;
    private static Class<?> LEVEL_FIELD_CLASS = null;

    private static Level extractLevel(BlockGetter getter) {
        if (getter instanceof Level lvl) return lvl;
        Class<?> clz = getter.getClass();
        if (LEVEL_FIELD != null && LEVEL_FIELD_CLASS == clz) {
            try {
                Object v = LEVEL_FIELD.get(getter);
                if (v instanceof Level lvl2) return lvl2;
            } catch (Exception ignored) {
            }
        } else {
            try {
                var f = clz.getDeclaredField("level");
                f.setAccessible(true);
                Object v = f.get(getter);
                if (v instanceof Level lvl2) {
                    LEVEL_FIELD = f;
                    LEVEL_FIELD_CLASS = clz;
                    return lvl2;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.level != null) return mc.level;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void ensureChunkScanned(LevelChunk chunk, Level lvl) {
        var opt = chunk.getCapability(BambooCapabilities.COLORED_LIGHT);
        if (!opt.isPresent()) return;
        var storage = opt.orElse(null);
        if (storage == null || storage.isScanned()) return;
        synchronized (storage) {
            if (storage.isScanned()) return;
            storage.setScanned(true);
            // パレットで枝刈り: IndLight を含むセクションだけを走査
            int minSec = lvl.getMinSection();
            var sections = chunk.getSections();
            for (int si = 0; si < sections.length; si++) {
                var sec = sections[si];
                if (sec == null || sec.hasOnlyAir()) continue;
                var states = sec.getStates();
                boolean maybeHas = false;
                try {
                    maybeHas = states.maybeHas(s -> s.getBlock() instanceof ILightColor);
                } catch (Exception e) {
                    maybeHas = true;
                }
                if (!maybeHas) continue;
                int secY = minSec + si;
                int baseY = secY << 4;
                int baseX = chunk.getPos().x << 4;
                int baseZ = chunk.getPos().z << 4;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockPos p = new BlockPos(baseX + x, baseY + y, baseZ + z);
                            BlockState st;
                            try {
                                st = sec.getBlockState(x, y, z);
                            } catch (Exception ignored) {
                                continue;
                            }
                            if (!(st.getBlock() instanceof ILightColor light)) continue;
                            int col = light.getLightColor(st, lvl, p) & 0xFFFFFF;
                            storage.getMap().put(Long.valueOf(p.asLong()), col);
                        }
                    }
                }
            }
        }
    }

    public static Vector3f getTint(BlockPos shadedPos, BlockGetter level) {
        if (shadedPos == null || level == null) {
            return new Vector3f(1f, 1f, 1f);
        }

        Level lvlForCache = extractLevel(level);
        // B-2: tintCache参照 (shadedPosの属するchunkのcache) — RenderChunkRegion でも Level 経由で引く
        if (lvlForCache != null && lvlForCache.hasChunkAt(shadedPos)) {
            LevelChunk chunkCache = lvlForCache.getChunkAt(shadedPos);
            var optCache = chunkCache.getCapability(BambooCapabilities.COLORED_LIGHT);
            if (optCache.isPresent()) {
                var storageCache = optCache.orElse(null);
                if (storageCache != null) {
                    Long2IntMap tintCache = storageCache.getTintCache();
                    long key = shadedPos.asLong();
                    synchronized (tintCache) {
                        if (tintCache.containsKey(key)) {
                            int cached = tintCache.get(key);
                            if (cached == 0xFFFFFF) return new Vector3f(1f, 1f, 1f);
                            float cr = ((cached >> 16) & 0xFF) / 255f;
                            float cg = ((cached >> 8) & 0xFF) / 255f;
                            float cb = (cached & 0xFF) / 255f;
                            return new Vector3f(cr, cg, cb);
                        }
                    }
                }
            }
        }

        java.util.ArrayList<Integer> colors = new java.util.ArrayList<>();
        java.util.ArrayList<Float> weights = new java.util.ArrayList<>();

        Level capLevel = extractLevel(level);

        // 遅延1回スキャン: 描画で触れたチャンクのみをパレット枝刈りで走査し map を遅延構築。常時 98k *100 を避ける
        if (capLevel != null) {
            Level lvl = capLevel;
            int cx0 = (shadedPos.getX() - RADIUS) >> 4;
            int cx1 = (shadedPos.getX() + RADIUS) >> 4;
            int cz0 = (shadedPos.getZ() - RADIUS) >> 4;
            int cz1 = (shadedPos.getZ() + RADIUS) >> 4;
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    if (!lvl.hasChunk(cx, cz)) continue;
                    LevelChunk chunk = lvl.getChunk(cx, cz);
                    ensureChunkScanned(chunk, lvl);
                }
            }
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

        if (colors.isEmpty()) {
            if (lvlForCache != null && lvlForCache.hasChunkAt(shadedPos)) {
                LevelChunk chunkPut = lvlForCache.getChunkAt(shadedPos);
                var optPut = chunkPut.getCapability(BambooCapabilities.COLORED_LIGHT);
                if (optPut.isPresent()) {
                    var storagePut = optPut.orElse(null);
                    if (storagePut != null) {
                        synchronized (storagePut.getTintCache()) {
                            storagePut.getTintCache().put(shadedPos.asLong(), 0xFFFFFF);
                        }
                    }
                }
            }
            return new Vector3f(1f, 1f, 1f);
        }

        // 距離減衰を正しく反映: blended は加重平均で純色 (距離に依らず同じになる) なので、
        // totalWeight(=sum w)で白とのlerp強度 alpha を決める。
        // 単一光源 di=0 -> w=1 -> alpha~1(純色に近い)、di=12 -> w=0.076 -> alpha~0.07(ほぼ白)
        float totalWeight = 0f;
        for (float w : weights) totalWeight += w;
        // 複数光源でも 1 でクランプ。0.8掛けで近距離でも20%白を残して暗すぎを緩和
        float alpha = Math.min(1f, totalWeight) * 1f;
        int blendedPure = blendAdditive(colors, weights);
        int pr = (blendedPure >> 16) & 0xFF;
        int pg = (blendedPure >> 8) & 0xFF;
        int pb = blendedPure & 0xFF;
        // 白(255,255,255) と pure の lerp
        int fr = Math.round(255 * (1 - alpha) + pr * alpha);
        int fg = Math.round(255 * (1 - alpha) + pg * alpha);
        int fb = Math.round(255 * (1 - alpha) + pb * alpha);
        // B: 輝度正規化 - 最大チャネルを255に合わせて乗算でも暗くならないように色相のみ残す
        float max = Math.max(fr, Math.max(fg, fb));
        if (max > 0 && max < 255) {
            float s = 255f / max;
            fr = Math.min(255, Math.round(fr * s));
            fg = Math.min(255, Math.round(fg * s));
            fb = Math.min(255, Math.round(fb * s));
        }
        int blended = (fr << 16) | (fg << 8) | fb;
        // キャッシュ保存 (RenderChunkRegion でも Level 経由で保存)
        if (lvlForCache != null && lvlForCache.hasChunkAt(shadedPos)) {
            LevelChunk chunkPut = lvlForCache.getChunkAt(shadedPos);
            var optPut = chunkPut.getCapability(BambooCapabilities.COLORED_LIGHT);
            if (optPut.isPresent()) {
                var storagePut = optPut.orElse(null);
                if (storagePut != null) {
                    synchronized (storagePut.getTintCache()) {
                        storagePut.getTintCache().put(shadedPos.asLong(), blended & 0xFFFFFF);
                    }
                }
            }
        }
        float r = (fr & 0xFF) / 255f;
        float g = (fg & 0xFF) / 255f;
        float b = (fb & 0xFF) / 255f;
        return new Vector3f(r, g, b);
    }
}
