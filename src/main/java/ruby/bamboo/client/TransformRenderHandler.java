package ruby.bamboo.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ruby.bamboo.BambooMod;
import ruby.bamboo.client.handler.ClientTransformHandler;
import ruby.bamboo.transform.TransformRegistry;

/**
 * 変身のクライアント描画。変身中はバニラの Player 描画を全置換する。
 * TRUE=真モデル / 併用=足接地(HYBRID_GROUND)・頭胸高さ浮遊(HYBRID_FLOAT)。
 * 併用は手持ち無表示 (真モデルのみメインハンド表示、オフハンドは全種非表示)。
 * 1人称・手持ち描画には触らない。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TransformRenderHandler {

    /** fake 本体 + 動的 y オフセット (オウム高度遷移/スライム系ホップ用)。 */
    private static final class FakeEntry {
        final String id;
        final LivingEntity fake;
        float yOff;

        FakeEntry(String id, LivingEntity fake, float yOff) {
            this.id = id;
            this.fake = fake;
            this.yOff = yOff;
        }
    }

    private static final Map<UUID, FakeEntry> FAKES = new ConcurrentHashMap<>();

    private TransformRenderHandler() {
    }

    @SubscribeEvent
    public static void onPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        String id = ClientTransformHandler.get(player.getUUID());
        if (id.isEmpty()) {
            return;
        }
        TransformRegistry.RaceConfig cfg = TransformRegistry.configOf(id);
        event.setCanceled(true);
        renderDisguise(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                event.getPartialTick(), player, id, cfg);
    }

    private static FakeEntry entryFor(Player player, String id, TransformRegistry.RaceConfig cfg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        UUID uuid = player.getUUID();
        FakeEntry entry = FAKES.get(uuid);
        if (entry != null && entry.id.equals(id) && entry.fake.level() == mc.level
                && !entry.fake.isRemoved()) {
            return entry;
        }
        EntityType<?> type = null;
        try {
            type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id));
        } catch (Exception ignored) {
        }
        if (type == null) {
            return null;
        }
        try {
            var created = type.create(mc.level);
            if (!(created instanceof LivingEntity living)) {
                return null;
            }
            FakeEntry fresh = new FakeEntry(id, living, cfg.yOff());
            FAKES.put(uuid, fresh);
            if (FAKES.size() > 100) {
                FAKES.clear();
                FAKES.put(uuid, fresh);
            }
            return fresh;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * クライアント tick 毎の追従。fake は tick されないため、補間用の旧値(O値)も
     * 現在値に揃えてレンダラの lerp 誤差をゼロにする (ブルブル防止)。
     * 歩行アニメは tick レートで update して進める (render 側では進めない)。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!FAKES.isEmpty()) {
                FAKES.clear();
            }
            return;
        }
        for (Map.Entry<UUID, FakeEntry> e : FAKES.entrySet()) {
            Player player = mc.level.getPlayerByUUID(e.getKey());
            if (player == null) {
                continue;
            }
            String id = ClientTransformHandler.get(player.getUUID());
            FakeEntry entry = e.getValue();
            if (id.isEmpty() || !entry.id.equals(id) || entry.fake.level() != mc.level
                    || entry.fake.isRemoved()) {
                continue;
            }
            boolean hands = TransformRegistry.configOf(id).mode() == TransformRegistry.RenderMode.TRUE;
            syncFake(entry.fake, player, hands);
            entry.fake.walkAnimation.update(player.walkAnimation.speed(), 1.0F);
            tickSpecial(entry, player);
        }
    }

    private static void syncFake(LivingEntity fake, Player player, boolean copyHands) {
        fake.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        fake.yRotO = player.getYRot();
        fake.xRotO = player.getXRot();
        fake.yBodyRot = player.yBodyRot;
        fake.yBodyRotO = player.yBodyRot;
        fake.yHeadRot = player.yHeadRot;
        fake.yHeadRotO = player.yHeadRot;
        fake.tickCount = player.tickCount;
        fake.attackAnim = player.attackAnim;
        fake.oAttackAnim = player.attackAnim;
        fake.hurtTime = player.hurtTime;
        // 防具を複写。手持ちは真モデルのみ複写し、併用・頭部は両手とも非表示。
        // オフハンドは全モードで複写しない。
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.OFFHAND
                    || ((slot == EquipmentSlot.MAINHAND) && !copyHands)) {
                try {
                    fake.setItemSlot(slot, ItemStack.EMPTY);
                } catch (Exception ignored) {
                }
                continue;
            }
            try {
                fake.setItemSlot(slot, player.getItemBySlot(slot).copy());
            } catch (Exception ignored) {
            }
        }
        fake.setCustomName(player.getDisplayName());
        fake.setCustomNameVisible(true);
    }

    /**
     * 種族固有アニメの駆動 (fake は tick されないためここで進める)。
     * オウム: 停止=羽休め(y0) / 移動=飛行(y1) を高度遷移付きで切替。
     * ニワトリ: 空中でのみ羽ばたき (地上では畳む)。
     * イカ: 足の開閉。
     * スライム/マグマキューブ/シュルカー: 移動時のみ y オフセットで小ジャンプ風 (実移動なし)。
     */
    private static void tickSpecial(FakeEntry entry, Player player) {
        LivingEntity fake = entry.fake;
        if (fake instanceof Parrot parrot) {
            boolean flying = player.walkAnimation.speed() > 0.08F
                    || player.getDeltaMovement().horizontalDistance() > 0.03D;
            parrot.oFlap = parrot.flap;
            parrot.oFlapSpeed = parrot.flapSpeed;
            parrot.flapSpeed = Mth.clamp(parrot.flapSpeed + (flying ? 4.0F : -1.0F) * 0.3F, 0.0F, 1.0F);
            parrot.flap += 0.2F + parrot.flapSpeed * 1.8F;
            float target = flying ? 1.0F : 0.0F;
            entry.yOff += (target - entry.yOff) * 0.08F;
            if (Math.abs(target - entry.yOff) < 0.01F) {
                entry.yOff = target;
            }
        } else if (fake instanceof Chicken chicken) {
            boolean airborne = !player.onGround() && !player.isInWater() && !player.isInLava();
            chicken.oFlap = chicken.flap;
            chicken.oFlapSpeed = chicken.flapSpeed;
            chicken.flapSpeed += (airborne ? 4.0F : -1.0F) * 0.3F;
            chicken.flapSpeed = Mth.clamp(chicken.flapSpeed, 0.0F, 1.0F);
            if (airborne && chicken.flapping < 1.0F) {
                chicken.flapping = 1.0F;
            }
            chicken.flapping *= 0.9F;
            chicken.flap += chicken.flapping * 2.0F;
        } else if (fake instanceof Squid squid) {
            squid.oldTentacleMovement = squid.tentacleMovement;
            squid.oldTentacleAngle = squid.tentacleAngle;
            float move = player.walkAnimation.speed();
            squid.tentacleMovement += 0.12F + Math.min(move * 0.08F, 0.4F);
            squid.tentacleAngle = 0.55F + (float) Math.sin(player.tickCount * 0.12F) * 0.22F;
            squid.xBodyRotO = squid.xBodyRot;
        } else if (fake instanceof Slime || fake instanceof MagmaCube || fake instanceof Shulker) {
            float base = TransformRegistry.configOf(entry.id).yOff();
            boolean moving = player.walkAnimation.speed() > 0.08F
                    || player.getDeltaMovement().horizontalDistance() > 0.03D;
            if (moving) {
                float amp = (fake instanceof Shulker) ? 0.18F : 0.25F;
                float hop = Math.abs(Mth.sin(player.tickCount * 0.45F)) * amp;
                entry.yOff = base + hop;
            } else {
                entry.yOff += (base - entry.yOff) * 0.3F;
                if (Math.abs(base - entry.yOff) < 0.01F) {
                    entry.yOff = base;
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void renderDisguise(PoseStack pose, MultiBufferSource buffer, int light, float partial,
            Player player, String id, TransformRegistry.RaceConfig cfg) {
        FakeEntry entry = entryFor(player, id, cfg);
        if (entry == null) {
            return;
        }
        LivingEntity fake = entry.fake;
        boolean hands = cfg.mode() == TransformRegistry.RenderMode.TRUE;
        syncFake(fake, player, hands);
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        pose.pushPose();
        if (cfg.mode() != TransformRegistry.RenderMode.TRUE) {
            pose.translate(0D, entry.yOff, 0D);
        }
        if (cfg.scale() != 1F) {
            pose.scale(cfg.scale(), cfg.scale(), cfg.scale());
        }
        try {
            EntityRenderer renderer = dispatcher.getRenderer(fake);
            renderer.render(fake, player.getYRot(), partial, pose, buffer, light);
        } catch (Exception ignored) {
        } finally {
            pose.popPose();
        }
    }

}
