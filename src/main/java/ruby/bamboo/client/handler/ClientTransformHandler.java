package ruby.bamboo.client.handler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * クライアント側の変身状態キャッシュ。本人分は Cap ではなくこの Map を正とする
 * (サーバーが唯一の真実であり、ログイン・追跡時に配布されるため)。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientTransformHandler {

    private static final Map<UUID, String> STATES = new ConcurrentHashMap<>();

    private ClientTransformHandler() {
    }

    public static void handleSync(UUID playerId, String entityId) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (entityId == null || entityId.isEmpty()) {
                STATES.remove(playerId);
            } else {
                STATES.put(playerId, entityId);
                if (STATES.size() > 200) {
                    STATES.clear();
                    STATES.put(playerId, entityId);
                }
            }
        });
    }

    public static String get(UUID playerId) {
        String s = STATES.get(playerId);
        return s != null ? s : "";
    }

    public static void clear() {
        STATES.clear();
    }
}
