package ruby.bamboo.daifugo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import ruby.bamboo.client.gui.trump.TrumpRank;

/**
 * 村人のハードモード思考 (CpuBrain ノーマルとは別クラス)。
 * 大富豪の定石を公開情報のみで打つ。他人の手札は見ない。
 *
 * 中核は勝ち形チェイン探索: 止められない手 (確定場取り・即流し・
 * パス確定情報) だけを繋いで手札を出し切る順序を予定し、初手を指す。
 * 例: 残り [4,8,2] なら 2単騎 (確定) →8 (流し) →4 で勝利。
 * 革命は序列反転としてチェイン内で評価する (弱札多→革命→強札化)。
 * チェインが無いときは捨て札の重み付け (温存: 確定札・8・スペ3・
 * ジョーカー・ペア・革命素材) と戦略的パスで構える。
 * 記憶 (カードカウンティング) は HardMemory が持ち、Room は通知のみ。
 * ハードモードでは全人格がこの思考を基準にし、人格別ラダーで乖離する。
 */
public final class HardVillagerBrain {
    /** 弱札の上限 (実効power)。この以下は親のときしか処理できない札。 */
    private static final int WEAK_MAX = 3;
    /** 終盤とみなす残り枚数 (上がり逆算フェーズ)。 */
    private static final int ENDGAME = 5;
    /** 上がり警戒する他人の残り枚数。 */
    private static final int THREAT_COUNT = 2;
    /** チェインの最大手数。 */
    private static final int CHAIN_DEPTH = 5;
    /** チェイン探索のノード上限。 */
    private static final int CHAIN_NODES = 4000;

    private record Cand(List<DaifugoCard> cards, boolean stairs) {
    }

    private HardVillagerBrain() {
    }

    /**
     * 出す札と役種を返す。空ids = パス。
     * 返す札は必ず手札内・合法。mySeat はパス情報の自分除外用。
     * memory が null のときは履歴なしの保守的判断になる。
     */
    public static CpuBrain.Play choosePlay(CpuBrain.View view, List<Integer> handIds,
            int mySeat, HardMemory memory, Random random) {
        return decide(view, handIds, mySeat, memory).play();
    }

    /**
     * 村人思考ベースの人格別選択 (ハードモード用)。
     * 全人格が村人思考で選び、弱い人格ほど重みを無視してランダムに切り替える
     * (CpuBrain.randomRate と同一ラダー)。ランダム pool は反則回避済み。
     */
    public static CpuBrain.Play choosePlayWithLadder(CpuBrain.View view,
            List<Integer> handIds, int mySeat, HardMemory memory,
            CpuBrain.Personality personality, Random random) {
        Decision d = decide(view, handIds, mySeat, memory);
        if (!d.ok().isEmpty()
                && random.nextFloat() < CpuBrain.randomRate(personality)) {
            Cand c = d.ok().get(random.nextInt(d.ok().size()));
            return new CpuBrain.Play(idsOf(c.cards()), c.stairs());
        }
        return d.play();
    }

    /** 思考結果と合法手 (ラダーのランダム pool)。 */
    private record Decision(CpuBrain.Play play, List<Cand> ok) {
    }

