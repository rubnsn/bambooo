package ruby.bamboo.crafting.cooking;

import net.minecraft.world.item.ItemStack;

/**
 * 囲炉裏 (Campfire) の調理レシピ。
 * <p>
 * 旧 CookingManager.RecipeEntry 相当。出力・材料・燃料消費量・調理時間を持つ。
 * 材料は ItemStack の配列で、shapeless 判定 (個数とアイテム一致) を行う。
 */
public record CookingRecipe(ItemStack output, ItemStack[] ingredients, int fuelCost, int cookTime) {

    public CookingRecipe {
        // 既定値: 旧 RecipeWrapper 未指定時は fuelCost=200 / cookTime=200
        if (fuelCost <= 0) {
            fuelCost = 200;
        }
        if (cookTime <= 0) {
            cookTime = 200;
        }
    }

    /** 材料がマトリクス (9スロット) に全て含まれるか (shapeless 判定) */
    public boolean matches(ItemStack[] matrix) {
        boolean[] used = new boolean[ingredients.length];
        for (ItemStack slot : matrix) {
            if (slot.isEmpty()) {
                continue;
            }
            boolean matched = false;
            for (int i = 0; i < ingredients.length; i++) {
                if (!used[i] && ItemStack.isSameItemSameComponents(slot, ingredients[i])) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        // 全材料が消費されたか (余分な素材が無いか)
        for (boolean u : used) {
            if (!u) {
                return false;
            }
        }
        return true;
    }
}