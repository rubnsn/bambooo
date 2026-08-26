package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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

    // ===== 3種簡素化 Tier 判定 =====
    public static boolean isHalfLevels(byte x, byte y, byte z) {
        return (x == 1 && y == 0 && z == 0) || (x == 0 && y == 1 && z == 0) || (x == 0 && y == 0 && z == 1);
    }

    public static boolean isEightLevels(byte x, byte y, byte z) {
        return x == 1 && y == 1 && z == 1;
    }

    public static boolean isQuarterLevels(byte x, byte y, byte z) {
        return x == 2 && y == 2 && z == 2;
    }

    public static boolean isFullLevels(byte x, byte y, byte z) {
        return x == 0 && y == 0 && z == 0;
    }

    public enum Tier { FULL, HALF, EIGHT, QUARTER, OTHER }

    public static Tier getTierFromLevels(byte x, byte y, byte z) {
        if (isFullLevels(x, y, z)) return Tier.FULL;
        if (isHalfLevels(x, y, z)) return Tier.HALF;
        if (isEightLevels(x, y, z)) return Tier.EIGHT;
        if (isQuarterLevels(x, y, z)) return Tier.QUARTER;
        return Tier.OTHER;
    }

    public static Tier getTierFromStack(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Tier.OTHER;
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
            BlockState st = bi.getBlock().defaultBlockState();
            if (isFullCubeState(st)) return Tier.FULL;
            return Tier.OTHER;
        }
        // cut_block は level で判定
        CutBlockData d = readFromStack(stack);
        if (d.state().isAir()) return Tier.OTHER;
        // entries持ちは単一とみなさない（混合）
        if (!readEntriesFromStack(stack).isEmpty()) return Tier.OTHER;
        return getTierFromLevels(d.xLevel(), d.yLevel(), d.zLevel());
    }

    public static boolean isHalfBounds(int[] b) {
        if (b == null || b.length < 6) return false;
        int xs = b[3] - b[0], ys = b[4] - b[1], zs = b[5] - b[2];
        return (xs == 8 && ys == 16 && zs == 16) || (xs == 16 && ys == 8 && zs == 16) || (xs == 16 && ys == 16 && zs == 8);
    }

    public static boolean isEightBounds(int[] b) {
        if (b == null || b.length < 6) return false;
        return b[3] - b[0] == 8 && b[4] - b[1] == 8 && b[5] - b[2] == 8;
    }

    public static boolean isQuarterBounds(int[] b) {
        if (b == null || b.length < 6) return false;
        return b[3] - b[0] == 4 && b[4] - b[1] == 4 && b[5] - b[2] == 4;
    }

    /** ハーフ(8x16x16) 6姿勢をヒット位置と面から決定。中央8x8は面平行、周辺は縦半 */
    public static int[] computeHalfBounds(net.minecraft.world.phys.Vec3 hitVec, BlockPos clickedPos, Direction clickedFace) {
        double fx = hitVec.x - clickedPos.getX();
        double fy = hitVec.y - clickedPos.getY();
        double fz = hitVec.z - clickedPos.getZ();
        // clamp 0-1
        if (fx < 0) fx = 0; if (fx > 1) fx = 1;
        if (fy < 0) fy = 0; if (fy > 1) fy = 1;
        if (fz < 0) fz = 0; if (fz > 1) fz = 1;
        // 面ごとの平面軸と中心判定（中央8x8 = 0.25-0.75）
        switch (clickedFace) {
            case UP, DOWN -> {
                boolean centerX = fx >= 0.25 && fx <= 0.75;
                boolean centerZ = fz >= 0.25 && fz <= 0.75;
                if (centerX && centerZ) {
                    // 面平行ハーフ
                    if (clickedFace == Direction.UP) return new int[]{0, 0, 0, 16, 8, 16}; // 下半
                    else return new int[]{0, 8, 0, 16, 16, 16}; // 上半
                }
                double dx = Math.abs(fx - 0.5);
                double dz = Math.abs(fz - 0.5);
                if (dx > dz) {
                    if (fx < 0.5) return new int[]{0, 0, 0, 8, 16, 16}; // 西
                    else return new int[]{8, 0, 0, 16, 16, 16}; // 東
                } else {
                    if (fz < 0.5) return new int[]{0, 0, 0, 16, 16, 8}; // 北
                    else return new int[]{0, 0, 8, 16, 16, 16}; // 南
                }
            }
            case NORTH, SOUTH -> {
                boolean centerX = fx >= 0.25 && fx <= 0.75;
                boolean centerY = fy >= 0.25 && fy <= 0.75;
                if (centerX && centerY) {
                    if (clickedFace == Direction.NORTH) return new int[]{0, 0, 8, 16, 16, 16}; // 南半
                    else return new int[]{0, 0, 0, 16, 16, 8}; // 北半
                }
                double dx = Math.abs(fx - 0.5);
                double dy = Math.abs(fy - 0.5);
                if (dx > dy) {
                    if (fx < 0.5) return new int[]{0, 0, 0, 8, 16, 16};
                    else return new int[]{8, 0, 0, 16, 16, 16};
                } else {
                    if (fy < 0.5) return new int[]{0, 0, 0, 16, 8, 16};
                    else return new int[]{0, 8, 0, 16, 16, 16};
                }
            }
            case WEST, EAST -> {
                boolean centerZ = fz >= 0.25 && fz <= 0.75;
                boolean centerY = fy >= 0.25 && fy <= 0.75;
                if (centerZ && centerY) {
                    if (clickedFace == Direction.WEST) return new int[]{8, 0, 0, 16, 16, 16}; // 東半
                    else return new int[]{0, 0, 0, 8, 16, 16}; // 西半
                }
                double dz = Math.abs(fz - 0.5);
                double dy = Math.abs(fy - 0.5);
                if (dz > dy) {
                    if (fz < 0.5) return new int[]{0, 0, 0, 16, 16, 8};
                    else return new int[]{0, 0, 8, 16, 16, 16};
                } else {
                    if (fy < 0.5) return new int[]{0, 0, 0, 16, 8, 16};
                    else return new int[]{0, 8, 0, 16, 16, 16};
                }
            }
            default -> {}
        }
        return new int[]{0, 0, 0, 8, 16, 16};
    }

    /** 新規配置用キューブ(8 or 4) のbounds。面隣接側に寄せる */
    public static int[] computeCubeBoundsForNewPlacement(net.minecraft.world.phys.Vec3 hitVec, BlockPos clickedPos, Direction clickedFace, int size) {
        double fx = hitVec.x - clickedPos.getX();
        double fy = hitVec.y - clickedPos.getY();
        double fz = hitVec.z - clickedPos.getZ();
        if (fx < 0) fx = 0; if (fx > 1) fx = 1;
        if (fy < 0) fy = 0; if (fy > 1) fy = 1;
        if (fz < 0) fz = 0; if (fz > 1) fz = 1;
        int xOff, yOff, zOff;
        if (size == 8) {
            xOff = fx > 0.5 ? 8 : 0;
            yOff = fy > 0.5 ? 8 : 0;
            zOff = fz > 0.5 ? 8 : 0;
        } else {
            if (fx < 0.25) xOff = 0; else if (fx < 0.5) xOff = 4; else if (fx < 0.75) xOff = 8; else xOff = 12;
            if (fy < 0.25) yOff = 0; else if (fy < 0.5) yOff = 4; else if (fy < 0.75) yOff = 8; else yOff = 12;
            if (fz < 0.25) zOff = 0; else if (fz < 0.5) zOff = 4; else if (fz < 0.75) zOff = 8; else zOff = 12;
        }
        // 法線軸は隣接側に固定
        switch (clickedFace) {
            case UP -> yOff = 0;
            case DOWN -> yOff = 16 - size;
            case NORTH -> zOff = 16 - size;
            case SOUTH -> zOff = 0;
            case WEST -> xOff = 16 - size;
            case EAST -> xOff = 0;
            default -> {}
        }
        return new int[]{xOff, yOff, zOff, xOff + size, yOff + size, zOff + size};
    }

    // ===== 前面(同一座標内空隙)/背面(隣接) 判定 — RAYのHIT面で正しく分岐 =====
    // hitVec + face*eps が同一BlockPos内の空隙セルに入るなら前面(=inside)、外へ出るなら背面(=adjacent)
    private static final double HIT_EPS = 0.005;

    /** 既存BE内で hitVec+face*eps が空隙セルに入るかを判定。Tierで分岐し canAddEntry も確認 */
    public static boolean shouldFillInside(CutBlockEntity be, BlockPos bePos, net.minecraft.world.phys.Vec3 hitVec, Direction face, Tier tier) {
        return getInsideCandidate(be, bePos, hitVec, face, tier) != null;
    }

    /** 前面判定が真の時の candidate bounds を返す。偽なら null */
    public static int[] getInsideCandidate(CutBlockEntity be, BlockPos bePos, net.minecraft.world.phys.Vec3 hitVec, Direction face, Tier tier) {
        if (be == null || bePos == null || hitVec == null || face == null || tier == null) return null;
        if (tier == Tier.OTHER || tier == Tier.FULL) return null;
        if (be.isEmpty()) return null;
        net.minecraft.world.phys.Vec3 insidePos = hitVec.add(face.getStepX() * HIT_EPS, face.getStepY() * HIT_EPS, face.getStepZ() * HIT_EPS);
        double fx = insidePos.x - bePos.getX();
        double fy = insidePos.y - bePos.getY();
        double fz = insidePos.z - bePos.getZ();
        if (fx < -1e-6 || fx > 1 + 1e-6 || fy < -1e-6 || fy > 1 + 1e-6 || fz < -1e-6 || fz > 1 + 1e-6) {
            return null; // 外へ出た → 隣接
        }
        if (fx < 0) fx = 0; if (fx > 1) fx = 1;
        if (fy < 0) fy = 0; if (fy > 1) fy = 1;
        if (fz < 0) fz = 0; if (fz > 1) fz = 1;
        int[] candidate = null;
        if (tier == Tier.HALF) {
            candidate = computeHalfBoundsForExistingInternal(hitVec, bePos, face);
            if (candidate == null) return null;
            if (!containsVec(insidePos, bePos, candidate)) return null;
        } else if (tier == Tier.EIGHT) {
            int xo = (int)Math.floor(fx * 2) * 8;
            int yo = (int)Math.floor(fy * 2) * 8;
            int zo = (int)Math.floor(fz * 2) * 8;
            if (xo < 0) xo = 0; if (xo > 8) xo = 8;
            if (yo < 0) yo = 0; if (yo > 8) yo = 8;
            if (zo < 0) zo = 0; if (zo > 8) zo = 8;
            candidate = new int[]{xo, yo, zo, xo + 8, yo + 8, zo + 8};
        } else if (tier == Tier.QUARTER) {
            int xo = (int)Math.floor(fx * 4) * 4;
            int yo = (int)Math.floor(fy * 4) * 4;
            int zo = (int)Math.floor(fz * 4) * 4;
            if (xo < 0) xo = 0; if (xo > 12) xo = 12;
            if (yo < 0) yo = 0; if (yo > 12) yo = 12;
            if (zo < 0) zo = 0; if (zo > 12) zo = 12;
            candidate = new int[]{xo, yo, zo, xo + 4, yo + 4, zo + 4};
        }
        if (candidate == null) return null;
        if (!be.canAddEntry(candidate)) return null;
        return candidate;
    }

    /** candidate bounds が insidePos を含むか */
    private static boolean containsVec(net.minecraft.world.phys.Vec3 pos, BlockPos origin, int[] b) {
        double fx = pos.x - origin.getX();
        double fy = pos.y - origin.getY();
        double fz = pos.z - origin.getZ();
        double x = fx * 16, y = fy * 16, z = fz * 16;
        double eps = 1e-6;
        return x >= b[0] - eps && x <= b[3] + eps && y >= b[1] - eps && y <= b[4] + eps && z >= b[2] - eps && z <= b[5] + eps;
    }

    /** HALF既存充填用のboundsを hitVec/face から算出。CutBlockItemの private をBE側に移設 */
    public static int[] computeHalfBoundsForExistingInternal(net.minecraft.world.phys.Vec3 hitVec, BlockPos pos, Direction face) {
        double fx = hitVec.x - pos.getX();
        double fy = hitVec.y - pos.getY();
        double fz = hitVec.z - pos.getZ();
        if (fx < 0) fx = 0; if (fx > 1) fx = 1;
        if (fy < 0) fy = 0; if (fy > 1) fy = 1;
        if (fz < 0) fz = 0; if (fz > 1) fz = 1;
        switch (face) {
            case UP -> {
                boolean cx = fx >= 0.25 && fx <= 0.75;
                boolean cz = fz >= 0.25 && fz <= 0.75;
                if (cx && cz) return new int[]{0, 8, 0, 16, 16, 16};
                double dx = Math.abs(fx - 0.5), dz = Math.abs(fz - 0.5);
                if (dx > dz) return fx < 0.5 ? new int[]{0,0,0,8,16,16} : new int[]{8,0,0,16,16,16};
                else return fz < 0.5 ? new int[]{0,0,0,16,16,8} : new int[]{0,0,8,16,16,16};
            }
            case DOWN -> {
                boolean cx = fx >= 0.25 && fx <= 0.75;
                boolean cz = fz >= 0.25 && fz <= 0.75;
                if (cx && cz) return new int[]{0, 0, 0, 16, 8, 16};
                double dx = Math.abs(fx - 0.5), dz = Math.abs(fz - 0.5);
                if (dx > dz) return fx < 0.5 ? new int[]{0,0,0,8,16,16} : new int[]{8,0,0,16,16,16};
                else return fz < 0.5 ? new int[]{0,0,0,16,16,8} : new int[]{0,0,8,16,16,16};
            }
            case NORTH -> {
                boolean cx = fx >= 0.25 && fx <= 0.75;
                boolean cy = fy >= 0.25 && fy <= 0.75;
                if (cx && cy) return new int[]{0, 0, 0, 16, 16, 8};
                double dx = Math.abs(fx - 0.5), dy = Math.abs(fy - 0.5);
                if (dx > dy) return fx < 0.5 ? new int[]{0,0,0,8,16,16} : new int[]{8,0,0,16,16,16};
                else return fy < 0.5 ? new int[]{0,0,0,16,8,16} : new int[]{0,8,0,16,16,16};
            }
            case SOUTH -> {
                boolean cx = fx >= 0.25 && fx <= 0.75;
                boolean cy = fy >= 0.25 && fy <= 0.75;
                if (cx && cy) return new int[]{0, 0, 8, 16, 16, 16};
                double dx = Math.abs(fx - 0.5), dy = Math.abs(fy - 0.5);
                if (dx > dy) return fx < 0.5 ? new int[]{0,0,0,8,16,16} : new int[]{8,0,0,16,16,16};
                else return fy < 0.5 ? new int[]{0,0,0,16,8,16} : new int[]{0,8,0,16,16,16};
            }
            case WEST -> {
                boolean cz = fz >= 0.25 && fz <= 0.75;
                boolean cy = fy >= 0.25 && fy <= 0.75;
                if (cz && cy) return new int[]{0, 0, 0, 8, 16, 16};
                double dz = Math.abs(fz - 0.5), dy = Math.abs(fy - 0.5);
                if (dz > dy) return fz < 0.5 ? new int[]{0,0,0,16,16,8} : new int[]{0,0,8,16,16,16};
                else return fy < 0.5 ? new int[]{0,0,0,16,8,16} : new int[]{0,8,0,16,16,16};
            }
            case EAST -> {
                boolean cz = fz >= 0.25 && fz <= 0.75;
                boolean cy = fy >= 0.25 && fy <= 0.75;
                if (cz && cy) return new int[]{8, 0, 0, 16, 16, 16};
                double dz = Math.abs(fz - 0.5), dy = Math.abs(fy - 0.5);
                if (dz > dy) return fz < 0.5 ? new int[]{0,0,0,16,16,8} : new int[]{0,0,8,16,16,16};
                else return fy < 0.5 ? new int[]{0,0,0,16,8,16} : new int[]{0,8,0,16,16,16};
            }
            default -> {}
        }
        return new int[]{0, 0, 0, 8, 16, 16};
    }

    /** 隣接BE用の candidate を返す。前面判定が真なら bounds、偽なら null */
    public static int[] getAdjacentCandidate(CutBlockEntity placeBe, BlockPos placePos, net.minecraft.world.phys.Vec3 hitVec, Direction face, Tier tier) {
        if (placeBe == null || placePos == null || hitVec == null || face == null || tier == null) return null;
        if (tier == Tier.OTHER || tier == Tier.FULL) return null;
        if (placeBe.isEmpty()) return null;
        net.minecraft.world.phys.Vec3 insidePlace = hitVec.add(face.getStepX() * HIT_EPS, face.getStepY() * HIT_EPS, face.getStepZ() * HIT_EPS);
        double fx = insidePlace.x - placePos.getX();
        double fy = insidePlace.y - placePos.getY();
        double fz = insidePlace.z - placePos.getZ();
        if (fx < -1e-6 || fx > 1 + 1e-6 || fy < -1e-6 || fy > 1 + 1e-6 || fz < -1e-6 || fz > 1 + 1e-6) return null;
        if (fx < 0) fx = 0; if (fx > 1) fx = 1;
        if (fy < 0) fy = 0; if (fy > 1) fy = 1;
        if (fz < 0) fz = 0; if (fz > 1) fz = 1;
        int[] candidate;
        if (tier == Tier.HALF) {
            candidate = computeHalfBounds(insidePlace, placePos, face);
            if (candidate == null) candidate = computeHalfBounds(hitVec, placePos, face);
        } else if (tier == Tier.EIGHT) {
            int xo = (int)Math.floor(fx * 2) * 8;
            int yo = (int)Math.floor(fy * 2) * 8;
            int zo = (int)Math.floor(fz * 2) * 8;
            candidate = new int[]{xo, yo, zo, xo + 8, yo + 8, zo + 8};
        } else if (tier == Tier.QUARTER) {
            int xo = (int)Math.floor(fx * 4) * 4;
            int yo = (int)Math.floor(fy * 4) * 4;
            int zo = (int)Math.floor(fz * 4) * 4;
            candidate = new int[]{xo, yo, zo, xo + 4, yo + 4, zo + 4};
        } else return null;
        if (candidate == null) return null;
        if (!placeBe.canAddEntry(candidate)) return null;
        if (!containsVec(insidePlace, placePos, candidate)) return null;
        return candidate;
    }

    /** 隣接BE用にも同一判定を流用。insidePos( hitVec+face*eps )が placePos 内に入るかで判定 */
    public static boolean shouldFillAdjacentInside(CutBlockEntity placeBe, BlockPos placePos, net.minecraft.world.phys.Vec3 hitVec, Direction face, Tier tier) {
        return getAdjacentCandidate(placeBe, placePos, hitVec, face, tier) != null;
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
            // 形状がフルでない場合はisSolidRenderで判定。階段/ハーフはここでfalse
            try {
                return state.isSolidRender(null, BlockPos.ZERO);
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ===== Rotation (水平のみ・バニラ依存) =====

    public void rotate(Rotation rot) {
        if (rot == null || rot == Rotation.NONE) return;
        if (!this.entries.isEmpty()) {
            java.util.List<CutEntry> rotated = new java.util.ArrayList<>();
            for (CutEntry e : this.entries) {
                rotated.add(new CutEntry(e.state, rotateBounds(e.bounds, rot)));
            }
            this.entries.clear();
            this.entries.addAll(rotated);
            this.shapeCache = null;
            markDirtyAndSync();
            return;
        }
        if (this.cutState == null || this.cutState.isAir()) return;
        int[] oldBounds = getBoundsAbsolute();
        int[] newBounds = rotateBounds(oldBounds, rot);
        if (newBounds[0] == 0 && newBounds[1] == 0 && newBounds[2] == 0) {
            // 原点寄せのまま収まる（90°でサイズ入替等）→ x/zLevel入れ替えで表現可能
            byte nx = sizeToLevel(newBounds[3] - newBounds[0]);
            byte ny = sizeToLevel(newBounds[4] - newBounds[1]);
            byte nz = sizeToLevel(newBounds[5] - newBounds[2]);
            this.xLevel = nx;
            this.yLevel = ny;
            this.zLevel = nz;
            this.shapeCache = null;
            markDirtyAndSync();
        } else {
            // 原点からずれる（180°の半分等）→ entries化して正確に保持
            BlockState st = this.cutState;
            this.cutState = Blocks.AIR.defaultBlockState();
            this.xLevel = 0;
            this.yLevel = 0;
            this.zLevel = 0;
            this.entries.clear();
            this.entries.add(new CutEntry(st, newBounds));
            this.shapeCache = null;
            markDirtyAndSync();
        }
    }

    public void mirror(Mirror mirror) {
        if (mirror == null || mirror == Mirror.NONE) return;
        if (!this.entries.isEmpty()) {
            java.util.List<CutEntry> mirrored = new java.util.ArrayList<>();
            for (CutEntry e : this.entries) {
                mirrored.add(new CutEntry(e.state, mirrorBounds(e.bounds, mirror)));
            }
            this.entries.clear();
            this.entries.addAll(mirrored);
            this.shapeCache = null;
            markDirtyAndSync();
            return;
        }
        if (this.cutState == null || this.cutState.isAir()) return;
        int[] oldBounds = getBoundsAbsolute();
        int[] newBounds = mirrorBounds(oldBounds, mirror);
        if (newBounds[0] == 0 && newBounds[1] == 0 && newBounds[2] == 0) {
            // 原点寄せで収まる場合はそのまま（サイズ不変のため通常は何も変わらないが一応）
            this.shapeCache = null;
            markDirtyAndSync();
        } else {
            BlockState st = this.cutState;
            this.cutState = Blocks.AIR.defaultBlockState();
            this.xLevel = 0;
            this.yLevel = 0;
            this.zLevel = 0;
            this.entries.clear();
            this.entries.add(new CutEntry(st, newBounds));
            this.shapeCache = null;
            markDirtyAndSync();
        }
    }

    private static int[] rotateBounds(int[] b, Rotation rot) {
        int minX = b[0], minY = b[1], minZ = b[2], maxX = b[3], maxY = b[4], maxZ = b[5];
        return switch (rot) {
            case CLOCKWISE_90 -> new int[]{16 - maxZ, minY, minX, 16 - minZ, maxY, maxX};
            case CLOCKWISE_180 -> new int[]{16 - maxX, minY, 16 - maxZ, 16 - minX, maxY, 16 - minZ};
            case COUNTERCLOCKWISE_90 -> new int[]{minZ, minY, 16 - maxX, maxZ, maxY, 16 - minX};
            default -> b.clone();
        };
    }

    private static int[] mirrorBounds(int[] b, Mirror mirror) {
        int minX = b[0], minY = b[1], minZ = b[2], maxX = b[3], maxY = b[4], maxZ = b[5];
        return switch (mirror) {
            case LEFT_RIGHT -> new int[]{16 - maxX, minY, minZ, 16 - minX, maxY, maxZ};
            case FRONT_BACK -> new int[]{minX, minY, 16 - maxZ, maxX, maxY, 16 - minZ};
            default -> b.clone();
        };
    }

    static Rotation getRotationFromFacing(Direction from, Direction to) {
        if (from == to) return Rotation.NONE;
        int f = from.get2DDataValue();
        int t = to.get2DDataValue();
        if (f < 0 || t < 0) return Rotation.NONE;
        int diff = (t - f + 4) % 4;
        return switch (diff) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    @Override
    public void setBlockState(BlockState state) {
        BlockState old = this.getBlockState();
        // pendingがあればそれを優先（CutBlock.rotate/mirrorからのThreadLocal）
        Rotation pendingRot = ruby.bamboo.block.CutBlock.consumePendingRotation();
        Mirror pendingMirror = ruby.bamboo.block.CutBlock.consumePendingMirror();
        boolean handled = false;
        if (pendingRot != null && pendingRot != Rotation.NONE) {
            rotate(pendingRot);
            handled = true;
        }
        if (pendingMirror != null && pendingMirror != Mirror.NONE) {
            mirror(pendingMirror);
            handled = true;
        }
        if (!handled && old != null && old.hasProperty(ruby.bamboo.block.CutBlock.FACING)
                && state.hasProperty(ruby.bamboo.block.CutBlock.FACING)) {
            Direction of = old.getValue(ruby.bamboo.block.CutBlock.FACING);
            Direction nf = state.getValue(ruby.bamboo.block.CutBlock.FACING);
            if (of != nf) {
                Rotation delta = getRotationFromFacing(of, nf);
                if (delta != Rotation.NONE) {
                    rotate(delta);
                }
            }
        }
        super.setBlockState(state);
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
