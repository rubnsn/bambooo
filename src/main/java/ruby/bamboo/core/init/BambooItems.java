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

    /** 袋 (旧 Sack。BlockItem 収容アイテム) */
    public static final RegistryObject<ruby.bamboo.item.Sack> SACK = register("sack",
            () -> new ruby.bamboo.item.Sack(new Item.Properties()));

    /** 刀 (旧 CommonKatana。鈎縄統合版。耐久無し) */
    public static final RegistryObject<ruby.bamboo.item.CommonKatana> COMMON_KATANA = register("commonkatana",
            () -> new ruby.bamboo.item.CommonKatana(net.minecraft.world.item.Tiers.IRON,
                    new Item.Properties()));

    // ===== 弓矢 (旧 BambooBow + arrow 6種の 1.20.1 移植。アンチ矢はオミット) =====

    /** 竹弓 (旧 BambooBow)。耐久400 */
    public static final RegistryObject<ruby.bamboo.item.BambooBowItem> BAMBOO_BOW = register("bamboobow",
            () -> new ruby.bamboo.item.BambooBowItem(new Item.Properties().durability(400)));

    /** 竹矢 (旧 BambooArrow。連射あり) */
    public static final RegistryObject<ruby.bamboo.item.arrow.BambooArrowItem> BAMBOO_ARROW = register("bambooarrow",
            () -> new ruby.bamboo.item.arrow.BambooArrowItem(new Item.Properties()));

    /** 松明矢 (旧 TorchArrow) */
    public static final RegistryObject<ruby.bamboo.item.arrow.TorchArrowItem> TORCH_ARROW = register("torcharrow",
            () -> new ruby.bamboo.item.arrow.TorchArrowItem(new Item.Properties()));

    /** 軽量矢 (旧 LightArrow) */
    public static final RegistryObject<ruby.bamboo.item.arrow.LightArrowItem> LIGHT_ARROW = register("lightarrow",
            () -> new ruby.bamboo.item.arrow.LightArrowItem(new Item.Properties()));

    /** 爆発矢 (旧 ExplodeArrow) */
    public static final RegistryObject<ruby.bamboo.item.arrow.ExplodeArrowItem> EXPLODE_ARROW = register("explodearrow",
            () -> new ruby.bamboo.item.arrow.ExplodeArrowItem(new Item.Properties()));

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
