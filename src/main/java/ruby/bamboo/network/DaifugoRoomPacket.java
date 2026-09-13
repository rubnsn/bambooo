package ruby.bamboo.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientDaifugoHandler;
import ruby.bamboo.daifugo.DaifugoSnapshot;

/**
 * S→C 大富豪の部屋スナップショット (要求席向け個別内容)。
 */
public class DaifugoRoomPacket {
    public DaifugoSnapshot snapshot = new DaifugoSnapshot();

    public DaifugoRoomPacket() {
    }

    public DaifugoRoomPacket(DaifugoSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static void encode(DaifugoRoomPacket msg, FriendlyByteBuf buf) {
        DaifugoSnapshot s = msg.snapshot;
        buf.writeUUID(s.roomId);
        buf.writeInt(s.state);
        buf.writeInt(s.round);
        buf.writeInt(s.mySeat);
        buf.writeInt(s.ownerSeat);
        for (int i = 0; i < 4; i++) {
            DaifugoSnapshot.SeatView v = i < s.seats.size() ? s.seats.get(i) : null;
            buf.writeBoolean(v != null);
            if (v != null) {
                buf.writeUtf(v.name(), 64);
                buf.writeInt(v.cpu());
                buf.writeBoolean(v.connected());
                buf.writeInt(v.handCount());
                buf.writeInt(v.roundRank());
                buf.writeInt(v.prevRank());
            }
        }
        buf.writeVarIntArray(s.hand.stream().mapToInt(Integer::intValue).toArray());
        buf.writeVarIntArray(s.table.stream().mapToInt(Integer::intValue).toArray());
        buf.writeInt(s.tableSeat);
        buf.writeBoolean(s.tableStairs);
        buf.writeInt(s.turnSeat);
        buf.writeVarIntArray(s.passedOut.stream().mapToInt(Integer::intValue).toArray());
        buf.writeBoolean(s.revolution);
        buf.writeBoolean(s.jback);
        buf.writeBoolean(s.ruleEightCut);
        buf.writeBoolean(s.ruleJBack);
        buf.writeBoolean(s.ruleSuitLock);
        buf.writeBoolean(s.ruleSpe3);
        buf.writeBoolean(s.ruleMiyako);
        buf.writeVarIntArray(s.lockSuits.stream().mapToInt(Integer::intValue).toArray());
        buf.writeVarIntArray(s.tributeOwed.stream().mapToInt(Integer::intValue).toArray());
        buf.writeInt(s.log.size());
        for (DaifugoSnapshot.LogEntry e : s.log) {
            buf.writeUtf(e.key(), 128);
            buf.writeInt(e.args().size());
            for (String a : e.args()) {
                buf.writeUtf(a, 128);
            }
        }
    }

    public static DaifugoRoomPacket decode(FriendlyByteBuf buf) {
        DaifugoRoomPacket msg = new DaifugoRoomPacket();
        DaifugoSnapshot s = msg.snapshot;
        s.roomId = buf.readUUID();
        s.state = buf.readInt();
        s.round = buf.readInt();
        s.mySeat = buf.readInt();
        s.ownerSeat = buf.readInt();
        List<DaifugoSnapshot.SeatView> seats = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            if (buf.readBoolean()) {
                seats.add(new DaifugoSnapshot.SeatView(buf.readUtf(64), buf.readInt(),
                        buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt()));
            } else {
                seats.add(null);
            }
        }
        s.seats = seats;
        List<Integer> hand = new ArrayList<>();
        for (int id : buf.readVarIntArray()) {
            hand.add(id);
        }
        s.hand = hand;
        List<Integer> table = new ArrayList<>();
        for (int id : buf.readVarIntArray()) {
            table.add(id);
        }
        s.table = table;
        s.tableSeat = buf.readInt();
        s.tableStairs = buf.readBoolean();
        s.turnSeat = buf.readInt();
        List<Integer> passedOut = new ArrayList<>();
        for (int n : buf.readVarIntArray()) {
            passedOut.add(n);
        }
        s.passedOut = passedOut;
        s.revolution = buf.readBoolean();
        s.jback = buf.readBoolean();
        s.ruleEightCut = buf.readBoolean();
        s.ruleJBack = buf.readBoolean();
        s.ruleSuitLock = buf.readBoolean();
        s.ruleSpe3 = buf.readBoolean();
        s.ruleMiyako = buf.readBoolean();
        List<Integer> lockSuits = new ArrayList<>();
        for (int suit : buf.readVarIntArray()) {
            lockSuits.add(suit);
        }
        s.lockSuits = lockSuits;
        List<Integer> owed = new ArrayList<>();
        for (int n : buf.readVarIntArray()) {
            owed.add(n);
        }
        s.tributeOwed = owed;
        List<DaifugoSnapshot.LogEntry> log = new ArrayList<>();
        int logSize = buf.readInt();
        for (int i = 0; i < logSize; i++) {
            String key = buf.readUtf(128);
            int argCount = buf.readInt();
            List<String> args = new ArrayList<>(argCount);
            for (int k = 0; k < argCount; k++) {
                args.add(buf.readUtf(128));
            }
            log.add(new DaifugoSnapshot.LogEntry(key, args));
        }
        s.log = log;
        return msg;
    }

    public static void handle(DaifugoRoomPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientDaifugoHandler.onSnapshot(msg.snapshot)));
        ctx.get().setPacketHandled(true);
    }
}
