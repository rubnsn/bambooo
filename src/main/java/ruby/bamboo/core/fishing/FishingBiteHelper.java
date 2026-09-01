package ruby.bamboo.core.fishing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * バイトパワー計算ヘルパ。
 * <p>
 * 仕様書 §2 準拠: バイトパワー = 餌 + 月齢(0-2) + 雨(+1) + 竿加算(0)。
 */
public final class FishingBiteHelper {

    private FishingBiteHelper() {}

    /**
     * 月齢ボーナス (0-2)。Level#getMoonPhase は 0-7 (0=満月)。
     * 満月 2, 次/前日 1, それ以外 0 とする。
     */
    public static int getMoonBonus(Level level) {
        int phase = level.getMoonPhase(); // 0 full, 4 new
        return switch (phase) {
            case 0 -> 2; // full
            case 1, 7 -> 1; // waxing/waning gibbous
            case 2, 6 -> 0;
            case 3, 5 -> 0;
            case 4 -> 0; // new
            default -> 0;
        };
    }

    public static int getRainBonus(Level level) {
        if (level.isRaining()) return 1;
        return 0;
    }

    public static int computeBitePower(int baitPower, Level level) {
        return baitPower + getMoonBonus(level) + getRainBonus(level);
        // 竿加算 0 は呼び出し側で加算する (将来用)
    }

    /**
     * 距離計算: chargeTicks (0-20) -> 4-15 ブロック
     */
    public static int computeDistance(int chargeTicks) {
        int c = Math.max(0, Math.min(20, chargeTicks));
        float f = c / 20.0f;
        int d = 4 + Math.round(f * 11); // 4-15
        return Math.max(4, Math.min(15, d));
    }

    /**
     * 竿のパワー。MVP は固定 25/s。
     */
    public static int getRodPower() {
        return 25;
    }
}
