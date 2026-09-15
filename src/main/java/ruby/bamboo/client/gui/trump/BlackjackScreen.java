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
import ruby.bamboo.blackjack.BlackjackGame;
import ruby.bamboo.blackjack.BlackjackGame.Card;
import ruby.bamboo.blackjack.BlackjackGame.Outcome;
import ruby.bamboo.blackjack.BlackjackGame.Phase;
import ruby.bamboo.blackjack.BlackjackManager;
import ruby.bamboo.client.gui.ChatOverlay;
import net.neoforged.neoforge.network.PacketDistributor;
import ruby.bamboo.network.BlackjackAbandonPacket;
import ruby.bamboo.network.BlackjackBetPacket;
import ruby.bamboo.network.BlackjackCashoutPacket;
import ruby.bamboo.network.BlackjackInsurancePacket;
import ruby.bamboo.network.BlackjackSettlePacket;

/**
 * ブラックジャックの画面。
 * フリーモード (点数なし) とベットモード (1エメラルド=1000点) を選べる。
 * カードは簡易3D風 (潰れ+影+滑り込み) のめくり演出で出す。
 */
public class BlackjackScreen extends Screen {
    /** カード最大幅。小さい画面では高さに合わせて縮める。 */
    private static final int CARD_W_MAX = 64;
    private static final int DEALER_Y = 56;
    /** 下部UI (ボタン・ヒント) の予約高さ。 */
    private static final int BOTTOM_UI = 72;
    /** 上下の列の間の最低すき間 (結果・合計表示用)。 */
    private static final int MID_GAP = 36;
    private static final int[] BET_CHOICES = {1, 2, 4, 8};

    private static final int FELT = 0xFF0B3D2C;
    private static final int FELT_EDGE = 0xFF072A20;
    private static final int TEXT_MAIN = 0xFFFFE9B0;
    private static final int TEXT_SUB = 0xFFB9C4A8;
    private static final int TEXT_WARN = 0xFFFF6B5E;

    private final BlackjackGame game = new BlackjackGame();
    private final Random rng = new Random();
    /** 共通チャットオーバーレイ (Tで開く。画面は切り替えない)。 */
    private final ChatOverlay chat = new ChatOverlay();

    private enum UiState {
        SELECT, BET, WAIT, STAKE, PLAY, CASHED
    }

    private UiState state = UiState.SELECT;
    /** BET選択から戻る先 (SELECT=初回 / PLAY=追加ベット)。 */
    private UiState betBack = UiState.SELECT;
    private boolean betMode;
    /** 今の掛け金 (100〜1000・100刻み。ラウンド非進行中のみ変更可)。 */
    private int stake = BlackjackGame.MIN_BET;
    /** サーバー残高ありと見なす (BET送信で楽観的にtrue、同期で確定)。 */
    private boolean sessionActive;
    private int balance;
    private boolean settled = true;
    /** 配札通番 (決着パケットの重複捨て用。配るごとに+1)。 */
    private long roundSeq;
    /** 決着送信済み・残高応答待ち。 */
    private boolean settlePending;
    /** 本決着の送信時点残高 (配当差分の基準。保険分を含まない)。 */
    private int baseForDelta;
    /** 保険決着の応答待ち (BJ確定時は応答後に本決着へ進む)。 */
    private boolean awaitingInsurance;
    /** 保険の提示ずみ (入る/入らないのどちらか)。 */
    private boolean insuranceDone;
    /** サーバー確定の今回配当差分 (応答前はnull)。 */
    private Integer lastDelta;
    private boolean quitSent;
    private boolean cashedOut;
    private Outcome lastOutcome;
    private int paidCount;
    private int paidRemainder;
    private String notice = "";
    private int noticeTicks;

    private final List<VisualCard> dealerVisuals = new ArrayList<>();
    private final List<VisualCard> playerVisuals = new ArrayList<>();
    private int deckX;
    /** 画面高さ連動のカード寸法 (initで決定)。 */
    private int cardW = CARD_W_MAX;
    private int cardH = TrumpCardRenderer.heightFor(CARD_W_MAX);
    private int fanPitch = 24;
    private int playerTop;

