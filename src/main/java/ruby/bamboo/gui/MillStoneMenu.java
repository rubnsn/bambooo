package ruby.bamboo.gui;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import ruby.bamboo.core.init.BambooMenus;

/**
 * 石臼のメニュー (旧 ContainerMillStone の移植) — レシピブック対応。
 * <p>
 * スロット配置は旧版と同一:
 * <ul>
 * <li>0: 入力 (80, 9)</li>
 * <li>1: メイン出力 (58, 57) — 取り出し専用</li>
 * <li>2: ボーナス出力 (102, 57) — 取り出し専用</li>
 * <li>3-38: プレイヤーインベントリ (y=84〜 / ホットバー y=142)</li>
 * </ul>
 * containerData 3値: grindMotion(0-3) / progress(0-3) / isGrind(0/1)
 * <p>
 * レシピブックは1入力→1出力(+ボーナス)で、vanilla furnace同様のTypeを持つ。
 */
public class MillStoneMenu extends RecipeBookMenu<Container> {

    public static final RecipeBookType BAMBOO_MILLSTONE_TYPE = RecipeBookType.create("BAMBOO_MILLSTONE");

    private final Container container;
    private final ContainerData data;

    /** クライアント側ファクトリ用 (MenuType の2引数シグネチャ) */
    public MillStoneMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(3), new SimpleContainerData(3));
    }

    public MillStoneMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(BambooMenus.MILL_STONE.get(), containerId);
        checkContainerSize(container, 3);
        this.container = container;
        this.data = data;

        // 石臼スロット
        this.addSlot(new Slot(container, 0, 80, 9));
        this.addSlot(new OutputSlot(container, 1, 58, 57));
        this.addSlot(new OutputSlot(container, 2, 102, 57));

        // 所持品 (旧版と同じ座標)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }

        this.addDataSlots(data);

        // 実績なしでも表示: サーバ側で自動アンロック (初回開封フォールバック)
        if (playerInventory.player instanceof net.minecraft.server.level.ServerPlayer sp) {
            try {
                ruby.bamboo.core.init.BambooRecipeUnlocker.awardMillstoneRecipes(sp);
            } catch (Exception ignored) {}
        }
    }

    /** 出力専用スロット (旧 SlotMillStone 相当: isItemValid=false) */
    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    // ===== RecipeBookMenu 契約 =====

    @Override
    public void fillCraftSlotsStackedContents(StackedContents helper) {
        if (container instanceof ruby.bamboo.block.entity.MillStoneBlockEntity be) {
            be.fillStackedContents(helper);
        } else {
            ItemStack s = container.getItem(0);
            if (!s.isEmpty()) helper.accountStack(s);
        }
    }

    @Override
    public void clearCraftingContent() {
        // 入力のみクリア、出力1,2は残す (shouldMoveToInventory false)
        this.getSlot(0).set(ItemStack.EMPTY);
    }

    @Override
    public boolean recipeMatches(Recipe<? super Container> recipe) {
        return recipe.matches(container, getLevel());
    }

    private net.minecraft.world.level.Level getLevel() {
        if (container instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.getLevel() != null) return be.getLevel();
        return null;
    }

    @Override
    public int getResultSlotIndex() { return 1; }

    @Override
    public int getGridWidth() { return 1; }

    @Override
    public int getGridHeight() { return 1; }

    @Override
    public int getSize() { return 3; }

    @Override
    public RecipeBookType getRecipeBookType() { return BAMBOO_MILLSTONE_TYPE; }

    @Override
    public boolean shouldMoveToInventory(int index) { return index == 0; }

    // ===== GUI描画用の同期値 =====

    /** 粉砕モーション (0-3)。臼アニメのフレーム番号 */
    public int getGrindMotion() { return data.get(0); }

    /** プログレスバー段数 (0-3) */
    public int getProgress() { return data.get(1); }

    /** 粉砕中か */
    public boolean isGrinding() { return data.get(2) != 0; }

    // ===== シフトクリック (旧 transferStackInSlot 相当) =====

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        int playerInvStart = 3;
        int playerInvEnd = 39; // 排他終端 (スロット総数 = 3 + 36)

        if (index == 1 || index == 2) {
            // 出力 → プレイヤーINV
            if (!this.moveItemStackTo(original, playerInvStart, playerInvEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(original, copy);
        } else if (index != 0) {
            // プレイヤーINV → レシピ品だけ入力スロットへ (旧版の賢いルーティング)
            // BambooGrindRecipeへの移行後はRecipeManagerで判定するが、クライアント側SimpleContainerでは取得できないため
            // 入力スロットへの挿入は常に許可し、サーバ側でcanStoreチェックで弾く。ただしBambooGrindRecipeのIngredientテストで近似。
            // 簡易: moved to 0-1
            if (!this.moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            // 入力スロット → プレイヤーINV
            if (!this.moveItemStackTo(original, playerInvStart, playerInvEnd, false)) return ItemStack.EMPTY;
        }

        if (original.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (original.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, original);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) { return this.container.stillValid(player); }
}
