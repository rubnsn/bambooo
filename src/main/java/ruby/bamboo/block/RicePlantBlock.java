package ruby.bamboo.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
        return state.is(net.minecraft.world.level.block.Blocks.FARMLAND);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return this.mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }

    // ===== ドロップ仕様 (旧 GrowableBase#getDrops/getItemDropped 相当) =====

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

    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        List<ItemStack> drops = new java.util.ArrayList<>();
        int age = state.getValue(this.getAgeProperty());
        RandomSource rand = builder.getLevel().getRandom();

        if (age >= this.getMaxAge()) {
            // 完熟: わら (旧 getProduct)
            drops.add(new ItemStack(BambooItems.STRAW.get()));
            // 種1個保証 (旧 RicePlant#extraDrop)
            drops.add(new ItemStack(BambooItems.RICE_SEED.get()));
            // 追加種: (age/max)/2F = 0.5 確率 x (3+fortune) 回 (旧 getDrops ループ)
            // fortune は旧版が 0 強制だったため 0 扱いで 3 回
            for (int i = 0; i < 3; ++i) {
                if (rand.nextFloat() <= 0.5F) {
                    drops.add(new ItemStack(BambooItems.RICE_SEED.get()));
                }
            }
        } else {
            // 未完熟: 種1個のみ (旧 getItemDropped + quantityDropped=1)
            drops.add(new ItemStack(BambooItems.RICE_SEED.get()));
        }
        return drops;
    }
}
