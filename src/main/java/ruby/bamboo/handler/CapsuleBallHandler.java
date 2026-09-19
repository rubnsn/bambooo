package ruby.bamboo.handler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.FigureEntity;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.FigureOpenPacket;

/**
 * フィギュア関連のサーバー側イベント処理。
 * 設置体への右クリックで調整GUIを開く。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CapsuleBallHandler {

    private CapsuleBallHandler() {
    }

    // ===== フィギュア調整GUIの開放 (設置体への右クリック) =====

    @SubscribeEvent
    public static void onFigureInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof FigureEntity figure)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BambooNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new FigureOpenPacket(figure.getId()));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