    private Button freeButton;
    private Button betModeButton;
    private final List<Button> betButtons = new ArrayList<>();
    private Button betBackButton;
    private Button hitButton;
    private Button standButton;
    private Button doubleButton;
    private Button insureButton;
    private Button noInsureButton;
    private Button stakeMinusButton;
    private Button stakePlusButton;
    private Button dealButton;
    private Button nextButton;
    private Button quitButton;
    private Button closeButton;

    /** 演出付きの場札。flip 0=裏→1=表、slide 0=山札位置→1=定位置。 */
    private static final class VisualCard {
        final Card card;
        boolean hole;
        float flip;
        float slide;
        int delay;

        VisualCard(Card card, boolean hole, int delay) {
            this.card = card;
            this.hole = hole;
            this.delay = delay;
        }

        void tick() {
            if (delay > 0) {
                delay--;
                return;
            }
            if (!hole && flip < 1.0F) {
                flip = Math.min(1.0F, flip + 1.0F / 8);
            }
            if (slide < 1.0F) {
                slide = Math.min(1.0F, slide + 1.0F / 6);
            }
        }

        boolean faceDown() {
            return flip < 0.5F;
        }
    }

    public BlackjackScreen() {
        super(Component.translatable("screen.bamboomod.blackjack"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        // 上下2列+すき間が収まるようカードを縮める (下限24)
        cardW = Math.max(24, Math.min(CARD_W_MAX,
                (this.height - DEALER_Y - BOTTOM_UI - MID_GAP) / 2 * 5 / 7));
        cardH = TrumpCardRenderer.heightFor(cardW);
        fanPitch = Math.max(12, cardW * 6 / 11);
        playerTop = this.height - BOTTOM_UI - cardH;
        deckX = this.width - cardW - 16;
        this.clearWidgets();
        freeButton = Button.builder(Component.translatable("screen.bamboomod.blackjack_free"),
                b -> startFree()).bounds(cx - 170, 120, 160, 20).build();
        betModeButton = Button.builder(Component.translatable("screen.bamboomod.blackjack_bet"),
                b -> {
                    betMode = true;
                    betBack = UiState.SELECT;
                    state = UiState.BET;
                    click(1.0F);
                }).bounds(cx + 10, 120, 160, 20).build();
        this.addRenderableWidget(freeButton);
        this.addRenderableWidget(betModeButton);
        betButtons.clear();
        int betMidY = this.height / 2;
        for (int i = 0; i < BET_CHOICES.length; i++) {
            final int em = BET_CHOICES[i];
            Button b = Button.builder(Component.literal(em + " EM"),
                    btn -> sendBet(em)).bounds(cx - 170 + (i % 2) * 180,
                            betMidY - 40 + (i / 2) * 24, 160, 20).build();
            betButtons.add(b);
            this.addRenderableWidget(b);
        }
        betBackButton = Button.builder(Component.translatable("screen.bamboomod.blackjack_back"),
                b -> {
                    state = betBack;
                    click(0.9F);
                }).bounds(cx - 80, betMidY + 36, 160, 20).build();
        this.addRenderableWidget(betBackButton);
        hitButton = Button.builder(Component.translatable("screen.bamboomod.blackjack_hit"),
                b -> doHit()).bounds(this.width - 268, this.height - 52, 80, 20).build();
        standButton = Button.builder(Component.translatable("screen.bamboomod.blackjack_stand"),
                b -> doStand()).bounds(this.width - 182, this.height - 52, 80, 20).build();
        doubleButton = Button.builder(
                Component.translatable("screen.bamboomod.blackjack_double"),
                b -> doDouble()).bounds(this.width - 96, this.height - 52, 80, 20).build();
        this.addRenderableWidget(hitButton);
        this.addRenderableWidget(standButton);
        this.addRenderableWidget(doubleButton);
        insureButton = Button.builder(
                Component.translatable("screen.bamboomod.blackjack_insure"),
                b -> acceptInsurance()).bounds(this.width - 268, this.height - 52, 130, 20)
                .build();
        noInsureButton = Button.builder(
                Component.translatable("screen.bamboomod.blackjack_noinsure"),
                b -> declineInsurance()).bounds(this.width - 132, this.height - 52, 116, 20)
                .build();
        this.addRenderableWidget(insureButton);
        this.addRenderableWidget(noInsureButton);
        nextButton = Button.builder(Component.translatable("screen.bamboomod.blackjack_next"),
                b -> nextRound()).bounds(this.width - 182, this.height - 52, 166, 20).build();
        this.addRenderableWidget(nextButton);
        quitButton = Button.builder(Component.translatable("screen.bamboomod.blackjack_quit"),
                b -> quit()).bounds(this.width - 182, this.height - 28, 166, 20).build();
        this.addRenderableWidget(quitButton);
        int stakeX = 8 + this.font.width("掛け金 ");
        stakeMinusButton = Button.builder(Component.literal("-"), b -> {
            stake = Math.max(BlackjackGame.MIN_BET, stake - BlackjackGame.BET_STEP);
            click(0.9F);
        }).bounds(cx - 110, this.height / 2 - 10, 60, 20).build();
        stakePlusButton = Button.builder(Component.literal("+"), b -> {
            stake = Math.min(BlackjackGame.MAX_BET, stake + BlackjackGame.BET_STEP);
            click(0.9F);
        }).bounds(cx + 50, this.height / 2 - 10, 60, 20).build();
        this.addRenderableWidget(stakeMinusButton);
        this.addRenderableWidget(stakePlusButton);
        dealButton = Button.builder(Component.translatable("screen.bamboomod.blackjack_deal"),
                b -> {
                    state = UiState.PLAY;
                    deal();
                }).bounds(cx - 80, this.height / 2 + 30, 160, 20).build();
        this.addRenderableWidget(dealButton);
        closeButton = Button.builder(Component.translatable("screen.bamboomod.blackjack_close"),
                b -> close()).bounds(cx - 80, 200, 160, 20).build();
        this.addRenderableWidget(closeButton);
        EditBox chatBox = chat.attach(this.font, this.width, this.height);
        this.addRenderableWidget(chatBox);
        if (chat.isOpen()) {
            this.setFocused(chatBox);
            chatBox.setFocused(true);
        }
        refreshButtons();
    }

    private void refreshButtons() {
        boolean selecting = state == UiState.SELECT;
        freeButton.visible = selecting && !chat.isOpen();
        betModeButton.visible = selecting && !chat.isOpen();
        for (Button b : betButtons) {
            b.visible = state == UiState.BET && !chat.isOpen();
        }
        betBackButton.visible = state == UiState.BET && !chat.isOpen();
        boolean playing = state == UiState.PLAY && game.phase() == Phase.PLAYER
                && !chat.isOpen() && !insurancePending();
        hitButton.visible = playing;
        standButton.visible = playing;
        doubleButton.visible = playing && game.canDouble()
                && (!betMode || balance >= stake * 2);
        insureButton.visible = state == UiState.PLAY && insurancePending()
                && !chat.isOpen();
        insureButton.active = balance >= stake / 2;
        insureButton.setMessage(Component.translatable("screen.bamboomod.blackjack_insure",
                String.valueOf(stake / 2)));
        noInsureButton.visible = insureButton.visible;
        // 決着応答待ちの間は次へ出さない (古い応答と新しい配札の混線防止)
        nextButton.visible = state == UiState.PLAY && game.phase() == Phase.DONE
                && !settlePending && !awaitingInsurance && !chat.isOpen();
        boolean stakeAdjust = state == UiState.STAKE && !chat.isOpen();
        stakeMinusButton.visible = stakeAdjust;
        stakeMinusButton.active = stake > BlackjackGame.MIN_BET;
        stakePlusButton.visible = stakeAdjust;
        stakePlusButton.active = stake < BlackjackGame.MAX_BET;
        dealButton.visible = stakeAdjust;
        quitButton.visible = (state == UiState.PLAY || state == UiState.BET
                || state == UiState.STAKE) && !chat.isOpen();
        quitButton.active = !quitSent;
        if (betMode && state == UiState.PLAY) {
            quitButton.setMessage(Component.translatable("screen.bamboomod.blackjack_quit_to",
                    String.valueOf(balance / BlackjackManager.POINTS_PER_EMERALD)));
        } else {
            quitButton.setMessage(Component.translatable("screen.bamboomod.blackjack_quit"));
        }
        closeButton.visible = state == UiState.CASHED && !chat.isOpen();
    }

    /**
     * 保険の提示中。ベット限定・A表示・ナチュラルなし・未回答の間のみ。
     * 10表示のpeekは提示なしで即確定 (カジノ式)。
     */
    private boolean insurancePending() {
        return betMode && game.phase() == Phase.PLAYER && !insuranceDone
                && game.dealerShowsAce()
                && !BlackjackGame.isBlackjack(game.playerHand());
    }

    /** 保険に入る (掛け金の半分。BJ確定なら2:1、違えば没収)。 */
    private void acceptInsurance() {
        if (!insurancePending() || balance < stake / 2) {
            return;
        }
        insuranceDone = true;
        boolean bj = game.dealerHasBlackjack();
        PacketDistributor.sendToServer(
                new BlackjackInsurancePacket(stake / 2, bj, roundSeq));
        notice = Component.translatable("screen.bamboomod.blackjack_insured",
                String.valueOf(stake / 2)).getString();
        noticeTicks = 200;
        click(1.0F);
        if (bj) {
            // BJ確定。保険応答を待ってから本決着へ進む
            awaitingInsurance = true;
        }
        // BJなしはpeek済み・そのまま続行
    }

    /** 保険に入らない。peekしてBJなら即決着、違えば続行。 */
    private void declineInsurance() {
        insuranceDone = true;
        click(0.9F);
        if (game.dealerHasBlackjack()) {
            revealHole();
            game.revealDealerBlackjack();
            resolve();
        }
    }

    @Override
    public void tick() {
        super.tick();
        for (VisualCard v : dealerVisuals) {
            v.tick();
        }
        for (VisualCard v : playerVisuals) {
            v.tick();
        }
        if (noticeTicks > 0) {
            noticeTicks--;
        }
        refreshButtons();
    }

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
    public void removed() {
        super.removed();
        // 終了せずに閉じたら没収 (換金済み・フリー・未入金は対象外)
        if (betMode && sessionActive && !cashedOut && this.minecraft != null
                && this.minecraft.player != null && this.minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new BlackjackAbandonPacket());
        }
    }

    /** S→C 残高同期。 */
    public void onBalance(int balance, boolean active, int paid, int remainder,
            String error) {
        this.balance = balance;
        this.sessionActive = active;
        if (error != null && !error.isEmpty()) {
            notice = Component.translatable(error).getString();
            noticeTicks = 200;
            quitSent = false;
        }
        if (active && (state == UiState.WAIT || state == UiState.BET)) {
            enterStake();
        }
        if (state == UiState.PLAY && settlePending && paid < 0) {
            // 本決着の応答。送信時点からの純増分を表示する
            settlePending = false;
            lastDelta = balance - baseForDelta;
        }
        if (awaitingInsurance && state == UiState.PLAY) {
            // 保険応答が来た (成否不問)。伏せ札を開けて本決着へ
            awaitingInsurance = false;
            revealHole();
            game.revealDealerBlackjack();
            resolve();
        }
        if (paid >= 0) {
            paidCount = paid;
            paidRemainder = remainder;
            cashedOut = true;
            sessionActive = false;
            awaitingInsurance = false;
            state = UiState.CASHED;
            click(1.2F);
        }
    }

    // ===== 進行 =====

    private void startFree() {
        betMode = false;
        state = UiState.PLAY;
        deal();
        click(1.0F);
    }

    private void sendBet(int emeralds) {
        PacketDistributor.sendToServer(new BlackjackBetPacket(emeralds));
        sessionActive = true;
        state = UiState.WAIT;
        click(1.0F);
    }

    private void deal() {
        game.newRound(rng, stake);
        settled = false;
        settlePending = false;
        awaitingInsurance = false;
        insuranceDone = false;
        lastDelta = null;
        roundSeq++;
        lastOutcome = null;
        dealerVisuals.clear();
        playerVisuals.clear();
        List<Card> dealer = game.dealerHand();
        for (int i = 0; i < dealer.size(); i++) {
            dealerVisuals.add(new VisualCard(dealer.get(i), i == 1, i * 4));
        }
        List<Card> player = game.playerHand();
        for (int i = 0; i < player.size(); i++) {
            playerVisuals.add(new VisualCard(player.get(i), false, 4 + i * 4));
        }
        click(0.9F);
        if (game.phase() == Phase.DONE) {
            // プレイヤーのナチュラル。peekして即決着 (保険対象外)
            revealHole();
            resolve();
        } else if (game.dealerHasBlackjack()
                && !(betMode && game.dealerShowsAce())) {
            // ディーラーBJの開示。A表示・ベット時は保険オファーを待つ、
            // それ以外 (10表示・フリー) は即確定。
            revealHole();
            game.revealDealerBlackjack();
            resolve();
        }
    }

    private void nextRound() {
        enterStake();
    }

    /**
     * 掛け金決めへ。カードを見る前に確定させるため、配札は配るボタンでのみ行う。
     * 残高が足りなければ追加ベット、手持ち以下に切り詰めてSTAKEへ。
     */
    private void enterStake() {
        // 応答待ちの取りこぼしを持ち越さない
        settlePending = false;
        awaitingInsurance = false;
        lastDelta = null;
        if (betMode && balance < BlackjackGame.MIN_BET) {
            notice = Component.translatable("screen.bamboomod.blackjack_need_points")
                    .getString();
            noticeTicks = 200;
            betBack = UiState.STAKE;
            state = UiState.BET;
            return;
        }
        if (betMode) {
            int affordable = balance / BlackjackGame.BET_STEP * BlackjackGame.BET_STEP;
            stake = Math.min(stake, Math.max(BlackjackGame.MIN_BET,
                    Math.min(BlackjackGame.MAX_BET, affordable)));
        }
        state = UiState.STAKE;
        click(0.9F);
    }

    private void doHit() {
        if (game.phase() != Phase.PLAYER) {
            return;
        }
        game.playerHit();
        List<Card> hand = game.playerHand();
        playerVisuals.add(new VisualCard(hand.get(hand.size() - 1), false, 0));
        click(1.0F);
        if (game.phase() == Phase.DONE) {
            resolve();
        }
    }

    private void doStand() {
        if (game.phase() != Phase.PLAYER) {
            return;
        }
        revealHole();
        game.playerStand();
        syncDealerVisuals();
        click(1.0F);
        resolve();
    }

    private void doDouble() {
        if (!game.canDouble()) {
            return;
        }
        if (betMode && balance < stake * 2) {
            return;
        }
        revealHole();
        game.playerDouble();
        List<Card> hand = game.playerHand();
        playerVisuals.add(new VisualCard(hand.get(hand.size() - 1), false, 0));
        syncDealerVisuals();
        click(1.2F);
        if (game.phase() == Phase.DONE) {
            resolve();
        }
    }

    private void revealHole() {
        for (VisualCard v : dealerVisuals) {
            v.hole = false;
        }
    }

    /** ディーラーの追加引きを演出付きで追う。 */
    private void syncDealerVisuals() {
        List<Card> hand = game.dealerHand();
        for (int i = dealerVisuals.size(); i < hand.size(); i++) {
            VisualCard v = new VisualCard(hand.get(i), false, (i - 2) * 6);
            dealerVisuals.add(v);
        }
    }

    private void resolve() {
        if (settled || game.phase() != Phase.DONE) {
            return;
        }
        settled = true;
        lastOutcome = game.outcome();
        if (betMode) {
            settlePending = true;
            baseForDelta = balance;
            PacketDistributor.sendToServer(new BlackjackSettlePacket(game.bet(),
                    lastOutcome.ordinal(), roundSeq));
        }
        if (this.minecraft != null) {
            float pitch = switch (lastOutcome) {
                case PLAYER_BLACKJACK, PLAYER_WIN, DEALER_BUST -> 1.4F;
                case PUSH -> 1.0F;
                default -> 0.6F;
            };
            if (lastOutcome == Outcome.PUSH) {
                this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
            } else {
                this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, pitch));
            }
        }
    }

    private void quit() {
        if (quitSent) {
            return;
        }
        awaitingInsurance = false;
        if (!betMode || !sessionActive) {
            close();
            return;
        }
        quitSent = true;
        PacketDistributor.sendToServer(new BlackjackCashoutPacket());
        click(1.0F);
    }

    private void close() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private void click(float pitch) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    // ===== 描画 =====

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, this.width, this.height, FELT);
        gfx.fill(0, 0, this.width, 3, FELT_EDGE);
        gfx.fill(0, this.height - 3, this.width, this.height, FELT_EDGE);
        gfx.fill(0, 0, 3, this.height, FELT_EDGE);
        gfx.fill(this.width - 3, 0, this.width, this.height, FELT_EDGE);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx, mouseX, mouseY, partialTick);
        gfx.drawString(this.font, this.title.getString(), 8, 6, TEXT_MAIN, false);
        if (state == UiState.PLAY) {
            drawTable(gfx);
        }
        if (state == UiState.SELECT) {
            drawSelect(gfx);
        } else if (state == UiState.STAKE) {
            drawStake(gfx);
        } else if (state == UiState.BET) {
            drawBet(gfx);
        } else if (state == UiState.WAIT) {
            dim(gfx);
            gfx.drawCenteredString(this.font,
                    Component.translatable("screen.bamboomod.blackjack_wait").getString(),
                    this.width / 2, 110, TEXT_SUB);
        } else if (state == UiState.CASHED) {
            dim(gfx);
            gfx.drawCenteredString(this.font,
                    Component.translatable("screen.bamboomod.blackjack_cashed",
                            String.valueOf(paidCount), String.valueOf(paidRemainder))
                            .getString(),
                    this.width / 2, 170, TEXT_MAIN);
        }
        chat.renderLog(gfx, this.font, this.width, this.height);
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawTable(GuiGraphics gfx) {
        if (betMode) {
            gfx.drawString(this.font,
                    Component.translatable("screen.bamboomod.blackjack_balance",
                            String.valueOf(balance),
                            String.valueOf(balance / BlackjackManager.POINTS_PER_EMERALD))
                            .getString(),
                    8, 18, TEXT_MAIN, false);
            // 掛け金表示 (変更はSTAKE画面の配るボタン前のみ)
            gfx.drawString(this.font,
                    Component.translatable("screen.bamboomod.blackjack_bet_info",
                            String.valueOf(settled ? stake : game.bet())).getString(),
                    8, 30, TEXT_SUB, false);
        }
        if (!notice.isEmpty() && noticeTicks > 0) {
            gfx.drawString(this.font, notice, 8, 52, TEXT_WARN, false);
        }
        // 山札 (演出の起点)
        for (int i = 2; i >= 0; i--) {
            TrumpCardRenderer.renderCard(gfx, this.font, deckX + i * 2, 16 + i * 2, cardW,
                    TrumpRank.ACE, TrumpSuit.SPADE, true);
        }
        // ディーラー
        boolean revealed = dealerVisuals.stream().noneMatch(v -> v.hole);
        String dealerText = revealed
                ? Component.translatable("screen.bamboomod.blackjack_dealer",
                        String.valueOf(BlackjackGame.handValue(game.dealerHand()))).getString()
                : Component.translatable("screen.bamboomod.blackjack_dealer_hidden")
                        .getString();
        gfx.drawCenteredString(this.font, dealerText, this.width / 2, DEALER_Y - 12,
                TEXT_SUB);
        drawFan(gfx, dealerVisuals, DEALER_Y);
        // プレイヤー
        int py = playerTop;
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.blackjack_you",
                        String.valueOf(BlackjackGame.handValue(game.playerHand()))).getString(),
                this.width / 2, py - 12, TEXT_SUB);
        drawFan(gfx, playerVisuals, py);
        // 結果 (ベットはサーバー確定の配当差分付き。応答前は反映待ち)
        if (game.phase() == Phase.DONE && lastOutcome != null) {
            String result = Component.translatable(outcomeKey(lastOutcome)).getString();
            if (betMode) {
                if (settlePending) {
                    result += " "
                            + Component.translatable("screen.bamboomod.blackjack_sync")
                                    .getString();
                } else if (lastDelta != null) {
                    result += " " + (lastDelta >= 0 ? "+" : "") + lastDelta;
                }
            }
            gfx.drawCenteredString(this.font, result,
                    this.width / 2, py - 28, TEXT_MAIN);
        }
        gfx.drawString(this.font, Component.translatable("screen.bamboomod.blackjack_hint")
                .getString(), 8, this.height - 18, TEXT_SUB, false);
        if (betMode) {
            // 没収警告は左上 (下中央はボタンと被るため短縮版)
            gfx.drawString(this.font,
                    Component.translatable("screen.bamboomod.blackjack_warn_short")
                            .getString(),
                    8, 42, TEXT_WARN, false);
        }
    }

    private void drawFan(GuiGraphics gfx, List<VisualCard> cards, int y) {
        int total = cardW + (cards.size() - 1) * fanPitch;
        int x0 = this.width / 2 - total / 2;
        for (int i = 0; i < cards.size(); i++) {
            renderVisual(gfx, x0 + i * fanPitch, y, cards.get(i));
        }
    }

    /**
     * 簡易3D風の1枚描画。横に潰して裏表を切り替え、影と山札からの滑り込みを付ける。
     */
    private void renderVisual(GuiGraphics gfx, int x, int y, VisualCard v) {
        int px = x + (int) ((1.0F - v.slide) * (deckX - x));
        float sx = Math.abs((float) Math.cos(v.flip * Math.PI));
        if (sx < 0.98F) {
            gfx.fill(px + 3, y + 4, px + cardW + 3, y + cardH + 4, 0x80000000);
        }
        gfx.pose().pushPose();
        gfx.pose().translate(px + cardW / 2.0F, 0.0F, 0.0F);
        gfx.pose().scale(Math.max(0.02F, sx), 1.0F, 1.0F);
        if (v.faceDown()) {
            TrumpCardRenderer.renderCard(gfx, this.font, -cardW / 2, y, cardW,
                    TrumpRank.ACE, TrumpSuit.SPADE, true);
        } else {
            TrumpCardRenderer.renderCard(gfx, this.font, -cardW / 2, y, cardW,
                    v.card.rank(), v.card.suit(), false);
        }
        gfx.pose().popPose();
    }

    private void drawSelect(GuiGraphics gfx) {
        dim(gfx);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.blackjack_select").getString(),
                this.width / 2, 90, TEXT_MAIN);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.blackjack_select_note").getString(),
                this.width / 2, 155, TEXT_WARN);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.blackjack_select_pay").getString(),
                this.width / 2, 168, TEXT_SUB);
    }

    private void drawStake(GuiGraphics gfx) {
        dim(gfx);
        int cx = this.width / 2;
        int midY = this.height / 2 - 10;
        gfx.drawString(this.font,
                Component.translatable("screen.bamboomod.blackjack_balance",
                        String.valueOf(balance),
                        String.valueOf(balance / BlackjackManager.POINTS_PER_EMERALD))
                        .getString(),
                8, 18, TEXT_MAIN, false);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.blackjack_stake_title").getString(),
                cx, midY - 40, TEXT_MAIN);
        String stakeText = stake + "点";
        gfx.drawCenteredString(this.font, stakeText, cx, midY + 6, TEXT_MAIN);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.blackjack_warn").getString(),
                cx, midY + 70, TEXT_WARN);
    }

    private void drawBet(GuiGraphics gfx) {
        dim(gfx);
        int betMidY = this.height / 2;
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.blackjack_bet_title").getString(),
                this.width / 2, betMidY - 70, TEXT_MAIN);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.bamboomod.blackjack_bet_note").getString(),
                this.width / 2, betMidY + 16, TEXT_WARN);
    }

    private void dim(GuiGraphics gfx) {
        gfx.fill(0, 0, this.width, this.height, 0xC0000000);
    }

    private static String outcomeKey(Outcome outcome) {
        return switch (outcome) {
            case PLAYER_BLACKJACK -> "screen.bamboomod.blackjack_r_bj";
            case PLAYER_WIN -> "screen.bamboomod.blackjack_r_win";
            case DEALER_BUST -> "screen.bamboomod.blackjack_r_dbust";
            case PUSH -> "screen.bamboomod.blackjack_r_push";
            case DEALER_WIN -> "screen.bamboomod.blackjack_r_lose";
            case PLAYER_BUST -> "screen.bamboomod.blackjack_r_pbust";
            case DEALER_BLACKJACK -> "screen.bamboomod.blackjack_r_dbj";
        };
    }
}
