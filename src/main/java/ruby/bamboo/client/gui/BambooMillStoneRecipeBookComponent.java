package ruby.bamboo.client.gui;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.crafting.grind.BambooGrindRecipe;

/**
 * 石臼用 RecipeBookComponent。 1入力→1出力(+ボーナス)。
 * ボーナスは薄表示(ゴースト自体が半透明) + tooltipで確率表記。
 */
@OnlyIn(Dist.CLIENT)
public class BambooMillStoneRecipeBookComponent extends RecipeBookComponent {

    @Override
    protected void initFilterButtonTextures() {
        this.filterButton.initTextureValues(152, 182, 28, 18, RECIPE_BOOK_LOCATION);
    }

    @Override
    public void setupGhostRecipe(Recipe<?> recipe, List<Slot> slots) {
        // slots: 0入力(80,9) 1出力(58,57) 2ボーナス(102,57) 3-38 player
        this.ghostRecipe.setRecipe(recipe);
        if (recipe instanceof BambooGrindRecipe gr) {
            // 入力
            this.ghostRecipe.addIngredient(gr.ingredient(), slots.get(0).x, slots.get(0).y);
            // メイン出力
            ItemStack result = gr.getResultItem(this.minecraft.level.registryAccess());
            this.ghostRecipe.addIngredient(Ingredient.of(result), slots.get(1).x, slots.get(1).y);
            // ボーナス (薄表示: 通常ゴーストだが存在を示す。確率tooltipはScreen側)
            if (gr.hasBonus()) {
                this.ghostRecipe.addIngredient(Ingredient.of(gr.bonus()), slots.get(2).x, slots.get(2).y);
            }
        } else {
            // フォールバック: 通常Recipe
            ItemStack result = recipe.getResultItem(this.minecraft.level.registryAccess());
            this.ghostRecipe.addIngredient(Ingredient.of(result), slots.get(1).x, slots.get(1).y);
            if (!recipe.getIngredients().isEmpty()) {
                this.ghostRecipe.addIngredient(recipe.getIngredients().get(0), slots.get(0).x, slots.get(0).y);
            }
        }
    }

    /** ボーナス確率取得 (Screenのtooltip用) */
    @Nullable
    public Float getCurrentBonusChance() {
        var rec = this.ghostRecipe.getRecipe();
        if (rec instanceof BambooGrindRecipe gr && gr.hasBonus()) {
            return gr.bonusChance();
        }
        return null;
    }

    /** ボーナススタック取得 (Screenのtooltip用) */
    @Nullable
    public ItemStack getCurrentBonusStack() {
        var rec = this.ghostRecipe.getRecipe();
        if (rec instanceof BambooGrindRecipe gr && gr.hasBonus()) {
            return gr.bonus();
        }
        return null;
    }
}
