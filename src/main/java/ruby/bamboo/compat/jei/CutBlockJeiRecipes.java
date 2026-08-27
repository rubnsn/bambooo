package ruby.bamboo.compat.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.entity.CutBlockEntity;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.crafting.CutBlockRecipe;

/**
 * JEI表示専用のカットブロックダミーレシピ生成。
 * 実クラフトは CutBlockRecipe (任意フルキューブ動的) が担当するが、JEIの作業台タブに
 * 代表3素材 ×3段階の9件を ShapelessRecipe として表示するために用いる。
 */
public final class CutBlockJeiRecipes {

    private CutBlockJeiRecipes() {}

    public static List<net.minecraft.world.item.crafting.CraftingRecipe> createJeiRecipes() {
        List<net.minecraft.world.item.crafting.CraftingRecipe> list = new ArrayList<>();
        // 代表素材: 丸石, 原木, 瓦 (mod)
        Block[] materials = new Block[] {
                Blocks.COBBLESTONE
        };
        String[] names = new String[] {"cobblestone", "oak_log", "kawara"};
        for (int i = 0; i < materials.length; i++) {
            Block mat = materials[i];
            // 防御: ワールド未ロードで KAWARA が未解決ならスキップ
            if (mat == null) continue;
            BlockState baseState = mat.defaultBlockState();
            if (!CutBlockEntity.isFullCubeState(baseState)) {
                // KAWARA が fullCube でない場合はスキップ (通常は full)
                continue;
            }
            String matName = names[i];
            // 1) FULL -> HALF x2 (8x16x16)
            list.add(makeShapeless(
                    new ResourceLocation(BambooMod.MODID, "jei_cut_" + matName + "_to_half"),
                    baseState, (byte)1, (byte)0, (byte)0, 2,
                    mat));
            // 2) HALF -> EIGHT x4 (8x8x8) : HALFは canonical 8x16x16
            // 出力は 8x8x8 なので入力HALFスタックを生成して材料とする
            // JEI上は素材フルキューブではなく HALF 自体を表示したいが、作業台タブでは簡素化のため
            // 入力もフルキューブ → 出力 EIGHT の直接表現はせず、HALF→EIGHT の段階を示すために
            // 入力HALFの見た目を出す: 別レシピとして HALF ブロックを素材とする
            // ここでは2段階目の入力を HALF cut_block とする
            list.add(makeHalfToEight(
                    new ResourceLocation(BambooMod.MODID, "jei_cut_" + matName + "_half_to_eight"),
                    baseState));
            // 3) EIGHT -> QUARTER x4 (4x4x4)
            list.add(makeEightToQuarter(
                    new ResourceLocation(BambooMod.MODID, "jei_cut_" + matName + "_eight_to_quarter"),
                    baseState));
        }
        return list;
    }

    private static ShapelessRecipe makeShapeless(ResourceLocation id, BlockState baseState, byte xl, byte yl, byte zl, int count, Block material) {
        ItemStack result = CutBlockRecipe.createCutBlockStack(baseState, xl, yl, zl);
        result.setCount(count);
        ItemStack matStack = new ItemStack(material);
        ItemStack katana = new ItemStack(BambooItems.COMMON_KATANA.get());
        NonNullList<Ingredient> ings = NonNullList.create();
        ings.add(Ingredient.of(matStack));
        ings.add(Ingredient.of(katana));
        // 刀は消費されないことを JEI 上で示すため、ShapelessRecipe を継承して getRemainingItems を上書き
        return new ShapelessRecipe(id, "", CraftingBookCategory.MISC, result, ings) {
            @Override
            public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.inventory.CraftingContainer container) {
                NonNullList<ItemStack> rem = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack s = container.getItem(i);
                    if (!s.isEmpty() && s.is(BambooItems.COMMON_KATANA.get())) {
                        rem.set(i, s.copy());
                    }
                }
                return rem;
            }
            @Override
            public boolean isSpecial() { return false; }
        };
    }

    private static ShapelessRecipe makeHalfToEight(ResourceLocation id, BlockState baseState) {
        // 入力: HALF (8x16x16) + 刀 → 出力: EIGHT(8x8x8)x4
        // 正しいNBT付きでないと透明になるため、Ingredient は NBT付き half を使用
        ItemStack half = CutBlockRecipe.createCutBlockStack(baseState, (byte)1, (byte)0, (byte)0);
        half.setCount(1);
        ItemStack katana = new ItemStack(BambooItems.COMMON_KATANA.get());
        ItemStack result = CutBlockRecipe.createCutBlockStack(baseState, (byte)1, (byte)1, (byte)1);
        result.setCount(4);
        NonNullList<Ingredient> ings = NonNullList.create();
        ings.add(Ingredient.of(half));
        ings.add(Ingredient.of(katana));
        return new ShapelessRecipe(id, "", CraftingBookCategory.MISC, result, ings) {
            @Override
            public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.inventory.CraftingContainer container) {
                NonNullList<ItemStack> rem = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack s = container.getItem(i);
                    if (!s.isEmpty() && s.is(BambooItems.COMMON_KATANA.get())) {
                        rem.set(i, s.copy());
                    }
                }
                return rem;
            }
            @Override
            public boolean isSpecial() { return false; }
        };
    }

    private static ShapelessRecipe makeEightToQuarter(ResourceLocation id, BlockState baseState) {
        ItemStack eight = CutBlockRecipe.createCutBlockStack(baseState, (byte)1, (byte)1, (byte)1);
        eight.setCount(1);
        ItemStack katana = new ItemStack(BambooItems.COMMON_KATANA.get());
        ItemStack result = CutBlockRecipe.createCutBlockStack(baseState, (byte)2, (byte)2, (byte)2);
        result.setCount(4);
        NonNullList<Ingredient> ings = NonNullList.create();
        ings.add(Ingredient.of(eight));
        ings.add(Ingredient.of(katana));
        return new ShapelessRecipe(id, "", CraftingBookCategory.MISC, result, ings) {
            @Override
            public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.inventory.CraftingContainer container) {
                NonNullList<ItemStack> rem = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack s = container.getItem(i);
                    if (!s.isEmpty() && s.is(BambooItems.COMMON_KATANA.get())) {
                        rem.set(i, s.copy());
                    }
                }
                return rem;
            }
            @Override
            public boolean isSpecial() { return false; }
        };
    }
}
