package ruby.bamboo.capability;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Phase B: sparse保存用Capability storage + Phase B-2 tintCache.
 * lightMap: key=BlockPos.asLong() (lightPos) value=0xRRGGBB (光源色)
 * tintCache: key=BlockPos.asLong() (shadedPos) value=0xRRGGBB (計算済みtint、白は未登録で省略)
 * tintCacheは永続化しない派生キャッシュ。lightMap更新時に周辺3x3 chunkのcacheをclearする。
 */
public class ColoredLightStorage implements INBTSerializable<CompoundTag> {

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

    @Override
    public CompoundTag serializeNBT() {
        // 非永続化: 保存しない (dev専用、再起動後は再スキャン)
        return new CompoundTag();
    }

    @Override
    public void deserializeNBT(CompoundTag n) {
        // 非永続化: 読み込まない。旧セーブがあっても無視し、初回 getTint 時の遅延スキャンで再構築する
        map.clear();
        tintCache.clear();
        scanned = false;
    }
}
