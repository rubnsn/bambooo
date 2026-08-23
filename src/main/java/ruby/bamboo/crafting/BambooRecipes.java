package ruby.bamboo.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.crafting.cooking.CookingManager;
import ruby.bamboo.crafting.grind.GrindManager;

/**
 * レシピ登録 (旧 BambooRecipes#addGrindRecipe の移植)。
 * <p>
 * GrindManager は静的マップのため、RegistryObject 解決後に ItemStack を生成できる
 * FMLCommonSetupEvent 内で登録する。
 */
public final class BambooRecipes {

    private BambooRecipes() {
    }

    /** BambooMod コンストラクタから呼ぶ */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BambooRecipes::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(BambooRecipes::addGrindRecipe);
        event.enqueueWork(BambooRecipes::addCookingRecipe);
    }

    /**
     * 石臼レシピ (旧 BambooRecipes#addGrindRecipe の有効8種と同一内容)。
     */
    private static void addGrindRecipe() {
        // 石 → 砂利 (+20%で砂)
        GrindManager.addItemRecipe(Items.STONE, 1, new net.minecraft.world.item.ItemStack(Blocks.GRAVEL),
                new net.minecraft.world.item.ItemStack(Blocks.SAND), 0.2F);
        // 丸石 → 砂利 (+10%で砂)
        GrindManager.addItemRecipe(Items.COBBLESTONE, 1, new net.minecraft.world.item.ItemStack(Blocks.GRAVEL),
                new net.minecraft.world.item.ItemStack(Blocks.SAND), 0.1F);
        // 砂利 → 砂 (+14%で火打石)
        GrindManager.addItemRecipe(Items.GRAVEL, 1, new net.minecraft.world.item.ItemStack(Blocks.SAND),
                new net.minecraft.world.item.ItemStack(Items.FLINT), 0.14F);
        // 骨 → 骨粉×3 (+50%で骨粉×2)
        GrindManager.addItemRecipe(Items.BONE, 1,
                new net.minecraft.world.item.ItemStack(Items.BONE_MEAL, 3),
                new net.minecraft.world.item.ItemStack(Items.BONE_MEAL, 2), 0.5F);
        // ブレイズロッド → ブレイズパウダー×2 (+50%で×1)
        GrindManager.addItemRecipe(Items.BLAZE_ROD, 1,
                new net.minecraft.world.item.ItemStack(Items.BLAZE_POWDER, 2),
                new net.minecraft.world.item.ItemStack(Items.BLAZE_POWDER, 1), 0.5F);
        // 稲の種×4 → 生米×1
        GrindManager.addItemRecipe(BambooItems.RICE_SEED.get(), 4,
                new net.minecraft.world.item.ItemStack(BambooItems.RAW_RICE.get()));
        // 砂岩 → 砂×4
        GrindManager.addItemRecipe(Items.SANDSTONE, 1, new net.minecraft.world.item.ItemStack(Blocks.SAND, 4));
        // オーク/マツ/シラカバ/ジャングルの葉×4 → サボテングリーン×1
        // (旧: LEAVES damage=WILD_CARD = 4種の葉すべてが対象)
        for (var leaves : new net.minecraft.world.item.Item[] { Items.OAK_LEAVES, Items.SPRUCE_LEAVES,
                Items.BIRCH_LEAVES, Items.JUNGLE_LEAVES }) {
            GrindManager.addItemRecipe(leaves, 4,
                    new net.minecraft.world.item.ItemStack(Items.GREEN_DYE));
        }

        BambooMod.LOGGER.info("Registered {} grind recipes", GrindManager.getRecipes().size());
    }

    /**
     * 囲炉裏レシピ (旧 BambooRecipes#addCookingRecipe の移植)。
     * <p>
     * 新modに存在するアイテム(バニラ+移植済みBambooItems/Blocks)のみで
     * 登録可能なレシピを登録する。soy_beans/red_beans/zunda/dough/flour/
     * men/tofu_kinu/seaweed/tomato 等の未移植作物に依存するレシピは
     * 該当アイテム移植後に追加する (TODO)。
     */
    private static void addCookingRecipe() {
        // ===== BambooFood 囲炉裏レシピ (旧BambooRecipesのうち移植済み食材のみ) =====
        // 食材マッピング: crop_rice→RAW_RICE, 竹→BAMBOO, 竹の子→BAMBOO_SHOOT,
        // 餅→bamboofood_mochi, 桜葉→SAKURA_LEAVES
        registerBambooFoodRecipes();

        // ダイヤモンド: 石炭×9 (shaped "XXX"×3行) / fuelCost=101200 / cookTime=1200
        CookingManager.addRecipe(new ItemStack(Items.DIAMOND), 101200, 1200,
                new ItemStack(Items.COAL, 9));

        // indlight×16 各12個 (shaped "XXX"/"XYX"/"XXX" X=色付きガラス Y=グロウストーンダスト)
        // 旧: 色付きガラス8個 + グロウストーンダスト1個 = 9個のマトリクス
        // 新modでは色付きガラス8個 + グロウストーンダスト1個を材料として登録
        for (var indlight : BambooBlocks.INDLIGHTS) {
            var block = (ruby.bamboo.block.IndLightBlock) indlight.get();
            // 色付きガラス (旧 STAINED_GLASS meta=color)
            ItemStack glass = new ItemStack(getStainedGlass(block.color));
            ItemStack glowstone = new ItemStack(Items.GLOWSTONE_DUST);
            // 8個の色付きガラス + 1個のグロウストーンダスト
            ItemStack[] ingredients = new ItemStack[9];
            for (int i = 0; i < 8; i++) {
                ingredients[i] = glass.copy();
            }
            ingredients[8] = glowstone;
            CookingManager.addRecipe(new ItemStack(indlight.get(), 12), ingredients);
        }

        BambooMod.LOGGER.info("Registered {} cooking recipes", CookingManager.getRecipes().size());
    }

    private static void registerBambooFoodRecipes() {
        // ヘルパ: BambooFoods enum → ItemStack
        java.util.function.Function<ruby.bamboo.item.BambooFoods, ItemStack> food = f ->
                new ItemStack(BambooItems.BAMBOO_FOODS.get(f.id).get());
        java.util.function.BiFunction<ruby.bamboo.item.BambooFoods, Integer, ItemStack> foodCount = (f, c) ->
                new ItemStack(BambooItems.BAMBOO_FOODS.get(f.id).get(), c);

        ItemStack rawRice = new ItemStack(BambooItems.RAW_RICE.get());
        ItemStack bamboo = new ItemStack(BambooBlocks.BAMBOO.get().asItem());
        ItemStack bambooShoot = new ItemStack(BambooBlocks.BAMBOO_SHOOT.get().asItem());
        ItemStack sakuraLeaves = new ItemStack(BambooBlocks.SAKURA_LEAVES.get().asItem());

        // 旧BambooRecipesの材料から移植可能なもののみ (soy_beans等の未移植作物を含むレシピはスキップ)
        // 牛飯 id1: 牛肉+crop_rice
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.GYUMESI),
                new ItemStack(Items.BEEF), rawRice.copy());
        // 豚飯 id2: 豚肉+crop_rice
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.BUTAMESI),
                new ItemStack(Items.PORKCHOP), rawRice.copy());
        // きのこ飯 id3: ブラウンマッシュルーム+crop_rice
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.KINOKOMESI),
                new ItemStack(Blocks.BROWN_MUSHROOM.asItem()), rawRice.copy());
        // 豚串 id4 ×2: 豚肉+竹
        CookingManager.addRecipe(foodCount.apply(ruby.bamboo.item.BambooFoods.BUTAKUSI, 2),
                new ItemStack(Items.PORKCHOP), bamboo.copy());
        // 牛串 id5 ×2: 牛肉+竹
        CookingManager.addRecipe(foodCount.apply(ruby.bamboo.item.BambooFoods.GYUKUSI, 2),
                new ItemStack(Items.BEEF), bamboo.copy());
        // 竹めし id6: 竹の子+crop_rice
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.TAKEMESI),
                bambooShoot.copy(), rawRice.copy());
        // 卵かけ id7: 卵+crop_rice
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.TAMAKAKE),
                new ItemStack(Items.EGG), rawRice.copy());
        // 親子丼 id8: 卵+鶏肉+crop_rice
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.OYAKO),
                new ItemStack(Items.EGG), new ItemStack(Items.CHICKEN), rawRice.copy());
        // 鉄火丼 id9: 生魚(fish meta0 → COD)+crop_rice
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.TEKKA),
                new ItemStack(Items.COD), rawRice.copy());
        // 鳥串 id10 ×2: 鶏肉+竹
        CookingManager.addRecipe(foodCount.apply(ruby.bamboo.item.BambooFoods.TORIKUSI, 2),
                new ItemStack(Items.CHICKEN), bamboo.copy());
        // みたらし団子 id19: 餅+竹+砂糖×2 (旧 danmitarashi)
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.DANMITARASHI),
                food.apply(ruby.bamboo.item.BambooFoods.MOCHI), bamboo.copy(),
                new ItemStack(Items.SUGAR), new ItemStack(Items.SUGAR));
        // 桜餅 id30: 餅+桜葉
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.SAKURAMOCHI),
                food.apply(ruby.bamboo.item.BambooFoods.MOCHI), sakuraLeaves.copy());
        // 卵牛飯 id31: 牛肉+crop_rice+卵
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.TAMAGYUMESHI),
                new ItemStack(Items.BEEF), rawRice.copy(), new ItemStack(Items.EGG));
        // カツ丼 id32: 豚肉+crop_rice+卵
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.KATSUDON),
                new ItemStack(Items.PORKCHOP), rawRice.copy(), new ItemStack(Items.EGG));
        // 海鮮親子丼 id42: 焼き魚(fish meta1 → COOKED_COD)+crop_rice
        CookingManager.addRecipe(food.apply(ruby.bamboo.item.BambooFoods.KAISENOYAKO),
                new ItemStack(Items.COOKED_COD), rawRice.copy());

        // TODO(未移植作物依存のためスキップ / 移植後に追加):
        // 梅おろし/鯖おろし/海鮮親子/きのこおろし/竹おろし/若布おろし: seaweed(海藻) 未移植
        // 団子あんこ/みたらし等 soy_beans/red_beans/zunda 依存: だんご(あんこ/きなこ/三色/ずんだ), おはぎ, 納豆系, 赤飯, 豆腐, 揚げ出し, うどん/そば/ラーメン/ピザ 等
        // 例: umeoni, sakeoni, tunaoni, kinooni, takeoni, wakameoni, dananko, dankinako, dansansyoku, danzunda,
        //     ohaanko, ohakinako, ohazunda, natto, nattomeshi, tamanattomeshi, sekihan, onisekihan, tofu, agedashi,
        //     men, udon, soba, ramen, pizza は作物移植後に登録
    }

    /** 色付きガラス (旧 STAINED_GLASS meta 相当) */
    private static net.minecraft.world.level.block.Block getStainedGlass(ruby.bamboo.block.IndLightBlock.DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_STAINED_GLASS;
            case ORANGE -> Blocks.ORANGE_STAINED_GLASS;
            case MAGENTA -> Blocks.MAGENTA_STAINED_GLASS;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_STAINED_GLASS;
            case YELLOW -> Blocks.YELLOW_STAINED_GLASS;
            case LIME -> Blocks.LIME_STAINED_GLASS;
            case PINK -> Blocks.PINK_STAINED_GLASS;
            case GRAY -> Blocks.GRAY_STAINED_GLASS;
            case SILVER -> Blocks.LIGHT_GRAY_STAINED_GLASS;
            case CYAN -> Blocks.CYAN_STAINED_GLASS;
            case PURPLE -> Blocks.PURPLE_STAINED_GLASS;
            case BLUE -> Blocks.BLUE_STAINED_GLASS;
            case BROWN -> Blocks.BROWN_STAINED_GLASS;
            case GREEN -> Blocks.GREEN_STAINED_GLASS;
            case RED -> Blocks.RED_STAINED_GLASS;
            case BLACK -> Blocks.BLACK_STAINED_GLASS;
        };
    }
}
