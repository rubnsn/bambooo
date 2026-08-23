package ruby.bamboo.crafting.grind;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 石臼レシピ管理 (旧 GrindManager 相当)。
 * <p>
 * 旧版は HashMap<IGrindInputItem, IGrindRecipe> で「アイテム+damage → ワイルドカード →
 * 鉱石辞書」の順に検索していたが、1.20.1 ではメタデータが廃止されたため
 * 「アイテム一致 → タグ一致」の2段階検索に整理した。
 * <p>
 * レシピの登録は {@link ruby.bamboo.crafting.BambooRecipes} から
 * FMLCommonSetupEvent 内で行う (RegistryObject 解決後に ItemStack を生成できるように)。
 */
public final class GrindManager {

    private static final Map<Item, GrindRecipe> ITEM_RECIPES = new HashMap<>();
    private static final Map<TagKey<Item>, GrindRecipe> TAG_RECIPES = new HashMap<>();

    private GrindManager() {
    }

    // ===== 登録 (旧 addRecipe オーバーロード群の整理版) =====

    /** アイテム一致レシピ (ボーナス無し) */
    public static void addItemRecipe(Item input, int count, ItemStack output) {
        addItemRecipe(input, count, output, null, 0.0F);
    }

    /** アイテム一致レシピ (ボーナス付き) */
    public static void addItemRecipe(Item input, int count, ItemStack output, @Nullable ItemStack bonus,
            float bonusWeight) {
        ITEM_RECIPES.put(input, new GrindRecipe(GrindInput.of(input, count), output, bonus, bonusWeight));
    }

    /** タグ一致レシピ (ボーナス無し) */
    public static void addTagRecipe(TagKey<Item> input, int count, ItemStack output) {
        addTagRecipe(input, count, output, null, 0.0F);
    }

    /** タグ一致レシピ (ボーナス付き) */
    public static void addTagRecipe(TagKey<Item> input, int count, ItemStack output, @Nullable ItemStack bonus,
            float bonusWeight) {
        TAG_RECIPES.put(input, new GrindRecipe(GrindInput.ofTag(input, count), output, bonus, bonusWeight));
    }

    // ===== 検索 =====

    /**
     * 入力スタックに対応するレシピを返す (旧 getOutput 相当)。
     * <p>
     * 検索順: アイテム一致 → タグ一致。
     * レシピの必要個数 (input.count) を満たさない場合は null を返す。
     */
    @Nullable
    public static GrindRecipe getOutput(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        GrindRecipe recipe = ITEM_RECIPES.get(stack.getItem());
        if (recipe != null) {
            return recipe.input().count() <= stack.getCount() ? recipe : null;
        }
        for (Map.Entry<TagKey<Item>, GrindRecipe> e : TAG_RECIPES.entrySet()) {
            if (stack.is(e.getKey())) {
                recipe = e.getValue();
                return recipe.input().count() <= stack.getCount() ? recipe : null;
            }
        }
        return null;
    }

    /**
     * 個数条件を無視してアイテムに対応するレシピを返す。
     * <p>
     * 粉砕完成時の再検索用 (旧 grindItem() が stackSize=64 のダミーで検索していた部分の代替)。
     * 粉砕開始時に記録したアイテムからレシピを引き直すために使用する。
     */
    @Nullable
    public static GrindRecipe getByItem(Item item) {
        GrindRecipe recipe = ITEM_RECIPES.get(item);
        if (recipe != null) {
            return recipe;
        }
        for (Map.Entry<TagKey<Item>, GrindRecipe> e : TAG_RECIPES.entrySet()) {
            if (item.builtInRegistryHolder().is(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 登録済み全レシピ (JEI連携・デバッグ用) */
    public static Collection<GrindRecipe> getRecipes() {
        return Collections.unmodifiableCollection(ITEM_RECIPES.values());
    }
}