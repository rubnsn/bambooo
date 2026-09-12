package ruby.bamboo.client.gui.trump;

/**
 * トランプのランク。A〜K の13階級。絵札(J/Q/K)は顔絵テクスチャを使う。
 */
public enum TrumpRank {
    ACE("A", false, true),
    TWO("2", false, true),
    THREE("3", false, true),
    FOUR("4", false, true),
    FIVE("5", false, true),
    SIX("6", false, true),
    SEVEN("7", false, true),
    EIGHT("8", false, true),
    NINE("9", false, true),
    TEN("10", false, true),
    JACK("J", true, true),
    QUEEN("Q", true, true),
    KING("K", true, true),
    /** 絵入りで文字なし。角インデックスも打たない。 */
    JOKER("JOKER", true, false);

    private final String label;
    private final boolean court;
    private final boolean corners;

    TrumpRank(String label, boolean court, boolean corners) {
        this.label = label;
        this.court = court;
        this.corners = corners;
    }

    public String label() {
        return label;
    }

    public boolean isCourt() {
        return court;
    }

    public boolean hasCorners() {
        return corners;
    }
}