    private static Decision decide(CpuBrain.View view, List<Integer> handIds,
            int mySeat, HardMemory memory) {
        List<DaifugoCard> hand = new ArrayList<>(handIds.size());
        for (int id : handIds) {
            hand.add(DaifugoCard.fromId(id));
        }
        List<DaifugoCard> table = new ArrayList<>(view.table().size());
        for (int id : view.table()) {
            table.add(DaifugoCard.fromId(id));
        }
        HardMemory mem = memory != null ? memory : new HardMemory();
        mem.observeHand(hand);
        boolean effNow = DaifugoRules.effectiveRevolution(view.jbackActive(), view.revolution());
        boolean lead = table.isEmpty();
        // 候補に役種を付ける (ノーマルと同一の合法手列挙)。
        Set<String> seen = new HashSet<>();
        List<Cand> cands = new ArrayList<>();
        for (List<DaifugoCard> set : DaifugoRules.genSets(hand)) {
            boolean validSet = DaifugoRules.isValidSet(set);
            boolean stairsShape = DaifugoRules.isStairs(set);
            if (lead) {
                if (validSet) {
                    addCand(cands, seen, set, false);
                }
                if (stairsShape) {
                    addCand(cands, seen, set, true);
                }
            } else if (view.tableStairs()) {
                if (stairsShape) {
                    addCand(cands, seen, set, true);
                }
            } else {
                if (validSet) {
                    addCand(cands, seen, set, false);
                }
            }
        }
        List<Cand> legal = new ArrayList<>();
        for (Cand cand : cands) {
            List<DaifugoCard> play = cand.cards();
            if (!DaifugoRules.satisfiesLock(play, view.lockSuits())) {
                continue;
            }
            if (lead || DaifugoRules.beats(table, play, effNow, view.ruleSpe3(),
                    view.tableStairs(), cand.stairs())) {
                legal.add(cand);
            }
        }
        if (legal.isEmpty()) {
            return new Decision(new CpuBrain.Play(List.of(), false), List.of());
        }
        // 反則上がり回避 (他に手がある限り必ず)。
        List<Cand> ok = new ArrayList<>();
        for (Cand cand : legal) {
            List<DaifugoCard> play = cand.cards();
            boolean violation = play.size() == hand.size() && DaifugoRules
                    .isViolationFinish(play, effNow, view.ruleEightCut(), view.ruleJBack(),
                            view.ruleSpe3(), cand.stairs());
            if (!violation) {
                ok.add(cand);
            }
        }
        if (ok.isEmpty()) {
            ok = legal;
        }
        // 上がり優先 (1手で出切れる手)。
        List<Cand> finishers = new ArrayList<>();
        for (Cand cand : ok) {
            if (cand.cards().size() == hand.size()) {
                finishers.add(cand);
            }
        }
        if (!finishers.isEmpty()) {
            Cand best = finishers.get(0);
            for (Cand c : finishers) {
                if (c.cards().size() > best.cards().size()) {
                    best = c;
                }
            }
            return new Decision(new CpuBrain.Play(idsOf(best.cards()), best.stairs()),
                    ok);
        }
        // 勝ち形チェイン (止められない手だけで出し切る順序) の初手を指す
        List<Cand> chain = findChain(ok, hand, view, mySeat, mem, effNow, lead,
                new int[]{CHAIN_NODES});
        if (chain != null && !chain.isEmpty()) {
            Cand first = chain.get(0);
            return new Decision(
                    new CpuBrain.Play(idsOf(first.cards()), first.stairs()), ok);
        }
        Cand chosen;
        if (lead) {
            chosen = chooseLead(ok, hand, view, mySeat, mem, effNow);
        } else {
            chosen = chooseFollow(ok, hand, view, mySeat, mem, effNow);
            if (chosen == null) {
                return new Decision(new CpuBrain.Play(List.of(), false), ok);
            }
        }
        return new Decision(new CpuBrain.Play(idsOf(chosen.cards()), chosen.stairs()),
                ok);
    }

    private static void addCand(List<Cand> cands, Set<String> seen,
            List<DaifugoCard> set, boolean stairs) {
        List<Integer> key = new ArrayList<>();
        for (DaifugoCard c : set) {
            key.add(c.id());
        }
        Collections.sort(key);
        if (seen.add((stairs ? "S" : "N") + key)) {
            cands.add(new Cand(set, stairs));
        }
    }

    private static List<Integer> idsOf(List<DaifugoCard> play) {
        List<Integer> ids = new ArrayList<>(play.size());
        for (DaifugoCard c : play) {
            ids.add(c.id());
        }
        return ids;
    }

    private static int powerOf(Cand cand, boolean eff) {
        return cand.stairs() ? DaifugoRules.stairPower(cand.cards(), eff)
                : DaifugoRules.playPower(cand.cards(), eff);
    }

    private static List<DaifugoCard> sub(List<DaifugoCard> hand, List<DaifugoCard> play) {
        List<DaifugoCard> rest = new ArrayList<>(hand);
        rest.removeAll(play);
        return rest;
    }

    // ===== 勝ち形チェイン探索 =====

