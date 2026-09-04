package ruby.bamboo.skill;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.crafting.cooking.CookingManager;

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

    // ===== 速度・水泳 (バニラ統計値の差分積算) =====
    // walk/swim系カスタム統計のみを見るため、飛行・騎乗・ボートの混入なし。
    // 基準値は Cap 保持 (ログアウト掃除不要・メモリリークなし)。

    /** 速度: 5000cm(50BL)で1xp、水泳: 2000cm(20BL)で1xp。 */
    private static final long SPEED_CM = 5000L;
    private static final long SWIM_CM = 2000L;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (!(event.player instanceof ServerPlayer sp) || event.player.isPassenger()) {
            return;
        }
        var stats = sp.getStats();
        int walk = stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.WALK_ON_WATER_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.WALK_UNDER_WATER_ONE_CM));
        int swim = stats.getValue(Stats.CUSTOM.get(Stats.SWIM_ONE_CM));
        SkillHelper.get(sp).ifPresent(s -> {
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
        });
    }

    // ===== 成長率回復1: 料理を食べる =====
    // 食材の種類数だけ、解放済み全スキルの成長率を回復 (上限300はsetGrowthでクランプ)。

    @SubscribeEvent
    public static void onFoodEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        ItemStack eaten = event.getItem();
        if (eaten.getFoodProperties(sp) == null) {
            return;
        }
        int kinds = countIngredientKinds(sp.serverLevel(), eaten);
        if (kinds <= 0) {
            return;
        }
        boolean[] changed = { false };
        SkillHelper.get(sp).ifPresent(s -> {
            for (SkillType t : SkillType.values()) {
                if (s.isAcquired(t) && s.getGrowth(t) < SkillStorage.GROWTH_MAX) {
                    s.setGrowth(t, s.getGrowth(t) + kinds);
                    changed[0] = true;
                }
            }
        });
        if (changed[0]) {
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
        boolean[] changed = { false };
        SkillHelper.get(sp).ifPresent(s -> {
            for (SkillType t : SkillType.values()) {
                int g = s.getGrowth(t);
                if (s.isAcquired(t) && g < SkillStorage.GROWTH_START) {
                    s.setGrowth(t, Math.min(SkillStorage.GROWTH_START, g + 25));
                    changed[0] = true;
                }
            }
        });
        if (changed[0]) {
            SkillHelper.sync(sp);
        }
    }

    /** 料理の食材種類数。囲炉裏・作業台レシピの最大値、なければ1 (素材自体)。 */
    private static int countIngredientKinds(net.minecraft.server.level.ServerLevel level, ItemStack food) {
        // 潜在回復1: 特殊回復食 (レシピの異種数に代えて固定値)
        if (food.is(net.minecraft.world.item.Items.GOLDEN_APPLE)
                || food.is(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE)) {
            return 8;
        }
        if (food.is(net.minecraft.world.item.Items.GOLDEN_CARROT)) {
            return 5;
        }
        int best = CookingManager.countDistinctIngredients(food);
        var access = level.registryAccess();
        for (Recipe<?> r : level.getRecipeManager().getRecipes()) {
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
