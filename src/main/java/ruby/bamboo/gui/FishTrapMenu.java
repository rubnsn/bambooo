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
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.core.init.BambooMenus;

/**
 * 魚捕り籠のメニュー。
 * <p>
 * スロット配置 (textures/gui/fish_trap.png の実測):
 * <ul>
 * <li>0: 餌 (34, 34) — 26px大型枠の中央に18pxスロット。釣りエサのみ</li>
 * <li>1-4: 出力2x2 (114, 26) (132, 26) (114, 44) (132, 44) — 取り出し専用・各1匹</li>
 * <li>5-40: プレイヤーインベントリ (y=84〜 / ホットバー y=142)</li>
 * </ul>
 * containerData 1値: progress (0-2400)
 */
public class FishTrapMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    /** クライアント側ファクトリ用 (MenuType の2引数シグネチャ) */
    public FishTrapMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(5), new SimpleContainerData(1));
    }

    public FishTrapMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(BambooMenus.FISH_TRAP.get(), containerId);
        checkContainerSize(container, 5);
        this.container = container;
        this.data = data;

        // 餌スロット (釣りエサのみ)
        this.addSlot(new Slot(container, 0, 34, 34) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(BambooItems.FISHING_BAIT.get());
            }
        });
        // 出力2x2 (取り出し専用)
        this.addSlot(new OutputSlot(container, 1, 114, 26));
        this.addSlot(new OutputSlot(container, 2, 132, 26));
        this.addSlot(new OutputSlot(container, 3, 114, 44));
        this.addSlot(new OutputSlot(container, 4, 132, 44));

        // 所持品 (標準配置)
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

    /** 出力専用スロット (isItemValid=false) */
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

    /** 生産進行度 (0-2400) */
    public int getProgress() { return data.get(0); }

    // ===== シフトクリック =====

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        int playerInvStart = 5;
        int playerInvEnd = 41; // 排他終端 (スロット総数 = 5 + 36)

        if (index >= 1 && index <= 4) {
            // 出力 → プレイヤーINV
            if (!this.moveItemStackTo(original, playerInvStart, playerInvEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(original, copy);
        } else if (index != 0) {
            // プレイヤーINV → 釣りエサだけ餌スロットへ
            if (original.is(BambooItems.FISHING_BAIT.get())) {
                if (!this.moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
        } else {
            // 餌スロット → プレイヤーINV
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
