package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.PlantType;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * 田んぼブロック (sakura PaddyField の移植)。
 * <p>
 * FarmBlock継承 + WATERLOGGED追加。PropertiesはMapColor.DIRT, hardness 0.6, tickRandomly。
 * moisture==7 で加速: 水没時はRICE_PLANTのみ、非水没時は汎用Crop(IPlantable PlantType.Crop)かつRICE_PLANT以外を75%確率でupperState.tick/randomTick加速。
 */
public class PaddyFieldBlock extends FarmBlock implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public PaddyFieldBlock(BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(MOISTURE, Integer.valueOf(0))
                .setValue(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState superState = super.getStateForPlacement(context);
        if (superState == null) {
            superState = this.defaultBlockState();
        }
        // sakura: setHorizontalWater
        return setHorizontalWater(superState, context.getLevel(), context.getClickedPos());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (oldState.getBlock() != this) {
            level.setBlock(pos, setHorizontalWater(state, level, pos), 3);
        }
    }

    public BlockState setHorizontalWater(BlockState state, Level level, BlockPos pos) {
        var fluidState = level.getFluidState(pos);
        var fluid = fluidState.getType();
        if (fluid == Fluids.EMPTY || fluid == Fluids.FLOWING_WATER) {
            // 水平隣接の同ブロックから水を探索 (sakura Streams相当をループで再現)
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos npos = pos.relative(d);
                if (level.getBlockState(npos).getBlock() == this) {
                    var nFluid = level.getFluidState(npos).getType();
                    if (nFluid == Fluids.WATER) {
                        fluid = Fluids.WATER;
                        break;
                    }
                }
            }
            if (fluid != Fluids.WATER) {
                fluid = Fluids.EMPTY;
            }
        }
        boolean waterlogged = fluid == Fluids.WATER;
        return state.setValue(WATERLOGGED, waterlogged).setValue(MOISTURE, waterlogged ? 7 : 0);
    }

    // ===== 成長加速 (sakura tick相当) =====

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        tryAccelerate(state, level, pos, random);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        tryAccelerate(state, level, pos, random);
    }

    private void tryAccelerate(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int moisture = state.getValue(MOISTURE);
        if (moisture != 7) {
            return;
        }
        boolean waterlogged = state.getValue(WATERLOGGED);
        BlockPos upperPos = pos.above();
        BlockState upperState = level.getBlockState(upperPos);
        net.minecraft.world.level.block.Block upperBlock = upperState.getBlock();
        if (!waterlogged) {
            if (upperBlock instanceof IPlantable plantable) {
                PlantType type = plantable.getPlantType(level, upperPos);
                // とりあえずハードコード: RICE_PLANT以外でCropのみ加速 (sakuraコメント準拠)
                if (type == PlantType.CROP && upperBlock != BambooBlocks.RICE_PLANT.get()) {
                    if (random.nextFloat() < 0.75F) {
                        accelerateUpper(upperState, level, upperPos, random);
                    }
                }
            }
        } else {
            if (upperBlock == BambooBlocks.RICE_PLANT.get()) {
                if (random.nextFloat() < 0.75F) {
                    accelerateUpper(upperState, level, upperPos, random);
                }
            }
        }
    }

    private void accelerateUpper(BlockState upperState, ServerLevel level, BlockPos upperPos, RandomSource random) {
        // sakuraは upperState.tick(...) を呼んでいたが、1.20では作物はrandomTickで成長するため両方対応
        var block = upperState.getBlock();
        if (upperState.isRandomlyTicking()) {
            block.randomTick(upperState, level, upperPos, random);
        } else {
            // fallback: tick (FarmBlock由来のtick等)
            upperState.tick(level, upperPos, random);
        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (!state.getValue(WATERLOGGED)) {
            super.fallOn(level, state, pos, entity, fallDistance);
        }
        // 水没時はトランプル無効 (sakura onFallenUpon準拠)
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            BlockState fromState = level.getBlockState(fromPos);
            if (fromState.getBlock() == this && isHorizontalPos(pos, fromPos)) {
                if (state.getValue(WATERLOGGED) != fromState.getValue(WATERLOGGED)) {
                    level.setBlock(pos, state.setValue(WATERLOGGED, fromState.getValue(WATERLOGGED)), 3);
                }
            }
        }
    }

    private boolean isHorizontalPos(BlockPos pos, BlockPos fromPos) {
        if (pos.getY() == fromPos.getY()) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (pos.relative(d).equals(fromPos)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canSustainPlant(BlockState state, BlockGetter world, BlockPos pos, Direction facing, IPlantable plantable) {
        PlantType type = plantable.getPlantType(world, pos.relative(facing));
        return type == PlantType.CROP;
    }
}
