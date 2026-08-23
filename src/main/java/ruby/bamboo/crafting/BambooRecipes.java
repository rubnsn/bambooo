package ruby.bamboo.crafting;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooItems;
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
        // 刀の特殊ドロップ表登録 (EntityType → loot_table 対応)
        event.enqueueWork(ruby.bamboo.item.katana.KatanaDrops::register);
        // 囲炉裏レシピはJSON化 (bamboomod:campfire)。旧CookingManagerコード登録は廃止。
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

    // 囲炉裏レシピはJSON (data/bamboomod/recipes/campfire/*.json) で管理。
    // 旧addCookingRecipe / registerBambooFoodRecipes はBambooCampfireRecipe(JSON)へ移行済み。
    // 未移植作物(soy_beans/red_beans/zunda/seaweed等)依存レシピは作物移植後にJSON追加する。
}
