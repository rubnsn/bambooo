package ruby.bamboo.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
 * 変身のクライアント描画。
 * TRUE=真モデル置換(防具はfakeへ複写) / 併用=Player体から頭・胴・脚を隠し腕だけ残し、
 * 変身モデルを足接地(HYBRID_GROUND)・頭胸高さ浮遊(HYBRID_FLOAT/HEAD)で重ねる。
 * 1人称・手持ち描画には触らない。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TransformRenderHandler {

    private record FakeEntry(String id, LivingEntity fake) {
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
        if (cfg.mode() == TransformRegistry.RenderMode.TRUE) {
            event.setCanceled(true);
            renderTrue(event, player, id, cfg);
            return;
        }
        // 併用: 頭・胴・脚を隠し、腕(手持ち)だけ残す。Post で必ず戻す。
        hideBody(event.getRenderer());
    }

    @SubscribeEvent
    public static void onPost(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        String id = ClientTransformHandler.get(player.getUUID());
        if (id.isEmpty()) {
            return;
        }
        TransformRegistry.RaceConfig cfg = TransformRegistry.configOf(id);
        if (cfg.mode() == TransformRegistry.RenderMode.TRUE) {
            return;
        }
        restoreBody(event.getRenderer());
        renderHybrid(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                event.getPartialTick(), player, id, cfg);
    }

    private static void hideBody(PlayerRenderer renderer) {
        try {
            PlayerModel<?> model = renderer.getModel();
            model.head.visible = false;
            model.hat.visible = false;
            model.body.visible = false;
            model.jacket.visible = false;
            model.leftLeg.visible = false;
            model.rightLeg.visible = false;
            setPantsVisible(model, false);
        } catch (Exception ignored) {
        }
    }

    private static void restoreBody(PlayerRenderer renderer) {
        try {
            PlayerModel<?> model = renderer.getModel();
            model.head.visible = true;
            model.hat.visible = true;
            model.body.visible = true;
            model.jacket.visible = true;
            model.leftLeg.visible = true;
            model.rightLeg.visible = true;
            model.leftArm.visible = true;
            model.rightArm.visible = true;
            setPantsVisible(model, true);
            setSleeveVisible(model, true);
        } catch (Exception ignored) {
        }
    }

    private static void setPantsVisible(PlayerModel<?> model, boolean v) {
        try {
            model.leftPants.visible = v;
            model.rightPants.visible = v;
        } catch (Exception ignored) {
        }
    }

    private static void setSleeveVisible(PlayerModel<?> model, boolean v) {
        try {
            model.leftSleeve.visible = v;
            model.rightSleeve.visible = v;
        } catch (Exception ignored) {
        }
    }

    private static LivingEntity fakeFor(Player player, String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        UUID uuid = player.getUUID();
        FakeEntry entry = FAKES.get(uuid);
        if (entry != null && entry.id().equals(id) && entry.fake().level() == mc.level
                && !entry.fake().isRemoved()) {
            return entry.fake();
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
            FAKES.put(uuid, new FakeEntry(id, living));
            if (FAKES.size() > 100) {
                FAKES.clear();
                FAKES.put(uuid, new FakeEntry(id, living));
            }
            return living;
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
            if (id.isEmpty() || !entry.id().equals(id) || entry.fake().level() != mc.level
                    || entry.fake().isRemoved()) {
                continue;
            }
            syncFake(entry.fake(), player);
            entry.fake().walkAnimation.update(player.walkAnimation.speed(), 1.0F);
        }
    }

    private static void syncFake(LivingEntity fake, Player player) {
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
        // 防具・手持ちを複写 (真モデル側の装備レイヤー用)
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            try {
                fake.setItemSlot(slot, player.getItemBySlot(slot).copy());
            } catch (Exception ignored) {
            }
        }
        fake.setCustomName(player.getDisplayName());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void renderTrue(RenderPlayerEvent.Pre event, Player player, String id,
            TransformRegistry.RaceConfig cfg) {
        LivingEntity fake = fakeFor(player, id);
        if (fake == null) {
            return;
        }
        syncFake(fake, player);
        fake.setCustomNameVisible(true);
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        if (cfg.scale() != 1F) {
            pose.scale(cfg.scale(), cfg.scale(), cfg.scale());
        }
        try {
            EntityRenderer renderer = dispatcher.getRenderer(fake);
            renderer.render(fake, player.getYRot(), event.getPartialTick(), pose,
                    event.getMultiBufferSource(), event.getPackedLight());
        } catch (Exception ignored) {
        } finally {
            pose.popPose();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void renderHybrid(PoseStack pose, MultiBufferSource buffer, int light, float partial,
            Player player, String id, TransformRegistry.RaceConfig cfg) {
        // 1人称視点の本人は描画しない (手持ちに触らない)
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == player && mc.options.getCameraType().isFirstPerson()
                && player instanceof AbstractClientPlayer) {
            return;
        }
        LivingEntity fake = fakeFor(player, id);
        if (fake == null) {
            return;
        }
        syncFake(fake, player);
        // fake 自身の名札は出さない (Player側の名札が生きているため)
        fake.setCustomNameVisible(false);
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        pose.pushPose();
        pose.translate(0D, cfg.yOff(), 0D);
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