    /**
     * 止められない手だけで手札を出し切る順序を探す。先頭が今回の着手。
     * 親を取るたび場が流れるため、中間手は確定場取り・即流しに限る
     * (パス情報は流れて消えるため初手フォローでのみ有効)。
     * 革命・Jバックは序列反転として伝播させる。
     */
    private static List<Cand> findChain(List<Cand> ok, List<DaifugoCard> hand,
            CpuBrain.View view, int mySeat, HardMemory mem, boolean effNow, boolean lead,
            int[] budget) {
        List<Cand> takes = new ArrayList<>(ok);
        takes.sort((a, b) -> {
            int pa = powerOf(a, effNow);
            int pb = powerOf(b, effNow);
            if (pa != pb) {
                return pa - pb;
            }
            return a.cards().size() - b.cards().size();
        });
        for (Cand take : takes) {
            if (!isFirstTake(take, view, mem, mySeat, effNow, lead)) {
                continue;
            }
            List<DaifugoCard> rem = sub(hand, take.cards());
            boolean rev1 = view.revolution()
                    ^ (!take.stairs() && DaifugoRules.isRevolution(take.cards()));
            List<Cand> path = new ArrayList<>();
            path.add(take);
            if (dfs(rem, rev1, view, mem, path, budget)) {
                return path;
            }
        }
        return null;
    }

    /** チェインの深さ優先探索。rem を確定手で削り、最終手を置ければ成功。 */
    private static boolean dfs(List<DaifugoCard> rem, boolean rev, CpuBrain.View view,
            HardMemory mem, List<Cand> path, int[] budget) {
        boolean eff = DaifugoRules.effectiveRevolution(false, rev);
        // 最終手: 残り全部 (両解釈を試す。反則上がりは不可)
        boolean validSet = DaifugoRules.isValidSet(rem);
        boolean stairsShape = DaifugoRules.isStairs(rem);
        if (validSet && !DaifugoRules.isViolationFinish(rem, eff, view.ruleEightCut(),
                view.ruleJBack(), view.ruleSpe3(), false)) {
            path.add(new Cand(rem, false));
            return true;
        }
        if (stairsShape && !DaifugoRules.isViolationFinish(rem, eff, view.ruleEightCut(),
                view.ruleJBack(), view.ruleSpe3(), true)) {
            path.add(new Cand(rem, true));
            return true;
        }
        if (path.size() >= CHAIN_DEPTH || budget[0] <= 0) {
            return false;
        }
        for (Cand u : midTakes(rem, view, mem, eff)) {
            budget[0]--;
            boolean rev2 = rev ^ (!u.stairs() && DaifugoRules.isRevolution(u.cards()));
            path.add(u);
            if (dfs(sub(rem, u.cards()), rev2, view, mem, path, budget)) {
                return true;
            }
            path.remove(path.size() - 1);
            if (budget[0] <= 0) {
                return false;
            }
        }
        return false;
    }

    /** 初手 (合法内) がチェインの取っ掛かりになるか。 */
    private static boolean isFirstTake(Cand take, CpuBrain.View view, HardMemory mem,
            int mySeat, boolean effNow, boolean lead) {
        List<DaifugoCard> play = take.cards();
        // 即流し (リード時の8・スペ3は流れないため six・最強階段のみ)
        if (DaifugoRules.isSixCard(play)
                || DaifugoRules.isSuperStairs(play, effNow)) {
            return true;
        }
        if (!lead) {
            if (isCutFollow(take, view) || isSpe3Return(take, view)) {
                return true;
            }
            // パス確定情報 (場が生きている初手フォローでのみ有効)
            if (mem.safeFromAll(play.size(), powerOf(take, effNow), take.stairs(),
                    effNow, view.handCounts(), mySeat)) {
                return true;
            }
        }
        return isCertain(take, mem, effNow);
    }

    /** 中間手 (真部分集合・確定か即流し) の列挙。弱い順。 */
    private static List<Cand> midTakes(List<DaifugoCard> rem, CpuBrain.View view,
            HardMemory mem, boolean eff) {
        Set<String> seen = new HashSet<>();
        List<Cand> out = new ArrayList<>();
        List<Integer> remIds = new ArrayList<>();
        for (DaifugoCard c : rem) {
            remIds.add(c.id());
        }
        Collections.sort(remIds);
        for (List<DaifugoCard> set : DaifugoRules.genSets(rem)) {
            for (boolean stairs : new boolean[]{false, true}) {
                if (stairs && !DaifugoRules.isStairs(set)) {
                    continue;
                }
                if (!stairs && !DaifugoRules.isValidSet(set)) {
                    continue;
                }
                Cand cand = new Cand(set, stairs);
                List<Integer> key = new ArrayList<>();
                for (DaifugoCard c : set) {
                    key.add(c.id());
                }
                Collections.sort(key);
                if (key.equals(remIds) || !seen.add((stairs ? "S" : "N") + key)) {
                    continue;
                }
                if (DaifugoRules.isSixCard(set)
                        || DaifugoRules.isSuperStairs(set, eff)
                        || isCertain(cand, mem, eff)) {
                    out.add(cand);
                    if (out.size() >= 24) {
                        out.sort((a, b) -> powerOf(a, eff) - powerOf(b, eff));
                        return out;
                    }
                }
            }
        }
        out.sort((a, b) -> powerOf(a, eff) - powerOf(b, eff));
        return out;
    }

