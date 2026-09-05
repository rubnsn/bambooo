package ruby.bamboo.crafting;

import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
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
 * カットブロックの動的レシピ — 3種簡素化。
 * B(任意フルキューブ or 既存cut_block) + K(刀) の2アイテムで、
 * フル(16³) → ハーフ(8x16x16)×2
 * ハーフ → 8x8x8×4
 * 8x8x8 → 4x4x4×4
 * 循環なし。刀は消費しない。2×2/3×3いずれでも成立、隣接方向不問。
 */
public class CutBlockRecipe implements CraftingRecipe {

    public CutBlockRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int width = input.width();
        int height = input.height();
        if (width <= 0) width = 3;
        if (height <= 0) height = 3;
        int nonEmptyCount = 0;
        int[] indices = new int[2];
        ItemStack[] stacks = new ItemStack[2];
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
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
        // 3種簡素化では方向不問で隣接なら成立
        return horizontal || vertical || diagonal;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int width = input.width();
        if (width <= 0) width = 3;

        ItemStack bStack = ItemStack.EMPTY;
        ItemStack kStack = ItemStack.EMPTY;
        int bIndex = -1, kIndex = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
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

        // 隣接チェック（方向不問）
        int bx = bIndex % width;
        int by = bIndex / width;
        int kx = kIndex % width;
        int ky = kIndex / width;
        boolean isHorizontal = (by == ky && Math.abs(bx - kx) == 1);
        boolean isVertical = (bx == kx && Math.abs(by - ky) == 1);
        boolean isDiagonal = (Math.abs(bx - kx) == 1 && Math.abs(by - ky) == 1);
        if (!isHorizontal && !isVertical && !isDiagonal) return ItemStack.EMPTY;

        BlockState baseState;
        CutBlockEntity.Tier tier;
        if (isCutBlock(bStack)) {
            if (!CutBlockEntity.readEntriesFromStack(bStack).isEmpty()) return ItemStack.EMPTY;
            CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(bStack);
            baseState = data.state();
            if (baseState.isAir()) return ItemStack.EMPTY;
            tier = CutBlockEntity.getTierFromLevels(data.xLevel(), data.yLevel(), data.zLevel());
            if (tier == CutBlockEntity.Tier.OTHER || tier == CutBlockEntity.Tier.FULL) return ItemStack.EMPTY;
        } else if (bStack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            baseState = block.defaultBlockState();
            if (!CutBlockEntity.isFullCubeState(baseState)) return ItemStack.EMPTY;
            tier = CutBlockEntity.Tier.FULL;
        } else {
            return ItemStack.EMPTY;
        }

        // 3段階: FULL->HALF×2, HALF->EIGHT×4, EIGHT->QUARTER×4
        if (tier == CutBlockEntity.Tier.FULL) {
            ItemStack out = createCutBlockStack(baseState, (byte)1, (byte)0, (byte)0); // 8x16x16 canonical
            out.setCount(2);
            return out;
        } else if (tier == CutBlockEntity.Tier.HALF) {
            ItemStack out = createCutBlockStack(baseState, (byte)1, (byte)1, (byte)1); // 8x8x8
            out.setCount(4);
            return out;
        } else if (tier == CutBlockEntity.Tier.EIGHT) {
            ItemStack out = createCutBlockStack(baseState, (byte)2, (byte)2, (byte)2); // 4x4x4
            out.setCount(4);
            return out;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
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
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
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
        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return key.equals("bamboomod:commonkatana");
    }

    private static boolean isCutBlock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (stack.is(BambooBlocks.CUT_BLOCK.get().asItem())) return true;
        } catch (Exception e) {
        }
        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return key.equals("bamboomod:cut_block");
    }

    private static boolean isValidMaterial(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isCutBlock(stack)) {
            if (!CutBlockEntity.readEntriesFromStack(stack).isEmpty()) return false;
            CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
            if (data.state().isAir()) return false;
            CutBlockEntity.Tier t = CutBlockEntity.getTierFromLevels(data.xLevel(), data.yLevel(), data.zLevel());
            return t == CutBlockEntity.Tier.FULL || t == CutBlockEntity.Tier.HALF || t == CutBlockEntity.Tier.EIGHT;
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
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("BlockEntityTag", bet);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** 旧 2引数互換: HをXとみなす */
    @Deprecated
    public static ItemStack createCutBlockStack(BlockState cutState, byte yLevel, byte hLevel) {
        return createCutBlockStack(cutState, hLevel, yLevel, (byte) 0);
    }

    public static class Serializer implements RecipeSerializer<CutBlockRecipe> {
        public static final MapCodec<CutBlockRecipe> CODEC = MapCodec.unit(CutBlockRecipe::new);
        // 無状態レシピのため送受信データなし。StreamCodec.unit は同一性チェックで落ちる
        // (JSON読込の別インスタンスを update_recipes で送れない) ため自前実装する。
        public static final StreamCodec<RegistryFriendlyByteBuf, CutBlockRecipe> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public CutBlockRecipe decode(RegistryFriendlyByteBuf buf) {
                return new CutBlockRecipe();
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, CutBlockRecipe recipe) {
            }
        };

        @Override
        public MapCodec<CutBlockRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CutBlockRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
