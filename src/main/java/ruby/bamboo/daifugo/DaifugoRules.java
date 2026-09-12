package ruby.bamboo.daifugo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import ruby.bamboo.client.gui.trump.TrumpRank;

/**
 * 大富豪の役判定・強弱 (純粋ロジック)。
 * 3&lt;4&lt;..&lt;K&lt;A&lt;2&lt;ジョーカー。革命中は数字のみ反転 (ジョーカーは常に最強)。
 * 階段はなし (N枚組のみ、ジョーカーはワイルド)。
 */
public final class DaifugoRules {
    private DaifugoRules() {
    }

    /** 強さ値。ジョーカー=13、数字は通常 3=0..2=12、革命中は反転。 */
    public static int power(int number, boolean revolution) {
        if (number < 0) {
            return 13;
        }
        int p = (number - 2 + 13) % 13;
        return revolution ? 12 - p : p;
    }

    /** セットの実効ランク (ジョーカーは他に合わせる。単体ジョーカーは -1)。 */
    public static int effectiveRank(List<DaifugoCard> cards) {
        for (DaifugoCard c : cards) {
            if (!c.joker()) {
                return c.number();
            }
        }
        return -1;
    }

    public static boolean containsJoker(List<DaifugoCard> cards) {
        for (DaifugoCard c : cards) {
            if (c.joker()) {
                return true;
            }
        }
        return false;
    }

    /** 同一実効ランクの1-6枚 (5枚以上はジョーカー埋め必須、素ジョーカー複数は不可)。 */
    public static boolean isValidSet(List<DaifugoCard> cards) {
        if (cards.isEmpty() || cards.size() > 6) {
            return false;
        }
        int eff = -2;
        for (DaifugoCard c : cards) {
            if (c.joker()) {
                continue;
            }
            if (eff == -2) {
                eff = c.number();
            } else if (eff != c.number()) {
                return false;
            }
        }
        // ジョーカーのみの複数枚は役なし
        return eff != -2 || cards.size() == 1;
    }

    public static int playPower(List<DaifugoCard> cards, boolean revolution) {
        return power(effectiveRank(cards), revolution);
    }

    /**
     * table へのフォロー可否 (同枚数・より強い)。単体ジョーカーには単体スペ3のみ。
     * 比べる序列は呼び出し側が渡す (部屋の現在実効序列)。
     */
    public static boolean beats(List<DaifugoCard> table, List<DaifugoCard> play,
            boolean effRevolution) {
        if (!isValidSet(play) || play.size() != table.size()) {
            return false;
        }
        if (table.size() == 1 && table.get(0).joker()) {
            return play.size() == 1 && play.get(0).spadeThree();
        }
        // J含み手も特権なし。現在の実効序列で比べる (革命中のJは弱い)。
        return playPower(play, effRevolution) > playPower(table, effRevolution);
    }

    /**
     * 非ジョーカーのスート ordinal 整列列。ジョーカーは含まない。
     */
    public static List<Integer> plainSuits(List<DaifugoCard> cards) {
        List<Integer> suits = new ArrayList<>();
        for (DaifugoCard c : cards) {
            if (!c.joker()) {
                suits.add(c.suit().ordinal());
            }
        }
        Collections.sort(suits);
        return suits;
    }

    public static int jokerCount(List<DaifugoCard> cards) {
        int n = 0;
        for (DaifugoCard c : cards) {
            if (c.joker()) {
                n++;
            }
        }
        return n;
    }

    /**
     * スートロックの多重集合照合。lock 内の各スートを play の素札で覆い、
     * 残りは play のジョーカーで埋められれば一致 (ジョーカーは万能)。
     * 枚数一致は呼び出し側で保証すること。
     */
    public static boolean lockMatch(List<Integer> lock, List<Integer> playSuits, int playWild) {
        List<Integer> need = new ArrayList<>(lock);
        for (int s : playSuits) {
            if (!need.remove((Integer) s)) {
                return false;
            }
        }
        return need.size() == playWild;
    }

    /**
     * スートロック充足。lock が null/空は無条件OK。
     * ジョーカーは適応するため常に充足するが、素札だけの出しでは縛り自体が解ける。
     * 2枚以上の場合は多重集合の完全一致がいる。
     */
    public static boolean satisfiesLock(List<DaifugoCard> play, List<Integer> lock) {
        if (lock == null || lock.isEmpty()) {
            return true;
        }
        return lockMatch(lock, plainSuits(play), jokerCount(play));
    }

    /**
     * 実効の革命状態。Jバック場の間だけ裏返る。
     * 革命中→通常、通常中→革命。場が流れれば (jbackActive 解除で) 元に戻る。
     */
    public static boolean effectiveRevolution(boolean jbackActive, boolean revolution) {
        return revolution != jbackActive;
    }

    /** 革命 (4枚以上)。 */
    public static boolean isRevolution(List<DaifugoCard> cards) {
        return cards.size() >= 4;
    }

    /** 8切り (実効8)。 */
    public static boolean containsEight(List<DaifugoCard> cards) {
        return effectiveRank(cards) == TrumpRank.EIGHT.ordinal();
    }

    /**
     * 反則上がり (手札を出し切る手に最強札/J/8/ジョーカー(実物) を含む)。
     * 最強札は実効序列で変わる (通常=2、革命中=3)。J・8・ジョーカーは序列によらず対象。
     * 実効ランクで判定するため、ジョーカーを該当札として使った場合も反則。
     */
    public static boolean isViolationFinish(List<DaifugoCard> cards, boolean revolution) {
        int eff = effectiveRank(cards);
        int strongest = revolution ? TrumpRank.THREE.ordinal() : TrumpRank.TWO.ordinal();
        return containsJoker(cards)
                || eff == strongest
                || eff == TrumpRank.JACK.ordinal()
                || eff == TrumpRank.EIGHT.ordinal();
    }

    /**
     * 手札から出せる全セットを列挙する。
     * 単体は全札区別 (スペ3識別のため)、2枚以上は代表形 (純粋+ジョーカー埋め)。
     */
    public static List<List<DaifugoCard>> genSets(List<DaifugoCard> hand) {
        List<List<DaifugoCard>> out = new ArrayList<>();
        Map<Integer, List<DaifugoCard>> byNumber = new java.util.TreeMap<>();
        List<DaifugoCard> jokers = new ArrayList<>();
        for (DaifugoCard c : hand) {
            if (c.joker()) {
                jokers.add(c);
            } else {
                byNumber.computeIfAbsent(c.number(), k -> new ArrayList<>()).add(c);
            }
        }
        DaifugoCard firstJoker = jokers.isEmpty() ? null : jokers.get(0);
        for (List<DaifugoCard> group : byNumber.values()) {
            for (DaifugoCard c : group) {
                out.add(List.of(c));
            }
            int max = Math.min(6, group.size() + jokers.size());
            for (int n = 2; n <= max; n++) {
                for (int k = 0; k <= Math.min(jokers.size(), n - 1); k++) {
                    int need = n - k;
                    if (need >= 1 && need <= group.size()) {
                        List<DaifugoCard> with = new ArrayList<>(group.subList(0, need));
                        with.addAll(jokers.subList(0, k));
                        out.add(List.copyOf(with));
                    }
                }
            }
        }
        if (firstJoker != null) {
            out.add(List.of(firstJoker));
        }
        return out;
    }

    /** 手札ソート用 (弱い順: 3→..→K→A→2、ジョーカー末尾。スートは固定順)。 */
    public static int sortKey(DaifugoCard c) {
        if (c.joker()) {
            return 1000;
        }
        return power(c.number(), false) * 4 + c.suit().ordinal();
    }
}
