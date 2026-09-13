package ruby.bamboo.client.gui.daifugo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import ruby.bamboo.client.gui.ChatOverlay;
import ruby.bamboo.client.gui.trump.TrumpCardRenderer;
import ruby.bamboo.client.gui.trump.TrumpRank;
import ruby.bamboo.client.gui.trump.TrumpSuit;
import ruby.bamboo.daifugo.DaifugoCard;
import ruby.bamboo.daifugo.DaifugoRoom;
import ruby.bamboo.daifugo.DaifugoRules;
import ruby.bamboo.daifugo.DaifugoSnapshot;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.DaifugoActionPacket;
import ruby.bamboo.network.DaifugoLeavePacket;
import ruby.bamboo.network.DaifugoTributePacket;

/**
 * 大富豪の対戦画面。
 * 自手札 (クリック選択)・場・他席・得点・ログを表示し、出す/パスを送る。
 * 結果表示 (ROUND_END) もこの画面で行う。
 */
public class DaifugoGameScreen extends Screen {
    private static final int CARD_W = 36;
    private static final int CARD_H = TrumpCardRenderer.heightFor(CARD_W);
    private static final int HAND_PITCH = 22;
    private static final int TABLE_PITCH = 40;
    private static final int SELECT_UP = 8;
    /** カードが飛んでくる演出の長さ (tick)。 */
    private static final int FLY_LEN = 12;
    /** 特殊流し演出の長さ (tick)。 */
    private static final int FX_LEN = 60;

    private DaifugoSnapshot snapshot;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private final List<int[]> hitRects = new ArrayList<>();
    private int flyTicks = 0;
    private int flySeat = -1;
    /** 飛来中に下に残す旧場札 (重なってから消える)。 */
    private List<Integer> prevTableCards = List.of();
    private String lastFxKey = "";
    private int fxTicks = 0;

    private Button playButton;
    private Button passButton;
    private Button leaveButton;
    private Button tributeButton;
    private Button declareTripleButton;
    private Button declareStairsButton;
    /** 共通チャットオーバーレイ (Tで開く。画面は切り替えない)。 */
    private final ChatOverlay chat = new ChatOverlay();

    public DaifugoGameScreen(DaifugoSnapshot snapshot) {
        super(Component.translatable("screen.bamboomod.daifugo_game"));
        this.snapshot = snapshot;
        this.lastFxKey = snapshot.fxKey;
    }

