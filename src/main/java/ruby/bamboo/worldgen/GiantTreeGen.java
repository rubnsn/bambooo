package ruby.bamboo.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 特殊巨木のプロシージャル生成器。
 * <p>
 * 幹・葉は固定せず引数 {@link BlockState} で受け取るため、サクラ・モミジ等の
 * 樹種から共用できる。原木は {@code AXIS} 持ち (RotatedPillarBlock系) を想定し、
 * 無ければ軸合わせを省略する。葉はバニラ {@code LeavesBlock} 系を想定し、
 * {@code DISTANCE} があれば 1 を付与して配置する (無ければ何もしない)。
 * <p>
 * ストラクチャ不使用。配置は {@code setBlock} 直置きのみ。
 * 樹形は一本桜の傘形 (中段から張り出す太枝+密生する花被覆) を狙う。
 */
public final class GiantTreeGen {

    private GiantTreeGen() {
    }

    /** 十字判定: 中央+東西南北4マスが同一苗木ブロックなら巨木対象。 */
    public static boolean isLargeTree(LevelReader level, BlockPos pos, BlockState sapling) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState check = level.getBlockState(pos.relative(dir));
            if (check == null || !check.is(sapling.getBlock())) {
                return false;
            }
        }
        return true;
    }

    /** 十字未満の隣接苗木があるか。あるだけでは育てず待機する。 */
    public static boolean hasNextSapling(LevelReader level, BlockPos pos, BlockState sapling) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState check = level.getBlockState(pos.relative(dir));
            if (check != null && check.is(sapling.getBlock())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 巨木を生成する。高さ15-19・傘の半径8前後。
     * 幹足元の十字5マスにある苗木は幹に上書き消費される。
     *
     * @return 生成成功時 true、空間不足等で失敗時 false (ブロックは変更しない)
     */
    public static boolean generate(ServerLevel level, BlockPos pos, RandomSource rand,
            BlockState log, BlockState leaves) {
        int h = 15 + rand.nextInt(5);
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() + h + 5 > level.getMaxBuildHeight()) {
            return false;
        }
        if (!hasSpace(level, pos, h)) {
            return false;
        }

        prepareSoil(level, pos);

        // 太幹: 下1/3は十字5マス、上は単柱
        int thickH = Math.max(4, h / 3);
        for (int y = 0; y < h; y++) {
            BlockPos p = pos.above(y);
            if (y <= thickH) {
                placeLog(level, p, log, Direction.Axis.Y);
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    placeLog(level, p.relative(dir), log, Direction.Axis.Y);
                }
            } else {
                placeLog(level, p, log, Direction.Axis.Y);
            }
        }
        // 根張り (非対称)
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (rand.nextBoolean()) {
                placeLog(level, pos.relative(dir, 2), log, Direction.Axis.Y);
            }
        }

        // 主枝: 4方向は確定で中段から長く張り出し、登りながら伸ばす。
        // 斜め4方向は半々。枝筋は葉付け用に記録する
        List<BlockPos> limbs = new ArrayList<>();
        List<BlockPos> tips = new ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            growLimb(level, pos, rand, log, limbs, tips,
                    dir.getStepX(), dir.getStepZ(),
                    h * 4 / 10 + rand.nextInt(3), 7 + rand.nextInt(3));
        }
        for (int i = 0; i < 4; i++) {
            if (rand.nextBoolean()) {
                continue;
            }
            int sx = (i & 1) == 0 ? 1 : -1;
            int sz = (i & 2) == 0 ? 1 : -1;
            growLimb(level, pos, rand, log, limbs, tips,
                    sx, sz, h * 4 / 10 + rand.nextInt(3), 5 + rand.nextInt(3));
        }
        // 頂部の上向き枝
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (rand.nextFloat() < 0.25F) {
                continue;
            }
            BlockPos p = pos.above(h - 3 + rand.nextInt(2));
            int len = 2 + rand.nextInt(2);
            Direction.Axis axis = dir.getAxis() == Direction.Axis.X ? Direction.Axis.X : Direction.Axis.Z;
            for (int j = 0; j < len; j++) {
                p = p.relative(dir);
                placeLog(level, p, log, axis);
                limbs.add(p);
            }
            for (int j = 0; j < 2; j++) {
                p = p.above();
                placeLog(level, p, log, Direction.Axis.Y);
            }
            tips.add(p);
        }

        // 葉その1: 枝筋に沿って密に付ける (枝が見え隠れする程度)
        for (BlockPos limb : limbs) {
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (Math.abs(x) + Math.abs(z) == 2 && rand.nextFloat() < 0.5F) {
                            continue;
                        }
                        if (rand.nextFloat() < 0.05F) {
                            continue;
                        }
                        placeLeaves(level, limb.offset(x, y, z), leaves);
                    }
                }
            }
        }

        // 葉その2: 傘の本体。上ほど狭く、下ほど広い重ね円盤で全体を覆う (半径8-12)
        BlockPos top = pos.above(h);
        placeDisc(level, top, -2, 11, 0.05F, rand, leaves);
        placeDisc(level, top, -1, 12, 0.04F, rand, leaves);
        placeDisc(level, top, 0, 11, 0.04F, rand, leaves);
        placeDisc(level, top, 1, 8, 0.05F, rand, leaves);
        placeDisc(level, top, 2, 5, 0.06F, rand, leaves);
        placeDisc(level, top, 3, 2, 0.06F, rand, leaves);
        placeLeaves(level, top.above(4), leaves);
        // 裾の垂れ下がり (外縁リングをまばらに下げて傘の縁を作る)
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                int d2 = x * x + z * z;
                if (d2 < 64 || d2 > 144) {
                    continue;
                }
                if (rand.nextFloat() < 0.35F) {
                    continue;
                }
                placeLeaves(level, top.offset(x, -3, z), leaves);
                if (rand.nextFloat() < 0.4F) {
                    placeLeaves(level, top.offset(x, -4, z), leaves);
                }
            }
        }
        // 葉その3: 傘の下層充填 (枝と傘をつなげて中抜けを防ぐ)
        BlockPos under = pos.above(h - 4);
        for (int x = -8; x <= 8; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -8; z <= 8; z++) {
                    double d = (double) (x * x + z * z) / 64.0 + (double) (y * y) / 9.0;
                    if (d > 1.0) {
                        continue;
                    }
                    if (rand.nextFloat() < 0.15F) {
                        continue;
                    }
                    placeLeaves(level, under.offset(x, y, z), leaves);
                }
            }
        }
        // 枝先の葉塊
        for (BlockPos tip : tips) {
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        if (x * x + y * y + z * z > 6) {
                            continue;
                        }
                        if (rand.nextFloat() < 0.08F) {
                            continue;
                        }
                        placeLeaves(level, tip.offset(x, y, z), leaves);
                    }
                }
            }
        }
        // 仕上げ: 原木からの距離で葉を検証し、届かない葉は除去・距離値を補正する。
        // 遠すぎる葉は腐朽対象になるため、接地時に確定させる
        validateLeaves(level, pos, h, log, leaves);
        return true;
    }

    /** 主枝を1本伸ばす。途中の側枝・先端も作り、枝筋と先端を記録する。 */
    private static void growLimb(ServerLevel level, BlockPos base, RandomSource rand, BlockState log,
            List<BlockPos> limbs, List<BlockPos> tips, int dx, int dz, int startY, int len) {
        Direction.Axis axis = dx != 0 ? Direction.Axis.X : Direction.Axis.Z;
        BlockPos p = base.above(startY);
        int riseEvery = 2 + rand.nextInt(2);
        for (int i = 0; i < len; i++) {
            p = p.offset(dx, 0, dz);
            if (i > 0 && i % riseEvery == 0) {
                p = p.above();
            }
            placeLog(level, p, log, axis);
            limbs.add(p);
            // 側枝 (半々): 直交方向へ2-3マス
            if (i >= 2 && rand.nextFloat() < 0.5F) {
                BlockPos q = p;
                int qx = dz != 0 ? (rand.nextBoolean() ? 1 : -1) : 0;
                int qz = dx != 0 ? (rand.nextBoolean() ? 1 : -1) : 0;
                Direction.Axis qaxis = qx != 0 ? Direction.Axis.X : Direction.Axis.Z;
                int qlen = 2 + rand.nextInt(2);
                for (int j = 0; j < qlen; j++) {
                    q = q.offset(qx, 0, qz);
                    placeLog(level, q, log, qaxis);
                    limbs.add(q);
                }
                q = q.above();
                placeLog(level, q, log, Direction.Axis.Y);
                tips.add(q);
            }
        }
        for (int i = 0; i < 2; i++) {
            p = p.above();
            placeLog(level, p, log, Direction.Axis.Y);
        }
        tips.add(p);
    }

    /** 水平円盤1層を葉で敷く。 */
    private static void placeDisc(ServerLevel level, BlockPos top, int dy, int r, float hole,
            RandomSource rand, BlockState leaves) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if ((double) (x * x + z * z) / (r * r) > 1.0) {
                    continue;
                }
                if (rand.nextFloat() < hole) {
                    continue;
                }
                placeLeaves(level, top.offset(x, dy, z), leaves);
            }
        }
    }

    /** 生成空間チェック。変更は行わない。苗木・葉・置換可能のみ許容。 */
    private static boolean hasSpace(ServerLevel level, BlockPos pos, int h) {
        int thickH = Math.max(4, h / 3);
        for (int y = 0; y <= h + 4; y++) {
            int r = y <= thickH ? 2 : 13;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockState s = level.getBlockState(pos.offset(x, y, z));
                    if (s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS) || s.isAir()) {
                        continue;
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 原木からの葉距離を確定させる。原木を起点に葉伝いで幅優先探索し、
     * 距離7以上 (腐朽対象) の葉は除去、到達した葉は DISTANCE を補正する。
     */
    private static void validateLeaves(ServerLevel level, BlockPos pos, int h,
            BlockState log, BlockState leaves) {
        int r = 13;
        int y0 = -1;
        int y1 = h + 5;
        Map<BlockPos, Integer> dist = new HashMap<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        for (int x = -r; x <= r; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = pos.offset(x, y, z);
                    if (level.getBlockState(p).is(log.getBlock())) {
                        dist.put(p, Integer.valueOf(0));
                        queue.add(p);
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int d = dist.get(p).intValue();
            if (d >= 6) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                BlockPos q = p.relative(dir);
                if (dist.containsKey(q)) {
                    continue;
                }
                if (Math.abs(q.getX() - pos.getX()) > r || Math.abs(q.getZ() - pos.getZ()) > r
                        || q.getY() < pos.getY() + y0 || q.getY() > pos.getY() + y1) {
                    continue;
                }
                if (level.getBlockState(q).is(leaves.getBlock())) {
                    dist.put(q, Integer.valueOf(d + 1));
                    queue.add(q);
                }
            }
        }
        boolean fixDistance = leaves.hasProperty(BlockStateProperties.DISTANCE);
        for (int x = -r; x <= r; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = pos.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.is(leaves.getBlock())) {
                        continue;
                    }
                    Integer d = dist.get(p);
                    if (d == null) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    } else if (fixDistance && s.getValue(BlockStateProperties.DISTANCE).intValue() != d.intValue()) {
                        level.setBlock(p, s.setValue(BlockStateProperties.DISTANCE, d), 2);
                    }
                }
            }
        }
    }

    /** 幹足元5マスの下が草ブロックなら土に戻す (バニラforce_dirt相当)。 */
    private static void prepareSoil(ServerLevel level, BlockPos pos) {
        setDirt(level, pos.below());
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            setDirt(level, pos.relative(dir).below());
        }
    }

    private static void setDirt(ServerLevel level, BlockPos p) {
        if (level.getBlockState(p).is(Blocks.GRASS_BLOCK)) {
            level.setBlock(p, Blocks.DIRT.defaultBlockState(), 3);
        }
    }

    private static void placeLog(ServerLevel level, BlockPos p, BlockState log, Direction.Axis axis) {
        BlockState current = level.getBlockState(p);
        if (!current.canBeReplaced() && !current.is(BlockTags.LEAVES)
                && !current.is(BlockTags.SAPLINGS) && !current.isAir()) {
            return;
        }
        BlockState s = log;
        if (s.hasProperty(BlockStateProperties.AXIS)) {
            s = s.setValue(BlockStateProperties.AXIS, axis);
        }
        level.setBlock(p, s, 2);
    }

    private static void placeLeaves(ServerLevel level, BlockPos p, BlockState leaves) {
        if (!level.getBlockState(p).isAir()) {
            return;
        }
        BlockState s = leaves;
        if (s.hasProperty(BlockStateProperties.DISTANCE)) {
            s = s.setValue(BlockStateProperties.DISTANCE, Integer.valueOf(1));
        }
        if (s.hasProperty(BlockStateProperties.PERSISTENT)) {
            s = s.setValue(BlockStateProperties.PERSISTENT, Boolean.valueOf(false));
        }
        level.setBlock(p, s, 3);
    }
}
