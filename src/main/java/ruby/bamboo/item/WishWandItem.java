package ruby.bamboo.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ruby.bamboo.handler.WishEventHandler;

/**
 * デバッグ用願いワンド。
 * 右クリックで願い入力画面を直接開く。耐久1で1回使用で破壊。
 */
public class WishWandItem extends Item {

    public WishWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            // デバッグ用途: cooldown/chance をバイパスして直接願い発動
            WishEventHandler.triggerForWand(sp);
            if (!player.getAbilities().instabuild) {
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
