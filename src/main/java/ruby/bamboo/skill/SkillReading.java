package ruby.bamboo.skill;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ruby.bamboo.BambooMod;
import ruby.bamboo.item.SkillBookItem;
import ruby.bamboo.network.SkillReadClosePacket;
import ruby.bamboo.network.SkillReadOpenPacket;

/**
 * 読書セッション管理 (サーバー権威、feat-spec-skill §4)。
 * 100tick完走で成功率ロール。移動・被弾・持替・閉屏で中断 (耐久消費なし・クールなし)。
 *
 * <p>1.21.1 NeoForge: 旧 {@code LivingHurtEvent} は {@code LivingDamageEvent.Pre} に、
 * 旧 Tick は {@code PlayerTickEvent.Post} に、送信は
 * {@code PacketDistributor.sendToPlayer} に置換。
 * ステータス本の付与は {@code BambooItems} 未配線でも組めるようレジストリ参照
 * (親が {@code STATUS_BOOK} を登録したら解決される)。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SkillReading {

    public static final int DURATION_TICKS = 100;
    public static final int FAIL_COOLDOWN_TICKS = 100;

    private record Session(SkillType type, InteractionHand hand, double x, double y, double z, long start) {
    }

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    private SkillReading() {
    }

    public static void start(ServerPlayer sp, SkillType type, InteractionHand hand) {
        if (ACTIVE.containsKey(sp.getUUID())) {
            return;
        }
        long now = sp.serverLevel().getGameTime();
        Long until = COOLDOWN_UNTIL.get(sp.getUUID());
        if (until != null && now < until) {
            sp.displayClientMessage(Component.translatable("message.bamboomod.skill.read_cooldown"), true);
            return;
        }
        if (SkillHelper.get(sp).isMaxed(type)) {
            sp.displayClientMessage(Component.translatable("message.bamboomod.skill.read_maxed"), true);
            return;
        }
        ACTIVE.put(sp.getUUID(),
                new Session(type, hand, sp.getX(), sp.getY(), sp.getZ(), now));
        sp.displayClientMessage(Component.translatable("message.bamboomod.skill.read_start",
                Component.translatable("item.bamboomod.skill_book_" + type.getId())), false);
        PacketDistributor.sendToPlayer(sp, new SkillReadOpenPacket(type.getId()));
    }

    /** クライアント閉屏 (Esc等) による黙っての中断。 */
    public static void onClientCancel(ServerPlayer sp) {
        ACTIVE.remove(sp.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        Session s = ACTIVE.get(sp.getUUID());
        if (s == null) {
            return;
        }
        ItemStack cur = sp.getItemInHand(s.hand());
        if (!(cur.getItem() instanceof SkillBookItem b) || b.getSkill() != s.type()) {
            cancel(sp, Component.translatable("message.bamboomod.skill.read_cancel"));
            return;
        }
        double dx = sp.getX() - s.x();
        double dy = sp.getY() - s.y();
        double dz = sp.getZ() - s.z();
        if (dx * dx + dy * dy + dz * dz > 0.25D) {
            cancel(sp, Component.translatable("message.bamboomod.skill.read_cancel"));
            return;
        }
        if (sp.serverLevel().getGameTime() - s.start() >= DURATION_TICKS) {
            finish(sp, s);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (ACTIVE.containsKey(sp.getUUID())) {
            cancel(sp, Component.translatable("message.bamboomod.skill.read_cancel"));
        }
    }

    private static void cancel(ServerPlayer sp, Component msg) {
        ACTIVE.remove(sp.getUUID());
        PacketDistributor.sendToPlayer(sp, new SkillReadClosePacket());
        sp.displayClientMessage(msg, true);
    }

    private static void finish(ServerPlayer sp, Session s) {
        ACTIVE.remove(sp.getUUID());
        PacketDistributor.sendToPlayer(sp, new SkillReadClosePacket());
        ItemStack stack = sp.getItemInHand(s.hand());
        if (!(stack.getItem() instanceof SkillBookItem b) || b.getSkill() != s.type()) {
            return;
        }
        SkillStorage st = SkillHelper.get(sp);
        int lv = st.getLevel(s.type());
        double prob = 1.0D;
        if (lv > 0) {
            prob = 0.05D + 0.95D * st.getProgress(s.type());
        }
        int acquired = 0;
        for (SkillType t : SkillType.values()) {
            if (st.isAcquired(t)) {
                acquired++;
            }
        }
        if (sp.getRandom().nextDouble() < prob) {
            st.grantByBook(s.type());
            SkillEffects.applyPersistent(sp);
            SkillHelper.sync(sp);
            if (!sp.getAbilities().instabuild) {
                stack.shrink(1);
            }
            sp.displayClientMessage(Component.translatable("message.bamboomod.skill.levelup",
                    Component.translatable("skill.bamboomod." + s.type().getId() + ".name"),
                    SkillHelper.getLevel(sp, s.type())), false);
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
            if (acquired == 0) {
                Item statusBook = BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "status_book"));
                if (statusBook != null && statusBook != Items.AIR) {
                    ItemStack book = new ItemStack(statusBook);
                    if (!sp.getInventory().add(book)) {
                        sp.drop(book, false);
                    }
                }
            }
        } else {
            if (!sp.getAbilities().instabuild) {
                int d = stack.getDamageValue() + 1;
                if (d >= stack.getMaxDamage()) {
                    stack.shrink(1);
                    sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                            SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
                } else {
                    stack.setDamageValue(d);
                }
            }
            COOLDOWN_UNTIL.put(sp.getUUID(), sp.serverLevel().getGameTime() + FAIL_COOLDOWN_TICKS);
            demerit(sp, lv);
        }
    }

    // ===== 失敗デメリット =====

    private static void demerit(ServerPlayer sp, int lv) {
        RandomSource r = sp.getRandom();
        boolean peaceful = sp.serverLevel().getDifficulty() == Difficulty.PEACEFUL;
        double roll = r.nextDouble() * 100.0D;
        if (roll < 50.0D) {
            sp.displayClientMessage(Component.translatable("message.bamboomod.skill.demerit_confusing"), false);
            return;
        }
        if (peaceful) {
            double share = 50.0D / 3.0D;
            if (roll < 50.0D + share) {
                demeritHp(sp);
            } else if (roll < 50.0D + share * 2.0D) {
                demeritHunger(sp);
            } else {
                demeritWarp(sp);
            }
            return;
        }
        if (roll < 65.0D) {
            demeritHp(sp);
        } else if (roll < 80.0D) {
            demeritHunger(sp);
        } else if (roll < 90.0D) {
            demeritWarp(sp);
        } else {
            demeritSwarm(sp, lv);
        }
    }

    private static void demeritHp(ServerPlayer sp) {
        sp.displayClientMessage(Component.translatable("message.bamboomod.skill.demerit_hp"), false);
        if (sp.getHealth() > 1.0F) {
            sp.setHealth(1.0F);
        } else {
            sp.hurt(sp.damageSources().magic(), 1000.0F);
        }
    }

    private static void demeritHunger(ServerPlayer sp) {
        sp.displayClientMessage(Component.translatable("message.bamboomod.skill.demerit_hunger"), false);
        sp.getFoodData().setFoodLevel(0);
        sp.getFoodData().setSaturation(0.0F);
    }

    private static void demeritWarp(ServerPlayer sp) {
        sp.displayClientMessage(Component.translatable("message.bamboomod.skill.demerit_warp"), false);
        RandomSource r = sp.getRandom();
        for (int i = 0; i < 8; i++) {
            double nx = sp.getX() + (r.nextBoolean() ? 1 : -1) * (8.0D + r.nextDouble() * 8.0D);
            double nz = sp.getZ() + (r.nextBoolean() ? 1 : -1) * (8.0D + r.nextDouble() * 8.0D);
            double ny = sp.getY() + r.nextInt(5) - 2;
            if (sp.randomTeleport(nx, ny, nz, true)) {
                break;
            }
        }
    }

    private static final List<EntityType<?>> OVERWORLD_SWARM = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.WITCH, EntityType.SLIME);
    private static final List<EntityType<?>> NETHER_SWARM = List.of(
            EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.PIGLIN);
    private static final List<EntityType<?>> END_SWARM = List.of(
            EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.PHANTOM);

    private static void demeritSwarm(ServerPlayer sp, int lv) {
        sp.displayClientMessage(Component.translatable("message.bamboomod.skill.demerit_swarm"), false);
        ServerLevel level = sp.serverLevel();
        List<EntityType<?>> table;
        if (level.dimension() == Level.NETHER) {
            table = NETHER_SWARM;
        } else if (level.dimension() == Level.END) {
            table = END_SWARM;
        } else {
            table = OVERWORLD_SWARM;
        }
        RandomSource r = sp.getRandom();
        int count = Math.max(2, lv * 2);
        for (int i = 0; i < count; i++) {
            EntityType<?> type = table.get(r.nextInt(table.size()));
            double a = r.nextDouble() * Math.PI * 2.0D;
            double rad = 5.0D + r.nextDouble() * 3.0D;
            int x = Mth.floor(sp.getX() + Math.cos(a) * rad);
            int z = Mth.floor(sp.getZ() + Math.sin(a) * rad);
            int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();
            Entity e = type.create(level);
            if (!(e instanceof Mob mob)) {
                continue;
            }
            mob.moveTo(x + 0.5D, y, z + 0.5D, r.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
            level.addFreshEntity(mob);
        }
    }

    /** ログアウト時の掃除。 */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        ACTIVE.remove(p.getUUID());
        COOLDOWN_UNTIL.remove(p.getUUID());
    }
}
