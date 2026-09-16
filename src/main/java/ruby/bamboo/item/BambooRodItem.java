package ruby.bamboo.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import ruby.bamboo.handler.FishingHandler;

/**
 * 竹竿。パワーゲージトグル方式 (右クリックでゲージ表示→再右クリックで決定)。
 * ミニゲーム中は仮想GUIで移動ロック。
 *
 * <p>1.21.1 NeoForge: getUseDuration は (ItemStack, LivingEntity) 形式、
 * appendHoverText は TooltipContext 形式。クライアント画面開けは
 * DistExecutor (1.21 撤去) ではなく直接呼び出し
 * (level.isClientSide ガード下のみ実行されるため専用サーバーで
 * クライアントクラスは解決されない)。
 */
public class BambooRodItem extends BambooItem {

    public static final int MAX_CHARGE_TICKS = 20;

    public BambooRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 餌チェックのみ（pendingは上書きで安全、GUIで封じられる）
        if (!FishingHandler.hasBait(player)) {
            player.displayClientMessage(Component.translatable("message.bamboomod.fishing.no_bait").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (level.isClientSide) {
            // 仮想 GUI を開く
            ruby.bamboo.client.handler.ClientFishingHandler.openPowerGauge();
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // パワーゲージ中は通常の弓の発射を行わない
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bamboomod.bamboo_rod.gauge_hint")
                .withStyle(ChatFormatting.AQUA));
    }
}
