package ruby.bamboo.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ruby.bamboo.BambooMod;
import ruby.bamboo.client.gui.BambooCampfireRecipeBookComponent;

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
public class CampfireScreen extends AbstractContainerScreen<CampfireMenu> implements RecipeUpdateListener {

    private static final ResourceLocation TEXTURE = new ResourceLocation(BambooMod.MODID, "textures/gui/campfire.png");
    private static final ResourceLocation RECIPE_BUTTON_LOCATION = new ResourceLocation("textures/gui/recipe_button.png");

    private final BambooCampfireRecipeBookComponent recipeBookComponent = new BambooCampfireRecipeBookComponent();
    private boolean widthTooNarrow;

    public CampfireScreen(CampfireMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // 旧 xSize/ySize デフォルト (176x166)
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 0;
        this.titleLabelY = 0;
        this.widthTooNarrow = this.width < 379;
        this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
        this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
        // ボタン位置: プログレスバー矢印(x+90,y+35 size23x16)の少し下 (y+62付近) に配置。 vanillaはheight/2-49(≈topPos+34)だが、囲炉裏は少し下げてheight/2-21(≈topPos+62)
        int btnY = this.height / 2 - 31;
        this.addRenderableWidget(new ImageButton(this.leftPos + 90, btnY, 20, 18, 0, 0, 19,
                RECIPE_BUTTON_LOCATION, button -> {
                    this.recipeBookComponent.toggleVisibility();
                    this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
                    button.setPosition(this.leftPos + 90, btnY);
                }));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.recipeBookComponent.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        if (this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
            this.renderBg(graphics, partialTick, mouseX, mouseY);
            this.recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
        } else {
            this.recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
            super.render(graphics, mouseX, mouseY, partialTick);
            this.recipeBookComponent.renderGhostRecipe(graphics, this.leftPos, this.topPos, true, partialTick);
        }
        this.renderTooltip(graphics, mouseX, mouseY);
        this.recipeBookComponent.renderTooltip(graphics, this.leftPos, this.topPos, mouseX, mouseY);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.recipeBookComponent.mouseClicked(mouseX, mouseY, button)) {
            return true;
        } else {
            return this.widthTooNarrow && this.recipeBookComponent.isVisible() ? true : super.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    protected void slotClicked(net.minecraft.world.inventory.Slot slot, int slotId, int mouseButton, net.minecraft.world.inventory.ClickType type) {
        super.slotClicked(slot, slotId, mouseButton, type);
        this.recipeBookComponent.slotClicked(slot);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.recipeBookComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean flag = mouseX < (double) guiLeft || mouseY < (double) guiTop || mouseX >= (double) (guiLeft + this.imageWidth)
                || mouseY >= (double) (guiTop + this.imageHeight);
        return this.recipeBookComponent.hasClickedOutside(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth,
                this.imageHeight, mouseButton) && flag;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.recipeBookComponent.charTyped(codePoint, modifiers)) {
            return true;
        } else {
            return super.charTyped(codePoint, modifiers);
        }
    }

    @Override
    public void recipesUpdated() {
        this.recipeBookComponent.recipesUpdated();
    }

    @Override
    public net.minecraft.client.gui.screens.recipebook.RecipeBookComponent getRecipeBookComponent() {
        return this.recipeBookComponent;
    }
}
