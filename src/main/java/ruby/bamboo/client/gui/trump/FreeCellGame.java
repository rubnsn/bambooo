package ruby.bamboo.client.gui.trump;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * フリーセルの純粋ロジック (描画・入力なし)。
 * 場札8列 (全表・降順赤黒交互)・フリーセル4・組札4 (Aから同スート)。
 * 複数枚移動は空きに応じた上限付き ((1+空フリーセル)×2^空列。移動先の空列は数えない)。
 */
public final class FreeCellGame {
    public static final int TABLEAU_COUNT = 8;
    public static final int FREECELL_COUNT = 4;
    public static final int FOUNDATION_COUNT = 4;

    /** 1枚。不変。フリーセルは全札表向きのため裏表なし。 */
    public record Card(TrumpSuit suit, TrumpRank rank) {
        /** A=1 … K=13。 */
        public int order() {
            return rank.ordinal() + 1;
        }

        public boolean red() {
            return suit == TrumpSuit.HEART || suit == TrumpSuit.DIAMOND;
        }
    }

    /** null 要素 = 空き。常に4枠。 */
    private final List<Card> freecells = new ArrayList<>(FREECELL_COUNT);
    private final List<List<Card>> foundations = new ArrayList<>(FOUNDATION_COUNT);
    private final List<List<Card>> tableau = new ArrayList<>(TABLEAU_COUNT);

    public FreeCellGame() {
        this(new Random());
    }

    public FreeCellGame(Random random) {
        for (int i = 0; i < FREECELL_COUNT; i++) {
            freecells.add(null);
        }
        for (int i = 0; i < FOUNDATION_COUNT; i++) {
            foundations.add(new ArrayList<>());
        }
        for (int i = 0; i < TABLEAU_COUNT; i++) {
            tableau.add(new ArrayList<>());
        }
        newGame(random);
    }

    /** 52枚をシャッフルして8列に配る (0-3列が7枚・4-7列が6枚)。 */
    public void newGame(Random random) {
        for (int i = 0; i < FREECELL_COUNT; i++) {
            freecells.set(i, null);
        }
        foundations.forEach(List::clear);
        tableau.forEach(List::clear);
        List<Card> deck = new ArrayList<>(52);
        for (TrumpSuit suit : TrumpSuit.values()) {
            for (TrumpRank rank : TrumpRank.values()) {
                if (rank == TrumpRank.JOKER) {
                    continue;
                }
                deck.add(new Card(suit, rank));
            }
        }
        Collections.shuffle(deck, random);
        for (int i = 0; i < deck.size(); i++) {
            tableau.get(i % TABLEAU_COUNT).add(deck.get(i));
        }
    }

    // ===== 参照 =====

    public Card freecellCard(int f) {
        return freecells.get(f);
    }

    public int freeFreecellCount() {
        int n = 0;
        for (Card c : freecells) {
            if (c == null) {
                n++;
            }
        }
        return n;
    }

    /** 最初の空きフリーセル。なければ -1。 */
    public int firstEmptyFreecell() {
        for (int i = 0; i < FREECELL_COUNT; i++) {
            if (freecells.get(i) == null) {
                return i;
            }
        }
        return -1;
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
        int n = 0;
        for (List<Card> pile : foundations) {
            n += pile.size();
        }
        return n == 52;
    }

    private static Card topOf(List<Card> pile) {
        return pile.isEmpty() ? null : pile.get(pile.size() - 1);
    }

    // ===== 場札 =====

    /** index 以降が降順・赤黒交互の正しい階段なら持ち上げ可。 */
    public boolean isMovableSequence(int col, int index) {
        List<Card> pile = tableau.get(col);
        if (index < 0 || index >= pile.size()) {
            return false;
        }
        for (int i = index; i + 1 < pile.size(); i++) {
            Card lower = pile.get(i);
            Card upper = pile.get(i + 1);
            if (upper.red() == lower.red() || upper.order() != lower.order() - 1) {
                return false;
            }
        }
        return true;
    }

    /** index 以降を取り除いて返す。 */
    public List<Card> removeTableauSequence(int col, int index) {
        List<Card> pile = tableau.get(col);
        List<Card> held = new ArrayList<>(pile.subList(index, pile.size()));
        pile.subList(index, pile.size()).clear();
        return held;
    }

    public void addTableauSequence(int col, List<Card> cards) {
        tableau.get(col).addAll(cards);
    }

    // ===== フリーセル・組札の出し入れ (持ち運びの戻し用) =====

    public Card removeFreecellCard(int f) {
        Card c = freecells.get(f);
        freecells.set(f, null);
        return c;
    }

    public void placeFreecellCard(int f, Card card) {
        freecells.set(f, card);
    }

    public Card removeFoundationTop(int f) {
        List<Card> pile = foundations.get(f);
        return pile.isEmpty() ? null : pile.remove(pile.size() - 1);
    }

    public void placeBackOnFoundation(int f, Card card) {
        foundations.get(f).add(card);
    }

    // ===== 配置可否 =====

    /** moving(持ち札の先頭=下端) を場札 col に置けるか。空列はなんでも可。 */
    public boolean canStackOnTableau(Card moving, int col) {
        Card top = topOf(tableau.get(col));
        if (top == null) {
            return true;
        }
        return top.red() != moving.red() && top.order() == moving.order() + 1;
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

    /**
     * 一度に動かせる最大枚数 ((1+空フリーセル)×2^空列。移動先の空列は数えない)。
     * 空列への移動は空列数を1つ少なく数えるのが定石通り。
     */
    public int maxMovable(int destCol) {
        int emptyCols = 0;
        for (int i = 0; i < TABLEAU_COUNT; i++) {
            if (i != destCol && tableau.get(i).isEmpty()) {
                emptyCols++;
            }
        }
        return (1 + freeFreecellCount()) * (1 << emptyCols);
    }

    // ===== 移動 (一手操作) =====

    public boolean moveTableauToTableau(int from, int index, int to) {
        if (from == to || !isMovableSequence(from, index)) {
            return false;
        }
        List<Card> pile = tableau.get(from);
        if (pile.size() - index > maxMovable(to) || !canStackOnTableau(pile.get(index), to)) {
            return false;
        }
        tableau.get(to).addAll(removeTableauSequence(from, index));
        return true;
    }

    /** 場札の先頭 (一番上) を空きフリーセルへ。 */
    public boolean moveTableauToFreecell(int from) {
        List<Card> pile = tableau.get(from);
        if (pile.isEmpty()) {
            return false;
        }
        int f = firstEmptyFreecell();
        if (f < 0) {
            return false;
        }
        freecells.set(f, pile.remove(pile.size() - 1));
        return true;
    }

    public boolean moveFreecellToTableau(int f, int to) {
        Card card = freecells.get(f);
        if (card == null || !canStackOnTableau(card, to)) {
            return false;
        }
        tableau.get(to).add(card);
        freecells.set(f, null);
        return true;
    }

    /** 場札の先頭 (一番上) を組札へ。 */
    public boolean moveTableauToFoundation(int from) {
        List<Card> pile = tableau.get(from);
        if (pile.isEmpty()) {
            return false;
        }
        Card top = pile.get(pile.size() - 1);
        int f = findFoundationFor(top);
        if (f < 0) {
            return false;
        }
        foundations.get(f).add(pile.remove(pile.size() - 1));
        return true;
    }

    public boolean moveFreecellToFoundation(int f) {
        Card card = freecells.get(f);
        if (card == null) {
            return false;
        }
        int dest = findFoundationFor(card);
        if (dest < 0) {
            return false;
        }
        foundations.get(dest).add(card);
        freecells.set(f, null);
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
