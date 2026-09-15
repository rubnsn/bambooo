package ruby.bamboo.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * インベントリ所持で発動するアクセサリの目印 I/F (sakura Accessory の 1.20.1 移植)。
 * <p>
 * {@link ruby.bamboo.handler.AccessoryTickHandler} が毎 tick インベントリを走査し、
 * 該当品の {@link #playerPostTick} を呼ぶ。発動条件・サイド・間引きは各アイテム側で制御する。
 * 将来、専用スロット (Capability) を復活させる場合もこの I/F を流用する。
 */
public interface Accessory {
    default void playerPostTick(Player player, ItemStack stack) {
    }
}
