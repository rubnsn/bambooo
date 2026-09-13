package ruby.bamboo.daifugo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ruby.bamboo.client.gui.trump.TrumpRank;

/**
 * CPU の思考 (純粋ロジック)。
 * 他人の手札は見ない。公開情報 (場・枚数・順位・革命) と自手札のみで選ぶ。
 */
public final class CpuBrain {
    /** ゾンビ &lt; スケルトン &lt; クリーパー &lt; エンダーマン &lt; 村人。 */
    public enum Personality {
        ZOMBIE, SKELETON, CREEPER, ENDERMAN, VILLAGER
    }

    /** CPU に見せてよい公開情報 (他人の手札なし)。 */
    public record View(
            List<Integer> table,
            boolean revolution,
            boolean jbackActive,
            List<Integer> lockSuits,
            int[] handCounts,
            int[] roundRanks,
            int round,
            boolean ruleEightCut,
            boolean ruleJBack,
            boolean ruleSpe3,
            boolean tableStairs) {
    }

    /** CPU の着手。stairs は [X,JK,JK] リード時の宣言 (3枚組=false)。 */
    public record Play(List<Integer> ids, boolean stairs) {
    }

    private record Cand(List<DaifugoCard> cards, boolean stairs) {
    }

    private CpuBrain() {
    }

