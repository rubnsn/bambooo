package ruby.bamboo.crafting.grind;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

/**
 * 石臼レシピ (旧 IGrindRecipe / GrindRecipe 相当)。
 * <p>
 * output は必ず入手。bonus は完成時に {@code rand.nextFloat() <= bonusWeight} で抽選される。
 *
 * @param input       入力条件
 * @param output      メイン出力 (100% 入手)
 * @param bonus       ボーナス出力 (確率)。無い場合は null
 * @param bonusWeight ボーナス確率
 */
public record GrindRecipe(GrindInput input, ItemStack output, @Nullable ItemStack bonus, float bonusWeight) {

    public boolean hasBonus() {
        return bonus != null && !bonus.isEmpty();
    }
}