package ruby.bamboo.block.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.BambooMod;

/**
 * 花壇の BlockEntity — プランター ({@link BambooPotBlockEntity}) と同型の最大16スロット。
 * ホッパー無効 (capabilityを公開しない)。
 */
public class FlowerBedBlockEntity extends BlockEntity {

    /** BE 型の ID。親の BambooBlockEntities.FLOWER_BED_BE 配線後に解決される。 */
    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "flower_bed");

    /**
     * 登録済み BE 型を返す。未配線 (ブロック未登録のため到達不能のはず) なら null。
     */
    @Nullable
    public static BlockEntityType<FlowerBedBlockEntity> lookupType() {
        BlockEntityType<?> found = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(TYPE_ID);
        if (found == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        BlockEntityType<FlowerBedBlockEntity> typed = (BlockEntityType<FlowerBedBlockEntity>) found;
        return typed;
    }

    public static final int MAX_PLANTS = 16;
    /** グリッド上限 (4x4=16セル)。 */
    public static final int MAX_GRID = 16;

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

    public FlowerBedBlockEntity(BlockPos pos, BlockState state) {
        super(lookupType(), pos, state);
    }

    public int getPlantCount() {
        return plants.size();
    }

    public int getGridCount() {
        int c = 0;
        for (PlantEntry e : plants) {
            if (e.isGrid) {
                c++;
            }
        }
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
        if (plants.size() >= MAX_PLANTS) {
            return false;
        }
        if (isGrid && getGridCount() >= MAX_GRID) {
            return false;
        }
        if (isGrid) {
            for (PlantEntry e : plants) {
                if (e.isGrid && Math.abs(e.offsetX - offsetX) < 0.001f && Math.abs(e.offsetZ - offsetZ) < 0.001f) {
                    return false;
                }
            }
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        plants.add(new PlantEntry(copy, offsetX, offsetZ, scale, isGrid));
        setChanged();
        sync();
        return true;
    }

    /** 単純に最後の1つを取り出す (フォールバック)。 */
    public ItemStack removeLast() {
        if (plants.isEmpty()) {
            return ItemStack.EMPTY;
        }
        PlantEntry removed = plants.remove(plants.size() - 1);
        setChanged();
        sync();
        return removed.stack;
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ===== NBT (1.21: HolderLookup.Provider 付き) =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Plants", writePlants(registries));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readPlants(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Plants", writePlants(registries));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        readPlants(tag, registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this,
                (be, registries) -> {
                    CompoundTag tag = new CompoundTag();
                    if (be instanceof FlowerBedBlockEntity bed) {
                        tag.put("Plants", bed.writePlants(registries));
                    }
                    return tag;
                });
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        readPlants(pkt.getTag(), registries);
    }

    private ListTag writePlants(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PlantEntry e : plants) {
            CompoundTag ct = new CompoundTag();
            ct.put("Item", e.stack.saveOptional(registries));
            ct.putFloat("OffsetX", e.offsetX);
            ct.putFloat("OffsetZ", e.offsetZ);
            ct.putFloat("Scale", e.scale);
            ct.putBoolean("IsGrid", e.isGrid);
            list.add(ct);
        }
        return list;
    }

    private void readPlants(CompoundTag tag, HolderLookup.Provider registries) {
        plants.clear();
        if (tag.contains("Plants", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Plants", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag ct = list.getCompound(i);
                ItemStack stack = ItemStack.parseOptional(registries, ct.getCompound("Item"));
                float ox = ct.getFloat("OffsetX");
                float oz = ct.getFloat("OffsetZ");
                float scale = ct.contains("Scale") ? ct.getFloat("Scale") : 0.35f;
                boolean isGrid = ct.getBoolean("IsGrid");
                if (!stack.isEmpty()) {
                    plants.add(new PlantEntry(stack, ox, oz, scale, isGrid));
                }
            }
        }
        while (plants.size() > MAX_PLANTS) {
            plants.remove(plants.size() - 1);
        }
    }

    // ホッパー無効: capabilityを公開しない (旧 Forge getCapability 委譲オーバーライドは削除。
    // ホッパー連携が必要になった場合は BambooCapabilities.registerCaps へ追加すること)。

    public void dropAllContents(Level lvl, BlockPos p) {
        for (PlantEntry e : plants) {
            Containers.dropItemStack(lvl, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, e.stack);
        }
        plants.clear();
    }
}
