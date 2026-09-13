package ruby.bamboo.daifugo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import ruby.bamboo.client.gui.trump.TrumpRank;

/**
 * 大富豪の部屋 (純粋状態機械、MC非依存、日本大富豪連盟の競技ルールに準拠)。
 * 4人制・ endless 回戦 (得点なし、まったり用)。
 * 固定: 献上 (下位→上位は最強札を自動、上位のお返しは手選び。
 * 大貧民→大富豪2枚・貧民→富豪1枚、大富豪→大貧民2枚・富豪→貧民1枚)、
 * 革命 (同一ランク4枚以上。8切りOFFでも8の4枚出しは革命だけ起きる)・
 * 反則上がり (最下位固定。J・8は対応ルールON時のみ対象。スペ3単騎・階段中8除外・
 * 反則2人目は1人目より上位)・階段 (同一スート連番3枚以上、固定の基本役)・
 * シックス/最強階段の即流し・スルーパス禁止・ブラインド2枚 (全員13枚)。
 * 切替可 (部屋主が開始前に設定、Jバックのみ既定OFF): 8切り・Jバック・
 * スートロック・スペ3抜き・都落ち。Jバックは連盟ルールにないローカル要素。
 * 詳細: イレブンバックはJが出た時その場限りで裏返す (単複不問。フォローでは剥がれず
 * 場が流れれば戻る。強弱・縛りに特権なし、階段は対象外)。スートロックは場と同一
 * スート (多重集合) で継続したときのみ成立 (リードでは付かない。ジョーカー含みでは
 * 新規発生せず、縛りスートのみなら維持)。スペ3抜きはジョーカー単騎を倒して
 * 流し・リード。都落ちは前大富豪が先頭で上がれなかった時点で即敗北・最下位固定
 * (反則者がいれば繰り上がる)。
 * 54枚 (ジョーカー2枚)。13枚ずつ配り、余り2枚は3♦保持者へ。
 * 初戦は3♦保持者、2戦目以降は前戦の大貧民がリード。
 */
public class DaifugoRoom {
    public enum State {
        LOBBY, PLAYING, ROUND_END, TRIBUTE
    }

    public enum PlayResult {
        OK, NOT_TURN, NOT_PLAYING, FINISHED, BAD_SELECT, BAD_COUNT, TOO_WEAK, SUIT_LOCK,
        NO_PASS_ON_LEAD, PASSED_OUT
    }

    public static class Seat {
        public UUID playerId;
        public String name = "";
        public int cpu = -1;
        public boolean connected = true;

        public boolean cpuControlled() {
            return cpu >= 0 || !connected;
        }

        public boolean isHuman() {
            return playerId != null;
        }

        public String displayName() {
            return name;
        }
    }

    public interface CpuChooser {
        CpuBrain.Play choose(int seat, CpuBrain.View view, List<Integer> hand);
    }

    public static final int SEATS = 4;
    public static final int CPU_DELAY = 30;
    public static final int ROUND_END_DELAY = 140;
    public static final int AFK_TICKS = 1200;
    /** お返しの選択猶予 (超過で最弱札を自動選択)。 */
    public static final int TRIBUTE_TIMEOUT = 600;
    /** 3♦のID。 */
    public static final int DIA_THREE_ID = 2 * 13 + 2;
    private static final int LOG_CAP = 8;

    public final UUID roomId = UUID.randomUUID();
    public State state = State.LOBBY;
    public final Seat[] seats = new Seat[SEATS];
    public int ownerSeat = -1;
    public int round = 1;

