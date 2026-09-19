package ruby.bamboo.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ruby.bamboo.capsule.CapsuleRules;
import ruby.bamboo.capsule.CapsuleTier;
import ruby.bamboo.entity.CapsuleBallEntity;

/**
 * カプセルボール (docs/port-spec-capsule-ball.md §1)。
 * 空は16スタック、捕獲済み・紐付け済みは1スタック。
 */
public class CapsuleBallItem extends Item {

    private final CapsuleTier tier;

    public CapsuleBallItem(CapsuleTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public CapsuleTier getTier() {
        return tier;
    }

    public static CapsuleTier tierOf(ItemStack stack) {
        if (stack.getItem() instanceof CapsuleBallItem ball) return ball.tier;
        return CapsuleTier.byId(stack.getOrCreateTag().getString(CapsuleRules.TAG_TIER));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(held);
        }
        // 捕獲投擲あるのみ (クリエ以外1消費。成否問わず返却なし)
        CapsuleBallEntity ball = new CapsuleBallEntity(level, player, held);
        ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2F, 1.0F);
        level.addFreshEntity(ball);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.8F);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.getCooldowns().addCooldown(this, 10);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.success(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        // Tier補正
        if (tier.bonus >= 100) {
            tooltip.add(Component.translatable("tooltip.bamboomod.capsule_ball.bonus_max")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.bamboomod.capsule_ball.bonus", tier.bonus)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
