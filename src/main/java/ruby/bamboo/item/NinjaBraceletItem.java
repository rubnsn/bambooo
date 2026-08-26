package ruby.bamboo.item;

import java.util.Comparator;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import ruby.bamboo.core.init.BambooEnchantments;
import ruby.bamboo.entity.ShurikenEntity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 手裏剣腕輪 (sakura NinjaBracelet の 1.20.1 移植)。
 * 即射+クール20tick、弾はインベントリの Shuriken を検索。
 */
public class NinjaBraceletItem extends Item {

    public static final float VELOCITY_BASE = 1.25F;
    public static final float INACCURACY_BASE = 1.75F;

    public NinjaBraceletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bracelet = player.getItemInHand(hand);
        ItemStack shurikenStack = findShuriken(player);
        if (shurikenStack.isEmpty()) {
            return InteractionResultHolder.fail(bracelet);
        }

        player.swing(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!level.isClientSide) {
            ShurikenEntity entity = new ShurikenEntity(level, player);
            // エンチャント適用
            applyEnchantments(entity, bracelet, shurikenStack);
            // snipe補正
            float velocity = VELOCITY_BASE;
            float inaccuracy = INACCURACY_BASE;
            if (hasEnchant(bracelet, BambooEnchantments.SNIPE_THROW.get())) {
                velocity *= 1.1F;
                inaccuracy *= 0.5F;
            }
            entity.setItemStack(shurikenStack.copyWithCount(1));
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, velocity, inaccuracy);
            level.addFreshEntity(entity);

            player.getCooldowns().addCooldown(this, getThrowingDelay(bracelet));

            if (!player.getAbilities().instabuild) {
                if (!entity.isNoPickup()) {
                    shurikenStack.shrink(1);
                }
                if (!isUnbreaking(bracelet, level.getRandom())) {
                    bracelet.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                }
            }
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(bracelet, level.isClientSide());
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (entity instanceof LivingEntity le) {
            le.knockback(1.0F, Mth.sin(player.getYRot() * ((float) Math.PI / 180F)), -Mth.cos(player.getYRot() * ((float) Math.PI / 180F)));
        }
        return true;
    }

    private ItemStack findShuriken(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && s.getItem() instanceof ShurikenItem) return s;
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (!s.isEmpty() && s.getItem() instanceof ShurikenItem) return s;
        }
        return ItemStack.EMPTY;
    }

    // ===== Enchant helpers =====

    public int getEnchantLv(ItemStack stack, Enchantment... enchs) {
        return Stream.of(enchs).map(e -> EnchantmentHelper.getItemEnchantmentLevel(e, stack)).max(Comparator.naturalOrder()).orElse(0);
    }

    public boolean hasEnchant(ItemStack stack, Enchantment... enchs) {
        return getEnchantLv(stack, enchs) > 0;
    }

    public float getThrowingDamage(ItemStack bracelet) {
        int lv = getEnchantLv(bracelet, BambooEnchantments.POWER_THROW.get());
        // 10%/lv -> +0.5/lv (旧式踏襲、stone2.0でlv5なら4.5)
        return lv * 0.5F;
    }

    public int getThrowingDelay(ItemStack bracelet) {
        int lv = getEnchantLv(bracelet, BambooEnchantments.QUICK_THROW.get());
        int cool = 20 - lv * 2;
        return Math.max(5, cool);
    }

    public float getEconomyRate(ItemStack stack) {
        int lv = getEnchantLv(stack, BambooEnchantments.ECONOMY_BRACELET.get());
        return lv * 0.1F;
    }

    boolean isUnbreaking(ItemStack stack, net.minecraft.util.RandomSource random) {
        int lv = getEnchantLv(stack, BambooEnchantments.UNBREAKING_BRACELET.get());
        return random.nextFloat() < lv * 0.25F;
    }

    void applyEnchantments(ShurikenEntity entity, ItemStack bracelet, ItemStack shurikenStack) {
        // power
        float dmg = getThrowingDamage(bracelet);
        entity.setBaseDamage(dmg);

        // crit
        int critLv = getEnchantLv(bracelet, BambooEnchantments.CRITICAL_THROW.get());
        if (critLv > 0) {
            float crit = critLv * 0.1F;
            if (entity.level().getRandom().nextFloat() < crit) {
                entity.setCritArrow(true);
            }
        }

        // poison
        int poisonLv = getEnchantLv(bracelet, BambooEnchantments.POISON_THROW.get());
        if (poisonLv > 0) {
            boolean isVenom = poisonLv >= 3;
            int amp = isVenom ? 1 : 0;
            int duration = isVenom ? 15 * 20 : 10 * 20;
            // ランダム選択: POISON or WITHER + WEAKNESS/SLOWNESS/GLOWING
            if (isVenom && entity.level().getRandom().nextBoolean()) {
                entity.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, amp));
            } else {
                // lv1-2はPOISON、lv3はWITHER(50%)
                if (isVenom) {
                    entity.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, amp));
                } else {
                    entity.addEffect(new MobEffectInstance(MobEffects.POISON, duration, 0));
                }
            }
            // 追加デバフ 30%程度
            if (entity.level().getRandom().nextFloat() < 0.3F) {
                MobEffectInstance[] extra = new MobEffectInstance[]{
                        new MobEffectInstance(MobEffects.WEAKNESS, 10 * 20, 0),
                        new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10 * 20, 0),
                        new MobEffectInstance(MobEffects.GLOWING, 10 * 20, 0)
                };
                entity.addEffect(extra[entity.level().getRandom().nextInt(extra.length)]);
            }
        }

        // flame
        if (hasEnchant(bracelet, BambooEnchantments.FLAME_THROW.get())) {
            entity.setSecondsOnFire(100);
        }

        // infinity
        if (hasEnchant(bracelet, BambooEnchantments.INFINITY_THROW.get())) {
            if (shurikenStack.getItem() instanceof ShurikenItem shuriken) {
                entity.setInfinity(shuriken.getTier());
                entity.setNonPickup();
            }
        } else if (entity.level().getRandom().nextFloat() < getEconomyRate(bracelet)) {
            // economy (infinityが無い場合のみ)
            entity.setNonPickup();
        }

        // snipe
        int snipeLv = getEnchantLv(bracelet, BambooEnchantments.SNIPE_THROW.get());
        if (snipeLv > 0) {
            entity.setSnipeLevel(snipeLv);
        }

        // double / triple
        if (hasEnchant(bracelet, BambooEnchantments.DOUBLE_THROW.get())) {
            entity.setMultiThrow(1);
        } else if (hasEnchant(bracelet, BambooEnchantments.TRIPLE_THROW.get())) {
            entity.setMultiThrow(2);
        }
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 120;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment.category == BraceletEnchantmentCategory.BRACELET;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        float dmg = getThrowingDamage(stack);
        int delay = getThrowingDelay(stack);
        int eco = (int) (getEconomyRate(stack) * 100);
        if (dmg > 0) {
            tooltip.add(Component.translatable("attribute.modifier.equals.0", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(dmg), Component.translatable("tooltip.bamboomod.throwing_damage")).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
        }
        tooltip.add(Component.translatable("attribute.modifier.equals.0", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(delay), Component.translatable("tooltip.bamboomod.throwing_delay")).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
        if (eco > 0) {
            tooltip.add(Component.translatable("attribute.modifier.equals.0", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(eco), Component.translatable("tooltip.bamboomod.throwing_save")).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
        }
    }
}
