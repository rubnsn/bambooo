package ruby.bamboo.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import ruby.bamboo.block.entity.MiniatureBlockEntity;

/**
 * ミニチュア用 BlockItem — 名称にサイズを表記する。
 * <p>
 * 単一登録名 `miniature` に対して NBT `Size` (4,8,12,16) で 4種をクリエタブに並べるため、
 * 通常の翻訳 `block.bamboomod.miniature` だけでは区別が付かない。hover 名にサイズを付記する。
 */
public class MiniatureItem extends BlockItem {

    public MiniatureItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public Component getName(ItemStack stack) {
        Component base = super.getName(stack);
        int size = MiniatureBlockEntity.getSizeFromStack(stack);
        // サイズが既定(8)以外でも常に表記して明確化
        // 例: 「ミニチュア [8×8×8]」 / 「Miniature [8×8×8]」
        return Component.literal(base.getString() + " [" + size + "×" + size + "×" + size + "]");
    }
}
