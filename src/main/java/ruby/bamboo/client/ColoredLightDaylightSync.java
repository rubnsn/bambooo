package ruby.bamboo.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.config.ColoredLightConfig;
import ruby.bamboo.core.init.BambooCapabilities;

/**
 * 昼光抑制の時刻追従: 時刻バケット変化でtintキャッシュを破棄し、
 * 光源近傍セクションを近接優先・分散dirtyで再焼き込みする。
 * <p>
 * 時刻はバニラ同期済み (クライアントローカル) のため新規パケットなし。
 * 1tickの負荷集中を避けるため、dirtyキューを毎tick上限数ずつ近接順に処理し、
 * 遠方チャンクは自動的に数tick遅延される。バニラ・Embeddium両対応
 * ({@code setSectionDirty} はSodium系も中継するForge標準経路)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ColoredLightDaylightSync {

    private ColoredLightDaylightSync() {
    }

    private static final Deque<SectionPos> DIRTY_QUEUE = new ArrayDeque<>();
    private static ClientLevel queueLevel = null;
    private static int lastLevel = -1;

    /** 設定画面からの即時反映要求 (次tickで全再評価) */
    public static void requestResync() {
        lastLevel = -1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.levelRenderer == null) {
            DIRTY_QUEUE.clear();
            queueLevel = null;
            lastLevel = -1;
            return;
        }
        if (level != queueLevel) {
            DIRTY_QUEUE.clear();
            queueLevel = level;
            lastLevel = -1;
        }
        // 分散処理: 近接順キューの先頭から上限数ずつdirty化
        int perTick;
        try {
            perTick = Math.max(1, ColoredLightConfig.CLIENT.sectionsPerTick.get());
        } catch (Exception e) {
            perTick = 6;
        }
        for (int i = 0; i < perTick && !DIRTY_QUEUE.isEmpty(); i++) {
            SectionPos s = DIRTY_QUEUE.pollFirst();
            try {
                // setSectionDirty はセクション座標系 (ブロック座標ではない)。
                // ブロック座標を渡すと存在しないセクションとして無言破棄される
                mc.levelRenderer.setSectionDirty(s.getX(), s.getY(), s.getZ());
            } catch (Exception ignored) {
            }
        }

        boolean enabled;
        int levels;
        int dirtyRadius;
        try {
            enabled = ColoredLightConfig.CLIENT.daylightSuppressEnabled.get();
            levels = Math.max(2, ColoredLightConfig.CLIENT.daylightLevels.get());
            dirtyRadius = Math.max(4, ColoredLightConfig.CLIENT.dirtyRadius.get());
        } catch (Exception e) {
            return;
        }
        if (!enabled) return;
        // 量子化昼光レベル変化時のみ再焼き込み。日中・夜間のフラット域は無コスト
        int daylight;
        try {
            daylight = ruby.bamboo.util.ColoredLightUtil.daylightLevel(level, levels);
        } catch (Exception e) {
            return;
        }
        if (daylight == lastLevel) return;
        lastLevel = daylight;
        onBucketChanged(level, mc, dirtyRadius);
    }

    /**
     * バケット変化時: 読込済み近傍チャンクのtintキャッシュを全破棄し、
     * 光源近傍セクションを近接順でキューイング (遠方は後続tickに遅延)。
     */
    private static void onBucketChanged(ClientLevel level, Minecraft mc, int dirtyRadius) {
        BlockPos playerPos;
        try {
            playerPos = mc.player.blockPosition();
        } catch (Exception e) {
            return;
        }
        int renderChunks = 8;
        try {
            renderChunks = Math.max(2, Math.min(8, mc.options.renderDistance().get()));
        } catch (Exception ignored) {
        }
        int pcx = playerPos.getX() >> 4;
        int pcz = playerPos.getZ() >> 4;
        List<BlockPos> lights = new ArrayList<>();
        for (int cx = pcx - renderChunks; cx <= pcx + renderChunks; cx++) {
            for (int cz = pcz - renderChunks; cz <= pcz + renderChunks; cz++) {
                LevelChunk chunk;
                try {
                    if (!level.hasChunk(cx, cz)) continue;
                    chunk = level.getChunk(cx, cz);
                } catch (Exception e) {
                    continue;
                }
                var opt = chunk.getCapability(BambooCapabilities.COLORED_LIGHT);
                if (!opt.isPresent()) continue;
                var storage = opt.orElse(null);
                if (storage == null) continue;
                try {
                    storage.invalidateTintCache();
                } catch (Exception ignored) {
                }
                // マップは非永続＋遅延スキャンのため、焼き込みを経ていないと空。
                // dirtyを積む前にここでスキャンして光源を再収集する
                try {
                    ruby.bamboo.util.ColoredLightUtil.ensureChunkScanned(chunk, level);
                } catch (Exception ignored) {
                }
                try {
                    var map = storage.getMap();
                    if (map != null && !map.isEmpty()) {
                        for (var e : map.object2IntEntrySet()) {
                            lights.add(BlockPos.of(e.getKey().longValue()));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (lights.isEmpty()) return;
        int secR = Math.max(1, (dirtyRadius + 15) >> 4);
        List<SectionPos> sections = new ArrayList<>();
        for (BlockPos light : lights) {
            int lcx = light.getX() >> 4, lcy = light.getY() >> 4, lcz = light.getZ() >> 4;
            for (int sx = lcx - secR; sx <= lcx + secR; sx++) {
                for (int sy = lcy - secR; sy <= lcy + secR; sy++) {
                    for (int sz = lcz - secR; sz <= lcz + secR; sz++) {
                        if (sy < level.getMinSection() || sy > level.getMaxSection()) continue;
                        sections.add(SectionPos.of(sx, sy, sz));
                    }
                }
            }
        }
        sections.sort(Comparator.comparingDouble(s -> s.distToCenterSqr(
                playerPos.getX() + 0.5, playerPos.getY() + 0.5, playerPos.getZ() + 0.5)));
        DIRTY_QUEUE.clear();
        int cap = 1024;
        for (SectionPos s : sections) {
            if (DIRTY_QUEUE.size() >= cap) break;
            DIRTY_QUEUE.addLast(s);
        }
    }
}
