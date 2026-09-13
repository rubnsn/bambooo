package ruby.bamboo.core.wish;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import ruby.bamboo.core.init.BambooCapabilities;

/**
 * 願いカウント操作の単一入口 (サーバー側)。
 * 願いの杖由来の願いはカウント対象外のため、呼び出し側で fromWand を判定すること。
 */
public final class WishHelper {

    private WishHelper() {
    }

    public static LazyOptional<WishStorage> get(Player player) {
        return player.getCapability(BambooCapabilities.WISH);
    }

    public static int getCount(Player player) {
        return get(player).map(WishStorage::getCount).orElse(0);
    }

    public static void increment(Player player) {
        get(player).ifPresent(WishStorage::increment);
    }

    /** 1減少(0止まり)。減少したら true。 */
    public static boolean decrement(Player player) {
        return get(player).map(WishStorage::decrement).orElse(false);
    }
}
