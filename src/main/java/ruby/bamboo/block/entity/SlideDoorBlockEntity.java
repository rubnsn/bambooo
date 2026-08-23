package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.core.Direction;
import ruby.bamboo.block.SlideDoorBlock;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * 引き戸の TileEntity — sakura-master `SlideDoorTileEntity` の 1.20.1 移植。
 * <p>
 * 旧 sakura は {@code ITickableTileEntity} で {@code posX/posZ, nowPosX/nowPosZ} を毎tick更新し、
 * {@code FACING.rotateYCCW()} + {@code HINGE} でスライド方向を決定、{@code MOVED} を立てていた。
 * 1.20.1 では {@link BlockEntity} + ticker 方式に移行。描画側で {@code Mth.lerp(partialTick, prev, pos)} する。
 */
public class SlideDoorBlockEntity extends BlockEntity {

    // 描画オフセット (ブロック1つ分 = 1.0)
    public float posX;
    public float posZ;
    public float prevPosX;
    public float prevPosZ;

    public SlideDoorBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.SLIDE_DOOR_BE.get(), pos, state);
    }

    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T be) {
        if (be instanceof SlideDoorBlockEntity slide) {
            slide.tick(level, pos, state);
        }
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        // 上半分は下半分の動きに追従させることで上下ズレを解消
        if (state.getValue(SlideDoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            BlockEntity below = level.getBlockEntity(pos.below());
            if (below instanceof SlideDoorBlockEntity belowSlide) {
                this.prevPosX = belowSlide.prevPosX;
                this.prevPosZ = belowSlide.prevPosZ;
                this.posX = belowSlide.posX;
                this.posZ = belowSlide.posZ;
                return;
            }
        }
        this.prevPosX = this.posX;
        this.prevPosZ = this.posZ;

        Direction facing = state.getValue(SlideDoorBlock.FACING).getCounterClockWise();
        if (state.getValue(SlideDoorBlock.HINGE) == DoorHingeSide.RIGHT) {
            facing = facing.getOpposite();
        }
        boolean isOpen = state.getValue(SlideDoorBlock.OPEN);
        if (isOpen) {
            this.posX = facing.getStepX();
            this.posZ = facing.getStepZ();
            if ((this.prevPosX != this.posX || this.prevPosZ != this.posZ) && !level.isClientSide && !state.getValue(SlideDoorBlock.MOVED)) {
                level.setBlock(pos, state.setValue(SlideDoorBlock.MOVED, true), 2);
                // 上半分も同時にMOVEDを立てる
                BlockPos upperPos = pos.above();
                BlockState upperState = level.getBlockState(upperPos);
                if (upperState.is(state.getBlock()) && !upperState.getValue(SlideDoorBlock.MOVED)) {
                    level.setBlock(upperPos, upperState.setValue(SlideDoorBlock.MOVED, true), 2);
                }
            }
        } else {
            this.posX = 0;
            this.posZ = 0;
            if (this.prevPosX == 0 && this.prevPosZ == 0 && !level.isClientSide && state.getValue(SlideDoorBlock.MOVED)) {
                level.setBlock(pos, state.setValue(SlideDoorBlock.MOVED, false), 2);
                BlockPos upperPos = pos.above();
                BlockState upperState = level.getBlockState(upperPos);
                if (upperState.is(state.getBlock()) && upperState.getValue(SlideDoorBlock.MOVED)) {
                    level.setBlock(upperPos, upperState.setValue(SlideDoorBlock.MOVED, false), 2);
                }
            }
        }
    }
}
