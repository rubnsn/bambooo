package ruby.bamboo.core.init;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.item.BambooBowItem;
import ruby.bamboo.item.BambooFoodItem;
import ruby.bamboo.item.BambooFoods;
import ruby.bamboo.item.BambooItem;
import ruby.bamboo.item.BambooRodItem;
import ruby.bamboo.item.ClimbingClawItem;
import ruby.bamboo.item.CommonKatana;
import ruby.bamboo.item.DaifugoItem;
import ruby.bamboo.item.FirecrackerItem;
import ruby.bamboo.item.FishingBaitItem;
import ruby.bamboo.item.FoldingFan;
import ruby.bamboo.item.GardenSpadeItem;
import ruby.bamboo.item.ItemMagnetItem;
import ruby.bamboo.item.LureItem;
import ruby.bamboo.item.NinjaBraceletItem;
import ruby.bamboo.item.PaddyFieldHoeItem;
import ruby.bamboo.item.Rawrice;
import ruby.bamboo.item.Sack;
import ruby.bamboo.item.ShurikenItem;
import ruby.bamboo.item.SkillBookItem;
import ruby.bamboo.item.SolitaireItem;
import ruby.bamboo.item.StatusBookItem;
import ruby.bamboo.item.Straw;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import ruby.bamboo.item.WaterWalkerItem;
import ruby.bamboo.item.WishWandItem;
import ruby.bamboo.item.arrow.BambooArrowItem;
import ruby.bamboo.item.arrow.ExplodeArrowItem;
import ruby.bamboo.item.arrow.LightArrowItem;
import ruby.bamboo.item.arrow.TorchArrowItem;
import ruby.bamboo.skill.SkillType;

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
    public static void addCreative(Supplier<? extends ItemLike> item) {
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

    /** トマト (旧 itemtomato。種兼用食料、ニンジン相当) */
    public static final RegistryObject<ItemNameBlockItem> TOMATO = register("tomato",
            () -> new ItemNameBlockItem(BambooBlocks.TOMATO_PLANT.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(3).saturationMod(0.6f).build())));

    /** 豆 (旧 itembean。種兼用食料) */
    public static final RegistryObject<ItemNameBlockItem> BEAN = register("bean",
            () -> new ItemNameBlockItem(BambooBlocks.BEAN_PLANT.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(2).saturationMod(0.4f).build())));

    /** 小麦粉 (旧 bambooflour。石臼で小麦→小麦粉。非食用の中間素材) */
    public static final RegistryObject<Item> FLOUR = register("flour",
            () -> new Item(new Item.Properties()));

    /** 生地 (旧 bamboodough。小麦粉+水バケツ。非食用の中間素材、ピザ用) */
    public static final RegistryObject<Item> DOUGH = register("dough",
            () -> new Item(new Item.Properties()));

    /** 海藻 (旧 foodSeaweed。釣りのハズレ枠で入手する非食用素材) */
    public static final RegistryObject<Item> SEAWEED = register("seaweed",
            () -> new Item(new Item.Properties()));

    /** 扇子 (旧 FoldingFan。耐久100、風で葉破壊) */
    public static final RegistryObject<FoldingFan> FOLDING_FAN = register("foldingfan",
            () -> new FoldingFan(new Item.Properties().durability(100)));

    /** 袋 (旧 Sack。BlockItem 収容アイテム) */
    public static final RegistryObject<Sack> SACK = register("sack",
            () -> new Sack(new Item.Properties()));

    /** 刀 (旧 CommonKatana。鈎縄統合版。耐久無し) */
    public static final RegistryObject<CommonKatana> COMMON_KATANA = register("commonkatana",
            () -> new CommonKatana(Tiers.IRON,
                    new Item.Properties()));

    // ===== 手裏剣 (旧 Shuriken, stone/iron/diamond) =====

    /** 石手裏剣 */
    public static final RegistryObject<ShurikenItem> SHURIKEN_STONE = register("shuriken_stone",
            () -> new ShurikenItem(Tiers.STONE, new Item.Properties().stacksTo(64), 2.0F));
    /** 鉄手裏剣 */
    public static final RegistryObject<ShurikenItem> SHURIKEN_IRON = register("shuriken_iron",
            () -> new ShurikenItem(Tiers.IRON, new Item.Properties().stacksTo(64), 3.5F));
    /** ダイヤ手裏剣 */
    public static final RegistryObject<ShurikenItem> SHURIKEN_DIAMOND = register("shuriken_diamond",
            () -> new ShurikenItem(Tiers.DIAMOND, new Item.Properties().stacksTo(64), 5.0F));

    /** 手裏剣腕輪 (旧 NinjaBracelet。耐久384、即射+クール20tick) */
    public static final RegistryObject<NinjaBraceletItem> NINJA_BRACELET = register("ninja_bracelet",
            () -> new NinjaBraceletItem(new Item.Properties().durability(384)));

    // ===== かんしゃく玉 (旧 ItemFirecracker meta0-2 → 独立アイテム化 + sticky追加) =====

    /** かんしゃく玉(小)。導火線20tick、ブロック破壊なし */
    public static final RegistryObject<FirecrackerItem> FIRECRACKER_S = register("firecracker_s",
            () -> new FirecrackerItem(FirecrackerItem.Type.S, new Item.Properties()));
    /** かんしゃく玉(中)。導火線60tick、TNT相当 */
    public static final RegistryObject<FirecrackerItem> FIRECRACKER_M = register("firecracker_m",
            () -> new FirecrackerItem(FirecrackerItem.Type.M, new Item.Properties()));
    /** かんしゃく玉(大)。導火線60tick、耐爆性無視 */
    public static final RegistryObject<FirecrackerItem> FIRECRACKER_L = register("firecracker_l",
            () -> new FirecrackerItem(FirecrackerItem.Type.L, new Item.Properties()));
    /** 粘着かんしゃく玉(中)。Mに固着追加 (テクスチャ仮) */
    public static final RegistryObject<FirecrackerItem> FIRECRACKER_M_STICKY = register("firecracker_m_sticky",
            () -> new FirecrackerItem(FirecrackerItem.Type.M_STICKY, new Item.Properties()));
    /** 粘着かんしゃく玉(大)。Lに固着追加 (テクスチャ仮) */
    public static final RegistryObject<FirecrackerItem> FIRECRACKER_L_STICKY = register("firecracker_l_sticky",
            () -> new FirecrackerItem(FirecrackerItem.Type.L_STICKY, new Item.Properties()));

    /** 田んぼクワ (sakura PaddyFieldHoe。DIAMOND相当、maxStack 1) */
    public static final RegistryObject<PaddyFieldHoeItem> PADDY_FIELD_HOE = register("paddy_field_hoe",
            () -> new PaddyFieldHoeItem(new Item.Properties()));

    /** 花壇用スコップ (土・草ブロックを花壇へ変換。IRON相当) */
    public static final RegistryObject<GardenSpadeItem> GARDEN_SPADE = register("garden_spade",
            () -> new GardenSpadeItem(new Item.Properties()));

    // ===== アクセサリ (インベントリ所持で発動。レシピ無し) =====

    /** 磁石 (sakura ItemMagnet。周囲のアイテム・経験値を自動回収) */
    public static final RegistryObject<ItemMagnetItem> ITEM_MAGNET = register("item_magnet",
            () -> new ItemMagnetItem(new Item.Properties().stacksTo(1)));

    /** 鉤爪 (sakura ClimbingClaw。任意の壁を足場相当で登攀) */
    public static final RegistryObject<ClimbingClawItem> CLIMBING_CLAW = register("climbing_claw",
            () -> new ClimbingClawItem(new Item.Properties().stacksTo(1)));

    /** ウォーターウォーカー (sakura WaterWalker。登録名の typo water_warker を修正。水上歩行) */
    public static final RegistryObject<WaterWalkerItem> WATER_WALKER = register("water_walker",
            () -> new WaterWalkerItem(new Item.Properties().stacksTo(1)));

    /** 願いワンド (デバッグ用。右クリックで願い発動、耐久1) */
    public static final RegistryObject<WishWandItem> WISH_WAND = register("wish_wand",
            () -> new WishWandItem(new Item.Properties().durability(1)));

    // ===== 釣り (Stardew Valley風) =====

    /** 竹竿 */
    public static final RegistryObject<BambooRodItem> BAMBOO_ROD = register("bamboo_rod",
            () -> new BambooRodItem(new Item.Properties().durability(64)));

    /** 釣りエサ (消耗品, バイトパワー6) */
    public static final RegistryObject<FishingBaitItem> FISHING_BAIT = register("fishing_bait",
            () -> new FishingBaitItem(new Item.Properties()));

    /** ルアー (木製, バイトパワー2, 耐久32) */
    public static final RegistryObject<LureItem> LURE_WOOD = register("lure_wood",
            () -> new LureItem(new Item.Properties().durability(32), 2));

    /** ルアー (鉄製, バイトパワー4, 耐久64) */
    public static final RegistryObject<LureItem> LURE_IRON = register("lure_iron",
            () -> new LureItem(new Item.Properties().durability(64), 4));

    /** ルアー (ダイヤ製, バイトパワー6, 耐久128) */
    public static final RegistryObject<LureItem> LURE_DIAMOND = register("lure_diamond",
            () -> new LureItem(new Item.Properties().durability(128), 6));

    // ===== 弓矢 (旧 BambooBow + arrow 6種の 1.20.1 移植。アンチ矢はオミット) =====

    /** 竹弓 (旧 BambooBow)。耐久400 */
    public static final RegistryObject<BambooBowItem> BAMBOO_BOW = register("bamboobow",
            () -> new BambooBowItem(new Item.Properties().durability(400)));

    /** 竹矢 (旧 BambooArrow。連射あり) */
    public static final RegistryObject<BambooArrowItem> BAMBOO_ARROW = register("bambooarrow",
            () -> new BambooArrowItem(new Item.Properties()));

    /** 松明矢 (旧 TorchArrow) */
    public static final RegistryObject<TorchArrowItem> TORCH_ARROW = register("torcharrow",
            () -> new TorchArrowItem(new Item.Properties()));

    /** 軽量矢 (旧 LightArrow) */
    public static final RegistryObject<LightArrowItem> LIGHT_ARROW = register("lightarrow",
            () -> new LightArrowItem(new Item.Properties()));

    /** 爆発矢 (旧 ExplodeArrow) */
    public static final RegistryObject<ExplodeArrowItem> EXPLODE_ARROW = register("explodearrow",
            () -> new ExplodeArrowItem(new Item.Properties()));

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

    // ===== スキル本 13種 + ステータス本 (feat-skill) =====

    /** ステータス本 (初スキル取得時に配布 + 常時クラフト可) */
    public static final RegistryObject<StatusBookItem> STATUS_BOOK = register("status_book",
            () -> new StatusBookItem(new Item.Properties().stacksTo(1)));

    /** スキル本 13種 (耐久5)。登録順 = SkillType 順 */
    public static final List<RegistryObject<SkillBookItem>> SKILL_BOOKS = createSkillBooks();

    private static List<RegistryObject<SkillBookItem>> createSkillBooks() {
        List<RegistryObject<SkillBookItem>> list = new ArrayList<>();
        for (SkillType type : SkillType.values()) {
            RegistryObject<SkillBookItem> ro = register("skill_book_" + type.getId(),
                    () -> new SkillBookItem(type, new Item.Properties().durability(5)));
            list.add(ro);
        }
        return List.copyOf(list);
    }

    // ===== ミニゲーム (テスト用) =====

    /** ソリティア起動札 (右クリックでクロンダイクを開く。テクスチャ仮置き) */
    public static final RegistryObject<SolitaireItem> SOLITAIRE = register("solitaire",
            () -> new SolitaireItem(new Item.Properties().stacksTo(1)));

    /** 大富豪札 (右クリックで参加・作成。テクスチャ仮置き) */
    public static final RegistryObject<DaifugoItem> DAIFUGO = register("daifugo",
            () -> new DaifugoItem(new Item.Properties().stacksTo(1)));

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