    /** 止められない手か (確定場取り。ジョーカー単騎はスペ3枯れが条件)。 */
    private static boolean isCertain(Cand cand, HardMemory mem, boolean eff) {
        List<DaifugoCard> play = cand.cards();
        int jk = DaifugoRules.jokerCount(play);
        if (jk == play.size()) {
            // 純ジョーカー: 単騎はスペ3枯れで確定、ペアは対策なしで確定
            return play.size() == 2 || (play.size() == 1 && mem.certainJoker());
        }
        if (play.size() == 1 && play.get(0).joker()) {
            return mem.certainJoker();
        }
        return mem.certainTake(powerOf(cand, eff), play.size(), eff);
    }

    /** 8切り成立のフォロー (N枚組の実効8。階段は対象外)。 */
    private static boolean isCutFollow(Cand cand, CpuBrain.View view) {
        return view.ruleEightCut() && !cand.stairs() && DaifugoRules
                .effectiveRank(cand.cards()) == TrumpRank.EIGHT.ordinal();
    }

    /** スペ3返し (ジョーカー単騎への単騎スペ3)。 */
    private static boolean isSpe3Return(Cand cand, CpuBrain.View view) {
        List<DaifugoCard> play = cand.cards();
        return view.ruleSpe3() && play.size() == 1 && play.get(0).spadeThree()
                && view.table().size() == 1
                && view.table().get(0) >= DaifugoCard.JOKER_A_ID;
    }

    // ===== 捨て札の重み付け =====

    /** 未上がり他席の最小残り枚数 (自分除外)。 */
    private static int minOthersLeft(CpuBrain.View view, int mySeat) {
        int min = Integer.MAX_VALUE;
        for (int seat = 0; seat < view.handCounts().length; seat++) {
            if (seat == mySeat) {
                continue;
            }
            min = Math.min(min, view.handCounts()[seat]);
        }
        return min == Integer.MAX_VALUE ? 99 : min;
    }

    /** 残り手札の弱札数 (親で処理したい札)。 */
    private static int weakLeft(List<DaifugoCard> hand, List<DaifugoCard> play,
            boolean eff) {
        int n = 0;
        outer: for (DaifugoCard c : hand) {
            for (DaifugoCard p : play) {
                if (p.id() == c.id()) {
                    continue outer;
                }
            }
            if (!c.joker() && DaifugoRules.power(c.number(), eff) <= WEAK_MAX) {
                n++;
            }
        }
        return n;
    }

    /**
     * 強制反則残りか。この手を出すと残り1枚になり、
     * その1枚が最終手で必ず反則になる (単騎出し切り = 反則確定)。
     * チェイン終端は非反則が保証されるため、ヒューリスティック着手用。
     */
    private static boolean leavesForcedViolation(List<DaifugoCard> hand,
            List<DaifugoCard> play, CpuBrain.View view, boolean eff) {
        List<DaifugoCard> rest = sub(hand, play);
        if (rest.size() != 1) {
            return false;
        }
        return DaifugoRules.isViolationFinish(rest, eff, view.ruleEightCut(),
                view.ruleJBack(), view.ruleSpe3(), false);
    }

    /** 同じ数字の相方が手札に残るか。 */
    private static boolean hasMate(DaifugoCard c, List<DaifugoCard> hand) {
        if (c.joker()) {
            return false;
        }
        for (DaifugoCard o : hand) {
            if (o.id() != c.id() && !o.joker() && o.number() == c.number()) {
                return true;
            }
        }
        return false;
    }

    /** 革命が得か (残り手札の平均強さが上がるか)。 */
    private static boolean revolutionDesirable(List<DaifugoCard> hand, boolean effNow) {
        if (hand.isEmpty()) {
            return false;
        }
        double now = 0.0;
        double next = 0.0;
        for (DaifugoCard c : hand) {
            now += DaifugoRules.power(c.number(), effNow);
            next += DaifugoRules.power(c.number(), !effNow);
        }
        return next / hand.size() > now / hand.size() + 0.5;
    }

