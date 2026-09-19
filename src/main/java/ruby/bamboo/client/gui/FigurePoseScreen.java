package ruby.bamboo.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import ruby.bamboo.client.FigurePose;
import ruby.bamboo.client.renderer.FigureRenderer;
import ruby.bamboo.entity.FigureEntity;
import ruby.bamboo.item.MonsterFigureItem;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.FigurePosePacket;

/**
 * フィギュア調整GUI (docs §10)。プレビューを見ながらサイズ・向き・可動部角度を設定する。
 * 変更は閉じる時にサーバーへ一括送信する。
 */
public class FigurePoseScreen extends Screen {

    private final FigureEntity figure;
    private final String entityId;
    private final float realHeight;
    private final float minScale;
    private LivingEntity dummy;
    private List<FigurePose.Part> parts = List.of();

    /** 作業値: 部位→[x,y,z] (度) */
    private final Map<String, float[]> angles = new LinkedHashMap<>();
    private float scaleVal;
    private float yawVal;
    /** プレビュー専用の向き (サーバーへ送らない。初期値は正面表示用の+180°) */
    private float previewYawVal;

    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listWidth;

    private static final int ROW_H = 24;

    public FigurePoseScreen(FigureEntity figure) {
        super(Component.translatable("screen.bamboomod.figure_title",
                figure.getFigureId().isEmpty() ? "?" : figure.getFigureId()));
        this.figure = figure;
        this.entityId = figure.getFigureId();
        this.realHeight = MonsterFigureItem.realHeight(entityId);
        this.minScale = MonsterFigureItem.minScale(entityId);
        reloadFromFigure();
    }

    private void reloadFromFigure() {
        this.scaleVal = figure.getFigureScale();
        this.yawVal = figure.getFigureYaw();
        this.previewYawVal = ((yawVal + 180.0F) % 360.0F + 360.0F) % 360.0F;
        this.angles.clear();
        this.angles.putAll(FigurePose.fromTag(figure.getFigurePose()));
    }

    private record Row(String label, AxisSlider slider) {
    }

