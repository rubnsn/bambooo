package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 篝火 (旧 crossLamp.0 の復刻。無機能の光源付きクロステクスチャ)。
 * <p>
 * 旧版は構想のみでコード・モデルなし。テクスチャ (kagaribi.png) だけが
 * バックアップに残っていたため、cross モデルの単純光源として起こす。
 * 当たり判定なし (すり抜け)、光レベル15。
 */
public class KagaribiBlock extends Block {

    public KagaribiBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // 松明準拠: 選択判定は残し衝突は noCollission に委譲 (すり抜け可・破壊可)
        return Block.box(4.0D, 0.0D, 4.0D, 12.0D, 12.0D, 12.0D);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        // 松明と同型 (TorchBlock:39-43)。篝火は炎が大きいため2箇所から出す
        double d0 = (double) pos.getX() + 0.5D;
        double d2 = (double) pos.getZ() + 0.5D;
        double d1 = (double) pos.getY() + 0.7;
        level.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
        level.addParticle(ParticleTypes.FLAME, d0, d1, d2, 0.0D, 0.0D, 0.0D);
        
    }
}
