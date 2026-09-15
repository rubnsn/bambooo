package ruby.bamboo.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import ruby.bamboo.BambooMod;
import ruby.bamboo.client.handler.ClientTransformHandler;
import ruby.bamboo.transform.TransformEatHelper;

/**
 * 非食料の摂取 (長押し食べ) のクライアント側。
 * 即時開始 + duration 32 のみ担当し、効果はサーバが適用する。
 * 右クリックはキャンセルしない (キャンセルするとサーバへパケットが飛ばない)。
 * 口元モーションは Mixin が使用中の対象品のみ EAT 扱いにする。
 *
 * <p>1.21.1 NeoForge: GAME bus + NeoForge イベントへ移行。中身は原文のまま。
 * なお口元モーション用 ItemStackEatMixin は本移植の対象外 (mixins.json 編集が
 * 禁止のため未作成。親側で追加すること)。
 */
@EventBusSubscriber(modid = BambooMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class TransformEatClientHandler {

    private record Pending(InteractionHand hand, Item item) {
    }

    private static final Map<UUID, Pending> CLIENT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRightClickItemClient(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        String id = ClientTransformHandler.get(player.getUUID());
        if (id.isEmpty() || player.isUsingItem()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (TransformEatHelper.match(id, stack, player) == null) {
            return;
        }
        CLIENT.put(player.getUUID(), new Pending(event.getHand(), stack.getItem()));
        player.startUsingItem(event.getHand());
    }

    @SubscribeEvent
    public static void onUseStartClient(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        Pending p = CLIENT.get(player.getUUID());
        if (p != null && event.getItem().is(p.item())) {
            event.setDuration(TransformEatHelper.EAT_DURATION);
        }
    }

    @SubscribeEvent
    public static void onUseStopClient(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof LocalPlayer player) {
            CLIENT.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onUseFinishClient(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof LocalPlayer player) {
            CLIENT.remove(player.getUUID());
        }
    }

    /** Mixin 用: ローカルが摂取使用中の対象品のみ true。 */
    public static boolean shouldForceEatAnimation(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isUsingItem() || stack.isEmpty()) {
            return false;
        }
        Pending p = CLIENT.get(player.getUUID());
        return p != null && stack.is(p.item());
    }
}
