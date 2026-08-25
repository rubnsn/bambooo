package ruby.bamboo.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
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
import net.minecraft.world.item.crafting.Ingredient;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.crafting.cooking.BambooCampfireRecipe;

/**
 * JEI カテゴリ: 囲炉裏 (campfire) 。
 * 3x3 shapeless + fuelCost / cookingTime / experience を表示。
 */
@SuppressWarnings("removal")
public class CampfireCategory implements IRecipeCategory<BambooCampfireRecipe> {

    @SuppressWarnings("removal")
    public static final RecipeType<BambooCampfireRecipe> TYPE =
            new RecipeType<>(new ResourceLocation(BambooMod.MODID, "campfire"), BambooCampfireRecipe.class);

    public static final int WIDTH = 116;
    public static final int HEIGHT = 62;

    private final IDrawable background;
    private final IDrawable icon;
    private final IGuiHelper guiHelper;
    private final Component title;

    public CampfireCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BambooBlocks.CAMPFIRE.get()));
        this.title = Component.translatable("jei.bamboomod.campfire");
    }

    @Override
    public RecipeType<BambooCampfireRecipe> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, BambooCampfireRecipe recipe, IFocusGroup focuses) {
        // 3x3 入力 (左上 3,3 を原点に 18px 間隔)。ingredients.size() は 1-9
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            Ingredient ing = recipe.getIngredients().get(i);
            int x = (i % 3) * 18 + 3;
            int y = (i / 3) * 18 + 3;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y).addIngredients(ing);
        }
        // 出力 (右側中央)
        ItemStack result = recipe.getResultItem(RegistryAccess.EMPTY);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 91, 19).addItemStack(result);
    }

    @Override
    @SuppressWarnings("removal")
    public void draw(BambooCampfireRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 矢印 (crafting grid -> result)
        try {
            guiHelper.getRecipeArrow().draw(guiGraphics, 61, 19);
        } catch (Exception e) {
            // ignore
        }

        // 下部に fuelCost / cookingTime / experience を小さく表示
        var font = Minecraft.getInstance().font;
        String fuelText = "Fuel: " + recipe.fuelCost();
        String timeText = "Time: " + recipe.cookingTime();
        if (recipe.experience() > 0) {
            String expText = String.format("%.2f xp", recipe.experience());
            guiGraphics.drawString(font, expText, 63, 44, 0x555555, false);
        }
        guiGraphics.drawString(font, fuelText, 3, 58 - 8, 0x555555, false);
        guiGraphics.drawString(font, timeText, 63, 58 - 8, 0x555555, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, BambooCampfireRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        // 矢印ホバーで shapeless を示す (JEI の shapelessIndicator 相当の簡易表示)
        if (61 <= mouseX && mouseX < 85 && 19 <= mouseY && mouseY < 36) {
            tooltip.add(Component.translatable("jei.tooltip.shapeless.recipe"));
        }
    }
}
