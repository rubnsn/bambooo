package ruby.bamboo.crafting.grind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import ruby.bamboo.BambooMod;

/**
 * 石臼のバニラ準拠レシピ (1入力→1出力+ボーナスランダム)。
 * 旧GrindRecipeの後継。RecipeManagerで管理されレシピブックに対応。
 * bonusChanceは0でボーナス無し扱い。
 * <p>
 * 1.21.1 (NeoForge): {@code Recipe<SingleRecipeInput>} + MapCodec/StreamCodec 化。
 * JSON形式は 1.20.1 と同一 (ingredient/count/result/bonus/bonusChance/group/category)。
 * ただし result/bonus の ItemStack は 1.21 形式 ({@code id}/{@code count})。
 */
public class BambooGrindRecipe implements Recipe<SingleRecipeInput> {

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
        public static final Codec<Category> CODEC = Codec.STRING.xmap(Category::fromString, Category::serializedName);
    }

    private final String group;
    private final Category category;
    private final Ingredient ingredient;
    private final int inputCount;
    private final ItemStack result;
    private final ItemStack bonus;
    private final float bonusChance;

    public BambooGrindRecipe(String group, Category category,
                             Ingredient ingredient, int inputCount,
                             ItemStack result, ItemStack bonus, float bonusChance) {
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
    public boolean matches(SingleRecipeInput input, Level level) {
        ItemStack stack = input.item();
        if (stack.isEmpty() || stack.getCount() < inputCount) return false;
        return ingredient.test(stack);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return true; }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) { return result.copy(); }

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

        public static final MapCodec<BambooGrindRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
                Category.CODEC.optionalFieldOf("category", Category.MISC).forGetter(r -> r.category),
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(r -> r.ingredient),
                Codec.INT.optionalFieldOf("count", 1).forGetter(r -> r.inputCount),
                ItemStack.CODEC.fieldOf("result").forGetter(r -> r.result),
                ItemStack.CODEC.optionalFieldOf("bonus", ItemStack.EMPTY).forGetter(r -> r.bonus),
                Codec.FLOAT.optionalFieldOf("bonusChance", 0.0F).forGetter(r -> r.bonusChance))
                .apply(inst, BambooGrindRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, BambooGrindRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        private static void toNetwork(RegistryFriendlyByteBuf buf, BambooGrindRecipe recipe) {
            buf.writeUtf(recipe.group);
            buf.writeUtf(recipe.category.serializedName());
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredient);
            buf.writeVarInt(recipe.inputCount);
            ItemStack.STREAM_CODEC.encode(buf, recipe.result);
            ItemStack.STREAM_CODEC.encode(buf, recipe.bonus);
            buf.writeFloat(recipe.bonusChance);
        }

        private static BambooGrindRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            String group = buf.readUtf();
            Category cat = Category.fromString(buf.readUtf());
            Ingredient ing = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            int count = buf.readVarInt();
            ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
            ItemStack bonus = ItemStack.STREAM_CODEC.decode(buf);
            float chance = buf.readFloat();
            return new BambooGrindRecipe(group, cat, ing, count, result, bonus, chance);
        }

        @Override
        public MapCodec<BambooGrindRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BambooGrindRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
