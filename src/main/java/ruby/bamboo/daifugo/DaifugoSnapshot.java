package ruby.bamboo.daifugo;

import java.util.List;
import java.util.UUID;

/**
 * 部屋状態の配信用データ (純粋データ、送受信双方で使う)。
 * 引数は全て文字列化済み (数値は String.valueOf)。
 */
public class DaifugoSnapshot {
    public record SeatView(String name, int cpu, boolean connected,
            int handCount, int roundRank, int prevRank) {
    }

    public record LogEntry(String key, List<String> args) {
    }

    public UUID roomId = new UUID(0L, 0L);
    public int state;
    public int round = 1;
    public int mySeat = -1;
    public int ownerSeat = -1;
    /** null 要素 = 空き席。 */
    public List<SeatView> seats = List.of();
    public List<Integer> hand = List.of();
    public List<Integer> table = List.of();
    public int tableSeat = -1;
    public int turnSeat = -1;
    public boolean revolution;
    /** Jバック場 (場にJがあり実効序列が裏返っている)。 */
    public boolean jback;
    /** ルール設定 (部屋主が開始前に変更。既定全ON)。 */
    public boolean ruleEightCut = true;
    public boolean ruleJBack = true;
    public boolean ruleSuitLock = true;
    public boolean ruleSpe3 = true;
    public boolean ruleMiyako = true;
    /** スートロック (素札スートの多重集合整列列。空=なし)。 */
    public List<Integer> lockSuits = List.of();
    public List<LogEntry> log = List.of();
    /** お返しの残り枚数 (席順、0=なし/済み)。 */
    public List<Integer> tributeOwed = List.of(0, 0, 0, 0);
}
