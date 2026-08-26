package ruby.bamboo.crafting;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.entity.CutBlockEntity;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooItems;

/**
 * カットブロックの動的レシピ — 3軸絶対切断。
 * B(任意フルキューブ or 既存cut_block) + K(刀) の2アイテムで、
 * 横隣接→X切断(8×16×16)、縦隣接→Y切断(16×8×16)、斜め隣接→Z切断(16×16×8) に分岐。
 * 刀は消費しない。Bは消費して新しいcut_blockを生成する。
 * 2×2/3×3 いずれのグリッドでも成立。
 */
public class CutBlockRecipe implements CraftingRecipe {

    private final ResourceLocation id;

    public CutBlockRecipe(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int width = container.getWidth();
        int height = container.getHeight();
        if (width <= 0) width = 3;
        if (height <= 0) height = 3;
        int nonEmptyCount = 0;
        int[] indices = new int[2];
        ItemStack[] stacks = new ItemStack[2];
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) {
                if (nonEmptyCount >= 2) return false;
                indices[nonEmptyCount] = i;
                stacks[nonEmptyCount] = s;
                nonEmptyCount++;
            }
        }
        if (nonEmptyCount != 2) return false;

        boolean s0IsKatana = isKatana(stacks[0]);
        boolean s1IsKatana = isKatana(stacks[1]);
        if (s0IsKatana == s1IsKatana) return false;
        ItemStack bStack = s0IsKatana ? stacks[1] : stacks[0];
        if (!isValidMaterial(bStack)) return false;

        int i0 = indices[s0IsKatana ? 1 : 0];
        int i1 = indices[s0IsKatana ? 0 : 1];
        int x0 = i0 % width;
        int y0 = i0 / width;
        int x1 = i1 % width;
        int y1 = i1 / width;
        boolean horizontal = (y0 == y1 && Math.abs(x0 - x1) == 1);
        boolean vertical = (x0 == x1 && Math.abs(y0 - y1) == 1);
        boolean diagonal = (Math.abs(x0 - x1) == 1 && Math.abs(y0 - y1) == 1);
        return horizontal || vertical || diagonal;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        int width = container.getWidth();
        if (width <= 0) width = 3;

        ItemStack bStack = ItemStack.EMPTY;
        ItemStack kStack = ItemStack.EMPTY;
        int bIndex = -1, kIndex = -1;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            if (isKatana(s)) {
                kStack = s;
                kIndex = i;
            } else {
                bStack = s;
                bIndex = i;
            }
        }
        if (bStack.isEmpty() || kStack.isEmpty()) return ItemStack.EMPTY;
        if (!isValidMaterial(bStack)) return ItemStack.EMPTY;

        int bx = bIndex % width;
        int by = bIndex / width;
        int kx = kIndex % width;
        int ky = kIndex / width;
        boolean isHorizontal = (by == ky && Math.abs(bx - kx) == 1);
        boolean isVertical = (bx == kx && Math.abs(by - ky) == 1);
        boolean isDiagonal = (Math.abs(bx - kx) == 1 && Math.abs(by - ky) == 1);
        if (!isHorizontal && !isVertical && !isDiagonal) return ItemStack.EMPTY;

        BlockState baseState;
        byte xLevel, yLevel, zLevel;
        if (isCutBlock(bStack)) {
            CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(bStack);
            baseState = data.state();
            xLevel = data.xLevel();
            yLevel = data.yLevel();
            zLevel = data.zLevel();
            if (baseState.isAir()) return ItemStack.EMPTY;
        } else if (bStack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            baseState = block.defaultBlockState();
            if (!CutBlockEntity.isFullCubeState(baseState)) return ItemStack.EMPTY;
            xLevel = 0;
            yLevel = 0;
            zLevel = 0;
        } else {
            return ItemStack.EMPTY;
        }

        if (isHorizontal) {
            xLevel = CutBlockEntity.nextLevel(xLevel);
        } else if (isVertical) {
            yLevel = CutBlockEntity.nextLevel(yLevel);
        } else {
            zLevel = CutBlockEntity.nextLevel(zLevel);
        }

        return createCutBlockStack(baseState, xLevel, yLevel, zLevel);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && isKatana(s)) {
                remaining.set(i, s.copy());
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BambooMod.CUT_BLOCK_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    // ===== ヘルパー =====

    private static boolean isKatana(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (stack.is(BambooItems.COMMON_KATANA.get())) return true;
        } catch (Exception e) {
        }
        String key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        return key.equals("bamboomod:commonkatana");
    }

    private static boolean isCutBlock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (stack.is(BambooBlocks.CUT_BLOCK.get().asItem())) return true;
        } catch (Exception e) {
        }
        String key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        return key.equals("bamboomod:cut_block");
    }

    private static boolean isValidMaterial(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isCutBlock(stack)) {
            CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
            return !data.state().isAir();
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            BlockState state = block.defaultBlockState();
            return CutBlockEntity.isFullCubeState(state);
        }
        return false;
    }

    public static ItemStack createCutBlockStack(BlockState cutState, byte xLevel, byte yLevel, byte zLevel) {
        ItemStack stack = new ItemStack(BambooBlocks.CUT_BLOCK.get());
        CompoundTag bet = new CompoundTag();
        bet.put(CutBlockEntity.TAG_CUT_STATE, NbtUtils.writeBlockState(cutState));
        bet.putByte(CutBlockEntity.TAG_X_LEVEL, xLevel);
        bet.putByte(CutBlockEntity.TAG_Y_LEVEL, yLevel);
        bet.putByte(CutBlockEntity.TAG_Z_LEVEL, zLevel);
        CompoundTag tag = stack.getOrCreateTag();
        tag.put("BlockEntityTag", bet);
        return stack;
    }

    /** 旧 2引数互換: HをXとみなす */
    @Deprecated
    public static ItemStack createCutBlockStack(BlockState cutState, byte yLevel, byte hLevel) {
        return createCutBlockStack(cutState, hLevel, yLevel, (byte) 0);
    }

    public static class Serializer implements RecipeSerializer<CutBlockRecipe> {
        @Override
        public CutBlockRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new CutBlockRecipe(id);
        }

        @Override
        public CutBlockRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new CutBlockRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, CutBlockRecipe recipe) {
        }
    }
}
