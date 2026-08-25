package ruby.bamboo.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooMenus;
import ruby.bamboo.crafting.cooking.BambooCampfireRecipe;
import ruby.bamboo.crafting.grind.BambooGrindRecipe;
import ruby.bamboo.gui.CampfireScreen;
import ruby.bamboo.gui.MillStoneScreen;

@JeiPlugin
@SuppressWarnings("removal")
public class BambooJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation(BambooMod.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new CampfireCategory(guiHelper),
                new MillstoneCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            // サーバ側やワールド未ロード時は登録できない。JEIはデータリロード時に再呼出されるためここではスキップ
            // フォールバック: シングルサーバーがあればそちらから取得
            var server = minecraft.getSingleplayerServer();
            if (server != null) {
                var mgr = server.getRecipeManager();
                registration.addRecipes(CampfireCategory.TYPE, mgr.getAllRecipesFor(BambooMod.CAMPFIRE_RECIPE_TYPE.get()));
                registration.addRecipes(MillstoneCategory.TYPE, mgr.getAllRecipesFor(BambooMod.MILLSTONE_RECIPE_TYPE.get()));
            }
            return;
        }
        var mgr = minecraft.level.getRecipeManager();
        registration.addRecipes(CampfireCategory.TYPE, mgr.getAllRecipesFor(BambooMod.CAMPFIRE_RECIPE_TYPE.get()));
        registration.addRecipes(MillstoneCategory.TYPE, mgr.getAllRecipesFor(BambooMod.MILLSTONE_RECIPE_TYPE.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(BambooBlocks.CAMPFIRE.get()), CampfireCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(BambooBlocks.MILLSTONE.get()), MillstoneCategory.TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // クリック領域: GUIテクスチャ上の矢印/炉部分をクリックでJEIを開く
        registration.addRecipeClickArea(CampfireScreen.class, 90, 35, 23, 16, CampfireCategory.TYPE);
        registration.addRecipeClickArea(MillStoneScreen.class, 80, 28, 16, 16, MillstoneCategory.TYPE);
        // 入力スロットクリックでも拾えるように追加
        registration.addRecipeClickArea(MillStoneScreen.class, 80, 9, 16, 16, MillstoneCategory.TYPE);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // 囲炉裏: 0-8 素材 (3x3) → プレイヤーINV 11-46 (36スロット)
        registration.addRecipeTransferHandler(ruby.bamboo.gui.CampfireMenu.class, BambooMenus.CAMPFIRE.get(), CampfireCategory.TYPE, 0, 9, 11, 36);
        // 石臼: 0 入力1 → プレイヤーINV 3-38 (36スロット)
        registration.addRecipeTransferHandler(ruby.bamboo.gui.MillStoneMenu.class, BambooMenus.MILL_STONE.get(), MillstoneCategory.TYPE, 0, 1, 3, 36);
    }
}
