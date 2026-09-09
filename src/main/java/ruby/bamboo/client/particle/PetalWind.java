package ruby.bamboo.client.particle;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 花びら用の環境風 (ローカルのみ、サーバー同期なし)。
 * <p>
 * 意図的にクライアント専用クラスを参照しない (import は common のみ) ため、
 * 葉ブロックの {@code animateTick} (common) から呼んでも専用サーバーで安全。
 * 時刻基準の決定的な風なので同期パケットは不要。
 * <ul>
 * <li>風向・風速は {@code getGameTime} の sin 合成で緩やかに変化</li>
 * <li>約60秒ごとに約6秒の突風 ({@code gustFactor} 0→1→0)</li>
 * </ul>
 */
public final class PetalWind {

    /** 突風サイクル全体 (tick)。約60秒ごとに1回 */
    private static final long CYCLE = 1200;
    /** 突風開始オフセット */
    private static final long GUST_START = 800;
    /** 突風継続 (tick)。約6秒 */
    private static final long GUST_LEN = 120;

    private PetalWind() {
    }

    /** 突風係数 0.0-1.0。通常時は 0、突風のピークで 1 */
    public static float gustFactor(Level level) {
        long m = level.getGameTime() % CYCLE;
        if (m < GUST_START || m > GUST_START + GUST_LEN) {
            return 0.0F;
        }
        double ph = (double) (m - GUST_START) / (double) GUST_LEN;
        return (float) Math.sin(ph * Math.PI);
    }

    /** 現在の環境風ベクトル (水平のみ、y=0)。目安: 通常 0.005-0.02、突風 +0.035 */
    public static Vec3 getWind(Level level) {
        long t = level.getGameTime();
        double angle = t * 0.002 + Math.sin(t * 0.0007) * 1.5;
        double speed = 0.010 + 0.006 * Math.sin(t * 0.0011) + 0.004 * Math.sin(t * 0.0043 + 1.7);
        if (speed < 0.002) {
            speed = 0.002;
        }
        float gust = gustFactor(level);
        speed += gust * 0.035;
        angle += gust * 0.6 * Math.sin(t * 0.05);
        return new Vec3(Math.cos(angle) * speed, 0.0, Math.sin(angle) * speed);
    }

    /** 葉の発生確率分母。突風時は密になる (100→50→25 目安) */
    public static int spawnChance(int base, Level level) {
        float g = gustFactor(level);
        if (g > 0.66F) {
            return Math.max(8, base / 4);
        }
        if (g > 0.33F) {
            return Math.max(12, base / 2);
        }
        return base;
    }

    /** 突風のピーク付近でたまに true。葉側で1粒おかわり用 */
    public static boolean spawnExtra(RandomSource rand, Level level) {
        return gustFactor(level) > 0.5F && rand.nextInt(3) == 0;
    }
}
