package ruby.bamboo.gacha;

import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ガチャのサーバー権威ロジック (単発・GUIなし・カプセル排出)。
 * <p>
 * マシンはコイン1枚でカプセル1個 (赤60/青30/黄9/虹1) を排出し、
 * 中身の抽選はカプセル所持者の右クリック時にカプセル色ごとの期待値で行う。
 * pending保持はしない (ブロック破壊時は投入済みコインだけ返却する)。
 */
public final class GachaManager {
    private GachaManager() {
    }

    /** 中身抽選の結果 (レアリティ+現物)。 */
    public record SingleResult(GachaRarity rarity, ItemStack stack) {
    }

    /** 枠内weight抽選。 */
    public static ItemStack rollEntry(GachaRarity rarity, RandomSource random) {
        List<GachaEntry> table = GachaTableLoader.INSTANCE.table(rarity);
        int total = 0;
        for (GachaEntry e : table) {
            total += e.weight;
        }
        if (total <= 0) {
            return new ItemStack(Items.STONE, 8);
        }
        int t = random.nextInt(total);
        for (GachaEntry e : table) {
            t -= e.weight;
            if (t < 0) {
                ItemStack s = e.roll(random);
                if (s != null && !s.isEmpty()) {
                    return s;
                }
                return new ItemStack(Items.STONE, 8);
            }
        }
        return new ItemStack(Items.STONE, 8);
    }

    /** カプセル開封時の中身抽選 (カプセル色ごとの期待値)。 */
    public static SingleResult rollContentResult(GachaCapsule capsule,
            RandomSource random) {
        GachaRarity r = capsule.rollContent(random);
        return new SingleResult(r, rollEntry(r, random));
    }
}
