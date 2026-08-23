package ruby.bamboo.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ruby.bamboo.BambooMod;
import ruby.bamboo.client.gui.BambooMillStoneRecipeBookComponent;

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
public class MillStoneScreen extends AbstractContainerScreen<MillStoneMenu> implements RecipeUpdateListener {

    private static final ResourceLocation TEXTURE = new ResourceLocation(BambooMod.MODID, "textures/gui/millstone.png");
    private static final ResourceLocation RECIPE_BUTTON_LOCATION = new ResourceLocation("textures/gui/recipe_button.png");

    private final BambooMillStoneRecipeBookComponent recipeBookComponent = new BambooMillStoneRecipeBookComponent();
    private boolean widthTooNarrow;

    public MillStoneScreen(MillStoneMenu menu, Inventory playerInventory, Component title) {
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
        // 石臼は左側が広いため作業台/カマド同様の左寄せボタン (leftPos+20, height/2-49)
        this.addRenderableWidget(new ImageButton(this.leftPos + 20, this.height / 2 - 49, 20, 18, 0, 0, 19,
                RECIPE_BUTTON_LOCATION, button -> {
                    this.recipeBookComponent.toggleVisibility();
                    this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
                    button.setPosition(this.leftPos + 20, this.height / 2 - 49);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.recipeBookComponent.mouseClicked(mouseX, mouseY, button)) return true;
        else return this.widthTooNarrow && this.recipeBookComponent.isVisible() ? true : super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void slotClicked(net.minecraft.world.inventory.Slot slot, int slotId, int mouseButton, net.minecraft.world.inventory.ClickType type) {
        super.slotClicked(slot, slotId, mouseButton, type);
        this.recipeBookComponent.slotClicked(slot);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.recipeBookComponent.keyPressed(keyCode, scanCode, modifiers)) return true;
        else return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean flag = mouseX < (double) guiLeft || mouseY < (double) guiTop || mouseX >= (double) (guiLeft + this.imageWidth)
                || mouseY >= (double) (guiTop + this.imageHeight);
        return this.recipeBookComponent.hasClickedOutside(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, mouseButton) && flag;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.recipeBookComponent.charTyped(codePoint, modifiers)) return true;
        else return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void recipesUpdated() { this.recipeBookComponent.recipesUpdated(); }

    @Override
    public net.minecraft.client.gui.screens.recipebook.RecipeBookComponent getRecipeBookComponent() { return this.recipeBookComponent; }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        Float chance = this.recipeBookComponent.getCurrentBonusChance();
        if (chance != null) {
            int x = this.leftPos;
            int y = this.topPos;
            // ボーナススロット(102,57) 16x16 ホバーで「アイテム名（確率％）」表示
            if (x + 102 <= mouseX && mouseX < x + 118 && y + 57 <= mouseY && mouseY < y + 73) {
                int pct = Math.round(chance * 100);
                var bonusStack = this.recipeBookComponent.getCurrentBonusStack();
                Component name = bonusStack != null && !bonusStack.isEmpty() ? bonusStack.getHoverName() : Component.literal("Bonus");
                graphics.renderTooltip(this.font, Component.translatable("tooltip.bamboomod.millstone.bonus", name.getString(), pct), mouseX, mouseY);
            }
        }
    }
}
