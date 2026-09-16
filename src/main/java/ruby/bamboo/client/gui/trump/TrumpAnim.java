package ruby.bamboo.client.gui.trump;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * トランプ演出の集約クラス。時刻基準 (単調な nanoTime) で進行度を求めるため、
 * フレームレートに依らず滑らかに動く。移動・めくり・残像の判定と描画を
 * ここに集め、各画面は進行度の計算や補間を自前しない。
 */
public final class TrumpAnim {
    private TrumpAnim() {
    }

    /** 配札の1枚分の stagger (ms)。場札を段順に飛ばす間隔。 */
    public static final long DEAL_GAP_MS = 35;
    /** 配札の飛来時間 (ms)。 */
    public static final long DEAL_FLY_MS = 300;
    /** めくり時間 (ms)。確定めくり・BJの伏せ開示で共用。 */
    public static final long FLIP_MS = 400;
    /** 山札めくりの1枚分の stagger (ms)。 */
    public static final long STOCK_GAP_MS = 90;

    /** 現在時刻 (ms)。 */
    public static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    /** 開始からの進行度 0..1。 */
    public static float progress(long startMillis, long durationMillis) {
        if (durationMillis <= 0) {
            return 1.0F;
        }
        return clamp01((now() - startMillis) / (float) durationMillis);
    }

    /** 終了済みか (残像などの表示判定用)。 */
    public static boolean done(long startMillis, long durationMillis) {
        return now() - startMillis >= durationMillis;
    }

    /** 0..1に丸める。 */
    public static float clamp01(float t) {
        return t <= 0.0F ? 0.0F : Math.min(1.0F, t);
    }

    /** ease-out (1-(1-t)^2)。飛来などの減速に使う。 */
    public static float easeOut(float t) {
        t = clamp01(t);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    /** めくりの横潰れ 0..1。flipT 0=裏→1=表。 */
    public static float flipScale(float flipT) {
        return Math.abs((float) Math.cos(clamp01(flipT) * Math.PI));
    }

    /**
     * 配札・場出しの到着めくり。移動の後半40%で裏→表に返す。
     * 伏せ札は使わず、表札だけに渡す (伏せ札は0.0F固定)。
     */
    public static float dealFlip(float t) {
        return clamp01((clamp01(t) - 0.6F) / 0.4F);
    }

    /** 表裏の境目。flipT < 0.5 で裏。 */
    public static boolean faceDown(float flipT) {
        return clamp01(flipT) < 0.5F;
    }

    /**
     * 移動+めくりの合成描画。t 0=始点→1=終点 (ease-out)、flipT 0=裏→1=表。
     * flipT=1固定でめくりなしの素通し移動、始点=終点でその場めくりになる。
     */
    public static void renderTravel(GuiGraphics gfx, Font font,
            float sx, float sy, float ex, float ey,
            float t, float flipT, int cardW, int cardH,
            TrumpRank rank, TrumpSuit suit) {
        float e = easeOut(t);
        int px = Math.round(sx + (ex - sx) * e);
        int py = Math.round(sy + (ey - sy) * e);
        float sc = flipScale(flipT);
        if (sc < 0.98F) {
            gfx.fill(px + 3, py + 4, px + cardW + 3, py + cardH + 4, 0x80000000);
        }
        gfx.pose().pushPose();
        gfx.pose().translate(px + cardW / 2.0F, 0.0F, 0.0F);
        gfx.pose().scale(Math.max(0.02F, sc), 1.0F, 1.0F);
        if (faceDown(flipT)) {
            TrumpCardRenderer.renderCard(gfx, font, -cardW / 2, py, cardW,
                    TrumpRank.ACE, TrumpSuit.SPADE, true);
        } else {
            TrumpCardRenderer.renderCard(gfx, font, -cardW / 2, py, cardW,
                    rank, suit, false);
        }
        gfx.pose().popPose();
    }
}
