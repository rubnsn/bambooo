package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooItems;

/**
 * 竹。旧 Bamboo (1.10.2) の移植。
 * <p>
 * 旧版は LENGTH (meta 0-15) を持ち、上へ伸びる・周囲にたけのこを増やす挙動だった。
 * 1.20.1では LENGTH → age(0-15) property として移植。
 * 下が竹なら維持、土/草/耕地の上にのみ設置可能。
 */
public class BambooBlock extends BushBlock implements BonemealableBlock {

    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 15);

    /** 旧 BLOCK_AABB (0.125,0,0.125)-(0.875,1,0.875) */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    private static final float GROW_PROBABILITY = 0.125F;
    private static final float GROW_PROBABILITY_RAIN = 0.25F;

    public BambooBlock(BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 10));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Vec3 off = state.getOffset(level, pos);
        return SHAPE.move(off.x, 0.0D, off.z);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Vec3 off = state.getOffset(level, pos);
        return SHAPE.move(off.x, 0.0D, off.z);
    }

    /**
     * 根本座標で束ねたXZ揺らぎ: 同一 x,z 柱の全段が同じ seed/offset を共有し、柱が折れず直立する。
     * バニラ BambooStalkBlock は Y を含むため段ごとにずれるが、本 mod の 1.10.2 由来の太い竹(12px)は
     * ずれが目立つため Y=0 固定で統一する。DoublePlantBlock#getSeed と同趣旨。
     */
    @Override
    public long getSeed(BlockState state, BlockPos pos) {
        return Mth.getSeed(pos.getX(), 0, pos.getZ());
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.FARMLAND);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // 自身の下 or 土/草/耕地の上 (旧 canBlockStay)
        return level.getBlockState(pos.below()).getBlock() == this || super.canSurvive(state, level, pos);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand) {
        tryGrow(level, pos, state, rand, level.isRainingAt(pos.above()) ? GROW_PROBABILITY_RAIN : GROW_PROBABILITY);
    }

    private void tryGrow(ServerLevel level, BlockPos pos, BlockState state, RandomSource rand, float probability) {
        if (!level.isEmptyBlock(pos.above())) {
            return;
        }
        if (rand.nextFloat() >= probability) {
            return;
        }
        int meta = state.getValue(AGE);
        if (meta > 0) {
            // 上へ1段成長、age を1減らす (旧 growBamboo)
            level.setBlock(pos.above(), state.setValue(AGE, meta - 1), 3);
        } else {
            // 地際 (age=0): 二重ゲート復元 (旧 tryBambooGrowth の rain||rand<prob)
            // 晴天時は確率二乗 (0.125*0.125)、雨天時は単ゲート
            if (!level.isRainingAt(pos.above()) && rand.nextFloat() >= probability) {
                return;
            }
            tryChildSpawn(level, pos, rand);
        }
    }

    /**
     * 地面まで下りて、その周囲1マス(27マス全走査)にたけのこを発生させる (旧 tryChildSpawn 完全復元)。
     * 旧: BlockPos.getAllInBox(p.add(-1,-1,-1), p.add(1,1,1)) で27マス全走査
     * 各マスで canChildSpawn の 0.1/0.25/0.4 独立ロール。
     */
    private void tryChildSpawn(ServerLevel level, BlockPos basePos, RandomSource rand) {
        // 竹柱を伝い地面の土系まで下降 (旧 canSustainBush 探索相当)
        BlockPos soilPos = basePos.below();
        while (soilPos.getY() >= level.getMinBuildHeight()) {
            BlockState soilState = level.getBlockState(soilPos);
            // 土/草/耕地なら到達
            if (soilState.is(Blocks.DIRT) || soilState.is(Blocks.GRASS_BLOCK) || soilState.is(Blocks.FARMLAND)
                    || super.mayPlaceOn(soilState, level, soilPos)) {
                break;
            }
            // 竹ならさらに下へ、竹でも土でもない場合も下へ探索継続
            if (soilState.is(this)) {
                soilPos = soilPos.below();
                continue;
            }
            // 竹柱が途中で途切れている等の異常系でも下へ
            soilPos = soilPos.below();
            // 無限ループ防止: 竹でも土でもないブロックが続く場合は打ち切り
            // ただし旧は canSustainBush が false の間ずっと下がるため同様に下がり続ける
            if (soilPos.getY() < level.getMinBuildHeight()) {
                return;
            }
            // 効率化: 32ブロック以上潜ったら打ち切り (旧は無制限だがワールド底まで行くと重い)
            if (basePos.getY() - soilPos.getY() > 32) {
                return;
            }
        }

        // 27マス全走査 (旧 getAllInBox 完全復元。乱数8回は廃止)
        BlockPos start = soilPos.offset(-1, -1, -1);
        BlockPos end = soilPos.offset(1, 1, 1);
        for (BlockPos shootPos : BlockPos.betweenClosed(start, end)) {
            if (!canChildSpawn(level, shootPos, rand)) {
                continue;
            }
            BlockPos dirtPos = shootPos.below();
            level.setBlock(dirtPos, Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(shootPos, BambooBlocks.BAMBOO_SHOOT.get().defaultBlockState(), 3);
        }
    }

    /**
     * 旧 canChildSpawn 復元: 空気 && たけのこ設置可能 && 雨/耕地で確率変動
     */
    private boolean canChildSpawn(ServerLevel level, BlockPos shootPos, RandomSource rand) {
        if (!level.isEmptyBlock(shootPos)) {
            return false;
        }
        BlockPos dirtPos = shootPos.below();
        BlockState below = level.getBlockState(dirtPos);
        // たけのこが設置可能か (旧 BambooBlocks.BAMBOOSHOOT.canBlockStay)
        if (!(below.is(Blocks.DIRT) || below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.FARMLAND))) {
            // super.mayPlaceOn も含める (BushBlock の土判定)
            if (!super.mayPlaceOn(below, level, dirtPos)) {
                return false;
            }
        }
        float threshold = level.isRainingAt(shootPos) ? 0.4F : below.is(Blocks.FARMLAND) ? 0.25F : 0.1F;
        return rand.nextFloat() < threshold;
    }

    // ===== D: 初期高さ抽選復元 (旧 onBlockAdded) =====

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide()) {
            BlockState below = level.getBlockState(pos.below());
            // 地面直上のみ 8+rand(5) に上書き (旧 canSustainBush 時)
            if (below.is(Blocks.DIRT) || below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.FARMLAND)
                    || super.mayPlaceOn(below, level, pos.below())) {
                // 竹の上に竹が積まれた場合は再抽選しない (旧同様)
                if (!below.is(this)) {
                    int newAge = 8 + level.random.nextInt(5);
                    if (state.getValue(AGE) != newAge) {
                        level.setBlock(pos, state.setValue(AGE, newAge), 3);
                    }
                }
            }
        }
    }

    // ===== E: 下段連鎖破壊復元 (旧 breakBlock) =====

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!state.is(newState.getBlock())) {
            BlockPos belowPos = pos.below();
            BlockState belowState = level.getBlockState(belowPos);
            if (belowState.is(this)) {
                // 直下1段をドロップして消す (旧 dropBlockAsItem + AIR)
                // bambooは BlockItem 無しのため asItem() は AIR になるので BambooItems.BAMBOO (素材) をドロップ
                popResource(level, belowPos, new net.minecraft.world.item.ItemStack(BambooItems.BAMBOO.get()));
                level.setBlock(belowPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    // ===== F: 骨粉対応復元 (旧 IGrowable) =====

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource rand, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource rand, BlockPos pos, BlockState state) {
        // 柱最上段を探索 (旧 grow)
        BlockPos topPos = pos;
        BlockPos next = topPos.above();
        while (!level.isEmptyBlock(next)) {
            BlockState nextState = level.getBlockState(next);
            if (!nextState.is(this)) {
                break;
            }
            topPos = next;
            next = topPos.above();
            if (topPos.getY() >= level.getMaxBuildHeight()) {
                break;
            }
        }
        BlockState topState = level.getBlockState(topPos);
        if (topState.is(this)) {
            this.tryGrow(level, topPos, topState, rand, 0.65F);
        }
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state != null && state.hasProperty(AGE)) {
            // 設置時のデフォルトは onPlace で 8+rand5 に上書きされるためここでは 10 のまま
            return state;
        }
        return state;
    }
}
