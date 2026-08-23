package ruby.bamboo.core.init;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;

/**
 * 囲炉裏レシピを実績/進捗なしでレシピブックに表示するための自動アンロック。
 * vanillaは拾得トリガーでunlockする必要があるが、Bambooは初回ログイン時に全解除する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID)
public final class BambooRecipeUnlocker {

    private BambooRecipeUnlocker() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            awardCampfireRecipes(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            awardCampfireRecipes(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            awardCampfireRecipes(sp);
        }
    }

    /**
     * 全囲炉裏レシピを付与。既知なら何もしない (ServerRecipeBook#addRecipes がcontainsチェック)。
     */
    public static void awardCampfireRecipes(ServerPlayer player) {
        if (BambooMod.CAMPFIRE_RECIPE_TYPE.get() == null) return;
        var mgr = player.server.getRecipeManager();
        try {
            var recipes = mgr.getAllRecipesFor(BambooMod.CAMPFIRE_RECIPE_TYPE.get());
            if (!recipes.isEmpty()) {
                // wildcardキャスト: Collection<BambooCampfireRecipe> -> Collection<Recipe<?>>
                @SuppressWarnings("unchecked")
                java.util.Collection<net.minecraft.world.item.crafting.Recipe<?>> asRecipes =
                        (java.util.Collection<net.minecraft.world.item.crafting.Recipe<?>>) (java.util.Collection<?>) recipes;
                player.awardRecipes(asRecipes);
            }
        } catch (Exception e) {
            BambooMod.LOGGER.debug("Failed to award campfire recipes: {}", e.getMessage());
        }
    }
}