    /**
     * 出す札と役種を返す。空ids = パス。
     * 返す札は必ず手札内・合法 (場が空なら任意の役、あれば同枚数・同構成でより強い)。
     */
    public static Play choosePlay(View view, List<Integer> handIds,
            Personality personality, Random random) {
        List<DaifugoCard> hand = new ArrayList<>(handIds.size());
        for (int id : handIds) {
            hand.add(DaifugoCard.fromId(id));
        }
        List<DaifugoCard> table = new ArrayList<>(view.table().size());
        for (int id : view.table()) {
            table.add(DaifugoCard.fromId(id));
        }
        boolean effNow = DaifugoRules.effectiveRevolution(view.jbackActive(), view.revolution());
        boolean lead = table.isEmpty();
        // 候補に役種を付ける。リード時は両解釈 ([X,JK,JK] の3枚組/階段)、
        // フォロー時は場の役種に固定。
        java.util.Set<String> seen = new java.util.HashSet<>();
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
            // Jバックに縛りの特権なし
            if (!DaifugoRules.satisfiesLock(play, view.lockSuits())) {
                continue;
            }
            if (lead || DaifugoRules.beats(table, play, effNow, view.ruleSpe3(),
                    view.tableStairs(), cand.stairs())) {
                legal.add(cand);
            }
        }
        if (legal.isEmpty()) {
            return new Play(List.of(), false);
        }
        // 反則上がり回避 (他に手がある限り。全階級共通。ゾンビは3割でやらかす)
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
        } else if (personality == Personality.ZOMBIE && random.nextFloat() < 0.3f) {
            ok = legal;
        }
        // 上がり優先 (ゾンビは7割)
        List<Cand> finishers = new ArrayList<>();
        for (Cand cand : ok) {
            if (cand.cards().size() == hand.size()) {
                finishers.add(cand);
            }
        }
        if (!finishers.isEmpty()
                && (personality != Personality.ZOMBIE || random.nextFloat() < 0.7f)) {
            Cand best = maxCount(finishers);
            return new Play(idsOf(best.cards()), best.stairs());
        }
        // パス癖
        float passChance = switch (personality) {
            case ZOMBIE -> 0.35f;
            case SKELETON -> 0.1f;
            default -> 0.0f;
        };
        if (!lead && random.nextFloat() < passChance) {
            return new Play(List.of(), false);
        }
        Cand chosen;
        if (lead) {
            chosen = chooseLead(ok, hand, view, personality, random, effNow);
        } else {
            chosen = chooseFollow(ok, table, hand, view, personality, random, effNow);
            if (chosen == null) {
                return new Play(List.of(), false);
            }
        }
        return new Play(idsOf(chosen.cards()), chosen.stairs());
    }

    private static void addCand(List<Cand> cands, java.util.Set<String> seen,
            List<DaifugoCard> set, boolean stairs) {
        List<Integer> key = new ArrayList<>();
        for (DaifugoCard c : set) {
            key.add(c.id());
        }
        java.util.Collections.sort(key);
        if (seen.add((stairs ? "S" : "N") + key)) {
            cands.add(new Cand(set, stairs));
        }
    }

    private static Cand maxCount(List<Cand> plays) {
        Cand best = plays.get(0);
        for (Cand p : plays) {
            if (p.cards().size() > best.cards().size()) {
                best = p;
            }
        }
        return best;
    }

    private static List<Integer> idsOf(List<DaifugoCard> play) {
        List<Integer> ids = new ArrayList<>(play.size());
        for (DaifugoCard c : play) {
            ids.add(c.id());
        }
        return ids;
    }

    // ===== リード =====

    private static Cand chooseLead(List<Cand> ok, List<DaifugoCard> hand,
            View view, Personality personality, Random random, boolean effNow) {
        if (personality == Personality.ZOMBIE) {
            return ok.get(random.nextInt(ok.size()));
        }
        Cand best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Cand cand : ok) {
            List<DaifugoCard> play = cand.cards();
            // リード時は場が空 (Jバック場はあり得ない)。
            double s = play.size() * 10.0
                    - playPowerFor(cand,
                            DaifugoRules.effectiveRevolution(false, view.revolution()));
            s += revolutionBonus(cand, hand, view, personality, effNow);
            s += jBackBonus(cand, hand, view, personality, effNow);
            s += flowBonus(cand, personality, effNow);
            if (personality == Personality.SKELETON && random.nextFloat() < 0.3f) {
                s = random.nextDouble() * 10.0;
            }
            if (s > bestScore) {
                bestScore = s;
                best = cand;
            }
        }
        return best;
    }

    // ===== フォロー =====

    /** null = パス。 */
    private static Cand chooseFollow(List<Cand> ok, List<DaifugoCard> table,
            List<DaifugoCard> hand, View view, Personality personality, Random random, boolean effNow) {
        Cand cheapest = null;
        int cheapestPower = Integer.MAX_VALUE;
        for (Cand cand : ok) {
            int p = playPowerFor(cand, effNow);
            if (p < cheapestPower) {
                cheapestPower = p;
                cheapest = cand;
            }
        }
        int tablePower = view.tableStairs() ? DaifugoRules.stairPower(table, effNow)
                : DaifugoRules.playPower(table, effNow);
        // 温存: A/2/ジョーカー級で受けるのはもったいない (場が弱いときはパス)
        if ((personality == Personality.ENDERMAN || personality == Personality.VILLAGER)
                && cheapestPower >= 11 && tablePower <= 6) {
            return null;
        }
        if (personality == Personality.ZOMBIE) {
            return ok.get(random.nextInt(ok.size()));
        }
        Cand best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Cand cand : ok) {
            List<DaifugoCard> play = cand.cards();
            double s = -playPowerFor(cand, effNow);
            if (DaifugoRules.containsJoker(play)
                    && (personality == Personality.SKELETON || personality == Personality.CREEPER
                            || personality == Personality.ENDERMAN || personality == Personality.VILLAGER)) {
                s -= 5.0; // ジョーカー温存
            }
            if (isCutPlay(cand, view) && personality != Personality.SKELETON) {
                s += 3.0; // 8切りで主導権
            }
            s += revolutionBonus(cand, hand, view, personality, effNow);
            s += jBackBonus(cand, hand, view, personality, effNow);
            s += flowBonus(cand, personality, effNow);
            if (personality == Personality.SKELETON && random.nextFloat() < 0.2f) {
                s = random.nextDouble() * 10.0;
            }
            if (s > bestScore) {
                bestScore = s;
                best = cand;
            }
        }
        return best;
    }

    // ===== 革命・Jバック評価 =====

    /** 役種を考慮した強さ値。 */
    private static int playPowerFor(Cand cand, boolean eff) {
        return cand.stairs() ? DaifugoRules.stairPower(cand.cards(), eff)
                : DaifugoRules.playPower(cand.cards(), eff);
    }

    /** 場を流して先手を取り直せる手 (シックス・最強階段) への加点。 */
    private static double flowBonus(Cand cand, Personality personality, boolean eff) {
        if (personality == Personality.ZOMBIE || personality == Personality.SKELETON) {
            return 0.0;
        }
        if (DaifugoRules.isSixCard(cand.cards())
                || DaifugoRules.isSuperStairs(cand.cards(), eff)) {
            return 6.0;
        }
        return 0.0;
    }

    /** 8切り成立形 (N枚組の実効8。階段は対象外)。 */
    private static boolean isCutPlay(Cand cand, View view) {
        return view.ruleEightCut() && !cand.stairs() && DaifugoRules
                .effectiveRank(cand.cards()) == TrumpRank.EIGHT.ordinal();
    }

    private static double revolutionBonus(Cand cand, List<DaifugoCard> hand,
            View view, Personality personality, boolean effNow) {
        List<DaifugoCard> play = cand.cards();
        // 階段では革命は起きない。
        if (cand.stairs() || !DaifugoRules.isRevolution(play)) {
            return 0.0;
        }
        // 出した後は場=この手。下地反転+Jバック場化での実効序列で損得評価。
        // JバックOFFならJ含みでも裏返しは起きない。
        boolean newActive = view.jbackActive()
                || (view.ruleJBack()
                        && DaifugoRules.effectiveRank(play) == TrumpRank.JACK.ordinal());
        boolean newEff = DaifugoRules.effectiveRevolution(newActive, !view.revolution());
        return switch (personality) {
            case CREEPER -> 8.0;
            case ENDERMAN, VILLAGER -> benefitsFrom(newEff, play, hand, effNow) ? 10.0 : -10.0;
            default -> 0.0;
        };
    }

    private static double jBackBonus(Cand cand, List<DaifugoCard> hand,
            View view, Personality personality, boolean effNow) {
        List<DaifugoCard> play = cand.cards();
        // J含み手で未裏返しの場に挑む (裏返し方向によらず)。新実効序列での損得で評価。
        // OFFならJは通常札のため評価しない。階段は対象外 (連盟外ローカル)。
        if (cand.stairs() || !view.ruleJBack()
                || DaifugoRules.effectiveRank(play) != TrumpRank.JACK.ordinal()
                || view.jbackActive()) {
            return 0.0;
        }
        boolean newEff = DaifugoRules.effectiveRevolution(true, view.revolution());
        return switch (personality) {
            case CREEPER -> 4.0;
            case ENDERMAN, VILLAGER -> benefitsFrom(newEff, play, hand, effNow) ? 10.0 : -5.0;
            default -> 0.0;
        };
    }

    /** この手を出した残り手札が、新実効序列で平均的に強くなるか。 */
    private static boolean benefitsFrom(boolean newRev, List<DaifugoCard> play,
            List<DaifugoCard> hand, boolean curRev) {
        List<DaifugoCard> rest = new ArrayList<>(hand);
        rest.removeAll(play);
        if (rest.isEmpty()) {
            return false;
        }
        double now = 0.0;
        double next = 0.0;
        for (DaifugoCard c : rest) {
            now += DaifugoRules.power(c.number(), curRev);
            next += DaifugoRules.power(c.number(), newRev);
        }
        return next / rest.size() > now / rest.size() + 0.5;
    }
}
