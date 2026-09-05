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
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.crafting.grind.BambooGrindRecipe;

/**
 * JEI カテゴリ: 石臼 (millstone) 。
 * 1入力 → 1出力 + ボーナス(確率) を表示。
 * <p>
 * JEI 19 (1.21): レシピは {@link RecipeHolder} で渡される。
 */
@SuppressWarnings("removal")
public class MillstoneCategory implements IRecipeCategory<RecipeHolder<BambooGrindRecipe>> {

    public static final RecipeType<RecipeHolder<BambooGrindRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "millstone"));

    public static final int WIDTH = 110;
    public static final int HEIGHT = 40;

    private final IDrawable background;
    private final IDrawable icon;
    private final IGuiHelper guiHelper;
    private final Component title;

    public MillstoneCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BambooBlocks.MILLSTONE.get()));
        this.title = Component.translatable("jei.bamboomod.millstone");
    }

    @Override
    public RecipeType<RecipeHolder<BambooGrindRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    @SuppressWarnings("removal")
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<BambooGrindRecipe> holder, IFocusGroup focuses) {
        BambooGrindRecipe recipe = holder.value();
        // 入力 (count表示のため ItemStack 化)
        java.util.List<ItemStack> inputStacks = new java.util.ArrayList<>();
        for (ItemStack s : recipe.ingredient().getItems()) {
            ItemStack copy = s.copy();
            copy.setCount(recipe.inputCount());
            inputStacks.add(copy);
        }
        if (inputStacks.isEmpty()) {
            // Ingredient が空でもスロットは確保 (tag の場合など getItems が空になることがある)
            builder.addSlot(RecipeIngredientRole.INPUT, 10, 12).addIngredients(recipe.ingredient());
        } else {
            builder.addSlot(RecipeIngredientRole.INPUT, 10, 12).addItemStacks(inputStacks);
        }

        // メイン出力
        ItemStack result = recipe.getResultItem(RegistryAccess.EMPTY);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 56, 12).addItemStack(result);

        // ボーナス出力 (確率付き)
        if (recipe.hasBonus()) {
            ItemStack bonus = recipe.bonus();
            builder.addSlot(RecipeIngredientRole.OUTPUT, 82, 12)
                    .addItemStack(bonus)
                    .addTooltipCallback((view, tooltip) -> {
                        int pct = Math.round(recipe.bonusChance() * 100);
                        tooltip.add(Component.translatable("tooltip.bamboomod.millstone.bonus", bonus.getHoverName().getString(), pct));
                    });
        }
    }

    @Override
    @SuppressWarnings("removal")
    public void draw(RecipeHolder<BambooGrindRecipe> holder, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        BambooGrindRecipe recipe = holder.value();
        // 矢印
        try {
            guiHelper.getRecipeArrow().draw(guiGraphics, 32, 12);
        } catch (Exception ignored) {
        }
        // ボーナス確率をスロット下に小さく表示 (ツールチップに加え視覚的にも)
        if (recipe.hasBonus()) {
            var font = Minecraft.getInstance().font;
            String pct = Math.round(recipe.bonusChance() * 100) + "%";
            // ボーナススロット (82,12) の下中央に表示
            guiGraphics.drawString(font, pct, 84, 30, 0x555555, false);
        }
        // 入力カウントが1より大きい場合は入力スロット下に "xN" を表示 (JEIのカウント表示だけでは気づきにくいため)
        if (recipe.inputCount() > 1) {
            var font = Minecraft.getInstance().font;
            String count = "x" + recipe.inputCount();
            guiGraphics.drawString(font, count, 10, 30, 0x555555, false);
        }
    }
}
