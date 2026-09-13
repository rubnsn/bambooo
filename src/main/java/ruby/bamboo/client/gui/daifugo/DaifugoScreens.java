package ruby.bamboo.client.gui.daifugo;

import net.minecraft.network.chat.Component;
import ruby.bamboo.daifugo.DaifugoSnapshot;

/**
 * 大富豪画面の共通小物 (CPU名・ログ行の組み立て)。
 */
public final class DaifugoScreens {
    private DaifugoScreens() {
    }

    /** CPU席の表示名。席名には personality のID (zombie等) が入っている。 */
    public static String cpuName(String id) {
        return Component.translatable("screen.bamboomod.daifugo_cpu_" + id).getString()
                + " (CPU)";
    }

    /**
     * 席の基本表示名。純粋CPU席 (接続中) は訳名、人間退出後の代打席
     * (cpu>=0・切断中) は「人間名 (代打CPU)」、人間席はそのままの名前。
     * 切断・部屋主の接尾辞は呼び出し側で付ける。
     */
    public static String seatName(DaifugoSnapshot.SeatView s) {
        if (s.cpu() >= 0 && s.connected()) {
            return cpuName(s.name());
        }
        if (s.cpu() >= 0) {
            return s.name()
                    + Component.translatable("screen.bamboomod.daifugo_sub").getString();
        }
        return s.name();
    }

    public static String logLine(DaifugoSnapshot.LogEntry e) {
        return Component.translatable(e.key(), (Object[]) e.args().toArray(new String[0])).getString();
    }
}
