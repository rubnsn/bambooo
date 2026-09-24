package ruby.bamboo.client.renderer;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import ruby.bamboo.client.FigurePose;
import ruby.bamboo.entity.FigureEntity;

/**
 * 設置フィギュアの描画 + ダミー再構成・描画ヘルパー (GUI/BEWLR共用)。
 * バニラの LivingEntityRenderer からモデル・テクスチャだけ借り、
 * setupAnim (静止) → ポーズ適用 → モデル単体描画する。
 * 捕獲時の装備はそのまま表示する (防具レイヤー含む)。
 */
public class FigureRenderer extends EntityRenderer<FigureEntity> {

    private static final ResourceLocation FALLBACK_TEXTURE = ResourceLocation
            .fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png");

    public FigureRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(FigureEntity entity) {
        return FALLBACK_TEXTURE;
    }

    @Override
    public void render(FigureEntity entity, float entityYaw, float partialTicks, PoseStack pose,
            MultiBufferSource buffer, int packedLight) {
        LivingEntity dummy = getPlacedDummy(entity);
        if (dummy == null) return;
        renderDummy(pose, buffer, dummy, entity.getFigureScale(), entity.getFigureYaw(),
                entity.getFigurePose(), packedLight);
    }

    // ===== ダミー再構成キャッシュ =====

    private static final int CACHE_MAX = 64;
    private static final Map<String, LivingEntity> DUMMY_CACHE = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, LivingEntity> eldest) {
            return size() > CACHE_MAX;
        }
    };

    /** 設置個体用のダミー (entityId単位で共有)。 */
    @javax.annotation.Nullable
    public static synchronized LivingEntity getPlacedDummy(FigureEntity figure) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        // 個体データもキーに含める (別個体・別セッションの同ID衝突で古いダミーを返さない)
        int dataHash = 0;
        try {
            dataHash = figure.getFigureData().hashCode();
        } catch (Exception e) {
        }
        String key = "e" + figure.getId() + "@" + figure.getFigureId() + "#" + dataHash;
        LivingEntity dummy = DUMMY_CACHE.get(key);
        if (dummy != null) {
            try {
                String dummyId = EntityType.getKey(dummy.getType()).toString();
                if (dummyId.equals(figure.getFigureId())) return dummy;
            } catch (Exception e) {
                // 検証失敗時は作り直す
            }
        }
        dummy = buildDummy(mc.level, figure.getFigureId(), figure.getFigureData());
        if (dummy != null) DUMMY_CACHE.put(key, dummy);
        return dummy;
    }

    /** アイテム用のダミー (NBTハッシュ単位で共有)。 */
    @javax.annotation.Nullable
    public static synchronized LivingEntity getItemDummy(Level level, String entityId, CompoundTag data) {
        if (level == null) return null;
        String key = "i" + entityId + "#" + data.hashCode();
        LivingEntity dummy = DUMMY_CACHE.get(key);
        if (dummy != null) return dummy;
        dummy = buildDummy(level, entityId, data);
        if (dummy != null) DUMMY_CACHE.put(key, dummy);
        return dummy;
    }

    @javax.annotation.Nullable
    private static LivingEntity buildDummy(Level level, String entityId, CompoundTag data) {
        if (entityId == null || entityId.isEmpty()) return null;
        try {
            var opt = EntityType.byString(entityId);
            if (opt.isEmpty() || !(opt.get().create(level) instanceof LivingEntity living)) return null;
            CompoundTag tag = data.copy();
            tag.remove("Pos");
            tag.remove("Motion");
            tag.remove("Rotation");
            tag.remove("id");
            living.load(tag);
            // 子供・座り等の瞬間状態を静止化 + ダメージ・死亡演出の残留を消す
            living.setDeltaMovement(0.0D, 0.0D, 0.0D);
            try {
                living.setPose(net.minecraft.world.entity.Pose.STANDING);
            } catch (Exception e) {
            }
            try {
                living.hurtTime = 0;
                living.deathTime = 0;
            } catch (Exception e) {
            }
            // 展示用に名前のみ外す (名札表示を防ぐ)。装備は捕獲時のまま表示する
            try {
                living.setCustomName(null);
            } catch (Exception e) {
            }
            neutralizeDisplayState(living);
            return living;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 展示用に姿勢を正す (ぶら下がり・仮死・腹見せ・睡眠はフィギュアでは直立に戻す)。
     * 同期データは触らずクライアント側ダミーのみに適用する。
     */
    private static void neutralizeDisplayState(LivingEntity living) {
        try {
            if (living instanceof net.minecraft.world.entity.ambient.Bat bat) {
                bat.setResting(false);
            }
            if (living instanceof net.minecraft.world.entity.animal.axolotl.Axolotl axolotl) {
                axolotl.setPlayingDead(false);
            }
            if (living instanceof net.minecraft.world.entity.animal.Cat cat) {
                cat.setRelaxStateOne(false);
                cat.setLying(false);
            }
        } catch (Exception e) {
            // 中立化失敗時はそのまま描画
        }
    }

    // ===== ダミー描画本体 =====

    /**
     * 足元原点・ブロック単位でダミーを描画する。
     * バニラの LivingEntityRenderer へ完全委譲するため、
     * 本物のMobと向き・陰影・レイヤーが一致する。可動部ポーズは
     * FigurePoseMixin が setupAnim 直後に注入する。
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void renderDummy(PoseStack pose, MultiBufferSource buffer, LivingEntity dummy,
            float scale, float yawDeg, CompoundTag poseTag, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderer<?> renderer;
        try {
            renderer = dispatcher.getRenderer(dummy);
        } catch (Exception e) {
            return;
        }
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)) {
            // 非Living系 (通常ありえない) は通常描画にフォールバック
            dispatcher.render(dummy, 0.0D, 0.0D, 0.0D, yawDeg, 1.0F, pose, buffer, packedLight);
            return;
        }
        pose.pushPose();
        pose.scale(scale, scale, scale);
        // バニラは引数yawではなくダミー自身のyBodyRotで体を回転させる。
        // ダミーはRotation剥がし済み (常に0) のため、表示yawを毎フレーム書き込む
        dummy.setYBodyRot(yawDeg);
        dummy.yBodyRotO = yawDeg;
        dummy.setYHeadRot(yawDeg);
        dummy.yHeadRotO = yawDeg;
        dummy.setYRot(yawDeg);
        dummy.yRotO = yawDeg;
        ruby.bamboo.client.FigurePoseState.set(poseTag);
        try {
            ((LivingEntityRenderer) livingRenderer).render(dummy, yawDeg, 1.0F, pose, buffer,
                    packedLight);
        } catch (Exception e) {
            // 描画失敗時は欠けとして無視
        } finally {
            ruby.bamboo.client.FigurePoseState.clear();
        }
        pose.popPose();
    }
}
