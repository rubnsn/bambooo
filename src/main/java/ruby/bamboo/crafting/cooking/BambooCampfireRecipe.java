package ruby.bamboo.crafting.cooking;

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
import net.minecraft.world.level.Level;
import ruby.bamboo.BambooMod;

/**
 * 囲炉裏のバニラ準拠レシピ (3x3 shapeless + fuelCost)。
 * <p>
 * 旧 CookingManager.CookingRecipe の後継。vanilla RecipeManager で管理され、
 * レシピブック・JEI・データパックで自動扱いされる。
 */
public class BambooCampfireRecipe implements Recipe<Container> {

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

        public String serializedName() {
            return name().toLowerCase();
        }
    }

    private final ResourceLocation id;
    private final String group;
    private final Category category;
    private final NonNullList<Ingredient> ingredients;
    private final ItemStack result;
    private final float experience;
    private final int cookingTime;
    private final int fuelCost;

    public BambooCampfireRecipe(ResourceLocation id, String group, Category category,
                                NonNullList<Ingredient> ingredients, ItemStack result,
                                float experience, int cookingTime, int fuelCost) {
        this.id = id;
        this.group = group;
        this.category = category;
        this.ingredients = ingredients;
        this.result = result;
        this.experience = experience;
        this.cookingTime = cookingTime;
        this.fuelCost = fuelCost > 0 ? fuelCost : 200;
    }

    public Category category() {
        return category;
    }

    public float experience() {
        return experience;
    }

    public int cookingTime() {
        return cookingTime;
    }

    public int fuelCost() {
        return fuelCost;
    }

    @Override
    public boolean matches(Container container, Level level) {
        // shapeless: 容器内の非空スロット数 == ingredients数 かつ全Ingredientが充足
        int nonEmpty = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            // 3x3(0-8)のみ判定。燃料/結果は含めないためサイズが11の場合は先頭9のみ
            if (i >= 9) break;
            if (!container.getItem(i).isEmpty()) nonEmpty++;
        }
        if (nonEmpty != ingredients.size()) return false;
        // ingredients を consumption チェック (shapeless)
        // コンテナ側をリスト化
        java.util.List<ItemStack> inputs = new java.util.ArrayList<>();
        for (int i = 0; i < 9 && i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) inputs.add(s);
        }
        boolean[] used = new boolean[ingredients.size()];
        for (ItemStack stack : inputs) {
            boolean matched = false;
            for (int i = 0; i < ingredients.size(); i++) {
                if (!used[i] && ingredients.get(i).test(stack)) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        for (boolean u : used) if (!u) return false;
        return true;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BambooMod.CAMPFIRE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return BambooMod.CAMPFIRE_RECIPE_TYPE.get();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

    @Override
    public String getGroup() {
        return group;
    }

    /**
     * Serializer (JSON + ネットワーク)。
     * vanilla SimpleCookingSerializer を参考に fuelCost を追加。
     */
    public static class Serializer implements RecipeSerializer<BambooCampfireRecipe> {

        @Override
        public BambooCampfireRecipe fromJson(ResourceLocation id, JsonObject json) {
            String group = GsonHelper.getAsString(json, "group", "");
            String catStr = GsonHelper.getAsString(json, "category", "misc");
            Category cat = Category.fromString(catStr);
            NonNullList<Ingredient> ingredients = itemsFromJson(GsonHelper.getAsJsonArray(json, "ingredients"));
            if (ingredients.isEmpty()) {
                throw new com.google.gson.JsonParseException("No ingredients for campfire recipe");
            } else if (ingredients.size() > 9) {
                throw new com.google.gson.JsonParseException("Too many ingredients for campfire recipe! The max is 9");
            }
            JsonObject resultObj = GsonHelper.getAsJsonObject(json, "result");
            ItemStack result = net.minecraft.world.item.crafting.ShapedRecipe.itemStackFromJson(resultObj);
            float exp = GsonHelper.getAsFloat(json, "experience", 0.0F);
            int cookingTime = GsonHelper.getAsInt(json, "cookingtime", 200);
            int fuelCost = GsonHelper.getAsInt(json, "fuelCost", 200);
            // 後方互換: snake_case
            if (json.has("cooking_time")) cookingTime = GsonHelper.getAsInt(json, "cooking_time", cookingTime);
            if (json.has("fuel_cost")) fuelCost = GsonHelper.getAsInt(json, "fuel_cost", fuelCost);
            return new BambooCampfireRecipe(id, group, cat, ingredients, result, exp, cookingTime, fuelCost);
        }

        private static NonNullList<Ingredient> itemsFromJson(com.google.gson.JsonArray jsonArray) {
            NonNullList<Ingredient> list = NonNullList.create();
            for (int i = 0; i < jsonArray.size(); i++) {
                Ingredient ing = Ingredient.fromJson(jsonArray.get(i));
                if (!ing.isEmpty()) list.add(ing);
            }
            return list;
        }

        @Override
        public BambooCampfireRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            String group = buf.readUtf();
            Category cat = buf.readEnum(Category.class);
            int size = buf.readVarInt();
            NonNullList<Ingredient> ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
            for (int i = 0; i < size; i++) ingredients.set(i, Ingredient.fromNetwork(buf));
            ItemStack result = buf.readItem();
            float exp = buf.readFloat();
            int cookingTime = buf.readVarInt();
            int fuelCost = buf.readVarInt();
            return new BambooCampfireRecipe(id, group, cat, ingredients, result, exp, cookingTime, fuelCost);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, BambooCampfireRecipe recipe) {
            buf.writeUtf(recipe.group);
            buf.writeEnum(recipe.category);
            buf.writeVarInt(recipe.ingredients.size());
            for (Ingredient ing : recipe.ingredients) ing.toNetwork(buf);
            buf.writeItem(recipe.result);
            buf.writeFloat(recipe.experience);
            buf.writeVarInt(recipe.cookingTime);
            buf.writeVarInt(recipe.fuelCost);
        }
    }
}