    /**
     * 札の温存度。高い札は勝ち形の部品として残す。
     * 確定場取り・8・スペ3・ジョーカー・ペア (弱ペアは革命保険)・革命素材。
     */
    private static double keepScore(DaifugoCard c, List<DaifugoCard> hand,
            CpuBrain.View view, HardMemory mem, boolean eff, boolean revWant) {
        if (c.joker()) {
            return (mem.certainJoker() || hand.size() <= ENDGAME) ? 2.0 : 10.0;
        }
        double k = 0.0;
        int p = DaifugoRules.power(c.number(), eff);
        // 確定場取り単騎は将来の親として温存する
        if (mem.certainTake(p, 1, eff)) {
            k += 8.0;
        }
        // 8は弱札処分の流し札として温存する
        if (view.ruleEightCut() && c.rank() == TrumpRank.EIGHT) {
            k += weakLeft(hand, List.of(c), eff) > 0 ? 6.0 : 2.0;
        }
        // スペ3はジョーカー返し用に温存する (返し先枯れなら凡札)
        if (view.ruleSpe3() && c.spadeThree()) {
            k += mem.unseenJokers() > 0 ? 8.0 : -2.0;
        }
        // ペアは崩さず残す (弱ペアは革命保険・終盤処分単位)
        if (hasMate(c, hand)) {
            k += p <= WEAK_MAX ? 5.0 : 3.0;
        }
        // 革命素材 (4枚揃い + 革命が得なとき)
        if (revWant) {
            int same = 0;
            for (DaifugoCard o : hand) {
                if (!o.joker() && o.number() == c.number()) {
                    same++;
                }
            }
            if (same >= 4) {
                k += 5.0;
            }
        }
        return k;
    }

    private static double maxKeep(Cand cand, List<DaifugoCard> hand, CpuBrain.View view,
            HardMemory mem, boolean eff, boolean revWant) {
        double max = 0.0;
        for (DaifugoCard c : cand.cards()) {
            max = Math.max(max, keepScore(c, hand, view, mem, eff, revWant));
        }
        return max;
    }

    // ===== リード =====

