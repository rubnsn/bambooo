package ruby.bamboo.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
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
    public int getMaxStackSize(ItemStack stack) {
        if (CapsuleRules.isCaptured(stack) || CapsuleRules.isBoundEmpty(stack)) return 1;
        return 16;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(held);
        }
        if (CapsuleRules.isCaptured(held)) {
            // 召喚投擲。ボールは消費しない (projectile はコピーを持つ)。
            // 着弾→実体化時に手元の対応ボールを紐付け空へ変える (doSummon側)
            CapsuleBallEntity ball = new CapsuleBallEntity(level, player, held, CapsuleBallEntity.MODE_SUMMON);
            ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2F, 1.0F);
            level.addFreshEntity(ball);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.8F);
            player.getCooldowns().addCooldown(this, 10);
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.success(held);
        }
        if (CapsuleRules.isBoundEmpty(held)) {
            // 紐付け空は投げられない。対象個体への右クリックで格納する
            player.displayClientMessage(
                    Component.translatable("message.bamboomod.capsule_ball_store_hint"), true);
            return InteractionResultHolder.fail(held);
        }
        // 空: 捕獲投擲 (クリエ以外1消費)
        CapsuleBallEntity ball = new CapsuleBallEntity(level, player, held, CapsuleBallEntity.MODE_CAPTURE);
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

    /**
     * 格納は {@link ruby.bamboo.handler.CapsuleBallHandler#onStoreInteract} 側で行う
     * (Mob側の右クリック処理に先に消費される個体があるため)。ここでは何もしない。
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        return InteractionResult.PASS;
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
        var tag = stack.getOrCreateTag();
        if (tag.getBoolean(CapsuleRules.TAG_CAPTURED) && tag.contains(CapsuleRules.TAG_ENTITY_ID)) {
            String name = CapsuleRules.describeEntityId(tag.getString(CapsuleRules.TAG_ENTITY_ID));
            tooltip.add(Component.translatable("tooltip.bamboomod.capsule_ball.filled", name)
                    .withStyle(ChatFormatting.AQUA));
            if (tag.contains(CapsuleRules.TAG_ENTITY)) {
                float hp = tag.getCompound(CapsuleRules.TAG_ENTITY).getFloat("Health");
                float max = (float) tag.getDouble(CapsuleRules.TAG_MAX_HP);
                if (max > 0.0F) {
                    tooltip.add(Component.translatable("tooltip.bamboomod.capsule_ball.hp",
                            String.format("%.1f", Math.max(0.0F, hp)), String.format("%.1f", max))
                            .withStyle(ChatFormatting.RED));
                }
            }
        } else if (tag.hasUUID(CapsuleRules.TAG_BOUND) && tag.contains(CapsuleRules.TAG_ENTITY_ID)) {
            String name = CapsuleRules.describeEntityId(tag.getString(CapsuleRules.TAG_ENTITY_ID));
            tooltip.add(Component.translatable("tooltip.bamboomod.capsule_ball.bound", name)
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable("tooltip.bamboomod.capsule_ball.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
