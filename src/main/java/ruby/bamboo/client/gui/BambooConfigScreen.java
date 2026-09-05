package ruby.bamboo.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.client.ColoredLightDaylightSync;
import ruby.bamboo.core.config.ColoredLightConfig;
import ruby.bamboo.core.config.MiniatureConfig;

/**
 * Forge ModList 用の統合 Config 画面 — 左に大項目、右にその内容。
 * <p>
 * カテゴリ切替で右ペインを作り直す。値は即時 {@code ConfigValue#set} で反映され、
 * Forge が自動保存する。色付き光の設定変更時は昼光同期に再評価を要求する。
 */
@OnlyIn(Dist.CLIENT)
public class BambooConfigScreen extends Screen {

    private enum Category {
        MINIATURE,
        COLORED_LIGHT,
    }

    private final Screen parent;
    private Category selected = Category.MINIATURE;

    public BambooConfigScreen(Screen parent) {
        super(Component.translatable("bamboomod.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int h = 20;
        int gap = 24;
        // 左: 大項目 (Embeddium式のカテゴリ列)
        int catX = 10;
        int catW = 110;
        int catY = 40;
        addCategoryButton(Category.MINIATURE, Component.translatable("bamboomod.config.cat.miniature"), catX, catY, catW, h);
        catY += gap;
        addCategoryButton(Category.COLORED_LIGHT, Component.translatable("bamboomod.config.cat.coloredlight"), catX, catY, catW, h);

        int rightX = catX + catW + 10;
        if (selected == Category.MINIATURE) {
            buildMiniaturePanel(rightX, 40, h, gap);
        } else {
            buildColoredLightPanel(rightX, 40, h, gap);
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    private void addCategoryButton(Category cat, Component label, int x, int y, int w, int h) {
        Button b = Button.builder(label, btn -> {
            selected = cat;
            this.init();
        }).bounds(x, y, w, h).build();
        b.active = (cat != selected);
        this.addRenderableWidget(b);
    }

    private void buildMiniaturePanel(int x0, int y0, int h, int gap) {
        int avail = this.width - x0 - 10;
        int colW = Math.min(155, Math.max(120, (avail - 5) / 2));
        int leftX = x0;
        int rightX = x0 + colW + 5;
        int yL = y0;
        int yR = y0;

        this.addRenderableWidget(CycleButton.onOffBuilder(MiniatureConfig.CLIENT.particleEnabled.get())
                .withInitialValue(MiniatureConfig.CLIENT.particleEnabled.get())
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("bamboomod.config.miniature.particle.enabled.tooltip")))
                .create(leftX, yL, colW, h, Component.translatable("bamboomod.config.miniature.particle.enabled"),
                        (btn, val) -> MiniatureConfig.CLIENT.particleEnabled.set(val)));
        yL += gap;
        this.addRenderableWidget(ConfigWidgets.intCycle(Component.translatable("bamboomod.config.miniature.particle.perMiniaturePerTick"),
                MiniatureConfig.CLIENT.particlesPerMiniaturePerTick.get(), 0, 10, v -> MiniatureConfig.CLIENT.particlesPerMiniaturePerTick.set(v),
                leftX, yL, colW, h));
        yL += gap;
        this.addRenderableWidget(ConfigWidgets.intCycle(Component.translatable("bamboomod.config.miniature.particle.globalMaxPerTick"),
                MiniatureConfig.CLIENT.maxParticlesPerClientTick.get(), 0, 512, v -> MiniatureConfig.CLIENT.maxParticlesPerClientTick.set(v),
                leftX, yL, colW, h));
        yL += gap;
        this.addRenderableWidget(ConfigWidgets.doubleStep(Component.translatable("bamboomod.config.miniature.particle.spawnChance"),
                MiniatureConfig.CLIENT.particleSpawnChance.get(), 0.0, 1.0, 0.1, v -> MiniatureConfig.CLIENT.particleSpawnChance.set(v),
                leftX, yL, colW, h));
        yL += gap;
        this.addRenderableWidget(ConfigWidgets.doubleStep(Component.translatable("bamboomod.config.miniature.particle.distance"),
                MiniatureConfig.CLIENT.particleDistance.get(), 4.0, 128.0, 4.0, v -> MiniatureConfig.CLIENT.particleDistance.set(v),
                leftX, yL, colW, h));
        yL += gap;

        this.addRenderableWidget(ConfigWidgets.intCycle(Component.translatable("bamboomod.config.miniature.particle.tickInterval"),
                MiniatureConfig.CLIENT.particleTickInterval.get(), 1, 20, v -> MiniatureConfig.CLIENT.particleTickInterval.set(v),
                rightX, yR, colW, h));
        yR += gap;
        this.addRenderableWidget(ConfigWidgets.choices(Component.translatable("bamboomod.config.miniature.render.maxCells"),
                String.valueOf(MiniatureConfig.CLIENT.maxCellsPerFrame.get()),
                new String[]{"0","128","256","512","1024","2048","4096","8192","16384","65536"},
                v -> {
                    try { MiniatureConfig.CLIENT.maxCellsPerFrame.set(Integer.parseInt(v)); } catch (Exception e) {}
                }, rightX, yR, colW, h));
        yR += gap;
        this.addRenderableWidget(ConfigWidgets.doubleStep(Component.translatable("bamboomod.config.miniature.render.maxDistance"),
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
    }

    private void buildColoredLightPanel(int x0, int y0, int h, int gap) {
        int avail = this.width - x0 - 10;
        int colW = Math.min(220, Math.max(150, avail));
        int y = y0;

        this.addRenderableWidget(ConfigWidgets.onOff(
                Component.translatable("bamboomod.config.coloredlight.daylight.enabled"),
                Component.translatable("bamboomod.config.coloredlight.daylight.enabled.tooltip"),
                ColoredLightConfig.CLIENT.daylightSuppressEnabled.get(),
                (btn, val) -> {
                    ColoredLightConfig.CLIENT.daylightSuppressEnabled.set(val);
                    ColoredLightDaylightSync.requestResync();
                }, x0, y, colW, h));
        y += gap;
        this.addRenderableWidget(ConfigWidgets.doubleStep(
                Component.translatable("bamboomod.config.coloredlight.daylight.rate"),
                ColoredLightConfig.CLIENT.daylightSuppressRate.get(), 0.0, 1.0, 0.05,
                v -> {
                    ColoredLightConfig.CLIENT.daylightSuppressRate.set(v);
                    ColoredLightDaylightSync.requestResync();
                }, x0, y, colW, h));
        y += gap;
        this.addRenderableWidget(ConfigWidgets.choices(
                Component.translatable("bamboomod.config.coloredlight.daylight.levels"),
                String.valueOf(ColoredLightConfig.CLIENT.daylightLevels.get()),
                new String[]{"2","4","8","16","32"},
                v -> {
                    try { ColoredLightConfig.CLIENT.daylightLevels.set(Integer.parseInt(v)); } catch (Exception e) {}
                    ColoredLightDaylightSync.requestResync();
                }, x0, y, colW, h));
        y += gap;
        this.addRenderableWidget(ConfigWidgets.choices(
                Component.translatable("bamboomod.config.coloredlight.daylight.sections"),
                String.valueOf(ColoredLightConfig.CLIENT.sectionsPerTick.get()),
                new String[]{"2","4","6","8","12","16","32","64"},
                v -> {
                    try { ColoredLightConfig.CLIENT.sectionsPerTick.set(Integer.parseInt(v)); } catch (Exception e) {}
                }, x0, y, colW, h));
        y += gap;
        this.addRenderableWidget(ConfigWidgets.choices(
                Component.translatable("bamboomod.config.coloredlight.daylight.radius"),
                String.valueOf(ColoredLightConfig.CLIENT.dirtyRadius.get()),
                new String[]{"4","8","12","16","24"},
                v -> {
                    try { ColoredLightConfig.CLIENT.dirtyRadius.set(Integer.parseInt(v)); } catch (Exception e) {}
                    ColoredLightDaylightSync.requestResync();
                }, x0, y, colW, h));
        y += gap;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        Component desc = selected == Category.MINIATURE
                ? Component.translatable("bamboomod.config.miniature.desc")
                : Component.translatable("bamboomod.config.coloredlight.desc");
        g.drawCenteredString(this.font, desc, this.width / 2, 27, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}
