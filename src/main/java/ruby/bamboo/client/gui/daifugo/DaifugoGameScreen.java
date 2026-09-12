package ruby.bamboo.client.gui.daifugo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ruby.bamboo.client.gui.trump.TrumpCardRenderer;
import ruby.bamboo.client.gui.trump.TrumpRank;
import ruby.bamboo.client.gui.trump.TrumpSuit;
import ruby.bamboo.daifugo.DaifugoCard;
import ruby.bamboo.daifugo.DaifugoRoom;
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

    private DaifugoSnapshot snapshot;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private final List<int[]> hitRects = new ArrayList<>();

    private Button playButton;
    private Button passButton;
    private Button leaveButton;
    private Button tributeButton;

    public DaifugoGameScreen(DaifugoSnapshot snapshot) {
        super(Component.translatable("screen.bamboomod.daifugo_game"));
        this.snapshot = snapshot;
    }

    public void update(DaifugoSnapshot snapshot) {
        this.snapshot = snapshot;
        selected.retainAll(snapshot.hand);
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
        return snapshot.turnSeat == snapshot.mySeat && me != null && me.roundRank() < 0;
    }

    private DaifugoSnapshot.SeatView seat(int idx) {
        return idx >= 0 && idx < snapshot.seats.size() ? snapshot.seats.get(idx) : null;
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
        this.addRenderableWidget(playButton);
        this.addRenderableWidget(passButton);
        this.addRenderableWidget(leaveButton);
        this.addRenderableWidget(tributeButton);
    }

    @Override
    public void tick() {
        super.tick();
        playButton.visible = isPlaying();
        passButton.visible = isPlaying();
        playButton.active = myTurn() && !selected.isEmpty();
        passButton.active = myTurn() && !snapshot.table.isEmpty();
        tributeButton.visible = isTribute() && myOwed() > 0;
        tributeButton.active = selected.size() == myOwed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || isRoundEnd()) {
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
        gfx.fill(0, 0, this.width, this.height, 0xFF0B3D2C);
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
        drawHand(gfx);
        drawLog(gfx);

        if (myTurn()) {
            gfx.drawCenteredString(this.font,
                    Component.translatable("screen.bamboomod.daifugo_turn").getString(),
                    this.width / 2, this.height - 92, 0xFFFFE08A);
        }
        if (isRoundEnd()) {
            drawRoundEnd(gfx);
        }
        if (isTribute()) {
            drawTribute(gfx);
        }
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
            gfx.drawString(this.font, trim(name, 18), x + 4, y + 4, 0xFFFFFFFF, false);
            gfx.drawString(this.font, "×" + s.handCount(), x + 4, y + 15, 0xFFB9C4A8, false);
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
        String info = snapshot.tableSeat >= 0 && seat(snapshot.tableSeat) != null
                ? seatName(snapshot.tableSeat) + "  (" + n + ")"
                : Component.translatable("screen.bamboomod.daifugo_lead").getString();
        gfx.drawCenteredString(this.font, info, this.width / 2, cy - 14, 0xFFB9C4A8);
        if (n == 0) {
            int x = this.width / 2 - CARD_W / 2;
            gfx.fill(x, cy, x + CARD_W, cy + CARD_H, 0xFF083125);
            frame(gfx, x, cy, CARD_W, CARD_H, 0xFF8A6D3B);
            return;
        }
        int x0 = this.width / 2 - ((n - 1) * TABLE_PITCH + CARD_W) / 2;
        for (int i = 0; i < n; i++) {
            renderCard(gfx, x0 + i * TABLE_PITCH, cy, snapshot.table.get(i));
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

    private void drawLog(GuiGraphics gfx) {
        int count = Math.min(5, snapshot.log.size());
        int base = snapshot.log.size() - count;
        for (int i = 0; i < count; i++) {
            gfx.drawString(this.font, DaifugoScreens.logLine(snapshot.log.get(base + i)),
                    8, this.height - 24 - (count - 1 - i) * 10, 0xFFB9C4A8, false);
        }
    }

    private void drawRoundEnd(GuiGraphics gfx) {
        gfx.fill(0, 0, this.width, this.height, 0xA0000000);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.daifugo_roundend",
                        String.valueOf(snapshot.round)).getString(),
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
