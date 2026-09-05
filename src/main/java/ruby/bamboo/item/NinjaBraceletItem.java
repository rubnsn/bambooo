package ruby.bamboo.item;

import java.util.Comparator;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
            if (hasEnchant(level.registryAccess(), bracelet, BambooEnchantments.SNIPE_THROW)) {
                velocity *= 1.1F;
                inaccuracy *= 0.5F;
            }
            entity.setItemStack(shurikenStack.copyWithCount(1));
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, velocity, inaccuracy);
            level.addFreshEntity(entity);

            player.getCooldowns().addCooldown(this, getThrowingDelay(level.registryAccess(), bracelet));

            if (!player.getAbilities().instabuild) {
                if (!entity.isNoPickup()) {
                    shurikenStack.shrink(1);
                }
                if (!isUnbreaking(level.registryAccess(), bracelet, level.getRandom())) {
                    bracelet.hurtAndBreak(1, player,
                            hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                                    : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
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

    // ===== Enchant helpers (1.21: datapack ResourceKey + HolderLookup 経由で解決) =====

    public int getEnchantLv(HolderLookup.Provider provider, ItemStack stack, ResourceKey<Enchantment>... enchs) {
        int max = 0;
        for (ResourceKey<Enchantment> key : enchs) {
            max = Math.max(max, BambooEnchantments.getLevel(provider, key, stack));
        }
        return max;
    }

    public boolean hasEnchant(HolderLookup.Provider provider, ItemStack stack, ResourceKey<Enchantment>... enchs) {
        return getEnchantLv(provider, stack, enchs) > 0;
    }

    public float getThrowingDamage(HolderLookup.Provider provider, ItemStack bracelet) {
        int lv = getEnchantLv(provider, bracelet, BambooEnchantments.POWER_THROW);
        // 10%/lv -> +0.5/lv (旧式踏襲、stone2.0でlv5なら4.5)
        return lv * 0.5F;
    }

    public int getThrowingDelay(HolderLookup.Provider provider, ItemStack bracelet) {
        int lv = getEnchantLv(provider, bracelet, BambooEnchantments.QUICK_THROW);
        int cool = 20 - lv * 2;
        return Math.max(5, cool);
    }

    public float getEconomyRate(HolderLookup.Provider provider, ItemStack stack) {
        int lv = getEnchantLv(provider, stack, BambooEnchantments.ECONOMY_BRACELET);
        return lv * 0.1F;
    }

    boolean isUnbreaking(HolderLookup.Provider provider, ItemStack stack, net.minecraft.util.RandomSource random) {
        int lv = getEnchantLv(provider, stack, BambooEnchantments.UNBREAKING_BRACELET);
        return random.nextFloat() < lv * 0.25F;
    }

    void applyEnchantments(ShurikenEntity entity, ItemStack bracelet, ItemStack shurikenStack) {
        HolderLookup.Provider provider = entity.level().registryAccess();
        // power
        float dmg = getThrowingDamage(provider, bracelet);
        entity.setBaseDamage(dmg);

        // crit
        int critLv = getEnchantLv(provider, bracelet, BambooEnchantments.CRITICAL_THROW);
        if (critLv > 0) {
            float crit = critLv * 0.1F;
            if (entity.level().getRandom().nextFloat() < crit) {
                entity.setCritArrow(true);
            }
        }

        // poison
        int poisonLv = getEnchantLv(provider, bracelet, BambooEnchantments.POISON_THROW);
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
        if (hasEnchant(provider, bracelet, BambooEnchantments.FLAME_THROW)) {
            entity.setRemainingFireTicks(100);
        }

        // infinity
        if (hasEnchant(provider, bracelet, BambooEnchantments.INFINITY_THROW)) {
            if (shurikenStack.getItem() instanceof ShurikenItem shuriken) {
                entity.setInfinity(shuriken.getTier());
                entity.setNonPickup();
            }
        } else if (entity.level().getRandom().nextFloat() < getEconomyRate(provider, bracelet)) {
            // economy (infinityが無い場合のみ)
            entity.setNonPickup();
        }

        // snipe
        int snipeLv = getEnchantLv(provider, bracelet, BambooEnchantments.SNIPE_THROW);
        if (snipeLv > 0) {
            entity.setSnipeLevel(snipeLv);
        }

        // double / triple
        if (hasEnchant(provider, bracelet, BambooEnchantments.DOUBLE_THROW)) {
            entity.setMultiThrow(1);
        } else if (hasEnchant(provider, bracelet, BambooEnchantments.TRIPLE_THROW)) {
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

    // canApplyAtEnchantingTable は 1.21 では datapack の supported_items に一任するためオーバーライドしない
    // (対象アイテム bamboomod:ninja_bracelet は data/bamboomod/enchantment/*.json で指定)

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        try {
            var clientLevel = net.minecraft.client.Minecraft.getInstance().level;
            if (clientLevel != null) {
                HolderLookup.Provider provider = clientLevel.registryAccess();
                float dmg = getThrowingDamage(provider, stack);
                int delay = getThrowingDelay(provider, stack);
                int eco = (int) (getEconomyRate(provider, stack) * 100);
                if (dmg > 0) {
                    tooltip.add(Component.translatable("attribute.modifier.equals.0", String.valueOf(dmg), Component.translatable("tooltip.bamboomod.throwing_damage")).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
                }
                tooltip.add(Component.translatable("attribute.modifier.equals.0", String.valueOf(delay), Component.translatable("tooltip.bamboomod.throwing_delay")).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
                if (eco > 0) {
                    tooltip.add(Component.translatable("attribute.modifier.equals.0", String.valueOf(eco), Component.translatable("tooltip.bamboomod.throwing_save")).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
                }
            }
        } catch (Exception e) {
            // サーバ等で解決できない場合は数値行を省略
        }
        tooltip.add(Component.translatable("tooltip.bamboomod.flash_jump").withStyle(net.minecraft.ChatFormatting.AQUA));
    }
}
