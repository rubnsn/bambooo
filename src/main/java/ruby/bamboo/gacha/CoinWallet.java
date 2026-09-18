package ruby.bamboo.gacha;

/**
 * コイン残高の境界スタブ。
 * <p>
 * 現状は常時プレイ可 (残高チェックなし)。将来コインをキャラクターへの
 * Capability 形式にする際は、このクラスの実装だけを差し替える。
 * {@link GachaManager} は本クラス経由でのみ残高に触ること。
 */
public final class CoinWallet {
    /** 10連の価格 (表示用。現状は消費しない) */
    public static final int COST_10 = 3000;
    /** true の間は無料 (テスト用)。Cap化時に false へ。 */
    public static final boolean ALWAYS_FREE = true;

    private CoinWallet() {
    }

    /** 表示用残高。スタブは無限大扱いのため -1 (「∞」表示用)。 */
    public static int balanceOf(net.minecraft.world.entity.player.Player player) {
        return -1;
    }

    /**
     * 消費試行。スタブは常に true。
     * 将来: {@code Capから残高取得→不足ならfalse→差引→sync}。
     */
    public static boolean tryConsume(net.minecraft.world.entity.player.Player player, int cost) {
        return true;
    }
}
