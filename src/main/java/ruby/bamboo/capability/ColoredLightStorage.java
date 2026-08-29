package ruby.bamboo.capability;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Phase B: sparse保存用Capability storage.
 * key=BlockPos.asLong(), value=0xRRGGBB
 */
public class ColoredLightStorage implements INBTSerializable<CompoundTag> {

    private final Object2IntMap<Long> map = new Object2IntOpenHashMap<>();

    public Object2IntMap<Long> getMap() {
        return map;
    }

    @Override
    public CompoundTag serializeNBT() {
        if (map == null) {
            return null;
        }
        ListTag list = new ListTag();
        for (Object2IntMap.Entry<Long> e : map.object2IntEntrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("p", e.getKey().longValue());
            t.putInt("c", e.getIntValue());
            list.add(t);
        }
        CompoundTag n = new CompoundTag();
        n.put("Lights", list);
        return n;
    }

    @Override
    public void deserializeNBT(CompoundTag n) {
        if (n == null) {
            return;
        }
        map.clear();
        if (!n.contains("Lights", 9)) {
            return;
        }
        ListTag list = n.getList("Lights", 10);
        for (Tag e : list) {
            CompoundTag t = (CompoundTag) e;
            map.put(Long.valueOf(t.getLong("p")), t.getInt("c"));
        }
    }
}
