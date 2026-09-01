package ruby.bamboo.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import ruby.bamboo.core.fishing.FishSize;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.FishingCastResultPacket;
import ruby.bamboo.network.FishingResultPacket;

/**
 * 釣りの仮想GUI。移動ロック。
 * WAITING → HOOKING(！表示0.5-2秒) → (ミス時はCOOLDOWN→50%で再HOOKING/失敗) → MINIGAME → 結果送信して解放。
 * 進行度は常に0スタート、距離は抽選のみ。
 * 背景の暗転は行わない。
 */
public class FishingMinigameScreen extends Screen {

    private final FishingCastResultPacket pkt;
    private enum State { WAITING, HOOKING, HOOK_COOLDOWN, MINIGAME }
    private State state = State.WAITING;

    private int waitCounter = 0;
    private final int waitTicks;

    // hooking
    private int hookingTicksRemaining = 0;
    private int hookingWindowTicks = 0;
    private int cooldownTicksRemaining = 0;
    private int hookAttempts = 0;
    private boolean hasSeenHooking = false;

    // minigame
    private float progress = 0f;
    private float captureY = 40f;
    private float captureVel = 0f;
    private float fishY = 50f;
    private float fishTargetY = 50f;
    private int fishTargetTimer = 0;
    private boolean fishDarting = false;
    private int minigameTicks = 0;

    private boolean rightHeld = false;
    private boolean finished = false;

    private static final int BAR_W = 16;
    private static final int BAR_H = 108;
    private static final int CAPTURE_H = 22;
    private static final int PROG_BAR_W = 6;

    public FishingMinigameScreen(FishingCastResultPacket pkt) {
        super(Component.translatable("screen.bamboomod.fishing_minigame"));
        this.pkt = pkt;
        this.progress = pkt.startProgress; // 20スタート（FishingManagerで決定）
        Minecraft mc = Minecraft.getInstance();
        int range = Math.max(0, pkt.waitMax - pkt.waitMin);
        int rand = (mc.player != null) ? mc.player.getRandom().nextInt(range + 1) : 0;
        this.waitTicks = pkt.waitMin + rand;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics gfx) {
        // 暗くしない
    }

    @Override
    public void tick() {
        super.tick();
        if (finished) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isAlive()) {
            finish(hasSeenHooking ? FishingResultPacket.FAIL : FishingResultPacket.CANCEL);
            return;
        }
        boolean hasRod = player.getMainHandItem().getItem() == BambooItems.BAMBOO_ROD.get()
                || player.getOffhandItem().getItem() == BambooItems.BAMBOO_ROD.get();
        if (!hasRod) {
            finish(hasSeenHooking ? FishingResultPacket.FAIL : FishingResultPacket.CANCEL);
            return;
        }
        if (player.hurtTime > 0) {
            finish(hasSeenHooking ? FishingResultPacket.FAIL : FishingResultPacket.CANCEL);
            return;
        }

