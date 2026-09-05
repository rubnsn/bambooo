package ruby.bamboo.capability;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Phase B: sparse保存用Capability storage + Phase B-2 tintCache.
 * lightMap: key=BlockPos.asLong() (lightPos) value=0xRRGGBB (光源色)
 * tintCache: key=BlockPos.asLong() (shadedPos) value=0xRRGGBB (計算済みtint、白は未登録で省略)
 * tintCacheは永続化しない派生キャッシュ。lightMap更新時に周辺3x3 chunkのcacheをclearする。
 *
 * <p>1.21.1 NeoForge: 旧 Forge Capability (INBTSerializable) を外し、通常クラス化。
 * AttachmentType には serialize 無し (メモリ保持のみ) で登録する — 非永続化設計のため
 * (保存しない dev専用、再起動後は再スキャン)。public メソッド名は維持。
 */
public class ColoredLightStorage {

    private final Object2IntMap<Long> map = new Object2IntOpenHashMap<>();
    private final Long2IntMap tintCache = new Long2IntOpenHashMap();
    private int version = 0;
    private boolean scanned = false;

    public Object2IntMap<Long> getMap() {
        return map;
    }

    public Long2IntMap getTintCache() {
        return tintCache;
    }

    public int getVersion() {
        return version;
    }

    public void incrementVersion() {
        version++;
    }

    public void invalidateTintCache() {
        tintCache.clear();
    }

    public boolean isScanned() {
        return scanned;
    }

    public void setScanned(boolean v) {
        this.scanned = v;
    }

    // 非永続化: 保存しない (dev専用、再起動後は再スキャン)。旧INBTSerializable互換のため残置。
    public CompoundTag serializeNBT() {
        return new CompoundTag();
    }

    // 非永続化: 読み込まない。旧セーブがあっても無視し、初回 getTint 時の遅延スキャンで再構築する。旧INBTSerializable互換のため残置。
    public void deserializeNBT(CompoundTag n) {
        map.clear();
        tintCache.clear();
        scanned = false;
    }
}
