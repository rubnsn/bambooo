package ruby.bamboo.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.WishRequestPacket;

@OnlyIn(Dist.CLIENT)
public class WishScreen extends Screen {

    private EditBox editBox;
    private Button wishButton;

    public WishScreen() {
        super(Component.translatable("bamboomod.wish.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.editBox = new EditBox(this.font, centerX - 120, centerY - 10, 240, 20, Component.translatable("bamboomod.wish.title"));
        this.editBox.setMaxLength(30);
        this.editBox.setCanLoseFocus(false);
        this.addRenderableWidget(this.editBox);
        this.setInitialFocus(this.editBox);
        this.editBox.setFocused(true);
        this.setFocused(this.editBox);

        this.wishButton = Button.builder(Component.translatable("bamboomod.wish.button"), b -> this.sendWish())
                .bounds(centerX - 50, centerY + 20, 100, 20).build();
        this.addRenderableWidget(this.wishButton);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.editBox != null && !this.editBox.isFocused()) {
            this.editBox.setFocused(true);
            this.setFocused(this.editBox);
        }
    }

    private void sendWish() {
        String wish = this.editBox.getValue();
        if (wish == null) {
            wish = "";
        }
        wish = wish.trim();
        if (wish.length() > 30) {
            wish = wish.substring(0, 30);
        }
        wish = wish.replaceAll("\\p{Cntrl}", "");
        BambooNetwork.CHANNEL.sendToServer(new WishRequestPacket(wish));
        this.onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gfx);
        gfx.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.GOLD), this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.sendWish();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
