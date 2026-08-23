package ruby.bamboo.item;

import net.minecraft.world.item.Item;

/**
 * 共通食料アイテム。旧 BambooFood.Food enum の各値を
 * 1.20.1 FoodProperties として個別登録するためのシンプル Item。
 * 食べる時間は 1.20.1 固定 32tick。
 */
public class BambooFoodItem extends Item {

    public BambooFoodItem(Properties props) {
        super(props);
    }
}
