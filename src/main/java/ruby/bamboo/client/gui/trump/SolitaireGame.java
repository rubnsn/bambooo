package ruby.bamboo.client.gui.trump;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * クロンダイクの純粋ロジック (描画・入力なし)。
 * 山札は1枚めくり・回収無制限。組札4山はスート固定なし (Aから同スートで積む)。
 * 場札への戻し (組札→場札) も許す。
 */
public final class SolitaireGame {
    public static final int TABLEAU_COUNT = 7;
    public static final int FOUNDATION_COUNT = 4;

    /** 1枚。不変。 */
    public record Card(TrumpSuit suit, TrumpRank rank, boolean faceUp) {
        /** 表向きにしたコピーを返す。 */
        public Card asFaceUp() {
            return faceUp ? this : new Card(suit, rank, true);
        }

        /** A=1 … K=13。 */
        public int order() {
            return rank.ordinal() + 1;
        }

        public boolean red() {
            return suit == TrumpSuit.HEART || suit == TrumpSuit.DIAMOND;
        }
    }

    private final List<Card> stock = new ArrayList<>();
    private final List<Card> waste = new ArrayList<>();
    private final List<List<Card>> foundations = new ArrayList<>(FOUNDATION_COUNT);
    private final List<List<Card>> tableau = new ArrayList<>(TABLEAU_COUNT);

    public SolitaireGame() {
        this(new Random());
    }

    public SolitaireGame(Random random) {
        for (int i = 0; i < FOUNDATION_COUNT; i++) {
            foundations.add(new ArrayList<>());
        }
        for (int i = 0; i < TABLEAU_COUNT; i++) {
            tableau.add(new ArrayList<>());
        }
        newGame(random);
    }

    public void newGame(Random random) {
        stock.clear();
        waste.clear();
        foundations.forEach(List::clear);
        tableau.forEach(List::clear);
        List<Card> deck = new ArrayList<>(52);
        for (TrumpSuit suit : TrumpSuit.values()) {
            for (TrumpRank rank : TrumpRank.values()) {
                if (rank == TrumpRank.JOKER) {
                    continue;
                }
                deck.add(new Card(suit, rank, false));
            }
        }
        Collections.shuffle(deck, random);
        int p = 0;
        for (int col = 0; col < TABLEAU_COUNT; col++) {
            for (int row = 0; row <= col; row++) {
                Card c = deck.get(p++);
                tableau.get(col).add(row == col ? c.asFaceUp() : c);
            }
        }
        stock.addAll(deck.subList(p, deck.size()));
    }

    // ===== 参照 =====

    public int stockCount() {
        return stock.size();
    }

    public boolean wasteEmpty() {
        return waste.isEmpty();
    }

    public Card wasteTop() {
        return topOf(waste);
    }

    public int tableauSize(int col) {
        return tableau.get(col).size();
    }

    public Card tableauCard(int col, int index) {
        return tableau.get(col).get(index);
    }

    public List<Card> tableauColumn(int col) {
        return Collections.unmodifiableList(tableau.get(col));
    }

    public int foundationSize(int f) {
        return foundations.get(f).size();
    }

    public Card foundationTop(int f) {
        return topOf(foundations.get(f));
    }

    public List<Card> foundationPile(int f) {
        return Collections.unmodifiableList(foundations.get(f));
    }

    public boolean isWon() {
        for (List<Card> pile : foundations) {
            if (pile.size() != 13) {
                return false;
            }
        }
        return true;
    }

    private static Card topOf(List<Card> pile) {
        return pile.isEmpty() ? null : pile.get(pile.size() - 1);
    }

    // ===== 山札 =====

    /** 山札を1枚めくる。 */
    public boolean drawFromStock() {
        if (stock.isEmpty()) {
            return false;
        }
        waste.add(stock.remove(stock.size() - 1).asFaceUp());
        return true;
    }

    /** 捨て札を裏返して山札に戻す (無制限)。束ごと裏返すため先にめくった札が上に来る。 */
    public boolean recycleStock() {
        if (!stock.isEmpty() || waste.isEmpty()) {
            return false;
        }
        for (int i = waste.size() - 1; i >= 0; i--) {
            Card c = waste.get(i);
            stock.add(new Card(c.suit(), c.rank(), false));
        }
        waste.clear();
        return true;
    }

    // ===== 場札 =====

