package ruby.bamboo.core.wish;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 願い成功回数の保存。成功率 100/(count+1) の分母として使う。
 * 成功時のみ増加・ベッド起床で1減少(0止まり)。死亡・リログで維持(Cap+Clone)。
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

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("wish_count", getCount());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        count = tag.contains("wish_count") ? Math.max(0, tag.getInt("wish_count")) : 0;
    }
}
