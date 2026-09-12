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
            int round) {
    }

    private CpuBrain() {
    }

    /**
     * 出す札のID列を返す。空 = パス。
     * 返す札は必ず手札内・合法 (場が空なら任意セット、あれば同枚数でより強い)。
     */
    public static List<Integer> choosePlay(View view, List<Integer> handIds,
            Personality personality, Random random) {
        List<DaifugoCard> hand = new ArrayList<>(handIds.size());
        for (int id : handIds) {
            hand.add(DaifugoCard.fromId(id));
        }
        List<DaifugoCard> table = new ArrayList<>(view.table().size());
        for (int id : view.table()) {
            table.add(DaifugoCard.fromId(id));
        }
        List<List<DaifugoCard>> cands = DaifugoRules.genSets(hand);
        boolean effNow = DaifugoRules.effectiveRevolution(view.jbackActive(), view.revolution());
        List<List<DaifugoCard>> legal = new ArrayList<>();
        for (List<DaifugoCard> play : cands) {
            // Jバックに縛りの特権なし
            if (!DaifugoRules.satisfiesLock(play, view.lockSuits())) {
                continue;
            }
            if (table.isEmpty() || DaifugoRules.beats(table, play, effNow)) {
                legal.add(play);
            }
        }
        if (legal.isEmpty()) {
            return List.of();
        }
        // 反則上がり回避 (他に手がある限り。全階級共通。ゾンビは3割でやらかす)
        List<List<DaifugoCard>> ok = new ArrayList<>();
        for (List<DaifugoCard> play : legal) {
            boolean violation = play.size() == hand.size()
                    && DaifugoRules.isViolationFinish(play, effNow);
            if (!violation) {
                ok.add(play);
            }
        }
        if (ok.isEmpty()) {
            ok = legal;
        } else if (personality == Personality.ZOMBIE && random.nextFloat() < 0.3f) {
            ok = legal;
        }
        boolean lead = table.isEmpty();
        // 上がり優先 (ゾンビは7割)
        List<List<DaifugoCard>> finishers = new ArrayList<>();
        for (List<DaifugoCard> play : ok) {
            if (play.size() == hand.size()) {
                finishers.add(play);
            }
        }
        if (!finishers.isEmpty()
                && (personality != Personality.ZOMBIE || random.nextFloat() < 0.7f)) {
            return idsOf(maxCount(finishers));
        }
        // パス癖
        float passChance = switch (personality) {
            case ZOMBIE -> 0.35f;
            case SKELETON -> 0.1f;
            default -> 0.0f;
        };
        if (!lead && random.nextFloat() < passChance) {
            return List.of();
        }
        List<DaifugoCard> chosen;
        if (lead) {
            chosen = chooseLead(ok, hand, view, personality, random, effNow);
        } else {
            chosen = chooseFollow(ok, table, hand, view, personality, random, effNow);
            if (chosen == null) {
                return List.of();
            }
        }
        return idsOf(chosen);
    }

    private static List<DaifugoCard> maxCount(List<List<DaifugoCard>> plays) {
        List<DaifugoCard> best = plays.get(0);
        for (List<DaifugoCard> p : plays) {
            if (p.size() > best.size()) {
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

    private static List<DaifugoCard> chooseLead(List<List<DaifugoCard>> ok, List<DaifugoCard> hand,
            View view, Personality personality, Random random, boolean effNow) {
        if (personality == Personality.ZOMBIE) {
            return ok.get(random.nextInt(ok.size()));
        }
        List<DaifugoCard> best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (List<DaifugoCard> play : ok) {
            // リード時は場が空 (Jバック場はあり得ない)。
            double s = play.size() * 10.0
                    - DaifugoRules.playPower(play,
                            DaifugoRules.effectiveRevolution(false, view.revolution()));
            s += revolutionBonus(play, hand, view, personality, effNow);
            s += jBackBonus(play, hand, view, personality, effNow);
            if (personality == Personality.SKELETON && random.nextFloat() < 0.3f) {
                s = random.nextDouble() * 10.0;
            }
            if (s > bestScore) {
                bestScore = s;
                best = play;
            }
        }
        return best;
    }

    // ===== フォロー =====

    /** null = パス。 */
    private static List<DaifugoCard> chooseFollow(List<List<DaifugoCard>> ok, List<DaifugoCard> table,
            List<DaifugoCard> hand, View view, Personality personality, Random random, boolean effNow) {
        List<DaifugoCard> cheapest = null;
        int cheapestPower = Integer.MAX_VALUE;
        for (List<DaifugoCard> play : ok) {
            int p = DaifugoRules.playPower(play, effNow);
            if (p < cheapestPower) {
                cheapestPower = p;
                cheapest = play;
            }
        }
        int tablePower = DaifugoRules.playPower(table, effNow);
        // 温存: A/2/ジョーカー級で受けるのはもったいない (場が弱いときはパス)
        if ((personality == Personality.ENDERMAN || personality == Personality.VILLAGER)
                && cheapestPower >= 11 && tablePower <= 6) {
            return null;
        }
        if (personality == Personality.ZOMBIE) {
            return ok.get(random.nextInt(ok.size()));
        }
        List<DaifugoCard> best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (List<DaifugoCard> play : ok) {
            double s = -DaifugoRules.playPower(play, effNow);
            if (DaifugoRules.containsJoker(play)
                    && (personality == Personality.SKELETON || personality == Personality.CREEPER
                            || personality == Personality.ENDERMAN || personality == Personality.VILLAGER)) {
                s -= 5.0; // ジョーカー温存
            }
            if (DaifugoRules.containsEight(play) && personality != Personality.SKELETON) {
                s += 3.0; // 8切りで主導権
            }
            s += revolutionBonus(play, hand, view, personality, effNow);
            s += jBackBonus(play, hand, view, personality, effNow);
            if (personality == Personality.SKELETON && random.nextFloat() < 0.2f) {
                s = random.nextDouble() * 10.0;
            }
            if (s > bestScore) {
                bestScore = s;
                best = play;
            }
        }
        return best;
    }

    // ===== 革命・Jバック評価 =====

    private static double revolutionBonus(List<DaifugoCard> play, List<DaifugoCard> hand,
            View view, Personality personality, boolean effNow) {
        if (!DaifugoRules.isRevolution(play)) {
            return 0.0;
        }
        // 出した後は場=この手。下地反転+Jバック場化での実効序列で損得評価。
        boolean newActive = view.jbackActive()
                || DaifugoRules.effectiveRank(play) == TrumpRank.JACK.ordinal();
        boolean newEff = DaifugoRules.effectiveRevolution(newActive, !view.revolution());
        return switch (personality) {
            case CREEPER -> 8.0;
            case ENDERMAN, VILLAGER -> benefitsFrom(newEff, play, hand, effNow) ? 10.0 : -10.0;
            default -> 0.0;
        };
    }

    private static double jBackBonus(List<DaifugoCard> play, List<DaifugoCard> hand,
            View view, Personality personality, boolean effNow) {
        // J含み手で未裏返しの場に挑む (裏返し方向によらず)。新実効序列での損得で評価。
        if (DaifugoRules.effectiveRank(play) != TrumpRank.JACK.ordinal()
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
