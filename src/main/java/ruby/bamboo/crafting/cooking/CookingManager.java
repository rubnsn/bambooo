package ruby.bamboo.crafting.cooking;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 囲炉裏 (Campfire) のレシピ管理 (旧 CookingManager の移植)。
 * <p>
 * 旧版は ShapedOreRecipe/ShapelessOreRecipe をラップしていたが、1.20.1 では
 * コード登録式の静的管理に整理した (GrindManager と同じ方針)。
 * <p>
 * 検索順:
 * <ol>
 * <li>素材が1個だけ → バニラ精錬レシピ (RecipeType.SMELTING) を試す</li>
 * <li>登録済み調理レシピ (CookingRecipe) を matches() で順次判定</li>
 * </ol>
 */
public final class CookingManager {

    private static final List<CookingRecipe> RECIPES = new ArrayList<>();

    private CookingManager() {
    }

    // ===== 登録 =====

    /** 調理レシピ登録 (fuelCost/cookTime は既定200) */
    public static void addRecipe(ItemStack output, ItemStack... ingredients) {
        addRecipe(output, 200, 200, ingredients);
    }

    /** 調理レシピ登録 (fuelCost/cookTime 指定) */
    public static void addRecipe(ItemStack output, int fuelCost, int cookTime, ItemStack... ingredients) {
        RECIPES.add(new CookingRecipe(output, ingredients, fuelCost, cookTime));
    }

    // ===== 検索 =====

    /**
     * マトリクス (9スロット) に対応するレシピを返す。
     * <p>
     * 素材が1個だけの場合はバニラ精錬レシピを最初に試す (旧 FurnaceRecipes 互換)。
     * それ以外は登録済み調理レシピを判定する。
     */
    @Nullable
    public static CookingRecipe findMatchingRecipe(ItemStack[] matrix, Level level) {
        // 素材数カウント
        int itemCount = 0;
        ItemStack single = null;
        for (ItemStack slot : matrix) {
            if (!slot.isEmpty()) {
                if (itemCount == 0) {
                    single = slot;
                }
                itemCount++;
            }
        }

        // 素材1個だけ → バニラ精錬レシピ
        if (itemCount == 1 && single != null) {
            var smelting = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new net.minecraft.world.SimpleContainer(single), level);
            if (smelting.isPresent()) {
                ItemStack result = smelting.get().getResultItem(level.registryAccess());
                if (!result.isEmpty()) {
                    return new CookingRecipe(result.copy(), new ItemStack[] { single }, 200, 200);
                }
            }
        }

        // 登録済み調理レシピ
        for (CookingRecipe recipe : RECIPES) {
            if (recipe.matches(matrix)) {
                return recipe;
            }
        }
        return null;
    }

    /** 登録済み全レシピ (デバッグ用) */
    public static List<CookingRecipe> getRecipes() {
        return RECIPES;
    }

    /**
     * 指定アイテムを出力するレシピの材料の種類数 (最大値)。なければ0。
     * スキル成長率の料理回復用。
     */
    public static int countDistinctIngredients(ItemStack output) {
        int best = 0;
        for (CookingRecipe r : RECIPES) {
            if (!ItemStack.isSameItem(r.output(), output)) {
                continue;
            }
            java.util.Set<net.minecraft.world.item.Item> kinds = new java.util.HashSet<>();
            for (ItemStack ing : r.ingredients()) {
                if (!ing.isEmpty()) {
                    kinds.add(ing.getItem());
                }
            }
            best = Math.max(best, kinds.size());
        }
        return best;
    }
}