package ruby.bamboo.transform;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.LogicalSide;
import ruby.bamboo.BambooMod;

/**
 * 変身中の Tick 処理と能力イベント (サーバー側)。
 * 敵対スキャンは 40tick 毎・UUID 分散で負荷を抑える。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TransformTickHandler {

    private static final UUID DYN_TURTLE = UUID.fromString("c3b8c7a1-1b2e-4d5f-9a1b-2c3d4e5f0011");
    private static final UUID DYN_GUARD = UUID.fromString("c3b8c7a1-1b2e-4d5f-9a1b-2c3d4e5f0012");
    private static final UUID DYN_DOLPHIN = UUID.fromString("c3b8c7a1-1b2e-4d5f-9a1b-2c3d4e5f0013");

    private TransformTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        if (id.isEmpty()) {
            return;
        }
        TransformHelper.applyAttributes(player);
        ServerLevel level = player.serverLevel();
        long time = level.getGameTime();
        TransformHelper.get(player).ifPresent(s -> tickPlayer(player, level, id, s, time));
        if (time % 20 == 0) {
            tickSlow(player, level, id, time);
        }
        if ((time + (player.getUUID().hashCode() & 31)) % 40 == 0) {
            hostilityScan(player, level, id);
        }
    }

    private static void tickPlayer(ServerPlayer player, ServerLevel level, String id, TransformStorage s, long time) {
        Vec3 delta = player.getDeltaMovement();
        // 疑似滑空 (エリトラなし)
        if (TransformRegistry.GLIDE.contains(id) && !player.onGround() && !player.isInWater()
                && !player.isInLava() && !player.isFallFlying() && delta.y < -0.2D) {
            player.setDeltaMovement(delta.x * 0.98D, delta.y * 0.6D, delta.z * 0.98D);
            player.fallDistance *= 0.7F;
        }
        // クモの登攀 (壁押し+前進で上昇)
        if ((id.equals("minecraft:spider") || id.equals("minecraft:cave_spider"))
                && player.horizontalCollision && player.zza > 0.1F && !player.isInWater()) {
            player.setDeltaMovement(delta.x, 0.25D, delta.z);
            player.fallDistance = 0F;
        }
        // ホグリンは沈む
        if (id.equals("minecraft:hoglin") && player.isInWater()) {
            player.setDeltaMovement(delta.x, delta.y - 0.05D, delta.z);
        }
        // ストライダーの溶岩浮力 (近似)
        if (id.equals("minecraft:strider") && player.isInLava()) {
            if (delta.y < 0.12D) {
                player.setDeltaMovement(delta.x, 0.12D, delta.z);
            }
            player.fallDistance = 0F;
        }
        // クリーパー自爆 fuse
        if (id.equals("minecraft:creeper") && s.getCreeperFuse() >= 0) {
            int fuse = s.getCreeperFuse() - 1;
            s.setCreeperFuse(fuse);
            if (fuse <= 0) {
                s.setCreeperFuse(-1);
                s.setCreeperCd(time + 100L);
                Vec3 p = player.position();
                level.explode(null, p.x, p.y, p.z, 3.0F, false, Level.ExplosionInteraction.NONE);
                player.hurt(player.damageSources().magic(), 2.0F);
            }
        }
        // ニワトリの産卵
        if (id.equals("minecraft:chicken")) {
            int t = s.getEggTimer() + 1;
            if (t >= 6000) {
                t = 0;
                player.spawnAtLocation(net.minecraft.world.item.Items.EGG);
            }
            s.setEggTimer(t);
        }
        // 腐肉デバフの取消 (腐肉OK種)
        if (TransformRegistry.SUN_BURN.contains(id) || id.equals("minecraft:skeleton")
                || id.equals("minecraft:stray") || id.equals("minecraft:wither_skeleton")) {
            if (player.hasEffect(MobEffects.HUNGER) && "minecraft:rotten_flesh".equals(s.getLastFood())
                    && time - s.getLastFoodTick() < 20L) {
                player.removeEffect(MobEffects.HUNGER);
            }
        }
        // 水棲の水中バフ
        if (TransformRegistry.AQUATIC.contains(id) && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false, true));
        }
        // カメの水中呼吸
        if (id.equals("minecraft:turtle") && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, false, false, true));
        }
        // ウォーデンの盲目
        if (id.equals("minecraft:warden") && time % 200 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 260, 0, false, false, true));
        }
        // ゾンビ系の夜間回復
        if (TransformRegistry.SUN_BURN.contains(id) && !level.isDay()
                && player.getFoodData().getFoodLevel() > 6 && player.getHealth() < player.getMaxHealth()
                && time % 60 == 0) {
            player.heal(1.0F);
        }
        // ゴーレム系の空腹軽減 (近似: 定期的に1回復)
        if ((id.equals("minecraft:iron_golem") || id.equals("minecraft:snow_golem"))
                && time % 100 == 0 && player.getFoodData().getFoodLevel() < 20
                && player.getFoodData().getFoodLevel() > 0) {
            player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() + 1);
        }
        // 動的速度 (地上/水中で切替)
        dynamicSpeed(player, id);
    }

    private static void dynamicSpeed(ServerPlayer player, String id) {
        boolean inWater = player.isInWater();
        if (id.equals("minecraft:turtle")) {
            setDyn(player, DYN_TURTLE, "turtle_land", inWater ? 0D : -0.5D);
        } else {
            setDyn(player, DYN_TURTLE, "turtle_land", 0D);
        }
        if (id.equals("minecraft:guardian") || id.equals("minecraft:elder_guardian")
                || id.equals("minecraft:squid") || id.equals("minecraft:glow_squid")) {
            setDyn(player, DYN_GUARD, "guard_land", inWater ? 0D : -0.2D);
        } else {
            setDyn(player, DYN_GUARD, "guard_land", 0D);
        }
        if (id.equals("minecraft:dolphin")) {
            setDyn(player, DYN_DOLPHIN, "dolphin_water", inWater ? 0.2D : 0D);
        } else {
            setDyn(player, DYN_DOLPHIN, "dolphin_water", 0D);
        }
    }

    private static void setDyn(ServerPlayer player, UUID uuid, String name, double amount) {
        AttributeInstance inst = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) {
            return;
        }
        boolean has = inst.getModifier(uuid) != null;
        if (amount == 0D) {
            if (has) {
                inst.removeModifier(uuid);
            }
            return;
        }
        if (!has) {
            inst.addTransientModifier(
                    new AttributeModifier(uuid, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void tickSlow(ServerPlayer player, ServerLevel level, String id, long time) {
        // 日光炎上 (ヘルメットで防げる)
        if (TransformRegistry.SUN_BURN.contains(id) && level.isDay()
                && level.canSeeSky(player.blockPosition()) && !player.isInWater()) {
            var helm = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!helm.isEmpty()) {
                helm.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.HEAD));
            } else {
                player.setSecondsOnFire(8);
            }
        }
        // 冠水ダメージ
        if (TransformRegistry.WATER_HURT.contains(id)
                && player.isEyeInFluid(FluidTags.WATER)) {
            player.hurt(player.damageSources().magic(), 1.0F);
        }
        // 粉雪 (ストライダー)
        if (id.equals("minecraft:strider") && player.isInPowderSnow) {
            player.hurt(player.damageSources().magic(), 1.0F);
        }
        // 空腹倍率 (近似: 定期 exhaust)
        double hunger = TransformRegistry.HUNGER.getOrDefault(id, 1.0D);
        if (hunger > 1.0D) {
            player.getFoodData().addExhaustion((float) ((hunger - 1.0D) * 1.0D));
        }
        // ピグリン系の採掘バフは BreakSpeed 側、攻撃バフは Hurt 側
    }

    /** 低負荷の敵対スキャン (40tick毎・分散)。 */
    private static void hostilityScan(ServerPlayer player, ServerLevel level, String id) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(16D);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box);
        boolean livestock = id.equals("minecraft:pig") || id.equals("minecraft:cow")
                || id.equals("minecraft:sheep") || id.equals("minecraft:rabbit")
                || id.equals("minecraft:chicken");
        boolean illager = id.equals("minecraft:evoker") || id.equals("minecraft:vindicator")
                || id.equals("minecraft:pillager") || id.equals("minecraft:silverfish");
        boolean endermite = id.equals("minecraft:endermite");
        for (Mob mob : mobs) {
            if (mob instanceof Wolf wolf && livestock && wolf.getTarget() == null) {
                wolf.setTarget(player);
            } else if (mob instanceof IronGolem golem && illager && golem.getTarget() == null) {
                golem.setTarget(player);
            } else if (mob instanceof EnderMan ender && endermite && ender.getTarget() == null) {
                ender.setTarget(player);
            }
        }
        // エヴォーカーの自動噛みつき (5秒毎)
        if (id.equals("minecraft:evoker")) {
            TransformHelper.get(player).ifPresent(s -> {
                if (timeOf(level) < s.getBiteCd()) {
                    return;
                }
                Mob victim = null;
                double best = Double.MAX_VALUE;
                for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6D))) {
                    if (mob.getTarget() == player || mob instanceof net.minecraft.world.entity.monster.Monster) {
                        double d = mob.distanceToSqr(player);
                        if (d < best) {
                            best = d;
                            victim = mob;
                        }
                    }
                }
                if (victim != null) {
                    s.setBiteCd(timeOf(level) + 100L);
                    victim.hurt(player.damageSources().mobAttack(player), 4.0F);
                }
            });
        }
    }

    private static long timeOf(ServerLevel level) {
        return level.getGameTime();
    }

    // ===== ダメージ系 =====

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        if (id.isEmpty()) {
            return;
        }
        float amount = event.getAmount();
        // 被ダメ倍率・軽減
        double taken = TransformRegistry.DAMAGE_TAKEN.getOrDefault(id, 1.0D);
        if (taken != 1.0D) {
            amount = (float) (amount * taken);
        }
        double cut = TransformRegistry.DAMAGE_CUT.getOrDefault(id, 0.0D);
        if (cut != 0.0D) {
            amount = (float) (amount * (1.0D - cut));
        }
        // 火・溶岩の無効
        if (TransformRegistry.FIRE_IMMUNE.contains(id)
                && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
            return;
        }
        // ガストの跳ね返り自傷は大ダメージ
        if (id.equals("minecraft:ghast") && event.getSource().getEntity() == player) {
            amount = amount * 3.0F;
        }
        event.setAmount(amount);

        // エンダーマンの被弾テレポート
        if (id.equals("minecraft:enderman")) {
            TransformHelper.get(player).ifPresent(s -> {
                long now = player.serverLevel().getGameTime();
                if (now >= s.getEnderCd()) {
                    s.setEnderCd(now + 60L);
                    player.randomTeleport(player.getX() + (player.getRandom().nextDouble() - 0.5D) * 16D,
                            player.getY() + player.getRandom().nextInt(8) - 4, player.getZ()
                                    + (player.getRandom().nextDouble() - 0.5D) * 16D,
                            true);
                }
            });
        }
        // クリーパーの自爆点火
        if (id.equals("minecraft:creeper")
                && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            TransformHelper.get(player).ifPresent(s -> {
                long now = player.serverLevel().getGameTime();
                if (s.getCreeperFuse() < 0 && now >= s.getCreeperCd()) {
                    s.setCreeperFuse(30);
                }
            });
        }
        // スライムの分裂
        if ((id.equals("minecraft:slime") || id.equals("minecraft:magma_cube"))
                && event.getSource().getEntity() instanceof LivingEntity attacker) {
            TransformHelper.get(player).ifPresent(s -> {
                long now = player.serverLevel().getGameTime();
                if (now >= s.getSplitCd() && player.getFoodData().getFoodLevel() > 6) {
                    s.setSplitCd(now + 200L);
                    int food = player.getFoodData().getFoodLevel();
                    player.getFoodData().setFoodLevel(Math.max(0, food / 2));
                    ServerLevel level = player.serverLevel();
                    Entity e = (id.equals("minecraft:slime") ? net.minecraft.world.entity.EntityType.SLIME
                            : net.minecraft.world.entity.EntityType.MAGMA_CUBE).create(level);
                    if (e instanceof Slime slime) {
                        slime.setSize(1, true);
                        slime.moveTo(player.getX(), player.getY(), player.getZ(), 0F, 0F);
                        slime.setTarget(attacker instanceof Mob mob ? mob : null);
                        level.addFreshEntity(slime);
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        // 攻撃側のバフ (ピグリン金比例・金は Hurt では取れないため Damage で処理)
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            String id = TransformHelper.resolvedId(player);
            if (id.isEmpty()) {
                return;
            }
            if (TransformRegistry.GOLD_SCALE.contains(id)) {
                int gold = countGoldArmor(player);
                if (gold > 0) {
                    double rate = id.equals("minecraft:piglin_brute") ? 0.07D : 0.05D;
                    event.setAmount((float) (event.getAmount() * (1.0D + rate * gold)));
                }
            }
        }
    }

    private static int countGoldArmor(Player player) {
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            var stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor
                    && armor.getMaterial() == net.minecraft.world.item.ArmorMaterials.GOLD) {
                n++;
            }
        }
        return n;
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        // ゴーレム系は自然・ポーション回復なし (金属・雪食いのみ)
        if (id.equals("minecraft:iron_golem") || id.equals("minecraft:snow_golem")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof ServerPlayer player)) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        if (id.isEmpty()) {
            return;
        }
        // 同種は中立 (近似: 常に取消)
        try {
            var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                    .getKey(event.getEntity().getType());
            if (key != null && key.toString().equals(id)) {
                event.setCanceled(true);
            }
            // ゾンビ系はゾンビに、ファントムはネコ系に襲われない
            if (event.getEntity() instanceof Zombie
                    && (id.equals("minecraft:zombie") || id.equals("minecraft:husk")
                            || id.equals("minecraft:drowned") || id.equals("minecraft:zombie_villager")
                            || id.equals("minecraft:zombified_piglin"))) {
                event.setCanceled(true);
            }
            if (event.getEntity().getType() == net.minecraft.world.entity.EntityType.PHANTOM
                    && (id.equals("minecraft:cat") || id.equals("minecraft:ocelot"))) {
                event.setCanceled(true);
            }
            // ゾンビは同族以外のアンデッド変身者も襲う (取消しない = 仕様通り)
        } catch (Exception ignored) {
        }
    }

    // ===== 飲食制限 =====

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        if (id.isEmpty()) {
            return;
        }
        var stack = event.getItem();
        if (stack.isEmpty()) {
            return;
        }
        var food = stack.getFoodProperties(player);
        if (food == null) {
            return;
        }
        // ハチは花のみ
        if (TransformRegistry.FLOWER_ONLY.contains(id)) {
            if (!stack.is(net.minecraft.tags.ItemTags.FLOWERS)) {
                event.setCanceled(true);
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1));
            }
            return;
        }
        if (TransformRegistry.CARNIVORE.contains(id) && !food.isMeat()) {
            event.setCanceled(true);
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1));
        } else if (TransformRegistry.HERBIVORE.contains(id) && food.isMeat()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        if (id.isEmpty()) {
            return;
        }
        var stack = event.getItem();
        // 腐肉の記録
        if (stack.is(net.minecraft.world.item.Items.ROTTEN_FLESH)) {
            long now = player.serverLevel().getGameTime();
            TransformHelper.get(player).ifPresent(s -> s.setLastFood("minecraft:rotten_flesh", now));
        }
        // オウムのクッキー即死
        if (id.equals("minecraft:parrot") && stack.is(net.minecraft.world.item.Items.COOKIE)) {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }
        // ヒツジの毛刈り回復
        if (id.equals("minecraft:sheep") && stack.getFoodProperties(player) != null) {
            TransformHelper.get(player).ifPresent(s -> {
                if (s.isSheared() && player.getRandom().nextInt(3) == 0) {
                    s.setSheared(false);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        if (id.isEmpty()) {
            return;
        }
        var inst = event.getEffectInstance();
        if (inst == null) {
            return;
        }
        if (TransformRegistry.POISON_IMMUNE.contains(id)
                && inst.getEffect() == MobEffects.POISON) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    private static final java.util.Set<UUID> WITCH_GUARD = java.util.Collections
            .newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!TransformHelper.resolvedId(player).equals("minecraft:witch")) {
            return;
        }
        UUID uuid = player.getUUID();
        if (!WITCH_GUARD.add(uuid)) {
            return;
        }
        try {
            var inst = event.getEffectInstance();
            if (inst == null || inst.isInfiniteDuration()) {
                return;
            }
            int longer = (int) Math.min(Integer.MAX_VALUE, (long) inst.getDuration() * 3 / 2);
            player.removeEffect(inst.getEffect());
            player.addEffect(new MobEffectInstance(inst.getEffect(), longer, inst.getAmplifier(),
                    inst.isAmbient(), inst.isVisible(), inst.showIcon()));
        } finally {
            WITCH_GUARD.remove(uuid);
        }
    }

    // ===== 矢の消費なし・火の玉化 =====

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity e = event.getEntity();
        if (!(e instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        if (id.isEmpty()) {
            return;
        }
        // ガスト: 矢を火の玉に置換 (ブロック破壊なしは Detonate 側で剥奪)
        if (id.equals("minecraft:ghast")) {
            event.setCanceled(true);
            ServerLevel level = player.serverLevel();
            Vec3 look = player.getLookAngle();
            var fireball = new net.minecraft.world.entity.projectile.SmallFireball(level, player,
                    look.x * 1.5D, look.y * 1.5D, look.z * 1.5D);
            fireball.moveTo(player.getX() + look.x, player.getEyeY(), player.getZ() + look.z, 0F, 0F);
            fireball.getPersistentData().putBoolean("bamboo_ghast", true);
            level.addFreshEntity(fireball);
            return;
        }
        // スケルトン系・ピリジャー: 矢を返却 (近似: 通常矢1本)
        if (TransformRegistry.BOW_FREE.contains(id) || TransformRegistry.XBOW_FREE.contains(id)) {
            if (!player.getInventory().add(new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.ARROW))) {
                player.drop(new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.ARROW), false);
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Explosion explosion = event.getExplosion();
        if (explosion == null) {
            return;
        }
        Entity exploder = explosion.getExploder();
        // ガスト変身者の火の玉はブロックを壊さない
        if (exploder != null && exploder.getPersistentData().getBoolean("bamboo_ghast")) {
            event.getAffectedBlocks().clear();
            return;
        }
        // クリーパー変身者の自爆は NONE 指定済みのため何もしない
    }

    // ===== 取引・採掘・右クリック =====

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        if (id.isEmpty()) {
            return;
        }
        var offer = event.getMerchantOffer();
        if (offer == null || player.level().isClientSide()) {
            return;
        }
        int emeralds = 0;
        if (offer.getCostA().is(net.minecraft.world.item.Items.EMERALD)) {
            emeralds = offer.getCostA().getCount();
        }
        if (emeralds <= 0) {
            return;
        }
        // 村人: 1割還元 / イリジャー系: 倍額徴収 (近似)
        if (id.equals("minecraft:villager")) {
            int back = Math.max(1, emeralds / 10);
            player.getInventory().add(
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, back));
        } else if (id.equals("minecraft:evoker") || id.equals("minecraft:vindicator")
                || id.equals("minecraft:pillager")) {
            int extra = emeralds;
            int has = player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD);
            int take = Math.min(extra, has);
            if (take > 0) {
                player.getInventory().clearOrCountMatchingItems(
                        stack -> stack.is(net.minecraft.world.item.Items.EMERALD), take,
                        player.inventoryMenu.getCraftSlots());
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        if (id.isEmpty()) {
            return;
        }
        if (TransformRegistry.GOLD_SCALE.contains(id)) {
            int gold = countGoldArmor(player);
            if (gold > 0) {
                double rate = id.equals("minecraft:piglin_brute") ? 0.07D : 0.05D;
                event.setNewSpeed((float) (event.getNewSpeed() * (1.0D + rate * gold)));
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        String id = TransformHelper.resolvedId(player);
        if (id.isEmpty()) {
            return;
        }
        var stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        // ウシ・ヤギ: 素振りバケツでミルク
        if ((id.equals("minecraft:cow") || id.equals("minecraft:goat"))
                && stack.is(net.minecraft.world.item.Items.BUCKET)) {
            if (!creative) {
                stack.shrink(1);
            }
            var milk = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MILK_BUCKET);
            if (!player.getInventory().add(milk)) {
                player.drop(milk, false);
            }
            event.setCanceled(true);
            return;
        }
        // ヒツジ: ハサミで自毛刈り
        if (id.equals("minecraft:sheep")
                && stack.is(net.minecraft.world.item.Items.SHEARS)) {
            TransformHelper.get(player).ifPresent(s -> {
                if (!s.isSheared()) {
                    s.setSheared(true);
                    stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(event.getHand()));
                    var wool = new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.WHITE_WOOL);
                    if (!player.getInventory().add(wool)) {
                        player.drop(wool, false);
                    }
                }
            });
            event.setCanceled(true);
            return;
        }
        // ゴーレム系: 金属・雪食い回復
        if (id.equals("minecraft:iron_golem") && player.getHealth() < player.getMaxHealth()
                && (stack.is(net.minecraft.world.item.Items.IRON_INGOT)
                        || stack.is(net.minecraft.world.item.Items.COPPER_INGOT)
                        || stack.is(net.minecraft.world.item.Items.GOLD_INGOT))) {
            if (!creative) {
                stack.shrink(1);
            }
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 4.0F));
            event.setCanceled(true);
            return;
        }
        if (id.equals("minecraft:snow_golem") && player.getHealth() < player.getMaxHealth()
                && stack.is(net.minecraft.world.item.Items.SNOW_BLOCK)) {
            if (!creative) {
                stack.shrink(1);
            }
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 4.0F));
            event.setCanceled(true);
            return;
        }
        // ハチ: 花を食べる (+空ビンがあれば10%で蜂蜜)
        if (id.equals("minecraft:bee") && stack.is(net.minecraft.tags.ItemTags.FLOWERS)) {
            if (!creative) {
                stack.shrink(1);
            }
            player.getFoodData().eat(2, 0.5F);
            if (player.getRandom().nextInt(10) == 0) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    var inv = player.getInventory().getItem(i);
                    if (inv.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
                        inv.shrink(1);
                        var honey = new net.minecraft.world.item.ItemStack(
                                net.minecraft.world.item.Items.HONEY_BOTTLE);
                        if (!player.getInventory().add(honey)) {
                            player.drop(honey, false);
                        }
                        break;
                    }
                }
            }
            event.setCanceled(true);
        }
    }

}
