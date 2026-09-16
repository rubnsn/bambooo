package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import ruby.bamboo.entity.FishingBobberEntity;
import ruby.bamboo.item.BambooRodItem;

/**
 * 釣りウキレンダラ — バニラ FishingHookRenderer を踏襲。
 * ウキは fishing_hook.png を entityCutout で 0.5 スケールのビルボード、ラインは lineStrip で 16 分割+弛み。
 *
 * <p>1.21.1 NeoForge: 竿所持判定は BambooItems 参照ではなく instanceof で行う
 * (登録は親の配線で行うためコンパイル時参照を持たない)。
 */
@OnlyIn(Dist.CLIENT)
public class FishingBobberRenderer extends EntityRenderer<FishingBobberEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE);

    public FishingBobberRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(FishingBobberEntity entity) {
        return TEXTURE;
    }

    private static boolean isHoldingRod(Player player) {
        return player.getMainHandItem().getItem() instanceof BambooRodItem
                || player.getOffhandItem().getItem() instanceof BambooRodItem;
    }

    @Override
    public void render(FishingBobberEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buf, int packedLight) {
        Player owner = entity.getOwnerPlayer();
        if (owner == null) {
            // fallback to local player if within range
            Player me = Minecraft.getInstance().player;
            if (me != null && entity.distanceTo(me) < 64) owner = me;
            else {
                super.render(entity, yaw, partialTick, pose, buf, packedLight);
                return;
            }
        }

        // ---- ウキ本体（バニラ踏襲）----
        pose.pushPose();
        pose.scale(0.5F, 0.5F, 0.5F);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        PoseStack.Pose last = pose.last();
        VertexConsumer vc = buf.getBuffer(RENDER_TYPE);
        vertex(vc, last, packedLight, 0.0F, 0, 0, 1);
        vertex(vc, last, packedLight, 1.0F, 0, 1, 1);
        vertex(vc, last, packedLight, 1.0F, 1, 1, 0);
        vertex(vc, last, packedLight, 0.0F, 1, 0, 0);
        pose.popPose();

        // ---- 成功時はアイテムをウキに引っ掛けて一緒に飛ばす ----
        ItemStack carried = entity.getCarried();
        if (!carried.isEmpty()) {
            pose.pushPose();
            // ウキの少し上（0.35）に浮かせて、常にカメラ向き（平面がプレイヤーを向く）
            pose.translate(0.0D, 0.35D, 0.0D);
            pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
            pose.scale(0.75F, 0.75F, 0.75F);
            Minecraft.getInstance().getItemRenderer().renderStatic(carried, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY, pose, buf, entity.level(), entity.getId());
            pose.popPose();
        }

        // ---- ライン（バニラ lineStrip 16 分割）----
        // ハンド位置の計算（バニラの first/third person 分岐を簡略踏襲）
        boolean isFirstPerson = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.getCameraType().isFirstPerson() && owner == mc.player) {
            isFirstPerson = true;
        }

        Vec3 handPos;
        if (isFirstPerson) {
            // First person: near plane 上の点（バニラは FOV と nearPlane で補正）
            double fovScale = 960.0 / (double) mc.options.fov().get();
            Camera cam = this.entityRenderDispatcher.camera;
            Camera.NearPlane near = cam.getNearPlane();
            int arm = owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
            // 竿が主手にあるかで反転
            if (!(owner.getMainHandItem().getItem() instanceof BambooRodItem)
                    && owner.getOffhandItem().getItem() instanceof BambooRodItem) {
                arm = -arm;
            }
            Vec3 nearPos = near.getPointOnPlane((float) arm * 0.525F, -0.1F).scale(fovScale).yRot(0.5F * owner.getAttackAnim(partialTick)).xRot(-0.7F * owner.getAttackAnim(partialTick));
            handPos = new Vec3(
                    Mth.lerp(partialTick, owner.xo, owner.getX()) + nearPos.x,
                    Mth.lerp(partialTick, owner.yo, owner.getY()) + nearPos.y,
                    Mth.lerp(partialTick, owner.zo, owner.getZ()) + nearPos.z
            );
            // eyeHeight 補正（バニラは y に eyeHeight を加算しない first person では nearPos が既に eye 付近）
            handPos = handPos.add(0, owner.getEyeHeight(), 0);
        } else {
            // Third person（バニラ踏襲）
            float yawLerp = Mth.lerp(partialTick, owner.yBodyRotO, owner.yBodyRot) * 0.017453292F;
            double sinYaw = Mth.sin(yawLerp);
            double cosYaw = Mth.cos(yawLerp);
            int k = owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
            if (!(owner.getMainHandItem().getItem() instanceof BambooRodItem)
                    && owner.getOffhandItem().getItem() instanceof BambooRodItem) {
                k = -k;
            }
            double handOffsetX = (double) k * 0.35D;
            double handOffsetZ = 0.8D;
            double px = Mth.lerp(partialTick, owner.xo, owner.getX()) - cosYaw * handOffsetX - sinYaw * handOffsetZ;
            double py = Mth.lerp(partialTick, owner.yo, owner.getY()) + (double) owner.getEyeHeight() - 0.45D;
            // しゃがみ補正
            float crouch = owner.isCrouching() ? -0.1875F : 0.0F;
            py += crouch;
            double pz = Mth.lerp(partialTick, owner.zo, owner.getZ()) - sinYaw * handOffsetX + cosYaw * handOffsetZ;
            handPos = new Vec3(px, py, pz);
        }

        double hookX = Mth.lerp(partialTick, entity.xo, entity.getX());
        double hookY = Mth.lerp(partialTick, entity.yo, entity.getY()) + 0.25D;
        double hookZ = Mth.lerp(partialTick, entity.zo, entity.getZ());

        float dx = (float) (handPos.x - hookX);
        float dy = (float) (handPos.y - hookY);
        float dz = (float) (handPos.z - hookZ);

        VertexConsumer lineVc = buf.getBuffer(RenderType.lineStrip());
        PoseStack.Pose linePose = pose.last();
        for (int i = 0; i <= 16; ++i) {
            stringVertex(dx, dy, dz, lineVc, linePose, fraction(i, 16), fraction(i + 1, 16));
        }

        super.render(entity, yaw, partialTick, pose, buf, packedLight);
    }

    private static float fraction(int cur, int total) {
        return (float) cur / (float) total;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, float x, int y, int u, int v) {
        vc.addVertex(pose, x - 0.5F, (float) y - 0.5F, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv((float) u, (float) v)
                .setOverlay(0)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void stringVertex(float dx, float dy, float dz, VertexConsumer vc, PoseStack.Pose pose, float t, float nextT) {
        float x = dx * t;
        // 弛み 0.25 を含むバニラ式: y = dy * (t*t + t) *0.5 +0.25
        float y = dy * (t * t + t) * 0.5F + 0.25F;
        float z = dz * t;
        float nx = dx * nextT - x;
        float ny = dy * (nextT * nextT + nextT) * 0.5F + 0.25F - y;
        float nz = dz * nextT - z;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len != 0) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        vc.addVertex(pose, x, y, z).setColor(0, 0, 0, 255).setNormal(pose, nx, ny, nz);
    }
}