    @Override
    protected void init() {
        rows.clear();
        dummy = FigureRenderer.getPlacedDummy(figure);
        if (dummy != null) {
            try {
                parts = FigurePose.groupRoots(
                        (net.minecraft.client.model.EntityModel<?>) ((net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?>) Minecraft
                                .getInstance().getEntityRenderDispatcher().getRenderer(dummy)).getModel());
            } catch (Exception e) {
                parts = List.of();
            }
        }
        int previewW = 130;
        listLeft = previewW + 20;
        listTop = 34;
        listBottom = this.height - 34;
        listWidth = this.width - listLeft - 14;

        int sliderW = Math.min(150, listWidth - 8);
        int x = listLeft + 4;
        // サイズ (高さブロック表示)
        float minH = MonsterFigureItem.MIN_HEIGHT;
        float maxH = realHeight;
        float curH = realHeight * scaleVal;
        addRow("size", Component.translatable("screen.bamboomod.figure_scale").getString(),
                minH, maxH, curH, v -> {
                    float h = v.floatValue();
                    scaleVal = h / realHeight;
                    return String.format("%.2f", h);
                });
        // 向き (ワールド反映)
        addRow("yaw", Component.translatable("screen.bamboomod.figure_yaw").getString(),
                0.0D, 360.0D, yawVal, v -> {
                    yawVal = v.floatValue();
                    return String.format("%.0f", v);
                });
        // プレビュー向き (GUI内表示専用。サーバーへ送らない)
        addRow("previewYaw",
                Component.translatable("screen.bamboomod.figure_preview_yaw").getString(),
                0.0D, 360.0D, previewYawVal, v -> {
                    previewYawVal = v.floatValue();
                    return String.format("%.0f", v);
                });
        // 可動部 (部位×XYZ)
        for (FigurePose.Part part : parts) {
            float[] a = angles.computeIfAbsent(part.key(), k -> new float[3]);
            String[] axes = { "X", "Y", "Z" };
            for (int i = 0; i < 3; i++) {
                final int axis = i;
                addRow(part.key() + ":" + axis, part.label() + " " + axes[i],
                        -180.0D, 180.0D, a[axis], v -> {
                            angles.computeIfAbsent(part.key(), k -> new float[3])[axis] = v.floatValue();
                            return String.format("%.0f", v);
                        });
            }
        }
        // ボタン
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.bamboomod.figure_done"), b -> this.onClose())
                .bounds(this.width - 110, this.height - 28, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.bamboomod.figure_reset"), b -> {
                    reloadFromFigure();
                    this.rebuildWidgets();
                }).bounds(this.width - 220, this.height - 28, 100, 20).build());
        layoutRows();
        super.init();
    }

    /** 行追加ヘルパー (xはlayoutRowsで確定するため仮配置)。 */
    private void addRow(String id, String label, double min, double max, double current,
            java.util.function.Function<Double, String> onChange) {
        AxisSlider slider = new AxisSlider(listLeft + 4, 0, Math.min(150, listWidth - 8), 20,
                label, min, max, current, onChange);
        rows.add(new Row(id, slider));
        this.addRenderableWidget(slider);
    }

    private void layoutRows() {
        int y = listTop - (int) scroll;
        for (Row row : rows) {
            row.slider().setY(y);
            row.slider().visible = y + 20 >= listTop && y <= listBottom;
            y += ROW_H;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int totalH = rows.size() * ROW_H;
        int viewH = listBottom - listTop;
        if (totalH > viewH) {
            scroll = Math.max(0.0D, Math.min(totalH - viewH, scroll - delta * 12.0D));
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        if (figure.isRemoved()) {
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        // プレビュー枠
        int px0 = 12, py0 = 30, px1 = 132, py1 = this.height - 12;
        graphics.fill(px0, py0, px1, py1, 0x88000000);
        graphics.renderOutline(px0, py0, px1 - px0, py1 - py0, 0xFF888888);
        renderPreview(graphics, px0, py0, px1, py1);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreview(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        if (dummy == null) return;
        float heightBlocks = Math.max(0.05F, realHeight * scaleVal);
        float fit = Math.min(x1 - x0 - 16, y1 - y0 - 16) / heightBlocks;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((x0 + x1) / 2.0F, y1 - 8.0F, 50.0F);
        pose.scale(-fit, fit, fit);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            FigureRenderer.renderDummy(pose, buffer, dummy, scaleVal, previewYawVal,
                    FigurePose.toTag(angles), LightTexture.FULL_BRIGHT);
        } catch (Exception e) {
            // プレビュー失敗時は枠のみ
        }
        pose.popPose();
        buffer.endBatch();
    }

    @Override
    public void removed() {
        // 閉じる時に一括送信 (Esc閉じも含む)
        try {
            BambooNetwork.CHANNEL.sendToServer(new FigurePosePacket(figure.getId(),
                    scaleVal, yawVal, FigurePose.toTag(angles)));
        } catch (Exception e) {
            // 送信失敗は無視
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 汎用スライダー (値→ラベル変換付き)。ドラッグ中はローカル反映のみ。 */
    private static class AxisSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final java.util.function.Function<Double, String> onChange;

        AxisSlider(int x, int y, int width, int height, String label,
                double min, double max, double current,
                java.util.function.Function<Double, String> onChange) {
            super(x, y, width, height, Component.literal(label), to01(min, max, current));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private static double to01(double min, double max, double v) {
            if (max <= min) return 0.0D;
            return Math.max(0.0D, Math.min(1.0D, (v - min) / (max - min)));
        }

        private double currentValue() {
            return min + this.value * (max - min);
        }

        @Override
        protected void updateMessage() {
            String v;
            try {
                v = onChange.apply(currentValue());
            } catch (Exception e) {
                v = "?";
            }
            this.setMessage(Component.literal(label + ": " + v));
        }

        @Override
        protected void applyValue() {
            updateMessage();
        }
    }
}
