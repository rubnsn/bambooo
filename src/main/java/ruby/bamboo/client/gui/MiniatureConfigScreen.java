package ruby.bamboo.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import ruby.bamboo.core.config.MiniatureConfig;

/**
 * Forge ModList 用 Config GUI — Miniature パーティクル設定をゲーム内から変更可能にする。
 * <p>
 * 旧 BambooMod にはインベントリ GUI しかなく、Forge の ConfigScreenFactory が未登録で
 * 「Config」ボタンが無効になっていたため、本クラスで CLIENT_SPEC の 6 値を編集可能にする。
 * 値は即時 {@code ConfigValue#set} で反映され、Forge が自動保存する。
 */
@OnlyIn(Dist.CLIENT)
public class MiniatureConfigScreen extends Screen {

    private final Screen parent;

    public MiniatureConfigScreen(Screen parent) {
        super(Component.translatable("bamboomod.config.miniature.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int h = 20;
        int gap = 24;
        // 2列レイアウト: 1列8行が限界のため左右5行ずつに分割
        int colW = 155;
        // 画面が狭い場合は中央寄せで幅を縮める
        if (this.width < colW * 2 + 20) {
            colW = (this.width - 20 - 10) / 2;
            if (colW < 120) colW = 120;
        }
        int leftX = centerX - colW - 5;
        int rightX = centerX + 5;
        int yL = 40;
        int yR = 40;

        // 左列: パーティクル系 5行
        this.addRenderableWidget(CycleButton.onOffBuilder(MiniatureConfig.CLIENT.particleEnabled.get())
                .withInitialValue(MiniatureConfig.CLIENT.particleEnabled.get())
                .displayOnlyValue()
                .withTooltip(v -> net.minecraft.client.gui.components.Tooltip.create(Component.translatable("bamboomod.config.miniature.particle.enabled.tooltip")))
                .create(leftX, yL, colW, h, Component.translatable("bamboomod.config.miniature.particle.enabled"),
                        (btn, val) -> MiniatureConfig.CLIENT.particleEnabled.set(val)));
        yL += gap;

        this.addRenderableWidget(createIntButton(Component.translatable("bamboomod.config.miniature.particle.perMiniaturePerTick"),
                MiniatureConfig.CLIENT.particlesPerMiniaturePerTick.get(), 0, 10, v -> MiniatureConfig.CLIENT.particlesPerMiniaturePerTick.set(v),
                leftX, yL, colW, h));
        yL += gap;

        this.addRenderableWidget(createIntButton(Component.translatable("bamboomod.config.miniature.particle.globalMaxPerTick"),
                MiniatureConfig.CLIENT.maxParticlesPerClientTick.get(), 0, 512, v -> MiniatureConfig.CLIENT.maxParticlesPerClientTick.set(v),
                leftX, yL, colW, h));
        yL += gap;

        this.addRenderableWidget(createDoubleButton(Component.translatable("bamboomod.config.miniature.particle.spawnChance"),
                MiniatureConfig.CLIENT.particleSpawnChance.get(), 0.0, 1.0, 0.1, v -> MiniatureConfig.CLIENT.particleSpawnChance.set(v),
                leftX, yL, colW, h));
        yL += gap;

        this.addRenderableWidget(createDoubleButton(Component.translatable("bamboomod.config.miniature.particle.distance"),
                MiniatureConfig.CLIENT.particleDistance.get(), 4.0, 128.0, 4.0, v -> MiniatureConfig.CLIENT.particleDistance.set(v),
                leftX, yL, colW, h));
        yL += gap;

        // 右列: 残りパーティクル1 + レンダー系 4行 = 5行
        this.addRenderableWidget(createIntButton(Component.translatable("bamboomod.config.miniature.particle.tickInterval"),
                MiniatureConfig.CLIENT.particleTickInterval.get(), 1, 20, v -> MiniatureConfig.CLIENT.particleTickInterval.set(v),
                rightX, yR, colW, h));
        yR += gap;

        this.addRenderableWidget(createChoicesButton(Component.translatable("bamboomod.config.miniature.render.maxCells"),
                String.valueOf(MiniatureConfig.CLIENT.maxCellsPerFrame.get()),
                new String[]{"0","128","256","512","1024","2048","4096","8192","16384","65536"},
                v -> {
                    try { MiniatureConfig.CLIENT.maxCellsPerFrame.set(Integer.parseInt(v)); } catch (Exception e) {}
                }, rightX, yR, colW, h));
        yR += gap;

        this.addRenderableWidget(createDoubleButton(Component.translatable("bamboomod.config.miniature.render.maxDistance"),
                MiniatureConfig.CLIENT.maxRenderDistance.get(), 4.0, 128.0, 8.0, v -> MiniatureConfig.CLIENT.maxRenderDistance.set(v),
                rightX, yR, colW, h));
        yR += gap;

        this.addRenderableWidget(CycleButton.builder((MiniatureConfig.PlaceholderMode v) -> Component.literal(v.name()))
                .withValues(MiniatureConfig.PlaceholderMode.values())
                .withInitialValue(MiniatureConfig.CLIENT.placeholderMode.get())
                .create(rightX, yR, colW, h, Component.translatable("bamboomod.config.miniature.render.placeholder"),
                        (btn, val) -> MiniatureConfig.CLIENT.placeholderMode.set(val)));
        yR += gap;

        this.addRenderableWidget(CycleButton.onOffBuilder(MiniatureConfig.CLIENT.lodBoundaryShell.get())
                .withInitialValue(MiniatureConfig.CLIENT.lodBoundaryShell.get())
                .create(rightX, yR, colW, h, Component.translatable("bamboomod.config.miniature.render.lodShell"),
                        (btn, val) -> MiniatureConfig.CLIENT.lodBoundaryShell.set(val)));
        yR += gap;

        int bottomY = Math.max(yL, yR) + 10;
        // 画面下部に収まらない場合は少し上に寄せる
        if (bottomY + h + 10 > this.height) {
            bottomY = this.height - h - 10;
        }
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(centerX - 100, bottomY, 200, 20).build());
    }

    private Button createIntButton(Component label, int current, int min, int max, java.util.function.IntConsumer setter, int x, int y, int w, int h) {
        // クリック毎に Config の現在値を取得して循環させる (final 変数 current は初回表示用のみ)
        return Button.builder(Component.literal(label.getString() + ": " + current), b -> {
            int cur = 0;
            try {
                // label から Config 値を逆引きせず、ボタンの表示から現在の数値をパースする簡易実装
                String txt = b.getMessage().getString();
                int idx = txt.lastIndexOf(": ");
                if (idx >= 0) cur = Integer.parseInt(txt.substring(idx + 2).trim());
                else cur = current;
            } catch (Exception e) { cur = current; }
            int next = cur + 1;
            if (next > max) next = min;
            setter.accept(next);
            b.setMessage(Component.literal(label.getString() + ": " + next));
        }).bounds(x, y, w, h).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("クリックで " + min + "〜" + max + " を循環"))).build();
    }

    private Button createChoicesButton(Component label, String current, String[] choices, java.util.function.Consumer<String> setter, int x, int y, int w, int h) {
        return Button.builder(Component.literal(label.getString() + ": " + current), b -> {
            String cur = current;
            try {
                String txt = b.getMessage().getString();
                int idx = txt.lastIndexOf(": ");
                if (idx >= 0) cur = txt.substring(idx + 2).trim();
            } catch (Exception e) {}
            int idx = -1;
            for (int i = 0; i < choices.length; i++) if (choices[i].equals(cur)) { idx = i; break; }
            String next = choices[(idx + 1) % choices.length];
            setter.accept(next);
            b.setMessage(Component.literal(label.getString() + ": " + next));
        }).bounds(x, y, w, h).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("クリックで " + String.join("/", choices) + " を循環"))).build();
    }

    private Button createDoubleButton(Component label, double current, double min, double max, double step, java.util.function.DoubleConsumer setter, int x, int y, int w, int h) {
        String fmt = String.format("%.2f", current);
        return Button.builder(Component.literal(label.getString() + ": " + fmt), b -> {
            double cur = current;
            try {
                String txt = b.getMessage().getString();
                int idx = txt.lastIndexOf(": ");
                if (idx >= 0) cur = Double.parseDouble(txt.substring(idx + 2).trim());
            } catch (Exception e) {}
            double next = Math.round((cur + step) * 100.0) / 100.0;
            if (next > max + 1e-9) next = min;
            setter.accept(next);
            b.setMessage(Component.literal(label.getString() + ": " + String.format("%.2f", next)));
        }).bounds(x, y, w, h).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("クリックで " + min + "〜" + max + " を " + step + " 刻みで循環"))).build();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("bamboomod.config.miniature.desc"), this.width / 2, 27, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        // ForgeConfigSpec は set() で dirty になり、ゲーム終了時またはワールド切替時に自動保存される
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}
