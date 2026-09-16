package ruby.bamboo.core.init;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.ZabutonColor;
import ruby.bamboo.item.BambooFoodItem;
import ruby.bamboo.item.BambooFoods;
import ruby.bamboo.item.BambooItem;
import ruby.bamboo.item.Rawrice;
import ruby.bamboo.item.Straw;
import ruby.bamboo.skill.SkillType;

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
    public static final DeferredItem<BambooItem> BAMBOO = register("bamboo", () -> new BambooItem(new Item.Properties()));

    /** ワラ */
    public static final DeferredItem<Straw> STRAW = register("straw", () -> new Straw(new Item.Properties()));

    /** 生米 */
    public static final DeferredItem<Rawrice> RAW_RICE = register("rawrice", () -> new Rawrice(new Item.Properties()));

    /** 稲の種 (旧 RiceSeed / rice_seed → riceseed) */
    public static final DeferredItem<ItemNameBlockItem> RICE_SEED = register("riceseed",
            () -> new ItemNameBlockItem(BambooBlocks.RICE_PLANT.get(), new Item.Properties()));

    /** トマト (種兼用食料3/0.6) */
    public static final DeferredItem<ItemNameBlockItem> TOMATO = register("tomato",
            () -> new ItemNameBlockItem(BambooBlocks.TOMATO_PLANT.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(3).saturationModifier(0.6f).build())));

    /** 豆 (種兼用食料2/0.4) */
    public static final DeferredItem<ItemNameBlockItem> BEAN = register("bean",
            () -> new ItemNameBlockItem(BambooBlocks.BEAN_PLANT.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(2).saturationModifier(0.4f).build())));

    /** 小麦粉 (石臼で小麦→小麦粉。非食用の中間素材) */
    public static final DeferredItem<Item> FLOUR = register("flour",
            () -> new Item(new Item.Properties()));

    /** 生地 (小麦粉+水バケツ。非食用の中間素材、ピザ用) */
    public static final DeferredItem<Item> DOUGH = register("dough",
            () -> new Item(new Item.Properties()));

    /** 海藻 (釣りハズレ枠で入手する非食用素材) */
    public static final DeferredItem<Item> SEAWEED = register("seaweed",
            () -> new Item(new Item.Properties()));

    /** 扇子 (旧 FoldingFan。耐久100、風で葉破壊) */
    public static final DeferredItem<ruby.bamboo.item.FoldingFan> FOLDING_FAN = register("foldingfan",
            () -> new ruby.bamboo.item.FoldingFan(new Item.Properties().durability(100)));

    /** 袋 (旧 Sack。BlockItem 収容アイテム) */
    public static final DeferredItem<ruby.bamboo.item.Sack> SACK = register("sack",
            () -> new ruby.bamboo.item.Sack(new Item.Properties()));

    /** 刀 (旧 CommonKatana。鈎縄統合版。耐久無し) */
    public static final DeferredItem<ruby.bamboo.item.CommonKatana> COMMON_KATANA = register("commonkatana",
            () -> new ruby.bamboo.item.CommonKatana(net.minecraft.world.item.Tiers.IRON,
                    new Item.Properties()));

    // ===== 手裏剣 (旧 Shuriken, stone/iron/diamond) =====

    /** 石手裏剣 */
    public static final DeferredItem<ruby.bamboo.item.ShurikenItem> SHURIKEN_STONE = register("shuriken_stone",
            () -> new ruby.bamboo.item.ShurikenItem(net.minecraft.world.item.Tiers.STONE, new Item.Properties().stacksTo(64), 2.0F));
    /** 鉄手裏剣 */
    public static final DeferredItem<ruby.bamboo.item.ShurikenItem> SHURIKEN_IRON = register("shuriken_iron",
            () -> new ruby.bamboo.item.ShurikenItem(net.minecraft.world.item.Tiers.IRON, new Item.Properties().stacksTo(64), 3.5F));
    /** ダイヤ手裏剣 */
    public static final DeferredItem<ruby.bamboo.item.ShurikenItem> SHURIKEN_DIAMOND = register("shuriken_diamond",
            () -> new ruby.bamboo.item.ShurikenItem(net.minecraft.world.item.Tiers.DIAMOND, new Item.Properties().stacksTo(64), 5.0F));

    /** 手裏剣腕輪 (旧 NinjaBracelet。耐久384、即射+クール20tick) */
    public static final DeferredItem<ruby.bamboo.item.NinjaBraceletItem> NINJA_BRACELET = register("ninja_bracelet",
            () -> new ruby.bamboo.item.NinjaBraceletItem(new Item.Properties().durability(384)));

    /** 田んぼクワ (sakura PaddyFieldHoe。DIAMOND相当、maxStack 1) */
    public static final DeferredItem<ruby.bamboo.item.PaddyFieldHoeItem> PADDY_FIELD_HOE = register("paddy_field_hoe",
            () -> new ruby.bamboo.item.PaddyFieldHoeItem(new Item.Properties()));

    /** 庭スコップ (花壇変換用。IRON相当) */
    public static final DeferredItem<ruby.bamboo.item.GardenSpadeItem> GARDEN_SPADE = register("garden_spade",
            () -> new ruby.bamboo.item.GardenSpadeItem(new Item.Properties()));

    // ===== アクセサリ3種 (装備で常時効果、stacksTo1、レシピ無し) =====

    /** 磁石 (8tick毎に周囲アイテム・XPを回収) */
    public static final DeferredItem<ruby.bamboo.item.ItemMagnetItem> ITEM_MAGNET = register("item_magnet",
            () -> new ruby.bamboo.item.ItemMagnetItem(new Item.Properties().stacksTo(1)));

    /** 登攀爪 (壁登り。スニーク=解除) */
    public static final DeferredItem<ruby.bamboo.item.ClimbingClawItem> CLIMBING_CLAW = register("climbing_claw",
            () -> new ruby.bamboo.item.ClimbingClawItem(new Item.Properties().stacksTo(1)));

    /** 水上歩行 (水上を歩く。スニーク=解除) */
    public static final DeferredItem<ruby.bamboo.item.WaterWalkerItem> WATER_WALKER = register("water_walker",
            () -> new ruby.bamboo.item.WaterWalkerItem(new Item.Properties().stacksTo(1)));

    // ===== かんしゃく玉 5種 =====

    /** かんしゃく玉(小)。導火線20tick、ブロック破壊なし */
    public static final DeferredItem<ruby.bamboo.item.FirecrackerItem> FIRECRACKER_S = register("firecracker_s",
            () -> new ruby.bamboo.item.FirecrackerItem(ruby.bamboo.item.FirecrackerItem.Type.S, new Item.Properties()));
    /** かんしゃく玉(中)。導火線80tick、TNT相当 */
    public static final DeferredItem<ruby.bamboo.item.FirecrackerItem> FIRECRACKER_M = register("firecracker_m",
            () -> new ruby.bamboo.item.FirecrackerItem(ruby.bamboo.item.FirecrackerItem.Type.M, new Item.Properties()));
    /** かんしゃく玉(大)。導火線80tick、耐爆性無視 */
    public static final DeferredItem<ruby.bamboo.item.FirecrackerItem> FIRECRACKER_L = register("firecracker_l",
            () -> new ruby.bamboo.item.FirecrackerItem(ruby.bamboo.item.FirecrackerItem.Type.L, new Item.Properties()));
    /** 粘着かんしゃく玉(中) */
    public static final DeferredItem<ruby.bamboo.item.FirecrackerItem> FIRECRACKER_M_STICKY = register("firecracker_m_sticky",
            () -> new ruby.bamboo.item.FirecrackerItem(ruby.bamboo.item.FirecrackerItem.Type.M_STICKY, new Item.Properties()));
    /** 粘着かんしゃく玉(大) */
    public static final DeferredItem<ruby.bamboo.item.FirecrackerItem> FIRECRACKER_L_STICKY = register("firecracker_l_sticky",
            () -> new ruby.bamboo.item.FirecrackerItem(ruby.bamboo.item.FirecrackerItem.Type.L_STICKY, new Item.Properties()));

    // ===== 座布団16色・掛け軸 =====

    /** 座布団 16色 (ZabutonColor順) */
    public static final List<DeferredItem<ruby.bamboo.item.ZabutonItem>> ZABUTONS = createZabutons();

    private static List<DeferredItem<ruby.bamboo.item.ZabutonItem>> createZabutons() {
        List<DeferredItem<ruby.bamboo.item.ZabutonItem>> list = new ArrayList<>();
        for (ZabutonColor color : ZabutonColor.values()) {
            DeferredItem<ruby.bamboo.item.ZabutonItem> ro = register("zabuton_" + color.registryName(),
                    () -> new ruby.bamboo.item.ZabutonItem(color, new Item.Properties()));
            list.add(ro);
        }
        return List.copyOf(list);
    }

    /** 掛け軸 (壁掛けEntity、24柄ランダム) */
    public static final DeferredItem<ruby.bamboo.item.KakezikuItem> KAKEZIKU = register("kakeziku",
            () -> new ruby.bamboo.item.KakezikuItem(new Item.Properties()));

    // ===== 釣り (竹竿・釣りエサ・ルアー3種) =====

    /** 竹竿 (耐久64、パワーゲージ式) */
    public static final DeferredItem<ruby.bamboo.item.BambooRodItem> BAMBOO_ROD = register("bamboo_rod",
            () -> new ruby.bamboo.item.BambooRodItem(new Item.Properties().durability(64)));

    /** 釣りエサ (消耗品) */
    public static final DeferredItem<ruby.bamboo.item.FishingBaitItem> FISHING_BAIT = register("fishing_bait",
            () -> new ruby.bamboo.item.FishingBaitItem(new Item.Properties()));

    /** 木ルアー (耐久32、バイト+2) */
    public static final DeferredItem<ruby.bamboo.item.LureItem> LURE_WOOD = register("lure_wood",
            () -> new ruby.bamboo.item.LureItem(new Item.Properties().durability(32), 2));
    /** 鉄ルアー (耐久64、バイト+4) */
    public static final DeferredItem<ruby.bamboo.item.LureItem> LURE_IRON = register("lure_iron",
            () -> new ruby.bamboo.item.LureItem(new Item.Properties().durability(64), 4));
    /** ダイヤルアー (耐久128、バイト+6) */
    public static final DeferredItem<ruby.bamboo.item.LureItem> LURE_DIAMOND = register("lure_diamond",
            () -> new ruby.bamboo.item.LureItem(new Item.Properties().durability(128), 6));

    // ===== スキル本13種・ステータス本 =====

    /** ステータス本 (スキル確認GUI) */
    public static final DeferredItem<ruby.bamboo.item.StatusBookItem> STATUS_BOOK = register("status_book",
            () -> new ruby.bamboo.item.StatusBookItem(new Item.Properties().stacksTo(1)));

    /** スキル本 13種 */
    public static final List<DeferredItem<ruby.bamboo.item.SkillBookItem>> SKILL_BOOKS = createSkillBooks();

    private static List<DeferredItem<ruby.bamboo.item.SkillBookItem>> createSkillBooks() {
        List<DeferredItem<ruby.bamboo.item.SkillBookItem>> list = new ArrayList<>();
        for (SkillType type : SkillType.values()) {
            DeferredItem<ruby.bamboo.item.SkillBookItem> ro = register("skill_book_" + type.getId(),
                    () -> new ruby.bamboo.item.SkillBookItem(type, new Item.Properties().durability(5)));
            list.add(ro);
        }
        return List.copyOf(list);
    }

    // ===== トランプミニゲーム4種 =====

    /** ソリティア */
    public static final DeferredItem<ruby.bamboo.item.SolitaireItem> SOLITAIRE = register("solitaire",
            () -> new ruby.bamboo.item.SolitaireItem(new Item.Properties().stacksTo(1)));
    /** 大富豪 */
    public static final DeferredItem<ruby.bamboo.item.DaifugoItem> DAIFUGO = register("daifugo",
            () -> new ruby.bamboo.item.DaifugoItem(new Item.Properties().stacksTo(1)));
    /** フリーセル */
    public static final DeferredItem<ruby.bamboo.item.FreeCellItem> FREECELL = register("freecell",
            () -> new ruby.bamboo.item.FreeCellItem(new Item.Properties().stacksTo(1)));
    /** ブラックジャック */
    public static final DeferredItem<ruby.bamboo.item.BlackjackItem> BLACKJACK = register("blackjack",
            () -> new ruby.bamboo.item.BlackjackItem(new Item.Properties().stacksTo(1)));

    /** 願いワンド (デバッグ用。右クリックで願い発動、耐久1) */
    public static final DeferredItem<ruby.bamboo.item.WishWandItem> WISH_WAND = register("wish_wand",
            () -> new ruby.bamboo.item.WishWandItem(new Item.Properties().durability(1)));

    // ===== 弓矢 (旧 BambooBow + arrow 6種の 1.20.1 移植。アンチ矢はオミット) =====

    /** 竹弓 (旧 BambooBow)。耐久400 */
    public static final DeferredItem<ruby.bamboo.item.BambooBowItem> BAMBOO_BOW = register("bamboobow",
            () -> new ruby.bamboo.item.BambooBowItem(new Item.Properties().durability(400)));

    /** 竹矢 (旧 BambooArrow。連射あり) */
    public static final DeferredItem<ruby.bamboo.item.arrow.BambooArrowItem> BAMBOO_ARROW = register("bambooarrow",
            () -> new ruby.bamboo.item.arrow.BambooArrowItem(new Item.Properties()));

    /** 松明矢 (旧 TorchArrow) */
    public static final DeferredItem<ruby.bamboo.item.arrow.TorchArrowItem> TORCH_ARROW = register("torcharrow",
            () -> new ruby.bamboo.item.arrow.TorchArrowItem(new Item.Properties()));

    /** 軽量矢 (旧 LightArrow) */
    public static final DeferredItem<ruby.bamboo.item.arrow.LightArrowItem> LIGHT_ARROW = register("lightarrow",
            () -> new ruby.bamboo.item.arrow.LightArrowItem(new Item.Properties()));

    /** 爆発矢 (旧 ExplodeArrow) */
    public static final DeferredItem<ruby.bamboo.item.arrow.ExplodeArrowItem> EXPLODE_ARROW = register("explodearrow",
            () -> new ruby.bamboo.item.arrow.ExplodeArrowItem(new Item.Properties()));

    // ===== 食料 43種 (旧 BambooFood meta0-42 → 独立アイテム化) =====
    // バランス補正は BambooFoods enum 側で適用済み (docs/port-spec-bamboofood.md §3.3)
    // クリエイティブ末尾配置のため、素材アイテムの後に登録する
    public static final List<DeferredItem<BambooFoodItem>> BAMBOO_FOODS = createBambooFoods();

    private static List<DeferredItem<BambooFoodItem>> createBambooFoods() {
        List<DeferredItem<BambooFoodItem>> list = new ArrayList<>();
        for (BambooFoods food : BambooFoods.values()) {
            DeferredItem<BambooFoodItem> ro = register(food.registryName(),
                    () -> new BambooFoodItem(new Item.Properties().food(food.foodProperties())));
            list.add(ro);
        }
        return List.copyOf(list);
    }

    private static <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> factory) {
        DeferredItem<I> item = BambooMod.ITEMS.register(name, factory);
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
