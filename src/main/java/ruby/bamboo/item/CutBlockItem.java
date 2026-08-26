package ruby.bamboo.item;

import java.util.function.Consumer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import ruby.bamboo.block.entity.CutBlockEntity;
import ruby.bamboo.client.renderer.CutBlockItemRenderer;

/**
 * カットブロックの BlockItem。
 * 表示名を "木材 (16×8)" のように動的に生成。同一サイズはスタック可。
 * インベントリでは BEWLR で原料ブロックのテクスチャを Bounds にクリップして描画する。
 */
public class CutBlockItem extends BlockItem {

    public CutBlockItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CutBlockItemRenderer.getInstance();
            }
        });
    }

    @Override
    public Component getName(ItemStack stack) {
        CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
        if (data.state().isAir()) {
            return super.getName(stack);
        }
        String baseName = data.state().getBlock().getName().getString();
        int hSize = CutBlockEntity.levelToSize(data.hLevel());
        int ySize = CutBlockEntity.levelToSize(data.yLevel());
        // フルならサイズ省略
        if (hSize == 16 && ySize == 16) {
            return Component.literal(baseName);
        }
        return Component.literal(baseName + " (" + hSize + "×" + ySize + ")");
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        // 表示名はgetNameで上書きするので、IDは通常通り
        return super.getDescriptionId(stack);
    }
}
