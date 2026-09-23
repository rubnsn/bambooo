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
import ruby.bamboo.core.init.BambooItems;

/**
 * 村人互換のメイド見た目レンダラー。EntityRenderers で VillagerRenderer と差し替え登録する。
 * 胴にメイド服があればメイドモデル描画、なければ親のバニラ流 (super) に丸投げする。
 * 自前2レイヤーは非メイド時に自衛ガードで何も描かないため、通常村人は完全バニラ描画。
 */
@OnlyIn(Dist.CLIENT)
public class MaidRenderer extends VillagerRenderer {

    private static final ResourceLocation MAID_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BambooMod.MODID, "textures/entity/maid/maid_green.png");

    private final MaidModel<Villager> maidModel;
    private final MaidHandLayer maidHand;

    /** 色サフィックス (バリアント追加時の拡張口。今は 00 固定) */
    private static final String ACC_SUFFIX = "green";

    /** maid_acc_XX.png があれば返し、無ければ null (付属品パス自体を飛ばす) */
    private static ResourceLocation accTexture() {
        ResourceLocation loc = new ResourceLocation("bamboomod",
                "textures/entity/maid/maid_acc_" + ACC_SUFFIX + ".png");
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
        this.maidHand = new MaidHandLayer(this, this.maidModel, ctx.getItemInHandRenderer());
        this.addLayer(this.maidHand);
        this.shadowRadius = 0.4F;
    }

    @Override
    public ResourceLocation getTextureLocation(Villager entity) {
        return isMaid(entity) ? MAID_TEXTURE : super.getTextureLocation(entity);
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
        ResourceLocation accTex = accTexture();
        if (accTex != null) {
            this.maidModel.prepareAccessoryPass(false);
            this.maidModel.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutout(accTex)),
                    packedLight, overlay, 1F, 1F, 1F, alpha);
            this.maidModel.prepareAccessoryPass(true);
            this.maidModel.renderToBuffer(pose, buffer.getBuffer(RenderType.entityTranslucent(accTex)),
                    packedLight, overlay, 1F, 1F, 1F, alpha);
            this.maidModel.restoreBodyVisible();
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
