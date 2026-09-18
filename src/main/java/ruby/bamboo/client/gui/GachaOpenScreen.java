package ruby.bamboo.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import ruby.bamboo.block.entity.GachaBlockRenderer;
import ruby.bamboo.client.handler.ClientGachaHandler;
import ruby.bamboo.gacha.GachaRarity;

/**
 * ガチャOpen: カプセル5x2=10個を端から順に開封。
 * 四角カプセルの3Dモデルがパカッと割れて中身が見える。
 * C=即開示 / R=白フラッシュ / SR=金フラッシュ+拡大+SE。全開封で「受取る」→払い出し。
 */
public class GachaOpenScreen extends Screen {
    private final List<GachaRarity> rarities;
    private final List<ItemStack> stacks;
    /** 開封済みフラグ (端からの順番強制のため次indexのみ開封可) */
    private final List<Boolean> opened = new ArrayList<>();
    private int openedCount;
    private Button claimButton;
    private long flashStart = -1;
    private boolean flashSr;
    private int flashIndex = -1;

    public GachaOpenScreen(List<GachaRarity> rarities, List<ItemStack> stacks) {
        super(Component.translatable("screen.bamboomod.gacha_open"));
        this.rarities = new ArrayList<>(rarities);
        this.stacks = new ArrayList<>(stacks);
        for (int i = 0; i < this.rarities.size(); i++) {
            this.opened.add(false);
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        // カプセル10個ボタン (5x2)。順番強制: 次の1個だけactive
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            int col = i % 5;
            int row = i / 5;
            int bx = cx - 130 + col * 54;
            int by = 60 + row * 70;
            this.addRenderableWidget(Button.builder(Component.literal("?"), b -> openOne(idx))
                    .bounds(bx, by, 48, 40).build());
        }
        this.claimButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.bamboomod.gacha_claim"), b -> {
                    ClientGachaHandler.claimAndClose();
                }).bounds(cx - 100, this.height - 34, 200, 20).build());
        refreshButtons();
    }

    private void refreshButtons() {
        for (int i = 0; i < 10; i++) {
            if (this.children().size() <= i) {
                break;
            }
        }
        // children順序依存を避けるため renderables から設定
        int bi = 0;
        for (var w : this.renderables) {
            if (w instanceof Button b && bi < 10) {
                b.active = !opened.get(bi) && bi == openedCount;
                bi++;
            }
        }
        if (this.claimButton != null) {
            this.claimButton.active = openedCount >= 10;
        }
    }

    private void openOne(int idx) {
        if (idx != openedCount || opened.get(idx)) {
            return;
        }
        opened.set(idx, true);
        openedCount++;
        GachaRarity r = rarities.get(idx);
        var p = Minecraft.getInstance().player;
        if (r == GachaRarity.SUPER_RARE) {
            flashStart = System.currentTimeMillis();
            flashSr = true;
            flashIndex = idx;
            if (p != null) {
                p.playSound(SoundEvents.TOTEM_USE, 0.5F, 1.2F);
            }
        } else if (r == GachaRarity.RARE) {
            flashStart = System.currentTimeMillis();
            flashSr = false;
            flashIndex = idx;
            if (p != null) {
                p.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.5F);
            }
        } else {
            if (p != null) {
                p.playSound(SoundEvents.BUNDLE_INSERT, 0.5F, 1.0F);
            }
        }
        // ボタン表示を消す (中身名は枠下に描く)
        int bi = 0;
        for (var w : this.renderables) {
            if (w instanceof Button b && bi < 10) {
                if (bi == idx) {
                    b.setMessage(Component.literal(""));
                    b.active = false;
                }
                bi++;
            }
        }
        refreshButtons();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.gacha_open_hint",
                        openedCount, 10).getString(),
                cx, 26, 0xA0A0A0);

        // カプセル枠 + 3D四角カプセル (開封でパカッと割れて中身表示)
        long now = System.currentTimeMillis();
        RenderSystem.enableDepthTest();
        // 背後のワールド深度が残っていると3Dが消えるため消去する
        RenderSystem.clear(256, Minecraft.ON_OSX);
        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        for (int i = 0; i < 10; i++) {
            int col = i % 5;
            int row = i / 5;
            int bx = cx - 130 + col * 54;
            int by = 60 + row * 70;
            // 未開封は「?」のまま色だけ表示、開封済みは枠を金/銀に
            int frame = !opened.get(i) ? 0xFF808080
                    : rarities.get(i) == GachaRarity.SUPER_RARE ? 0xFFFFD700
                            : rarities.get(i) == GachaRarity.RARE ? 0xFF9FD8FF : 0xFF606060;
            g.fill(bx - 2, by - 14, bx + 50, by + 42, 0x80000000);
            g.fill(bx - 2, by - 14, bx + 50, by - 12, frame);
            g.fill(bx - 2, by + 40, bx + 50, by + 42, frame);
            g.fill(bx - 2, by - 14, bx, by + 42, frame);
            g.fill(bx + 48, by - 14, bx + 50, by + 42, frame);
            // 四角カプセル3D (枠中央)
            renderCapsule3d(g, bx + 24, by + 22, rarities.get(i), opened.get(i));
            if (!opened.get(i)) {
                g.drawCenteredString(this.font, "?", bx + 24, by + 14, 0x202020);
            } else {
                // 割れた上から中身を覗かせる + 枠下に名前
                g.renderItem(stacks.get(i), bx + 16, by - 10);
                g.renderItemDecorations(this.font, stacks.get(i), bx + 16, by - 10);
                g.drawCenteredString(this.font, trimName(stacks.get(i)), bx + 24, by + 44,
                        0xFFFFFF);
            }
            // SR開封直後の拡大フラッシュ
            if (flashIndex == i && flashStart > 0 && now - flashStart < (flashSr ? 600 : 250)) {
                float a = 1.0F - (float) (now - flashStart) / (flashSr ? 600 : 250);
                int fcol = flashSr ? 0xFFD700 : 0xFFFFFF;
                g.fill(bx - 4, by - 16, bx + 52, by + 44,
                        ((int) (a * 110) << 24) | (fcol & 0xFFFFFF));
            }
        }
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        if (openedCount >= 10) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.bamboomod.gacha_all_open").getString(),
                    cx, this.height - 50, 0xFFE28A);
        }
    }

    /** 四角カプセル単体の3D描画 (BERと共用)。開封済みは上半分が跳ね上がる。 */
    private void renderCapsule3d(GuiGraphics g, int x, int y, GachaRarity r, boolean open) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 200.0);
        // Y反転スケールは表裏逆転でカリング消去されるため回転で上下反転 (Spin画面と同理由)。
        // ModelPartは頂点化で1/16されるため、カプセル4uを約24pxで見せるには96倍が必要
        pose.scale(96.0F, 96.0F, 96.0F);
        pose.mulPose(Axis.XP.rotationDegrees(180.0F));
        pose.mulPose(Axis.YP.rotationDegrees(205.0F));
        int col = r.capsuleColor;
        float cr = (col >> 16 & 0xFF) / 255.0F;
        float cg = (col >> 8 & 0xFF) / 255.0F;
        float cb = (col & 0xFF) / 255.0F;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            // packedOverlay は生0ではなく NO_OVERLAY (0は赤フラッシュ行を指す)
            GachaBlockRenderer.renderCapsule(pose, buffers, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    cr, cg, cb, open);
            buffers.endBatch();
        } catch (Exception e) {
            ruby.bamboo.BambooMod.LOGGER.warn("GachaOpen 3D render failed", e);
        }
        pose.popPose();
    }

    /** 枠下に収まるよう中身名を詰める。 */
    private String trimName(ItemStack s) {
        String n = s.getHoverName().getString();
        while (this.font.width(n) > 50 && n.length() > 1) {
            n = n.substring(0, n.length() - 1);
        }
        return n;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // 閉じるボタン(ESC)では払い出さない。必ず「受取る」経由 (複製・紛失防止)
    }
}
