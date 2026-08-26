package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
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

    private final LazyOptional<IItemHandlerModifiable>[] handlers = SidedInvWrapper.create(this, Direction.values());

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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        // isDouble は書かない (旧ワールド読込時のみ無視)
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        // 旧NBT isDouble があっても無視 (削除してクリーンに)
        if (tag.contains("isDouble")) {
            // 何もしない (読飛ばし)。将来的にタグを消す場合は super.load 後に除去してもよいが保持しても害なし
        }
    }

    // ===== 同期 (MillStoneパターン) =====

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        ContainerHelper.saveAllItems(tag, items);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, items);
        return ClientboundBlockEntityDataPacket.create(this, be -> tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items);
        }
    }

    // ===== Capability =====

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && side != null) {
            return handlers[side.ordinal()].cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER && side == null) {
            return handlers[0].cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (var h : handlers) h.invalidate();
    }
}
