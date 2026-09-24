package ruby.bamboo.block.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import ruby.bamboo.block.FishTrapBlock;
import ruby.bamboo.core.fishing.FishingEntry;
import ruby.bamboo.core.fishing.FishingManager;
import ruby.bamboo.core.init.BambooBlockEntities;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.gui.FishTrapMenu;

/**
 * 魚捕り籠の BlockEntity。
 * <p>
 * スロット: 0=餌 (釣りエサのみ) / 1-4=出力 (各1匹・スタック不可・取出専用)。
 * 水没時のみ進行し、2400tickで餌1個を消費して60%で魚1匹を空き出力枠へ入れる。
 * 魚は FISH枠の重み付き抽選で、品質タグ (bamboo_size_class) は付けない。
 * <p>
 * ホッパー: 上・横から餌投入可 (餌のみ)、下・横から出力のみ取出可。餌の取出不可。
 */
public class FishTrapBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    /** 生産に要するtick (2分)。かなりゆっくり */
    public static final int MAX_PROGRESS = 2400;
    /** 餌1個あたりの捕獲確率 */
    public static final float CATCH_CHANCE = 0.6F;

    private static final int SLOT_BAIT = 0;
    private static final int[] SLOTS_TOP = new int[] { 0 };
    private static final int[] SLOTS_BOTTOM = new int[] { 1, 2, 3, 4 };
    private static final int[] SLOTS_SIDES = new int[] { 0, 1, 2, 3, 4 };

    /** スロット: 0=餌 / 1-4=出力 */
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);

    /** 生産進行度 (0..2400)。0=待機 */
    private int progress;

    private static final RandomSource random = RandomSource.create();

    private final LazyOptional<IItemHandlerModifiable>[] itemHandlers = SidedInvWrapper.create(this, Direction.values());

    public FishTrapBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.FISH_TRAP_BE.get(), pos, state);
    }

    // ===== 毎tick処理 (サーバーのみ) =====

    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T be) {
        if (be instanceof FishTrapBlockEntity trap) {
            trap.serverTick(state);
        }
    }

    private void serverTick(BlockState state) {
        boolean dirty = false;

        if (canWork(state)) {
            progress += 1;
            if (progress >= MAX_PROGRESS) {
                progress = 0;
                completeCatch();
            }
            dirty = true;
        } else if (progress != 0) {
            // 水切れ・餌切れ・満杯でリセット
            progress = 0;
            dirty = true;
        }

        if (dirty) {
            setChanged();
        }
    }

    /** 稼働条件: 水没 + 餌あり + 空き出力枠あり */
    private boolean canWork(BlockState state) {
        if (!state.getValue(FishTrapBlock.WATERLOGGED)) return false;
        if (items.get(SLOT_BAIT).isEmpty()) return false;
        return firstEmptyOutput() >= 0;
    }

    /** 空き出力枠 (1-4) の先頭。満杯なら -1 */
    private int firstEmptyOutput() {
        for (int i = 1; i <= 4; i++) {
            if (items.get(i).isEmpty()) return i;
        }
        return -1;
    }

    /** 餌1個消費 → 60%で魚1匹を空き枠へ。外れても餌は消費する */
    private void completeCatch() {
        items.get(SLOT_BAIT).shrink(1);
        if (items.get(SLOT_BAIT).isEmpty()) {
            items.set(SLOT_BAIT, ItemStack.EMPTY);
        }
        if (level == null) return;
        if (random.nextFloat() > CATCH_CHANCE) return;
        ItemStack fish = rollFish();
        if (fish.isEmpty()) return;
        int slot = firstEmptyOutput();
        if (slot < 0) return;
        items.set(slot, fish);
        level.playSound(null, worldPosition,
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    /** FISH枠の重み付き抽選。品質タグなし・1匹 */
    private ItemStack rollFish() {
        if (level == null) return ItemStack.EMPTY;
        List<FishingEntry> fishes = new ArrayList<>();
        for (FishingEntry e : FishingManager.getEntries()) {
            if (e.isFish()) fishes.add(e);
        }
        if (fishes.isEmpty()) return ItemStack.EMPTY;
        int total = 0;
        for (FishingEntry e : fishes) total += Math.max(1, e.weight);
        int roll = random.nextInt(total);
        for (FishingEntry e : fishes) {
            roll -= Math.max(1, e.weight);
            if (roll < 0) {
                Item item = ForgeRegistries.ITEMS.getValue(e.itemId);
                if (item == null) return ItemStack.EMPTY;
                return new ItemStack(item, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    // ===== GUI連携値 =====

    public int getProgress() {
        return progress;
    }

    // ===== WorldlyContainer (ホッパー連携) =====

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) return SLOTS_BOTTOM;
        if (side == Direction.UP) return SLOTS_TOP;
        return SLOTS_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
        // 挿入は餌スロットへの釣りエサのみ
        return index == SLOT_BAIT && stack.is(BambooItems.FISHING_BAIT.get());
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        // 餌の取出不可。出力のみ可
        return index != SLOT_BAIT;
    }

    // ===== Container 基本実装 =====

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack taken = ContainerHelper.removeItem(items, index, count);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(items, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        items.set(index, stack);
        // 出力枠はスタック不可 (1匹上限)
        if (index != SLOT_BAIT && stack.getCount() > 1) {
            stack.setCount(1);
        }
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /** GUIタイトル */
    public Component getDefaultName() {
        return Component.translatable("container.bamboomod.fish_trap");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FishTrapMenu(containerId, playerInventory, this,
                new SimpleContainerData(1) {
            @Override
            public int get(int index) {
                return index == 0 ? FishTrapBlockEntity.this.progress : 0;
            }
        });
    }

    @Override
    public Component getDisplayName() {
        return getDefaultName();
    }

    // ===== NBT =====

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("progress", progress);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(5, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items);
        if (tag.contains("progress")) {
            this.progress = tag.getInt("progress");
        }
    }

    // ===== Capability (ホッパー/パイプ連携) =====

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && side != null) {
            return itemHandlers[side.ordinal()].cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (LazyOptional<IItemHandlerModifiable> handler : itemHandlers) {
            handler.invalidate();
        }
    }

    /** 破壊時に中身を散布 (FishTrapBlock.onRemove から呼ばれる) */
    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }
}
