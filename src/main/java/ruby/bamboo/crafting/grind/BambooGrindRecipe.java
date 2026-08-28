package ruby.bamboo.crafting.grind;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import ruby.bamboo.BambooMod;

/**
 * 石臼のバニラ準拠レシピ (1入力→1出力+ボーナスランダム)。
 * 旧GrindRecipeの後継。RecipeManagerで管理されレシピブックに対応。
 * bonusChanceは0でボーナス無し扱い。
 */
public class BambooGrindRecipe implements Recipe<Container> {

    public enum Category {
        FOOD, BLOCKS, MISC;
        public static Category fromString(String s) {
            return switch (s.toLowerCase()) {
                case "food" -> FOOD;
                case "blocks" -> BLOCKS;
                case "misc" -> MISC;
                default -> MISC;
            };
        }
        public String serializedName() { return name().toLowerCase(); }
    }

    private final ResourceLocation id;
    private final String group;
    private final Category category;
    private final Ingredient ingredient;
    private final int inputCount;
    private final ItemStack result;
    private final ItemStack bonus;
    private final float bonusChance;

    public BambooGrindRecipe(ResourceLocation id, String group, Category category,
                             Ingredient ingredient, int inputCount,
                             ItemStack result, ItemStack bonus, float bonusChance) {
        this.id = id;
        this.group = group;
        this.category = category;
        this.ingredient = ingredient;
        this.inputCount = Math.max(1, inputCount);
        this.result = result;
        this.bonus = bonus == null ? ItemStack.EMPTY : bonus;
        this.bonusChance = bonusChance;
    }

    public Category category() { return category; }
    public Ingredient ingredient() { return ingredient; }
    public int inputCount() { return inputCount; }
    public float bonusChance() { return bonusChance; }
    public boolean hasBonus() { return !bonus.isEmpty() && bonusChance > 0; }
    public ItemStack bonus() { return bonus.copy(); }

    @Override
    public boolean matches(Container container, Level level) {
        if (container.getContainerSize() == 0) return false;
        ItemStack stack = container.getItem(0);
        if (stack.isEmpty() || stack.getCount() < inputCount) return false;
        return ingredient.test(stack);
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return true; }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) { return result.copy(); }

    @Override
    public ResourceLocation getId() { return id; }

    @Override
    public RecipeSerializer<?> getSerializer() { return BambooMod.MILLSTONE_SERIALIZER.get(); }

    @Override
    public RecipeType<?> getType() { return BambooMod.MILLSTONE_RECIPE_TYPE.get(); }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(ingredient);
        return list;
    }

    @Override
    public String getGroup() { return group; }

    @Override
    public boolean isSpecial() { return true; }

    public static class Serializer implements RecipeSerializer<BambooGrindRecipe> {

        @Override
        public BambooGrindRecipe fromJson(ResourceLocation id, JsonObject json) {
            String group = GsonHelper.getAsString(json, "group", "");
            String catStr = GsonHelper.getAsString(json, "category", "misc");
            Category cat = Category.fromString(catStr);
            Ingredient ing;
            if (json.has("ingredient")) {
                ing = Ingredient.fromJson(json.get("ingredient"));
            } else if (json.has("input")) {
                ing = Ingredient.fromJson(json.get("input"));
            } else {
                throw new com.google.gson.JsonParseException("Missing ingredient for millstone recipe");
            }
            int count = GsonHelper.getAsInt(json, "count", 1);
            if (json.has("inputCount")) count = GsonHelper.getAsInt(json, "inputCount", count);
            JsonObject resultObj = GsonHelper.getAsJsonObject(json, "result");
            ItemStack result = ShapedRecipe.itemStackFromJson(resultObj);
            ItemStack bonus = ItemStack.EMPTY;
            float chance = 0;
            if (json.has("bonus")) {
                JsonObject bonusObj = GsonHelper.getAsJsonObject(json, "bonus");
                bonus = ShapedRecipe.itemStackFromJson(bonusObj);
                chance = GsonHelper.getAsFloat(json, "bonusChance", 0);
                if (json.has("bonus_chance")) chance = GsonHelper.getAsFloat(json, "bonus_chance", chance);
            }
            if (result.isEmpty()) throw new com.google.gson.JsonParseException("Result empty for millstone recipe");
            return new BambooGrindRecipe(id, group, cat, ing, count, result, bonus, chance);
        }

        @Override
        public BambooGrindRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            String group = buf.readUtf();
            Category cat = buf.readEnum(Category.class);
            Ingredient ing = Ingredient.fromNetwork(buf);
            int count = buf.readVarInt();
            ItemStack result = buf.readItem();
            ItemStack bonus = buf.readItem();
            float chance = buf.readFloat();
            return new BambooGrindRecipe(id, group, cat, ing, count, result, bonus, chance);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, BambooGrindRecipe recipe) {
            buf.writeUtf(recipe.group);
            buf.writeEnum(recipe.category);
            recipe.ingredient.toNetwork(buf);
            buf.writeVarInt(recipe.inputCount);
            buf.writeItem(recipe.result);
            buf.writeItem(recipe.bonus);
            buf.writeFloat(recipe.bonusChance);
        }
    }
}
