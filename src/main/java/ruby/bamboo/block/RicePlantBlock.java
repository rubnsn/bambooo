package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.core.init.BambooItems;

/**
 * 稲。旧 RicePlant (1.10.2, GrowableBase継承) の移植。
 * <p>
 * 旧仕様 (docs/port-spec-rice-indlight.md 参照):
 * - AGE 0-4、耕地のみ設置可
 * - 破壊時: age==max → わら(product) + 種1個保証 + 追加で (age/max)/2F 確率×(3+fortune)回の種
 * - 破壊時: 未完熟 → 種1個のみ
 * - 中クリック(getCloneItemStack) → 常に稲の種
 */
public class RicePlantBlock extends CropBlock {

    /** 旧 BLOCK_AABB (0,0,0)-(1,0.25,1) ベースの段階別形状 */
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(0, 0, 0, 16, 5, 16),
            Block.box(0, 0, 0, 16, 8, 16),
            Block.box(0, 0, 0, 16, 11, 16),
            Block.box(0, 0, 0, 16, 15, 16)
    };

    public RicePlantBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public int getMaxAge() {
        return 4;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(this.getAgeProperty())];
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(net.minecraft.world.level.block.Blocks.FARMLAND)
                || state.getBlock() instanceof PaddyFieldBlock
                || state.canSustainPlant(level, pos, Direction.UP, this);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return this.mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }

    // ===== ドロップ仕様: loot_tables/blocks/rice_plant.json に委譲 (旧 GrowableBase#getDrops/getItemDropped 相当) =====
    // 旧コードの getDrops オーバーライドは loot_table をバイパスし二重管理になるため削除。
    // 現行は CropBlock 標準の BlockLoot に従い、rice_plant.json で以下を再現:
    // - 完熟: straw 1 + riceseed 1保証 + 追加 riceseed 0-3 (各0.5)
    // - 未完熟: riceseed 1

    @Override
    public Item asItem() {
        // ブロック自体のアイテム形態は稲の種 (旧 getSeed 相当・中クリック用の基礎)
        return BambooItems.RICE_SEED.get();
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        // 旧 getPickBlock: 常に種を返す (中クリック対策)
        return new ItemStack(BambooItems.RICE_SEED.get());
    }
}
