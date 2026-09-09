package ruby.bamboo.client.gui;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Config画面用の共通ウィジェット工場 (左分類・右内容の統合画面と個別画面で共用)。
 */
@OnlyIn(Dist.CLIENT)
public final class ConfigWidgets {

    private ConfigWidgets() {
    }

    public static CycleButton<Boolean> onOff(Component label, Component tooltip, boolean current,
            CycleButton.OnValueChange<Boolean> onChange, int x, int y, int w, int h) {
        var b = CycleButton.onOffBuilder(current)
                .withInitialValue(current)
                .displayOnlyValue();
        if (tooltip != null) b.withTooltip(v -> Tooltip.create(tooltip));
        return b.create(x, y, w, h, label, onChange);
    }

    public static Button intCycle(Component label, int current, int min, int max,
            IntConsumer setter, int x, int y, int w, int h) {
        return Button.builder(Component.literal(label.getString() + ": " + current), b -> {
            int cur = current;
            try {
                String txt = b.getMessage().getString();
                int idx = txt.lastIndexOf(": ");
                if (idx >= 0) cur = Integer.parseInt(txt.substring(idx + 2).trim());
            } catch (Exception e) {
            }
            int next = cur + 1;
            if (next > max) next = min;
            setter.accept(next);
            b.setMessage(Component.literal(label.getString() + ": " + next));
        }).bounds(x, y, w, h).tooltip(Tooltip.create(Component.literal("クリックで " + min + "〜" + max + " を循環"))).build();
    }

    public static Button choices(Component label, String current, String[] choices,
            Consumer<String> setter, int x, int y, int w, int h) {
        return Button.builder(Component.literal(label.getString() + ": " + current), b -> {
            String cur = current;
            try {
                String txt = b.getMessage().getString();
                int idx = txt.lastIndexOf(": ");
                if (idx >= 0) cur = txt.substring(idx + 2).trim();
            } catch (Exception e) {
            }
            int idx = -1;
            for (int i = 0; i < choices.length; i++) if (choices[i].equals(cur)) { idx = i; break; }
            String next = choices[(idx + 1) % choices.length];
            setter.accept(next);
            b.setMessage(Component.literal(label.getString() + ": " + next));
        }).bounds(x, y, w, h).tooltip(Tooltip.create(Component.literal("クリックで " + String.join("/", choices) + " を循環"))).build();
    }

    public static Button doubleStep(Component label, double current, double min, double max, double step,
            DoubleConsumer setter, int x, int y, int w, int h) {
        String fmt = String.format("%.2f", current);
        return Button.builder(Component.literal(label.getString() + ": " + fmt), b -> {
            double cur = current;
            try {
                String txt = b.getMessage().getString();
                int idx = txt.lastIndexOf(": ");
                if (idx >= 0) cur = Double.parseDouble(txt.substring(idx + 2).trim());
            } catch (Exception e) {
            }
            double next = Math.round((cur + step) * 100.0) / 100.0;
            if (next > max + 1e-9) next = min;
            setter.accept(next);
            b.setMessage(Component.literal(label.getString() + ": " + String.format("%.2f", next)));
        }).bounds(x, y, w, h).tooltip(Tooltip.create(Component.literal("クリックで " + min + "〜" + max + " を " + step + " 刻みで循環"))).build();
    }
}
