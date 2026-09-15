package ruby.bamboo.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ruby.bamboo.BambooMod;

/**
 * スキル効果の反映 (feat-spec-skill §3)。
 * xp付与 (SkillXpEvents) とは別クラス。素直な +1%/Lv は剣・射撃・採掘のみ、
 * 確率系は別係数。
 *
 * <p>1.21.1 NeoForge: 旧 {@code LivingHurtEvent} は {@code LivingDamageEvent.Pre}
 * (装甲適用後の値に乗算) に、旧 {@code ShieldBlockEvent} は
 * {@code LivingShieldBlockEvent} (要 {@code getBlocked()} 判定・耐久スキップは
 * {@code setShieldDamage(0)}) に、旧 Tick は {@code PlayerTickEvent.Post} に置換。
 * 耐久消費は {@code hurtAndBreak(int, LivingEntity, EquipmentSlot)} 形式。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SkillEffectEvents {

    private SkillEffectEvents() {
    }

    /** 二刀流の遅延オフハンド振り (UUID→残tick)。 */
    private static final Map<UUID, Integer> PENDING_SWING = new HashMap<>();

    // ===== 採掘3種 =====

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        SkillType type = null;
        if (held.getItem() instanceof PickaxeItem) {
            type = SkillType.PICKAXE;
        } else if (held.getItem() instanceof AxeItem) {
            type = SkillType.AXE;
        } else if (held.getItem() instanceof ShovelItem) {
            type = SkillType.SHOVEL;
        }
        if (type == null) {
            return;
        }
        int lv = SkillHelper.getLevel(player, type);
        if (lv > 0) {
            event.setNewSpeed(event.getNewSpeed() * (1.0F + 0.01F * lv));
        }
    }

    // ===== 剣・射撃・二刀流 =====

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile) {
            int lv = SkillHelper.getLevel(player, SkillType.SHOOTING);
            if (lv > 0) {
                event.setNewDamage(event.getNewDamage() * (1.0F + 0.01F * lv));
            }
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof SwordItem)) {
            return;
        }
        int swordLv = SkillHelper.getLevel(player, SkillType.SWORD);
        float amount = event.getNewDamage();
        if (swordLv > 0) {
            amount = amount * (1.0F + 0.01F * swordLv);
        }
        if (player.getOffhandItem().getItem() instanceof SwordItem) {
            int dualLv = SkillHelper.getLevel(player, SkillType.DUAL_WIELD);
            if (dualLv > 0) {
                amount = amount + event.getNewDamage() * 0.05F * dualLv;
                // 即時swingはクラのメイン振りと重なりガードで捨てられるため4tick遅延させる
                PENDING_SWING.put(player.getUUID(), 4);
                ItemStack off = player.getOffhandItem();
                if (off.isDamageableItem() && player instanceof ServerPlayer sp) {
                    off.hurtAndBreak(1, sp, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                }
            }
        }
        event.setNewDamage(amount);
    }

    // ===== 運 (鉱石のおまけ: BreakEvent + ドロッププレビュー方式) =====
    // GlobalLootModifier だと新規 DeferredRegister が必要になるため、
    // 破壊確定時に getDrops でプレビューし、確率で1スタック複写を追加する。
    // プレビューと実ドロップは別RNGのため内容がずれる場合がある (仕様)。

    @SubscribeEvent
    public static void onLuckBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.level().isClientSide || !(player instanceof ServerPlayer sp) || player.isCreative()) {
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof PickaxeItem)) {
            return;
        }
        if (!event.getState().is(Tags.Blocks.ORES)) {
            return;
        }
        int lv = SkillHelper.getLevel(player, SkillType.LUCK);
        if (lv <= 0 || sp.getRandom().nextFloat() >= 0.02F * lv) {
            return;
        }
        ServerLevel level = sp.serverLevel();
        var be = level.getBlockEntity(event.getPos());
        List<ItemStack> preview = Block.getDrops(
                event.getState(), level, event.getPos(), be, player, player.getMainHandItem());
        if (preview.isEmpty()) {
            return;
        }
        ItemStack bonus = preview.get(sp.getRandom().nextInt(preview.size())).copy();
        if (!bonus.isEmpty()) {
            Block.popResource(level, event.getPos(), bonus);
        }
    }

    // ===== 水泳 (呼吸) =====

    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getEntity().level().isClientSide) {
            return;
        }
        int lv = SkillHelper.getLevel(player, SkillType.SWIM);
        if (lv > 0 && !event.canBreathe() && event.getConsumeAirAmount() > 0) {
            if (player.getRandom().nextFloat() < 0.05F * lv) {
                event.setConsumeAirAmount(0);
            }
        }
    }

    // ===== 解剖 (率UP: ドロップ複写) =====

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Player player)) {
            return;
        }
        int lv = SkillHelper.getLevel(player, SkillType.ANATOMY);
        if (lv <= 0 || event.getDrops().isEmpty()) {
            return;
        }
        float chance = 0.02F * lv;
        ArrayList<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            if (player.getRandom().nextFloat() < chance && !drop.getItem().isEmpty()) {
                ItemEntity copy = new ItemEntity(drop.level(), drop.getX(), drop.getY(), drop.getZ(),
                        drop.getItem().copy());
                copy.setDefaultPickUpDelay();
                extra.add(copy);
            }
        }
        event.getDrops().addAll(extra);
    }

    // ===== 交渉 (成立時確率で材料返金) =====

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int lv = SkillHelper.getLevel(player, SkillType.NEGOTIATION);
        if (lv <= 0) {
            return;
        }
        if (player.getRandom().nextFloat() < 0.02F * lv) {
            ItemStack refund = event.getMerchantOffer().getCostA().copy();
            if (!refund.isEmpty()) {
                if (!player.getInventory().add(refund)) {
                    player.drop(refund, false);
                }
            }
        }
    }

    // ===== 盾 (耐久スキップ + 構え減速軽減) =====

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled() || !event.getBlocked()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        int lv = SkillHelper.getLevel(player, SkillType.SHIELD);
        if (lv > 0 && player.getRandom().nextFloat() < 0.05F * lv) {
            event.setShieldDamage(0.0F);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        SkillEffects.applyShield(player, player.isBlocking());
        Integer left = PENDING_SWING.get(player.getUUID());
        if (left != null) {
            if (left <= 1) {
                PENDING_SWING.remove(player.getUUID());
                player.swing(InteractionHand.OFF_HAND, true);
            } else {
                PENDING_SWING.put(player.getUUID(), left - 1);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_SWING.remove(event.getEntity().getUUID());
    }
}
