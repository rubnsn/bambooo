package ruby.bamboo.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ruby.bamboo.BambooMod;

/**
 * 袋の GUI (旧 GuiSack の移植)。
 * <p>
 * 背景: textures/gui/sack.png (旧 guis/guisack.png 256x256 を流用)。
 * 1 スロット + プレイヤーインベントリ。タイトルは旧版同様に描画しない。
 */
public class SackScreen extends AbstractContainerScreen<SackMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(BambooMod.MODID, "textures/gui/sack.png");

    public SackScreen(SackMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 0;
        this.titleLabelY = 0;
        this.inventoryLabelX = 0;
        this.inventoryLabelY = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    /** 旧版はタイトルを描画していないため空実装 */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // no-op
    }
}
