package ruby.bamboo.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.block.entity.WallShelfBlockEntity;

/**
 * 壁棚 (sakura-master WallShelf 移植)。
 * <p>
 * 旧仕様: ContainerBlock + HORIZONTAL_FACING + 薄板VoxelShape + 2slot TileEntity(LEFT/RIGHT) + BERでアイテム描画。
 * hit位置で左右スロットを判定し、空なら手持ちから1個格納、あれば取出してpopする。壁不要(飾り)。
 */
public class WallShelfBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final VoxelShape AABB_EAST = Block.box(8.0D, 5.0D, 0.0D, 16.0D, 6.0D, 16.0D);
    public static final VoxelShape AABB_WEST = Block.box(0.0D, 5.0D, 0.0D, 8.0D, 6.0D, 16.0D);
    public static final VoxelShape AABB_NORTH = Block.box(0.0D, 5.0D, 0.0D, 16.0D, 6.0D, 8.0D);
    public static final VoxelShape AABB_SOUTH = Block.box(0.0D, 5.0D, 8.0D, 16.0D, 6.0D, 16.0D);

    public WallShelfBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(1.5F)
                .noOcclusion()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction dir = state.getValue(FACING);
        return switch (dir) {
            case EAST -> AABB_EAST;
            case WEST -> AABB_WEST;
            case SOUTH -> AABB_SOUTH;
            case NORTH -> AABB_NORTH;
            default -> AABB_NORTH;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof WallShelfBlockEntity shelf)) {
            return InteractionResult.PASS;
        }
        // 旧 WallShelf.onBlockActivated と同等の hit位置→LEFT/RIGHT判定を移植
        Vec3 hitVec = hit.getLocation();
        double hitX = Math.abs(hitVec.x - pos.getX());
        double hitZ = Math.abs(hitVec.z - pos.getZ());
        Direction dir = state.getValue(FACING);
        int slot;
        if (hitX < 0.5) {
            if (hitZ < 0.5) {
                // left up
                slot = (dir == Direction.NORTH) ? WallShelfBlockEntity.SLOT_LEFT : WallShelfBlockEntity.SLOT_RIGHT;
            } else {
                // left down
                slot = (dir == Direction.SOUTH) ? WallShelfBlockEntity.SLOT_RIGHT : WallShelfBlockEntity.SLOT_LEFT;
            }
        } else {
            if (hitZ < 0.5) {
                // right up
                slot = (dir == Direction.NORTH) ? WallShelfBlockEntity.SLOT_RIGHT : WallShelfBlockEntity.SLOT_LEFT;
            } else {
                // right down
                slot = (dir == Direction.SOUTH) ? WallShelfBlockEntity.SLOT_LEFT : WallShelfBlockEntity.SLOT_RIGHT;
            }
        }
        boolean isCreative = player.isCreative();
        boolean updated;
        if (!shelf.getItem(slot).isEmpty()) {
            // 取出
            net.minecraft.world.item.ItemStack removed = shelf.removeItemNoUpdate(slot);
            // setChanged は removeItemNoUpdateでは呼ばれないため明示
            shelf.setChanged();
            if (!removed.isEmpty()) {
                if (!isCreative) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), removed);
                }
            }
            updated = true;
            // 取出は空でも成功扱い (旧は removeStackFromSlot が常に返す)
        } else {
            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
            if (!held.isEmpty()) {
                if (isCreative) {
                    net.minecraft.world.item.ItemStack copy = held.copy();
                    copy.setCount(1);
                    shelf.setItem(slot, copy);
                } else {
                    // split(1)相当: 1個だけ移動
                    net.minecraft.world.item.ItemStack one = held.split(1);
                    shelf.setItem(slot, one);
                }
                updated = true;
            } else {
                updated = false;
            }
        }
        if (updated) {
            // 旧 notifyBlockUpdate + notifyNeighbors 相当
            level.sendBlockUpdated(pos, state, state, 3);
            level.updateNeighborsAt(pos, this);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof WallShelfBlockEntity shelf) {
                Containers.dropContents(level, pos, shelf);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WallShelfBlockEntity(pos, state);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }
}
