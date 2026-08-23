package ruby.bamboo.item;

import net.minecraft.world.item.Item;

/**
 * 通帳 (旧 Tudura の移植)。
 * <p>
 * 機能を持たない素材アイテム。袋 (Sack) のクラフト材料として使用する。
 * 旧版は Item 直継承でロジック無しのため、1.20.1 でも単なる Item。
 */
public class Tudura extends Item {

    public Tudura(Properties properties) {
        super(properties);
    }
}
