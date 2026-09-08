package ruby.bamboo.crafting;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

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
    // 未移植中間素材(tofu_kinu等)・未移植作物(tallgrass代用未定の三色団子等)依存レシピは見送り。
    // soy_beans→bean代用、seaweed→釣りハズレ枠、flour/dough/men新設で旧レシピの大半をJSON復元済み。
}