    public final List<List<Integer>> hands = new ArrayList<>(SEATS);
    public final int[] roundRank = new int[SEATS];
    public final boolean[] finished = new boolean[SEATS];
    public final boolean[] violated = new boolean[SEATS];
    /** 反則した順番 (finishOrder上の位置。後の反則ほど上位)。 */
    public final int[] violatedAt = new int[SEATS];
    /** スルーパス後の出場停止 (流れで解除)。 */
    public boolean[] passedOut = new boolean[SEATS];
    public final List<Integer> finishOrder = new ArrayList<>();
    /** 前戦の順位 (献上用)。 */
    public final int[] prevRank = new int[SEATS];
    /** 都落ちで強制最下位 (反則よりは上、通常上がりより下)。 */
    public final boolean[] miyakoForced = new boolean[SEATS];
    /** お返しの残り枚数 (0=なし/済み)。 */
    public final int[] tributeOwed = new int[SEATS];
    /** お返しの相手席 (-1=なし)。 */
    public final int[] tributeTarget = new int[SEATS];
    public final List<Integer> table = new ArrayList<>();
    public int tableSeat = -1;
    /** 場の役種 (階段=true)。フォローは同役種で受ける。 */
    public boolean tableStairs = false;
    public int turnSeat = -1;
    public boolean revolution = false;
    /** Jバック場 (裏返し中)。フォローでは剥がれず、流れでのみ解除。 */
    public boolean jbackActive = false;
    /** スートロック (素札スートの多重集合整列列。null=なし)。 */
    public List<Integer> lockSuits = null;
    // ===== ルール設定 (部屋主が開始前に変更可。Jバックのみ既定OFFで競技準拠) =====
    // 固定: 献上・革命 (同一ランク4枚以上)・反則上がり・階段・シックス/最強階段・
    // スルーパス禁止・ブラインド。切替可: 8切り・Jバック・
    // スートロック・スペ3抜き・都落ち。8切りOFFでも8の4枚出しは革命だけ起きる。
    public boolean ruleEightCut = true;
    public boolean ruleJBack = false;
    public boolean ruleSuitLock = true;
    public boolean ruleSpe3 = true;
    public boolean ruleMiyako = true;
    public int lastPlaySeat = -1;
    public int prevDaifugo = -1;
    public final List<DaifugoSnapshot.LogEntry> log = new ArrayList<>();

    private int cpuTimer = 0;
    private int stateTimer = 0;
    private int turnTimer = 0;
    private int tributeTimer = 0;

    public DaifugoRoom() {
        for (int i = 0; i < SEATS; i++) {
            hands.add(new ArrayList<>());
            roundRank[i] = -1;
            prevRank[i] = -1;
            violatedAt[i] = -1;
        }
    }

    // ===== メンバー =====

    public int seatOf(UUID playerId) {
        for (int i = 0; i < SEATS; i++) {
            if (seats[i] != null && playerId.equals(seats[i].playerId)) {
                return i;
            }
        }
        return -1;
    }

    public int humanCount() {
        int n = 0;
        for (Seat s : seats) {
            if (s != null && s.isHuman()) {
                n++;
            }
        }
        return n;
    }

    /** 空きがなければ -1。 */
    public int addMember(UUID playerId, String name) {
        int existing = seatOf(playerId);
        if (existing >= 0) {
            return existing;
        }
        for (int i = 0; i < SEATS; i++) {
            if (seats[i] == null) {
                Seat s = new Seat();
                s.playerId = playerId;
                s.name = name;
                seats[i] = s;
                if (ownerSeat < 0) {
                    ownerSeat = i;
                }
                addLog("log.bamboomod.daifugo_join", name);
                return i;
            }
        }
        return -1;
    }

    public int rejoin(UUID playerId) {
        int idx = seatOf(playerId);
        if (idx < 0) {
            return -1;
        }
        seats[idx].connected = true;
        addLog("log.bamboomod.daifugo_back", seats[idx].name);
        return idx;
    }

    /** ロビー退出。解散が必要なら true。 */
    public boolean removeLobbyMember(int idx) {
        addLog("log.bamboomod.daifugo_leave", seats[idx].name);
        seats[idx] = null;
        if (ownerSeat == idx) {
            ownerSeat = firstHumanSeat();
            if (ownerSeat < 0) {
                ownerSeat = firstOccupiedSeat();
            }
        }
        return humanCount() == 0;
    }

    /** 戦闘中退出。席は純粋CPU化。解散が必要なら true。 */
    public boolean leaveInGame(int idx) {
        addLog("log.bamboomod.daifugo_leave", seats[idx].name);
        seats[idx].playerId = null;
        seats[idx].cpu = CpuBrain.Personality.CREEPER.ordinal();
        seats[idx].connected = true;
        if (ownerSeat == idx) {
            ownerSeat = firstHumanSeat();
        }
        return humanCount() == 0;
    }

    public void setDisconnected(UUID playerId) {
        int idx = seatOf(playerId);
        if (idx < 0) {
            return;
        }
        seats[idx].connected = false;
        String cpuName = cpuName(CpuBrain.Personality.CREEPER);
        addLog("log.bamboomod.daifugo_cpu", seats[idx].name, cpuName);
    }

    private int firstHumanSeat() {
        for (int i = 0; i < SEATS; i++) {
            if (seats[i] != null && seats[i].isHuman()) {
                return i;
            }
        }
        return -1;
    }

