package ruby.bamboo.crafting;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

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
        // 石臼・囲炉裏レシピはJSON化 (bamboomod:millstone / bamboomod:campfire)。旧GrindManager/CookingManagerコード登録は廃止。
        // 刀の特殊ドロップ表登録 (EntityType → loot_table 対応)
        event.enqueueWork(ruby.bamboo.item.katana.KatanaDrops::register);
    }

    // 石臼レシピはJSON (data/bamboomod/recipes/millstone/*.json) で管理。旧addGrindRecipeはBambooGrindRecipeへ移行済み。
    // BambooRecipes.addGrindRecipeは完全撤廃 (ランダム報酬はBambooGrindRecipe.bonusChanceで対応)。

    // 囲炉裏レシピはJSON (data/bamboomod/recipes/campfire/*.json) で管理。
    // 旧addCookingRecipe / registerBambooFoodRecipes はBambooCampfireRecipe(JSON)へ移行済み。
    // 未移植作物(soy_beans/red_beans/zunda/seaweed等)依存レシピは作物移植後にJSON追加する。
}
