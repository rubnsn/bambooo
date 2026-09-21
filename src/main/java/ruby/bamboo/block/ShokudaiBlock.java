package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 燭台 (旧 crossLamp.1 の復刻。無機能の光源付きクロステクスチャ)。
 * <p>
 * 松明準拠: 選択判定あり・衝突なし (すり抜け可・破壊可)、下支え喪失で落下、
 * 先端から煙+炎パーティクル。光レベル14 (松明と同値)。
 */
public class ShokudaiBlock extends Block {

    protected static final VoxelShape AABB = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);

    public ShokudaiBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return AABB;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !this.canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        double d0 = (double) pos.getX() + 0.5D;
        double d1 = (double) pos.getY() + 1D;
        double d2 = (double) pos.getZ() + 0.5D;
        level.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
        level.addParticle(ParticleTypes.FLAME, d0, d1, d2, 0.0D, 0.0D, 0.0D);
    }
}