    private int firstOccupiedSeat() {
        for (int i = 0; i < SEATS; i++) {
            if (seats[i] != null) {
                return i;
            }
        }
        return -1;
    }

    private static String cpuName(CpuBrain.Personality p) {
        return switch (p) {
            case ZOMBIE -> "zombie";
            case SKELETON -> "skeleton";
            case CREEPER -> "creeper";
            case ENDERMAN -> "enderman";
            case VILLAGER -> "villager";
        };
    }

    // ===== 進行 =====

    /** 空き席をランダムな distinct CPU で埋めて開始。 */
    public void startGame(Random random) {
        List<CpuBrain.Personality> pool = new ArrayList<>(List.of(CpuBrain.Personality.values()));
        Collections.shuffle(pool, random);
        int p = 0;
        for (int i = 0; i < SEATS; i++) {
            if (seats[i] == null) {
                Seat s = new Seat();
                s.cpu = pool.get(p++ % pool.size()).ordinal();
                s.name = cpuName(CpuBrain.Personality.values()[s.cpu]);
                seats[i] = s;
            }
        }
        if (ownerSeat < 0) {
            ownerSeat = 0;
        }
        round = 1;
        prevDaifugo = -1;
        for (int i = 0; i < SEATS; i++) {
            prevRank[i] = -1;
        }
        state = State.PLAYING;
        addLog("log.bamboomod.daifugo_start");
        startRound(random);
    }

    public void startRound(Random random) {
        List<Integer> deck = new ArrayList<>(DaifugoCard.DECK_SIZE);
        for (int i = 0; i < DaifugoCard.DECK_SIZE; i++) {
            deck.add(i);
        }
        Collections.shuffle(deck, random);
        // ブラインド2枚 (ジョーカー以外) を除き、13枚ずつ配る (連盟 §10・§80)。
        List<Integer> rest = new ArrayList<>(DaifugoCard.DECK_SIZE);
        int blind = 0;
        for (int id : deck) {
            if (blind < 2 && id < DaifugoCard.JOKER_A_ID) {
                blind++;
                continue;
            }
            rest.add(id);
        }
        for (int i = 0; i < SEATS; i++) {
            hands.get(i).clear();
            roundRank[i] = -1;
            finished[i] = false;
            violated[i] = false;
            violatedAt[i] = -1;
            miyakoForced[i] = false;
        }
        finishOrder.clear();
        table.clear();
        tableStairs = false;
        revolution = false;
        jbackActive = false;
        lockSuits = null;
        passedOut = new boolean[SEATS];
        tableSeat = -1;
        lastPlaySeat = -1;
        cpuTimer = 0;
        turnTimer = 0;
        for (int i = 0; i < SEATS; i++) {
            for (int k = 0; k < 13; k++) {
                hands.get(i).add(rest.get(i * 13 + k));
            }
        }
        sortHands();
        tributeTimer = 0;
        for (int i = 0; i < SEATS; i++) {
            tributeOwed[i] = 0;
            tributeTarget[i] = -1;
        }
        // 2戦目以降は献上 (前戦の順位で)。下位→上位は自動、上位のお返しは選択。
        if (round >= 2) {
            tributeIn();
        }
        if (anyTributeOwed()) {
            state = State.TRIBUTE;
            return;
        }
        finalizeRound();
    }

    /** 配札確定 (整列・開始者・PLAYING移行)。 */
    private void finalizeRound() {
        sortHands();
        // 2戦目以降は前戦の大貧民から開始
        int starter = 0;
        if (round >= 2) {
            int daihinmin = seatByPrevRank(SEATS - 1);
            if (daihinmin >= 0) {
                starter = daihinmin;
            } else {
                starter = leadSeat();
            }
        } else {
            starter = leadSeat();
        }
        turnSeat = starter;
        state = State.PLAYING;
        addLog("log.bamboomod.daifugo_roundstart", String.valueOf(round), seats[starter].displayName());
    }

    /**
     * 初戦の開始者。3♦保持者だが、ブラインドで除外されることがあるため
     * 3♣→3♥→3♠→0席の順でフォールバックする。
     */
    private int leadSeat() {
        int[] cands = {DIA_THREE_ID, 3 * 13 + 2, 1 * 13 + 2, 0 * 13 + 2};
        for (int id : cands) {
            for (int i = 0; i < SEATS; i++) {
                if (hands.get(i).contains(id)) {
                    return i;
                }
            }
        }
        return 0;
    }

