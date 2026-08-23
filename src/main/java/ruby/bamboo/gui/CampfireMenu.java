package ruby.bamboo.gui;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ruby.bamboo.core.init.BambooMenus;

/**
 * 囲炉裏のメニュー (旧 ContainerCampfire の移植)。
 * <p>
 * スロット配置は旧版と同一:
 * <ul>
 * <li>0-8: 素材3×3 (30+i%3*18, 17+i/3*18)</li>
 * <li>9: 燃料 (8, 53)</li>
 * <li>10: 結果 (124, 35) — 取り出し専用</li>
 * <li>11-46: プレイヤーインベントリ (y=84〜 / ホットバー y=142)</li>
 * </ul>
 * containerData 2値: fuelRatio(%) / cookRatio(%)
 */
public class CampfireMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    /** クライアント側ファクトリ用 (MenuType の2引数シグネチャ) */
    public CampfireMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(11), new SimpleContainerData(2));
    }

    public CampfireMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(BambooMenus.CAMPFIRE.get(), containerId);
        checkContainerSize(container, 11);
        this.container = container;
        this.data = data;

        // 素材3×3
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(container, i, 30 + i % 3 * 18, 17 + i / 3 * 18));
        }
        // 燃料
        this.addSlot(new Slot(container, 9, 8, 53));
        // 結果 (取り出し専用)
        this.addSlot(new OutputSlot(container, 10, 124, 35));

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
    }

    /** 出力専用スロット (旧 SlotCampfire 相当: isItemValid=false) */
    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    // ===== GUI描画用の同期値 =====

    /** 燃料残量 (%) */
    public int getFuelRatio() {
        return data.get(0);
    }

    /** 調理進行度 (%) */
    public int getCookRatio() {
        return data.get(1);
    }

    // ===== シフトクリック (旧 transferStackInSlot 相当) =====

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        int playerInvStart = 11;
        int playerInvEnd = 47; // 排他終端 (スロット総数 = 11 + 36)

        if (index == 10) {
            // 結果 → プレイヤーINV
            if (!this.moveItemStackTo(original, playerInvStart, playerInvEnd, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(original, copy);
        } else if (index >= playerInvStart && index < playerInvEnd) {
            // プレイヤーINV → 素材/燃料スロットへ
            if (!this.moveItemStackTo(original, 0, 10, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 素材/燃料 → プレイヤーINV
            if (!this.moveItemStackTo(original, playerInvStart, playerInvEnd, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (original.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, original);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }
}