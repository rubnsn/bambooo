package ruby.bamboo.core.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.item.BambooFoodItem;
import ruby.bamboo.item.BambooFoods;
import ruby.bamboo.item.Rawrice;
import ruby.bamboo.item.Straw;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * アイテム登録。旧 BambooData.@BambooItem + DataLoader の置き換え。
 * <p>
 * CREATIVE_ITEMS はクリエイティブタブの表示内容を制御する(追加順 = 表示順)。
 */
public final class BambooItems {

    /** クリエイティブタブに表示するアイテム (Supplierで遅延解決) */
    public static final List<Supplier<ItemStack>> CREATIVE_ITEMS = new ArrayList<>();

    /**
     * BlockItem 等をクリエイティブタブの表示リストへ追加する。
     * (BambooBlocks 側の登録から呼ばれる)
     * <p>
     * 注意: ForgeHooks は accept 時に count==1 を要求するため、
     * 必ず {@code getDefaultInstance()} (count=1) を渡す。
     */
    public static void addCreative(Supplier<? extends net.minecraft.world.level.ItemLike> item) {
        CREATIVE_ITEMS.add(() -> item.get().asItem().getDefaultInstance());
    }

    // ===== 素材アイテム =====

    /** ワラ */
    public static final RegistryObject<Straw> STRAW = register("straw", () -> new Straw(new Item.Properties()));

    /** 生米 */
    public static final RegistryObject<Rawrice> RAW_RICE = register("rawrice", () -> new Rawrice(new Item.Properties()));

    /** 稲の種 (旧 RiceSeed / rice_seed → riceseed) */
    public static final RegistryObject<ItemNameBlockItem> RICE_SEED = register("riceseed",
            () -> new ItemNameBlockItem(BambooBlocks.RICE_PLANT.get(), new Item.Properties()));

    /** 通帳 (旧 Tudura。無機能素材アイテム、袋のクラフト材料) */
    public static final RegistryObject<ruby.bamboo.item.Tudura> TUDURA = register("tudura",
            () -> new ruby.bamboo.item.Tudura(new Item.Properties()));

    /** 袋 (旧 Sack。BlockItem 収容アイテム) */
    public static final RegistryObject<ruby.bamboo.item.Sack> SACK = register("sack",
            () -> new ruby.bamboo.item.Sack(new Item.Properties()));

    // ===== 食料 43種 (旧 BambooFood meta0-42 → 独立アイテム化) =====
    // バランス補正は BambooFoods enum 側で適用済み (docs/port-spec-bamboofood.md §3.3)
    // クリエイティブ末尾配置のため、素材アイテムの後に登録する
    public static final List<RegistryObject<BambooFoodItem>> BAMBOO_FOODS = createBambooFoods();

    private static List<RegistryObject<BambooFoodItem>> createBambooFoods() {
        List<RegistryObject<BambooFoodItem>> list = new ArrayList<>();
        for (BambooFoods food : BambooFoods.values()) {
            RegistryObject<BambooFoodItem> ro = register(food.registryName(),
                    () -> new BambooFoodItem(new Item.Properties().food(food.foodProperties())));
            list.add(ro);
        }
        return List.copyOf(list);
    }

    private static <I extends Item> RegistryObject<I> register(String name, Supplier<? extends I> factory) {
        RegistryObject<I> item = BambooMod.ITEMS.register(name, factory);
        CREATIVE_ITEMS.add(() -> item.get().getDefaultInstance());
        return item;
    }

    /**
     * 静的初期化保証用ダミー。
     */
    public static void init() {
        // no-op: static フィールド初期化はクラスロード時に行われる
    }
}
