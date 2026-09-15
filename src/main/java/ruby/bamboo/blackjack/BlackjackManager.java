package ruby.bamboo.blackjack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import ruby.bamboo.blackjack.BlackjackGame.Outcome;
import ruby.bamboo.network.BlackjackBalancePacket;

/**
 * ベットモードのサーバー側点数管理。
 * 1エメラルド=1000点。所持エメラルドを預かって点化し、終了時に1000点=1エメラルド
 * (端数切捨て) で返す。終了せずに閉じる・切断は没収 (残高破棄)。
 * ラウンド結果はクライアント申告のため、掛け金と配札通番の重複のみ検証する。
 * 決着・終了には必ず残高応答を返す (クライアントを黙って待たせない)。
 */
public final class BlackjackManager {
    /** 1エメラルドあたりの点数。 */
    public static final int POINTS_PER_EMERALD = 1000;

    private static final Map<UUID, Integer> BALANCE = new HashMap<>();
    /** 反映済みの配札通番 (重複・遅延決着の捨て判定用)。 */
    private static final Map<UUID, Long> LAST_SEQ = new HashMap<>();
    /** 保険受付の通番→金額 (本決着との整合用)。 */
    private static final Map<UUID, Long> INS_SEQ = new HashMap<>();
    private static final Map<UUID, Integer> INS_BET = new HashMap<>();

    private BlackjackManager() {
    }

    public static boolean active(UUID id) {
        return BALANCE.containsKey(id);
    }

    public static int balance(UUID id) {
        return BALANCE.getOrDefault(id, 0);
    }

    /** エメラルドを預かって点化する。 */
    public static void bet(MinecraftServer server, ServerPlayer player, int emeralds) {
        UUID id = player.getUUID();
        int want = Math.max(1, Math.min(64, emeralds));
        int have = countEmerald(player);
        boolean wasActive = active(id);
        if (have < want) {
            send(player, balance(id), wasActive, -1, 0,
                    "screen.bamboomod.blackjack_err_emerald");
            return;
        }
        removeEmerald(player, want);
        BALANCE.put(id, balance(id) + want * POINTS_PER_EMERALD);
        send(player, balance(id), true, -1, 0, "");
    }

    /**
     * ラウンド決着の反映 (申告制)。役ごとの配当は下表の通り。
     * 勝ち/ディーラーbust=+掛け金、ブラックジャック=+1.5倍、引分=±0、
     * 負け/bust/ディーラーBJ=-掛け金 (ダブル時は掛け金自体が2倍)。
     */
    public static void settle(MinecraftServer server, ServerPlayer player, int bet,
            int outcomeOrd, long seq) {
        UUID id = player.getUUID();
        if (!active(id)) {
            send(player, 0, false, -1, 0, "screen.bamboomod.blackjack_err_session");
            return;
        }
        if (outcomeOrd < 0 || outcomeOrd >= Outcome.values().length || bet < 100
                || bet > BlackjackGame.MAX_BET * 2 || bet % 100 != 0) {
            send(player, balance(id), true, -1, 0,
                    "screen.bamboomod.blackjack_err_session");
            return;
        }
        if (INS_SEQ.getOrDefault(id, 0L) == seq
                && bet != INS_BET.getOrDefault(id, 0) * 2) {
            // 保険ありの本決着は保険の2倍でなければ弾く (虚偽申告対策)
            send(player, balance(id), true, -1, 0,
                    "screen.bamboomod.blackjack_err_session");
            return;
        }
        if (seq <= LAST_SEQ.getOrDefault(id, 0L)) {
            // 重複・遅延決着は捨てるが、現在残高は返して黙殺しない
            send(player, balance(id), true, -1, 0, "");
            return;
        }
        LAST_SEQ.put(id, seq);
        int bal = balance(id);
        if (bet > bal) {
            send(player, bal, true, -1, 0, "screen.bamboomod.blackjack_err_points");
            return;
        }
        Outcome outcome = Outcome.values()[outcomeOrd];
        bal = switch (outcome) {
            case PLAYER_WIN, DEALER_BUST -> bal + bet;
            case PLAYER_BLACKJACK -> bal + bet + bet / 2;
            case PUSH -> bal;
            default -> bal - bet;
        };
        BALANCE.put(id, bal);
        send(player, bal, true, -1, 0, "");
    }

    /** インシュランス (掛け金の半分・1ラウンド1回)。BJ確定なら2:1、違えば没収。 */
    public static void insurance(MinecraftServer server, ServerPlayer player, int bet,
            boolean dealerBj, long seq) {
        UUID id = player.getUUID();
        if (!active(id)) {
            send(player, 0, false, -1, 0, "screen.bamboomod.blackjack_err_session");
            return;
        }
        if (bet < BlackjackGame.MIN_INS || bet > BlackjackGame.MAX_INS || bet % 50 != 0
                || seq <= INS_SEQ.getOrDefault(id, 0L)) {
            send(player, balance(id), true, -1, 0, "");
            return;
        }
        INS_SEQ.put(id, seq);
        INS_BET.put(id, bet);
        int bal = balance(id);
        if (bet > bal) {
            send(player, bal, true, -1, 0, "screen.bamboomod.blackjack_err_points");
            return;
        }
        bal = dealerBj ? bal + bet * 2 : bal - bet;
        BALANCE.put(id, bal);
        send(player, bal, true, -1, 0, "");
    }

    /** 終了。1000点=1エメラルド (端数切捨て) で返し、残高を閉じる。 */
    public static void cashout(MinecraftServer server, ServerPlayer player) {
        UUID id = player.getUUID();
        if (!active(id)) {
            send(player, 0, false, -1, 0, "screen.bamboomod.blackjack_err_session");
            return;
        }
        int bal = balance(id);
        int paid = bal / POINTS_PER_EMERALD;
        int remainder = bal % POINTS_PER_EMERALD;
        BALANCE.remove(id);
        LAST_SEQ.remove(id);
        INS_SEQ.remove(id);
        INS_BET.remove(id);
        giveEmerald(player, paid);
        send(player, 0, false, paid, remainder, "");
    }

    /** 終了せずに閉じた。没収 (返さない)。 */
    public static void abandon(ServerPlayer player) {
        UUID id = player.getUUID();
        BALANCE.remove(id);
        LAST_SEQ.remove(id);
        INS_SEQ.remove(id);
        INS_BET.remove(id);
    }

    public static void onLogout(UUID id) {
        BALANCE.remove(id);
        LAST_SEQ.remove(id);
        INS_SEQ.remove(id);
        INS_BET.remove(id);
    }

    public static void clear() {
        BALANCE.clear();
        LAST_SEQ.clear();
        INS_SEQ.clear();
        INS_BET.clear();
    }

    private static void send(ServerPlayer player, int balance, boolean active, int paid,
            int remainder, String error) {
        PacketDistributor.sendToPlayer(player,
                new BlackjackBalancePacket(balance, active, paid, remainder, error));
    }

    private static int countEmerald(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeEmerald(ServerPlayer player, int want) {
        int left = want;
        for (ItemStack stack : player.getInventory().items) {
            if (left <= 0) {
                break;
            }
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }

    private static void giveEmerald(ServerPlayer player, int count) {
        int left = count;
        while (left > 0) {
            int n = Math.min(left, new ItemStack(Items.EMERALD, 1).getMaxStackSize());
            ItemStack stack = new ItemStack(Items.EMERALD, n);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            left -= n;
        }
    }
}
