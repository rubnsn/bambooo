package ruby.bamboo.crafting;

import com.google.gson.JsonObject;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import ruby.bamboo.BambooMod;
import ruby.bamboo.capsule.CapsuleRules;
import ruby.bamboo.item.CapsuleBallItem;

/**
 * カプセルボールのTier上位レシピ (docs §2)。
 * 下位ボール (状態不問・中身ごと継承) + スライムボール → 上位ボール。
 * 捕獲済みの上位化でも EntityData・紐付けをそのまま引き継ぐ。
 */
public class CapsuleUpgradeRecipe implements CraftingRecipe {

    private final ResourceLocation id;
    private final Item lower;
    private final ItemStack result;

    public CapsuleUpgradeRecipe(ResourceLocation id, Item lower, ItemStack result) {
        this.id = id;
        this.lower = lower;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int found = 0;
        boolean hasLower = false;
        boolean hasSlime = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            found++;
            if (found > 2) return false;
            if (s.is(lower)) {
                if (hasLower) return false;
                hasLower = true;
            } else if (s.is(Items.SLIME_BALL)) {
                if (hasSlime) return false;
                hasSlime = true;
            } else {
                return false;
            }
        }
        return found == 2 && hasLower && hasSlime;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack lowerStack = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && s.is(lower)) {
                lowerStack = s;
                break;
            }
        }
        if (lowerStack.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(result.getItem());
        if (lowerStack.hasTag()) {
            out.setTag(lowerStack.getTag().copy());
        }
        // Tierを引き上げ (中身・紐付けは維持)
        if (out.getItem() instanceof CapsuleBallItem ball) {
            out.getOrCreateTag().putString(CapsuleRules.TAG_TIER, ball.getTier().id);
        }
        out.setCount(1);
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
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
        return BambooMod.CAPSULE_UPGRADE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(Ingredient.of(lower));
        list.add(Ingredient.of(Items.SLIME_BALL));
        return list;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<CapsuleUpgradeRecipe> {
        @Override
        public CapsuleUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            String lowerId = GsonHelper.getAsString(json, "lower");
            Item lower = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(lowerId));
            JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
            String resultId = GsonHelper.getAsString(resultJson, "item");
            Item resultItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(resultId));
            return new CapsuleUpgradeRecipe(id, lower, new ItemStack(resultItem));
        }

        @Override
        public CapsuleUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Item lower = buf.readRegistryIdUnsafe(ForgeRegistries.ITEMS);
            ItemStack result = buf.readItem();
            return new CapsuleUpgradeRecipe(id, lower, result);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, CapsuleUpgradeRecipe recipe) {
            buf.writeRegistryIdUnsafe(ForgeRegistries.ITEMS, recipe.lower);
            buf.writeItem(recipe.result);
        }
    }
}