    public void update(DaifugoSnapshot snapshot) {
        DaifugoSnapshot old = this.snapshot;
        boolean tableChanged = !snapshot.table.equals(old.table);
        if (!snapshot.fxKey.isEmpty() && !snapshot.fxKey.equals(lastFxKey)) {
            // 特殊流し (8切り/スペ3/シックス/最強階段/流れ): 札と見出しを残す
            lastFxKey = snapshot.fxKey;
            fxTicks = FX_LEN;
            playFxSound(snapshot.fxKey);
        } else if (tableChanged && !snapshot.table.isEmpty()) {
            // 通常の出し: 旧場札を残したまま、出した席から場へ飛んでくる
            prevTableCards = new ArrayList<>(old.table);
            flyTicks = FLY_LEN;
            flySeat = snapshot.tableSeat;
            playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F);
        } else if (tableChanged) {
            prevTableCards = List.of();
        }
        if (snapshot.fxKey.isEmpty()) {
            lastFxKey = "";
        }
        if (snapshot.revolution != old.revolution) {
            playSound(SoundEvents.PLAYER_LEVELUP, 0.9F, 0.7F);
        }
        // 反則上がりは場が変わらないためログで検出する
        for (DaifugoSnapshot.LogEntry e : snapshot.log) {
            if (e.key().contains("violation") && !containsLog(old.log, e)) {
                playSound(SoundEvents.VILLAGER_NO, 1.0F, 0.9F);
                break;
            }
        }
        this.snapshot = snapshot;
        selected.retainAll(snapshot.hand);
    }

    private static boolean containsLog(List<DaifugoSnapshot.LogEntry> log,
            DaifugoSnapshot.LogEntry e) {
        for (DaifugoSnapshot.LogEntry o : log) {
            if (o.key().equals(e.key()) && o.args().equals(e.args())) {
                return true;
            }
        }
        return false;
    }

    private void playSound(SoundEvent sound, float pitch, float volume) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }

    private void playFxSound(String key) {
        switch (key) {
            case "cut" -> playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.1F, 0.8F);
            case "spe3" -> playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.9F);
            case "six", "superstairs" ->
                    playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F);
            default -> playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.5F);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isRoundEnd() {
        return snapshot.state == DaifugoRoom.State.ROUND_END.ordinal();
    }

    private boolean isTribute() {
        return snapshot.state == DaifugoRoom.State.TRIBUTE.ordinal();
    }

    private boolean isPlaying() {
        return snapshot.state == DaifugoRoom.State.PLAYING.ordinal();
    }

    /** 自分のお返し残り枚数。 */
    private int myOwed() {
        if (snapshot.mySeat < 0 || snapshot.mySeat >= snapshot.tributeOwed.size()) {
            return 0;
        }
        return snapshot.tributeOwed.get(snapshot.mySeat);
    }

    private boolean myTurn() {
        if (snapshot.state != DaifugoRoom.State.PLAYING.ordinal()) {
            return false;
        }
        DaifugoSnapshot.SeatView me = seat(snapshot.mySeat);
        return snapshot.turnSeat == snapshot.mySeat && me != null && me.roundRank() < 0
                && !isPassedOut(snapshot.mySeat);
    }

    /** スルーパス後の出場停止 (流れ待ち)。 */
    private boolean isPassedOut(int idx) {
        return idx >= 0 && idx < snapshot.passedOut.size()
                && snapshot.passedOut.get(idx) != 0;
    }

    /** [X,JK,JK] のリード選択中 (3枚組/階段の宣言がいる)。 */
    private boolean isDualLeadSelected() {
        if (!myTurn() || !snapshot.table.isEmpty() || selected.size() != 3) {
            return false;
        }
        List<DaifugoCard> cards = new ArrayList<>(3);
        for (int id : selected) {
            cards.add(DaifugoCard.fromId(id));
        }
        return DaifugoRules.isValidSet(cards) && DaifugoRules.isStairs(cards);
    }

    private DaifugoSnapshot.SeatView seat(int idx) {
        return idx >= 0 && idx < snapshot.seats.size() ? snapshot.seats.get(idx) : null;
    }

    /** tick残りを秒表示 (切り上げ)。 */
    private static int ceilSec(int ticks) {
        return (ticks + 19) / 20;
    }

    private String timeoutText(int ticks) {
        return Component.translatable("screen.bamboomod.daifugo_timeout",
                String.valueOf(ceilSec(ticks))).getString();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int w = this.width;
        int h = this.height;
        playButton = Button.builder(Component.translatable("screen.bamboomod.daifugo_play"),
                b -> BambooNetwork.CHANNEL.sendToServer(new DaifugoActionPacket(new ArrayList<>(selected))))
                .bounds(w - 180, h - 28, 76, 20)
                .build();
        passButton = Button.builder(Component.translatable("screen.bamboomod.daifugo_pass"),
                b -> BambooNetwork.CHANNEL.sendToServer(new DaifugoActionPacket(List.of())))
                .bounds(w - 98, h - 28, 62, 20)
                .build();
        leaveButton = Button.builder(Component.translatable("screen.bamboomod.daifugo_leave"),
                b -> {
                    BambooNetwork.CHANNEL.sendToServer(new DaifugoLeavePacket());
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(null);
                    }
                })
                .bounds(w - 68, 4, 60, 20)
                .build();
        tributeButton = Button.builder(Component.translatable("screen.bamboomod.daifugo_tribute_confirm"),
                b -> BambooNetwork.CHANNEL.sendToServer(new DaifugoTributePacket(new ArrayList<>(selected))))
                .bounds(w / 2 - 75, h / 2 - 44, 150, 20)
                .build();
        declareTripleButton = Button.builder(
                Component.translatable("screen.bamboomod.daifugo_declare_triple"),
                b -> BambooNetwork.CHANNEL.sendToServer(
                        new DaifugoActionPacket(new ArrayList<>(selected), false)))
                .bounds(w - 260, h - 52, 120, 20)
                .build();
        declareStairsButton = Button.builder(
                Component.translatable("screen.bamboomod.daifugo_declare_stairs"),
                b -> BambooNetwork.CHANNEL.sendToServer(
                        new DaifugoActionPacket(new ArrayList<>(selected), true)))
                .bounds(w - 134, h - 52, 120, 20)
                .build();
        this.addRenderableWidget(playButton);
        this.addRenderableWidget(passButton);
        this.addRenderableWidget(leaveButton);
        this.addRenderableWidget(tributeButton);
        this.addRenderableWidget(declareTripleButton);
        this.addRenderableWidget(declareStairsButton);
        this.addRenderableWidget(chat.attach(this.font, w, h));
        if (chat.isOpen()) {
            this.setFocused(chat.box());
            chat.box().setFocused(true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (flyTicks > 0) {
            flyTicks--;
            if (flyTicks == 0) {
                // 飛来完了で旧場札を消す (重なってから消える)
                prevTableCards = List.of();
            }
        }
        if (fxTicks > 0) {
            fxTicks--;
        }
        // チャット入力中は下段ボタンを隠す (入力欄と被るため)
        playButton.visible = isPlaying() && !chat.isOpen();
        passButton.visible = isPlaying() && !chat.isOpen();
        playButton.active = myTurn() && !selected.isEmpty();
        passButton.active = myTurn() && !snapshot.table.isEmpty();
        declareTripleButton.visible = isDualLeadSelected() && !chat.isOpen();
        declareStairsButton.visible = isDualLeadSelected() && !chat.isOpen();
        tributeButton.visible = isTribute() && myOwed() > 0;
        tributeButton.active = selected.size() == myOwed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (chat.keyPressed(this, keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (chat.charTyped(c, modifiers)) {
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // チャット入力中は札選択しない (入力欄への click は上部で処理済み)
        if (chat.isOpen() || button != 0 || isRoundEnd()) {
            return false;
        }
        int x = (int) mouseX;
        int y = (int) mouseY;
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            int[] r = hitRects.get(i);
            if (x >= r[1] && x < r[1] + r[3] && y >= r[2] && y < r[2] + r[4]) {
                if (!selected.remove(r[0])) {
                    selected.add(r[0]);
                }
                return true;
            }
        }
        return false;
    }

    // ===== 描画 =====

    @Override
    public void renderBackground(GuiGraphics gfx) {
        // 革命中はマットを赤っぽくする
        gfx.fill(0, 0, this.width, this.height,
                snapshot.revolution ? 0xFF5A1A12 : 0xFF0B3D2C);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        String head = this.title.getString() + "  "
                + Component.translatable("screen.bamboomod.daifugo_round",
                        String.valueOf(snapshot.round)).getString();
        if (snapshot.revolution) {
            head += "  " + Component.translatable("screen.bamboomod.daifugo_revolution").getString();
        }
        if (snapshot.jback) {
            head += "  " + Component.translatable("screen.bamboomod.daifugo_jback").getString();
        }
        if (snapshot.tableStairs && !snapshot.table.isEmpty()) {
            head += "  " + Component.translatable("screen.bamboomod.daifugo_stairs").getString();
        }
        if (!snapshot.lockSuits.isEmpty()) {
            StringBuilder badge = new StringBuilder("  ");
            for (int suit : snapshot.lockSuits) {
                if (suit >= 0 && suit < TrumpSuit.values().length) {
                    badge.append(TrumpSuit.values()[suit].glyph());
                }
            }
            badge.append(Component.translatable("screen.bamboomod.daifugo_lock").getString());
            head += badge.toString();
        }
        gfx.drawString(this.font, head, 8, 6, 0xFFFFE9B0, false);

        drawOthers(gfx);
        drawTable(gfx);
        drawFx(gfx);
        drawLog(gfx);
        chat.renderLog(gfx, this.font, this.width, this.height);

        if (myTurn()) {
            gfx.drawCenteredString(this.font,
                    Component.translatable("screen.bamboomod.daifugo_turn").getString(),
                    this.width / 2, this.height - 92, 0xFFFFE08A);
            if (snapshot.turnLimit >= 0) {
                gfx.drawCenteredString(this.font, timeoutText(snapshot.turnLimit),
                        this.width / 2, this.height - 81, 0xFFB9C4A8);
            }
        }
        if (isPlaying() && isPassedOut(snapshot.mySeat)
                && seat(snapshot.mySeat) != null
                && seat(snapshot.mySeat).roundRank() < 0) {
            gfx.drawCenteredString(this.font,
                    Component.translatable("screen.bamboomod.daifugo_passedout_wait").getString(),
                    this.width / 2, this.height - 92, 0xFFB9C4A8);
        }
        if (isRoundEnd()) {
            drawRoundEnd(gfx);
        }
        if (isTribute()) {
            // お返し選択時は手札に暗いマスクをかけないよう、先に暗転する
            drawTribute(gfx);
        }
        drawHand(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawOthers(GuiGraphics gfx) {
        List<Integer> others = new ArrayList<>(4);
        for (int i = 0; i < DaifugoRoom.SEATS; i++) {
            if (i != snapshot.mySeat && seat(i) != null) {
                others.add(i);
            }
        }
        int pw = 120;
        int ph = 36;
        int x0 = this.width / 2 - (others.size() * (pw + 8) - 8) / 2;
        for (int k = 0; k < others.size(); k++) {
            int idx = others.get(k);
            DaifugoSnapshot.SeatView s = seat(idx);
            int x = x0 + k * (pw + 8);
            int y = 20;
            gfx.fill(x, y, x + pw, y + ph, 0xA0083125);
            int line = idx == snapshot.turnSeat ? 0xFFFFE08A : 0xFF8A6D3B;
            frame(gfx, x, y, pw, ph, line);
            String name = s.cpu() >= 0 ? DaifugoScreens.cpuName(s.name()) : s.name();
            if (idx == snapshot.ownerSeat) {
                name += Component.translatable("screen.bamboomod.daifugo_owner").getString();
            }
            if (isPassedOut(idx)) {
                name += " (" + Component.translatable("screen.bamboomod.daifugo_passedout").getString() + ")";
            }
            gfx.drawString(this.font, trim(name, 18), x + 4, y + 4, 0xFFFFFFFF, false);
            String count = "×" + s.handCount();
            if (idx == snapshot.turnSeat && snapshot.turnLimit >= 0) {
                count += " " + timeoutText(snapshot.turnLimit);
            }
            gfx.drawString(this.font, count, x + 4, y + 15, 0xFFB9C4A8, false);
            if (s.roundRank() >= 0) {
                gfx.drawString(this.font,
                        Component.translatable("screen.bamboomod.daifugo_rank" + s.roundRank())
                                .getString(),
                        x + 4, y + 25, 0xFFFFE08A, false);
            } else if (s.prevRank() >= 0) {
                gfx.drawString(this.font,
                        Component.translatable("screen.bamboomod.daifugo_rank" + s.prevRank())
                                .getString(),
                        x + 4, y + 25, 0xFFB9C4A8, false);
            } else if (!s.connected()) {
                gfx.drawString(this.font,
                        Component.translatable("screen.bamboomod.daifugo_off").getString(),
                        x + 4, y + 25, 0xFF888888, false);
            }
        }
    }

    private void drawTable(GuiGraphics gfx) {
        int n = snapshot.table.size();
        int cy = this.height / 2 - 56;
        String info;
        if (n == 0) {
            info = Component.translatable("screen.bamboomod.daifugo_lead").getString();
        } else if (snapshot.tableStairs) {
            info = seatName(snapshot.tableSeat) + "  (" + n + " "
                    + Component.translatable("screen.bamboomod.daifugo_stairs").getString() + ")";
        } else {
            info = snapshot.tableSeat >= 0 && seat(snapshot.tableSeat) != null
                    ? seatName(snapshot.tableSeat) + "  (" + n + ")"
                    : Component.translatable("screen.bamboomod.daifugo_lead").getString();
        }
        gfx.drawCenteredString(this.font, info, this.width / 2, cy - 14, 0xFFB9C4A8);
        if (n == 0) {
            int x = this.width / 2 - CARD_W / 2;
            gfx.fill(x, cy, x + CARD_W, cy + CARD_H, 0xFF083125);
            frame(gfx, x, cy, CARD_W, CARD_H, 0xFF8A6D3B);
            return;
        }
        int x0 = this.width / 2 - ((n - 1) * TABLE_PITCH + CARD_W) / 2;
        // 飛来中は旧場札を下に残す (新札が重なってから消える)
        if (flyTicks > 0 && !prevTableCards.isEmpty()) {
            int pn = prevTableCards.size();
            int px0 = this.width / 2 - ((pn - 1) * TABLE_PITCH + CARD_W) / 2;
            for (int i = 0; i < pn; i++) {
                renderCard(gfx, px0 + i * TABLE_PITCH, cy, prevTableCards.get(i));
            }
        }
        for (int i = 0; i < n; i++) {
            int tx = x0 + i * TABLE_PITCH;
            int ty = cy;
            if (flyTicks > 0) {
                // 出した席から場へ飛んでくる (ease-out)
                float t = 1.0F - flyTicks / (float) FLY_LEN;
                float e = 1.0F - (1.0F - t) * (1.0F - t);
                int[] src = seatPos(flySeat);
                tx = (int) (src[0] - CARD_W / 2 + (tx - (src[0] - CARD_W / 2)) * e);
                ty = (int) (src[1] + (cy - src[1]) * e);
            }
            renderCard(gfx, tx, ty, snapshot.table.get(i));
        }
        if (!snapshot.lockSuits.isEmpty()) {
            StringBuilder badge = new StringBuilder();
            for (int suit : snapshot.lockSuits) {
                if (suit >= 0 && suit < TrumpSuit.values().length) {
                    badge.append(TrumpSuit.values()[suit].glyph());
                }
            }
            badge.append(Component.translatable("screen.bamboomod.daifugo_lock").getString());
            gfx.drawCenteredString(this.font, badge.toString(),
                    this.width / 2, cy + CARD_H + 4, 0xFFFFE08A);
        }
    }

    /** 特殊流しの残像 (流した札+見出しをしばらく残す)。場が空のときのみ。 */
    private void drawFx(GuiGraphics gfx) {
        if (fxTicks <= 0 || lastFxKey.isEmpty() || snapshot.fxCards.isEmpty()
                || !snapshot.table.isEmpty()) {
            return;
        }
        int n = snapshot.fxCards.size();
        int cy = this.height / 2 - 56;
        String label = switch (lastFxKey) {
            case "cut" -> Component.translatable("screen.bamboomod.daifugo_fx_cut").getString();
            case "spe3" -> Component.translatable("screen.bamboomod.daifugo_fx_spe3").getString();
            case "six" -> Component.translatable("screen.bamboomod.daifugo_fx_six").getString();
            case "superstairs" ->
                    Component.translatable("screen.bamboomod.daifugo_fx_superstairs").getString();
            default -> Component.translatable("log.bamboomod.daifugo_flow").getString();
        };
        gfx.drawCenteredString(this.font, label, this.width / 2, cy + CARD_H + 4, 0xFFFFE08A);
        int x0 = this.width / 2 - ((n - 1) * TABLE_PITCH + CARD_W) / 2;
        for (int i = 0; i < n; i++) {
            renderCard(gfx, x0 + i * TABLE_PITCH, cy, snapshot.fxCards.get(i));
        }
    }

    /** 演出の飛び元 (席パネル中央。drawOthers と同じ配置)。 */
    private int[] seatPos(int seat) {
        if (seat == snapshot.mySeat) {
            return new int[]{this.width / 2, this.height - 66};
        }
        List<Integer> others = new ArrayList<>(4);
        for (int i = 0; i < DaifugoRoom.SEATS; i++) {
            if (i != snapshot.mySeat && seat(i) != null) {
                others.add(i);
            }
        }
        int pw = 120;
        int x0 = this.width / 2 - (others.size() * (pw + 8) - 8) / 2;
        for (int k = 0; k < others.size(); k++) {
            if (others.get(k) == seat) {
                return new int[]{x0 + k * (pw + 8) + pw / 2, 20 + 18};
            }
        }
        return new int[]{this.width / 2, this.height / 2};
    }

    private void drawHand(GuiGraphics gfx) {
        hitRects.clear();
        List<Integer> hand = snapshot.hand;
        int n = hand.size();
        if (n == 0) {
            return;
        }
        int y0 = this.height - 66;
        int x0 = this.width / 2 - ((n - 1) * HAND_PITCH + CARD_W) / 2;
        for (int i = 0; i < n; i++) {
            int id = hand.get(i);
            int x = x0 + i * HAND_PITCH;
            int y = selected.contains(id) ? y0 - SELECT_UP : y0;
            renderCard(gfx, x, y, id);
            hitRects.add(new int[]{id, x, y, CARD_W, CARD_H});
            if (selected.contains(id)) {
                frame(gfx, x, y, CARD_W, CARD_H, 0xFFFFE08A);
            }
        }
    }

    /** 対戦ログ (右側の中段に右寄せ)。 */
    private void drawLog(GuiGraphics gfx) {
        int count = Math.min(5, snapshot.log.size());
        int base = snapshot.log.size() - count;
        int y0 = this.height / 2 - 10;
        for (int i = 0; i < count; i++) {
            String line = DaifugoScreens.logLine(snapshot.log.get(base + i));
            gfx.drawString(this.font, line,
                    this.width - 8 - this.font.width(line), y0 + i * 10, 0xFFB9C4A8, false);
        }
    }

    private void drawRoundEnd(GuiGraphics gfx) {
        gfx.fill(0, 0, this.width, this.height, 0xA0000000);
        String title = Component.translatable("screen.bamboomod.daifugo_roundend",
                String.valueOf(snapshot.round)).getString();
        if (snapshot.roundEndLimit >= 0) {
            title += " " + Component.translatable("screen.bamboomod.daifugo_nextround",
                    String.valueOf(ceilSec(snapshot.roundEndLimit))).getString();
        }
        gfx.drawCenteredString(this.font, title,
                this.width / 2, this.height / 2 - 52, 0xFFFFE9B0);
        List<DaifugoSnapshot.SeatView> order = seatsByRank();
        for (int i = 0; i < order.size(); i++) {
            DaifugoSnapshot.SeatView s = order.get(i);
            String line = Component.translatable("screen.bamboomod.daifugo_rank" + i).getString()
                    + " " + displayName(s);
            gfx.drawCenteredString(this.font, line, this.width / 2, this.height / 2 - 30 + i * 12,
                    0xFFFFFFFF);
        }
    }

    private void drawTribute(GuiGraphics gfx) {
        gfx.fill(0, 0, this.width, this.height, 0xA0000000);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.daifugo_tribute_title").getString(),
                this.width / 2, this.height / 2 - 80, 0xFFFFE9B0);
        String sub = myOwed() > 0
                ? Component.translatable("screen.bamboomod.daifugo_tribute_choose",
                        String.valueOf(myOwed())).getString()
                : Component.translatable("screen.bamboomod.daifugo_tribute_wait").getString();
        if (myOwed() > 0 && snapshot.tributeLimit >= 0) {
            sub += " " + timeoutText(snapshot.tributeLimit);
        }
        gfx.drawCenteredString(this.font, sub, this.width / 2, this.height / 2 - 62, 0xFFB9C4A8);
    }

    private List<DaifugoSnapshot.SeatView> seatsByRank() {
        List<DaifugoSnapshot.SeatView> list = new ArrayList<>();
        for (int r = 0; r < DaifugoRoom.SEATS; r++) {
            for (int i = 0; i < snapshot.seats.size(); i++) {
                DaifugoSnapshot.SeatView s = snapshot.seats.get(i);
                if (s != null && s.roundRank() == r) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    private String displayName(DaifugoSnapshot.SeatView s) {
        return s.cpu() >= 0 ? DaifugoScreens.cpuName(s.name()) : s.name();
    }

    private String seatName(int idx) {
        DaifugoSnapshot.SeatView s = seat(idx);
        return s == null ? "?" : displayName(s);
    }

    private void renderCard(GuiGraphics gfx, int x, int y, int id) {
        if (id >= DaifugoCard.JOKER_A_ID) {
            TrumpCardRenderer.renderCard(gfx, this.font, x, y, CARD_W,
                    TrumpRank.JOKER, TrumpSuit.SPADE, false);
            return;
        }
        TrumpCardRenderer.renderCard(gfx, this.font, x, y, CARD_W,
                TrumpRank.values()[id % 13], TrumpSuit.values()[id / 13], false);
    }

    private String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void frame(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x, y, x + w, y + 1, color);
        gfx.fill(x, y + h - 1, x + w, y + h, color);
        gfx.fill(x, y, x + 1, y + h, color);
        gfx.fill(x + w - 1, y, x + w, y + h, color);
    }
}
