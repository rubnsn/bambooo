package ruby.bamboo.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import ruby.bamboo.client.handler.ClientChatLog;
import ruby.bamboo.client.handler.ClientChatLog.Entry;

/**
 * ミニゲーム画面共通のチャット送受信オーバーレイ。
 * Tキーで入力欄を重ねて開き、Enter送信・Esc取消。ログは左下に置き、
 * 入力中以外はバニラ同様に一定時間でフェードアウトする。
 */
public final class ChatOverlay {
    /** 直近の表示行数。 */
    private static final int MAX_LINES = 8;
    /** ここまでは不透明 (tick)。 */
    private static final int FULL_TICKS = 160;
    /** ここで完全透明 (tick)。 */
    private static final int FADE_TICKS = 220;
    /** 折返し幅の上限。 */
    private static final int WRAP_MAX = 320;

    private EditBox box;
    private boolean open;
    /** 開くきっかけのTキー自体のchar入力を1文字だけ捨てる。 */
    private boolean eatOpenChar;

    public boolean isOpen() {
        return open;
    }

    /** attach() で作った入力欄。フォーカス当てに使う。 */
    public EditBox box() {
        return box;
    }

    /**
     * init() から呼ぶ。入力欄を作り直して返すので、呼び出し側で addRenderableWidget する。
     * 開閉状態と入力途中の文字列は維持される。
     */
    public EditBox attach(Font font, int screenW, int screenH) {
        String draft = box != null ? box.getValue() : "";
        box = new EditBox(font, 4, screenH - 28, screenW - 8, 20, Component.empty());
        box.setMaxLength(256);
        box.setValue(draft);
        box.visible = open;
        return box;
    }

    /**
     * keyPressed の先頭から呼ぶ。消費したら true。
     * 未消費 (入力中の通常キー) は false を返し、呼び出し側で super へ回す。
     */
    public boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        if (open) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                send(screen);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setOpen(screen, false);
                return true;
            }
            return false;
        }
        Minecraft mc = screen.getMinecraft();
        if (mc != null && mc.options.keyChat.matches(keyCode, scanCode)) {
            setOpen(screen, true);
            if (box != null) {
                box.setValue("");
                screen.setFocused(box);
                box.setFocused(true);
            }
            // このTキーによるchar入力 ('t') は入力欄に入れない
            eatOpenChar = true;
            return true;
        }
        return false;
    }

    public boolean charTyped(char c, int modifiers) {
        if (eatOpenChar) {
            eatOpenChar = false;
            return true;
        }
        return false;
    }

    private void setOpen(Screen screen, boolean on) {
        open = on;
        if (box != null) {
            box.visible = on;
            box.setFocused(on);
            if (!on) {
                screen.setFocused(null);
            }
        }
    }

    /** バニラの署名付き送信で送る (/始まりはコマンド)。空文は閉じるだけ。 */
    private void send(Screen screen) {
        String msg = box != null ? box.getValue().trim() : "";
        setOpen(screen, false);
        if (msg.isEmpty()) {
            return;
        }
        Minecraft mc = screen.getMinecraft();
        if (mc == null || mc.player == null) {
            return;
        }
        if (msg.startsWith("/")) {
            mc.player.connection.sendCommand(msg.substring(1));
        } else {
            mc.player.connection.sendChat(msg);
        }
    }

    /** 受信ログを左下に描く。入力中は全行不透明、そうでなければ古い行から消える。 */
    public void renderLog(GuiGraphics gfx, Font font, int screenW, int screenH) {
        int wrapW = Math.min(WRAP_MAX, screenW - 16);
        if (wrapW < 60) {
            return;
        }
        long now = Minecraft.getInstance().gui.getGuiTicks();
        List<TimedLine> lines = new ArrayList<>();
        for (Entry e : ClientChatLog.entries()) {
            for (FormattedCharSequence seq : font.split(e.text(), wrapW)) {
                lines.add(new TimedLine(seq, e.tick()));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        int show = Math.min(MAX_LINES, lines.size());
        int bottom = open ? screenH - 32 : screenH - 24;
        for (int i = 0; i < show; i++) {
            TimedLine line = lines.get(lines.size() - show + i);
            int alpha;
            if (open) {
                alpha = 255;
            } else {
                long age = now - line.tick();
                if (age > FADE_TICKS) {
                    continue;
                }
                alpha = age <= FULL_TICKS ? 255
                        : (int) ((FADE_TICKS - age) * 255 / (FADE_TICKS - FULL_TICKS));
            }
            int y = bottom - (show - i) * 10;
            gfx.fill(6, y - 1, 10 + wrapW, y + 9, (alpha / 2 << 24));
            gfx.drawString(font, line.seq(), 8, y, (alpha << 24) | 0xFFFFFF, true);
        }
    }

    private record TimedLine(FormattedCharSequence seq, long tick) {
    }
}
