package ruby.bamboo.crafting.cooking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import ruby.bamboo.BambooMod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p>
 * 1.21.1 (NeoForge): {@code Recipe<CraftingInput>} + MapCodec/StreamCodec 化。
 * JSON形式は 1.20.1 と同一 (pattern/key または ingredients + result/experience/cookingtime/fuelCost)。
 * ただし result の ItemStack は 1.21 形式 ({@code id}/{@code count})。
 */
public class BambooCampfireRecipe implements Recipe<CraftingInput> {

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

        public static final Codec<Category> CODEC = Codec.STRING.xmap(Category::fromString, Category::serializedName);
    }

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

    // 不定形用コンストラクタ
    public BambooCampfireRecipe(String group, Category category,
                                NonNullList<Ingredient> ingredients, ItemStack result,
                                float experience, int cookingTime, int fuelCost) {
        this(group, category, ingredients, NonNullList.create(), 0, 0, false, result, experience, cookingTime, fuelCost);
    }

    // 共通コンストラクタ
    public BambooCampfireRecipe(String group, Category category,
                                NonNullList<Ingredient> ingredients,
                                NonNullList<Ingredient> pattern, int width, int height, boolean shaped,
                                ItemStack result,
                                float experience, int cookingTime, int fuelCost) {
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
    public boolean matches(CraftingInput input, Level level) {
        if (shaped) {
            return matchesShaped(input);
        } else {
            return matchesShapeless(input);
        }
    }

    private boolean matchesShapeless(CraftingInput input) {
        int nonEmpty = 0;
        for (int i = 0; i < input.size(); i++) {
            if (i >= 9) break;
            if (!input.getItem(i).isEmpty()) nonEmpty++;
        }
        if (nonEmpty != ingredients.size()) return false;
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < 9 && i < input.size(); i++) {
            ItemStack s = input.getItem(i);
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

    private boolean matchesShaped(CraftingInput input) {
        // バニラ ShapedRecipe と同様に、パターンを3x3内の全オフセットで試す
        for (int offsetX = 0; offsetX <= 3 - width; offsetX++) {
            for (int offsetY = 0; offsetY <= 3 - height; offsetY++) {
                if (matchesAt(input, offsetX, offsetY)) {
                    // パターン外の余白が空か確認はmatchesAt内で行う
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAt(CraftingInput input, int offsetX, int offsetY) {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int patternX = x - offsetX;
                int patternY = y - offsetY;
                Ingredient expected = Ingredient.EMPTY;
                if (patternX >= 0 && patternY >= 0 && patternX < width && patternY < height) {
                    expected = pattern.get(patternY * width + patternX);
                }
                int slot = y * 3 + x;
                ItemStack stack = slot < input.size() ? input.getItem(slot) : ItemStack.EMPTY;
                boolean isEmpty = stack.isEmpty();
                boolean expectEmpty = expected.isEmpty();
                if (isEmpty != expectEmpty) return false;
                if (!isEmpty && !expected.test(stack)) return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
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
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
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

    @Override
    public boolean isSpecial() {
        return true;
    }

    /**
     * Serializer (MapCodec + StreamCodec)。
     * pattern/key があれば定型、なければingredientsで不定形として扱う。
     */
    public static class Serializer implements RecipeSerializer<BambooCampfireRecipe> {

        private record Parts(String group, Category category, List<String> pattern, Map<String, Ingredient> key,
                List<Ingredient> ingredients, ItemStack result, float experience, int cookingTime, int fuelCost) {
        }

        public static final MapCodec<BambooCampfireRecipe> CODEC = RecordCodecBuilder.<Parts>mapCodec(inst -> inst.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(Parts::group),
                Category.CODEC.optionalFieldOf("category", Category.MISC).forGetter(Parts::category),
                Codec.STRING.listOf().optionalFieldOf("pattern", List.of()).forGetter(Parts::pattern),
                Codec.unboundedMap(Codec.STRING, Ingredient.CODEC).optionalFieldOf("key", Map.of()).forGetter(Parts::key),
                Ingredient.CODEC_NONEMPTY.listOf().optionalFieldOf("ingredients", List.of()).forGetter(Parts::ingredients),
                ItemStack.CODEC.fieldOf("result").forGetter(Parts::result),
                Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(Parts::experience),
                Codec.INT.optionalFieldOf("cookingtime", 200).forGetter(Parts::cookingTime),
                Codec.INT.optionalFieldOf("fuelCost", 200).forGetter(Parts::fuelCost))
                .apply(inst, Parts::new))
                .xmap(Serializer::fromParts, Serializer::toParts);

        private static BambooCampfireRecipe fromParts(Parts p) {
            if (!p.pattern().isEmpty()) {
                // 定型
                List<String> rows = p.pattern();
                if (rows.isEmpty() || rows.size() > 3) {
                    throw new IllegalStateException("Invalid pattern array size " + rows.size());
                }
                int width = rows.get(0).length();
                int height = rows.size();
                if (width == 0 || width > 3) {
                    throw new IllegalStateException("Invalid pattern row length " + width);
                }
                for (String row : rows) {
                    if (row.length() != width) {
                        throw new IllegalStateException("Pattern rows must be same width");
                    }
                }
                NonNullList<Ingredient> patternList = NonNullList.withSize(width * height, Ingredient.EMPTY);
                for (int y = 0; y < height; y++) {
                    String row = rows.get(y);
                    for (int x = 0; x < width; x++) {
                        char c = row.charAt(x);
                        if (c == ' ') {
                            patternList.set(y * width + x, Ingredient.EMPTY);
                        } else {
                            Ingredient ing = p.key().get(String.valueOf(c));
                            if (ing == null) {
                                throw new IllegalStateException("Pattern references missing key '" + c + "'");
                            }
                            patternList.set(y * width + x, ing);
                        }
                    }
                }
                NonNullList<Ingredient> ingredients = NonNullList.create();
                for (Ingredient ing : patternList) {
                    if (!ing.isEmpty()) ingredients.add(ing);
                }
                if (ingredients.isEmpty()) {
                    throw new IllegalStateException("No ingredients for campfire recipe");
                }
                return new BambooCampfireRecipe(p.group(), p.category(), ingredients, patternList, width, height,
                        true, p.result(), p.experience(), p.cookingTime(), p.fuelCost());
            } else {
                // 不定形
                if (p.ingredients().isEmpty()) {
                    throw new IllegalStateException("No ingredients or pattern for campfire recipe");
                }
                if (p.ingredients().size() > 9) {
                    throw new IllegalStateException("Too many ingredients for campfire recipe! The max is 9");
                }
                NonNullList<Ingredient> ingredients = NonNullList.create();
                ingredients.addAll(p.ingredients());
                return new BambooCampfireRecipe(p.group(), p.category(), ingredients, p.result(),
                        p.experience(), p.cookingTime(), p.fuelCost());
            }
        }

        private static Parts toParts(BambooCampfireRecipe recipe) {
            List<String> pattern = List.of();
            Map<String, Ingredient> key = Map.of();
            List<Ingredient> ingredients;
            if (recipe.shaped) {
                // 非可逆だが同期・エンコード用。key はダミー連番で復元する
                ingredients = List.copyOf(recipe.ingredients);
                key = new HashMap<>();
                StringBuilder[] rows = new StringBuilder[recipe.height];
                for (int i = 0; i < recipe.height; i++) rows[i] = new StringBuilder();
                char next = 'A';
                Map<Ingredient, Character> seen = new HashMap<>();
                for (int y = 0; y < recipe.height; y++) {
                    for (int x = 0; x < recipe.width; x++) {
                        Ingredient ing = recipe.pattern.get(y * recipe.width + x);
                        if (ing.isEmpty()) {
                            rows[y].append(' ');
                        } else {
                            Character c = seen.get(ing);
                            if (c == null) {
                                c = next++;
                                seen.put(ing, c);
                                key.put(String.valueOf(c), ing);
                            }
                            rows[y].append(c);
                        }
                    }
                }
                List<String> prow = new ArrayList<>();
                for (StringBuilder sb : rows) prow.add(sb.toString());
                pattern = prow;
            } else {
                ingredients = List.copyOf(recipe.ingredients);
            }
            return new Parts(recipe.group, recipe.category, pattern, key, ingredients,
                    recipe.result, recipe.experience, recipe.cookingTime, recipe.fuelCost);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, BambooCampfireRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        private static void toNetwork(RegistryFriendlyByteBuf buf, BambooCampfireRecipe recipe) {
            buf.writeUtf(recipe.group);
            buf.writeUtf(recipe.category.serializedName());
            buf.writeBoolean(recipe.shaped);
            if (recipe.shaped) {
                buf.writeVarInt(recipe.width);
                buf.writeVarInt(recipe.height);
                buf.writeVarInt(recipe.pattern.size());
                for (Ingredient ing : recipe.pattern) Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ing);
                buf.writeVarInt(recipe.ingredients.size());
                for (Ingredient ing : recipe.ingredients) Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ing);
            } else {
                buf.writeVarInt(recipe.ingredients.size());
                for (Ingredient ing : recipe.ingredients) Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ing);
            }
            ItemStack.STREAM_CODEC.encode(buf, recipe.result);
            buf.writeFloat(recipe.experience);
            buf.writeVarInt(recipe.cookingTime);
            buf.writeVarInt(recipe.fuelCost);
        }

        private static BambooCampfireRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            String group = buf.readUtf();
            Category cat = Category.fromString(buf.readUtf());
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
                for (int i = 0; i < pSize; i++) pattern.set(i, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
                int iSize = buf.readVarInt();
                ingredients = NonNullList.withSize(iSize, Ingredient.EMPTY);
                for (int i = 0; i < iSize; i++) ingredients.set(i, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
            } else {
                int size = buf.readVarInt();
                ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
                for (int i = 0; i < size; i++) ingredients.set(i, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
            }
            ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
            float exp = buf.readFloat();
            int cookingTime = buf.readVarInt();
            int fuelCost = buf.readVarInt();
            if (shaped) {
                return new BambooCampfireRecipe(group, cat, ingredients, pattern, width, height, true, result, exp, cookingTime, fuelCost);
            } else {
                return new BambooCampfireRecipe(group, cat, ingredients, result, exp, cookingTime, fuelCost);
            }
        }

        @Override
        public MapCodec<BambooCampfireRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BambooCampfireRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
