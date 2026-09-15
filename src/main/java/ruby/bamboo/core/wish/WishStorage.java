package ruby.bamboo.core.wish;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * 願い成功回数の保存。成功率 100/(count+1) の分母として使う。
 * 成功時のみ増加・ベッド起床で1減少 (0止まり)。死亡・リログで維持 (Attachment + copyOnDeath)。
 * <p>
 * 1.21.1 NeoForge: 旧 Forge Capability + LazyOptional を Player Attachment へ移行。
 * {@code BambooCapabilities.WISH} に
 * {@code AttachmentType.serializable(WishStorage::new).copyOnDeath()} で登録する。
 * NeoForge の INBTSerializable は provider 付きのため両方実装し、実体は引数なし版。
 */
public class WishStorage implements INBTSerializable<CompoundTag> {

    private int count;

    public int getCount() {
        return Math.max(0, count);
    }

    public void setCount(int v) {
        count = Math.max(0, v);
    }

    public int increment() {
        if (count < Integer.MAX_VALUE) {
            count++;
        }
        return getCount();
    }

    /** 1減少。0以下にはならない。減少したら true。 */
    public boolean decrement() {
        if (count > 0) {
            count--;
            return true;
        }
        return false;
    }

    /** Attachment 永続化 (NeoForge INBTSerializable、provider は未使用)。 */
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return serializeNBT();
    }

    /** Attachment 復元 (NeoForge INBTSerializable、provider は未使用)。 */
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        deserializeNBT(nbt);
    }

    /** 実体。 */
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("wish_count", getCount());
        return tag;
    }

    /** 実体。 */
    public void deserializeNBT(CompoundTag tag) {
        count = tag.contains("wish_count") ? Math.max(0, tag.getInt("wish_count")) : 0;
    }
}
