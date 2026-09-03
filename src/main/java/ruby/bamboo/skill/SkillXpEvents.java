package ruby.bamboo.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;

/**
 * 使用ベース xp 付与 (feat-spec-skill §2)。
 * 方針: 入りやすい行動=1、交渉=材料価値で1〜5。
 * 未所持スキルへの加算は Storage 側で無視される。
 * 同期は上昇時のみ (statsu 本の開封時同期は Phase3 で追加)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkillXpEvents {

    private SkillXpEvents() {
    }

    // ===== 採掘3種 + 運 =====

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.level().isClientSide || !(player instanceof ServerPlayer)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof PickaxeItem) {
            SkillHelper.addXp(player, SkillType.PICKAXE, 1);
            if (event.getState().is(Tags.Blocks.ORES)) {
                SkillHelper.addXp(player, SkillType.LUCK, 1);
            }
        } else if (held.getItem() instanceof AxeItem) {
            SkillHelper.addXp(player, SkillType.AXE, 1);
        } else if (held.getItem() instanceof ShovelItem) {
            SkillHelper.addXp(player, SkillType.SHOVEL, 1);
        }
    }

    // ===== 剣・二刀流・射撃 =====

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof ServerPlayer player)) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile) {
            SkillHelper.addXp(player, SkillType.SHOOTING, 1);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof SwordItem) {
            SkillHelper.addXp(player, SkillType.SWORD, 1);
            ItemStack off = player.getOffhandItem();
            if (off.getItem() instanceof SwordItem) {
                SkillHelper.addXp(player, SkillType.DUAL_WIELD, 1);
            }
        }
    }

    // ===== 解剖 =====

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (killer instanceof ServerPlayer player) {
            SkillHelper.addXp(player, SkillType.ANATOMY, 1);
        }
    }

    // ===== 盾 =====

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillHelper.addXp(player, SkillType.SHIELD, 1);
        }
    }

    // ===== 交渉 (材料価値で1〜5) =====

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int count = event.getMerchantOffer().getCostA().getCount()
                + event.getMerchantOffer().getCostB().getCount();
        int amount = Math.max(1, Math.min(5, 1 + count / 16));
        SkillHelper.addXp(player, SkillType.NEGOTIATION, amount);
    }

    // ===== 速度・水泳 (移動距離積算) =====

    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    private static final Map<UUID, Double> MOVE_ACC = new HashMap<>();
    private static final Map<UUID, Double> SWIM_ACC = new HashMap<>();

    /** 速度: 50ブロックで1xp、水泳: 20ブロックで1xp。 */
    private static final double SPEED_BLOCKS = 50.0D;
    private static final double SWIM_BLOCKS = 20.0D;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (!(player instanceof ServerPlayer) || player.isPassenger() || player.getAbilities().flying) {
            return;
        }
        UUID id = player.getUUID();
        double[] last = LAST_POS.get(id);
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        if (last == null) {
            LAST_POS.put(id, new double[] { x, y, z });
            return;
        }
        double dx = x - last[0];
        double dy = y - last[1];
        double dz = z - last[2];
        last[0] = x;
        last[1] = y;
        last[2] = z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01D || dist > 10.0D) {
            return;
        }
        if (player.isInWater()) {
            double acc = SWIM_ACC.getOrDefault(id, 0.0D) + dist;
            if (acc >= SWIM_BLOCKS) {
                acc -= SWIM_BLOCKS;
                SkillHelper.addXp(player, SkillType.SWIM, 1);
            }
            SWIM_ACC.put(id, acc);
        } else {
            double acc = MOVE_ACC.getOrDefault(id, 0.0D) + dist;
            if (acc >= SPEED_BLOCKS) {
                acc -= SPEED_BLOCKS;
                SkillHelper.addXp(player, SkillType.SPEED, 1);
            }
            MOVE_ACC.put(id, acc);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_POS.remove(id);
        MOVE_ACC.remove(id);
        SWIM_ACC.remove(id);
    }
}