    private void sortHands() {
        for (int i = 0; i < SEATS; i++) {
            hands.get(i).sort(
                    Comparator.comparingInt(id -> DaifugoRules.sortKey(DaifugoCard.fromId(id))));
        }
    }

    /**
     * 献上。下位→上位は最強札を自動移動し、上位のお返しを owed として積む。
     * 大貧民→大富豪2枚・貧民→富豪1枚、大富豪→大貧民2枚・富豪→貧民1枚。
     * 強弱は通常序列 (ジョーカー最強)。
     */
    private void tributeIn() {
        int daifugo = seatByPrevRank(0);
        int fugo = seatByPrevRank(1);
        int hinmin = seatByPrevRank(2);
        int daihinmin = seatByPrevRank(3);
        if (daifugo < 0 || fugo < 0 || hinmin < 0 || daihinmin < 0) {
            return;
        }
        moveStrongest(daihinmin, daifugo, 2);
        addLog("log.bamboomod.daifugo_tribute", seats[daihinmin].displayName(),
                seats[daifugo].displayName(), "2");
        moveStrongest(hinmin, fugo, 1);
        addLog("log.bamboomod.daifugo_tribute", seats[hinmin].displayName(),
                seats[fugo].displayName(), "1");
        tributeOwed[daifugo] = 2;
        tributeTarget[daifugo] = daihinmin;
        tributeOwed[fugo] = 1;
        tributeTarget[fugo] = hinmin;
    }

