package ruby.bamboo.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import ruby.bamboo.client.gui.trump.TrumpRank;
import ruby.bamboo.client.gui.trump.TrumpSuit;

/**
 * ブラックジャックの純粋ロジック (描画・通信なし。JVMテスト可)。
 * 52枚1デッキ・ディーラーは17でスタンド (ソフト17含む)・ナチュラル即確定。
 * ダブルは最初の2枚のみ (1枚引いて強制スタンド)。
 */
public final class BlackjackGame {
    /** 1ラウンドの掛け金 (点)。ベットモード固定。 */
    public static final int ROUND_BET = 100;
    /** 掛け金の最小・最大・刻み。 */
    public static final int MIN_BET = 100;
    public static final int MAX_BET = 1000;
    public static final int BET_STEP = 100;
    /** 保険の最小・最大・刻み (掛け金の半分)。 */
    public static final int MIN_INS = 50;
    public static final int MAX_INS = 500;

    public enum Phase {
        IDLE, PLAYER, DONE
    }

    public enum Outcome {
        PLAYER_BLACKJACK, PLAYER_WIN, DEALER_BUST, PUSH, DEALER_WIN, PLAYER_BUST,
        DEALER_BLACKJACK
    }

    public record Card(TrumpSuit suit, TrumpRank rank) {
    }

    private final List<Card> shoe = new ArrayList<>();
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> dealerHand = new ArrayList<>();
    private int bet;
    private boolean doubled;
    private Phase phase = Phase.IDLE;

    public Phase phase() {
        return phase;
    }

    public int bet() {
        return bet;
    }

    public boolean doubled() {
        return doubled;
    }

    public List<Card> playerHand() {
        return List.copyOf(playerHand);
    }

    public List<Card> dealerHand() {
        return List.copyOf(dealerHand);
    }

    /** A=1/11、絵札=10 の最良値 (21以下で最大)。 */
    public static int handValue(List<Card> hand) {
        int total = 0;
        int aces = 0;
        for (Card c : hand) {
            int o = c.rank().ordinal();
            if (o == TrumpRank.ACE.ordinal()) {
                aces++;
                total += 1;
            } else if (o >= TrumpRank.JACK.ordinal()) {
                total += 10;
            } else {
                total += o + 1;
            }
        }
        while (aces > 0 && total + 10 <= 21) {
            total += 10;
            aces--;
        }
        return total;
    }

    /** ナチュラル (2枚で21)。 */
    public static boolean isBlackjack(List<Card> hand) {
        return hand.size() == 2 && handValue(hand) == 21;
    }

    /** ディーラーの表札がA。 */
    public boolean dealerShowsAce() {
        return !dealerHand.isEmpty() && dealerHand.get(0).rank() == TrumpRank.ACE;
    }

    /** ディーラーがナチュラルか (peek用)。 */
    public boolean dealerHasBlackjack() {
        return isBlackjack(dealerHand);
    }

    /** peekでBJ確定時の強制終了 (BJでなければ何もしない)。 */
    public void revealDealerBlackjack() {
        if (phase == Phase.PLAYER && isBlackjack(dealerHand)) {
            phase = Phase.DONE;
        }
    }

    public void newRound(Random rng, int bet) {
        shoe.clear();
        playerHand.clear();
        dealerHand.clear();
        for (TrumpSuit s : TrumpSuit.values()) {
            for (TrumpRank r : TrumpRank.values()) {
                if (r == TrumpRank.JOKER) {
                    continue;
                }
                shoe.add(new Card(s, r));
            }
        }
        Collections.shuffle(shoe, rng);
        this.bet = bet;
        this.doubled = false;
        playerHand.add(draw());
        dealerHand.add(draw());
        playerHand.add(draw());
        dealerHand.add(draw());
        // プレイヤーのナチュラルのみ即確定。ディーラーBJ単独はpeek待ち
        // (A表示なら保険オファー、10表示なら即開示を画面側で行う)。
        phase = isBlackjack(playerHand) ? Phase.DONE : Phase.PLAYER;
    }

    public boolean canDouble() {
        return phase == Phase.PLAYER && playerHand.size() == 2;
    }

    public void playerHit() {
        if (phase != Phase.PLAYER) {
            return;
        }
        playerHand.add(draw());
        if (handValue(playerHand) > 21) {
            phase = Phase.DONE;
        }
    }

    public void playerDouble() {
        if (!canDouble()) {
            return;
        }
        bet *= 2;
        doubled = true;
        playerHand.add(draw());
        if (handValue(playerHand) > 21) {
            phase = Phase.DONE;
        } else {
            dealerPlay();
        }
    }

    public void playerStand() {
        if (phase != Phase.PLAYER) {
            return;
        }
        dealerPlay();
    }

    private void dealerPlay() {
        while (handValue(dealerHand) < 17) {
            dealerHand.add(draw());
        }
        phase = Phase.DONE;
    }

    private Card draw() {
        return shoe.remove(shoe.size() - 1);
    }

    /** 手札のみから結果を決める (phase不問)。 */
    public Outcome outcome() {
        int p = handValue(playerHand);
        int d = handValue(dealerHand);
        boolean pBj = isBlackjack(playerHand);
        boolean dBj = isBlackjack(dealerHand);
        if (p > 21) {
            return Outcome.PLAYER_BUST;
        }
        if (d > 21) {
            return Outcome.DEALER_BUST;
        }
        if (pBj && dBj) {
            return Outcome.PUSH;
        }
        if (pBj) {
            return Outcome.PLAYER_BLACKJACK;
        }
        if (dBj) {
            return Outcome.DEALER_BLACKJACK;
        }
        if (p > d) {
            return Outcome.PLAYER_WIN;
        }
        if (p < d) {
            return Outcome.DEALER_WIN;
        }
        return Outcome.PUSH;
    }
}
