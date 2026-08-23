package ruby.bamboo.client.gui;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 囲炉裏用 RecipeBookComponent。
 * <p>
 * AbstractFurnaceRecipeBookComponentは1x1(炉)専用なため、3x3+燃料+結果の囲炉裏用に自前で
 * setupGhostRecipe / filterButtonテクスチャを実装する。
 */
@OnlyIn(Dist.CLIENT)
public class BambooCampfireRecipeBookComponent extends RecipeBookComponent {

    @Nullable
    private Ingredient fuels;

    @Override
    protected void initFilterButtonTextures() {
        // 炉と同じテクスチャ (152,182)
        this.filterButton.initTextureValues(152, 182, 28, 18, RECIPE_BOOK_LOCATION);
    }

    @Override
    public void setupGhostRecipe(Recipe<?> recipe, List<Slot> slots) {
        // slots: 0-8 素材, 9 燃料, 10 結果, 11-46 プレイヤー
        ItemStack result = recipe.getResultItem(this.minecraft.level.registryAccess());
        this.ghostRecipe.setRecipe(recipe);
        // 結果スロット (10)
        this.ghostRecipe.addIngredient(Ingredient.of(result), slots.get(10).x, slots.get(10).y);
        NonNullList<Ingredient> ings = recipe.getIngredients();

        // 燃料スロット (9) 空なら燃料Ingredientをゴースト
        Slot fuelSlot = slots.get(9);
        if (fuelSlot.getItem().isEmpty()) {
            if (this.fuels == null) {
                Set<Item> fuelItems = AbstractFurnaceBlockEntity.getFuel().keySet();
                this.fuels = Ingredient.of(fuelItems.stream().map(ItemStack::new));
            }
            this.ghostRecipe.addIngredient(this.fuels, fuelSlot.x, fuelSlot.y);
        }

        // 3x3素材 (0-8) へ順番に配置
        Iterator<Ingredient> it = ings.iterator();
        for (int i = 0; i < 9; i++) {
            if (!it.hasNext()) break;
            Ingredient ing = it.next();
            if (!ing.isEmpty()) {
                Slot s = slots.get(i);
                this.ghostRecipe.addIngredient(ing, s.x, s.y);
            }
        }
    }
}
