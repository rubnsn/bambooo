package ruby.bamboo.daifugo;

import ruby.bamboo.client.gui.trump.TrumpRank;
import ruby.bamboo.client.gui.trump.TrumpSuit;

/**
 * 大富豪の札。不変・MC非依存。
 * ID 0-51 = suit*13+rank (A=0..K=12)、52/53=ジョーカー2枚。
 */
public record DaifugoCard(TrumpSuit suit, TrumpRank rank, boolean joker, int id) {
    public static final int JOKER_A_ID = 52;
    public static final int JOKER_B_ID = 53;
    public static final int DECK_SIZE = 54;

    public DaifugoCard(TrumpSuit suit, TrumpRank rank, boolean joker) {
        this(suit, rank, joker,
                joker ? JOKER_A_ID : suit.ordinal() * 13 + rank.ordinal());
    }

    public static DaifugoCard fromId(int id) {
        if (id >= JOKER_A_ID) {
            return new DaifugoCard(TrumpSuit.SPADE, TrumpRank.JOKER, true, id);
        }
        return new DaifugoCard(TrumpSuit.values()[id / 13], TrumpRank.values()[id % 13], false, id);
    }

    public boolean spadeThree() {
        return !joker && suit == TrumpSuit.SPADE && rank == TrumpRank.THREE;
    }

    /** 数字ランク A=0..K=12。ジョーカーは -1。 */
    public int number() {
        return joker ? -1 : rank.ordinal();
    }
}
