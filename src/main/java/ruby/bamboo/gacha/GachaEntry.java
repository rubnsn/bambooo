package ruby.bamboo.gacha;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 抽選テーブルの1エントリ。JSON: {@code {"id":"minecraft:stone","count":[8,16],"weight":10}}。
 * <p>
 * count は [min,max] または単数。解決失敗 (未登録ID) 時は null を返し、呼び出し側で代替する。
 */
public final class GachaEntry {
    public final ResourceLocation id;
    public final int min;
    public final int max;
    public final int weight;

    public GachaEntry(ResourceLocation id, int min, int max, int weight) {
        this.id = id;
        this.min = Math.max(1, min);
        this.max = Math.max(this.min, max);
        this.weight = Math.max(1, weight);
    }

    /** 個数を決めて ItemStack 化。ID不正なら null。 */
    public ItemStack roll(RandomSource random) {
        var item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return null;
        }
        int n = min >= max ? min : min + random.nextInt(max - min + 1);
        n = Math.min(n, item.getMaxStackSize());
        if (n <= 0) {
            return null;
        }
        return new ItemStack(item, n);
    }
}
