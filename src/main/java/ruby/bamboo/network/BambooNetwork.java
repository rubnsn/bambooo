package ruby.bamboo.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * ネットワーク登録 (1.21.1 NeoForge CustomPacketPayload方式)。
 * client→server: 鈎縄入力・閃跳・願い送信 / server→client: 願い画面開け。
 * 回収分: トランプ4種・スキル・変身・釣り。
 */
public final class BambooNetwork {

    private static final String PROTOCOL_VERSION = "1";

    private BambooNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
            registrar.playToServer(KaginawaInputPacket.TYPE, KaginawaInputPacket.STREAM_CODEC, KaginawaInputPacket::handle);
            registrar.playToServer(FlashJumpPacket.TYPE, FlashJumpPacket.STREAM_CODEC, FlashJumpPacket::handle);
            registrar.playToServer(WishRequestPacket.TYPE, WishRequestPacket.STREAM_CODEC, WishRequestPacket::handle);
            registrar.playToClient(WishOpenPacket.TYPE, WishOpenPacket.STREAM_CODEC, WishOpenPacket::handle);
            // ===== スキル =====
            registrar.playToClient(SkillSyncPacket.TYPE, SkillSyncPacket.STREAM_CODEC, SkillSyncPacket::handle);
            registrar.playToClient(SkillStatusOpenPacket.TYPE, SkillStatusOpenPacket.STREAM_CODEC, SkillStatusOpenPacket::handle);
            registrar.playToClient(SkillReadOpenPacket.TYPE, SkillReadOpenPacket.STREAM_CODEC, SkillReadOpenPacket::handle);
            registrar.playToClient(SkillReadClosePacket.TYPE, SkillReadClosePacket.STREAM_CODEC, SkillReadClosePacket::handle);
            registrar.playToServer(SkillReadCancelPacket.TYPE, SkillReadCancelPacket.STREAM_CODEC, SkillReadCancelPacket::handle);
            // ===== 変身 =====
            registrar.playToClient(TransformSyncPacket.TYPE, TransformSyncPacket.STREAM_CODEC, TransformSyncPacket::handle);
            registrar.playToServer(TransformGlidePacket.TYPE, TransformGlidePacket.STREAM_CODEC, TransformGlidePacket::handle);
            // ===== 釣り =====
            registrar.playToServer(FishingCastRequestPacket.TYPE, FishingCastRequestPacket.STREAM_CODEC, FishingCastRequestPacket::handle);
            registrar.playToClient(FishingCastResultPacket.TYPE, FishingCastResultPacket.STREAM_CODEC, FishingCastResultPacket::handle);
            registrar.playToServer(FishingResultPacket.TYPE, FishingResultPacket.STREAM_CODEC, FishingResultPacket::handle);
            // ===== ブラックジャック (C→S 5 / S→C 2) =====
            registrar.playToServer(BlackjackBetPacket.TYPE, BlackjackBetPacket.STREAM_CODEC, BlackjackBetPacket::handle);
            registrar.playToServer(BlackjackAbandonPacket.TYPE, BlackjackAbandonPacket.STREAM_CODEC, BlackjackAbandonPacket::handle);
            registrar.playToServer(BlackjackSettlePacket.TYPE, BlackjackSettlePacket.STREAM_CODEC, BlackjackSettlePacket::handle);
            registrar.playToServer(BlackjackCashoutPacket.TYPE, BlackjackCashoutPacket.STREAM_CODEC, BlackjackCashoutPacket::handle);
            registrar.playToServer(BlackjackInsurancePacket.TYPE, BlackjackInsurancePacket.STREAM_CODEC, BlackjackInsurancePacket::handle);
            registrar.playToClient(BlackjackOpenPacket.TYPE, BlackjackOpenPacket.STREAM_CODEC, BlackjackOpenPacket::handle);
            registrar.playToClient(BlackjackBalancePacket.TYPE, BlackjackBalancePacket.STREAM_CODEC, BlackjackBalancePacket::handle);
            // ===== 大富豪 (C→S 5 / S→C 1) =====
            registrar.playToServer(DaifugoActionPacket.TYPE, DaifugoActionPacket.STREAM_CODEC, DaifugoActionPacket::handle);
            registrar.playToServer(DaifugoLeavePacket.TYPE, DaifugoLeavePacket.STREAM_CODEC, DaifugoLeavePacket::handle);
            registrar.playToServer(DaifugoRulesPacket.TYPE, DaifugoRulesPacket.STREAM_CODEC, DaifugoRulesPacket::handle);
            registrar.playToServer(DaifugoStartPacket.TYPE, DaifugoStartPacket.STREAM_CODEC, DaifugoStartPacket::handle);
            registrar.playToServer(DaifugoTributePacket.TYPE, DaifugoTributePacket.STREAM_CODEC, DaifugoTributePacket::handle);
            registrar.playToClient(DaifugoRoomPacket.TYPE, DaifugoRoomPacket.STREAM_CODEC, DaifugoRoomPacket::handle);
            // ===== ソリティア / フリーセル =====
            registrar.playToClient(SolitaireOpenPacket.TYPE, SolitaireOpenPacket.STREAM_CODEC, SolitaireOpenPacket::handle);
            registrar.playToClient(FreeCellOpenPacket.TYPE, FreeCellOpenPacket.STREAM_CODEC, FreeCellOpenPacket::handle);
        });
    }
}
