package ruby.bamboo.daifugo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import ruby.bamboo.client.gui.trump.TrumpRank;

/**
 * 大富豪の役判定・強弱 (純粋ロジック、日本大富豪連盟の競技ルールに準拠)。
 * 3&lt;4&lt;..&lt;K&lt;A&lt;2&lt;ジョーカー。革命中は数字のみ反転 (ジョーカーは常に最強)。
 * 役は N枚組 (ジョーカー代用可) と階段 (同一スート連番3枚以上、JK補完可)。
 * 階段の強さは最弱札で比べる。Jバックは連盟ルールにないローカル要素 (別管理)。
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
     * table へのフォロー可否 (同枚数・同じ構成・より強い。連盟 §25-27)。
     * 単体ジョーカーには単体スペ3のみ
     * (spe3Enabled が false ならジョーカー単騎は倒せない)。
     * 階段には階段で、最弱札同士を比べる (連盟 §36)。
     * 比べる序列は呼び出し側が渡す (部屋の現在実効序列)。
     */
    public static boolean beats(List<DaifugoCard> table, List<DaifugoCard> play,
            boolean effRevolution, boolean spe3Enabled, boolean tableStairs, boolean playStairs) {
        if (play.size() != table.size() || tableStairs != playStairs) {
            return false;
        }
        if (tableStairs) {
            if (!isStairs(play)) {
                return false;
            }
            return stairPower(play, effRevolution) > stairPower(table, effRevolution);
        }
        if (!isValidSet(play)) {
            return false;
        }
        if (table.size() == 1 && table.get(0).joker()) {
            return spe3Enabled && play.size() == 1 && play.get(0).spadeThree();
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

    // ===== 階段 =====

    /**
     * 連番位置。number() は ACE=0, TWO=1, THREE=2..KING=12 に対し、
     * 3=0 .. K=10, A=11, 2=12 を返す。
     */
    public static int seqPos(int number) {
        return (number + 11) % 13;
    }

    /** 連番位置→number()。 */
    public static int numberAtPos(int pos) {
        return pos <= 10 ? pos + 2 : pos - 11;
    }

    /**
     * 階段として成立する連番開始位置の列挙。
     * 同一スート・3枚以上・ジョーカーは不足位置を補完 (端・中間いずれも可、2枚まで)。
     * 空 = 階段不成立 (複数スート混在など)。
     */
    public static List<Integer> stairStarts(List<DaifugoCard> cards) {
        List<Integer> starts = new ArrayList<>();
        if (cards.size() < 3) {
            return starts;
        }
        int suit = -1;
        boolean[] has = new boolean[13];
        int wild = 0;
        for (DaifugoCard c : cards) {
            if (c.joker()) {
                wild++;
                continue;
            }
            if (suit < 0) {
                suit = c.suit().ordinal();
            } else if (suit != c.suit().ordinal()) {
                return starts;
            }
            has[seqPos(c.number())] = true;
        }
        int n = cards.size();
        for (int s = 0; s + n <= 13; s++) {
            int miss = 0;
            for (int k = 0; k < n; k++) {
                if (!has[s + k]) {
                    miss++;
                }
            }
            if (miss == wild) {
                starts.add(s);
            }
        }
        return starts;
    }

    public static boolean isStairs(List<DaifugoCard> cards) {
        return !stairStarts(cards).isEmpty();
    }

    /**
     * 最強解釈の最弱位置 (存在しない場合 -1)。
     * [4,5,JK] は 4-5-6 と 3-4-5 の両解釈があり得るが、最強 (4-5-6) を採る。
     */
    public static int stairWeakestPos(List<DaifugoCard> cards) {
        int best = -1;
        for (int s : stairStarts(cards)) {
            if (s > best) {
                best = s;
            }
        }
        return best;
    }

    /** 階段の強さ値 (最弱札で比べる)。 */
    public static int stairPower(List<DaifugoCard> cards, boolean revolution) {
        return power(numberAtPos(stairWeakestPos(cards)), revolution);
    }

    /**
     * 3枚組と階段の両方に読める形 ([X,JK,JK])。
     * リード時はどちらとして出すか宣言がいる (連盟 §21)。
     */
    public static boolean isDualShape(List<DaifugoCard> cards) {
        return cards.size() == 3 && jokerCount(cards) == 2;
    }

    /**
     * 最強階段 (通常 A-2-JK / 革命時 3-4-JK)。即流し対象 (連盟 §38条の2)。
     * 素札2枚は同一スート (階段成立が前提)。
     */
    public static boolean isSuperStairs(List<DaifugoCard> cards, boolean effRevolution) {
        if (cards.size() != 3 || jokerCount(cards) != 1 || !isStairs(cards)) {
            return false;
        }
        TrumpRank r1 = effRevolution ? TrumpRank.THREE : TrumpRank.ACE;
        TrumpRank r2 = effRevolution ? TrumpRank.FOUR : TrumpRank.TWO;
        boolean a = false;
        boolean b = false;
        for (DaifugoCard c : cards) {
            if (c.joker()) {
                continue;
            }
            if (c.rank() == r1) {
                a = true;
            } else if (c.rank() == r2) {
                b = true;
            } else {
                return false;
            }
        }
        return a && b;
    }

    /** シックスカード (同一ランク4枚+ジョーカー2枚の6枚出し)。即流し対象 (連盟 §22条の2)。 */
    public static boolean isSixCard(List<DaifugoCard> cards) {
        return cards.size() == 6 && jokerCount(cards) == 2 && isValidSet(cards);
    }

    /**
     * スートロックの多重集合照合。lock 内の各スートを play の素札で覆い、
     * 残りは play のジョーカーで埋められれば一致 (ジョーカーは万能)。
     * 素札に縛り外スートが混ざると不一致 (連盟 §57)。
     * 枚数一致は呼び出し側で保証すること。
     */
    public static boolean lockMatch(List<Integer> lock, List<Integer> playSuits, int playWild) {
        List<Integer> need = new ArrayList<>(lock);
        for (int s : playSuits) {
            if (!need.remove((Integer) s)) {
                return false;
            }
        }
        return need.size() <= playWild;
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

    /** 革命 (同一ランク4枚以上。階段は含まない。連盟 §48-49)。 */
    public static boolean isRevolution(List<DaifugoCard> cards) {
        return cards.size() >= 4 && isValidSet(cards);
    }

    /**
     * 反則上がり (連盟 §60-61)。
     * 通常時: 2を含む手・8切り成立の8出し・ジョーカーを1枚でも含む手・スペ3単騎。
     * 革命時: 3を含むすべての最終手 (ペア・階段を含む)。
     * J・8は対応ルールON時のみ対象 (OFFなら通常札のため適法)。
     * 階段中の8は8切り不成立のため対象外 (連盟 §47)。
     * 階段中のJはJバック (連盟外ローカル) の対象外。
     * 実効ランクで判定するため、ジョーカーを該当札として使った場合も反則。
     */
    public static boolean isViolationFinish(List<DaifugoCard> cards, boolean revolution,
            boolean eightCut, boolean jback, boolean spe3, boolean asStairs) {
        if (containsJoker(cards)) {
            return true;
        }
        if (asStairs) {
            // 最強解釈に禁則札 (通常2・革命3) を含むか。JKは上部で確定済み。
            int forbidden = revolution ? TrumpRank.THREE.ordinal() : TrumpRank.TWO.ordinal();
            return stairHasRank(cards, forbidden);
        }
        int eff = effectiveRank(cards);
        int strongest = revolution ? TrumpRank.THREE.ordinal() : TrumpRank.TWO.ordinal();
        boolean spe3Single = spe3 && cards.size() == 1 && cards.get(0).spadeThree();
        return eff == strongest || spe3Single
                || (jback && eff == TrumpRank.JACK.ordinal())
                || (eightCut && eff == TrumpRank.EIGHT.ordinal());
    }

    /** 階段の最強解釈に指定ランク (number()) を含むか。 */
    private static boolean stairHasRank(List<DaifugoCard> cards, int number) {
        int start = stairWeakestPos(cards);
        if (start < 0) {
            return false;
        }
        int want = seqPos(number);
        return want >= start && want < start + cards.size();
    }

    /**
     * 手札から出せる全セットを列挙する。
     * 単体は全札区別 (スペ3識別のため)、2枚以上は代表形 (純粋+ジョーカー埋め)。
     * 階段も同一スート連番の代表形で列挙する ([X,JK,JK] は3枚組と重複するが、
     * 役種が違うため両方残す。呼び出し側で解釈を分ける)。
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
        // 階段 (同一スート連番3枚以上、代表形。別解釈の重複は除く)
        Map<Integer, Map<Integer, DaifugoCard>> suitPos = new java.util.TreeMap<>();
        for (DaifugoCard c : hand) {
            if (!c.joker()) {
                suitPos.computeIfAbsent(c.suit().ordinal(), k -> new java.util.TreeMap<>())
                        .putIfAbsent(seqPos(c.number()), c);
            }
        }
        java.util.Set<String> seenStairs = new java.util.HashSet<>();
        for (Map<Integer, DaifugoCard> pos : suitPos.values()) {
            for (int start = 0; start < 13; start++) {
                for (int len = 3; start + len <= 13; len++) {
                    List<DaifugoCard> reals = new ArrayList<>();
                    for (int k = 0; k < len; k++) {
                        DaifugoCard rc = pos.get(start + k);
                        if (rc != null) {
                            reals.add(rc);
                        }
                    }
                    int need = len - reals.size();
                    if (reals.isEmpty() || need < 0 || need > jokers.size()) {
                        continue;
                    }
                    List<DaifugoCard> cand = new ArrayList<>(reals);
                    cand.addAll(jokers.subList(0, need));
                    List<Integer> key = new ArrayList<>();
                    for (DaifugoCard cd : cand) {
                        key.add(cd.id());
                    }
                    Collections.sort(key);
                    if (seenStairs.add(key.toString())) {
                        out.add(List.copyOf(cand));
                    }
                }
            }
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
