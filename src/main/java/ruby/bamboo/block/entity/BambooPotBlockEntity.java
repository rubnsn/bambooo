package ruby.bamboo.block.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * 竹鉢の BlockEntity — 最大16スロット、3x3グリッド+自由配置。
 * ホッパー無効（capabilityを公開しない）。
 * 各エントリは ItemStack + offsetX/Z (中心からのオフセット -0.5〜0.5) + scale + isGrid を持つ。
 */
public class BambooPotBlockEntity extends BlockEntity {

    public static final int MAX_PLANTS = 16;
    public static final int MAX_GRID = 9;

    public static class PlantEntry {
        public ItemStack stack;
        public float offsetX;
        public float offsetZ;
        public float scale;
        public boolean isGrid;

        public PlantEntry(ItemStack stack, float offsetX, float offsetZ, float scale, boolean isGrid) {
            this.stack = stack;
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
            this.scale = scale;
            this.isGrid = isGrid;
        }
    }

    private final List<PlantEntry> plants = new ArrayList<>();

    public BambooPotBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.BAMBOO_POT_BE.get(), pos, state);
    }

    public int getPlantCount() {
        return plants.size();
    }

    public int getGridCount() {
        int c = 0;
        for (PlantEntry e : plants) if (e.isGrid) c++;
        return c;
    }

    public List<PlantEntry> getPlants() {
        return plants;
    }

    /**
     * 植物を追加。成功時はchanged+syncを行う。
     * @return 成功時true
     */
    public boolean addPlant(ItemStack stack, float offsetX, float offsetZ, float scale, boolean isGrid) {
        if (plants.size() >= MAX_PLANTS) return false;
        if (isGrid && getGridCount() >= MAX_GRID) return false;
        if (isGrid) {
            // 同一グリッドセル重複チェック（offset完全一致）
            for (PlantEntry e : plants) {
                if (e.isGrid && Math.abs(e.offsetX - offsetX) < 0.001f && Math.abs(e.offsetZ - offsetZ) < 0.001f) {
                    return false;
                }
            }
        }
        // 自由配置は重複許容（わずかな重なりは許容）
        ItemStack copy = stack.copy();
        copy.setCount(1);
        plants.add(new PlantEntry(copy, offsetX, offsetZ, scale, isGrid));
        setChanged();
        sync();
        return true;
    }

    /**
     * ヒット位置に最も近い植物を1つ取り出す。空ならEMPTY。
     */
    public ItemStack removeNearest(float hitX, float hitZ) {
        if (plants.isEmpty()) return ItemStack.EMPTY;
        float targetOffsetX = hitX - 0.5f;
        float targetOffsetZ = hitZ - 0.5f;
        int bestIdx = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < plants.size(); i++) {
            PlantEntry e = plants.get(i);
            double dx = e.offsetX - targetOffsetX;
            double dz = e.offsetZ - targetOffsetZ;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestDist) {
                bestDist = d2;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) return ItemStack.EMPTY;
        PlantEntry removed = plants.remove(bestIdx);
        setChanged();
        sync();
        return removed.stack;
    }

    /** 単純に最後の1つを取り出す（フォールバック） */
    public ItemStack removeLast() {
        if (plants.isEmpty()) return ItemStack.EMPTY;
        PlantEntry removed = plants.remove(plants.size() - 1);
        setChanged();
        sync();
        return removed.stack;
    }

    /** 互換用: 旧1スロットAPI — 0番目を返す */
    public ItemStack getItem(int index) {
        if (index < 0 || index >= plants.size()) return ItemStack.EMPTY;
        return plants.get(index).stack;
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
        ListTag list = new ListTag();
        for (PlantEntry e : plants) {
            CompoundTag ct = new CompoundTag();
            ct.put("Item", e.stack.save(new CompoundTag()));
            ct.putFloat("OffsetX", e.offsetX);
            ct.putFloat("OffsetZ", e.offsetZ);
            ct.putFloat("Scale", e.scale);
            ct.putBoolean("IsGrid", e.isGrid);
            list.add(ct);
        }
        tag.put("Plants", list);
        // 旧1スロット互換の Items タグも残さない（読み込み時のみ対応）
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        plants.clear();
        if (tag.contains("Plants", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Plants", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag ct = list.getCompound(i);
                ItemStack stack = ItemStack.of(ct.getCompound("Item"));
                float ox = ct.getFloat("OffsetX");
                float oz = ct.getFloat("OffsetZ");
                float scale = ct.contains("Scale") ? ct.getFloat("Scale") : 0.35f;
                boolean isGrid = ct.getBoolean("IsGrid");
                if (!stack.isEmpty()) {
                    plants.add(new PlantEntry(stack, ox, oz, scale, isGrid));
                }
            }
        } else if (tag.contains("Items", Tag.TAG_LIST) || tag.contains("items")) {
            // 旧1スロットからの移行（ContainerHelper形式）
            // 互換: 旧は NonNullList 1件を ContainerHelper.saveAllItems で保存
            try {
                net.minecraft.core.NonNullList<ItemStack> old = net.minecraft.core.NonNullList.withSize(1, ItemStack.EMPTY);
                net.minecraft.world.ContainerHelper.loadAllItems(tag, old);
                if (!old.get(0).isEmpty()) {
                    plants.add(new PlantEntry(old.get(0), 0f, 0f, 0.35f, false));
                }
            } catch (Exception ignored) {}
        }
        // 上限を超えていたら切り詰め（旧ワールド保護）
        while (plants.size() > MAX_PLANTS) plants.remove(plants.size() - 1);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        ListTag list = new ListTag();
        for (PlantEntry e : plants) {
            CompoundTag ct = new CompoundTag();
            ct.put("Item", e.stack.save(new CompoundTag()));
            ct.putFloat("OffsetX", e.offsetX);
            ct.putFloat("OffsetZ", e.offsetZ);
            ct.putFloat("Scale", e.scale);
            ct.putBoolean("IsGrid", e.isGrid);
            list.add(ct);
        }
        tag.put("Plants", list);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        plants.clear();
        if (tag.contains("Plants", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Plants", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag ct = list.getCompound(i);
                ItemStack stack = ItemStack.of(ct.getCompound("Item"));
                float ox = ct.getFloat("OffsetX");
                float oz = ct.getFloat("OffsetZ");
                float scale = ct.contains("Scale") ? ct.getFloat("Scale") : 0.35f;
                boolean isGrid = ct.getBoolean("IsGrid");
                if (!stack.isEmpty()) plants.add(new PlantEntry(stack, ox, oz, scale, isGrid));
            }
        }
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (PlantEntry e : plants) {
            CompoundTag ct = new CompoundTag();
            ct.put("Item", e.stack.save(new CompoundTag()));
            ct.putFloat("OffsetX", e.offsetX);
            ct.putFloat("OffsetZ", e.offsetZ);
            ct.putFloat("Scale", e.scale);
            ct.putBoolean("IsGrid", e.isGrid);
            list.add(ct);
        }
        tag.put("Plants", list);
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this, be -> tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        plants.clear();
        if (tag.contains("Plants", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Plants", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag ct = list.getCompound(i);
                ItemStack stack = ItemStack.of(ct.getCompound("Item"));
                float ox = ct.getFloat("OffsetX");
                float oz = ct.getFloat("OffsetZ");
                float scale = ct.contains("Scale") ? ct.getFloat("Scale") : 0.35f;
                boolean isGrid = ct.getBoolean("IsGrid");
                if (!stack.isEmpty()) plants.add(new PlantEntry(stack, ox, oz, scale, isGrid));
            }
        }
    }

    // ホッパー無効: capabilityを公開しない
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, net.minecraft.core.Direction side) {
        return super.getCapability(cap, side);
    }

    public void dropAllContents(net.minecraft.world.level.Level lvl, BlockPos p) {
        for (PlantEntry e : plants) {
            net.minecraft.world.Containers.dropItemStack(lvl, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, e.stack);
        }
        plants.clear();
    }
}
