package ruby.bamboo.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
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
        int y = 40;
        int w = 200;
        int h = 20;
        int gap = 24;

        // particleEnabled — CycleButton (ON/OFF)
        this.addRenderableWidget(CycleButton.onOffBuilder(MiniatureConfig.CLIENT.particleEnabled.get())
                .withInitialValue(MiniatureConfig.CLIENT.particleEnabled.get())
                .displayOnlyValue()
                .withTooltip(v -> net.minecraft.client.gui.components.Tooltip.create(Component.translatable("bamboomod.config.miniature.particle.enabled.tooltip")))
                .create(centerX - w / 2, y, w, h, Component.translatable("bamboomod.config.miniature.particle.enabled"),
                        (btn, val) -> MiniatureConfig.CLIENT.particleEnabled.set(val)));
        y += gap;

        // particlesPerMiniaturePerTick — Int 0-10
        this.addRenderableWidget(createIntButton(Component.translatable("bamboomod.config.miniature.particle.perMiniaturePerTick"),
                MiniatureConfig.CLIENT.particlesPerMiniaturePerTick.get(), 0, 10, v -> MiniatureConfig.CLIENT.particlesPerMiniaturePerTick.set(v),
                centerX - w / 2, y, w, h));
        y += gap;

        // maxParticlesPerClientTick — Int 0-512 step 8
        this.addRenderableWidget(createIntButton(Component.translatable("bamboomod.config.miniature.particle.globalMaxPerTick"),
                MiniatureConfig.CLIENT.maxParticlesPerClientTick.get(), 0, 512, v -> MiniatureConfig.CLIENT.maxParticlesPerClientTick.set(v),
                centerX - w / 2, y, w, h));
        y += gap;

        // particleSpawnChance — Double 0.0-1.0
        this.addRenderableWidget(createDoubleButton(Component.translatable("bamboomod.config.miniature.particle.spawnChance"),
                MiniatureConfig.CLIENT.particleSpawnChance.get(), 0.0, 1.0, 0.1, v -> MiniatureConfig.CLIENT.particleSpawnChance.set(v),
                centerX - w / 2, y, w, h));
        y += gap;

        // particleDistance — Double 4.0-128.0
        this.addRenderableWidget(createDoubleButton(Component.translatable("bamboomod.config.miniature.particle.distance"),
                MiniatureConfig.CLIENT.particleDistance.get(), 4.0, 128.0, 4.0, v -> MiniatureConfig.CLIENT.particleDistance.set(v),
                centerX - w / 2, y, w, h));
        y += gap;

        // particleTickInterval — Int 1-20
        this.addRenderableWidget(createIntButton(Component.translatable("bamboomod.config.miniature.particle.tickInterval"),
                MiniatureConfig.CLIENT.particleTickInterval.get(), 1, 20, v -> MiniatureConfig.CLIENT.particleTickInterval.set(v),
                centerX - w / 2, y, w, h));
        y += gap + 10;

        // Done
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(centerX - 100, y, 200, 20).build());
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
        this.renderBackground(g);
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
