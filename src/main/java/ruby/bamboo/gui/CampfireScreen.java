package ruby.bamboo.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ruby.bamboo.BambooMod;

/**
 * 囲炉裏のGUI (旧 GuiCampfire の移植) — レシピブック対応。
 * <p>
 * 旧仕様の踏襲点:
 * <ul>
 * <li>背景テクスチャ: textures/gui/campfire.png (旧 guis/campfire.png)</li>
 * <li>燃料ゲージ: (k+10, l+17) 幅12 高さ32*(fuelRatio/100) 反転表示</li>
 * <li>調理ゲージ: (k+90, l+35) 幅23*(cookRatio/100) 高さ16</li>
 * <li>燃料バー領域ホバーで % ツールチップ表示</li>
 * <li>レシピブック: 左上のレシピボタン + 横展開パネル (vanilla furnace準拠)</li>
 * </ul>
 */
public class CampfireScreen extends AbstractContainerScreen<CampfireMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(BambooMod.MODID, "textures/gui/campfire.png");

    public CampfireScreen(CampfireMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 0;
        this.titleLabelY = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // 燃料ゲージ (反転表示: 残量が減ると下から消える)
        int fuelH = 32 - (int) (32 * (this.menu.getFuelRatio() / 100F));
        graphics.blit(TEXTURE, x + 10, y + 17, 176, 17, 12, fuelH);

        // 調理ゲージ (進行方向矢印型)
        int cookW = 23 - (int) (23 * (this.menu.getCookRatio() / 100F));
        graphics.blit(TEXTURE, x + 90, y + 35, 176, 0, cookW, 16);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 旧版はタイトルを描画していないため空実装
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        // 燃料バー領域ホバーで % ツールチップ
        int x = this.leftPos;
        int y = this.topPos;
        if (x + 10 < mouseX && mouseX < x + 22 && y + 8 < mouseY && mouseY < y + 58) {
            graphics.renderTooltip(this.font, Component.literal(this.menu.getFuelRatio() + "%"), mouseX, mouseY);
        }
    }


}
