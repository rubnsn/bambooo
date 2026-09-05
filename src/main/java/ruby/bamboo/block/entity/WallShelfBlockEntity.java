package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * 壁棚の BlockEntity (sakura-master WallShelfTileEntity 移植)。
 * <p>
 * 旧: LockableLootTileEntity size2 + isDouble常時true。1.20.1では BlockEntity+WorldlyContainer。
 * isDoubleはデッドコードのため削除、旧NBTに含まれても読飛ばし (後方互換)。
 */
public class WallShelfBlockEntity extends BlockEntity implements WorldlyContainer {

    public static final int SLOT_RIGHT = 0;
    public static final int SLOT_LEFT = 1;
    public static final int CONTAINER_SIZE = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    // ホッパー連携は NeoForge BlockCapability へ移行 (BambooCapabilities.registerCaps で登録)。

    public WallShelfBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.WALL_SHELF_BE.get(), pos, state);
    }

    // ===== Container =====

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        if (index < 0 || index >= items.size()) return ItemStack.EMPTY;
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack ret = ContainerHelper.removeItem(items, index, count);
        if (!ret.isEmpty()) setChanged();
        return ret;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(items, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= items.size()) return;
        items.set(index, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
        // クライアントへ同期 (BER用)
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    public boolean hasItem(int slot) {
        return !getItem(slot).isEmpty();
    }

    // ===== WorldlyContainer =====

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{SLOT_RIGHT, SLOT_LEFT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index == SLOT_RIGHT || index == SLOT_LEFT;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index == SLOT_RIGHT || index == SLOT_LEFT;
    }

    // ===== NBT =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        // isDouble は書かない (旧ワールド読込時のみ無視)
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        // 旧NBT isDouble があっても無視 (削除してクリーンに)
        if (tag.contains("isDouble")) {
            // 何もしない (読飛ばし)。将来的にタグを消す場合は super.load 後に除去してもよいが保持しても害なし
        }
    }

    // ===== 同期 (MillStoneパターン) =====

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this,
                (be, registries) -> {
                    CompoundTag tag = new CompoundTag();
                    if (be instanceof WallShelfBlockEntity shelf) {
                        ContainerHelper.saveAllItems(tag, shelf.items, registries);
                    }
                    return tag;
                });
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items, registries);
        }
    }

    // ===== Capability (ホッパー/パイプ連携: NeoForge BlockCapability へ移行) =====
    // 旧 getCapability/invalidateCaps (ForgeCapabilities/LazyOptional) は削除。
    // 登録は BambooCapabilities.registerCaps (RegisterCapabilitiesEvent) で行う。
}
