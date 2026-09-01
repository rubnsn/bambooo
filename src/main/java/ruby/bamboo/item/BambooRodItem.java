package ruby.bamboo.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import ruby.bamboo.handler.FishingHandler;

/**
 * 竹竿。パワーゲージトグル方式 (右クリックでゲージ表示→再右クリックで決定)。
 * ミニゲーム中は仮想GUIで移動ロック。
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
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 餌チェックのみ（pendingは上書きで安全、GUIで封じられる）
        if (!level.isClientSide) {
            if (!FishingHandler.hasBait(player)) {
                return InteractionResultHolder.fail(stack);
            }
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        } else {
            if (!FishingHandler.hasBait(player)) {
                return InteractionResultHolder.fail(stack);
            }
            player.startUsingItem(hand);
            // 仮想 GUI を開く (DistExecutor でクライアントクラスロードを回避)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                try {
                    Class<?> cls = Class.forName("ruby.bamboo.client.handler.ClientFishingHandler");
                    cls.getMethod("openPowerGauge").invoke(null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // パワーゲージ中は通常の弓の発射を行わない
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bamboomod.bamboo_rod.power", FishingHandler.ROD_POWER)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bamboomod.bamboo_rod.bite_bonus", 0)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bamboomod.bamboo_rod.gauge_hint")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
