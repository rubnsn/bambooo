package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 和風チェストの BlockEntity (旧 TileJPChest)。
 * <p>
 * 54 スロット (9x6)、スタック上限はバニラ通り 64。
 * 旧版はバニラ ContainerChest を使用しており、GUI もバニラ {@link ChestMenu#sixRows} を流用するため
 * 独自 MenuType/Screen は不要。
 * ホッパー連携は NeoForge BlockCapability へ移行 (BambooCapabilities.registerCaps で InvWrapper を登録)。
 */
public class JPChestBlockEntity extends BaseContainerBlockEntity {

    /** 旧 TileJPChest.getSizeInventory() = 54 */
    public static final int CONTAINER_SIZE = 9 * 6;

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    public JPChestBlockEntity(BlockPos pos, BlockState state) {
        super(ruby.bamboo.core.init.BambooBlockEntities.JP_CHEST_BE.get(), pos, state);
    }

    // ===== Container 実装 =====

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack removed = ContainerHelper.removeItem(this.items, index, count);
        if (!removed.isEmpty()) {
            this.setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(this.items, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.items.set(index, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public boolean stillValid(Player player) {
        // バニラ ChestBlockEntity と同様: BEが破壊されていなければ使用可 (距離8ブロック以内)
        return this.level != null && this.level.getBlockEntity(this.worldPosition) == this
                && player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5,
                        this.worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.bamboomod.jpchest");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return ChestMenu.sixRows(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
    }

    // ===== NeoForge ITEM_HANDLER capability (ホッパー/パイプ連携用) =====
    // 旧 getCapability/invalidateCaps (ForgeCapabilities/LazyOptional) は削除。
    // 登録は BambooCapabilities.registerCaps (RegisterCapabilitiesEvent) で行う。
}
