package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import ruby.bamboo.BambooMod;
import ruby.bamboo.client.model.MaidModel;
import ruby.bamboo.client.model.VillagerHatModel;
import ruby.bamboo.core.init.BambooItems;

/**
 * 村人互換のメイド見た目レンダラー。EntityRenderers で VillagerRenderer と差し替え登録する。
 * 胴にメイド服があればメイドモデル描画、なければ親のバニラ流 (super) に丸投げする。
 * 自前2レイヤーは非メイド時に自衛ガードで何も描かないため、通常村人は完全バニラ描画。
 */
@OnlyIn(Dist.CLIENT)
public class MaidRenderer extends VillagerRenderer {

    /** 素体色サフィックス (現在 green/white。acc は同一値で追従し、必ず一致する) */
    private static String bodySuffix(Villager villager) {
        return isHatted(villager) ? "white" : "green";
    }

    /** maid_<suffix>.png。素体 (帽子あり職業は white、素頭は green) */
    private static ResourceLocation bodyTexture(Villager villager) {
        return ResourceLocation.fromNamespaceAndPath(BambooMod.MODID,
                "textures/entity/maid/maid_" + bodySuffix(villager) + ".png");
    }

    private final MaidModel<Villager> maidModel;
    private final MaidHandLayer maidHand;
    private final VillagerHatModel hatModel;

