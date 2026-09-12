package ruby.bamboo.client.gui.trump;

import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;

/**
 * トランプの動的合成レンダラ。
 * <p>
 * 白紙/顔絵/裏面の3種の土台テクスチャに、ランク文字とスート模様(バニラフォント)を
 * 重ねて52枚+裏面を描画する。焼き付けPNGは不要。
 * 基準座標は 50x70 ドット。任意幅に等比拡大して描画する。
 */
public final class TrumpCardRenderer {
    public static final int BASE_W = 50;
    public static final int BASE_H = 70;

    public static final ResourceLocation BLANK = tex("card_blank.png");
    public static final ResourceLocation BACK = tex("card_back.png");
    public static final ResourceLocation JACK = tex("jack.png");
    public static final ResourceLocation QUEEN = tex("qween.png");
    public static final ResourceLocation KING = tex("king.png");
    public static final ResourceLocation JOKER = tex("joker.png");
    /** 絵札の角を絵から分離する白下地 (12x20)。 */
    public static final ResourceLocation PLAQUE = tex("corner_plaque.png");

    private static final float CORNER_X = 5.0F;
    private static final float LABEL_Y = 4.0F;
    private static final int MINI_X = 5;
    private static final int MINI_Y = 13;
    private static final int MINI_W = 5;
    private static final int MINI_H = 7;

    /** [rank][n] = {中心x, 中心y, フォント倍率, 倒立1/正立0}。180度回転対称の確定配置。 */
    private static final float[][][] PIP_LAYOUTS = new float[TrumpRank.values().length][][];
    static {
        PIP_LAYOUTS[TrumpRank.ACE.ordinal()] = new float[][] {
                {25.0F, 34.5F, 3.0F, 0.0F} };
        PIP_LAYOUTS[TrumpRank.TWO.ordinal()] = new float[][] {
                {25.0F, 19.5F, 2.0F, 0.0F}, {27F, 49.5F, 2.0F, 1.0F} };
        PIP_LAYOUTS[TrumpRank.THREE.ordinal()] = new float[][] {
                {25.0F, 14.5F, 2.0F, 0.0F}, {27.0F, 54.5F, 2.0F, 1.0F}, {25.0F, 34.5F, 2.0F, 0.0F} };
        PIP_LAYOUTS[TrumpRank.FOUR.ordinal()] = new float[][] {
                {17.5F, 19.5F, 2.0F, 0.0F}, {34.5F, 49.5F, 2.0F, 1.0F},
                {32.5F, 19.5F, 2.0F, 0.0F}, {19.5F, 49.5F, 2.0F, 1.0F} };
        PIP_LAYOUTS[TrumpRank.FIVE.ordinal()] = new float[][] {
                {17.5F, 19.5F, 2.0F, 0.0F}, {34.5F, 49.5F, 2.0F, 1.0F},
                {32.5F, 19.5F, 2.0F, 0.0F}, {19.5F, 49.5F, 2.0F, 1.0F},
                {25.0F, 34.5F, 2.0F, 0.0F} };
        PIP_LAYOUTS[TrumpRank.SIX.ordinal()] = new float[][] {
                {17.5F, 17.0F, 2.0F, 0.0F}, {34.5F, 52.0F, 2.0F, 1.0F},
                {32.5F, 17.0F, 2.0F, 0.0F}, {19.5F, 52.0F, 2.0F, 1.0F},
                {17.5F, 34.5F, 2.0F, 0.0F}, {34.5F, 34.5F, 2.0F, 1.0F} };
        PIP_LAYOUTS[TrumpRank.SEVEN.ordinal()] = new float[][] {
                {17.5F, 17.0F, 2.0F, 0.0F}, {34.5F, 52.0F, 2.0F, 1.0F},
                {32.5F, 17.0F, 2.0F, 0.0F}, {19.5F, 52.0F, 2.0F, 1.0F},
                {25.0F, 25.5F, 1.0F, 0.0F},
                {17.5F, 34.5F, 2.0F, 0.0F}, {32.5F, 34.5F, 2.0F, 1.0F} };
        PIP_LAYOUTS[TrumpRank.EIGHT.ordinal()] = new float[][] {
                {17.5F, 17.0F, 2.0F, 0.0F}, {34.5F, 52.0F, 2.0F, 1.0F},
                {32.5F, 17.0F, 2.0F, 0.0F}, {19.5F, 52.0F, 2.0F, 1.0F},
                {25.0F, 25.5F, 1.0F, 0.0F}, {25.0F, 43.5F, 1.0F, 1.0F},
                {17.5F, 34.5F, 2.0F, 0.0F}, {32.5F, 34.5F, 2.0F, 1.0F} };
        PIP_LAYOUTS[TrumpRank.NINE.ordinal()] = new float[][] {
                {15.0F, 13.5F, 1.0F, 0.0F}, {35.0F, 55.5F, 1.0F, 1.0F},
                {35.0F, 13.5F, 1.0F, 0.0F}, {15.0F, 55.5F, 1.0F, 1.0F},
                {15.0F, 27.5F, 1.0F, 0.0F}, {35.0F, 41.5F, 1.0F, 1.0F},
                {35.0F, 27.5F, 1.0F, 0.0F}, {15.0F, 41.5F, 1.0F, 1.0F},
                {25.0F, 34.5F, 1.0F, 0.0F} };
        PIP_LAYOUTS[TrumpRank.TEN.ordinal()] = new float[][] {
                {15.0F, 14.5F, 1.0F, 0.0F}, {35.0F, 54.5F, 1.0F, 1.0F},
                {35.0F, 14.5F, 1.0F, 0.0F}, {15.0F, 54.5F, 1.0F, 1.0F},
                {15.0F, 27.5F, 1.0F, 0.0F}, {35.0F, 41.5F, 1.0F, 1.0F},
                {35.0F, 27.5F, 1.0F, 0.0F}, {15.0F, 41.5F, 1.0F, 1.0F},
                {25.0F, 20.5F, 1.0F, 0.0F}, {25.0F, 48.5F, 1.0F, 1.0F} };
        PIP_LAYOUTS[TrumpRank.JACK.ordinal()] = new float[0][];
        PIP_LAYOUTS[TrumpRank.QUEEN.ordinal()] = new float[0][];
        PIP_LAYOUTS[TrumpRank.KING.ordinal()] = new float[0][];
        PIP_LAYOUTS[TrumpRank.JOKER.ordinal()] = new float[0][];
    }

