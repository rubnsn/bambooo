package ruby.bamboo.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import ruby.bamboo.entity.KaginawaHookEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバ/クライアント共通の鈎縄フック追跡。
 * 単一フック仕様: プレイヤー1人につき1フック。
 */
public final class KaginawaStateManager {

    private static final Map<UUID, KaginawaHookEntity> HOOKS = new ConcurrentHashMap<>();

    private KaginawaStateManager() {
    }

    public static void put(Player player, KaginawaHookEntity hook) {
        HOOKS.put(player.getUUID(), hook);
    }

    public static KaginawaHookEntity getHook(Player player) {
        return HOOKS.get(player.getUUID());
    }

    public static boolean hasHook(Player player) {
        KaginawaHookEntity h = HOOKS.get(player.getUUID());
        return h != null && !h.isRemoved() && h.isAlive();
    }

    public static void remove(Player player) {
        HOOKS.remove(player.getUUID());
    }

    public static void remove(UUID uuid) {
        HOOKS.remove(uuid);
    }

    public static void removeHook(KaginawaHookEntity hook) {
        if (hook == null) {
            return;
        }
        // UUIDで線形探索して一致するエントリを削除 (ownerが変わるケースは無いが安全)
        HOOKS.entrySet().removeIf(e -> e.getValue() == hook);
    }

    /** クライアント側でも呼ばれるため ServerPlayer 限定にしない */
    public static void clearIfDead() {
        HOOKS.entrySet().removeIf(e -> e.getValue().isRemoved() || !e.getValue().isAlive());
    }
}
