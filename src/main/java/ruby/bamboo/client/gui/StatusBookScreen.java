package ruby.bamboo.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.core.init.BambooCapabilities;
import ruby.bamboo.skill.SkillType;

/**
 * ステータス本 (feat-spec-skill §5)。
 * p1=概要 + p2以降=1スキル1ページ (バー+説明+上げ方)。計14p。
 */
public class StatusBookScreen extends Screen {

    private static final int OVERVIEW_PER_PAGE = 7;
    private static final Style SGA_STYLE = Style.EMPTY
            .withFont(new ResourceLocation("minecraft", "alt"));

    private int page = 0;
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> unlocked = new ArrayList<>();
    /** 概要用: 取得済み優先 + 未取得は末尾 (マスク表示)。 */
    private final List<Row> overview = new ArrayList<>();

    private record Row(SkillType type, int lv, int xp, int next, int max) {
    }

    public StatusBookScreen() {
        super(Component.translatable("item.bamboomod.status_book"));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getCapability(BambooCapabilities.SKILL).ifPresent(s -> {
                for (SkillType t : SkillType.values()) {
                    int lv = s.getLevel(t);
                    Row r = new Row(t, lv, s.getXp(t), s.getNext(t), s.getMaxLevel(t));
                    rows.add(r);
                    if (lv > 0) {
                        unlocked.add(r);
                    }
                }
            });
        }
        overview.addAll(unlocked);
        for (Row r : rows) {
            if (r.lv() <= 0) {
                overview.add(r);
            }
        }
    }

    private int overviewPages() {
        return Math.max(1, (overview.size() + OVERVIEW_PER_PAGE - 1) / OVERVIEW_PER_PAGE);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int pages() {
        return overviewPages() + unlocked.size();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int by = this.height - 32;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = (page + pages() - 1) % pages();
            rebuild();
        }).bounds(cx - 110, by, 40, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(cx - 40, by, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = (page + 1) % pages();
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
        if (page < overviewPages()) {
            drawCenter(gfx, this.title.getString(), y, 0xFFE8C872);
            y += 20;
            int from = page * OVERVIEW_PER_PAGE;
            int to = Math.min(overview.size(), from + OVERVIEW_PER_PAGE);
            for (int i = from; i < to; i++) {
                Row r = overview.get(i);
                if (r.lv() > 0) {
                    String line = Component.translatable("skill.bamboomod." + r.type().getId() + ".name").getString()
                            + " Lv" + r.lv() + "  " + r.xp() + "/" + r.next();
                    gfx.drawString(this.font, line, x0, y, 0xFFAAFFAA, false);
                    y += 11;
                    int bw = 200;
                    gfx.fill(x0, y, x0 + bw, y + 4, 0xFF333333);
                    int prog = (int) (bw * Math.min(1.0F, r.xp() / (float) Math.max(1, r.next())));
                    gfx.fill(x0, y, x0 + prog, y + 4, 0xFF5AC85A);
                    y += 9;
                } else {
                    // 未取得はエンチャント文字でマスク。Lv・xpは見せない
                    Component masked = Component.translatable("skill.bamboomod." + r.type().getId() + ".name")
                            .withStyle(SGA_STYLE);
                    gfx.drawString(this.font, masked, x0, y, 0xFF777777, false);
                    y += 11;
                    gfx.drawString(this.font, "???", x0, y, 0xFF555555, false);
                    y += 9;
                }
            }
        } else {
            Row r = unlocked.get(page - overviewPages());
            String name = Component.translatable("skill.bamboomod." + r.type().getId() + ".name").getString();
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
            y = drawWrapped(gfx, Component.translatable("skill.bamboomod." + r.type().getId() + ".how").getString(),
                    x0, y, pw, 0xFFAAAAAA) + 8;
        }
        drawCenter(gfx, (page + 1) + " / " + pages(), this.height - 44, 0xFF888888);
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
