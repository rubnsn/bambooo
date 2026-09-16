package ruby.bamboo.daifugo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ハードAI専用の公開情報記憶 (他人の手札は一切扱わない)。
 * Room が公開イベントを通知し、Manager が部屋ごとに保持する。
 * 場に出た札のカードカウンティング・パス確定情報・場主の追跡を担う。
 *
 * 未確認の非ジョーカー札には毎戦抜かれるブラインド2枚が含まれる。
 * ブラインドはジョーカーになり得ないため、脅威の見積もりでは
 * 非ジョーカーの未確認数からのみ2枚を差し引く (ジョーカー残数は正確)。
 */
public final class HardMemory implements CpuObserver {
    /** ブラインドの枚数 (ジョーカー以外から2枚抜き)。 */
    public static final int BLIND = 2;

    private final boolean[] seen = new boolean[DaifugoCard.DECK_SIZE];
    private final List<PassEvent> passes = new ArrayList<>();
    /** 直近の場主 (-1=流れ中)。 */
    private int tableSeat = -1;

    @Override
    public void onRoundStart() {
        Arrays.fill(seen, false);
        passes.clear();
        tableSeat = -1;
    }

    @Override
    public void onPlay(int seat, List<Integer> ids) {
        for (int id : ids) {
            if (id >= 0 && id < seen.length) {
                seen[id] = true;
            }
        }
        if (seat >= 0) {
            tableSeat = seat;
        }
    }

    @Override
    public void onPass(PassEvent ev) {
        passes.add(ev);
    }

    @Override
    public void onFlow() {
        passes.clear();
        tableSeat = -1;
    }

    /** 自手札を見済みにする (着手時に呼ぶ。献上後の手札変動もここで追う)。 */
    public void observeHand(List<DaifugoCard> hand) {
        for (DaifugoCard c : hand) {
            seen[c.id()] = true;
        }
    }

    public int tableSeat() {
        return tableSeat;
    }

    /** 未確認ジョーカー数 (盲にならないため正確)。 */
    public int unseenJokers() {
        int n = 0;
        for (int id = DaifugoCard.JOKER_A_ID; id < DaifugoCard.DECK_SIZE; id++) {
            if (!seen[id]) {
                n++;
            }
        }
        return n;
    }

    /** スペ3が未確認 (誰かが持っているか盲の可能性)。 */
    public boolean spe3Unseen() {
        return !seen[DaifugoCard.SPADE_THREE_ID];
    }

    /**
     * 指定の強さより強い未確認札数 (非ジョーカー、実効序列基準)。
     * ジョーカーは常に強いため別枠で扱う。
     */
    public int strongerUnseen(int power, boolean eff) {
        int n = 0;
        for (int id = 0; id < DaifugoCard.JOKER_A_ID; id++) {
            if (!seen[id]) {
                int p = DaifugoRules.power(DaifugoCard.fromId(id).number(), false);
                if (!eff ? p > power : p < power) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * 場取り確定判定。N枚の手を出して誰にも超えられないか。
     * 全未確認札を相手持ちと仮定してもN枚組めないときに true
     * (ブランクの当たり外れに左右されない真の確定。チェイン用)。
     * 階段形の制約は無視する近似 (確定側に倒すことはない)。
     */
    public boolean certainTake(int power, int n, boolean eff) {
        return strongerUnseen(power, eff) + unseenJokers() < n;
    }

    /**
     * 場取りの見積もり。ブラインド2枚を差し引いた残数で判定する。
     * 未確認の非ジョーカー札には誰の手札にもない2枚が含まれるため、
     * 脅威の見積もりでは2枚を差し引く (ジョーカー残数は正確なため不変)。
     * 確定的ではないため、チェインには使わず threat 時の消費判断用。
     */
    public boolean likelyTake(int power, int n, boolean eff) {
        return Math.max(0, strongerUnseen(power, eff) - BLIND) + unseenJokers() < n;
    }

    /**
     * ジョーカー単騎リードの確定判定。倒せるのはスペ3のみ。
     * スペ3が未確認なら盲の可能性はあるが他人の可能性も残るため不確定。
     */
    public boolean certainJoker() {
        return !spe3Unseen() && unseenJokers() == 0;
    }

    /**
     * 出す手が全員に止められない確定情報か (定石「相手のパスは確定情報」)。
     * 全未上がり他席が、同条件 (枚数・役種・実効序列) で
     * この手以下の場にパスした記録を持つときに true。
     * スルーパス禁止により記録は場が流れるまで有効。
     */
    public boolean safeFromAll(int n, int power, boolean stairs, boolean eff,
            int[] handCounts, int mySeat) {
        for (int seat = 0; seat < handCounts.length; seat++) {
            if (seat == mySeat || handCounts[seat] <= 0) {
                continue;
            }
            boolean covered = false;
            for (PassEvent ev : passes) {
                if (ev.seat() != seat || ev.count() != n || ev.stairs() != stairs) {
                    continue;
                }
                boolean evEff = DaifugoRules.effectiveRevolution(ev.jback(),
                        ev.revolution());
                if (evEff != eff || ev.power() > power) {
                    continue;
                }
                covered = true;
                break;
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }
}
