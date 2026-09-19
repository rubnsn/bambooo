package ruby.bamboo.handler;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import ruby.bamboo.BambooMod;
import ruby.bamboo.capsule.CapsuleRules;
import ruby.bamboo.item.CapsuleBallItem;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.CapsuleStatePacket;

/**
 * カプセルボールのサーバー側ライフサイクル管理 (docs §4-§6)。
 * AI再適用 / 死亡時ボール破壊 / 格納中回復 / ドロップ回復 / 存在チェック / 状態再同期。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CapsuleBallHandler {

    private CapsuleBallHandler() {
    }

    /** ドロップ回復の対象アイテム (datapackタグ。代表的なドロップ+汎用食料) */
    public static final TagKey<Item> HEAL_ITEMS = ItemTags
            .create(ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "capsule_heal"));

    // ===== AI再適用 (バニラMobはロード時にゴールを再構築するため) =====

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!CapsuleRules.isReleased(mob)) return;
        UUID owner = CapsuleRules.getReleasedOwner(mob);
        if (owner == null) return;
        mob.setPersistenceRequired();
        CapsuleRules.applyAi(mob, owner);
    }

    // ===== 個体死亡 → 対応ボール破壊 (§4) =====

    @SubscribeEvent
    public static void onReleasedDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!CapsuleRules.isReleased(entity)) return;
        CapsuleRules.broadcastState(entity, false, 0.0F);
        UUID owner = CapsuleRules.getReleasedOwner(entity);
        if (owner == null || entity.getServer() == null) return;
        ServerPlayer player = entity.getServer().getPlayerList().getPlayer(owner);
        if (player == null) return;
        if (CapsuleRules.destroyBoundBall(player, entity.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("message.bamboomod.capsule_ball_broken",
                            entity.getType().getDescription().getString()),
                    false);
        }
    }

    // ===== 存在チェック: 召喚中にボールが失われたら個体死亡 (§9-4) =====

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != Phase.END || event.getServer() == null) return;
        if (event.getServer().getTickCount() % 200 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (entity.isRemoved() || !CapsuleRules.isReleased(entity)) continue;
                UUID owner = CapsuleRules.getReleasedOwner(entity);
                if (owner == null) continue;
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(owner);
                // 所有者オフラインは判定保留 (ログアウトで殺さない)
                if (player == null) continue;
                if (CapsuleRules.hasBoundBall(player, entity.getUUID())) continue;
                living.kill();
                player.displayClientMessage(
                        Component.translatable("message.bamboomod.capsule_ball_lost",
                                entity.getType().getDescription().getString()),
                        false);
            }
        }
    }

    // ===== 格納中回復: 200tickごとにHealth+1 (§6) =====

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        if (event.phase != Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide() || !player.isAlive()) return;
        if (player.tickCount % 200 != 0) return;
        for (ItemStack stack : player.getInventory().items) {
            healStored(player, stack);
        }
        healStored(player, player.getOffhandItem());
    }

    private static void healStored(Player player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof CapsuleBallItem)) return;
        if (!CapsuleRules.isCaptured(stack)) return;
        var tag = stack.getOrCreateTag();
        if (!tag.contains(CapsuleRules.TAG_ENTITY) || !tag.contains(CapsuleRules.TAG_MAX_HP)) return;
        int timer = tag.getInt(CapsuleRules.TAG_HEAL_TIMER) + 200;
        if (timer < 200) {
            tag.putInt(CapsuleRules.TAG_HEAL_TIMER, timer);
            return;
        }
        timer -= 200;
        tag.putInt(CapsuleRules.TAG_HEAL_TIMER, timer);
        var data = tag.getCompound(CapsuleRules.TAG_ENTITY);
        float max = (float) tag.getDouble(CapsuleRules.TAG_MAX_HP);
        float hp = data.getFloat("Health");
        if (hp < max) {
            data.putFloat("Health", Math.min(max, hp + 1.0F));
            tag.put(CapsuleRules.TAG_ENTITY, data);
            // tooltipのHP表示を更新するため同期する
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.inventoryMenu.broadcastChanges();
            }
        }
    }

    // ===== 格納: ボールで解放個体に右クリック (Forgeフック。Mob側処理より先に確定) =====

    @SubscribeEvent
    public static void onStoreInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !(held.getItem() instanceof CapsuleBallItem)) return;
        UUID bound = CapsuleRules.getBoundId(held);
        if (bound == null || !bound.equals(target.getUUID())) return;
        if (!target.isAlive() || target.isRemoved()) return;
        Player player = event.getEntity();
        target.ejectPassengers();
        if (!CapsuleRules.storeInto(target, held)) return;
        target.discard();
        // 破棄直後個体の追跡配信は失敗しうるため握りつぶす
        try {
            CapsuleRules.broadcastState(target, false, 0.0F);
        } catch (Exception e) {
            BambooMod.LOGGER.debug("CapsuleBall store broadcast skipped", e);
        }
        // NBTのみの書き換えは自動同期されないため明示的に送る
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
        // 格納直後の押しっぱなし右クリックで即投擲 (即召喚) されないよう長めにロック
        player.getCooldowns().addCooldown(held.getItem(), 25);
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.7F, 1.2F);
        player.displayClientMessage(
                Component.translatable("message.bamboomod.capsule_ball_stored",
                        target.getType().getDescription().getString()),
                true);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    // ===== ドロップ回復: 解放個体への右クリック (§6) =====

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof LivingEntity living)) return;
        if (!CapsuleRules.isReleased(living)) return;
        if (!living.isAlive()) return;
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !held.is(HEAL_ITEMS)) return;
        if (living.getHealth() >= living.getMaxHealth()) return;
        living.setHealth(Math.min(living.getMaxHealth(), living.getHealth() + 2.0F));
        Player player = event.getEntity();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.level().playSound(null, living.getX(), living.getY(), living.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.7F, 1.0F);
        event.setCanceled(true);
    }

    // ===== 状態再同期 (ログイン・次元移動時に解放中を全送) =====

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        resyncTo(player);
    }

    @SubscribeEvent
    public static void onPlayerDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        resyncTo(player);
    }

    private static void resyncTo(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity)) continue;
                if (!CapsuleRules.isReleased(entity)) continue;
                BambooNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new CapsuleStatePacket(entity.getId(), true, entity instanceof LivingEntity living
                                ? living.getMaxHealth()
                                : 20.0F));
            }
        }
    }
}
