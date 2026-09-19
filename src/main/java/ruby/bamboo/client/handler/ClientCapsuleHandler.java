package ruby.bamboo.client.handler;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * クライアント側 カプセル解放状態の保持 (HPバー描画用)。
 * HP自体はバニラ同期 (LivingEntity#getHealth) を読み、ここでは
 * 解放有無・maxHp・被弾表示タイマーの推定値のみ持つ。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientCapsuleHandler {

    private ClientCapsuleHandler() {
    }

    public static final class Entry {
        public final float maxHp;
        public float lastHp;
        /** 被弾を見た時刻 (gameTime)。HP半分未満は常時表示のため参照されない */
        public long lastHurtTime = -1000L;

        Entry(float maxHp) {
            this.maxHp = maxHp;
            this.lastHp = maxHp;
        }
    }

    private static final Map<Integer, Entry> STATES = new HashMap<>();

    public static void handleState(int entityId, boolean released, float maxHp) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (released) {
                STATES.put(entityId, new Entry(maxHp > 0.0F ? maxHp : 20.0F));
            } else {
                STATES.remove(entityId);
            }
        });
    }

    public static Map<Integer, Entry> states() {
        return STATES;
    }
}
