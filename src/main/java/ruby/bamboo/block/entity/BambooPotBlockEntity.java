package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * 竹鉢の BlockEntity — 1スロット WorldlyContainer。
 * 保存・同期・ホッパー連携を MillStone/Campfire と同様の規約で実装。
 */
public class BambooPotBlockEntity extends BlockEntity implements WorldlyContainer {

    private static final int[] SLOTS = new int[] { 0 };

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    private final LazyOptional<IItemHandlerModifiable>[] handlers = SidedInvWrapper.create(this, Direction.values());

    public BambooPotBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.BAMBOO_POT_BE.get(), pos, state);
    }

    // ===== WorldlyContainer =====

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
        if (index != 0) return false;
        if (!items.get(0).isEmpty()) return false;
        return ruby.bamboo.block.BambooPotBlock.isValidPlant(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index == 0;
    }

    // ===== Container =====

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack taken = net.minecraft.world.ContainerHelper.removeItem(items, index, count);
        if (!taken.isEmpty()) {
            setChanged();
            sync();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return net.minecraft.world.ContainerHelper.takeItem(items, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        items.set(index, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
        sync();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ===== NBT =====

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(1, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, this.items);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        this.items = NonNullList.withSize(1, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, this.items);
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        CompoundTag tag = new CompoundTag();
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items);
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this, be -> tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        this.items = NonNullList.withSize(1, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(pkt.getTag(), this.items);
    }

    // ===== Capability =====

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && side != null) {
            return handlers[side.ordinal()].cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (LazyOptional<IItemHandlerModifiable> h : handlers) h.invalidate();
    }

    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }
}
