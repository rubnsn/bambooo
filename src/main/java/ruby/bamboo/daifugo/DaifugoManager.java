package ruby.bamboo.daifugo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.DaifugoRoomPacket;

/**
 * 大富豪の部屋管理 (サーバー側)。
 * 参加/作成・退出・開始・着手を受け付け、変化があれば個別スナップショットを配信する。
 */
public final class DaifugoManager {
    private static final Map<UUID, DaifugoRoom> ROOMS = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_ROOM = new HashMap<>();
    private static final Random RANDOM = new Random();

    private DaifugoManager() {
    }

    public static void clear() {
        ROOMS.clear();
        PLAYER_ROOM.clear();
    }

    public static DaifugoRoom roomOf(UUID playerId) {
        UUID roomId = PLAYER_ROOM.get(playerId);
        return roomId == null ? null : ROOMS.get(roomId);
    }

    /** 参加受付中の部屋があれば参加、なければ作成 (再入場もここ)。 */
    public static DaifugoRoom joinOrCreate(MinecraftServer server, ServerPlayer player) {
        DaifugoRoom current = roomOf(player.getUUID());
        if (current != null) {
            int idx = current.seatOf(player.getUUID());
            if (idx >= 0) {
                current.rejoin(player.getUUID());
                broadcast(server, current);
                return current;
            }
            PLAYER_ROOM.remove(player.getUUID());
        }
        for (DaifugoRoom room : ROOMS.values()) {
            if (room.state == DaifugoRoom.State.LOBBY && room.addMember(player.getUUID(),
                    player.getGameProfile().getName()) >= 0) {
                PLAYER_ROOM.put(player.getUUID(), room.roomId);
                broadcast(server, room);
                return room;
            }
        }
        DaifugoRoom room = new DaifugoRoom();
        ROOMS.put(room.roomId, room);
        room.addMember(player.getUUID(), player.getGameProfile().getName());
        PLAYER_ROOM.put(player.getUUID(), room.roomId);
        broadcast(server, room);
        return room;
    }

    /** 退出 (ロビー=席削除、対戦中=CPU化)。席に人間がいなくなれば解散。 */
    public static void leave(MinecraftServer server, ServerPlayer player) {
        DaifugoRoom room = roomOf(player.getUUID());
        if (room == null) {
            return;
        }
        int idx = room.seatOf(player.getUUID());
        if (idx < 0) {
            PLAYER_ROOM.remove(player.getUUID());
            return;
        }
        boolean disband;
        if (room.state == DaifugoRoom.State.LOBBY) {
            disband = room.removeLobbyMember(idx);
        } else {
            disband = room.leaveInGame(idx);
        }
        PLAYER_ROOM.remove(player.getUUID());
        if (disband) {
            ROOMS.remove(room.roomId);
        } else {
            broadcast(server, room);
        }
    }

    public static void start(MinecraftServer server, ServerPlayer player) {
        DaifugoRoom room = roomOf(player.getUUID());
        if (room == null) {
            return;
        }
        if (room.seatOf(player.getUUID()) != room.ownerSeat) {
            return;
        }
        if (room.state != DaifugoRoom.State.LOBBY) {
            return;
        }
        room.startGame(RANDOM);
        broadcast(server, room);
    }

    /** ローカルルール設定 (開始前ロビーの部屋主のみ。成功時だけ配信)。 */
    public static void rules(MinecraftServer server, ServerPlayer player, boolean eightCut,
            boolean jback, boolean suitLock, boolean spe3, boolean miyako) {
        DaifugoRoom room = roomOf(player.getUUID());
        if (room == null) {
            return;
        }
        if (room.setRules(player.getUUID(), eightCut, jback, suitLock, spe3, miyako)) {
            broadcast(server, room);
        }
    }

    /** 着手 (空配列=パス)。stairs は [X,JK,JK] リード時の宣言。 */
    public static void action(MinecraftServer server, ServerPlayer player, List<Integer> ids,
            boolean stairs) {
        DaifugoRoom room = roomOf(player.getUUID());
        if (room == null) {
            return;
        }
        int idx = room.seatOf(player.getUUID());
        if (idx < 0 || room.seats[idx].cpuControlled()) {
            return;
        }
        DaifugoRoom.PlayResult result = room.playCards(idx, ids, stairs);
        if (result == DaifugoRoom.PlayResult.OK) {
            broadcast(server, room);
            return;
        }
        String key = switch (result) {
            case BAD_SELECT -> "screen.bamboomod.daifugo_reject_select";
            case BAD_COUNT -> "screen.bamboomod.daifugo_reject_count";
            case TOO_WEAK -> "screen.bamboomod.daifugo_reject_weak";
            case SUIT_LOCK -> "screen.bamboomod.daifugo_reject_lock";
            case NO_PASS_ON_LEAD -> "screen.bamboomod.daifugo_reject_pass";
            case PASSED_OUT -> "screen.bamboomod.daifugo_reject_passed";
            case NOT_TURN, FINISHED -> "screen.bamboomod.daifugo_reject_turn";
            default -> null;
        };
        if (key != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
        }
    }

    /** お返し (TRIBUTE中の上位席。CPU操作席の分はサーバーが自動処理するため無視)。 */
    public static void tribute(MinecraftServer server, ServerPlayer player, List<Integer> ids) {
        DaifugoRoom room = roomOf(player.getUUID());
        if (room == null) {
            return;
        }
        int idx = room.seatOf(player.getUUID());
        if (idx < 0 || room.seats[idx].cpuControlled()) {
            return;
        }
        if (room.completeGiveback(idx, ids)) {
            broadcast(server, room);
        }
    }

    public static void onLogout(UUID playerId, MinecraftServer server) {
        DaifugoRoom room = roomOf(playerId);
        if (room == null) {
            return;
        }
        room.setDisconnected(playerId);
        broadcast(server, room);
    }

    public static void tick(MinecraftServer server) {
        if (ROOMS.isEmpty()) {
            return;
        }
        for (DaifugoRoom room : List.copyOf(ROOMS.values())) {
            if (room.tick(RANDOM, (seat, view, hand) -> {
                CpuBrain.Play play = CpuBrain.choosePlay(view, hand,
                        room.seats[seat].cpu >= 0
                                ? CpuBrain.Personality.values()[room.seats[seat].cpu]
                                : CpuBrain.Personality.CREEPER,
                        RANDOM);
                return play;
            })) {
                broadcast(server, room);
            }
        }
    }

    public static void broadcast(MinecraftServer server, DaifugoRoom room) {
        for (int i = 0; i < DaifugoRoom.SEATS; i++) {
            DaifugoRoom.Seat s = room.seats[i];
            if (s == null || s.playerId == null || !s.connected) {
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(s.playerId);
            if (p == null) {
                continue;
            }
            BambooNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                    new DaifugoRoomPacket(room.snapshotFor(i)));
        }
    }
}
