package ruby.bamboo.crafting.cooking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
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
 * 囲炉裏のバニラ準拠レシピ (3x3 shaped/shapeless + fuelCost)。
 * <p>
 * 旧 CookingManager.CookingRecipe の後継。vanilla RecipeManager で管理され、
 * レシピブック・JEI・データパックで自動扱いされる。
 * <p>
 * 定型(pattern/key)と不定形(ingredients)の両方をサポートする。
 * 旧1.10.2 ShapedOreRecipe/ShapelessOreRecipe と同様に、
 * 定型は空欄(スペース)を含めた3x3位置が厳密に一致する必要がある。
 * 不定形は個数とIngredient一致のみで位置は問わない。
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
    private final NonNullList<Ingredient> ingredients; // shapeless用 or shapedの非空一覧(JEI用)
    private final boolean shaped;
    private final int width;
    private final int height;
    private final NonNullList<Ingredient> pattern; // shapedのみ width*height, 空欄はEMPTY, shapeless時は空
    private final ItemStack result;
    private final float experience;
    private final int cookingTime;
    private final int fuelCost;

    // shapeless用コンストラクタ
    public BambooCampfireRecipe(ResourceLocation id, String group, Category category,
                                NonNullList<Ingredient> ingredients, ItemStack result,
                                float experience, int cookingTime, int fuelCost) {
        this(id, group, category, ingredients, NonNullList.create(), 0, 0, false, result, experience, cookingTime, fuelCost);
    }

    // 共通コンストラクタ
    public BambooCampfireRecipe(ResourceLocation id, String group, Category category,
                                NonNullList<Ingredient> ingredients,
                                NonNullList<Ingredient> pattern, int width, int height, boolean shaped,
                                ItemStack result,
                                float experience, int cookingTime, int fuelCost) {
        this.id = id;
        this.group = group;
        this.category = category;
        this.ingredients = ingredients;
        this.pattern = pattern;
        this.width = width;
        this.height = height;
        this.shaped = shaped;
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

    public boolean isShaped() {
        return shaped;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** 定型パターン (width*height, 空欄はEMPTY). 不定形時は空リスト */
    public NonNullList<Ingredient> getPattern() {
        return pattern;
    }

    @Override
    public boolean matches(Container container, Level level) {
        if (shaped) {
            return matchesShaped(container);
        } else {
            return matchesShapeless(container);
        }
    }

    private boolean matchesShapeless(Container container) {
        int nonEmpty = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (i >= 9) break;
            if (!container.getItem(i).isEmpty()) nonEmpty++;
        }
        if (nonEmpty != ingredients.size()) return false;
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

    private boolean matchesShaped(Container container) {
        // バニラ ShapedRecipe と同様に、パターンを3x3内の全オフセットで試す
        for (int offsetX = 0; offsetX <= 3 - width; offsetX++) {
            for (int offsetY = 0; offsetY <= 3 - height; offsetY++) {
                if (matchesAt(container, offsetX, offsetY)) {
                    // パターン外の余白が空か確認はmatchesAt内で行う
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAt(Container container, int offsetX, int offsetY) {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int patternX = x - offsetX;
                int patternY = y - offsetY;
                Ingredient expected = Ingredient.EMPTY;
                if (patternX >= 0 && patternY >= 0 && patternX < width && patternY < height) {
                    expected = pattern.get(patternY * width + patternX);
                }
                int slot = y * 3 + x;
                ItemStack stack = slot < container.getContainerSize() ? container.getItem(slot) : ItemStack.EMPTY;
                boolean isEmpty = stack.isEmpty();
                boolean expectEmpty = expected.isEmpty();
                if (isEmpty != expectEmpty) return false;
                if (!isEmpty && !expected.test(stack)) return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        if (shaped) {
            return width >= this.width && height >= this.height;
        }
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
     * vanilla ShapedRecipe/ ShapelessRecipe + SimpleCookingSerializer を参考に fuelCost を追加。
     * pattern/key があれば定型、なければingredientsで不定形として扱う。
     */
    public static class Serializer implements RecipeSerializer<BambooCampfireRecipe> {

        @Override
        public BambooCampfireRecipe fromJson(ResourceLocation id, JsonObject json) {
            String group = GsonHelper.getAsString(json, "group", "");
            String catStr = GsonHelper.getAsString(json, "category", "misc");
            Category cat = Category.fromString(catStr);
            JsonObject resultObj = GsonHelper.getAsJsonObject(json, "result");
            ItemStack result = net.minecraft.world.item.crafting.ShapedRecipe.itemStackFromJson(resultObj);
            float exp = GsonHelper.getAsFloat(json, "experience", 0.0F);
            int cookingTime = GsonHelper.getAsInt(json, "cookingtime", 200);
            int fuelCost = GsonHelper.getAsInt(json, "fuelCost", 200);
            if (json.has("cooking_time")) cookingTime = GsonHelper.getAsInt(json, "cooking_time", cookingTime);
            if (json.has("fuel_cost")) fuelCost = GsonHelper.getAsInt(json, "fuel_cost", fuelCost);

            if (json.has("pattern")) {
                // 定型
                JsonArray patternArray = GsonHelper.getAsJsonArray(json, "pattern");
                if (patternArray.size() == 0 || patternArray.size() > 3) {
                    throw new com.google.gson.JsonParseException("Invalid pattern array size " + patternArray.size());
                }
                String[] pattern = new String[patternArray.size()];
                for (int i = 0; i < patternArray.size(); i++) {
                    pattern[i] = GsonHelper.convertToString(patternArray.get(i), "pattern[" + i + "]");
                    if (pattern[i].length() > 3) {
                        throw new com.google.gson.JsonParseException("Invalid pattern row length " + pattern[i].length());
                    }
                }
                int width = pattern[0].length();
                int height = pattern.length;
                for (String row : pattern) {
                    if (row.length() != width) {
                        throw new com.google.gson.JsonParseException("Pattern rows must be same width");
                    }
                }
                JsonObject keyObj = GsonHelper.getAsJsonObject(json, "key");
                Map<String, Ingredient> keyMap = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : keyObj.entrySet()) {
                    String k = e.getKey();
                    if (k.length() != 1) {
                        throw new com.google.gson.JsonParseException("Invalid key entry '" + k + "' is not single char");
                    }
                    if (k.equals(" ")) {
                        throw new com.google.gson.JsonParseException("Invalid key entry ' ' is space");
                    }
                    keyMap.put(k, Ingredient.fromJson(e.getValue()));
                }
                NonNullList<Ingredient> patternList = NonNullList.withSize(width * height, Ingredient.EMPTY);
                for (int y = 0; y < height; y++) {
                    String row = pattern[y];
                    for (int x = 0; x < width; x++) {
                        char c = row.charAt(x);
                        if (c == ' ') {
                            patternList.set(y * width + x, Ingredient.EMPTY);
                        } else {
                            String ks = String.valueOf(c);
                            Ingredient ing = keyMap.get(ks);
                            if (ing == null) {
                                throw new com.google.gson.JsonParseException("Pattern references missing key '" + ks + "'");
                            }
                            patternList.set(y * width + x, ing);
                        }
                    }
                }
                // JEI用 ingredients (非空のみ)
                NonNullList<Ingredient> ingredients = NonNullList.create();
                for (Ingredient ing : patternList) {
                    if (!ing.isEmpty()) ingredients.add(ing);
                }
                if (ingredients.isEmpty()) {
                    throw new com.google.gson.JsonParseException("No ingredients for campfire recipe");
                }
                return new BambooCampfireRecipe(id, group, cat, ingredients, patternList, width, height, true, result, exp, cookingTime, fuelCost);
            } else {
                // 不定形
                if (!json.has("ingredients")) {
                    throw new com.google.gson.JsonParseException("No ingredients or pattern for campfire recipe");
                }
                NonNullList<Ingredient> ingredients = itemsFromJson(GsonHelper.getAsJsonArray(json, "ingredients"));
                if (ingredients.isEmpty()) {
                    throw new com.google.gson.JsonParseException("No ingredients for campfire recipe");
                } else if (ingredients.size() > 9) {
                    throw new com.google.gson.JsonParseException("Too many ingredients for campfire recipe! The max is 9");
                }
                return new BambooCampfireRecipe(id, group, cat, ingredients, result, exp, cookingTime, fuelCost);
            }
        }

        private static NonNullList<Ingredient> itemsFromJson(JsonArray jsonArray) {
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
            boolean shaped = buf.readBoolean();
            NonNullList<Ingredient> ingredients;
            NonNullList<Ingredient> pattern = NonNullList.create();
            int width = 0;
            int height = 0;
            if (shaped) {
                width = buf.readVarInt();
                height = buf.readVarInt();
                int pSize = buf.readVarInt();
                pattern = NonNullList.withSize(pSize, Ingredient.EMPTY);
                for (int i = 0; i < pSize; i++) pattern.set(i, Ingredient.fromNetwork(buf));
                int iSize = buf.readVarInt();
                ingredients = NonNullList.withSize(iSize, Ingredient.EMPTY);
                for (int i = 0; i < iSize; i++) ingredients.set(i, Ingredient.fromNetwork(buf));
            } else {
                int size = buf.readVarInt();
                ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
                for (int i = 0; i < size; i++) ingredients.set(i, Ingredient.fromNetwork(buf));
            }
            ItemStack result = buf.readItem();
            float exp = buf.readFloat();
            int cookingTime = buf.readVarInt();
            int fuelCost = buf.readVarInt();
            if (shaped) {
                return new BambooCampfireRecipe(id, group, cat, ingredients, pattern, width, height, true, result, exp, cookingTime, fuelCost);
            } else {
                return new BambooCampfireRecipe(id, group, cat, ingredients, result, exp, cookingTime, fuelCost);
            }
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, BambooCampfireRecipe recipe) {
            buf.writeUtf(recipe.group);
            buf.writeEnum(recipe.category);
            buf.writeBoolean(recipe.shaped);
            if (recipe.shaped) {
                buf.writeVarInt(recipe.width);
                buf.writeVarInt(recipe.height);
                buf.writeVarInt(recipe.pattern.size());
                for (Ingredient ing : recipe.pattern) ing.toNetwork(buf);
                buf.writeVarInt(recipe.ingredients.size());
                for (Ingredient ing : recipe.ingredients) ing.toNetwork(buf);
            } else {
                buf.writeVarInt(recipe.ingredients.size());
                for (Ingredient ing : recipe.ingredients) ing.toNetwork(buf);
            }
            buf.writeItem(recipe.result);
            buf.writeFloat(recipe.experience);
            buf.writeVarInt(recipe.cookingTime);
            buf.writeVarInt(recipe.fuelCost);
        }
    }
}