        switch (state) {
            case WAITING -> {
                waitCounter++;
                if (waitCounter >= waitTicks) {
                    enterHooking();
                }
            }
            case HOOKING -> {
                hookingTicksRemaining--;
                if (hookingTicksRemaining <= 0) {
                    // 時間切れ → 初回ならクールダウン、2回目なら失敗
                    if (hookAttempts == 1) {
                        state = State.HOOK_COOLDOWN;
                        var rnd = (mc.player != null) ? mc.player.getRandom() : net.minecraft.util.RandomSource.create();
                        cooldownTicksRemaining = 15 + rnd.nextInt(25); // 0.75-2sec
                        mc.gui.setTitle(Component.empty());
                    } else {
                        finish(FishingResultPacket.FAIL);
                    }
                }
            }
            case HOOK_COOLDOWN -> {
                cooldownTicksRemaining--;
                if (cooldownTicksRemaining <= 0) {
                    var rnd = (mc.player != null) ? mc.player.getRandom() : net.minecraft.util.RandomSource.create();
                    boolean retry = rnd.nextFloat() < 0.5f;
                    if (retry) {
                        enterHooking();
                    } else {
                        finish(FishingResultPacket.FAIL);
                    }
                }
            }
            case MINIGAME -> {
                minigameTicks++;
                float accel = rightHeld ? 0.45f : -0.45f;
                captureVel += accel;
                captureVel *= 0.92f;
                captureVel = clamp(captureVel, -3.2f, 3.2f);
                captureY += captureVel;
                captureY = clamp(captureY, 0f, 100f - CAPTURE_H * (100f / BAR_H));
                updateFish();
                boolean overlap = isOverlapping();
                float rodPerTick = 25f / 20f;
                float staminaPerTick = pkt.fishStamina / 20f;
                if (overlap) progress += rodPerTick;
                else progress -= staminaPerTick;
                progress = clamp(progress, 0f, 100f);
                if (progress >= 100f) {
                    finish(FishingResultPacket.SUCCESS);
                } else if (progress <= 0f) {
                    finish(FishingResultPacket.FAIL);
                } else if (minigameTicks > 20 * 30) {
                    finish(FishingResultPacket.FAIL);
                }
            }
        }
    }

    private void enterHooking() {
        hasSeenHooking = true;
        hookAttempts++;
        hookingWindowTicks = computeHookWindowTicks();
        hookingTicksRemaining = hookingWindowTicks;
        state = State.HOOKING;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FISHING_BOBBER_SPLASH, 0.95F));
        mc.gui.setTitle(Component.literal("!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        mc.gui.setTimes(0, hookingWindowTicks + 5, 5);
    }

    private int computeHookWindowTicks() {
        // 魚とのパワー差で 10(0.5s)〜40(2s)
        int stamina = pkt.fishStamina;
        int power = pkt.fishPower;
        // difficulty = stamina + power*2.5 ; 8→40ticks, 33→10ticks
        float diff = stamina + power * 2.5f;
        float window = 40f - (diff - 8f) * 1.2f;
        window = Math.max(10f, Math.min(40f, window));
        return Math.round(window);
    }

    private void updateFish() {
        if (fishTargetTimer <= 0) {
            Minecraft mc = Minecraft.getInstance();
            var random = mc.player != null ? mc.player.getRandom() : net.minecraft.util.RandomSource.create();
            fishDarting = false;
            float newTarget = 50f;
            int pattern = pkt.movePatternOrdinal;
            if (pattern == 3) pattern = random.nextInt(3);
            switch (pattern) {
                case 0 -> {
                    newTarget = random.nextFloat() * 80f + 10f;
                    fishTargetTimer = 25 + random.nextInt(35);
                }
                case 1 -> {
                    boolean dart = random.nextFloat() < 0.22f;
                    if (dart) {
                        float cur = fishY;
                        if (cur < 50) newTarget = 70f + random.nextFloat() * 25f;
                        else newTarget = random.nextFloat() * 25f;
                        fishDarting = true;
                        fishTargetTimer = 12 + random.nextInt(12);
                    } else {
                        newTarget = random.nextFloat() * 80f + 10f;
                        fishTargetTimer = 20 + random.nextInt(30);
                    }
                }
                case 2 -> {
                    if (random.nextFloat() < 0.70f) newTarget = random.nextFloat() * 30f;
                    else newTarget = random.nextFloat() * 60f + 20f;
                    fishTargetTimer = 20 + random.nextInt(40);
                }
                default -> {
                    newTarget = random.nextFloat() * 80f + 10f;
                    fishTargetTimer = 25 + random.nextInt(30);
                }
            }
            fishTargetY = clamp(newTarget, 0f, 95f);
        } else {
            fishTargetTimer--;
        }
        float baseSpeed = 1.0f + pkt.fishPower * 0.7f;
        if (fishDarting) baseSpeed *= 2.2f;
        float diff = fishTargetY - fishY;
        float dir = Math.signum(diff);
        float step = Math.min(Math.abs(diff), baseSpeed + (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getRandom().nextFloat() * 0.6f : 0f));
        fishY += dir * step;
        fishY = clamp(fishY, 0f, 95f);
    }

    private boolean isOverlapping() {
        float capBottom = captureY;
        float capTop = captureY + CAPTURE_H * (100f / BAR_H);
        return fishY >= capBottom - 2f && fishY <= capTop + 2f;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void finish(int resultType) {
        if (finished) return;
        finished = true;
        try {
            BambooNetwork.CHANNEL.sendToServer(new FishingResultPacket(resultType));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTitle(Component.empty());
        if (resultType == FishingResultPacket.SUCCESS) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F));
            if (pkt != null && mc.player != null) {
                var item = ForgeRegistries.ITEMS.getValue(pkt.itemId);
                if (item != null) {
                    ItemStack tmp = new ItemStack(item);
                    FishSize size = FishSize.fromIndex(pkt.sizeOrdinal);
                    int cm = size.representativeCm(pkt.minCm, pkt.midCm, pkt.maxCm);
                    String sizeName = switch (size) {
                        case MIN -> "ミニ";
                        case NORMAL -> "ノーマル";
                        case BIG -> "ビッグ";
                    };
                    Component msg = Component.translatable("message.bamboomod.fishing.success", tmp.getHoverName().getString(), sizeName, cm).withStyle(ChatFormatting.GOLD);
                    mc.player.displayClientMessage(msg, false);
                }
            }
        } else if (resultType == FishingResultPacket.FAIL) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FISHING_BOBBER_SPLASH, 0.5F));
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("message.bamboomod.fishing.fail").withStyle(ChatFormatting.GRAY), true);
            }
        } else {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.7F));
        }
        mc.setScreen(null);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 背景暗転なし
        int sw = this.width;
        int sh = this.height;
        switch (state) {
            case WAITING -> {
                String waitText = Component.translatable("message.bamboomod.fishing.waiting").getString();
                int tw = this.font.width(waitText);
                gfx.drawString(this.font, waitText, (sw - tw) / 2, sh / 2 - 40, 0xFFFFFF, true);
                String distText = Component.translatable("message.bamboomod.fishing.distance", pkt.distance).getString();
                int dw = this.font.width(distText);
                gfx.drawString(this.font, distText, (sw - dw) / 2, sh / 2 - 28, 0xAAAAAA, true);
                int dots = (waitCounter / 10) % 4;
                String dotStr = ".".repeat(dots);
                int dotW = this.font.width(dotStr);
                gfx.drawString(this.font, dotStr, (sw - dotW) / 2, sh / 2 - 16, 0xFFFFFF, true);
                // 回収ヒント（初回のみキャンセル）
                if (!hasSeenHooking) {
                    String cancelHint = Component.translatable("message.bamboomod.fishing.cancel_hint").getString();
                    int cw = this.font.width(cancelHint);
                    gfx.drawString(this.font, cancelHint, (sw - cw) / 2, sh / 2 + 20, 0x777777, true);
                }
            }
            case HOOKING -> {
                // 大きな！を中央に
                String ex = "!";
                // フォントスケールを上げる代わりに複数回描画で強調
                int scale = 4;
                // 中央の大きな！
                gfx.pose().pushPose();
                gfx.pose().scale(4.0f, 4.0f, 4.0f);
                int w = this.font.width(ex) * 4;
                // スケール後の座標は 1/4
                int cx = sw / 2 / 4 - this.font.width(ex) / 2;
                int cy = sh / 2 / 4 - 20;
                gfx.drawString(this.font, ex, cx, cy, 0xFFFF55, true);
                gfx.pose().popPose();
                // 猶予バー (残り時間)
                int barW = 60;
                int barH = 6;
                int bx = (sw - barW) / 2;
                int by = sh / 2 + 10;
                gfx.fill(bx - 1, by - 1, bx + barW + 1, by + barH + 1, 0xFF333333);
                gfx.fill(bx, by, bx + barW, by + barH, 0xFF555555);
                int fill = Math.round((hookingTicksRemaining / (float) hookingWindowTicks) * barW);
                int col = hookingTicksRemaining > hookingWindowTicks * 0.5 ? 0xFF55FF55 : hookingTicksRemaining > hookingWindowTicks * 0.25 ? 0xFFFFFF55 : 0xFFFF5555;
                gfx.fill(bx, by, bx + fill, by + barH, col);
                String hint = Component.translatable("message.bamboomod.fishing.hook_hint").getString();
                int hw = this.font.width(hint);
                gfx.drawString(this.font, hint, (sw - hw) / 2, by + 12, 0xFFFFFF, true);
            }
            case HOOK_COOLDOWN -> {
                String cd = Component.translatable("message.bamboomod.fishing.cooldown").getString();
                int cw = this.font.width(cd);
                gfx.drawString(this.font, cd, (sw - cw) / 2, sh / 2 - 10, 0xAAAAAA, true);
                int dots = ((cooldownTicksRemaining / 6) % 4);
                String dotStr = ".".repeat(dots);
                int dotW = this.font.width(dotStr);
                gfx.drawString(this.font, dotStr, (sw - dotW) / 2, sh / 2 + 2, 0xFFFFFF, true);
            }
            case MINIGAME -> {
                int barX = sw / 2 - BAR_W / 2 - 10;
                int barY = sh / 2 - BAR_H / 2;
                int progBarX = barX - 12;
                int progBarY = barY;
                int progBarH = BAR_H;
                gfx.fill(barX - 1, barY - 1, barX + BAR_W + 1, barY + BAR_H + 1, 0xFF2B2B2B);
                gfx.fill(barX, barY, barX + BAR_W, barY + BAR_H, 0xFF555555);
                gfx.fill(progBarX - 1, progBarY - 1, progBarX + PROG_BAR_W + 1, progBarY + progBarH + 1, 0xFF2B2B2B);
                gfx.fill(progBarX, progBarY, progBarX + PROG_BAR_W, progBarY + progBarH, 0xFF333333);
                int capH = CAPTURE_H;
                // 判定と視覚を一致させるため、共に BAR_H 基準で bottom 位置をマッピング
                int capYpix = barY + BAR_H - Math.round(captureY / 100f * BAR_H) - capH;
                capYpix = Math.max(barY, Math.min(barY + BAR_H - capH, capYpix));
                int capColor = isOverlapping() ? 0xFF5CFF5C : 0xFF3A9E3A;
                gfx.fill(barX, capYpix, barX + BAR_W, capYpix + capH, capColor);
                int fishH = 6;
                int fishYpix = barY + BAR_H - Math.round(fishY / 100f * BAR_H) - fishH;
                fishYpix = Math.max(barY, Math.min(barY + BAR_H - fishH, fishYpix));
                gfx.fill(barX + 2, fishYpix, barX + BAR_W - 2, fishYpix + fishH, 0xFFFFD54F);
                gfx.fill(barX + 3, fishYpix + 1, barX + BAR_W - 3, fishYpix + 2, 0xFFFFF59D);
                int progFillH = Math.round((progress / 100f) * progBarH);
                int progColor = progress > 70 ? 0xFF4FC3F7 : progress > 30 ? 0xFFFFEB3B : 0xFFF44336;
                gfx.fill(progBarX, progBarY + progBarH - progFillH, progBarX + PROG_BAR_W, progBarY + progBarH, progColor);
                if (pkt != null) {
                    FishSize size = FishSize.fromIndex(pkt.sizeOrdinal);
                    int cm = size.representativeCm(pkt.minCm, pkt.midCm, pkt.maxCm);
                    String sizeName = switch (size) { case MIN -> "ミニ"; case NORMAL -> "ノーマル"; case BIG -> "ビッグ"; };
                    var item = ForgeRegistries.ITEMS.getValue(pkt.itemId);
                    if (item != null) {
                        ItemStack tmp = new ItemStack(item);
                        String title = tmp.getHoverName().getString() + " [" + sizeName + " " + cm + "cm]";
                        int tw = this.font.width(title);
                        gfx.drawString(this.font, title, (sw - tw) / 2, barY - 16, 0xFFFFFF, true);
                    }
                    String hint = Component.translatable("message.bamboomod.fishing.hint").getString();
                    int hw = this.font.width(hint);
                    gfx.drawString(this.font, hint, (sw - hw) / 2, barY + BAR_H + 8, 0xAAAAAA, true);
                    String catStr = switch (pkt.categoryOrdinal) { case 1 -> "ゴミ"; case 2 -> "宝"; default -> ""; };
                    if (!catStr.isEmpty()) {
                        gfx.drawString(this.font, catStr, barX + BAR_W + 6, barY + 2, 0xFFCC99, true);
                    }
                }
                String pct = Math.round(progress) + "%";
                int pw = this.font.width(pct);
                gfx.drawString(this.font, pct, progBarX - pw / 2 + PROG_BAR_W / 2, progBarY + progBarH + 4, 0xFFFFFF, true);
            }
        }
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右クリック (1) でフッキング/回収。ミミニゲームは左右どちらでも保持。
        if (state == State.WAITING || state == State.HOOK_COOLDOWN) {
            if (button == 1 || button == 0) {
                // 回収
                if (!hasSeenHooking) {
                    finish(FishingResultPacket.CANCEL);
                } else {
                    finish(FishingResultPacket.FAIL);
                }
                return true;
            }
        } else if (state == State.HOOKING) {
            if (button == 1 || button == 0) {
                // 成功: ミニゲームへ
                Minecraft mc = Minecraft.getInstance();
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F));
                mc.gui.setTitle(Component.empty());
                state = State.MINIGAME;
                minigameTicks = 0;
                // 右クリック保持をリセット
                rightHeld = false;
                return true;
            }
        } else if (state == State.MINIGAME) {
            if (button == 0 || button == 1) {
                rightHeld = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (state == State.MINIGAME && (button == 0 || button == 1)) {
            rightHeld = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            finish(hasSeenHooking ? FishingResultPacket.FAIL : FishingResultPacket.CANCEL);
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyInventory.matches(keyCode, scanCode)) {
            finish(hasSeenHooking ? FishingResultPacket.FAIL : FishingResultPacket.CANCEL);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!finished) {
            finish(hasSeenHooking ? FishingResultPacket.FAIL : FishingResultPacket.CANCEL);
        } else {
            super.onClose();
        }
    }
}
