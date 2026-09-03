package ruby.bamboo.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.fishing.FishSize;
import ruby.bamboo.core.fishing.FishingBiteHelper;
import ruby.bamboo.core.fishing.FishingEntry;
import ruby.bamboo.core.fishing.FishingManager;
import ruby.bamboo.item.FishingBaitItem;
import ruby.bamboo.item.LureItem;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.FishingCastResultPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;
import ruby.bamboo.entity.FishingBobberEntity;
import net.minecraft.world.phys.Vec3;

/**
 * サーバー側 釣り pending 管理 + キャスト抽選。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FishingHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, FishingPending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, FishingBobberEntity> BOBBERS = new ConcurrentHashMap<>();
    private static final int TTL_TICKS = 6000; // 5分
    public static final int ROD_POWER = 25;
    public static final int MIN_SUCCESS_TICKS = 40; // チート対策: キャストからこの Tick 未満の成功は拒否

    public static boolean hasPending(UUID id) {
        return PENDING.containsKey(id);
    }

    public static void clearPending(UUID id) {
        PENDING.remove(id);
    }

    /**
     * 餌を持っているか (オフハンド優先、無ければインベントリ)。
     */
    public static boolean hasBait(Player player) {
        return findBaitStack(player) != null;
    }

    private static class BaitInfo {
        ItemStack stack;
        int bitePower;
        boolean isLure;
        ResourceLocation itemId;
    }

    private static BaitInfo findBaitInfo(Player player) {
        // オフハンド優先
        ItemStack off = player.getOffhandItem();
        BaitInfo offInfo = toBaitInfo(off);
        if (offInfo != null) return offInfo;
        // インベントリ
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            BaitInfo info = toBaitInfo(s);
            if (info != null) return info;
        }
        return null;
    }

    private static BaitInfo toBaitInfo(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item instanceof FishingBaitItem bait) {
            BaitInfo info = new BaitInfo();
            info.stack = stack;
            info.bitePower = bait.getBitePower();
            info.isLure = false;
            info.itemId = ForgeRegistries.ITEMS.getKey(item);
            return info;
        } else if (item instanceof LureItem lure) {
            BaitInfo info = new BaitInfo();
            info.stack = stack;
            info.bitePower = lure.getBitePower();
            info.isLure = true;
            info.itemId = ForgeRegistries.ITEMS.getKey(item);
            return info;
        }
        return null;
    }

    private static ItemStack findBaitStack(Player player) {
        BaitInfo info = findBaitInfo(player);
        return info == null ? null : info.stack;
    }

    /**
     * 竿の releaseUsing から呼ばれるサーバー側キャスト処理 (旧方式)。
     */
    public static void handleCast(Player player, ItemStack rodStack, int chargeTicks) {
        if (!(player instanceof ServerPlayer sp)) return;
        int distance = FishingBiteHelper.computeDistance(chargeTicks);
        handleCastRequest(sp, distance);
    }

    /**
     * パワーゲージ決定後の新方式キャスト。距離は 4-15 でクライアントが決定。
     */
    public static void handleCastRequest(ServerPlayer sp, int distance) {
        ServerLevel level = sp.serverLevel();
        // 竿所持チェック
        boolean hasRod = false;
        for (InteractionHand hand : InteractionHand.values()) {
            if (sp.getItemInHand(hand).getItem() instanceof ruby.bamboo.item.BambooRodItem) {
                hasRod = true;
                break;
            }
        }
        if (!hasRod) {
            return;
        }
        // 既存ウキが残っていれば回収して上書き（hasPendingは不要、ミニゲームGUIで封じられる＆上書きで安全）
        // 新規キャストは旧pendingを上書きし、後から来た成功パケット以外は pendingなしで破棄される
        FishingBobberEntity old = BOBBERS.remove(sp.getUUID());
        if (old != null && !old.isRemoved()) old.discard();
        // 餌チェック
        BaitInfo baitInfo = findBaitInfo(sp);
        if (baitInfo == null) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bamboomod.fishing.no_bait").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }
        int baitPower = baitInfo.bitePower;
        int moonBonus = FishingBiteHelper.getMoonBonus(level);
        int rainBonus = FishingBiteHelper.getRainBonus(level);
        // 釣りスキル: バイト微強化 (+1/3Lv)。高品質魚は強化前提のバランス
        int fishLv = ruby.bamboo.skill.SkillHelper.getLevel(sp, ruby.bamboo.skill.SkillType.FISHING);
        int bitePower = baitPower + moonBonus + rainBonus + fishLv / 3;

        distance = Math.max(4, Math.min(15, distance));

        // 水着水チェック — 水でなければキャンセル（無消費）
        Vec3 bobPos = computeBobberPos(sp, distance);
        if (bobPos == null) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bamboomod.fishing.not_water").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.4F, 0.8F);
            return;
        }

        // 抽選
        BlockPos pos = sp.blockPosition();
        FishingManager.RollResult result = FishingManager.roll(sp, level, pos, bitePower, distance, sp.getRandom());
        if (result == null) {
            LOGGER.warn("Fishing roll failed for {}", sp.getName().getString());
            return;
        }
        long now = level.getGameTime();
        FishingPending pending = new FishingPending(
                result.entry, result.size, distance,
                result.startProgress, result.fishStamina, result.fishPower,
                result.movePattern,
                bitePower, now,
                baitInfo.itemId, baitInfo.isLure);
        // 上書き
        PENDING.put(sp.getUUID(), pending);

        // ウキ生成
        FishingBobberEntity bob = new FishingBobberEntity(level, sp, bobPos);
        bob.setOwner(sp);
        level.addFreshEntity(bob);
        BOBBERS.put(sp.getUUID(), bob);

        // S2C 送信
        // 釣りスキル: 開始進行度 +1/Lv (ロッド補助)、待ち -2tick/Lv (下限20)
        int startP = result.startProgress + fishLv;
        int waitMin = Math.max(20, 40 + sp.getRandom().nextInt(30) - 2 * fishLv);
        int waitMax = waitMin + 40 + sp.getRandom().nextInt(30);
        FishingCastResultPacket pkt = new FishingCastResultPacket(
                pending.entry.id,
                pending.entry.itemId,
                pending.entry.category.ordinal(),
                pending.size.ordinal(),
                startP,
                pending.fishStamina,
                pending.fishPower,
                pending.movePattern.ordinal(),
                distance,
                waitMin, waitMax);
        BambooNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), pkt);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.6F, 0.9F);
    }

    private static Vec3 computeBobberPos(ServerPlayer sp, int distance) {
        Level lvl = sp.serverLevel();
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle().normalize();
        if (look.lengthSqr() < 1e-6) look = new Vec3(0, 0, 1);
        double dist = Mth.clamp(distance, 4, 15);
        // クロスヘア方向へ飛ばす（ピッチを尊重）。軽い放物線を足して自然に
        Vec3 target = eye.add(look.scale(dist));
        // 放物線補正: 距離に応じて少し下げる（15で約-0.9）
        target = target.add(0, -dist * dist * 0.015, 0);
        // レイでブロック衝突チェック
        ClipContext ctx = new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp);
        BlockHitResult hit = lvl.clip(ctx);
        Vec3 pos;
        if (hit.getType() == HitResult.Type.BLOCK) {
            pos = hit.getLocation().add(hit.getDirection().getNormal().getX() * 0.05, 0.15, hit.getDirection().getNormal().getZ() * 0.05);
        } else {
            pos = target;
        }
        int px = Mth.floor(pos.x);
        int pz = Mth.floor(pos.z);
        // 水面を探す — 水に着水しなければキャンセル扱いのため水以外は null
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        // 少し広めに探索（水面の揺れや半ブロック対応）
        for (int dy = 3; dy >= -8; dy--) {
            int y = Mth.floor(pos.y) + dy;
            mp.set(px, y, pz);
            var state = lvl.getBlockState(mp);
            // 水源または流れでも水として扱う（流れでも釣り可能に）
            if (!state.getFluidState().isEmpty() && state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.WATER) {
                // 上が空気または水なら水面として有効
                var above = lvl.getBlockState(mp.above());
                if (above.isAir() || !above.getFluidState().isEmpty()) {
                    // 水面の高さを FluidState から取得（源泉は 0.9、流れは少し低め）
                    float h = state.getFluidState().getHeight(lvl, mp);
                    // 源泉なら 0.9、流れなら h に応じて
                    double waterY = y + h;
                    // クリップ位置が水面より少し上になるように
                    return new Vec3(px + 0.5, waterY + 0.05, pz + 0.5);
                }
            }
            // 水logged ブロックも水として扱う（例: 水入り半ブロック）
            if (state.getFluidState().isSource() && state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.WATER) {
                return new Vec3(px + 0.5, y + 0.9, pz + 0.5);
            }
        }
        // 水が見つからなければ null（着水失敗→キャンセル）
        return null;
    }

    private static void removeBobber(UUID id) {
        FishingBobberEntity b = BOBBERS.remove(id);
        if (b != null && !b.isRemoved()) b.discard();
    }

    /**
     * クライアントからの結果報告を処理する。
     * @param resultType 0=成功, 1=失敗, 2=キャンセル
     */
    public static void handleResult(ServerPlayer player, int resultType) {
        UUID id = player.getUUID();
        FishingPending pending = PENDING.get(id);
        if (pending == null) {
            LOGGER.warn("Fishing result without pending from {}", player.getName().getString());
            return;
        }
        long now = player.serverLevel().getGameTime();
        // TTL チェック
        if (now - pending.castTick > TTL_TICKS) {
            PENDING.remove(id);
            LOGGER.warn("Fishing pending expired for {}", player.getName().getString());
            return;
        }
        // チート軽対策: 成功が早すぎる場合は失敗扱い
        if (resultType == 0) {
            if (now - pending.castTick < MIN_SUCCESS_TICKS) {
                LOGGER.warn("Suspicious fast fishing success from {} ({} ticks), treating as fail",
                        player.getName().getString(), now - pending.castTick);
                resultType = 1;
            }
        }

        // 成功 / 失敗 の場合のみ消費
        boolean shouldConsume = (resultType == 0 || resultType == 1);
        if (shouldConsume) {
            // 餌消費
            consumeBait(player, pending);
            // 竿耐久
            damageRod(player, pending);
        }
        // ウキ再利用: 失敗/キャンセルはウキのみ、成功はItemEntityを引っ掛けて帰還
        FishingBobberEntity bob = BOBBERS.get(id);
        if (resultType == 0) {
            ruby.bamboo.skill.SkillHelper.addXp(player, ruby.bamboo.skill.SkillType.FISHING, 1);
            ServerLevel level = player.serverLevel();
            ItemStack catchStack = FishingManager.createCatchStack(
                    new FishingManager.RollResult(pending.entry, pending.size, pending.startProgress,
                            pending.fishStamina, pending.fishPower, pending.movePattern),
                    player.getRandom());
            if (!catchStack.isEmpty()) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.9F, 1.05F);
                if (bob != null && !bob.isRemoved()) {
                    // ちゃんとItemで出す: ウキに魚を引っ掛けて一緒に帰還。実体はItemEntity、座標同期はウキに任せる
                    ItemEntity hookItem = new ItemEntity(level, bob.getX(), bob.getY() + 0.2, bob.getZ(), catchStack);
                    hookItem.setPickUpDelay(1000);
                    hookItem.setUnlimitedLifetime();
                    hookItem.setNoGravity(true);
                    try {
                        java.lang.reflect.Field f = ItemEntity.class.getDeclaredField("bobOffs");
                        f.setAccessible(true);
                        Vec3 to0 = new Vec3(player.getX(), player.getY() + 0.35, player.getZ()).subtract(bob.position());
                        float angle = (float) Mth.atan2(to0.x, to0.z);
                        f.setFloat(hookItem, angle);
                    } catch (Exception ignored) {}
                    level.addFreshEntity(hookItem);
                    hookItem.startRiding(bob);
                    bob.startReturn(ItemStack.EMPTY);
                } else {
                    // ウキが無い保険は直接インベントリへ
                    boolean added = player.getInventory().add(catchStack);
                    if (!added) {
                        ItemEntity drop = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), catchStack);
                        drop.setPickUpDelay(0);
                        level.addFreshEntity(drop);
                    }
                }
            } else {
                if (bob != null && !bob.isRemoved()) bob.startReturn(ItemStack.EMPTY);
            }
        } else if (resultType == 1) {
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.5F, 0.9F);
            if (bob != null && !bob.isRemoved()) bob.startReturn(ItemStack.EMPTY);
        } else {
            if (bob != null && !bob.isRemoved()) bob.startReturn(ItemStack.EMPTY);
        }
        // pending 消化（ウキは帰還させるため即時除去せず、到達時にtickでdiscard）
        PENDING.remove(id);
    }

    private static void consumeBait(ServerPlayer player, FishingPending pending) {
        // 現在のインベントリから該当餌を 1 個消費 / 耐久減少
        // offhand 優先で検索
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack handStack = player.getItemInHand(hand);
            if (!handStack.isEmpty() && isMatchingBait(handStack, pending.baitItemId)) {
                if (pending.baitIsLure) {
                    // ルアー: ダメージ
                    if (handStack.isDamageableItem()) {
                        handStack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                    }
                } else {
                    // エサ: 個数消費
                    if (!player.getAbilities().instabuild) {
                        handStack.shrink(1);
                    }
                }
                return;
            }
        }
        // インベントリ走査
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            if (!isMatchingBait(s, pending.baitItemId)) continue;
            if (pending.baitIsLure) {
                if (s.isDamageableItem()) {
                    s.hurtAndBreak(1, player, p -> {});
                }
            } else {
                if (!player.getAbilities().instabuild) {
                    s.shrink(1);
                }
            }
            return;
        }
        // 見つからない場合: 餌なしで釣ったとみなす (何もしない)
        LOGGER.warn("Bait not found for consumption: player={}, bait={}", player.getName().getString(), pending.baitItemId);
    }

    private static boolean isMatchingBait(ItemStack stack, ResourceLocation baitId) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return baitId.equals(key);
    }

    private static void damageRod(ServerPlayer player, FishingPending pending) {
        // 竿はメインハンド or オフハンドの bamboo_rod
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack handStack = player.getItemInHand(hand);
            if (!handStack.isEmpty() && handStack.getItem() instanceof ruby.bamboo.item.BambooRodItem) {
                handStack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                return;
            }
        }
        // インベントリ内の竿もダメージ対象にしない (手持ちのみ)
    }

    // ===== Player lifecycle（TTLは不要: 上書きで安全なため） =====

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        removeBobber(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        removeBobber(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        removeBobber(event.getEntity().getUUID());
    }
}
