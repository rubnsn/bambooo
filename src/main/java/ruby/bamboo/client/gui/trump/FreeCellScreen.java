package ruby.bamboo.client.gui.trump;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import ruby.bamboo.client.gui.ChatOverlay;
import ruby.bamboo.client.gui.trump.FreeCellGame.Card;

/**
 * フリーセルの画面。
 * 左クリックで持つ/置く、右クリックで組札へ。複数枚移動は空きに応じた上限付き。
 */
public class FreeCellScreen extends Screen {
    private static final int CARD_W = 44;
    private static final int CARD_H = TrumpCardRenderer.heightFor(CARD_W);
    private static final int COL_GAP = 8;
    private static final int PITCH = CARD_W + COL_GAP;
    private static final int TOP_Y = 16;
    private static final int TABLEAU_GAP_Y = 14;
    private static final int UP_GAP = 18;
    private static final int UP_GAP_MIN = 7;

    private static final int FELT = 0xFF0B3D2C;
    private static final int FELT_EDGE = 0xFF072A20;
    private static final int SLOT_FILL = 0xFF083125;
    private static final int SLOT_LINE = 0xFF8A6D3B;
    private static final int SLOT_HINT = 0xFF3E6B5C;
    private static final int TARGET_LINE = 0xFFFFE08A;
    private static final int TEXT_MAIN = 0xFFFFE9B0;
    private static final int TEXT_SUB = 0xFFB9C4A8;

    private final FreeCellGame game = new FreeCellGame();
    /** 共通チャットオーバーレイ (Tで開く。画面は切り替えない)。 */
    private final ChatOverlay chat = new ChatOverlay();

    /** 持ち運び中の札 (持ち上げ時に場から取り除き済み)。先頭が一番下。 */
    private List<Card> held;
    private HeldFrom heldFrom;
    private int heldFromIndex;
    private int heldStartIndex;
    /** 持ち札の一部が別所へ確定済み (残りを持ち続ける)。 */
    private boolean heldSplit;
    private int grabDX;
    private int grabDY;
    private boolean wasWon;

    private int guiLeft;
    private int tableauTop;
    private int maxColH;
    private Button newGameButton;

    private enum HeldFrom {
        FREECELL, FOUNDATION, TABLEAU
    }

    private enum Target {
        NONE, FREECELL, FOUNDATION, TABLEAU
    }

    private record Hit(Target target, int index) {
    }

