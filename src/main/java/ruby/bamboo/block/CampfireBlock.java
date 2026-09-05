package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import ruby.bamboo.block.entity.CampfireBlockEntity;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * 囲炉裏 (旧 Campfire の移植)。
 * <p>
 * 旧仕様: Material.GROUND / 硬度1 / 耐爆300 / FACING(6方向→水平4方向に簡略化) /
 * 非不透明・非フルキューブ / TESR描画 / ピストン移動不可。
 * <p>
 * 描画は {@link RenderShape#INVISIBLE} + BER
 * ({@link ruby.bamboo.block.entity.CampfireBlockRenderer}) で行う。
 */
public class CampfireBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<CampfireBlock> CODEC =
            com.mojang.serialization.MapCodec.unit(CampfireBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** 向き (旧 FACING の水平4方向簡略化)。BER回転用 */
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);

    public CampfireBlock() {
        super(Properties.of()
                .mapColor(MapColor.NONE)
                .sound(SoundType.WOOD)
                // 旧 hardness=1 / resistance=300
                .strength(1.0f, 300.0f)
                .noOcclusion()
                // 旧 getMobilityFlag=IGNORE (ピストン移動不可)
                .pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 旧 onBlockPlacedBy: プレイヤー逆向き (水平)
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // ===== GUI オープン (旧 onBlockActivated 相当) =====

    @Override
    public net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire) {
            player.openMenu(campfire);
        }
        return InteractionResult.CONSUME;
    }

    // ===== パーティクル (旧 randomDisplayTick 相当) =====

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // 10% で FLAME パーティクル (x+0.5±0.2, y+0.2, z+0.5±0.2)
        if (random.nextFloat() < 0.1F) {
            double x = pos.getX() + 0.5 + (random.nextFloat() * 0.4F - 0.2F);
            double y = pos.getY() + 0.2;
            double z = pos.getZ() + 0.5 + (random.nextFloat() * 0.4F - 0.2F);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    // ===== BlockEntity =====

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // TESR相当: ブロックモデルは描画せずBERで描く
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CampfireBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, BambooBlockEntities.CAMPFIRE_BE.get(), CampfireBlockEntity::tick);
    }

    /** 破壊時に中身をドロップする (旧 breakBlock 相当) */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire) {
                campfire.dropContents(level, pos);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}