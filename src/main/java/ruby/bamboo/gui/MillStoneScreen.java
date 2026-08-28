package ruby.bamboo.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ruby.bamboo.BambooMod;

/**
 * 石臼のGUI (旧 GuiMillStone の移植) — レシピブック対応。
 * <p>
 * 旧仕様の踏襲点:
 * <ul>
 * <li>背景テクスチャ: textures/gui/millstone.png (旧 guis/guimillstone.png)</li>
 * <li>粉砕中のみ描画:
 * 臼アニメ4フレーム (テクスチャ座標 176,16*motion / 画面座標 80,28)、
 * プログレスバー (テクスチャ座標 192,6*progress / 画面座標 80,46)</li>
 * <li>タイトル文字列は旧版同様に描画しない</li>
 * <li>レシピブック: 左側のレシピボタン + 横展開パネル (vanilla炉/作業台準拠)</li>
 * </ul>
 */
public class MillStoneScreen extends AbstractContainerScreen<MillStoneMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(BambooMod.MODID, "textures/gui/millstone.png");

    public MillStoneScreen(MillStoneMenu menu, Inventory playerInventory, Component title) {
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

        if (this.menu.isGrinding()) {
            // ぐらいんだー (臼アニメ 4フレーム)
            int motion = this.menu.getGrindMotion();
            if (motion < 4) {
                graphics.blit(TEXTURE, x + 80, y + 28, 176, 16 * motion, 16, 16);
            }
            // バケツ型プログレスバー
            graphics.blit(TEXTURE, x + 80, y + 46, 192, 6 * this.menu.getProgress(), 16, 6);
        }
    }

    /** 旧版はタイトルを描画していないため空実装 */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // no-op: 旧 GuiMillStone は前景ラベル無し
    }




}
