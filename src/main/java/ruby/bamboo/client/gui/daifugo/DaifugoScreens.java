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

    public static String logLine(DaifugoSnapshot.LogEntry e) {
        return Component.translatable(e.key(), (Object[]) e.args().toArray(new String[0])).getString();
    }
}