    private TrumpCardRenderer() {
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(BambooMod.MODID, "textures/gui/minigame/trump/" + name);
    }

    public static int heightFor(int w) {
        return Math.round(w * BASE_H / (float) BASE_W);
    }

    /**
     * カード1枚を描画する。faceDown=true で裏面。
     */
    public static void renderCard(GuiGraphics gfx, Font font, int x, int y, int w,
            TrumpRank rank, TrumpSuit suit, boolean faceDown) {
        float s = w / (float) BASE_W;
        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0.0F);
        gfx.pose().scale(s, s, 1.0F);
        ResourceLocation base = faceDown ? BACK : courtTexture(rank);
        gfx.blit(base, 0, 0, 0, 0, BASE_W, BASE_H, BASE_W, BASE_H);
        if (!faceDown && rank.hasCorners()) {
            drawCorners(gfx, font, rank, suit, rank.isCourt());
            for (float[] e : PIP_LAYOUTS[rank.ordinal()]) {
                drawPip(gfx, font, suit.glyph(), suit.color(), e[0] + suit.pipDx() * e[2],
                        e[1] + suit.pipDy() * e[2], e[2], e[3] > 0.5F);
            }
        }
        gfx.pose().popPose();
    }

    private static ResourceLocation courtTexture(TrumpRank rank) {
        if (rank == TrumpRank.JACK) {
            return JACK;
        }
        if (rank == TrumpRank.QUEEN) {
            return QUEEN;
        }
        if (rank == TrumpRank.KING) {
            return KING;
        }
        if (rank == TrumpRank.JOKER) {
            return JOKER;
        }
        return BLANK;
    }

    private static void drawCorners(GuiGraphics gfx, Font font, TrumpRank rank, TrumpSuit suit, boolean plaque) {
        drawCornerSet(gfx, font, rank, suit, plaque);
        // 右下は180度回転で対称配置
        gfx.pose().pushPose();
        gfx.pose().translate(BASE_W, BASE_H, 0.0F);
        gfx.pose().mulPose(Axis.ZP.rotationDegrees(180.0F));
        drawCornerSet(gfx, font, rank, suit, plaque);
        gfx.pose().popPose();
    }

    private static void drawCornerSet(GuiGraphics gfx, Font font, TrumpRank rank, TrumpSuit suit, boolean plaque) {
        if (plaque) {
            gfx.blit(PLAQUE, 2, 2, 0, 0, 12, 20, 12, 20);
        }
        if (rank == TrumpRank.TEN) {
            // "10"は詰めて描画 (自然送りだと場の模様に触れる)
            gfx.drawString(font, "1", CORNER_X, LABEL_Y, suit.color(), false);
            gfx.drawString(font, "0", CORNER_X + font.width("1") - 1, LABEL_Y, suit.color(), false);
        } else {
            gfx.drawString(font, rank.label(), CORNER_X, LABEL_Y, suit.color(), false);
        }
        gfx.blit(suit.mini(), MINI_X, MINI_Y, 0, 0, MINI_W, MINI_H, MINI_W, MINI_H);
    }

    private static void drawPip(GuiGraphics gfx, Font font, String glyph, int color,
            float cx, float cy, float scale, boolean inverted) {
        gfx.pose().pushPose();
        gfx.pose().translate(cx, cy, 0.0F);
        if (inverted) {
            gfx.pose().mulPose(Axis.ZP.rotationDegrees(180.0F));
        }
        gfx.pose().scale(scale, scale, 1.0F);
        float w = font.width(glyph);
        // 字形は advance 6 に対しインク5幅でペン先頭寄せのため、そのままでは
        // ink中心がpen中心より1.0左に着地し、180度反転で逆に1.0右へずれる。
        // penを+1.0してink中心を(cx,cy)へ一致させる(正立・倒立共通)。
        gfx.drawString(font, glyph, -w / 2.0F + 1.0F, -font.lineHeight / 2.0F, color, false);
        gfx.pose().popPose();
    }
}
