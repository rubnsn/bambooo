package ruby.bamboo.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * ホタル瓶アイテム (捕獲ホタル入り。右クリックで瓶ブロックを設置)。
 */
public class FireflyBottleItem extends BlockItem {

    public FireflyBottleItem(Block block, Item.Properties props) {
        super(block, props);
    }
}
