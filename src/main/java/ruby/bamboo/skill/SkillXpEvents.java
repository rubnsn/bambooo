package ruby.bamboo.skill;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ruby.bamboo.BambooMod;

/**
 * 使用ベース xp 付与 (feat-spec-skill §2)。
 * 方針: 入りやすい行動=1、交渉=材料価値で1〜5。
 * 未所持スキルへの加算は Storage 側で無視される。
 * 同期は上昇時のみ (statsu 本の開封時同期は Phase3 で追加)。
 *
 * <p>1.21.1 NeoForge: 旧 {@code LivingHurtEvent} は {@code LivingDamageEvent.Pre} に、
 * 旧 {@code ShieldBlockEvent} は {@code LivingShieldBlockEvent} (要 {@code getBlocked()} 判定) に、
 * 旧 {@code TickEvent.PlayerTickEvent} は {@code PlayerTickEvent.Post} に置換。
 *
 * <p>FISHING xp フック点 (別chore: fishing): 釣り成功時に fishing 側から
 * {@code SkillHelper.addXp(player, SkillType.FISHING, n)} を呼ぶこと。
 * skill 側に FishingHandler 参照は持たない (循環防止)。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
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
        // 適正ツールでのみ付与 (草・葉などの不適物は除外)。isCorrectToolForDrops 準拠
        if (!held.isCorrectToolForDrops(event.getState())) {
            return;
        }
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
    public static void onHurt(LivingDamageEvent.Pre event) {
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
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled() || !event.getBlocked()) {
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

    // ===== 速度・水泳 (バニラ統計値の差分積算) =====
    // walk/swim系カスタム統計のみを見るため、飛行・騎乗・ボートの混入なし。
    // 基準値は Cap 保持 (ログアウト掃除不要・メモリリークなし)。

    /** 速度: 5000cm(50BL)で1xp、水泳: 2000cm(20BL)で1xp。 */
    private static final long SPEED_CM = 5000L;
    private static final long SWIM_CM = 2000L;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (!(player instanceof ServerPlayer sp) || player.isPassenger()) {
            return;
        }
        var stats = sp.getStats();
        int walk = stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.WALK_ON_WATER_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.WALK_UNDER_WATER_ONE_CM));
        int swim = stats.getValue(Stats.CUSTOM.get(Stats.SWIM_ONE_CM));
        SkillStorage s = SkillHelper.get(sp);
        long dw = (long) walk - s.getWalkBase();
        s.setWalkBase(walk);
        if (dw > 0) {
            if (!s.isAcquired(SkillType.SPEED) || s.isMaxed(SkillType.SPEED)) {
                s.setWalkAcc(0);
            } else {
                long acc = s.getWalkAcc() + dw;
                while (acc >= SPEED_CM) {
                    acc -= SPEED_CM;
                    SkillHelper.addXp(sp, SkillType.SPEED, 1);
                    if (s.isMaxed(SkillType.SPEED)) {
                        acc = 0;
                        break;
                    }
                }
                s.setWalkAcc(acc);
            }
        }
        long dsw = (long) swim - s.getSwimBase();
        s.setSwimBase(swim);
        if (dsw > 0) {
            if (!s.isAcquired(SkillType.SWIM) || s.isMaxed(SkillType.SWIM)) {
                s.setSwimAcc(0);
            } else {
                long acc = s.getSwimAcc() + dsw;
                while (acc >= SWIM_CM) {
                    acc -= SWIM_CM;
                    SkillHelper.addXp(sp, SkillType.SWIM, 1);
                    if (s.isMaxed(SkillType.SWIM)) {
                        acc = 0;
                        break;
                    }
                }
                s.setSwimAcc(acc);
            }
        }
    }

    // ===== 成長率回復1: 料理を食べる =====
    // 食材の種類数だけ、解放済み全スキルの成長率を回復 (上限300はsetGrowthでクランプ)。

    @SubscribeEvent
    public static void onFoodEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        ItemStack eaten = event.getItem();
        // 1.21: 食料判定は DataComponents.FOOD (旧 getFoodProperties は撤去)。
        if (!eaten.has(DataComponents.FOOD)) {
            return;
        }
        int kinds = countIngredientKinds(sp.serverLevel(), eaten);
        if (kinds <= 0) {
            return;
        }
        SkillStorage s = SkillHelper.get(sp);
        boolean changed = false;
        for (SkillType t : SkillType.values()) {
            if (s.isAcquired(t) && s.getGrowth(t) < SkillStorage.GROWTH_MAX) {
                s.setGrowth(t, s.getGrowth(t) + kinds);
                changed = true;
            }
        }
        if (changed) {
            SkillHelper.sync(sp);
        }
    }

    // ===== 成長率回復2: 睡眠 =====
    // 起床時に+25。ただし100が上限 (99→100、100以上は不変)。布団の睡眠も対象。

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        SkillStorage s = SkillHelper.get(sp);
        boolean changed = false;
        for (SkillType t : SkillType.values()) {
            int g = s.getGrowth(t);
            if (s.isAcquired(t) && g < SkillStorage.GROWTH_START) {
                s.setGrowth(t, Math.min(SkillStorage.GROWTH_START, g + 25));
                changed = true;
            }
        }
        if (changed) {
            SkillHelper.sync(sp);
        }
    }

    /**
     * 料理の食材種類数。作業台レシピの最大値、なければ1 (素材自体)。
     * 1.21: worktree の CookingManager に countDistinctIngredients が無いため
     * クラフトレシピ走査のみ。囲炉裏レシピ由来の種類数は料理chore側で拡張可。
     */
    private static int countIngredientKinds(ServerLevel level, ItemStack food) {
        // 潜在回復1: 特殊回復食 (レシピの異種数に代えて固定値)
        if (food.is(Items.GOLDEN_APPLE)
                || food.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return 8;
        }
        if (food.is(Items.GOLDEN_CARROT)) {
            return 5;
        }
        int best = 0;
        var access = level.registryAccess();
        for (var holder : level.getRecipeManager().getRecipes()) {
            var r = holder.value();
            if (r.getType() != RecipeType.CRAFTING) {
                continue;
            }
            if (!ItemStack.isSameItem(r.getResultItem(access), food)) {
                continue;
            }
            Set<Item> kinds = new HashSet<>();
            for (Ingredient ing : r.getIngredients()) {
                if (ing.isEmpty()) {
                    continue;
                }
                ItemStack[] opts = ing.getItems();
                if (opts.length > 0 && !opts[0].isEmpty()) {
                    kinds.add(opts[0].getItem());
                }
            }
            best = Math.max(best, kinds.size());
        }
        return Math.max(1, best);
    }
}