    private static Cand chooseLead(List<Cand> ok, List<DaifugoCard> hand,
            CpuBrain.View view, int mySeat, HardMemory mem, boolean effNow) {
        int othersMin = minOthersLeft(view, mySeat);
        boolean endgame = hand.size() <= ENDGAME;
        boolean revWant = revolutionDesirable(hand, effNow);
        Cand best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Cand cand : ok) {
            List<DaifugoCard> play = cand.cards();
            int n = play.size();
            int power = powerOf(cand, effNow);
            boolean joker = DaifugoRules.containsJoker(play);
            // 基本は弱い札・少数枚から (親維持と弱札処理)。
            // 定石「強いカードは枚数で数える」: 強い札の多枚数出しは場取り回数を減らす。
            double s = -power * 2.0 - n * 1.5;
            if (power <= WEAK_MAX) {
                s += (WEAK_MAX - power) * 1.0 + 4.0;
            }
            // 安全な攻め (全員パス確定) は弱札処理の好機として高評価
            if (mem.safeFromAll(n, power, cand.stairs(), effNow, view.handCounts(),
                    mySeat)) {
                s += 18.0 + (WEAK_MAX >= power ? 6.0 : 0.0);
            }
            // 強い札の多枚数出しは場取り回数を潰すため減点 (革命・即流しは除く)
            if (n >= 2 && power >= 10 && !DaifugoRules.isRevolution(play)
                    && !DaifugoRules.isSixCard(play)
                    && !DaifugoRules.isSuperStairs(play, effNow)) {
                s -= (n - 1) * 6.0;
            }
            // 終盤は速度優先 (枚数の多い手を出す)
            if (endgame) {
                s += n * 4.0;
            }
            // 上がり警戒: 他人が残り少ないときは確定場取り札で親を渡さない。
            // 真の確定札は温存を解いて使う。見積もり確度でも弱札より優先する
            boolean certainNow = !joker && mem.certainTake(power, n, effNow);
            if (othersMin <= THREAT_COUNT && hand.size() > ENDGAME) {
                if (certainNow) {
                    s += 25.0;
                } else if (!joker && mem.likelyTake(power, n, effNow)) {
                    s += 10.0;
                } else if (power >= 10) {
                    s += 6.0;
                } else {
                    s -= 8.0;
                }
            }
            // 勝ち形の部品は崩さない (温存度が高い手は避ける。
            // 革命手自体は革命評価に任せるため対象外。
            // 脅威下の確定札消費も温存を解く)
            if (!DaifugoRules.isRevolution(play)
                    && !(othersMin <= THREAT_COUNT && hand.size() > ENDGAME
                            && certainNow)) {
                s -= maxKeep(cand, hand, view, mem, effNow, revWant) * 2.0;
            }
            // ペア崩しは避ける (終盤を除く。弱ペアは革命保険)
            if (n == 1 && !endgame && hasMate(play.get(0), hand)) {
                int p = DaifugoRules.power(play.get(0).number(), effNow);
                s -= p <= WEAK_MAX ? 12.0 : 6.0;
            }
            s += revolutionBonus(cand, hand, view, effNow);
            s += jBackBonus(cand, hand, view, effNow);
            s -= leadJokerWaste(cand, mem, endgame);
            s -= spe3LeadWaste(cand, view, mem);
            // ジョーカー単騎の確定読み (スペ3・JK残なしのときだけ積極策)
            if (n == 1 && play.get(0).joker() && mem.certainJoker()) {
                s += 12.0;
            }
            // 終盤の残り1枚の強制反則は避ける (その1枚は単騎でしか出せない)
            if (hand.size() <= ENDGAME + 1
                    && leavesForcedViolation(hand, play, view, effNow)) {
                s -= 30.0;
            }
            if (s > bestScore) {
                bestScore = s;
                best = cand;
            }
        }
        return best;
    }

    // ===== フォロー =====

    /** null = パス (戦略的パスを含む)。 */
    private static Cand chooseFollow(List<Cand> ok, List<DaifugoCard> hand,
            CpuBrain.View view, int mySeat, HardMemory mem, boolean effNow) {
        // スペ3返し (ジョーカー単騎には即抜き。それ以外は温存)
        for (Cand cand : ok) {
            if (isSpe3Return(cand, view)) {
                return cand;
            }
        }
        int dealerSeat = mem.tableSeat();
        int dealerLeft = dealerSeat >= 0 && dealerSeat < view.handCounts().length
                ? view.handCounts()[dealerSeat]
                : 99;
        boolean dealerThreat = dealerLeft <= THREAT_COUNT;
        boolean revWant = revolutionDesirable(hand, effNow);
        int weakAfterMin = Integer.MAX_VALUE;
        for (Cand cand : ok) {
            weakAfterMin = Math.min(weakAfterMin, weakLeft(hand, cand.cards(), effNow));
        }
        boolean endgame = hand.size() <= ENDGAME;
        Cand best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Cand cand : ok) {
            List<DaifugoCard> play = cand.cards();
            int n = play.size();
            int power = powerOf(cand, effNow);
            boolean joker = DaifugoRules.containsJoker(play);
            // 基本は最弱受け
            double s = -power * 2.0;
            // 場を出した相手が上がり間際なら止め手を優先する
            if (dealerThreat) {
                s += 20.0;
                if (joker && mem.spe3Unseen() && n == 1) {
                    // JK単騎受けはスペ3で抜かれて親を失う危険がある
                    s -= 15.0;
                }
            }
            // 8切りは親が欲しいときだけ使う (親不要なら8も温存)
            if (isCutFollow(cand, view)) {
                s += (weakAfterMin > 0 || endgame) ? 12.0 + weakAfterMin * 3.0 : -6.0;
            }
            // 確定場取り札は弱札処理のための親が欲しいときだけ使う
            if (!joker && mem.certainTake(power, n, effNow)) {
                s += weakAfterMin > 0 ? 10.0 : -10.0;
            }
            // ジョーカー受けは残り少ないか親を取る価値が高いときのみ
            if (joker) {
                boolean lastResort = hand.size() <= 3
                        || (weakAfterMin >= 3 && !(n == 1 && mem.spe3Unseen()));
                s += lastResort ? 8.0 : -18.0;
            }
            // 受けた後に誰も止められないなら積極策
            if (mem.safeFromAll(n, power, cand.stairs(), effNow, view.handCounts(),
                    mySeat)) {
                s += 8.0;
            }
            // 勝ち形の部品は崩さない
            s -= maxKeep(cand, hand, view, mem, effNow, revWant) * 2.0;
            // ペア崩しは避ける (終盤を除く)
            if (n == 1 && !endgame && !play.get(0).joker()
                    && hasMate(play.get(0), hand)) {
                int p = DaifugoRules.power(play.get(0).number(), effNow);
                s -= p <= WEAK_MAX ? 12.0 : 6.0;
            }
            s += revolutionBonus(cand, hand, view, effNow);
            s += jBackBonus(cand, hand, view, effNow);
            if (DaifugoRules.isSixCard(play)
                    || DaifugoRules.isSuperStairs(play, effNow)) {
                s += 6.0;
            }
            // 終盤の残り1枚の強制反則は避ける
            if (hand.size() <= ENDGAME + 1
                    && leavesForcedViolation(hand, play, view, effNow)) {
                s -= 30.0;
            }
            if (s > bestScore) {
                bestScore = s;
                best = cand;
            }
        }
        if (best == null) {
            return null;
        }
        // ===== 戦略的パス (止められるとき以外は優位を保つ) =====
        if (!dealerThreat && hand.size() > ENDGAME) {
            List<DaifugoCard> play = best.cards();
            int n = play.size();
            int power = powerOf(best, effNow);
            boolean joker = DaifugoRules.containsJoker(play);
            int weakAfter = weakLeft(hand, play, effNow);
            // 序盤の最強ペア切りは無駄 (弱札処分の優位として残す)
            if (n >= 2 && power >= 11) {
                return null;
            }
            // ジョーカーは抱えて終盤に回す (残3枚以下を除く)
            if (joker && hand.size() > 3) {
                return null;
            }
            // 8切りは親不要なら温存する
            if (isCutFollow(best, view) && weakAfter == 0) {
                return null;
            }
            // 確定場取り札は親不要なら温存する
            if (!joker && mem.certainTake(power, n, effNow) && weakAfter == 0) {
                return null;
            }
        }
        return best;
    }

    // ===== 献上のお返し選び =====

    /**
     * お返しの献上札を選ぶ (弱い順。ただし勝ち形の部品は渡さない)。
     * ペア崩し・8 (流し札) ・階段素材は後回しにする。
     * ジョーカーは最強のため通常は選ばれない。
     */
    public static List<Integer> chooseGiveback(List<Integer> handIds, int count,
            boolean eightCut) {
        List<DaifugoCard> rest = new ArrayList<>(handIds.size());
        for (int id : handIds) {
            rest.add(DaifugoCard.fromId(id));
        }
        List<Integer> give = new ArrayList<>();
        for (int k = 0; k < count && !rest.isEmpty(); k++) {
            DaifugoCard pick = rest.get(0);
            double bestScore = Double.POSITIVE_INFINITY;
            for (DaifugoCard c : rest) {
                double s = DaifugoRules.power(c.number(), false) * 2.0;
                if (c.joker()) {
                    s += 100.0;
                }
                // ペアを崩す渡しは避ける (両方渡す分には構わない)
                if (hasMateIn(c, rest)) {
                    s += 10.0;
                }
                // 8は流し札として残す
                if (eightCut && c.rank() == TrumpRank.EIGHT) {
                    s += 6.0;
                }
                // 階段素材 (同スート3連番以上の一部) は残す
                if (inRun(c, rest)) {
                    s += 4.0;
                }
                if (s < bestScore) {
                    bestScore = s;
                    pick = c;
                }
            }
            rest.remove(pick);
            give.add(pick.id());
        }
        return give;
    }

    /** 候補内に相方が残るか (自分以外)。 */
    private static boolean hasMateIn(DaifugoCard c, List<DaifugoCard> rest) {
        if (c.joker()) {
            return false;
        }
        for (DaifugoCard o : rest) {
            if (o.id() != c.id() && !o.joker() && o.number() == c.number()) {
                return true;
            }
        }
        return false;
    }

    /** 同スート3連番以上の一部か (階段素材)。 */
    private static boolean inRun(DaifugoCard c, List<DaifugoCard> rest) {
        if (c.joker()) {
            return false;
        }
        boolean[] has = new boolean[13];
        for (DaifugoCard o : rest) {
            if (!o.joker() && o.suit() == c.suit()) {
                has[DaifugoRules.seqPos(o.number())] = true;
            }
        }
        int pos = DaifugoRules.seqPos(c.number());
        for (int s = Math.max(0, pos - 2); s + 2 < 13 && s <= pos; s++) {
            if (has[s] && has[s + 1] && has[s + 2]) {
                return true;
            }
        }
        return false;
    }

    // ===== 革命・Jバック評価 =====
    /**
     * リードでのジョーカー無駄遣いを抑える。
     * 終盤・確定読み・革命・シックス・最強階段に昇格する手は対象外。
     */
    private static double leadJokerWaste(Cand cand, HardMemory mem, boolean endgame) {
        int jk = DaifugoRules.jokerCount(cand.cards());
        if (jk == 0 || endgame) {
            return 0.0;
        }
        List<DaifugoCard> play = cand.cards();
        if (DaifugoRules.isSixCard(play) || DaifugoRules.isRevolution(play)
                || DaifugoRules.isSuperStairs(play, false)
                || DaifugoRules.isSuperStairs(play, true)) {
            return 0.0;
        }
        if (play.size() == 1 && mem.certainJoker()) {
            return 0.0;
        }
        return 14.0 * jk;
    }

    /**
     * 単騎スペ3のリードは温存する (ジョーカーが残っている間は返し用)。
     * 手持ちのスペ3も返し札として数える (場のスペ3枯れとは別)。
     */
    private static double spe3LeadWaste(Cand cand, CpuBrain.View view, HardMemory mem) {
        List<DaifugoCard> play = cand.cards();
        if (!view.ruleSpe3() || play.size() != 1 || !play.get(0).spadeThree()) {
            return 0.0;
        }
        // ジョーカーが枯れていれば返し先がないため通常札として出す
        if (mem.unseenJokers() == 0) {
            return -4.0;
        }
        return 8.0;
    }

    /**
     * 革命の損得。定石「弱い札が多いときに起こす」:
     * 出した残り手札が新実効序列で平均的に強くなるなら加点、逆なら減点。
     * 階段では革命は起きない。
     */
    private static double revolutionBonus(Cand cand, List<DaifugoCard> hand,
            CpuBrain.View view, boolean effNow) {
        List<DaifugoCard> play = cand.cards();
        if (cand.stairs() || !DaifugoRules.isRevolution(play)) {
            return 0.0;
        }
        boolean newActive = view.jbackActive()
                || (view.ruleJBack()
                        && DaifugoRules.effectiveRank(play) == TrumpRank.JACK.ordinal());
        boolean newEff = DaifugoRules.effectiveRevolution(newActive, !view.revolution());
        List<DaifugoCard> rest = new ArrayList<>(hand);
        rest.removeAll(play);
        if (rest.isEmpty()) {
            return 0.0;
        }
        double now = 0.0;
        double next = 0.0;
        for (DaifugoCard c : rest) {
            now += DaifugoRules.power(c.number(), effNow);
            next += DaifugoRules.power(c.number(), newEff);
        }
        double diff = next / rest.size() - now / rest.size();
        // 4枚出しは場取り回数を減らすため、明確な得があるときだけ起こす
        if (diff > 0.5) {
            return 12.0;
        }
        if (diff < -0.5) {
            return -14.0;
        }
        return -4.0;
    }

    /**
     * Jバックの損得。新実効序列での残り手札の平均強さで評価する。
     * OFFならJは通常札のため評価しない。階段は対象外。
     */
    private static double jBackBonus(Cand cand, List<DaifugoCard> hand,
            CpuBrain.View view, boolean effNow) {
        List<DaifugoCard> play = cand.cards();
        if (cand.stairs() || !view.ruleJBack()
                || DaifugoRules.effectiveRank(play) != TrumpRank.JACK.ordinal()
                || view.jbackActive()) {
            return 0.0;
        }
        boolean newEff = DaifugoRules.effectiveRevolution(true, view.revolution());
        List<DaifugoCard> rest = new ArrayList<>(hand);
        rest.removeAll(play);
        if (rest.isEmpty()) {
            return 0.0;
        }
        double now = 0.0;
        double next = 0.0;
        for (DaifugoCard c : rest) {
            now += DaifugoRules.power(c.number(), effNow);
            next += DaifugoRules.power(c.number(), newEff);
        }
        return next / rest.size() > now / rest.size() + 0.5 ? 12.0 : -6.0;
    }
}
