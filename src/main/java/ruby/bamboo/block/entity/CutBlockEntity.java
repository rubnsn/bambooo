package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * カットブロックの BlockEntity。
 * 単一のフルキューブ BlockState + yLevel/hLevel(0..2) で Bounds を導出する。
 * 仕様: docs/port-spec-cutblock.md §2.1
 */
public class CutBlockEntity extends BlockEntity {

    public static final String TAG_CUT_STATE = "CutState";
    public static final String TAG_Y_LEVEL = "YLevel";
    public static final String TAG_H_LEVEL = "HLevel";
    // 互換用: 旧Bounds配列
    public static final String TAG_BOUNDS = "Bounds";

    private static final int SYNC_DELAY = 5;

    private BlockState cutState = Blocks.AIR.defaultBlockState();
    private byte yLevel = 0; // 0=16, 1=8, 2=4
    private byte hLevel = 0; // 0=16, 1=8, 2=4

    private VoxelShape shapeCache = null;
    private boolean dirty = false;
    private int syncTimer = 0;

    public CutBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.CUT_BLOCK_BE.get(), pos, state);
    }

    // ===== サイズ導出 =====

    public static int levelToSize(byte level) {
        return switch (level) {
            case 1 -> 8;
            case 2 -> 4;
            default -> 16;
        };
    }

    public static byte sizeToLevel(int size) {
        if (size <= 4) return 2;
        if (size <= 8) return 1;
        return 0;
    }

    public static byte nextLevel(byte level) {
        return (byte) ((level + 1) % 3);
    }

    public int getYSize() {
        return levelToSize(this.yLevel);
    }

    public int getHSize() {
        return levelToSize(this.hLevel);
    }

    public byte getYLevel() {
        return this.yLevel;
    }

    public byte getHLevel() {
        return this.hLevel;
    }

    public void setLevels(byte yLevel, byte hLevel) {
        this.yLevel = clampLevel(yLevel);
        this.hLevel = clampLevel(hLevel);
        this.shapeCache = null;
        markDirtyAndSync();
    }

    public void cycleY() {
        this.yLevel = nextLevel(this.yLevel);
        this.shapeCache = null;
        markDirtyAndSync();
    }

    public void cycleH() {
        this.hLevel = nextLevel(this.hLevel);
        this.shapeCache = null;
        markDirtyAndSync();
    }

    private static byte clampLevel(byte v) {
        if (v < 0) return 0;
        if (v > 2) return 2;
        return v;
    }

    // ===== Bounds導出 =====

    /**
     * FACINGを考慮した Bounds [minX,minY,minZ,maxX,maxY,maxZ] を返す。
     * Yは常に下側を残す。HはFACINGで軸を決定し、向きにより保持側を分岐してEast/Westの反転による透けを解消。
     */
    public int[] getBounds(net.minecraft.core.Direction facing) {
        int ySize = getYSize();
        int hSize = getHSize();
        int minX = 0, minY = 0, minZ = 0;
        int maxX = 16, maxY = ySize, maxZ = 16;
        if (hSize != 16) {
            switch (facing) {
                case NORTH -> {
                    // 北向き: Xの西側(0..hSize)を残す
                    maxX = hSize;
                    maxZ = 16;
                }
                case SOUTH -> {
                    // 南向き: Xの東側(16-hSize..16)を残す（Northと対称で透け防止）
                    minX = 16 - hSize;
                    maxX = 16;
                    maxZ = 16;
                }
                case EAST -> {
                    // 東向き: Zの北側(0..hSize)を残す
                    maxX = 16;
                    maxZ = hSize;
                }
                case WEST -> {
                    // 西向き: Zの南側(16-hSize..16)を残す（Eastと対称で透け防止）
                    minZ = 16 - hSize;
                    maxX = 16;
                    maxZ = 16;
                }
                default -> {
                    maxX = hSize;
                    maxZ = 16;
                }
            }
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /**
     * FACING非依存のBounds（Xを削る版）。レンダリングのフォールバック用。
     */
    public int[] getBoundsDefault() {
        return getBounds(net.minecraft.core.Direction.NORTH);
    }

    public VoxelShape getShapeCache(net.minecraft.core.Direction facing) {
        if (this.shapeCache != null) {
            return this.shapeCache;
        }
        if (isEmpty()) {
            this.shapeCache = Shapes.empty();
            return this.shapeCache;
        }
        int[] b = getBounds(facing);
        this.shapeCache = Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        return this.shapeCache;
    }

    public void invalidateShapeCache() {
        this.shapeCache = null;
    }

    // ===== CutState =====

    public BlockState getCutState() {
        return this.cutState;
    }

    public void setCutState(BlockState state) {
        if (state == null) {
            state = Blocks.AIR.defaultBlockState();
        }
        this.cutState = state;
        this.shapeCache = null;
        markDirtyAndSync();
    }

    public boolean isEmpty() {
        return this.cutState == null || this.cutState.isAir();
    }

    public boolean isFullCube() {
        return isEmpty() || (getYSize() == 16 && getHSize() == 16);
    }

    // ===== フルキューブ判定（静的） =====

    public static boolean isFullCubeState(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (state == null || state.isAir()) return false;
        if (state.hasBlockEntity()) return false;
        if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) return false;
        try {
            // collisionShape または shape がフルブロックか
            if (Block.isShapeFullBlock(state.getCollisionShape(level, pos))) return true;
            if (Block.isShapeFullBlock(state.getShape(level, pos))) return true;
            // フォールバック: isSolidRender 相当
            return state.isSolidRender(level, pos);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isFullCubeState(BlockState state) {
        if (state == null || state.isAir()) return false;
        if (state.hasBlockEntity()) return false;
        if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) return false;
        try {
            // BlockGetterなしでの簡易判定: shapeがフルか
            VoxelShape shape = state.getShape(null, BlockPos.ZERO);
            if (shape != null && Block.isShapeFullBlock(shape)) return true;
            VoxelShape coll = state.getCollisionShape(null, BlockPos.ZERO);
            if (coll != null && Block.isShapeFullBlock(coll)) return true;
        } catch (Exception e) {
            // null levelで例外なら、RenderShapeでのみ判定済みなのでtrue扱い
            return true;
        }
        // shape取得で例外だがRenderShapeがMODELなら許可
        return true;
    }

    // ===== Dirty / Sync =====

    public void markDirtyAndSync() {
        this.dirty = true;
        this.shapeCache = null;
        if (this.syncTimer <= 0) {
            this.syncTimer = SYNC_DELAY;
        }
        setChanged();
    }

    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T be) {
        if (be instanceof CutBlockEntity cut) {
            cut.serverTick();
        }
    }

    private void serverTick() {
        if (this.syncTimer > 0) {
            this.syncTimer--;
            if (this.syncTimer == 0 && this.dirty) {
                setChanged();
                if (this.level != null) {
                    this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
                }
                this.dirty = false;
            }
        }
    }

    // ===== NBT =====

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeSyncData(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readSyncData(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeSyncData(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        readSyncData(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        CompoundTag tag = new CompoundTag();
        writeSyncData(tag);
        return ClientboundBlockEntityDataPacket.create(this, be -> tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        readSyncData(pkt.getTag());
    }

    public void writeSyncData(CompoundTag tag) {
        if (this.cutState != null && !this.cutState.isAir()) {
            tag.put(TAG_CUT_STATE, NbtUtils.writeBlockState(this.cutState));
        }
        tag.putByte(TAG_Y_LEVEL, this.yLevel);
        tag.putByte(TAG_H_LEVEL, this.hLevel);
    }

    public void readSyncData(CompoundTag tag) {
        if (tag.contains(TAG_CUT_STATE, Tag.TAG_COMPOUND)) {
            CompoundTag stateTag = tag.getCompound(TAG_CUT_STATE);
            BlockState s = readBlockState(stateTag);
            this.cutState = s != null ? s : Blocks.AIR.defaultBlockState();
        } else {
            this.cutState = Blocks.AIR.defaultBlockState();
        }
        if (tag.contains(TAG_Y_LEVEL, Tag.TAG_BYTE)) {
            this.yLevel = clampLevel(tag.getByte(TAG_Y_LEVEL));
        } else if (tag.contains(TAG_Y_LEVEL, Tag.TAG_INT)) {
            this.yLevel = clampLevel((byte) tag.getInt(TAG_Y_LEVEL));
        } else {
            this.yLevel = 0;
        }
        if (tag.contains(TAG_H_LEVEL, Tag.TAG_BYTE)) {
            this.hLevel = clampLevel(tag.getByte(TAG_H_LEVEL));
        } else if (tag.contains(TAG_H_LEVEL, Tag.TAG_INT)) {
            this.hLevel = clampLevel((byte) tag.getInt(TAG_H_LEVEL));
        } else {
            this.hLevel = 0;
        }
        // 互換: Bounds配列があればY/Hを逆算（旧データ移行用、将来削除可）
        if (tag.contains(TAG_BOUNDS, Tag.TAG_INT_ARRAY)) {
            int[] bounds = tag.getIntArray(TAG_BOUNDS);
            if (bounds.length >= 6) {
                int ySize = bounds[4] - bounds[1];
                int hSize = bounds[3] - bounds[0];
                if (bounds[3] == 16 && bounds[5] != 16) {
                    hSize = bounds[5] - bounds[2];
                }
                this.yLevel = sizeToLevel(ySize);
                this.hLevel = sizeToLevel(hSize);
            }
        }
        this.shapeCache = null;
        this.dirty = false;
        this.syncTimer = 0;
    }

    private BlockState readBlockState(CompoundTag stateTag) {
        if (stateTag == null || stateTag.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }
        return readBlockStateFallback(stateTag);
    }

    private static BlockState readBlockStateFallback(CompoundTag stateTag) {
        if (!stateTag.contains("Name", Tag.TAG_STRING)) {
            return Blocks.AIR.defaultBlockState();
        }
        String name = stateTag.getString("Name");
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            return Blocks.AIR.defaultBlockState();
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = block.defaultBlockState();
        if (stateTag.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag props = stateTag.getCompound("Properties");
            for (String key : props.getAllKeys()) {
                Property<?> prop = block.getStateDefinition().getProperty(key);
                if (prop == null) continue;
                String valueStr = props.getString(key);
                state = applyProperty(state, prop, valueStr);
            }
        }
        return state;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> prop, String valueStr) {
        return prop.getValue(valueStr).map(v -> state.setValue(prop, v)).orElse(state);
    }

    /**
     * ItemStackから CutState/YLevel/HLevel を読み取るヘルパー（設置時用）。
     * BlockEntityTag があればそこから、なければトップレベルから読む。
     */
    public static CutBlockData readFromStack(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CutBlockData(Blocks.AIR.defaultBlockState(), (byte) 0, (byte) 0);
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return new CutBlockData(Blocks.AIR.defaultBlockState(), (byte) 0, (byte) 0);
        }
        CompoundTag bet = tag.contains("BlockEntityTag", Tag.TAG_COMPOUND) ? tag.getCompound("BlockEntityTag") : tag;
        BlockState state = Blocks.AIR.defaultBlockState();
        byte yLevel = 0, hLevel = 0;
        if (bet.contains(TAG_CUT_STATE, Tag.TAG_COMPOUND)) {
            state = readBlockStateFallback(bet.getCompound(TAG_CUT_STATE));
        }
        if (bet.contains(TAG_Y_LEVEL, Tag.TAG_BYTE)) yLevel = clampLevelStatic(bet.getByte(TAG_Y_LEVEL));
        else if (bet.contains(TAG_Y_LEVEL, Tag.TAG_INT)) yLevel = clampLevelStatic((byte) bet.getInt(TAG_Y_LEVEL));
        if (bet.contains(TAG_H_LEVEL, Tag.TAG_BYTE)) hLevel = clampLevelStatic(bet.getByte(TAG_H_LEVEL));
        else if (bet.contains(TAG_H_LEVEL, Tag.TAG_INT)) hLevel = clampLevelStatic((byte) bet.getInt(TAG_H_LEVEL));
        return new CutBlockData(state, yLevel, hLevel);
    }

    private static byte clampLevelStatic(byte v) {
        if (v < 0) return 0;
        if (v > 2) return 2;
        return v;
    }

    public record CutBlockData(BlockState state, byte yLevel, byte hLevel) {
    }
}
