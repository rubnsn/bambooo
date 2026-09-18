package ruby.bamboo.item;

import net.minecraft.world.item.Item;

/**
 * ガチャコイン (docs/port-spec-gacha.md)。
 * <p>
 * ガチャポン専用の単発トークン。1枚で1回遊べる。レシピなし
 * (クリエイティブタブから取得)。残高Cap等の所持状態は持たない。
 */
public class GachaCoinItem extends Item {

    public GachaCoinItem(Properties props) {
        super(props);
    }
}