    public FreeCellScreen() {
        super(Component.translatable("screen.bamboomod.freecell"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int totalW = FreeCellGame.TABLEAU_COUNT * CARD_W
                + (FreeCellGame.TABLEAU_COUNT - 1) * COL_GAP;
        guiLeft = (this.width - totalW) / 2;
        tableauTop = TOP_Y + CARD_H + TABLEAU_GAP_Y;
        maxColH = this.height - tableauTop - 30;
        this.clearWidgets();
        int right = guiLeft + totalW;
        newGameButton = Button.builder(
                Component.translatable("screen.bamboomod.freecell_new"), b -> newGame())
                .bounds(right - 82, this.height - 24, 78, 20)
                .build();
        this.addRenderableWidget(newGameButton);
        EditBox chatBox = chat.attach(this.font, this.width, this.height);
        this.addRenderableWidget(chatBox);
        if (chat.isOpen()) {
            this.setFocused(chatBox);
            chatBox.setFocused(true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        // チャット入力中は下段ボタンを隠す (入力欄と被るため)
        newGameButton.visible = !chat.isOpen();
    }

    private void newGame() {
        game.newGame(new Random());
        held = null;
        heldSplit = false;
        wasWon = false;
        click(0.9F);
    }

    // ===== 座標 =====

    private int columnX(int col) {
        return guiLeft + col * PITCH;
    }

    private int freecellX(int f) {
        return columnX(f);
    }

    private int foundationX(int f) {
        return columnX(FreeCellGame.FREECELL_COUNT + f);
    }

    private static boolean inRect(int x, int y, int rx, int ry, int w, int h) {
        return x >= rx && x < rx + w && y >= ry && y < ry + h;
    }

    private boolean inTopSlot(int x, int y, int sx) {
        return inRect(x, y, sx, TOP_Y, CARD_W, CARD_H);
    }

    private int columnAt(int x, int y) {
        if (y < tableauTop) {
            return -1;
        }
        int rel = x - guiLeft;
        if (rel < 0) {
            return -1;
        }
        int col = rel / PITCH;
        if (col >= FreeCellGame.TABLEAU_COUNT || rel - col * PITCH >= CARD_W) {
            return -1;
        }
        return col;
    }

    /** 列の各札の上端y。列が長いとき自動で詰める。 */
    private int[] columnTops(int col) {
        int n = game.tableauSize(col);
        int[] ys = new int[n];
        int gap = UP_GAP;
        if (n > 1) {
            int need = CARD_H + (n - 1) * UP_GAP;
            if (need > maxColH) {
                gap = Math.max(UP_GAP_MIN, (maxColH - CARD_H) / (n - 1));
            }
        }
        int y = tableauTop;
        for (int i = 0; i < n; i++) {
            ys[i] = y;
            y += gap;
        }
        return ys;
    }

    /** 列内で y に掛かる一番上の札。空列・列より下は size()、列より上は -1。 */
    private int indexAt(int col, int y, int[] ys) {
        if (y < tableauTop) {
            return -1;
        }
        for (int i = ys.length - 1; i >= 0; i--) {
            if (y >= ys[i]) {
                return i;
            }
        }
        return ys.length;
    }

    private Hit hitTest(int x, int y) {
        for (int f = 0; f < FreeCellGame.FREECELL_COUNT; f++) {
            if (inTopSlot(x, y, freecellX(f))) {
                return new Hit(Target.FREECELL, f);
            }
        }
        for (int f = 0; f < FreeCellGame.FOUNDATION_COUNT; f++) {
            if (inTopSlot(x, y, foundationX(f))) {
                return new Hit(Target.FOUNDATION, f);
            }
        }
        int col = columnAt(x, y);
        if (col >= 0) {
            return new Hit(Target.TABLEAU, col);
        }
        return new Hit(Target.NONE, 0);
    }

    // ===== 入力 =====

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (chat.keyPressed(this, keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (chat.charTyped(c, modifiers)) {
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (chat.isOpen()) {
            return true;
        }
        int x = (int) mouseX;
        int y = (int) mouseY;
        if (button == 1) {
            rightClick(x, y);
            return true;
        }
        if (button != 0) {
            return false;
        }
        leftClick(x, y);
        return true;
    }

    private long lastLeftClick;
    private int lastClickX;
    private int lastClickY;

    private void leftClick(int x, int y) {
        long now = System.currentTimeMillis();
        boolean doubleClick = now - lastLeftClick < 300
                && Math.abs(x - lastClickX) < 8 && Math.abs(y - lastClickY) < 8;
        lastLeftClick = now;
        lastClickX = x;
        lastClickY = y;
        if (held != null) {
            // ダブルクリックは先頭札の組札への自動移動。不可なら通常ドロップへ。
            if (doubleClick && sendHeldToFoundation()) {
                click(1.2F);
                checkWin();
                return;
            }
            tryDrop(hitTest(x, y));
            return;
        }
        Hit hit = hitTest(x, y);
        switch (hit.target()) {
            case FREECELL -> {
                Card top = game.freecellCard(hit.index());
                if (top != null) {
                    held = List.of(game.removeFreecellCard(hit.index()));
                    heldSplit = false;
                    heldFrom = HeldFrom.FREECELL;
                    heldFromIndex = hit.index();
                    grabFrom(x, y, freecellX(hit.index()), TOP_Y);
                    click(1.0F);
                }
            }
            case FOUNDATION -> {
                Card top = game.foundationTop(hit.index());
                if (top != null) {
                    held = List.of(game.removeFoundationTop(hit.index()));
                    heldSplit = false;
                    heldFrom = HeldFrom.FOUNDATION;
                    heldFromIndex = hit.index();
                    grabFrom(x, y, foundationX(hit.index()), TOP_Y);
                    click(1.0F);
                }
            }
            case TABLEAU -> {
                int col = hit.index();
                int[] ys = columnTops(col);
                int idx = indexAt(col, y, ys);
                if (idx >= 0 && idx < game.tableauSize(col) && game.isMovableSequence(col, idx)) {
                    heldStartIndex = idx;
                    held = game.removeTableauSequence(col, idx);
                    heldSplit = false;
                    heldFrom = HeldFrom.TABLEAU;
                    heldFromIndex = col;
                    grabFrom(x, y, columnX(col), ys[idx]);
                    click(1.0F);
                }
            }
            default -> {
            }
        }
        checkWin();
    }

    private void grabFrom(int x, int y, int cardX, int cardY) {
        grabDX = x - cardX;
        grabDY = y - cardY;
    }

    private void tryDrop(Hit hit) {
        boolean placed = false;
        boolean keepHolding = false;
        switch (hit.target()) {
            case FREECELL -> {
                // 1枚持ちで空きセルのみ
                if (held.size() == 1 && game.freecellCard(hit.index()) == null) {
                    game.placeFreecellCard(hit.index(), held.get(0));
                    held = null;
                    placed = true;
                }
            }
            case FOUNDATION -> {
                // 複数持ちでも先頭1枚だけ置いて残りは持ち続ける
                Card top = held.get(held.size() - 1);
                if (game.canPlaceOnFoundation(top, hit.index())) {
                    game.placeBackOnFoundation(hit.index(), top);
                    if (held.size() == 1) {
                        held = null;
                    } else {
                        held.remove(held.size() - 1);
                        heldSplit = true;
                        keepHolding = true;
                    }
                    placed = true;
                }
            }
            case TABLEAU -> {
                int col = hit.index();
                if (heldFrom == HeldFrom.TABLEAU && col == heldFromIndex) {
                    game.addTableauSequence(col, held);
                    held = null;
                    placed = true;
                } else if (held.size() <= game.maxMovable(col)
                        && game.canStackOnTableau(held.get(0), col)) {
                    game.addTableauSequence(col, held);
                    held = null;
                    placed = true;
                }
            }
            default -> {
            }
        }
        if (!placed) {
            cancelHeld();
            click(0.7F);
        } else {
            click(1.2F);
        }
        if (!keepHolding) {
            held = null;
        }
        checkWin();
    }

    /** 持ち札の先頭1枚を組札へ。複数持ちは残りを持ち続ける。 */
    private boolean sendHeldToFoundation() {
        if (held == null || held.isEmpty()) {
            return false;
        }
        Card top = held.get(held.size() - 1);
        int f = game.findFoundationFor(top);
        if (f < 0) {
            return false;
        }
        game.placeBackOnFoundation(f, top);
        if (held.size() == 1) {
            held = null;
        } else {
            held.remove(held.size() - 1);
            heldSplit = true;
        }
        return true;
    }

    private void cancelHeld() {
        if (held == null) {
            return;
        }
        switch (heldFrom) {
            case FREECELL -> game.placeFreecellCard(heldFromIndex, held.get(0));
            case FOUNDATION -> game.placeBackOnFoundation(heldFromIndex, held.get(0));
            case TABLEAU -> game.addTableauSequence(heldFromIndex, held);
        }
        held = null;
    }

    private void rightClick(int x, int y) {
        if (held != null) {
            if (sendHeldToFoundation()) {
                click(1.2F);
            } else {
                cancelHeld();
                click(0.7F);
            }
            checkWin();
            return;
        }
        Hit hit = hitTest(x, y);
        boolean moved = false;
        if (hit.target() == Target.FREECELL) {
            moved = game.moveFreecellToFoundation(hit.index());
        } else if (hit.target() == Target.TABLEAU) {
            int col = hit.index();
            int[] ys = columnTops(col);
            if (indexAt(col, y, ys) == game.tableauSize(col) - 1 && game.tableauSize(col) > 0) {
                moved = game.moveTableauToFoundation(col);
            }
        }
        if (moved) {
            click(1.2F);
        }
        checkWin();
    }

    private void checkWin() {
        if (game.isWon() && !wasWon) {
            wasWon = true;
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
            }
        }
    }

    private void click(float pitch) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    // ===== 描画 (背景=フェルト+枠のみの簡易版) =====

    @Override
    public void renderBackground(GuiGraphics gfx) {
        gfx.fill(0, 0, this.width, this.height, FELT);
        gfx.fill(0, 0, this.width, 3, FELT_EDGE);
        gfx.fill(0, this.height - 3, this.width, this.height, FELT_EDGE);
        gfx.fill(0, 0, 3, this.height, FELT_EDGE);
        gfx.fill(this.width - 3, 0, this.width, this.height, FELT_EDGE);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        gfx.drawString(this.font, this.title.getString(), 8, 6, TEXT_MAIN, false);

        Hit hover = held != null ? hitTest(mouseX, mouseY) : null;
        drawTopRow(gfx, hover);
        drawTableau(gfx, hover);
        drawHeld(gfx, mouseX, mouseY);

        gfx.drawString(this.font, Component.translatable("screen.bamboomod.freecell_hint").getString(),
                8, this.height - 18, TEXT_SUB, false);

        if (game.isWon()) {
            gfx.fill(0, 0, this.width, this.height, 0xA0000000);
            gfx.drawCenteredString(this.font,
                    Component.translatable("screen.bamboomod.freecell_win").getString(),
                    this.width / 2, this.height / 2 - 12, TEXT_MAIN);
            gfx.drawCenteredString(this.font,
                    Component.translatable("screen.bamboomod.freecell_win_sub").getString(),
                    this.width / 2, this.height / 2 + 6, TEXT_SUB);
        }
        chat.renderLog(gfx, this.font, this.width, this.height);
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawTopRow(GuiGraphics gfx, Hit hover) {
        // フリーセル (1枚・空きのみ置ける)
        for (int f = 0; f < FreeCellGame.FREECELL_COUNT; f++) {
            int fx = freecellX(f);
            boolean legal = held != null && held.size() == 1
                    && game.freecellCard(f) == null
                    && !(heldFrom == HeldFrom.FREECELL && heldFromIndex == f);
            drawSlot(gfx, fx, TOP_Y, hover != null && hover.target() == Target.FREECELL
                    && hover.index() == f && legal);
            Card top = game.freecellCard(f);
            if (top != null && !(held != null && heldFrom == HeldFrom.FREECELL
                    && heldFromIndex == f)) {
                renderFace(gfx, fx, TOP_Y, top);
            }
        }
        // 組札
        for (int f = 0; f < FreeCellGame.FOUNDATION_COUNT; f++) {
            int fx = foundationX(f);
            boolean legal = held != null && !held.isEmpty()
                    && game.canPlaceOnFoundation(held.get(held.size() - 1), f);
            drawSlot(gfx, fx, TOP_Y, hover != null && hover.target() == Target.FOUNDATION
                    && hover.index() == f && legal);
            Card top = game.foundationTop(f);
            if (top != null) {
                renderFace(gfx, fx, TOP_Y, top);
            } else {
                gfx.drawCenteredString(this.font, "A", fx + CARD_W / 2, TOP_Y + CARD_H / 2 - 4,
                        SLOT_HINT);
            }
        }
    }

    private void drawTableau(GuiGraphics gfx, Hit hover) {
        for (int col = 0; col < FreeCellGame.TABLEAU_COUNT; col++) {
            int cx = columnX(col);
            int[] ys = columnTops(col);
            if (ys.length == 0) {
                boolean legal = held != null && !(heldFrom == HeldFrom.TABLEAU && heldFromIndex == col)
                        && held.size() <= game.maxMovable(col)
                        && game.canStackOnTableau(held.get(0), col);
                drawSlot(gfx, cx, tableauTop, hover != null && hover.target() == Target.TABLEAU
                        && hover.index() == col && legal);
                continue;
            }
            int skipFrom = (held != null && heldFrom == HeldFrom.TABLEAU && heldFromIndex == col)
                    ? heldStartIndex : -1;
            for (int i = 0; i < ys.length; i++) {
                if (i >= skipFrom && skipFrom >= 0) {
                    continue;
                }
                renderFace(gfx, cx, ys[i], game.tableauCard(col, i));
            }
            // 合法な置き場の列先頭を強調
            if (held != null && hover != null && hover.target() == Target.TABLEAU
                    && hover.index() == col) {
                boolean same = heldFrom == HeldFrom.TABLEAU && heldFromIndex == col;
                if (same || (held.size() <= game.maxMovable(col)
                        && game.canStackOnTableau(held.get(0), col))) {
                    drawFrame(gfx, cx, ys[ys.length - 1], CARD_W, CARD_H, TARGET_LINE);
                }
            }
        }
    }

    private void renderFace(GuiGraphics gfx, int x, int y, Card card) {
        TrumpCardRenderer.renderCard(gfx, this.font, x, y, CARD_W, card.rank(), card.suit(), false);
    }

    private void drawHeld(GuiGraphics gfx, int mouseX, int mouseY) {
        if (held == null) {
            return;
        }
        int x = mouseX - grabDX;
        int y = mouseY - grabDY;
        for (Card c : held) {
            renderFace(gfx, x, y, c);
            y += UP_GAP;
        }
    }

    /** 空き置き場の枠。 */
    private static void drawSlot(GuiGraphics gfx, int x, int y, boolean highlight) {
        gfx.fill(x, y, x + CARD_W, y + CARD_H, SLOT_FILL);
        drawFrame(gfx, x, y, CARD_W, CARD_H, highlight ? TARGET_LINE : SLOT_LINE);
        if (highlight) {
            gfx.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, 0x22FFE08A);
        }
    }

    private static void drawFrame(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x, y, x + w, y + 1, color);
        gfx.fill(x, y + h - 1, x + w, y + h, color);
        gfx.fill(x, y, x + 1, y + h, color);
        gfx.fill(x + w - 1, y, x + w, y + h, color);
    }
}
