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
 * カットブロックの動的レシピ — プランA本命。
 * B(任意フルキューブ or 既存cut_block) + K(刀) の2アイテムで、
 * 横隣接→横スライス(hLevel)、縦隣接→縦スライス(yLevel) に分岐する。
 * 刀は消費しない。Bは消費して新しいcut_blockを生成する。
 * 詳細は docs/port-spec-cutblock.md §2.5
 */
public class CutBlockRecipe implements CraftingRecipe {

    private final ResourceLocation id;

    public CutBlockRecipe(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        // 非空スロットを収集
        int width = container.getWidth();
        int height = container.getHeight();
        // width/heightが0の場合（JEI等のダミー）は3として扱う
        if (width <= 0) width = 3;
        if (height <= 0) height = 3;
        int nonEmptyCount = 0;
        int[] indices = new int[2];
        ItemStack[] stacks = new ItemStack[2];
        int idx = 0;
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

        // どちらが刀か判定
        boolean s0IsKatana = isKatana(stacks[0]);
        boolean s1IsKatana = isKatana(stacks[1]);
        if (s0IsKatana == s1IsKatana) return false; // 両方刀 or 両方非刀は不可
        ItemStack bStack = s0IsKatana ? stacks[1] : stacks[0];
        // Bが有効な素材か
        if (!isValidMaterial(bStack)) return false;

        // 位置判定: 横隣接 or 縦隣接
        int i0 = indices[s0IsKatana ? 1 : 0];
        int i1 = indices[s0IsKatana ? 0 : 1];
        // i0がBのindex、i1がKのindexだが、判定はどちらがBでも同じ
        int x0 = i0 % width;
        int y0 = i0 / width;
        int x1 = i1 % width;
        int y1 = i1 / width;
        // 横隣接: 同じ行で列が1違う
        boolean horizontal = (y0 == y1 && Math.abs(x0 - x1) == 1);
        // 縦隣接: 同じ列で行が1違う
        boolean vertical = (x0 == x1 && Math.abs(y0 - y1) == 1);
        return horizontal || vertical;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        int width = container.getWidth();
        if (width <= 0) width = 3;
        int height = container.getHeight();
        if (height <= 0) height = 3;

        // BとKを特定
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

        // 方向判定
        int bx = bIndex % width;
        int by = bIndex / width;
        int kx = kIndex % width;
        int ky = kIndex / width;
        boolean isHorizontal = (by == ky && Math.abs(bx - kx) == 1);
        boolean isVertical = (bx == kx && Math.abs(by - ky) == 1);
        if (!isHorizontal && !isVertical) return ItemStack.EMPTY;

        // BからbaseStateとyLevel/hLevelを取得
        BlockState baseState;
        byte yLevel, hLevel;
        if (isCutBlock(bStack)) {
            CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(bStack);
            baseState = data.state();
            yLevel = data.yLevel();
            hLevel = data.hLevel();
            // 空のcut_blockは不可
            if (baseState.isAir()) return ItemStack.EMPTY;
        } else if (bStack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            baseState = block.defaultBlockState();
            // フルキューブ判定
            if (!CutBlockEntity.isFullCubeState(baseState)) return ItemStack.EMPTY;
            yLevel = 0;
            hLevel = 0;
        } else {
            return ItemStack.EMPTY;
        }

        // 該当軸を1段階進める
        if (isHorizontal) {
            hLevel = CutBlockEntity.nextLevel(hLevel);
        } else {
            yLevel = CutBlockEntity.nextLevel(yLevel);
        }

        return createCutBlockStack(baseState, yLevel, hLevel);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && isKatana(s)) {
                // 刀は返却（コピー、耐久消費なし）
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
        // BambooItems.COMMON_KATANAが初期化前でもis()で落ちないようにtry
        try {
            if (stack.is(BambooItems.COMMON_KATANA.get())) return true;
        } catch (Exception e) {
        }
        // フォールバック: 名前で判定
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

    public static ItemStack createCutBlockStack(BlockState cutState, byte yLevel, byte hLevel) {
        ItemStack stack = new ItemStack(BambooBlocks.CUT_BLOCK.get());
        CompoundTag bet = new CompoundTag();
        bet.put(CutBlockEntity.TAG_CUT_STATE, NbtUtils.writeBlockState(cutState));
        bet.putByte(CutBlockEntity.TAG_Y_LEVEL, yLevel);
        bet.putByte(CutBlockEntity.TAG_H_LEVEL, hLevel);
        CompoundTag tag = stack.getOrCreateTag();
        tag.put("BlockEntityTag", bet);
        // トップレベルにもY/Hをコピー（CutBlockEntity.readFromStackのフォールバック用）
        tag.putByte(CutBlockEntity.TAG_Y_LEVEL, yLevel);
        tag.putByte(CutBlockEntity.TAG_H_LEVEL, hLevel);
        return stack;
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