    /** maid_acc_<suffix>.png があれば返し、無ければ null (付属品パス自体を飛ばす) */
    private static ResourceLocation accTexture(Villager villager) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID,
                "textures/entity/maid/maid_acc_" + bodySuffix(villager) + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(loc).isPresent()) return loc;
        return null;
    }

    /**
     * 帽子あり職業 (実テクスチャの帽子シェル部で判定。2026-09-24)。
     * 素頭: leatherworker、mason、nitwit、toolsmith、weaponsmith、none、asobinin(自作・素頭確認)
     */
    private static final java.util.Set<String> HATTED = java.util.Set.of(
            "armorer", "butcher", "cartographer", "cleric", "farmer",
            "fisherman", "fletcher", "librarian", "shepherd");

    /** 帽子あり職業か (maid_acc_<職業>.png / white 素体の選択に使う) */
    public static boolean isHatted(Villager villager) {
        String path = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()).getPath();
        return HATTED.contains(path);
    }

    /**
     * 帽子あり職業のバニラ帽子テクスチャ。職業ファイルが無ければ null (帽子パスを飛ばす)
     */
    private static ResourceLocation professionHatTexture(Villager villager) {
        String prof = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()).getPath();
        ResourceLocation loc = new ResourceLocation(
                "textures/entity/villager/profession/" + prof + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(loc).isPresent()) return loc;
        return null;
    }

    /** メイド服着用判定。レイヤーの自衛ガードと render 分岐の共通口 */
    public static boolean isMaid(Villager villager) {
        return villager.getItemBySlot(EquipmentSlot.CHEST).is(BambooItems.MAID_CLOTHES.get());
    }

    public MaidRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.maidModel = new MaidModel<>(MaidModel.createBodyLayer().bakeRoot());
        this.hatModel = new VillagerHatModel(VillagerHatModel.createBodyLayer().bakeRoot());
        this.maidHand = new MaidHandLayer(this, this.maidModel, ctx.getItemInHandRenderer());
        this.addLayer(this.maidHand);
        this.shadowRadius = 0.4F;
    }

    @Override
    public ResourceLocation getTextureLocation(Villager entity) {
        if (!isMaid(entity)) return super.getTextureLocation(entity);
        return bodyTexture(entity);
    }

    @Override
    protected void scale(Villager entity, PoseStack pose, float partialTicks) {
        if (!isMaid(entity)) {
            super.scale(entity, pose, partialTicks);
            return;
        }
        // young 分岐が子供対応するため半減しない (バニラ Humanoid 系と同様)。二重縮小の防止
        float f = 0.9375F;
        this.shadowRadius = entity.isBaby() ? 0.25F : 0.4F;
        pose.scale(f, f, f);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(Villager entity, float entityYaw, float partialTicks, PoseStack pose,
            MultiBufferSource buffer, int packedLight) {
        if (!isMaid(entity)) {
            super.render(entity, entityYaw, partialTicks, pose, buffer, packedLight);
            return;
        }
        if (MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Pre<Villager, VillagerModel<Villager>>(
                entity, this, partialTicks, pose, buffer, packedLight))) {
            return;
        }
        pose.pushPose();
        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float yawDiff = headYaw - bodyYaw;
        float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        boolean shouldSit = entity.isPassenger()
                && entity.getVehicle() != null && entity.getVehicle().shouldRiderSit();
        if (shouldSit && entity.getVehicle() instanceof LivingEntity vehicle) {
            bodyYaw = Mth.rotLerp(partialTicks, vehicle.yBodyRotO, vehicle.yBodyRot);
            yawDiff = headYaw - bodyYaw;
            float wrapped = Mth.wrapDegrees(yawDiff);
            if (wrapped < -85F) wrapped = -85F;
            if (wrapped >= 85F) wrapped = 85F;
            bodyYaw = headYaw - wrapped;
            if (wrapped * wrapped > 2500F) bodyYaw += wrapped * 0.2F;
            yawDiff = headYaw - bodyYaw;
        }
        if (entity.hasPose(Pose.SLEEPING)) {
            Direction bedDir = entity.getBedOrientation();
            if (bedDir != null) {
                float eyeGap = entity.getEyeHeight(Pose.STANDING) - 0.1F;
                pose.translate(-bedDir.getStepX() * eyeGap, 0F, -bedDir.getStepZ() * eyeGap);
            }
        }
        float bob = this.getBob(entity, partialTicks);
        this.setupRotations(entity, pose, bob, bodyYaw, partialTicks);
        pose.scale(-1F, -1F, 1F);
        this.scale(entity, pose, partialTicks);
        pose.translate(0F, -1.501F, 0F);
        float limbSwingAmt = 0F;
        float limbSwing = 0F;
        if (!shouldSit && entity.isAlive()) {
            limbSwingAmt = entity.walkAnimation.speed(partialTicks);
            limbSwing = entity.walkAnimation.position(partialTicks);
            if (entity.isBaby()) limbSwing *= 3F;
            if (limbSwingAmt > 1F) limbSwingAmt = 1F;
        }
        // LivingEntityRenderer.java:58 と同様。未代入だと初期値 true のまま成人も
        // baby 分岐 (AgeableListModel.java:45-64、頭+16・胴×0.5) で描画される。
        // 12px浮き・小柄・ピッチ分離の全てはこれが正体。帽子は分岐を迂回するため別途追従
        this.maidModel.young = entity.isBaby();
        this.maidModel.prepareMobModel(entity, limbSwing, limbSwingAmt, partialTicks);
        this.maidModel.setupAnim(entity, limbSwing, limbSwingAmt, bob, yawDiff, pitch);
        Minecraft minecraft = Minecraft.getInstance();
        boolean visible = !entity.isInvisible();
        boolean visibleToPlayer = !visible && !entity.isInvisibleTo(minecraft.player);
        boolean glowing = minecraft.shouldEntityAppearGlowing(entity);
        RenderType renderType = this.getRenderType(entity, visible, visibleToPlayer, glowing);
        float alpha = visibleToPlayer ? 0.15F : 1F;
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, this.getWhiteOverlayProgress(entity, partialTicks));
        if (renderType != null) {
            VertexConsumer consumer = buffer.getBuffer(renderType);
            this.maidModel.renderToBuffer(pose, consumer, packedLight, overlay, 1F, 1F, 1F, alpha);
        }
        // 付属品2パス (head 直下の子を本体と同一経路で描く。手動の head 変換なし)
        boolean hatted = isHatted(entity);
        ResourceLocation accTex = accTexture(entity);
        if (accTex != null) {
            this.maidModel.prepareAccessoryPass(false);
            this.maidModel.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutout(accTex)),
                    packedLight, overlay, 1F, 1F, 1F, alpha);
            this.maidModel.prepareAccessoryPass(true);
            this.maidModel.renderToBuffer(pose, buffer.getBuffer(RenderType.entityTranslucent(accTex)),
                    packedLight, overlay, 1F, 1F, 1F, alpha);
            this.maidModel.restoreBodyVisible();
        }
        // 村人帽子 (バニラ職業テクスチャをそのまま被せる。maid 頭の回転・位置を複写。
        // 子供時は AgeableListModel.java:45-56 の head 分岐と同一変換を被せて追従させる)
        if (hatted) {
            ResourceLocation profTex = professionHatTexture(entity);
            if (profTex != null) {
                net.minecraft.client.model.geom.ModelPart maidHead = this.maidModel.getHead();
                net.minecraft.client.model.geom.ModelPart hatHead = this.hatModel.head;
                hatHead.setPos(maidHead.x, maidHead.y, maidHead.z);
                hatHead.xRot = maidHead.xRot;
                hatHead.yRot = maidHead.yRot;
                hatHead.zRot = maidHead.zRot;
                pose.pushPose();
                if (this.maidModel.young) {
                    float f = 1.5F / 2.0F;
                    pose.scale(f, f, f);
                    pose.translate(0F, 16F / 16F, 0F);
                }
                hatHead.render(pose, buffer.getBuffer(RenderType.entityCutout(profTex)),
                        packedLight, overlay, 1F, 1F, 1F, alpha);
                pose.popPose();
            }
        }
        if (!entity.isSpectator()) {
            // 親レイヤー (職業服・腕組み持物) は VillagerModel 用で部位が合わないため呼ばない
            this.maidHand.render(pose, buffer, packedLight, entity, limbSwing, limbSwingAmt, partialTicks, bob, yawDiff, pitch);
        }
        pose.popPose();
        RenderNameTagEvent nameEvent = new RenderNameTagEvent(entity, entity.getDisplayName(),
                this, pose, buffer, packedLight, partialTicks);
        MinecraftForge.EVENT_BUS.post(nameEvent);
        if (nameEvent.getResult() != Event.Result.DENY
                && (nameEvent.getResult() == Event.Result.ALLOW || this.shouldShowName(entity))) {
            this.renderNameTag(entity, nameEvent.getContent(), pose, buffer, packedLight);
        }
        MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Post<Villager, VillagerModel<Villager>>(
                entity, this, partialTicks, pose, buffer, packedLight));
    }
}