    /** index 以降が表向きの正しい階段 (降順・赤黒交互) なら持ち上げ可。 */
    public boolean isMovableSequence(int col, int index) {
        List<Card> pile = tableau.get(col);
        if (index < 0 || index >= pile.size() || !pile.get(index).faceUp()) {
            return false;
        }
        for (int i = index; i + 1 < pile.size(); i++) {
            Card lower = pile.get(i);
            Card upper = pile.get(i + 1);
            if (!upper.faceUp() || upper.red() == lower.red() || upper.order() != lower.order() - 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * index 以降を取り除いて返す。露出した札はこの時点ではめくらない
     * (置き場所確定=配置コミット時に {@link #flipTableauTop} で表にする。戻しは裏のまま)。
     */
    public List<Card> removeTableauSequence(int col, int index) {
        List<Card> pile = tableau.get(col);
        List<Card> held = new ArrayList<>(pile.subList(index, pile.size()));
        pile.subList(index, pile.size()).clear();
        return held;
    }

    public void addTableauSequence(int col, List<Card> cards) {
        tableau.get(col).addAll(cards);
    }

    /** 配置コミット後に呼ぶ。露出した一番上の札が裏なら表にする。 */
    public void flipTableauTop(int col) {
        List<Card> pile = tableau.get(col);
        if (!pile.isEmpty()) {
            int last = pile.size() - 1;
            pile.set(last, pile.get(last).asFaceUp());
        }
    }

    // ===== 捨て札・組札の出し入れ (持ち運びの戻し用) =====

    public Card removeWasteTop() {
        return waste.isEmpty() ? null : waste.remove(waste.size() - 1);
    }

    public void addWasteTop(Card card) {
        waste.add(card);
    }

    public Card removeFoundationTop(int f) {
        List<Card> pile = foundations.get(f);
        return pile.isEmpty() ? null : pile.remove(pile.size() - 1);
    }

    public void placeBackOnFoundation(int f, Card card) {
        foundations.get(f).add(card);
    }

    // ===== 配置可否 =====

    /** moving(持ち札の末尾=下端) を場札 col に置けるか。 */
    public boolean canStackOnTableau(Card moving, int col) {
        Card top = topOf(tableau.get(col));
        if (top == null) {
            return moving.order() == 13;
        }
        return top.faceUp() && top.red() != moving.red() && top.order() == moving.order() + 1;
    }

    public boolean canPlaceOnFoundation(Card card, int f) {
        Card top = topOf(foundations.get(f));
        if (top == null) {
            return card.order() == 1;
        }
        return top.suit() == card.suit() && card.order() == top.order() + 1;
    }

    /** card を置ける組札を探す (同スート継続を優先、空きはAのみ)。-1 は置き場なし。 */
    public int findFoundationFor(Card card) {
        for (int f = 0; f < FOUNDATION_COUNT; f++) {
            Card top = topOf(foundations.get(f));
            if (top != null && top.suit() == card.suit() && card.order() == top.order() + 1) {
                return f;
            }
        }
        if (card.order() == 1) {
            for (int f = 0; f < FOUNDATION_COUNT; f++) {
                if (foundations.get(f).isEmpty()) {
                    return f;
                }
            }
        }
        return -1;
    }

    // ===== 移動 (一手操作) =====

    public boolean moveWasteToTableau(int col) {
        Card top = wasteTop();
        if (top == null || !canStackOnTableau(top, col)) {
            return false;
        }
        tableau.get(col).add(waste.remove(waste.size() - 1));
        return true;
    }

    public boolean moveWasteToFoundation() {
        Card top = wasteTop();
        if (top == null) {
            return false;
        }
        int f = findFoundationFor(top);
        if (f < 0) {
            return false;
        }
        foundations.get(f).add(waste.remove(waste.size() - 1));
        return true;
    }

    public boolean moveTableauToTableau(int from, int index, int to) {
        if (from == to || !isMovableSequence(from, index)) {
            return false;
        }
        List<Card> pile = tableau.get(from);
        if (!canStackOnTableau(pile.get(index), to)) {
            return false;
        }
        tableau.get(to).addAll(removeTableauSequence(from, index));
        flipTableauTop(from);
        return true;
    }

    /** 場札の先頭 (一番上) を組札へ。 */
    public boolean moveTableauToFoundation(int from) {
        List<Card> pile = tableau.get(from);
        if (pile.isEmpty()) {
            return false;
        }
        Card top = pile.get(pile.size() - 1);
        if (!top.faceUp()) {
            return false;
        }
        int f = findFoundationFor(top);
        if (f < 0) {
            return false;
        }
        foundations.get(f).add(pile.remove(pile.size() - 1));
        flipTableauTop(from);
        return true;
    }

    public boolean moveFoundationToTableau(int f, int to) {
        Card top = topOf(foundations.get(f));
        if (top == null || !canStackOnTableau(top, to)) {
            return false;
        }
        tableau.get(to).add(foundations.get(f).remove(foundations.get(f).size() - 1));
        return true;
    }
}
