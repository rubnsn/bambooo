package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.core.init.BambooItems;

/**
 * トマト。sakura-master / 旧1.10.2 の残骸資産
 * (textures/block/tomato_stage_0-4 + textures/item/itemtomato) の復元実装。
 * <p>
 * 稲 ({@link RicePlantBlock}) と同じ AGE 0-4 の {@link CropBlock}。
 * 旧langに種の定義が無い (item.itemtomato のみ) ため、実アイテム
 * (トマト自体) が種を兼ねる (バニラのニンジン/ジャガイモ方式)。
 * 耕地のみ設置可 (水田不可)。ドロップは loot_table に委譲。
 */
public class TomatoPlantBlock extends CropBlock {

    /** 稲と共通の段階別形状 (0,0,0)-(1,0.25,1) ベース) */
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(0, 0, 0, 16, 5, 16),
            Block.box(0, 0, 0, 16, 8, 16),
            Block.box(0, 0, 0, 16, 11, 16),
            Block.box(0, 0, 0, 16, 15, 16)
    };

    public TomatoPlantBlock(BlockBehaviour.Properties props) {
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
    public Item asItem() {
        // ブロック自体のアイテム形態はトマト (種兼用・中クリック用の基礎)
        return BambooItems.TOMATO.get();
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(BambooItems.TOMATO.get());
    }
}
