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
 * 複数のフルキューブ BlockState を同一ワールド座標内のサブ空間(Bounds)に保持する。
 * 3軸絶対切断 (X 8/4, Y 8/4, Z 8/4) に対応。FACING依存を撤廃。
 * 仕様: docs/port-spec-cutblock.md §2.1
 */
public class CutBlockEntity extends BlockEntity {

    public static final String TAG_CUT_STATE = "CutState";
    public static final String TAG_X_LEVEL = "XLevel";
    public static final String TAG_Y_LEVEL = "YLevel";
    public static final String TAG_Z_LEVEL = "ZLevel";
    // 旧互換 (読込のみ)
    public static final String TAG_H_LEVEL = "HLevel";
    public static final String TAG_BOUNDS = "Bounds";
    public static final String TAG_ENTRIES = "Entries";
    public static final String TAG_STATE = "State";

    private static final int SYNC_DELAY = 5;

    // 単一保持 (entriesが空の時のみ有効) — 3軸
    private BlockState cutState = Blocks.AIR.defaultBlockState();
    private byte xLevel = 0; // 0=16, 1=8, 2=4
    private byte yLevel = 0;
    private byte zLevel = 0;

    // 複数エントリ
    private final java.util.List<CutEntry> entries = new java.util.ArrayList<>();

    private VoxelShape shapeCache = null;
    private boolean dirty = false;
    private int syncTimer = 0;

