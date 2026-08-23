package ruby.bamboo.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ruby.bamboo.core.init.BambooMenus;
import ruby.bamboo.item.Sack;

/**
 * 袋のメニュー (旧 ContainerSack の移植)。
 * <p>
 * スロット配置は旧版と同一:
 * <ul>
 * <li>0: 袋へ入れるアイテム (80, 33) — BlockItem のみ mayPlace</li>
 * <li>1-36: プレイヤーインベントリ</li>
 * </ul>
 * <p>
 * 閉じた時 ({@link #removed})、スロット 0 の中身を手持ちの袋へ収容する。
 * 手持ちに袋が無い場合は床へドロップ。
 */
public class SackMenu extends AbstractContainerMenu {

    private final Container container;

    /** クライアント側ファクトリ用 (MenuType の2引数シグネチャ) */
    public SackMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(1));
    }

    public SackMenu(int containerId, Inventory playerInventory, Container container) {
        super(BambooMenus.SACK.get(), containerId);
        checkContainerSize(container, 1);
        this.container = container;

        // 袋スロット: BlockItem のみ格納可
        this.addSlot(new Slot(container, 0, 80, 33) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Sack.isStorage(stack);
            }
        });

        // 所持品 (MillStoneMenu と同じ座標系)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    /** 手持ちの袋スタックを探す (メイン→オフハンド順) */
    private static ItemStack findSack(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.getItem() instanceof Sack) {
                return s;
            }
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof Sack ? off : ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 手持ちに袋があれば継続 (旧 canInteractWith 相当)
        return !findSack(player).isEmpty();
    }

    // ===== シフトクリック =====

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        if (index == 0) {
            // 袋スロット → プレイヤーinv
            if (!this.moveItemStackTo(original, 1, 37, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // プレイヤーinv → 袋スロット (BlockItem のみ)
            if (!Sack.isStorage(original) || !this.moveItemStackTo(original, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (original.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    // ===== GUI を閉じた時の後始末 =====

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (player.level().isClientSide) {
            return;
        }

        ItemStack slotContent = this.container.getItem(0);
        if (slotContent.isEmpty()) {
            return;
        }

        ItemStack sack = findSack(player);
        if (!sack.isEmpty() && Sack.isStorage(slotContent)) {
            // 袋へ収容
            Sack.setContent(sack, slotContent.getItem());
            Sack.setCount(sack, slotContent.getCount());
        } else {
            // 収容先が無い/対象外 → 床ドロップ
            player.drop(slotContent.copy(), false);
        }
        this.container.removeItem(0, Integer.MAX_VALUE);
    }
}
