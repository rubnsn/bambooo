package ruby.bamboo.client.gui.trump;

import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;

/**
 * トランプのスート。模様はバニラフォント収録の U+2660/65/66/63 を描画する。
 * 数字札の模様・角インデックス色はこの色に統一する。
 * <p>
 * バニラ字形のインク重心yはスート毎にずれる (実測: ♠3.61 ♣3.35 ♥3.24 ♦3.50、
 * 平均3.42。横は4種とも2.00で一致)。場の模様が4行で縦に揃って見えるよう
 * 重心を平均に合わせる補正値 (字形px単位) を持つ。
 */
public enum TrumpSuit {
    SPADE("\u2660", 0xFF222222, "spade_mini.png", 0.00F, -0.19F),
    HEART("\u2665", 0xFFCB242A, "heart_mini.png", 0.00F, 0.19F),
    DIAMOND("\u2666", 0xFFCB242A, "diamond_mini.png", 0.00F, -0.08F),
    CLUB("\u2663", 0xFF222222, "club_mini.png", 0.00F, 0.07F);

    private final String glyph;
    private final int color;
    private final ResourceLocation mini;
    private final float pipDx;
    private final float pipDy;

    TrumpSuit(String glyph, int color, String mini, float pipDx, float pipDy) {
        this.glyph = glyph;
        this.color = color;
        this.mini = new ResourceLocation(BambooMod.MODID, "textures/gui/minigame/trump/" + mini);
        this.pipDx = pipDx;
        this.pipDy = pipDy;
    }

    public String glyph() {
        return glyph;
    }

    public int color() {
        return color;
    }

    /** 角インデックス用の小模様 (5x7ドット)。 */
    public ResourceLocation mini() {
        return mini;
    }

    /** 場の模様の中心補正x (字形px単位、倍率適用前に加算)。 */
    public float pipDx() {
        return pipDx;
    }

    /** 場の模様の中心補正y (字形px単位、倍率適用前に加算)。 */
    public float pipDy() {
        return pipDy;
    }
}
