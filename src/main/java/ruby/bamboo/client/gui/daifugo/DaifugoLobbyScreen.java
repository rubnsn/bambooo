package ruby.bamboo.client.gui.daifugo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ruby.bamboo.daifugo.DaifugoRoom;
import ruby.bamboo.daifugo.DaifugoSnapshot;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.DaifugoLeavePacket;
import ruby.bamboo.network.DaifugoRulesPacket;
import ruby.bamboo.network.DaifugoStartPacket;

/**
 * 大富豪のロビー。参加受付メンバーの一覧・開始 (オーナー)・退出。
 * ローカルルール5種はオーナーが開始前に切替可 (既定全ON)。
 */
public class DaifugoLobbyScreen extends Screen {
    private static final String[] RULE_KEYS = {
            "screen.bamboomod.daifugo_rule_eight",
            "screen.bamboomod.daifugo_rule_jback",
            "screen.bamboomod.daifugo_rule_lock",
            "screen.bamboomod.daifugo_rule_spe3",
            "screen.bamboomod.daifugo_rule_miyako",
    };

    private DaifugoSnapshot snapshot;

    private Button startButton;
    private Button leaveButton;
    private final Button[] ruleButtons = new Button[RULE_KEYS.length];
    /** オーバーレイのチャット入力 (Tで開く。画面は切り替えない)。 */
    private EditBox chatBox;
    private boolean chatMode = false;
    /** 開くきっかけのTキー自体のchar入力を1文字だけ捨てる。 */
    private boolean eatOpenChar = false;

    public DaifugoLobbyScreen(DaifugoSnapshot snapshot) {
        super(Component.translatable("screen.bamboomod.daifugo_lobby"));
        this.snapshot = snapshot;
    }

    public void update(DaifugoSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (chatMode) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                sendChatMessage();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setChatMode(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // Tでチャット入力を重ねて開く (ロビー画面は残る)
        if (this.minecraft != null && this.minecraft.options.keyChat.matches(keyCode, scanCode)) {
            setChatMode(true);
            // このTキーによるchar入力 ('t') は入力欄に入れない
            eatOpenChar = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (eatOpenChar) {
            eatOpenChar = false;
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    private void setChatMode(boolean on) {
        chatMode = on;
        if (chatBox != null) {
            chatBox.visible = on;
            chatBox.setFocused(on);
            if (on) {
                chatBox.setValue("");
                this.setFocused(chatBox);
            }
        }
    }

    /** バニラの署名付き送信で送る (/始まりはコマンド)。 */
    private void sendChatMessage() {
        String msg = chatBox != null ? chatBox.getValue().trim() : "";
        setChatMode(false);
        if (msg.isEmpty() || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (msg.startsWith("/")) {
            this.minecraft.player.connection.sendCommand(msg.substring(1));
        } else {
            this.minecraft.player.connection.sendChat(msg);
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int cx = this.width / 2;
        startButton = Button.builder(Component.translatable("screen.bamboomod.daifugo_start"),
                b -> BambooNetwork.CHANNEL.sendToServer(new DaifugoStartPacket()))
                .bounds(cx - 160, this.height - 30, 150, 20)
                .build();
        leaveButton = Button.builder(Component.translatable("screen.bamboomod.daifugo_leave"),
                b -> {
                    BambooNetwork.CHANNEL.sendToServer(new DaifugoLeavePacket());
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(null);
                    }
                })
                .bounds(cx + 10, this.height - 30, 150, 20)
                .build();
        this.addRenderableWidget(startButton);
        this.addRenderableWidget(leaveButton);
        for (int i = 0; i < RULE_KEYS.length; i++) {
            final int idx = i;
            Button rule = Button.builder(ruleLabel(idx, true), b -> toggleRule(idx))
                    .bounds(cx - 155 + (i % 2) * 160, 120 + (i / 2) * 22, 150, 20)
                    .build();
            ruleButtons[i] = rule;
            this.addRenderableWidget(rule);
        }
        String draft = chatBox != null ? chatBox.getValue() : "";
        chatBox = new EditBox(this.font, 4, this.height - 28, this.width - 8, 20,
                Component.empty());
        chatBox.setMaxLength(256);
        chatBox.setValue(draft);
        chatBox.visible = chatMode;
        this.addRenderableWidget(chatBox);
        if (chatMode) {
            this.setFocused(chatBox);
            chatBox.setFocused(true);
        }
    }

    private boolean ruleValue(int idx) {
        return switch (idx) {
            case 0 -> snapshot.ruleEightCut;
            case 1 -> snapshot.ruleJBack;
            case 2 -> snapshot.ruleSuitLock;
            case 3 -> snapshot.ruleSpe3;
            default -> snapshot.ruleMiyako;
        };
    }

    private Component ruleLabel(int idx, boolean value) {
        return Component.translatable(RULE_KEYS[idx]).append(value ? ": ON" : ": OFF");
    }

    /** オーナーが1つ反転させた全5値を送る (非オーナーはボタン無効のため届かない)。 */
    private void toggleRule(int idx) {
        boolean[] v = {
                snapshot.ruleEightCut, snapshot.ruleJBack, snapshot.ruleSuitLock,
                snapshot.ruleSpe3, snapshot.ruleMiyako,
        };
        v[idx] = !v[idx];
        BambooNetwork.CHANNEL.sendToServer(
                new DaifugoRulesPacket(v[0], v[1], v[2], v[3], v[4]));
    }

    @Override
    public void tick() {
        super.tick();
        boolean owner = snapshot.mySeat == snapshot.ownerSeat;
        // チャット入力中は下段ボタンを隠し、ルール切替を無効化する
        startButton.visible = owner && !chatMode;
        leaveButton.visible = !chatMode;
        for (int i = 0; i < ruleButtons.length; i++) {
            ruleButtons[i].active = owner && !chatMode;
            ruleButtons[i].setMessage(ruleLabel(i, ruleValue(i)));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        gfx.drawCenteredString(this.font, this.title.getString(), this.width / 2, 20, 0xFFFFE9B0);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.daifugo_waiting").getString(),
                this.width / 2, 34, 0xFFB9C4A8);
        int y = 60;
        for (int i = 0; i < DaifugoRoom.SEATS; i++) {
            DaifugoSnapshot.SeatView s = i < snapshot.seats.size() ? snapshot.seats.get(i) : null;
            String line = (i + 1) + ". " + (s == null
                    ? Component.translatable("screen.bamboomod.daifugo_empty").getString()
                    : seatName(s, i));
            gfx.drawCenteredString(this.font, line, this.width / 2, y + i * 14, 0xFFFFFFFF);
        }
        int logY = this.height - 110;
        for (int i = 0; i < snapshot.log.size(); i++) {
            gfx.drawString(this.font, DaifugoScreens.logLine(snapshot.log.get(i)),
                    10, logY + i * 10, 0xFFB9C4A8, false);
        }
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private String seatName(DaifugoSnapshot.SeatView s, int idx) {
        String name = s.cpu() >= 0 ? DaifugoScreens.cpuName(s.name()) : s.name();
        if (!s.connected()) {
            name += Component.translatable("screen.bamboomod.daifugo_off").getString();
        }
        if (idx == snapshot.ownerSeat) {
            name += Component.translatable("screen.bamboomod.daifugo_owner").getString();
        }
        return name;
    }
}
