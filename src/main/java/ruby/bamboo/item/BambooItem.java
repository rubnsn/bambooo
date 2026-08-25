package ruby.bamboo.item;

import net.minecraft.world.item.Item;

/**
 * 竹アイテム (旧 1.10.2 の bamboo アイテム相当)。
 * <p>
 * ワールド上の竹ブロック ({@link ruby.bamboo.block.BambooBlock}) を壊すと
 * 本アイテムがドロップされる。無機能アイテムであり、直接は植えられない
 * (植え付けはたけのこ {@code bamboo_shoot} が担う)。
 * <p>
 * バニラにおける「小麦アイテム vs 麦ブロック」の関係と同様、
 * ブロックとは独立した存在。クラフト素材として各レシピで使用する。
 */
public class BambooItem extends Item {

    public BambooItem(Properties properties) {
        super(properties);
    }
}
