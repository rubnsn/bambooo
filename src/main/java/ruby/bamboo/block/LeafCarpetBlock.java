package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 葉カーペット (桜/ヒノキ/モミジ/イチョウ)。
 * バニラ CarpetBlock (厚さ1/16) を継承し、見た目を葉テクスチャで敷き詰める薄い装飾ブロック。
 * 旧 sakuracarpet テクスチャを流用 (sakura, maple, ginkgo) + ヒノキは新規。
 */
public class LeafCarpetBlock extends CarpetBlock {

    protected static final VoxelShape SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public LeafCarpetBlock(MapColor color) {
        super(BlockBehaviour.Properties.of()
                .mapColor(color)
                .sound(SoundType.GRASS)
                .strength(0.2F)
                .noOcclusion()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false));
    }

    public LeafCarpetBlock() {
        this(MapColor.PLANT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
