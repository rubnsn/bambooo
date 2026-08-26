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
 * 旧仕様の単一 cutState + yLevel/hLevel は互換のため残存するが、新規は entries リストで管理。
 * 仕様: docs/port-spec-cutblock.md §2.1 + 2026-08-27 拡張(ヒット位置・隙間充填)
 */
public class CutBlockEntity extends BlockEntity {

    public static final String TAG_CUT_STATE = "CutState";
    public static final String TAG_Y_LEVEL = "YLevel";
    public static final String TAG_H_LEVEL = "HLevel";
    // 互換用: 旧Bounds配列
    public static final String TAG_BOUNDS = "Bounds";
    // 新: 複数エントリ
    public static final String TAG_ENTRIES = "Entries";
    public static final String TAG_STATE = "State";

    private static final int SYNC_DELAY = 5;

    // 旧単一互換
    private BlockState cutState = Blocks.AIR.defaultBlockState();
    private byte yLevel = 0; // 0=16, 1=8, 2=4
    private byte hLevel = 0; // 0=16, 1=8, 2=4

    // 新: 複数エントリ
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
        // 新: 複数エントリがある場合は合算、旧単一互換は従来のgetBoundsを使用
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
        int[] b = getBounds(facing);
        this.shapeCache = Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        return this.shapeCache;
    }

    /** 全エントリの合算 Shape（facing無視、BEWLR/衝突用） */
    public VoxelShape getShapeCacheUnion() {
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
        return getShapeCache(net.minecraft.core.Direction.NORTH);
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
        // 旧単一が残っている場合、そのBoundsとも重なりチェック（移行期）
        if (this.entries.isEmpty() && this.cutState != null && !this.cutState.isAir()) {
            int[] oldBounds = getBounds(net.minecraft.core.Direction.NORTH);
            // facingはBEのBlockStateから取得すべきだが、ここでは近似でNORTHを使用
            // 旧データは単一なので、重なる場合は追加不可
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
        // 旧単一データを移行: 初回追加時に旧データをentriesへ移す
        if (this.entries.isEmpty() && this.cutState != null && !this.cutState.isAir()) {
            int[] oldBounds = getBounds(net.minecraft.core.Direction.NORTH);
            // 旧 facing を考慮すべきだが、近似でNORTHのBoundsを使用（後で上書きされる）
            // 正確には BlockStateのFACINGで再計算すべきだが、ここではBEがまだ level==null の場合もあるためデフォルト
            this.entries.add(new CutEntry(this.cutState, oldBounds));
            this.cutState = Blocks.AIR.defaultBlockState();
            this.yLevel = 0;
            this.hLevel = 0;
        }
        this.entries.add(new CutEntry(state, bounds));
        this.shapeCache = null;
        markDirtyAndSync();
        return true;
    }

    public void clearEntries() {
        this.entries.clear();
        this.cutState = Blocks.AIR.defaultBlockState();
        this.yLevel = 0;
        this.hLevel = 0;
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
        // 新方式では entries をクリアして単一に
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
            // いずれかがフルならフルとみなす（簡易）
            for (CutEntry e : this.entries) {
                int[] b = e.bounds;
                if (b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 16 && b[4] == 16 && b[5] == 16) return true;
            }
            return false;
        }
        return this.cutState == null || this.cutState.isAir() || (getYSize() == 16 && getHSize() == 16);
    }

    /** ヒット位置から Bounds を決定（狙った位置に設置）— 新規配置用（placePos基準） */
    public static int[] computeBoundsFromHit(net.minecraft.world.phys.Vec3 hitVec, BlockPos pos, byte yLevel, byte hLevel, net.minecraft.core.Direction facing) {
        return computeBoundsFromHit(hitVec, pos, pos, yLevel, hLevel, facing, null);
    }

    /** 既存BEの隙間充填用: clickedPos基準でヒット位置からBoundsを決定 */
    public static int[] computeBoundsFromHitForExisting(net.minecraft.world.phys.Vec3 hitVec, BlockPos clickedPos, byte yLevel, byte hLevel, net.minecraft.core.Direction facing) {
        return computeBoundsFromHit(hitVec, clickedPos, clickedPos, yLevel, hLevel, facing, null);
    }

    /** 空き空間を考慮して最適なBoundsを探索（ヒットに最も近く、重ならないもの） */
    public int[] findBestBoundsForPlacement(net.minecraft.world.phys.Vec3 hitVec, BlockPos pos, byte yLevel, byte hLevel, net.minecraft.core.Direction facing, net.minecraft.core.Direction clickedFace) {
        int ySize = levelToSize(yLevel);
        int hSize = levelToSize(hLevel);
        java.util.List<Integer> yOffsets = new java.util.ArrayList<>();
        if (ySize == 16) yOffsets.add(0);
        else if (ySize == 8) { yOffsets.add(0); yOffsets.add(8); }
        else if (ySize == 4) { yOffsets.add(0); yOffsets.add(4); yOffsets.add(8); yOffsets.add(12); }
        java.util.List<Integer> hOffsets = new java.util.ArrayList<>();
        if (hSize == 16) hOffsets.add(0);
        else if (hSize == 8) { hOffsets.add(0); hOffsets.add(8); }
        else if (hSize == 4) { hOffsets.add(0); hOffsets.add(4); hOffsets.add(8); hOffsets.add(12); }
        double hitY = hitVec.y - pos.getY();
        double hitH;
        boolean isX = facing == net.minecraft.core.Direction.NORTH || facing == net.minecraft.core.Direction.SOUTH;
        if (isX) hitH = hitVec.x - pos.getX();
        else hitH = hitVec.z - pos.getZ();
        if (hitY < 0) hitY = 0; if (hitY > 1) hitY = 1;
        if (hitH < 0) hitH = 0; if (hitH > 1) hitH = 1;
        double hitY16 = hitY * 16;
        double hitH16 = hitH * 16;
        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int yOff : yOffsets) {
            for (int hOff : hOffsets) {
                int minX = 0, minY = yOff, minZ = 0, maxX = 16, maxY = yOff + ySize, maxZ = 16;
                if (isX) { minX = hOff; maxX = hOff + hSize; minZ = 0; maxZ = 16; }
                else { minZ = hOff; maxZ = hOff + hSize; minX = 0; maxX = 16; }
                int[] cand = new int[]{minX, minY, minZ, maxX, maxY, maxZ};
                if (!canAddEntry(cand)) continue;
                double cx = (minX + maxX) / 2.0;
                double cy = (minY + maxY) / 2.0;
                double cz = (minZ + maxZ) / 2.0;
                double hx = isX ? hitH * 16 : 8;
                double hz = !isX ? hitH * 16 : 8;
                double hy = hitY16;
                double dx = isX ? cx - hx : 0;
                double dz = !isX ? cz - hz : 0;
                double dy = cy - hy;
                double dist = dy * dy + dx * dx + dz * dz;
                if (dist < bestDist) { bestDist = dist; best = cand; }
            }
        }
        if (best != null) return best;
        return computeBoundsFromHit(hitVec, pos, pos, yLevel, hLevel, facing, clickedFace);
    }

    /** 汎用: hitVec と targetPos(配置先) と clickedPos/face を考慮してBounds決定 */
    public static int[] computeBoundsFromHit(net.minecraft.world.phys.Vec3 hitVec, BlockPos targetPos, BlockPos clickedPos, byte yLevel, byte hLevel, net.minecraft.core.Direction facing, net.minecraft.core.Direction clickedFace) {
        int ySize = levelToSize(yLevel);
        int hSize = levelToSize(hLevel);
        int minX = 0, minY = 0, minZ = 0;
        int maxX = 16, maxY = ySize, maxZ = 16;
        // ヒットのワールド座標から、targetPos内のローカル座標(0-16)を求める
        // ただし、clickedFaceがY軸ならYは境界上なので、clickedPos基準のfracYを使用
        double fracX, fracY, fracZ;
        if (clickedPos != null && clickedFace != null) {
            // Yは常にclickedPos基準のfracYを使用（側面クリック時の高さで上下を決定）
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
        // Y軸: 側面クリック時はhitYで上下を決定、上面/下面クリック時は常に下側（0）とする（天井/床への自由配置は別途）
        if (ySize != 16) {
            boolean isSideFace = clickedFace != null && clickedFace.getAxis() != net.minecraft.core.Direction.Axis.Y;
            if (isSideFace || clickedFace == null) {
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
                // 上面/下面は常に下側（床/天井は別途Y=12等も可能だが、まずは0）
                minY = 0; maxY = ySize;
            }
        }
        // H軸: facingで軸を決定し、ヒット位置でオフセットを決定
        if (hSize != 16) {
            // H軸がXかZかで、使用するfracを決定
            // ただし、clickedFaceがH軸と同軸なら、その面へのクリックではhitの該当軸は境界上なので、もう一方の軸を使用できない
            // 例: facing NORTH(SOUTH) は X軸、clickedFace EAST/WEST(X軸)なら hitXは境界上なので使えない → ZではなくX? 実際は使えないのでデフォルト0
            boolean canUseX = true, canUseZ = true;
            if (clickedFace != null) {
                if (clickedFace.getAxis() == net.minecraft.core.Direction.Axis.X) canUseX = false;
                if (clickedFace.getAxis() == net.minecraft.core.Direction.Axis.Z) canUseZ = false;
            }
            switch (facing) {
                case NORTH, SOUTH -> {
                    // X軸
                    if (canUseX) {
                        if (hSize == 8) {
                            minX = fracX > 0.5 ? 8 : 0;
                            maxX = minX + 8;
                        } else if (hSize == 4) {
                            if (fracX < 0.25) minX = 0;
                            else if (fracX < 0.5) minX = 4;
                            else if (fracX < 0.75) minX = 8;
                            else minX = 12;
                            maxX = minX + 4;
                        }
                    } else {
                        // 使えない場合は常に0
                        minX = 0; maxX = hSize;
                    }
                    minZ = 0; maxZ = 16;
                }
                case EAST, WEST -> {
                    // Z軸
                    if (canUseZ) {
                        if (hSize == 8) {
                            minZ = fracZ > 0.5 ? 8 : 0;
                            maxZ = minZ + 8;
                        } else if (hSize == 4) {
                            if (fracZ < 0.25) minZ = 0;
                            else if (fracZ < 0.5) minZ = 4;
                            else if (fracZ < 0.75) minZ = 8;
                            else minZ = 12;
                            maxZ = minZ + 4;
                        }
                    } else {
                        minZ = 0; maxZ = hSize;
                    }
                    minX = 0; maxX = 16;
                }
                default -> {
                    if (hSize == 8) {
                        minX = fracX > 0.5 ? 8 : 0;
                        maxX = minX + 8;
                    }
                }
            }
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
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
        // 新: Entriesがあればそちらを優先
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
            tag.putByte(TAG_Y_LEVEL, this.yLevel);
            tag.putByte(TAG_H_LEVEL, this.hLevel);
        } else {
            // 空の場合でもEntriesは空リストとして書かない（BEが空であることを示す）
        }
    }

    public void readSyncData(CompoundTag tag) {
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
                // クランプ
                for (int j = 0; j < 6; j++) {
                    if (bounds[j] < 0) bounds[j] = 0;
                    if (bounds[j] > 16) bounds[j] = 16;
                }
                this.entries.add(new CutEntry(s, bounds));
            }
            // 旧単一フィールドはクリア
            this.cutState = Blocks.AIR.defaultBlockState();
            this.yLevel = 0;
            this.hLevel = 0;
        } else {
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
            // 互換: Bounds配列があればY/Hを逆算（旧データ移行用）
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