    private boolean anyTributeOwed() {
        for (int owed : tributeOwed) {
            if (owed > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * お返しの確定。枚数一致・手札内なら相手席へ移し、全員分揃えば配札確定。
     * 成功時 true (Manager が配信する)。
     */
    public boolean completeGiveback(int seat, List<Integer> ids) {
        if (state != State.TRIBUTE || tributeOwed[seat] <= 0) {
            return false;
        }
        if (ids.size() != tributeOwed[seat]) {
            return false;
        }
        List<Integer> hand = hands.get(seat);
        if (!hand.containsAll(ids) || ids.size() != new java.util.HashSet<>(ids).size()) {
            return false;
        }
        ids = new ArrayList<>(ids);
        int target = tributeTarget[seat];
        hand.removeAll(ids);
        hands.get(target).addAll(ids);
        tributeOwed[seat] = 0;
        tributeTarget[seat] = -1;
        addLog("log.bamboomod.daifugo_giveback", seats[seat].displayName(),
                seats[target].displayName(), String.valueOf(ids.size()));
        if (!anyTributeOwed()) {
            finalizeRound();
        }
        return true;
    }

    /** お返しの自動選択 (最弱札)。 */
    private void autoGiveback(int seat) {
        List<Integer> hand = hands.get(seat);
        hand.sort((x, y) -> Integer.compare(cardPower(x), cardPower(y)));
        completeGiveback(seat, new ArrayList<>(hand.subList(0, tributeOwed[seat])));
    }

    private int seatByPrevRank(int rank) {
        for (int i = 0; i < SEATS; i++) {
            if (prevRank[i] == rank) {
                return i;
            }
        }
        return -1;
    }

    private void moveStrongest(int from, int to, int count) {
        List<Integer> hand = hands.get(from);
        hand.sort((x, y) -> Integer.compare(cardPower(y), cardPower(x)));
        for (int i = 0; i < count && !hand.isEmpty(); i++) {
            hands.get(to).add(hand.remove(0));
        }
    }

    private static int cardPower(int id) {
        return DaifugoRules.power(DaifugoCard.fromId(id).number(), false);
    }

    private int activeCount() {
        int n = 0;
        for (boolean f : finished) {
            if (!f) {
                n++;
            }
        }
        return n;
    }

    private int nextActive(int from) {
        for (int k = 1; k <= SEATS; k++) {
            int i = (from + k) % SEATS;
            // 上がり済みとスルーパス後の出場停止は飛ばす
            if (!finished[i] && !passedOut[i]) {
                return i;
            }
        }
        return from;
    }

    // ===== 着手 =====

    /**
     * ルール設定の変更。開始前ロビーで部屋主のみ可。成功時 true。
     */
    public boolean setRules(UUID senderId, boolean eightCut, boolean jback,
            boolean suitLock, boolean spe3, boolean miyako) {
        if (state != State.LOBBY || senderId == null) {
            return false;
        }
        if (seatOf(senderId) != ownerSeat) {
            return false;
        }
        ruleEightCut = eightCut;
        ruleJBack = jback;
        ruleSuitLock = suitLock;
        ruleSpe3 = spe3;
        ruleMiyako = miyako;
        return true;
    }

    public PlayResult playCards(int seat, List<Integer> ids) {
        return playCards(seat, ids, false);
    }

    /**
     * 着手。declaredStairs は [X,JK,JK] リード時の宣言 (true=階段。連盟 §21)。
     * フォロー時は場の役種で自動確定するため無視される。
     */
    public PlayResult playCards(int seat, List<Integer> ids, boolean declaredStairs) {
        if (state != State.PLAYING) {
            return PlayResult.NOT_PLAYING;
        }
        if (seat != turnSeat || finished[seat]) {
            return PlayResult.NOT_TURN;
        }
        // 呼び出し側のリストと手札のエイリアス対策 (subList等)
        ids = new ArrayList<>(ids);
        if (ids.isEmpty()) {
            if (table.isEmpty()) {
                return PlayResult.NO_PASS_ON_LEAD;
            }
            doPass(seat);
            return PlayResult.OK;
        }
        // スルーパス禁止: パス済みは場が流れるまで出せない (連盟 §16)。
        if (!table.isEmpty() && passedOut[seat]) {
            return PlayResult.PASSED_OUT;
        }
        List<Integer> hand = hands.get(seat);
        if (!hand.containsAll(ids) || ids.size() != new java.util.HashSet<>(ids).size()) {
            return PlayResult.BAD_SELECT;
        }
        List<DaifugoCard> play = new ArrayList<>(ids.size());
        for (int id : ids) {
            play.add(DaifugoCard.fromId(id));
        }
        boolean validSet = DaifugoRules.isValidSet(play);
        boolean stairsShape = DaifugoRules.isStairs(play);
        if (!validSet && !stairsShape) {
            return PlayResult.BAD_SELECT;
        }
        List<DaifugoCard> tableCards = new ArrayList<>(table.size());
        for (int id : table) {
            tableCards.add(DaifugoCard.fromId(id));
        }
        // Jバックに縛りの特権なし。場と同一多重集合でなければ縛り拒否。
        // 比べる序列は現在の実効序列 (Jバック場なら裏返し中)。
        boolean eff = DaifugoRules.effectiveRevolution(jbackActive, revolution);
        final boolean stairsPlay;
        if (table.isEmpty()) {
            // 両読み ([X,JK,JK]) は宣言で確定。それ以外は形で自動確定。
            if (validSet && stairsShape) {
                stairsPlay = declaredStairs;
            } else {
                stairsPlay = stairsShape;
            }
        } else {
            if (play.size() != table.size()) {
                return PlayResult.BAD_COUNT;
            }
            // フォローは場と同役種 (連盟 §25)。両読み札も場の役種で読む。
            stairsPlay = tableStairs;
            if (!DaifugoRules.beats(tableCards, play, eff, ruleSpe3, tableStairs,
                    stairsPlay)) {
                return PlayResult.TOO_WEAK;
            }
            if (!DaifugoRules.satisfiesLock(play, lockSuits)) {
                return PlayResult.SUIT_LOCK;
            }
        }
        boolean wasEmpty = table.isEmpty();
        List<Integer> fieldSuits = DaifugoRules.plainSuits(tableCards);
        hand.removeAll(ids);
        boolean spe3 = ruleSpe3 && table.size() == 1 && table.get(0) >= DaifugoCard.JOKER_A_ID
                && play.size() == 1 && play.get(0).spadeThree();
        if (spe3) {
            addLog("log.bamboomod.daifugo_spe3", seats[seat].displayName());
        }
        // 反則上がり (出し切り + 役札) は最下位固定。場は変わらない。
        // 最強札は実効序列で判定 (革命中の2上がりは適法)。
        // J・8は対応ルールON時のみ反則 (OFFなら通常札)。スペ3単騎・階段中8除外。
        if (hand.isEmpty() && DaifugoRules.isViolationFinish(play, eff, ruleEightCut,
                ruleJBack, ruleSpe3, stairsPlay)) {
            violated[seat] = true;
            violatedAt[seat] = finishOrder.size();
            addLog("log.bamboomod.daifugo_violation", seats[seat].displayName());
            finishSeat(seat);
            if (state == State.PLAYING) {
                turnTimer = 0;
                if (!checkOutFlow()) {
                    turnSeat = nextActive(seat);
                }
            }
            return PlayResult.OK;
        }
        table.clear();
        table.addAll(ids);
        tableStairs = stairsPlay;
        tableSeat = seat;
        lastPlaySeat = seat;
        turnTimer = 0;
        // 縛りは場にカードがあるときのみ成立。リードでは付かない。
        // ジョーカー含みでは新規発生しない (連盟 §57補足)。
        // 既存の縛りはフォローが充足した時点で維持される (場流れでのみ解除)。
        List<Integer> playSuits = DaifugoRules.plainSuits(play);
        if (!ruleSuitLock || wasEmpty) {
            lockSuits = null;
        } else if (lockSuits == null && DaifugoRules.jokerCount(play) == 0
                && !fieldSuits.isEmpty() && !playSuits.isEmpty()
                && DaifugoRules.lockMatch(fieldSuits, playSuits, 0)) {
            lockSuits = new ArrayList<>(fieldSuits);
        }
        if (hand.isEmpty()) {
            finishSeat(seat);
            if (state == State.PLAYING) {
                turnTimer = 0;
                if (!checkOutFlow()) {
                    turnSeat = nextActive(seat);
                }
            }
            return PlayResult.OK;
        }
        // スペ3抜き: 場を流してリードを取り直す (Jバックも解除)
        if (spe3) {
            table.clear();
            tableStairs = false;
            tableSeat = -1;
            lockSuits = null;
            jbackActive = false;
            passedOut = new boolean[SEATS];
            return PlayResult.OK;
        }
        // シックスカード: 革命も起こして即流し (連盟 §22条の2・§48)
        if (DaifugoRules.isSixCard(play)) {
            revolution = !revolution;
            addLog("log.bamboomod.daifugo_six", seats[seat].displayName());
            addLog("log.bamboomod.daifugo_revolution", seats[seat].displayName());
            table.clear();
            tableStairs = false;
            tableSeat = -1;
            lockSuits = null;
            jbackActive = false;
            passedOut = new boolean[SEATS];
            return PlayResult.OK;
        }
        // 最強階段: 即流し (連盟 §38条の2)。革命状態は変わらない。
        if (DaifugoRules.isSuperStairs(play, eff)) {
            addLog("log.bamboomod.daifugo_superstairs", seats[seat].displayName());
            table.clear();
            tableStairs = false;
            tableSeat = -1;
            lockSuits = null;
            jbackActive = false;
            passedOut = new boolean[SEATS];
            return PlayResult.OK;
        }
        // 8切り: 階段以外の8のみ構成 (JK代用を含む)。4枚以上は革命も起こる。
        if (ruleEightCut && !stairsPlay
                && DaifugoRules.effectiveRank(play) == TrumpRank.EIGHT.ordinal()) {
            table.clear();
            tableStairs = false;
            tableSeat = -1;
            lockSuits = null;
            jbackActive = false;
            passedOut = new boolean[SEATS];
            addLog("log.bamboomod.daifugo_cut", seats[seat].displayName());
            if (DaifugoRules.isRevolution(play)) {
                revolution = !revolution;
                addLog("log.bamboomod.daifugo_revolution", seats[seat].displayName());
            }
            return PlayResult.OK;
        }
        // Jバック: J含み手が出たら、その場が流れるまで裏返し継続 (単複・方向不問)。
        // 強弱・縛りに特権なし。フォローでは剥がれない。OFFならJは通常札。
        // 階段は対象外 (連盟外ローカル)。
        if (ruleJBack && !stairsPlay
                && DaifugoRules.effectiveRank(play) == TrumpRank.JACK.ordinal()
                && !jbackActive) {
            jbackActive = true;
            addLog("log.bamboomod.daifugo_jback", seats[seat].displayName());
        }
        // 革命は同一ランク4枚以上 (階段を除く)。
        if (DaifugoRules.isRevolution(play)) {
            revolution = !revolution;
            addLog("log.bamboomod.daifugo_revolution", seats[seat].displayName());
        }
        if (!checkOutFlow()) {
            turnSeat = nextActive(seat);
        }
        return PlayResult.OK;
    }

    private void doPass(int seat) {
        // スルーパス禁止: パスした者は場が流れるまで出場停止 (連盟 §16)。
        passedOut[seat] = true;
        addLog("log.bamboomod.daifugo_pass", seats[seat].displayName());
        if (checkOutFlow()) {
            return;
        }
        turnSeat = nextActive(seat);
        turnTimer = 0;
    }

    /**
     * 即流し判定。リード以外の未上がり者が全員パス済みなら場を流す (連盟 §17)。
     * 流したら true (turnSeat は再設定済み)。
     */
    private boolean checkOutFlow() {
        if (table.isEmpty()) {
            return false;
        }
        for (int i = 0; i < SEATS; i++) {
            if (!finished[i] && i != lastPlaySeat && !passedOut[i]) {
                return false;
            }
        }
        flowField();
        return true;
    }

    /** 場流し。場を空けて直前のリードに先手を与える (連盟 §17-18)。 */
    private void flowField() {
        clearField(!finished[lastPlaySeat] ? lastPlaySeat : nextActive(lastPlaySeat));
        addLog("log.bamboomod.daifugo_flow");
    }

    /** 場のクリア (切断・特殊流し共通)。先手は指定席。 */
    private void clearField(int starter) {
        table.clear();
        tableStairs = false;
        tableSeat = -1;
        lockSuits = null;
        jbackActive = false;
        passedOut = new boolean[SEATS];
        turnSeat = starter;
        turnTimer = 0;
    }

    private void finishSeat(int seat) {
        finished[seat] = true;
        finishOrder.add(seat);
        addLog("log.bamboomod.daifugo_finish", seats[seat].displayName(),
                String.valueOf(finishOrder.size()));
        // 都落ち: 前大富豪が先頭で上がれなかった場合、その場で敗北・最下位固定 (OFFなら通常進行)
        if (ruleMiyako && finishOrder.size() == 1 && prevDaifugo >= 0 && seat != prevDaifugo
                && !finished[prevDaifugo]) {
            forceMiyako(prevDaifugo);
        }
        if (finishOrder.size() == SEATS - 1) {
            for (int i = 0; i < SEATS; i++) {
                if (!finished[i]) {
                    finished[i] = true;
                    finishOrder.add(i);
                    break;
                }
            }
            endRound();
        }
    }

    /** 都落ちの強制敗北。残り手札を破棄して即脱落、最下位に固定する。 */
    private void forceMiyako(int seat) {
        finished[seat] = true;
        miyakoForced[seat] = true;
        hands.get(seat).clear();
        // 順位表に積んでおく (残り1人になってもソロ消化なしで閉幕する)
        finishOrder.add(seat);
        addLog("log.bamboomod.daifugo_miyako", seats[seat].displayName());
    }

    private void endRound() {
        // 脱落済みで未登録 (都落ち強制など) があれば末尾に補う
        for (int i = 0; i < SEATS; i++) {
            if (finished[i] && !finishOrder.contains(i)) {
                finishOrder.add(i);
            }
        }
        List<Integer> ordered = new ArrayList<>(finishOrder);
        // 通常 < 都落ち < 反則。反則が複数なら後の反則ほど上位
        // (最初が最下位・2番目が貧民。連盟 §62)。
        ordered.sort((a, b) -> {
            int ga = violated[a] ? 2 : miyakoForced[a] ? 1 : 0;
            int gb = violated[b] ? 2 : miyakoForced[b] ? 1 : 0;
            if (ga != gb) {
                return ga - gb;
            }
            if (ga == 2) {
                return violatedAt[b] - violatedAt[a];
            }
            return 0;
        });
        for (int i = 0; i < ordered.size(); i++) {
            int s = ordered.get(i);
            roundRank[s] = i;
            prevRank[s] = i;
        }
        prevDaifugo = ordered.get(0);
        addLog("log.bamboomod.daifugo_roundend", String.valueOf(round));
        state = State.ROUND_END;
        stateTimer = 0;
    }

    // ===== tick (CPU・遷移・AFK) =====

    /** 状態が変わったら true (Manager が配信する)。 */
    public boolean tick(Random random, CpuChooser chooser) {
        if (state == State.PLAYING) {
            if (finished[turnSeat]) {
                turnSeat = nextActive(turnSeat);
                return true;
            }
            Seat s = seats[turnSeat];
            if (s.cpuControlled()) {
                cpuTimer++;
                if (cpuTimer >= CPU_DELAY) {
                    cpuTimer = 0;
                    CpuBrain.Personality personality = s.cpu >= 0
                            ? CpuBrain.Personality.values()[s.cpu]
                            : CpuBrain.Personality.CREEPER;
                    CpuBrain.View view = new CpuBrain.View(List.copyOf(table), revolution,
                            jbackActive,
                            lockSuits == null ? List.of() : List.copyOf(lockSuits),
                            handCounts(), roundRank.clone(), round,
                            ruleEightCut, ruleJBack, ruleSpe3, tableStairs);
                    CpuBrain.Play play = chooser.choose(turnSeat, view,
                            List.copyOf(hands.get(turnSeat)));
                    if (playCards(turnSeat, play.ids(), play.stairs()) != PlayResult.OK) {
                        if (table.isEmpty()) {
                            playCards(turnSeat, List.of(lowestSingle()), false);
                        } else {
                            doPass(turnSeat);
                        }
                    }
                    return true;
                }
                return false;
            }
            turnTimer++;
            if (turnTimer >= AFK_TICKS) {
                turnTimer = 0;
                if (table.isEmpty()) {
                    playCards(turnSeat, List.of(lowestSingle()));
                } else {
                    doPass(turnSeat);
                }
                return true;
            }
            return false;
        }
        if (state == State.ROUND_END) {
            stateTimer++;
            if (stateTimer >= ROUND_END_DELAY) {
                stateTimer = 0;
                round++;
                startRound(random);
                return true;
            }
        }
        if (state == State.TRIBUTE) {
            tributeTimer++;
            boolean acted = false;
            if (tributeTimer >= CPU_DELAY * 2) {
                for (int i = 0; i < SEATS; i++) {
                    if (tributeOwed[i] > 0 && seats[i] != null && seats[i].cpuControlled()) {
                        autoGiveback(i);
                        acted = true;
                    }
                }
            }
            if (tributeTimer >= TRIBUTE_TIMEOUT) {
                for (int i = 0; i < SEATS; i++) {
                    if (tributeOwed[i] > 0) {
                        autoGiveback(i);
                        acted = true;
                    }
                }
            }
            return acted;
        }
        return false;
    }

    private int[] handCounts() {
        int[] counts = new int[SEATS];
        for (int i = 0; i < SEATS; i++) {
            counts[i] = hands.get(i).size();
        }
        return counts;
    }

    private int lowestSingle() {
        List<Integer> hand = hands.get(turnSeat);
        int best = hand.get(0);
        int bestKey = Integer.MAX_VALUE;
        for (int id : hand) {
            int k = DaifugoRules.sortKey(DaifugoCard.fromId(id));
            if (k < bestKey) {
                bestKey = k;
                best = id;
            }
        }
        return best;
    }

    // ===== 配信 =====

    public DaifugoSnapshot snapshotFor(int seat) {
        DaifugoSnapshot snap = new DaifugoSnapshot();
        snap.roomId = roomId;
        snap.state = state.ordinal();
        snap.round = round;
        snap.mySeat = seat;
        snap.ownerSeat = ownerSeat;
        List<DaifugoSnapshot.SeatView> views = new ArrayList<>(SEATS);
        for (int i = 0; i < SEATS; i++) {
            Seat s = seats[i];
            if (s == null) {
                views.add(null);
            } else {
                views.add(new DaifugoSnapshot.SeatView(s.displayName(), s.cpu, s.connected,
                        hands.get(i).size(), roundRank[i], prevRank[i]));
            }
        }
        snap.seats = views;
        snap.hand = List.copyOf(hands.get(seat));
        snap.table = List.copyOf(table);
        snap.tableSeat = tableSeat;
        snap.tableStairs = tableStairs;
        snap.turnSeat = turnSeat;
        List<Integer> out = new ArrayList<>(SEATS);
        for (boolean p : passedOut) {
            out.add(p ? 1 : 0);
        }
        snap.passedOut = out;
        snap.revolution = revolution;
        snap.lockSuits = lockSuits == null ? List.of() : List.copyOf(lockSuits);
        snap.jback = jbackActive;
        snap.ruleEightCut = ruleEightCut;
        snap.ruleJBack = ruleJBack;
        snap.ruleSuitLock = ruleSuitLock;
        snap.ruleSpe3 = ruleSpe3;
        snap.ruleMiyako = ruleMiyako;
        snap.log = List.copyOf(log);
        List<Integer> owed = new ArrayList<>(SEATS);
        for (int owedCount : tributeOwed) {
            owed.add(owedCount);
        }
        snap.tributeOwed = owed;
        return snap;
    }

    private void addLog(String key, String... args) {
        log.add(new DaifugoSnapshot.LogEntry(key, List.of(args)));
        while (log.size() > LOG_CAP) {
            log.remove(0);
        }
    }
}
