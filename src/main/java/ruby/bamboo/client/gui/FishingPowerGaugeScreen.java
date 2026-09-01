package ruby.bamboo.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.FishingCastRequestPacket;

/**
 * キャスティング用パワーゲージ GUI。
 * 自動で 0-100 を往復し、再度右クリックで距離を決定する。
 * この画面が開いている間はプレイヤー移動がロックされる (Screen の仕様)。
 */
public class FishingPowerGaugeScreen extends Screen {

    private float gauge = 0f;
    private int dir = 1;
    private static final float SPEED = 2.8f;

    private static final int BAR_W = 120;
    private static final int BAR_H = 12;

    public FishingPowerGaugeScreen() {
        super(Component.translatable("screen.bamboomod.fishing_power_gauge"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics gfx) {
        // 暗転なし
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            var player = mc.player;
            if (!player.isAlive() || player.hurtTime > 0) {
                stopUsingAndClose();
                return;
            }
            boolean hasRod = player.getMainHandItem().getItem() == ruby.bamboo.core.init.BambooItems.BAMBOO_ROD.get()
                    || player.getOffhandItem().getItem() == ruby.bamboo.core.init.BambooItems.BAMBOO_ROD.get();
            if (!hasRod) {
                stopUsingAndClose();
                return;
            }
            // 後ろに引くモーションを保持（BOWの満引き状態を維持）
            keepBowPull(player);
        }
        gauge += dir * SPEED;
        if (gauge >= 100f) {
            gauge = 100f;
            dir = -1;
        } else if (gauge <= 0f) {
            gauge = 0f;
            dir = 1;
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 半透明背景は描かない (ワールドを見せたい)
        // 中央のゲージを描画
        int sw = this.width;
        int sh = this.height;
        int barX = (sw - BAR_W) / 2;
        int barY = sh / 2 - 20;

        // 背景
        gfx.fill(barX - 2, barY - 2, barX + BAR_W + 2, barY + BAR_H + 2, 0xFF2B2B2B);
        gfx.fill(barX, barY, barX + BAR_W, barY + BAR_H, 0xFF333333);
        // フィル
        int fillW = Math.round(gauge / 100f * BAR_W);
        int col = gauge < 33 ? 0xFF4CAF50 : gauge < 66 ? 0xFFFFC107 : 0xFFF44336;
        gfx.fill(barX, barY, barX + fillW, barY + BAR_H, col);
        // インジケーター線上端
        int indX = barX + fillW;
        gfx.fill(indX - 1, barY - 4, indX + 1, barY + BAR_H + 4, 0xFFFFFFFF);

        // テキスト
        String title = Component.translatable("screen.bamboomod.fishing_power_gauge.title").getString();
        int tw = this.font.width(title);
        gfx.drawString(this.font, title, (sw - tw) / 2, barY - 20, 0xFFFFFF, true);

        String hint = Component.translatable("screen.bamboomod.fishing_power_gauge.hint").getString();
        int hw = this.font.width(hint);
        gfx.drawString(this.font, hint, (sw - hw) / 2, barY + BAR_H + 10, 0xAAAAAA, true);

        // 距離プレビュー
        int dist = 4 + Math.round(gauge / 100f * 11);
        String distStr = Component.translatable("message.bamboomod.fishing.distance", dist).getString();
        int dw = this.font.width(distStr);
        gfx.drawString(this.font, distStr, (sw - dw) / 2, barY + BAR_H + 22, 0xFFFFFF, true);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右クリック (1) / 左クリック (0) どちらでも決定
        if (button == 0 || button == 1) {
            confirm();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // スペースや E で決定も許可? 右クリック以外は ESC でキャンセル扱い
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        int dist = 4 + Math.round(gauge / 100f * 11);
        dist = Math.max(4, Math.min(15, dist));
        try {
            BambooNetwork.CHANNEL.sendToServer(new FishingCastRequestPacket(dist));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FISHING_BOBBER_THROW, 0.9F));
        // 戻す動作
        if (mc.player != null) {
            mc.player.stopUsingItem();
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        mc.setScreen(null);
    }

    @Override
    public void onClose() {
        // キャンセル: 何も送らず閉じる (pending 無しなので餌無消費) + 戻す動作
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
        }
        Minecraft.getInstance().setScreen(null);
    }

    private void stopUsingAndClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isUsingItem()) mc.player.stopUsingItem();
        mc.setScreen(null);
    }

    private void keepBowPull(net.minecraft.world.entity.player.Player player) {
        // 初回は BambooRodItem.use で startUsing 済み。以降は満引きを維持
        if (!player.isUsingItem()) {
            // 離されたら再開して引く動作を維持
            InteractionHand hand = InteractionHand.MAIN_HAND;
            if (player.getOffhandItem().getItem() == ruby.bamboo.core.init.BambooItems.BAMBOO_ROD.get())
                hand = InteractionHand.OFF_HAND;
            player.startUsingItem(hand);
        }
        // 満引き状態を維持するため remaining を 72000-20 に固定
        try {
            java.lang.reflect.Field f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("useItemRemaining");
            f.setAccessible(true);
            int dur = 72000;
            // BOW の満引きは 20tick なので 20 を引いた値を維持
            f.setInt(player, dur - 20);
        } catch (Exception ignored) {
            try {
                java.lang.reflect.Field f2 = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("f_21209_");
                f2.setAccessible(true);
                f2.setInt(player, 72000 - 20);
            } catch (Exception ignored2) {}
        }
    }
}
