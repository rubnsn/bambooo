package ruby.bamboo.core.wish;

import net.minecraft.world.entity.player.Player;
import ruby.bamboo.core.init.BambooCapabilities;

/**
 * 願いカウント操作の単一入口 (サーバー側)。
 * 願いの杖由来の願いはカウント対象外のため、呼び出し側で fromWand を判定すること。
 * <p>
 * 1.21.1 NeoForge: 旧 LazyOptional + getCapability を Player Attachment
 * ({@code BambooCapabilities.WISH}) の getData/setData に置換。
 */
public final class WishHelper {

    private WishHelper() {
    }

    public static WishStorage get(Player player) {
        return player.getData(BambooCapabilities.WISH.get());
    }

    public static int getCount(Player player) {
        try {
            return get(player).getCount();
        } catch (Exception e) {
            return 0;
        }
    }

    public static void increment(Player player) {
        try {
            WishStorage storage = get(player);
            storage.increment();
            player.setData(BambooCapabilities.WISH.get(), storage);
        } catch (Exception ignored) {
        }
    }

    /** 1減少 (0止まり)。減少したら true。 */
    public static boolean decrement(Player player) {
        try {
            WishStorage storage = get(player);
            boolean changed = storage.decrement();
            if (changed) {
                player.setData(BambooCapabilities.WISH.get(), storage);
            }
            return changed;
        } catch (Exception e) {
            return false;
        }
    }
}
