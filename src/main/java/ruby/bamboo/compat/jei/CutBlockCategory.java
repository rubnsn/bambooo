package ruby.bamboo.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooItems;

/**
 * JEI カテゴリ: カットブロック (作業台)。
 * JEIのバニラ作業台タブに加え、専用タブでも表示して確実に見つかるようにする。
 * <p>
 * JEI 19 (1.21): レシピは {@link RecipeHolder} で渡される。
 */
public class CutBlockCategory implements IRecipeCategory<RecipeHolder<CraftingRecipe>> {

    public static final RecipeType<RecipeHolder<CraftingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "cut_block"));

    public static final int WIDTH = 120;
    public static final int HEIGHT = 54;

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;
    private final IGuiHelper guiHelper;

    public CutBlockCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BambooItems.COMMON_KATANA.get()));
        this.title = Component.translatable("jei.bamboomod.cut_block");
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CraftingRecipe> holder, IFocusGroup focuses) {
        CraftingRecipe recipe = holder.value();
        // 入力2つを並べて表示 (左側)
        var ingredients = recipe.getIngredients();
        for (int i = 0; i < ingredients.size() && i < 2; i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 10 + i * 18, 18)
                    .addIngredients(ingredients.get(i))
                    .addTooltipCallback((view, tooltip) -> {
                        // 刀は消費されないことを補足 (全レシピ共通)
                        // 個別スロットでは判定できないため、カテゴリ全体の説明として draw で表示
                    });
        }
        // 出力 (右側)
        ItemStack result = recipe.getResultItem(Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.registryAccess()
                : net.minecraft.core.RegistryAccess.EMPTY);
        // result が empty の場合は getResultItem(EMPTY) で再試行
        if (result.isEmpty()) {
            try {
                result = recipe.getResultItem(net.minecraft.core.RegistryAccess.EMPTY);
            } catch (Exception e) {}
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 86, 18).addItemStack(result);
    }

    @Override
    public void draw(RecipeHolder<CraftingRecipe> holder, IRecipeSlotsView view, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        try {
            guiHelper.getRecipeArrow().draw(guiGraphics, 48, 18);
        } catch (Exception e) {}
        var font = Minecraft.getInstance().font;
        // 下部に補足テキスト
        guiGraphics.drawString(font, Component.translatable("jei.tooltip.cut_block.katana_not_consumed"), 10, 40, 0x555555, false);
        guiGraphics.drawString(font, Component.translatable("jei.tooltip.cut_block.any_full_cube"), 10, 49, 0x555555, false);
    }
}
