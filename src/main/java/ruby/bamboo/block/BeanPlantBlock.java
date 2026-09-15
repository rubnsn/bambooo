package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.BambooMod;

/**
 * 豆 (beanplant)。sakura-master / 旧1.10.2 の残骸資産
 * (textures/block/beanplant_stage_0-4 + textures/item/itembean) の復元実装。
 * <p>
 * 稲 ({@link RicePlantBlock}) と同じ AGE 0-4 の {@link CropBlock}。
 * 旧langに種の定義が無い (item.itembean のみ) ため、実アイテム
 * (豆自体) が種を兼ねる (バニラのニンジン/ジャガイモ方式)。
 * 耕地のみ設置可 (水田不可)。ドロップは loot_table に委譲。
 */
public class BeanPlantBlock extends CropBlock {

    // 注: CropBlock.codec() は vanilla で final のためオーバーライドしない
    // (移植済み RicePlantBlock と同様、CODEC 無しでコンパイル可能)。

    /** 種兼用アイテム (豆) の ID。親の BambooItems.BEAN 配線後に解決される。 */
    public static final ResourceLocation SEED_ID = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "bean");

    /** 稲と共通の段階別形状 (0,0,0)-(1,0.25,1) ベース) */
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(0, 0, 0, 16, 5, 16),
            Block.box(0, 0, 0, 16, 8, 16),
            Block.box(0, 0, 0, 16, 11, 16),
            Block.box(0, 0, 0, 16, 15, 16)
    };

    public BeanPlantBlock(BlockBehaviour.Properties props) {
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
        // ブロック自体のアイテム形態は豆 (種兼用・中クリック用の基礎)。
        // 未配線時は AIR 相当 (super) を返し、配線後に豆を返す。
        Item found = BuiltInRegistries.ITEM.get(SEED_ID);
        return found != null ? found : super.asItem();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        Item found = BuiltInRegistries.ITEM.get(SEED_ID);
        return new ItemStack(found != null ? found : Items.AIR);
    }
}
