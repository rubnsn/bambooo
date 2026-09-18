package ruby.bamboo.client.gui;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import ruby.bamboo.block.entity.GachaBlockRenderer;
import ruby.bamboo.client.handler.ClientGachaHandler;
import ruby.bamboo.gacha.GachaRarity;

/**
 * ガチャSpin: 3Dマシン + レバーボタン。排出カプセル色でノーマル/レア演出。
 * <p>
 * 結果到着前は待機表示。レバークリックで40tick回転→排出→Openへ。
 */
public class GachaSpinScreen extends Screen {
    private static final int SPIN_TICKS = 40;

    private List<GachaRarity> rarities;
    private boolean failed;
    private boolean spinning;
    private int spinTick;
    private float rotor;
    private float lever;
    private Button leverButton;
    private long flashStart = -1;
    private boolean srFlash;

    public GachaSpinScreen() {
        super(Component.translatable("screen.bamboomod.gacha_spin"));
        if (!ClientGachaHandler.isWaiting()) {
            this.rarities = ClientGachaHandler.rarities();
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.leverButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.bamboomod.gacha_lever"), b -> pullLever())
                .bounds(cx - 100, this.height - 40, 200, 20).build());
        updateButton();
    }

    private void updateButton() {
        if (this.leverButton == null) {
            return;
        }
        this.leverButton.active = !spinning && rarities != null && !rarities.isEmpty();
    }

    /** サーバー結果到着。 */
    public void onDrawArrived(List<GachaRarity> r) {
        this.rarities = r;
        updateButton();
    }

    public void onDrawFailed() {
        this.failed = true;
        updateButton();
    }

    private void pullLever() {
        if (spinning || rarities == null || rarities.isEmpty()) {
            return;
        }
        spinning = true;
        spinTick = 0;
        lever = 90.0F;
        updateButton();
        play(SoundEvents.LEVER_CLICK, 1.0F);
    }

    private void play(net.minecraft.sounds.SoundEvent e, float pitch) {
        var p = Minecraft.getInstance().player;
        if (p != null) {
            p.playSound(e, 0.6F, pitch);
        }
    }

    @Override
    public void tick() {
        rotor += spinning ? 12.0F : 1.0F;
        if (rotor >= 360.0F) {
            rotor -= 360.0F;
        }
        if (lever > 0) {
            lever = Math.max(0, lever - 6.0F);
        }
        if (spinning) {
            spinTick++;
            if (spinTick >= SPIN_TICKS) {
                spinning = false;
                onEjected();
            }
        }
    }

    private void onEjected() {
        // 排出カプセル = 最高レアで演出分岐
        GachaRarity best = GachaRarity.COMMON;
        for (GachaRarity r : rarities) {
            if (r.ordinal() > best.ordinal()) {
                best = r;
            }
        }
        flashStart = System.currentTimeMillis();
        if (best == GachaRarity.SUPER_RARE) {
            srFlash = true;
            play(SoundEvents.TOTEM_USE, 1.2F);
        } else if (best == GachaRarity.RARE) {
            srFlash = false;
            play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5F);
        } else {
            play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F);
        }
        // 余韻後にOpenへ (C=600ms / R=900ms / SR=1400ms)
        long delay = best == GachaRarity.SUPER_RARE ? 1400 : best == GachaRarity.RARE ? 900 : 600;
        List<GachaRarity> rs = rarities;
        new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
            }
            Minecraft.getInstance().execute(ClientGachaHandler::openOpenScreen);
        }).start();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // 診断中: 背後のワールドを隠す不透明背景 (GUI描画とワールド実物の判別用・暫定)
        g.fill(0, 0, this.width, this.height, 0xFF0C0C10);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 14, 0xFFFFFF);
        if (failed) {
            super.render(g, mouseX, mouseY, partial);
            g.drawCenteredString(this.font,
                    Component.translatable("screen.bamboomod.gacha_failed").getString(),
                    cx, 30, 0xFF6B5E);
            return;
        }
        if (rarities == null) {
            super.render(g, mouseX, mouseY, partial);
            g.drawCenteredString(this.font,
                    Component.translatable("screen.bamboomod.gacha_waiting").getString(),
                    cx, 30, 0xA0A0A0);
            return;
        }

        // 3Dマシン (GUI内モデル)。主役なので画面いっぱいに大きく描き、
        // ボタンは手前に重ねる (描画順: マシン→ウィジェット)。
        GachaRarity best = GachaRarity.COMMON;
        for (GachaRarity r : rarities) {
            if (r.ordinal() > best.ordinal()) {
                best = r;
            }
        }
        renderMachine3d(g, cx, this.height - 46, best.capsuleColor, spinning);
        super.render(g, mouseX, mouseY, partial);

        // フラッシュ演出 (排出直後)
        if (flashStart > 0) {
            long el = System.currentTimeMillis() - flashStart;
            long dur = srFlash ? 900 : 400;
            if (el < dur) {
                float a = 1.0F - (float) el / dur;
                int col = srFlash ? 0xFFD700 : 0xFFFFFF;
                int alpha = (int) (a * 120) << 24;
                g.fill(0, 0, this.width, this.height, alpha | (col & 0xFFFFFF));
                g.drawCenteredString(this.font,
                        Component.translatable(best.langKey).getString(), cx, 32, col);
            }
        }
    }

    private void renderMachine3d(GuiGraphics g, int x, int y, int capsuleColor, boolean fast) {
        var mc = Minecraft.getInstance();
        com.mojang.blaze3d.vertex.PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 50.0);
        // タイトル下〜ボタン上に31uが収まるよう縮小 (足元はボタン裏に回さない)
        // ModelPartは頂点化で1/16されるため画面sピクセル/uには16倍が必要
        float s3d = Math.max(48.0F, (this.height - 90) / 2.0F);
        // 注意: scale(s,-s,s) のY反転は行列式が負になり表裏逆転→カリングで全消去される。
        // 等価な回転 (X180+Y180) で上下反転する (行列式+1で表裏・法線を維持、前面も維持)
        pose.scale(s3d, s3d, s3d);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0F));
        // ショーケース回転はやめて正面固定 (回るのは球内のカプセルのみ)。本体が回ると見にくい
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
        RenderSystem.enableDepthTest();
        // 背後のワールド深度が残っていると深度テストで負けて消えるため消去する
        // (clearDepthは値設定のみで消去しないため clear(256=DEPTH) が必要)
        RenderSystem.clear(256, Minecraft.ON_OSX);
        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        // 霧の無効化は一旦外す (色変わりの容疑箇所・バニラ人形も触らない。
        // 検証で消えたら戻す)
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        try {
            // GUI専用組み立て (ワールドと切り離し)。素のdispatcher呼び出しは
            // 影描画がGUIで失敗して後続フラッシュを巻き添えにするため置かない
            // 通常表示に戻す (左右比較終了。光経路は正常と確定済み)
            // packedOverlay は生0ではなく NO_OVERLAY (0は赤フラッシュ行を指す)
            GachaBlockRenderer.renderMachineGui(pose, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    this.rotor, this.lever,
                    capsuleColor, false, 1.0F);
            buffers.endBatch();
        } catch (Exception e) {
            ruby.bamboo.BambooMod.LOGGER.warn("GachaSpin 3D render failed", e);
        }
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        pose.popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
