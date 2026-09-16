package ruby.bamboo.daifugo;

import java.util.List;

/**
 * 公開イベントの通知先 (CPU記憶用)。
 * Room は履歴を保持せず、起きた事実だけを通知する。
 * 献上などの非公開の受け渡しは通知しない (透視防止)。
 */
public interface CpuObserver {
    /** ラウンド開始 (記憶のリセット)。 */
    void onRoundStart();

    /** 場への出し (反則上がりを含む、公開情報)。 */
    void onPlay(int seat, List<Integer> ids);

    /** パス (パス時点の場条件付き確定情報)。 */
    void onPass(PassEvent ev);

    /** 場流し (パス記録・場主の無効化)。 */
    void onFlow();
}
