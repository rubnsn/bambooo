package ruby.bamboo.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import ruby.bamboo.client.handler.ClientSkillReadHandler;

/**
 * 読書演出画面 (移動ロック用、feat-spec-skill §4)。
 * 100tickの進行バー + 20tickごとのページめくり音。判定はサーバー権威。
 * Esc等で閉じると中断として扱われる。
 */
public class ReadingScreen extends Screen {

    private final String skillId;
    private int ticks = 0;

    public ReadingScreen(String skillId) {
        super(Component.translatable("item.bamboomod.skill_book_" + skillId));
        this.skillId = skillId;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (ticks % 20 == 0) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    @Override
    public void removed() {
        super.removed();
        ClientSkillReadHandler.onScreenClosed();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int sw = this.width;
        int sh = this.height;
        int pw = 220;
        int ph = 110;
        int x0 = (sw - pw) / 2;
        int y0 = (sh - ph) / 2;
        gfx.fill(x0, y0, x0 + pw, y0 + ph, 0xE0101010);
        gfx.renderOutline(x0, y0, pw, ph, 0xFF8B5A2B);
        String title = this.title.getString();
        gfx.drawString(this.font, title, x0 + (pw - this.font.width(title)) / 2, y0 + 14, 0xFFE8C872, true);
        String flavor = Component.translatable("screen.bamboomod.reading.flavor").getString();
        gfx.drawString(this.font, flavor, x0 + (pw - this.font.width(flavor)) / 2, y0 + 30, 0xFFAAAAAA, true);
        int bw = pw - 40;
        int bx = x0 + 20;
        int by = y0 + 52;
        gfx.fill(bx, by, bx + bw, by + 10, 0xFF333333);
        int prog = (int) (bw * Math.min(1.0F, ticks / 100.0F));
        gfx.fill(bx, by, bx + prog, by + 10, 0xFF5AC85A);
        String pct = (int) (Math.min(1.0F, ticks / 100.0F) * 100.0F) + "%";
        gfx.drawString(this.font, pct, x0 + (pw - this.font.width(pct)) / 2, by + 16, 0xFFFFFFFF, true);
        String hint = Component.translatable("screen.bamboomod.reading.hint").getString();
        gfx.drawString(this.font, hint, x0 + (pw - this.font.width(hint)) / 2, y0 + ph - 16, 0xFF777777, false);
    }
}
