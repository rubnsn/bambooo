package ruby.bamboo.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooItems;
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
    public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration registration) {
        // cut_block / miniature は NBT で区別されるため、JEI の重複警告を解消し正しく表示するために NBT をサブタイプとして扱う
        try {
            registration.useNbtForSubtypes(BambooBlocks.CUT_BLOCK.get().asItem());
        } catch (Exception e) {}
        try {
            registration.useNbtForSubtypes(BambooBlocks.MINIATURE.get().asItem());
        } catch (Exception e) {}
        try {
            // 袋やカットブロックの NBT バリアントを持つアイテムも念のため
            registration.useNbtForSubtypes(BambooItems.SACK.get());
        } catch (Exception e) {}
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new CampfireCategory(guiHelper),
                new MillstoneCategory(guiHelper),
                new CutBlockCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var minecraft = Minecraft.getInstance();
        // JEI専用カットブロックレシピ (レベル未依存で生成可能)
        List<net.minecraft.world.item.crafting.CraftingRecipe> cutJei = List.of();
        try {
            cutJei = CutBlockJeiRecipes.createJeiRecipes();
            BambooMod.LOGGER.info("CutBlock JEI dummy recipes generated: {}", cutJei.size());
        } catch (Exception e) {
            BambooMod.LOGGER.warn("Failed to generate CutBlock JEI recipes", e);
        }

        if (minecraft.level == null) {
            var server = minecraft.getSingleplayerServer();
            if (server != null) {
                var mgr = server.getRecipeManager();
                registration.addRecipes(CampfireCategory.TYPE, mgr.getAllRecipesFor(BambooMod.CAMPFIRE_RECIPE_TYPE.get()));
                registration.addRecipes(MillstoneCategory.TYPE, mgr.getAllRecipesFor(BambooMod.MILLSTONE_RECIPE_TYPE.get()));
            }
            // JEI表示専用 — CRAFTINGへの二重登録はDecoder(12)の原因のためcut_blockのみに
            if (!cutJei.isEmpty()) {
                registration.addRecipes(CutBlockCategory.TYPE, cutJei);
                BambooMod.LOGGER.info("CutBlock JEI recipes registered to cut_block only (server fallback): {}", cutJei.size());
            }
            return;
        }
        var mgr = minecraft.level.getRecipeManager();
        registration.addRecipes(CampfireCategory.TYPE, mgr.getAllRecipesFor(BambooMod.CAMPFIRE_RECIPE_TYPE.get()));
        registration.addRecipes(MillstoneCategory.TYPE, mgr.getAllRecipesFor(BambooMod.MILLSTONE_RECIPE_TYPE.get()));
        if (!cutJei.isEmpty()) {
            registration.addRecipes(CutBlockCategory.TYPE, cutJei);
            BambooMod.LOGGER.info("CutBlock JEI recipes registered to cut_block only: {}", cutJei.size());
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(BambooBlocks.CAMPFIRE.get()), CampfireCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(BambooBlocks.MILLSTONE.get()), MillstoneCategory.TYPE);
        // カットブロック専用カテゴリの触媒は作業台のみ（左ペインに透明カットブロックが出ないように）
        registration.addRecipeCatalyst(new ItemStack(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE), CutBlockCategory.TYPE);
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

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        // 空のカットブロック(透明)は入手不可ダミーなので JEI の材料リストから隠す
        try {
            var ingredientManager = runtime.getIngredientManager();
            var empty = new ItemStack(BambooBlocks.CUT_BLOCK.get());
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.of(empty));
            BambooMod.LOGGER.info("Hid empty cut_block from JEI ingredient list");
        } catch (Exception e) {
            BambooMod.LOGGER.warn("Failed to hide empty cut_block from JEI", e);
        }
    }
}
