package ruby.bamboo.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.entity.FishTrapBlockEntity;

/**
 * 魚捕り籠のGUI (textures/gui/fish_trap.png)。
 * <p>
 * 中央の矢印が生産インジケーター。矢印素体 (76, 36, 22x15) の上に、
 * 右上の進捗スプライト (176, 0) を進行度に比例した幅で重ね描きする。
 * タイトル文字列は描画しない。
 */
public class FishTrapScreen extends AbstractContainerScreen<FishTrapMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "textures/gui/fish_trap.png");

    /** 矢印素体の左上 (GUI座標) */
    private static final int ARROW_X = 76;
    private static final int ARROW_Y = 36;
    /** 矢印の全幅 (px)。高さ15 */
    private static final int ARROW_WIDTH = 22;
    private static final int ARROW_HEIGHT = 15;
    /** 進捗スプライトの左上 (テクスチャ座標) */
    private static final int PROGRESS_U = 176;
    private static final int PROGRESS_V = 1;

    public FishTrapScreen(FishTrapMenu menu, Inventory playerInventory, Component title) {
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

        int progress = this.menu.getProgress();
        if (progress > 0) {
            int width = (int) ((float) progress / FishTrapBlockEntity.MAX_PROGRESS * ARROW_WIDTH);
            if (width > 0) {
                graphics.blit(TEXTURE, x + ARROW_X, y + ARROW_Y,
                        PROGRESS_U, PROGRESS_V, width, ARROW_HEIGHT);
            }
        }
    }

    /** タイトルを描画しないため空実装 */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // no-op
    }
}
