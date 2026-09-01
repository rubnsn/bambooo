package ruby.bamboo.core.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.item.BambooFoodItem;
import ruby.bamboo.item.BambooFoods;
import ruby.bamboo.item.BambooItem;
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

    /** NBT付きItemStackを直接クリエタブへ追加 (ミニチュアのサイズ違い等) */
    public static void addCreativeStack(Supplier<ItemStack> stack) {
        CREATIVE_ITEMS.add(stack);
    }

    // ===== 素材アイテム =====

    /** 竹 (ワールド上の竹ブロックを壊すと落ちる。無機能・植えられない素材アイテム) */
    public static final RegistryObject<BambooItem> BAMBOO = register("bamboo", () -> new BambooItem(new Item.Properties()));

    /** ワラ */
    public static final RegistryObject<Straw> STRAW = register("straw", () -> new Straw(new Item.Properties()));

    /** 生米 */
    public static final RegistryObject<Rawrice> RAW_RICE = register("rawrice", () -> new Rawrice(new Item.Properties()));

    /** 稲の種 (旧 RiceSeed / rice_seed → riceseed) */
    public static final RegistryObject<ItemNameBlockItem> RICE_SEED = register("riceseed",
            () -> new ItemNameBlockItem(BambooBlocks.RICE_PLANT.get(), new Item.Properties()));

    /** 扇子 (旧 FoldingFan。耐久100、風で葉破壊) */
    public static final RegistryObject<ruby.bamboo.item.FoldingFan> FOLDING_FAN = register("foldingfan",
            () -> new ruby.bamboo.item.FoldingFan(new Item.Properties().durability(100)));

    /** 袋 (旧 Sack。BlockItem 収容アイテム) */
    public static final RegistryObject<ruby.bamboo.item.Sack> SACK = register("sack",
            () -> new ruby.bamboo.item.Sack(new Item.Properties()));

    /** 刀 (旧 CommonKatana。鈎縄統合版。耐久無し) */
    public static final RegistryObject<ruby.bamboo.item.CommonKatana> COMMON_KATANA = register("commonkatana",
            () -> new ruby.bamboo.item.CommonKatana(net.minecraft.world.item.Tiers.IRON,
                    new Item.Properties()));

    // ===== 手裏剣 (旧 Shuriken, stone/iron/diamond) =====

    /** 石手裏剣 */
    public static final RegistryObject<ruby.bamboo.item.ShurikenItem> SHURIKEN_STONE = register("shuriken_stone",
            () -> new ruby.bamboo.item.ShurikenItem(net.minecraft.world.item.Tiers.STONE, new Item.Properties().stacksTo(64), 2.0F));
    /** 鉄手裏剣 */
    public static final RegistryObject<ruby.bamboo.item.ShurikenItem> SHURIKEN_IRON = register("shuriken_iron",
            () -> new ruby.bamboo.item.ShurikenItem(net.minecraft.world.item.Tiers.IRON, new Item.Properties().stacksTo(64), 3.5F));
    /** ダイヤ手裏剣 */
    public static final RegistryObject<ruby.bamboo.item.ShurikenItem> SHURIKEN_DIAMOND = register("shuriken_diamond",
            () -> new ruby.bamboo.item.ShurikenItem(net.minecraft.world.item.Tiers.DIAMOND, new Item.Properties().stacksTo(64), 5.0F));

    /** 手裏剣腕輪 (旧 NinjaBracelet。耐久384、即射+クール20tick) */
    public static final RegistryObject<ruby.bamboo.item.NinjaBraceletItem> NINJA_BRACELET = register("ninja_bracelet",
            () -> new ruby.bamboo.item.NinjaBraceletItem(new Item.Properties().durability(384)));

    /** 田んぼクワ (sakura PaddyFieldHoe。DIAMOND相当、maxStack 1) */
    public static final RegistryObject<ruby.bamboo.item.PaddyFieldHoeItem> PADDY_FIELD_HOE = register("paddy_field_hoe",
            () -> new ruby.bamboo.item.PaddyFieldHoeItem(new Item.Properties()));

    /** 願いワンド (デバッグ用。右クリックで願い発動、耐久1) */
    public static final RegistryObject<ruby.bamboo.item.WishWandItem> WISH_WAND = register("wish_wand",
            () -> new ruby.bamboo.item.WishWandItem(new Item.Properties().durability(1)));

    // ===== 釣り (Stardew Valley風) =====

    /** 竹竿 */
    public static final RegistryObject<ruby.bamboo.item.BambooRodItem> BAMBOO_ROD = register("bamboo_rod",
            () -> new ruby.bamboo.item.BambooRodItem(new Item.Properties().durability(64)));

    /** 釣りエサ (消耗品, バイトパワー6) */
    public static final RegistryObject<ruby.bamboo.item.FishingBaitItem> FISHING_BAIT = register("fishing_bait",
            () -> new ruby.bamboo.item.FishingBaitItem(new Item.Properties()));

    /** ルアー (木製, バイトパワー2, 耐久32) */
    public static final RegistryObject<ruby.bamboo.item.LureItem> LURE_WOOD = register("lure_wood",
            () -> new ruby.bamboo.item.LureItem(new Item.Properties().durability(32), 2));

    /** ルアー (鉄製, バイトパワー4, 耐久64) */
    public static final RegistryObject<ruby.bamboo.item.LureItem> LURE_IRON = register("lure_iron",
            () -> new ruby.bamboo.item.LureItem(new Item.Properties().durability(64), 4));

    /** ルアー (ダイヤ製, バイトパワー6, 耐久128) */
    public static final RegistryObject<ruby.bamboo.item.LureItem> LURE_DIAMOND = register("lure_diamond",
            () -> new ruby.bamboo.item.LureItem(new Item.Properties().durability(128), 6));

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
