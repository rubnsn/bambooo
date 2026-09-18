package ruby.bamboo.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ruby.bamboo.client.handler.ClientGachaHandler;
import ruby.bamboo.gacha.CoinWallet;

/**
 * ガチャTop: 残高(スタブ) + 10連ボタン。
 */
public class GachaTopScreen extends Screen {
    public GachaTopScreen() {
        super(Component.translatable("screen.bamboomod.gacha_top"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        String label = CoinWallet.ALWAYS_FREE
                ? Component.translatable("screen.bamboomod.gacha_draw10_free").getString()
                : Component.translatable("screen.bamboomod.gacha_draw10",
                        CoinWallet.COST_10).getString();
        this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
            ClientGachaHandler.requestDraw();
        }).bounds(cx - 100, cy + 10, 200, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.bamboomod.gacha_close"), b -> {
                    this.minecraft.setScreen(null);
                }).bounds(cx - 100, cy + 36, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        int cx = this.width / 2;
        int cy = this.height / 2 - 40;
        g.drawCenteredString(this.font, this.title, cx, cy, 0xFFFFFF);
        String balance = CoinWallet.ALWAYS_FREE
                ? Component.translatable("screen.bamboomod.gacha_balance_free").getString()
                : Component.translatable("screen.bamboomod.gacha_balance", 0).getString();
        g.drawCenteredString(this.font, balance, cx, cy + 16, 0xFFE28A);
        g.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.gacha_top_hint").getString(),
                cx, cy + 30, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
