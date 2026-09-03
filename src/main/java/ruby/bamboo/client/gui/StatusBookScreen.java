package ruby.bamboo.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import ruby.bamboo.core.init.BambooCapabilities;
import ruby.bamboo.skill.SkillType;

/**
 * ステータス本 (feat-spec-skill §5)。
 * p1=概要 + p2以降=1スキル1ページ (バー+説明+上げ方)。計14p。
 */
public class StatusBookScreen extends Screen {

    private static final int PAGES = 1 + SkillType.values().length;

    private int page = 0;
    private final List<Row> rows = new ArrayList<>();
    private int acquired = 0;
    private int totalLv = 0;

    private record Row(SkillType type, int lv, int xp, int next, int max) {
    }

    public StatusBookScreen() {
        super(Component.translatable("item.bamboomod.status_book"));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getCapability(BambooCapabilities.SKILL).ifPresent(s -> {
                for (SkillType t : SkillType.values()) {
                    int lv = s.getLevel(t);
                    rows.add(new Row(t, lv, s.getXp(t), s.getNext(t), s.getMaxLevel(t)));
                    if (lv > 0) {
                        acquired++;
                    }
                    totalLv += lv;
                }
            });
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int by = this.height - 32;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = (page + PAGES - 1) % PAGES;
            rebuild();
        }).bounds(cx - 110, by, 40, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(cx - 40, by, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = (page + 1) % PAGES;
            rebuild();
        }).bounds(cx + 70, by, 40, 20).build());
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        int sw = this.width;
        int pw = 260;
        int x0 = (sw - pw) / 2;
        int y = 30;
        gfx.fill(x0 - 8, y - 12, x0 + pw + 8, this.height - 40, 0xE0101010);
        if (page == 0) {
            drawCenter(gfx, this.title.getString(), y, 0xFFE8C872);
            y += 18;
            drawCenter(gfx, Component.translatable("screen.bamboomod.status.overview",
                    acquired, SkillType.values().length, totalLv).getString(), y, 0xFFFFFFFF);
            y += 26;
            for (Row r : rows) {
                String line = Component.translatable("item.bamboomod.skill_book_" + r.type().getId()).getString()
                        + " Lv" + r.lv();
                gfx.drawString(this.font, line, x0, y, r.lv() > 0 ? 0xFFAAFFAA : 0xFF777777, false);
                y += 12;
            }
        } else {
            Row r = rows.get(page - 1);
            String name = Component.translatable("item.bamboomod.skill_book_" + r.type().getId()).getString();
            drawCenter(gfx, name, y, 0xFFE8C872);
            y += 16;
            String lvLine = r.lv() > 0
                    ? "Lv" + r.lv() + " / MAX" + r.max()
                    : Component.translatable("screen.bamboomod.status.locked").getString();
            drawCenter(gfx, lvLine, y, 0xFFFFFFFF);
            y += 16;
            if (r.lv() > 0 && r.lv() < r.max()) {
                int bw = pw - 20;
                gfx.fill(x0, y, x0 + bw, y + 8, 0xFF333333);
                int prog = (int) (bw * Math.min(1.0F, r.xp() / (float) Math.max(1, r.next())));
                gfx.fill(x0, y, x0 + prog, y + 8, 0xFF5AC85A);
                drawCenter(gfx, r.xp() + " / " + r.next(), y + 12, 0xFFCCCCCC);
                y += 28;
            } else {
                y += 6;
            }
            y = drawWrapped(gfx, Component.translatable("skill.bamboomod." + r.type().getId() + ".desc").getString(),
                    x0, y, pw, 0xFFFFFFFF) + 8;
            y = drawWrapped(gfx, Component.translatable("skill.bamboomod." + r.type().getId() + ".how").getString(),
                    x0, y, pw, 0xFFAAAAAA) + 8;
        }
        drawCenter(gfx, (page + 1) + " / " + PAGES, this.height - 44, 0xFF888888);
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawCenter(GuiGraphics gfx, String s, int y, int color) {
        gfx.drawString(this.font, s, (this.width - this.font.width(s)) / 2, y, color, true);
    }

    private int drawWrapped(GuiGraphics gfx, String s, int x, int y, int w, int color) {
        for (net.minecraft.util.FormattedCharSequence line : this.font.split(Component.literal(s), w)) {
            gfx.drawString(this.font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }
}
