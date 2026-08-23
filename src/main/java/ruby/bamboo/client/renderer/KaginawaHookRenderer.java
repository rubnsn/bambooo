package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.KaginawaHookEntity;

/**
 * 鈎縄フックレンダラ — 太線quad帯 + フック本体(小cube)。
 * FishingHookRendererの細線ではなく、遠距離(30m)でも視認できる幅0.06の帯を描画。
 */
public class KaginawaHookRenderer extends EntityRenderer<KaginawaHookEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(BambooMod.MODID, "textures/entity/kaginawa_hook.png");
    private static final ResourceLocation BEAM_TEXTURE = new ResourceLocation("minecraft", "textures/misc/white.png");
    // 麻縄色 0xC8B898
    private static final int ROPE_R = 0xC8;
    private static final int ROPE_G = 0xB8;
    private static final int ROPE_B = 0x98;
    private static final int ROPE_A = 255;

    // 暫定: RenderTypeはentityTranslucent相当の半透明帯。幅はジオメトリで出すためline系でなくtriangle系
    private static final RenderType ROPE_RENDER_TYPE = RenderType.entityTranslucent(BEAM_TEXTURE);

    public KaginawaHookRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(KaginawaHookEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(KaginawaHookEntity entity, Frustum camera, double camX, double camY, double camZ) {
        // 常に描画 (固着時は遠距離でも必要)
        return true;
    }

    @Override
    public void render(KaginawaHookEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {

        // 1. フック本体: 仮としてスライムボールを表示 (要望: 箱の代わりに借り)
        poseStack.pushPose();
        poseStack.translate(0, 0.15, 0);
        poseStack.scale(0.8F, 0.8F, 0.8F);
        // カメラに対してビルボード的に回転させると見やすい
        // ただし GROUND 表示で十分視認できるため追加回転なし
        try {
            net.minecraft.world.item.ItemStack slime = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SLIME_BALL);
            Minecraft.getInstance().getItemRenderer().renderStatic(slime, net.minecraft.world.item.ItemDisplayContext.GROUND, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), 0);
        } catch (Exception e) {
            // フォールバック: 旧cube
            renderHookCube(poseStack, buffer, packedLight);
        }
        poseStack.popPose();

        // 2. ロープ: プレイヤー眼→アンカー
        Player owner = entity.getOwnerPlayer();
        if (owner == null && Minecraft.getInstance().player != null) {
            // フォールバック: シングルプレイでowner同期が遅れた場合、自分のフックとみなす
            Player me = Minecraft.getInstance().player;
            if (entity.distanceTo(me) < 32) {
                owner = me;
            }
        }
        if (owner != null) {
            Vec3 anchor = entity.getAnchor();
            // 部分tick補間
            Vec3 eye = getPlayerEyePos(owner, partialTick);
            renderRope(eye, anchor, poseStack, buffer, packedLight);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private Vec3 getPlayerEyePos(Player player, float partialTick) {
        double x = Mth.lerp(partialTick, player.xo, player.getX());
        double y = Mth.lerp(partialTick, player.yo, player.getY()) + player.getEyeHeight() * 0.5;
        double z = Mth.lerp(partialTick, player.zo, player.getZ());
        // 手の位置に少しオフセット: look方向に0.3
        // 釣り竿は手からだが、鈎縄は刀からなので eye で十分
        return new Vec3(x, y, z);
    }

    private void renderHookCube(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normal = pose.normal();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        float s = 0.15F;
        // 6面のcube (Y+側を少し長く)
        // 前後左右上下
        // 簡易: 2つの四角で十字に見せる (ビルボードではなく固定)
        // ここでは簡易box: 8頂点から6面
        // 頂点: (-s, -s, -s) .. (s, s, s)
        // 面ごとに法線とUV
        // 下面 normal 0,-1,0
        addQuad(vc, mat, normal, -s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s, 0, -1, 0, packedLight);
        // 上面 0,1,0
        addQuad(vc, mat, normal, -s, s, -s, -s, s, s, s, s, s, s, s, -s, 0, 1, 0, packedLight);
        // 北 0,0,-1
        addQuad(vc, mat, normal, -s, -s, -s, -s, s, -s, s, s, -s, s, -s, -s, 0, 0, -1, packedLight);
        // 南 0,0,1
        addQuad(vc, mat, normal, -s, -s, s, s, -s, s, s, s, s, -s, s, s, 0, 0, 1, packedLight);
        // 西 -1,0,0
        addQuad(vc, mat, normal, -s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s, -1, 0, 0, packedLight);
        // 東 1,0,0
        addQuad(vc, mat, normal, s, -s, -s, s, s, -s, s, s, s, s, -s, s, 1, 0, 0, packedLight);
    }

    private void addQuad(VertexConsumer vc, Matrix4f mat, Matrix3f normal,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         float x4, float y4, float z4,
                         float nx, float ny, float nz,
                         int packedLight) {
        vc.vertex(mat, x1, y1, z1).color(255, 255, 255, 255).uv(0, 0).overlayCoords(0).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x2, y2, z2).color(255, 255, 255, 255).uv(1, 0).overlayCoords(0).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x3, y3, z3).color(255, 255, 255, 255).uv(1, 1).overlayCoords(0).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x4, y4, z4).color(255, 255, 255, 255).uv(0, 1).overlayCoords(0).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
    }

    private void renderRope(Vec3 start, Vec3 end, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // スタートからエンドへのベクトル
        Vec3 diff = end.subtract(start);
        double length = diff.length();
        if (length < 0.1) {
            return;
        }
        Vec3 dir = diff.normalize();
        // カメラの右ベクトルで帯の幅を出す
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camRight = new Vec3(cam.getLookVector().z, 0, -cam.getLookVector().x);
        // カメラが真上を向いている場合は別軸
        if (camRight.lengthSqr() < 1e-6) {
            camRight = new Vec3(1, 0, 0);
        } else {
            camRight = camRight.normalize();
        }
        // dirとcamRightの外積で帯の法線に近い幅方向を算出: 帯はカメラに正対させる (ビルボード帯)
        // 簡易: 帯の幅ベクトル = camRight * width/2 (カメラの水平右) ではなく、dirに垂直かつカメラに向くベクトル
        // 正確には dir cross camDir の正規化 * width
        Vec3 camDir = new Vec3(cam.getLookVector().x, cam.getLookVector().y, cam.getLookVector().z);
        Vec3 widthDir = dir.cross(camDir);
        if (widthDir.lengthSqr() < 1e-6) {
            widthDir = camRight;
        } else {
            widthDir = widthDir.normalize();
        }
        float width = 0.06F; // 6cm 太線
        Vec3 offset = widthDir.scale(width * 0.5);

        // ワールド座標を PoseStack に変換: entityは既に anchor にいるが、poseStackは entity位置が原点
        // startはワールド座標、endはanchor (=entity位置)。poseStackはentity位置を原点にしているため、
        // startを entity相対座標に変換する必要がある。
        // render()は entity位置で poseStack が translate された状態で呼ばれるわけではない (EntityRendererがtranslateする前か後か要確認)
        // 1.20.1 EntityRenderer.render は呼び出し側で poseStack.translate(entityPos - camPos) されている
        // そのため、ワールド座標を直接使わず、相対座標に変換:
        // startRelative = start - entity.position()
        // endRelative = end - entity.position() = 0
        Vec3 entityPos = end; // anchor == entity pos
        Vec3 startRel = start.subtract(entityPos);
        Vec3 endRel = Vec3.ZERO;

        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f norm = pose.normal();
        VertexConsumer vc = buffer.getBuffer(ROPE_RENDER_TYPE);

        // 帯の4頂点: startRel±offset, endRel±offset
        Vec3 p1 = startRel.add(offset);
        Vec3 p2 = startRel.subtract(offset);
        Vec3 p3 = endRel.subtract(offset);
        Vec3 p4 = endRel.add(offset);

        // 法線はカメラ向き
        float nx = (float) camDir.x;
        float ny = (float) camDir.y;
        float nz = (float) camDir.z;

        // 単一quad帯: 2三角形 (4頂点)
        vc.vertex(mat, (float) p1.x, (float) p1.y, (float) p1.z).color(ROPE_R, ROPE_G, ROPE_B, ROPE_A).uv(0, 0).overlayCoords(0).uv2(packedLight).normal(norm, nx, ny, nz).endVertex();
        vc.vertex(mat, (float) p2.x, (float) p2.y, (float) p2.z).color(ROPE_R, ROPE_G, ROPE_B, ROPE_A).uv(1, 0).overlayCoords(0).uv2(packedLight).normal(norm, nx, ny, nz).endVertex();
        vc.vertex(mat, (float) p3.x, (float) p3.y, (float) p3.z).color(ROPE_R, ROPE_G, ROPE_B, ROPE_A).uv(1, 1).overlayCoords(0).uv2(packedLight).normal(norm, nx, ny, nz).endVertex();
        vc.vertex(mat, (float) p4.x, (float) p4.y, (float) p4.z).color(ROPE_R, ROPE_G, ROPE_B, ROPE_A).uv(0, 1).overlayCoords(0).uv2(packedLight).normal(norm, nx, ny, nz).endVertex();
    }
}