    public CutBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.CUT_BLOCK_BE.get(), pos, state);
    }

    /** 単一サブブロックの保持データ */
    public static class CutEntry {
        public final BlockState state;
        public final int[] bounds; // [minX,minY,minZ,maxX,maxY,maxZ] 0..16

        public CutEntry(BlockState state, int[] bounds) {
            this.state = state;
            this.bounds = bounds.clone();
        }
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

    public int getXSize() {
        return levelToSize(this.xLevel);
    }

    public int getYSize() {
        return levelToSize(this.yLevel);
    }

    public int getZSize() {
        return levelToSize(this.zLevel);
    }

    /** 旧 hSize 互換: Xサイズを返す */
    @Deprecated
    public int getHSize() {
        return getXSize();
    }

    public byte getXLevel() {
        return this.xLevel;
    }

    public byte getYLevel() {
        return this.yLevel;
    }

    public byte getZLevel() {
        return this.zLevel;
    }

    /** 旧 hLevel 互換 */
    @Deprecated
    public byte getHLevel() {
        return this.xLevel;
    }

    public void setLevels(byte xLevel, byte yLevel, byte zLevel) {
        this.xLevel = clampLevel(xLevel);
        this.yLevel = clampLevel(yLevel);
        this.zLevel = clampLevel(zLevel);
        this.shapeCache = null;
        markDirtyAndSync();
    }

    /** 旧 2引数互換: HをXとみなす */
    @Deprecated
    public void setLevels(byte yLevel, byte hLevel) {
        setLevels(hLevel, yLevel, (byte) 0);
    }

    public void cycleX() {
        this.xLevel = nextLevel(this.xLevel);
        this.shapeCache = null;
        markDirtyAndSync();
    }

    public void cycleY() {
        this.yLevel = nextLevel(this.yLevel);
        this.shapeCache = null;
        markDirtyAndSync();
    }

    public void cycleZ() {
        this.zLevel = nextLevel(this.zLevel);
        this.shapeCache = null;
        markDirtyAndSync();
    }

    @Deprecated
    public void cycleH() {
        cycleX();
    }

    private static byte clampLevel(byte v) {
        if (v < 0) return 0;
        if (v > 2) return 2;
        return v;
    }

    // ===== Bounds導出 (3軸絶対, FACING非依存) =====

    /**
     * 3軸絶対 Bounds [minX,minY,minZ,maxX,maxY,maxZ] を返す。
     * 常に原点寄せ [0,0,0,xSize,ySize,zSize]。
     */
    public int[] getBoundsAbsolute() {
        int xSize = getXSize();
        int ySize = getYSize();
        int zSize = getZSize();
        return new int[]{0, 0, 0, xSize, ySize, zSize};
    }

    /**
     * @deprecated FACING依存の旧API。絶対Boundsを返す。
     */
    @Deprecated
    public int[] getBounds(net.minecraft.core.Direction facing) {
        return getBoundsAbsolute();
    }

    public int[] getBoundsDefault() {
        return getBoundsAbsolute();
    }

    public VoxelShape getShapeCacheAbsolute() {
        if (this.shapeCache != null) {
            return this.shapeCache;
        }
        if (isEmpty()) {
            this.shapeCache = Shapes.empty();
            return this.shapeCache;
        }
        if (!this.entries.isEmpty()) {
            VoxelShape shape = Shapes.empty();
            for (CutEntry e : this.entries) {
                int[] b = e.bounds;
                VoxelShape part = Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
                shape = Shapes.or(shape, part);
            }
            this.shapeCache = shape;
            return this.shapeCache;
        }
        int[] b = getBoundsAbsolute();
        this.shapeCache = Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        return this.shapeCache;
    }

    @Deprecated
    public VoxelShape getShapeCache(net.minecraft.core.Direction facing) {
        return getShapeCacheAbsolute();
    }

    /** 全エントリの合算 Shape（facing無視） */
    public VoxelShape getShapeCacheUnion() {
        return getShapeCacheAbsolute();
    }

    public void invalidateShapeCache() {
        this.shapeCache = null;
    }

    // ===== 複数エントリ管理 =====

    public java.util.List<CutEntry> getEntries() {
        return java.util.Collections.unmodifiableList(this.entries);
    }

    public int getEntryCount() {
        return this.entries.size();
    }

    /** 新しいエントリが既存と重ならず、0..16に収まるか */
    public boolean canAddEntry(int[] newBounds) {
        if (newBounds == null || newBounds.length < 6) return false;
        int nMinX = newBounds[0], nMinY = newBounds[1], nMinZ = newBounds[2];
        int nMaxX = newBounds[3], nMaxY = newBounds[4], nMaxZ = newBounds[5];
        if (nMinX < 0 || nMinY < 0 || nMinZ < 0 || nMaxX > 16 || nMaxY > 16 || nMaxZ > 16) return false;
        if (nMinX >= nMaxX || nMinY >= nMaxY || nMinZ >= nMaxZ) return false;
        for (CutEntry e : this.entries) {
            int[] b = e.bounds;
            boolean overlapX = nMinX < b[3] && nMaxX > b[0];
            boolean overlapY = nMinY < b[4] && nMaxY > b[1];
            boolean overlapZ = nMinZ < b[5] && nMaxZ > b[2];
            if (overlapX && overlapY && overlapZ) return false;
        }
        // 旧単一が残っている場合、そのBoundsとも重なりチェック
        if (this.entries.isEmpty() && this.cutState != null && !this.cutState.isAir()) {
            int[] oldBounds = getBoundsAbsolute();
            boolean overlapX = nMinX < oldBounds[3] && nMaxX > oldBounds[0];
            boolean overlapY = nMinY < oldBounds[4] && nMaxY > oldBounds[1];
            boolean overlapZ = nMinZ < oldBounds[5] && nMaxZ > oldBounds[2];
            if (overlapX && overlapY && overlapZ) return false;
        }
        return true;
    }

    public boolean addEntry(BlockState state, int[] bounds) {
        if (state == null || state.isAir()) return false;
        if (!canAddEntry(bounds)) return false;
        // 旧単一データを移行: 初回追加時に旧データをentriesへ移す (原点寄せBounds)
        if (this.entries.isEmpty() && this.cutState != null && !this.cutState.isAir()) {
            int[] oldBounds = getBoundsAbsolute();
            this.entries.add(new CutEntry(this.cutState, oldBounds));
            this.cutState = Blocks.AIR.defaultBlockState();
            this.xLevel = 0;
            this.yLevel = 0;
            this.zLevel = 0;
        }
        this.entries.add(new CutEntry(state, bounds));
        this.shapeCache = null;
        markDirtyAndSync();
        return true;
    }

    public void clearEntries() {
        this.entries.clear();
        this.cutState = Blocks.AIR.defaultBlockState();
        this.xLevel = 0;
        this.yLevel = 0;
        this.zLevel = 0;
        this.shapeCache = null;
        markDirtyAndSync();
    }

    public boolean removeEntry(CutEntry entry) {
        boolean removed = this.entries.remove(entry);
        if (removed) {
            this.shapeCache = null;
            markDirtyAndSync();
        }
        return removed;
    }

    // ===== CutState (旧互換) =====

    public BlockState getCutState() {
        if (!this.entries.isEmpty()) {
            return this.entries.get(0).state;
        }
        return this.cutState;
    }

    public void setCutState(BlockState state) {
        if (state == null) {
            state = Blocks.AIR.defaultBlockState();
        }
        this.entries.clear();
        this.cutState = state;
        this.shapeCache = null;
        markDirtyAndSync();
    }

    /** 旧単一 + 新複数を統合した空判定 */
    public boolean isEmpty() {
        if (!this.entries.isEmpty()) return false;
        return this.cutState == null || this.cutState.isAir();
    }

    public boolean isFullCube() {
        if (!this.entries.isEmpty()) {
            for (CutEntry e : this.entries) {
                int[] b = e.bounds;
                if (b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 16 && b[4] == 16 && b[5] == 16) return true;
            }
            return false;
        }
        return this.cutState == null || this.cutState.isAir() || (getXSize() == 16 && getYSize() == 16 && getZSize() == 16);
    }

    // ===== 3軸 Bounds 計算 (ヒット位置・隙間充填) =====

    /** 旧2軸互換 */
    @Deprecated
    public static int[] computeBoundsFromHit(net.minecraft.world.phys.Vec3 hitVec, BlockPos pos, byte yLevel, byte hLevel, net.minecraft.core.Direction facing) {
        return computeBoundsFromHit(hitVec, pos, pos, hLevel, yLevel, (byte) 0, facing, null);
    }

    @Deprecated
    public static int[] computeBoundsFromHitForExisting(net.minecraft.world.phys.Vec3 hitVec, BlockPos clickedPos, byte yLevel, byte hLevel, net.minecraft.core.Direction facing) {
        return computeBoundsFromHit(hitVec, clickedPos, clickedPos, hLevel, yLevel, (byte) 0, facing, null);
    }

    @Deprecated
    public int[] findBestBoundsForPlacement(net.minecraft.world.phys.Vec3 hitVec, BlockPos pos, byte yLevel, byte hLevel, net.minecraft.core.Direction facing, net.minecraft.core.Direction clickedFace) {
        return findBestBoundsForPlacement(hitVec, pos, hLevel, yLevel, (byte) 0, clickedFace);
    }

    /** 3軸: 空き空間を考慮して最適なBoundsを探索（ヒットに最も近く、重ならないもの） */
    public int[] findBestBoundsForPlacement(net.minecraft.world.phys.Vec3 hitVec, BlockPos pos, byte xLevel, byte yLevel, byte zLevel, net.minecraft.core.Direction clickedFace) {
        int xSize = levelToSize(xLevel);
        int ySize = levelToSize(yLevel);
        int zSize = levelToSize(zLevel);
        java.util.List<Integer> xOffsets = new java.util.ArrayList<>();
        java.util.List<Integer> yOffsets = new java.util.ArrayList<>();
        java.util.List<Integer> zOffsets = new java.util.ArrayList<>();
        if (xSize == 16) xOffsets.add(0);
        else if (xSize == 8) { xOffsets.add(0); xOffsets.add(8); }
        else { xOffsets.add(0); xOffsets.add(4); xOffsets.add(8); xOffsets.add(12); }
        if (ySize == 16) yOffsets.add(0);
        else if (ySize == 8) { yOffsets.add(0); yOffsets.add(8); }
        else { yOffsets.add(0); yOffsets.add(4); yOffsets.add(8); yOffsets.add(12); }
        if (zSize == 16) zOffsets.add(0);
        else if (zSize == 8) { zOffsets.add(0); zOffsets.add(8); }
        else { zOffsets.add(0); zOffsets.add(4); zOffsets.add(8); zOffsets.add(12); }

        double hitX = hitVec.x - pos.getX();
        double hitY = hitVec.y - pos.getY();
        double hitZ = hitVec.z - pos.getZ();
        if (hitX < 0) hitX = 0; if (hitX > 1) hitX = 1;
        if (hitY < 0) hitY = 0; if (hitY > 1) hitY = 1;
        if (hitZ < 0) hitZ = 0; if (hitZ > 1) hitZ = 1;
        double hitX16 = hitX * 16;
        double hitY16 = hitY * 16;
        double hitZ16 = hitZ * 16;
        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int xOff : xOffsets) {
            for (int yOff : yOffsets) {
                for (int zOff : zOffsets) {
                    int[] cand = new int[]{xOff, yOff, zOff, xOff + xSize, yOff + ySize, zOff + zSize};
                    if (!canAddEntry(cand)) continue;
                    double cx = (xOff + xOff + xSize) / 2.0;
                    double cy = (yOff + yOff + ySize) / 2.0;
                    double cz = (zOff + zOff + zSize) / 2.0;
                    double dx = cx - hitX16;
                    double dy = cy - hitY16;
                    double dz = cz - hitZ16;
                    double dist = dx * dx + dy * dy + dz * dz;
                    if (dist < bestDist) { bestDist = dist; best = cand; }
                }
            }
        }
        if (best != null) return best;
        return computeBoundsFromHit(hitVec, pos, pos, xLevel, yLevel, zLevel, null, clickedFace);
    }

    /** 汎用: hitVec と targetPos(配置先) と clickedPos/face を考慮してBounds決定 (3軸絶対) */
    public static int[] computeBoundsFromHit(net.minecraft.world.phys.Vec3 hitVec, BlockPos targetPos, BlockPos clickedPos, byte xLevel, byte yLevel, byte zLevel, net.minecraft.core.Direction facing, net.minecraft.core.Direction clickedFace) {
        int xSize = levelToSize(xLevel);
        int ySize = levelToSize(yLevel);
        int zSize = levelToSize(zLevel);
        int minX = 0, minY = 0, minZ = 0;
        int maxX = xSize, maxY = ySize, maxZ = zSize;
        double fracX, fracY, fracZ;
        if (clickedPos != null && clickedFace != null) {
            fracX = hitVec.x - clickedPos.getX();
            fracY = hitVec.y - clickedPos.getY();
            fracZ = hitVec.z - clickedPos.getZ();
        } else {
            fracX = hitVec.x - targetPos.getX();
            fracY = hitVec.y - targetPos.getY();
            fracZ = hitVec.z - targetPos.getZ();
        }
        if (fracX < 0) fracX = 0; if (fracX > 1) fracX = 1;
        if (fracY < 0) fracY = 0; if (fracY > 1) fracY = 1;
        if (fracZ < 0) fracZ = 0; if (fracZ > 1) fracZ = 1;

        // 各軸: clickedFaceが同軸ならヒットは境界上なのでオフセット0固定
        boolean canUseX = true, canUseY = true, canUseZ = true;
        if (clickedFace != null) {
            if (clickedFace.getAxis() == net.minecraft.core.Direction.Axis.X) canUseX = false;
            if (clickedFace.getAxis() == net.minecraft.core.Direction.Axis.Y) canUseY = false;
            if (clickedFace.getAxis() == net.minecraft.core.Direction.Axis.Z) canUseZ = false;
        }
        if (xSize != 16) {
            if (canUseX) {
                if (xSize == 8) {
                    minX = fracX > 0.5 ? 8 : 0;
                    maxX = minX + 8;
                } else if (xSize == 4) {
                    if (fracX < 0.25) minX = 0;
                    else if (fracX < 0.5) minX = 4;
                    else if (fracX < 0.75) minX = 8;
                    else minX = 12;
                    maxX = minX + 4;
                }
            } else {
                minX = 0; maxX = xSize;
            }
        } else {
            minX = 0; maxX = 16;
        }
        if (ySize != 16) {
            if (canUseY) {
                if (ySize == 8) {
                    minY = fracY > 0.5 ? 8 : 0;
                    maxY = minY + 8;
                } else if (ySize == 4) {
                    if (fracY < 0.25) minY = 0;
                    else if (fracY < 0.5) minY = 4;
                    else if (fracY < 0.75) minY = 8;
                    else minY = 12;
                    maxY = minY + 4;
                }
            } else {
                minY = 0; maxY = ySize;
            }
        } else {
            minY = 0; maxY = 16;
        }
        if (zSize != 16) {
            if (canUseZ) {
                if (zSize == 8) {
                    minZ = fracZ > 0.5 ? 8 : 0;
                    maxZ = minZ + 8;
                } else if (zSize == 4) {
                    if (fracZ < 0.25) minZ = 0;
                    else if (fracZ < 0.5) minZ = 4;
                    else if (fracZ < 0.75) minZ = 8;
                    else minZ = 12;
                    maxZ = minZ + 4;
                }
            } else {
                minZ = 0; maxZ = zSize;
            }
        } else {
            minZ = 0; maxZ = 16;
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    // 旧シグネチャ互換 (facing無視)
    public static int[] computeBoundsFromHit(net.minecraft.world.phys.Vec3 hitVec, BlockPos targetPos, BlockPos clickedPos, byte yLevel, byte hLevel, net.minecraft.core.Direction facing, net.minecraft.core.Direction clickedFace) {
        return computeBoundsFromHit(hitVec, targetPos, clickedPos, hLevel, yLevel, (byte) 0, facing, clickedFace);
    }

    // ===== フルキューブ判定（静的） =====

    public static boolean isFullCubeState(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (state == null || state.isAir()) return false;
        if (state.hasBlockEntity()) return false;
        if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) return false;
        try {
            if (Block.isShapeFullBlock(state.getCollisionShape(level, pos))) return true;
            if (Block.isShapeFullBlock(state.getShape(level, pos))) return true;
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
            VoxelShape shape = state.getShape(null, BlockPos.ZERO);
            if (shape != null && Block.isShapeFullBlock(shape)) return true;
            VoxelShape coll = state.getCollisionShape(null, BlockPos.ZERO);
            if (coll != null && Block.isShapeFullBlock(coll)) return true;
        } catch (Exception e) {
            return true;
        }
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
        if (tag == null) {
            this.entries.clear();
            this.cutState = Blocks.AIR.defaultBlockState();
            this.xLevel = 0;
            this.yLevel = 0;
            this.zLevel = 0;
            this.shapeCache = null;
            this.dirty = false;
            this.syncTimer = 0;
            return;
        }
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
        CompoundTag tag = pkt.getTag();
        if (tag == null) {
            this.entries.clear();
            this.cutState = Blocks.AIR.defaultBlockState();
            this.xLevel = 0;
            this.yLevel = 0;
            this.zLevel = 0;
            this.shapeCache = null;
            this.dirty = false;
            this.syncTimer = 0;
            return;
        }
        readSyncData(tag);
    }

    public void writeSyncData(CompoundTag tag) {
        if (!this.entries.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (CutEntry e : this.entries) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.put(TAG_STATE, NbtUtils.writeBlockState(e.state));
                entryTag.putIntArray(TAG_BOUNDS, e.bounds);
                list.add(entryTag);
            }
            tag.put(TAG_ENTRIES, list);
        } else if (this.cutState != null && !this.cutState.isAir()) {
            tag.put(TAG_CUT_STATE, NbtUtils.writeBlockState(this.cutState));
            tag.putByte(TAG_X_LEVEL, this.xLevel);
            tag.putByte(TAG_Y_LEVEL, this.yLevel);
            tag.putByte(TAG_Z_LEVEL, this.zLevel);
        }
    }

    public void readSyncData(CompoundTag tag) {
        if (tag == null) {
            this.entries.clear();
            this.cutState = Blocks.AIR.defaultBlockState();
            this.xLevel = 0;
            this.yLevel = 0;
            this.zLevel = 0;
            this.shapeCache = null;
            this.dirty = false;
            this.syncTimer = 0;
            return;
        }
        this.entries.clear();
        this.shapeCache = null;
        if (tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                if (!entryTag.contains(TAG_STATE, Tag.TAG_COMPOUND)) continue;
                BlockState s = readBlockState(entryTag.getCompound(TAG_STATE));
                if (s == null || s.isAir()) continue;
                int[] bounds = entryTag.getIntArray(TAG_BOUNDS);
                if (bounds.length < 6) continue;
                for (int j = 0; j < 6; j++) {
                    if (bounds[j] < 0) bounds[j] = 0;
                    if (bounds[j] > 16) bounds[j] = 16;
                }
                this.entries.add(new CutEntry(s, bounds));
            }
            this.cutState = Blocks.AIR.defaultBlockState();
            this.xLevel = 0;
            this.yLevel = 0;
            this.zLevel = 0;
        } else {
            if (tag.contains(TAG_CUT_STATE, Tag.TAG_COMPOUND)) {
                CompoundTag stateTag = tag.getCompound(TAG_CUT_STATE);
                BlockState s = readBlockState(stateTag);
                this.cutState = s != null ? s : Blocks.AIR.defaultBlockState();
            } else {
                this.cutState = Blocks.AIR.defaultBlockState();
            }
            if (tag.contains(TAG_X_LEVEL, Tag.TAG_BYTE)) {
                this.xLevel = clampLevel(tag.getByte(TAG_X_LEVEL));
            } else if (tag.contains(TAG_X_LEVEL, Tag.TAG_INT)) {
                this.xLevel = clampLevel((byte) tag.getInt(TAG_X_LEVEL));
            } else if (tag.contains(TAG_H_LEVEL, Tag.TAG_BYTE)) {
                this.xLevel = clampLevel(tag.getByte(TAG_H_LEVEL));
            } else if (tag.contains(TAG_H_LEVEL, Tag.TAG_INT)) {
                this.xLevel = clampLevel((byte) tag.getInt(TAG_H_LEVEL));
            } else {
                this.xLevel = 0;
            }
            if (tag.contains(TAG_Y_LEVEL, Tag.TAG_BYTE)) {
                this.yLevel = clampLevel(tag.getByte(TAG_Y_LEVEL));
            } else if (tag.contains(TAG_Y_LEVEL, Tag.TAG_INT)) {
                this.yLevel = clampLevel((byte) tag.getInt(TAG_Y_LEVEL));
            } else {
                this.yLevel = 0;
            }
            if (tag.contains(TAG_Z_LEVEL, Tag.TAG_BYTE)) {
                this.zLevel = clampLevel(tag.getByte(TAG_Z_LEVEL));
            } else if (tag.contains(TAG_Z_LEVEL, Tag.TAG_INT)) {
                this.zLevel = clampLevel((byte) tag.getInt(TAG_Z_LEVEL));
            } else {
                this.zLevel = 0;
            }
            if (tag.contains(TAG_BOUNDS, Tag.TAG_INT_ARRAY)) {
                int[] bounds = tag.getIntArray(TAG_BOUNDS);
                if (bounds.length >= 6) {
                    int xSize = bounds[3] - bounds[0];
                    int ySize = bounds[4] - bounds[1];
                    int zSize = bounds[5] - bounds[2];
                    this.xLevel = sizeToLevel(xSize);
                    this.yLevel = sizeToLevel(ySize);
                    this.zLevel = sizeToLevel(zSize);
                }
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
     * ItemStackから CutState/X/Y/ZLevel を読み取るヘルパー
     */
    public static CutBlockData readFromStack(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CutBlockData(Blocks.AIR.defaultBlockState(), (byte) 0, (byte) 0, (byte) 0);
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return new CutBlockData(Blocks.AIR.defaultBlockState(), (byte) 0, (byte) 0, (byte) 0);
        }
        CompoundTag bet = tag.contains("BlockEntityTag", Tag.TAG_COMPOUND) ? tag.getCompound("BlockEntityTag") : tag;
        // Entriesがある場合は先頭エントリを代表として返す (表示用)。複数復元は readEntriesFromStack を使う
        if (bet.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = bet.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            if (!list.isEmpty()) {
                CompoundTag entryTag = list.getCompound(0);
                if (entryTag.contains(TAG_STATE, Tag.TAG_COMPOUND)) {
                    BlockState s = readBlockStateFallback(entryTag.getCompound(TAG_STATE));
                    int[] bounds = entryTag.getIntArray(TAG_BOUNDS);
                    if (s != null && !s.isAir() && bounds.length >= 6) {
                        byte xl = sizeToLevel(bounds[3] - bounds[0]);
                        byte yl = sizeToLevel(bounds[4] - bounds[1]);
                        byte zl = sizeToLevel(bounds[5] - bounds[2]);
                        return new CutBlockData(s, xl, yl, zl);
                    }
                }
            }
        }
        BlockState state = Blocks.AIR.defaultBlockState();
        byte xLevel = 0, yLevel = 0, zLevel = 0;
        if (bet.contains(TAG_CUT_STATE, Tag.TAG_COMPOUND)) {
            state = readBlockStateFallback(bet.getCompound(TAG_CUT_STATE));
        }
        if (bet.contains(TAG_X_LEVEL, Tag.TAG_BYTE)) xLevel = clampLevelStatic(bet.getByte(TAG_X_LEVEL));
        else if (bet.contains(TAG_X_LEVEL, Tag.TAG_INT)) xLevel = clampLevelStatic((byte) bet.getInt(TAG_X_LEVEL));
        else if (bet.contains(TAG_H_LEVEL, Tag.TAG_BYTE)) xLevel = clampLevelStatic(bet.getByte(TAG_H_LEVEL));
        else if (bet.contains(TAG_H_LEVEL, Tag.TAG_INT)) xLevel = clampLevelStatic((byte) bet.getInt(TAG_H_LEVEL));
        if (bet.contains(TAG_Y_LEVEL, Tag.TAG_BYTE)) yLevel = clampLevelStatic(bet.getByte(TAG_Y_LEVEL));
        else if (bet.contains(TAG_Y_LEVEL, Tag.TAG_INT)) yLevel = clampLevelStatic((byte) bet.getInt(TAG_Y_LEVEL));
        if (bet.contains(TAG_Z_LEVEL, Tag.TAG_BYTE)) zLevel = clampLevelStatic(bet.getByte(TAG_Z_LEVEL));
        else if (bet.contains(TAG_Z_LEVEL, Tag.TAG_INT)) zLevel = clampLevelStatic((byte) bet.getInt(TAG_Z_LEVEL));
        // Bounds配列があればサイズから逆算 (単一エントリのドロップ由来でXLevelが無い場合のみ)
        if (bet.contains(TAG_BOUNDS, Tag.TAG_INT_ARRAY) && !bet.contains(TAG_X_LEVEL, Tag.TAG_BYTE) && !bet.contains(TAG_X_LEVEL, Tag.TAG_INT)
                && !bet.contains(TAG_Y_LEVEL, Tag.TAG_BYTE) && !bet.contains(TAG_Y_LEVEL, Tag.TAG_INT)
                && !bet.contains(TAG_Z_LEVEL, Tag.TAG_BYTE) && !bet.contains(TAG_Z_LEVEL, Tag.TAG_INT)) {
            int[] b = bet.getIntArray(TAG_BOUNDS);
            if (b.length >= 6) {
                xLevel = sizeToLevel(b[3] - b[0]);
                yLevel = sizeToLevel(b[4] - b[1]);
                zLevel = sizeToLevel(b[5] - b[2]);
            }
        }
        return new CutBlockData(state, xLevel, yLevel, zLevel);
    }

    /**
     * ItemStackから Entries リストを読み取る (複数復元用)。空なら空リスト。
     */
    public static java.util.List<CutEntry> readEntriesFromStack(net.minecraft.world.item.ItemStack stack) {
        java.util.List<CutEntry> result = new java.util.ArrayList<>();
        if (stack == null || stack.isEmpty()) return result;
        CompoundTag tag = stack.getTag();
        if (tag == null) return result;
        CompoundTag bet = tag.contains("BlockEntityTag", Tag.TAG_COMPOUND) ? tag.getCompound("BlockEntityTag") : tag;
        if (!bet.contains(TAG_ENTRIES, Tag.TAG_LIST)) return result;
        net.minecraft.nbt.ListTag list = bet.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            if (!entryTag.contains(TAG_STATE, Tag.TAG_COMPOUND)) continue;
            BlockState s = readBlockStateFallback(entryTag.getCompound(TAG_STATE));
            if (s == null || s.isAir()) continue;
            int[] bounds = entryTag.getIntArray(TAG_BOUNDS);
            if (bounds.length < 6) continue;
            for (int j = 0; j < 6; j++) {
                if (bounds[j] < 0) bounds[j] = 0;
                if (bounds[j] > 16) bounds[j] = 16;
            }
            result.add(new CutEntry(s, bounds));
        }
        return result;
    }

    private static byte clampLevelStatic(byte v) {
        if (v < 0) return 0;
        if (v > 2) return 2;
        return v;
    }

    public record CutBlockData(BlockState state, byte xLevel, byte yLevel, byte zLevel) {
        @Deprecated
        public byte hLevel() { return xLevel; }
    }
}
