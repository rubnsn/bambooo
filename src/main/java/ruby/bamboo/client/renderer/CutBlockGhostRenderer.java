package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.CutBlock;
import ruby.bamboo.block.entity.CutBlockEntity;

/**
 * カットブロック設置時のゴースト表示。
 * cut_blockを持っているとき、照準先の設置位置にBoundsサイズの半透明Boxを表示する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CutBlockGhostRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Player player = mc.player;
        // 手持ちチェック: メインハンドまたはオフハンドにcut_block
        ItemStack held = player.getMainHandItem();
        if (!isCutBlock(held)) {
            held = player.getOffhandItem();
            if (!isCutBlock(held)) return;
        }
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) return;
        if (bhr.getType() != HitResult.Type.BLOCK) return;
        BlockPos hitPos = bhr.getBlockPos();
        Direction face = bhr.getDirection();
        BlockPos placePos = hitPos.relative(face);
        // 設置可能か簡易チェック
        BlockState placeState = mc.level.getBlockState(placePos);
        if (!placeState.canBeReplaced()) return;

        // heldからBoundsを取得
        CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(held);
        if (data.state().isAir()) return; // 空のcut_blockはゴーストなし
        // FACINGはプレイヤー向きから決定（CutBlock#getStateForPlacementと同様）
        Direction facing = player.getDirection().getOpposite();
        // 向きが水平でない場合はNORTH
        if (facing.getAxis() == Direction.Axis.Y) facing = Direction.NORTH;
        int[] bounds = computeBounds(data.yLevel(), data.hLevel(), facing);
        float minX = bounds[0] / 16f;
        float minY = bounds[1] / 16f;
        float minZ = bounds[2] / 16f;
        float maxX = bounds[3] / 16f;
        float maxY = bounds[4] / 16f;
        float maxZ = bounds[5] / 16f;

        // レンダリング
        PoseStack poseStack = event.getPoseStack();
        // カメラ位置でオフセット
        net.minecraft.world.phys.Vec3 cam = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(placePos.getX() - cam.x, placePos.getY() - cam.y, placePos.getZ() - cam.z);

        // 半透明Box + ワイヤーフレーム
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        // 半透明キューブ
        renderTranslucentBox(poseStack, buffer, minX, minY, minZ, maxX, maxY, maxZ);
        // ワイヤーフレーム
        renderWireframeBox(poseStack, buffer, minX, minY, minZ, maxX, maxY, maxZ);
        // flush
        buffer.endBatch(RenderType.translucent());
        buffer.endBatch(RenderType.lines());

        poseStack.popPose();
    }

    private static boolean isCutBlock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            // 登録名で判定（初期化前でも安全）
            String key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            return key.equals("bamboomod:cut_block");
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] computeBounds(byte yLevel, byte hLevel, Direction facing) {
        int ySize = CutBlockEntity.levelToSize(yLevel);
        int hSize = CutBlockEntity.levelToSize(hLevel);
        int minX = 0, minY = 0, minZ = 0;
        int maxX = 16, maxY = ySize, maxZ = 16;
        if (hSize != 16) {
            switch (facing) {
                case NORTH -> {
                    maxX = hSize;
                    maxZ = 16;
                }
                case SOUTH -> {
                    minX = 16 - hSize;
                    maxX = 16;
                    maxZ = 16;
                }
                case EAST -> {
                    maxX = 16;
                    maxZ = hSize;
                }
                case WEST -> {
                    minZ = 16 - hSize;
                    maxX = 16;
                    maxZ = 16;
                }
                default -> {
                    maxX = hSize;
                    maxZ = 16;
                }
            }
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static void renderTranslucentBox(PoseStack poseStack, MultiBufferSource buffer,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        VertexConsumer vc = buffer.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normal = pose.normal();
        float r = 0.4f, g = 1.0f, b = 0.4f, a = 0.25f;
        int ri = (int)(r*255), gi = (int)(g*255), bi = (int)(b*255), ai = (int)(a*255);
        int light = 0xF000F0;
        int overlay = 0;
        // 下面
        quad(vc, mat, normal, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, 0,-1,0, ri,gi,bi,ai, light, overlay);
        // 上面
        quad(vc, mat, normal, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, 0,1,0, ri,gi,bi,ai, light, overlay);
        // 北
        quad(vc, mat, normal, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, 0,0,-1, ri,gi,bi,ai, light, overlay);
        // 南
        quad(vc, mat, normal, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0,0,1, ri,gi,bi,ai, light, overlay);
        // 西（裏面カリング修正）
        quad(vc, mat, normal, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, -1,0,0, ri,gi,bi,ai, light, overlay);
        // 東（裏面カリング修正）
        quad(vc, mat, normal, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, 1,0,0, ri,gi,bi,ai, light, overlay);
    }

    private static void renderWireframeBox(PoseStack poseStack, MultiBufferSource buffer,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normal = pose.normal();
        float r=0.4f,g=1.0f,b=0.4f,a=1.0f;
        // 12辺
        line(vc, mat, normal, minX,minY,minZ, maxX,minY,minZ, r,g,b,a);
        line(vc, mat, normal, maxX,minY,minZ, maxX,minY,maxZ, r,g,b,a);
        line(vc, mat, normal, maxX,minY,maxZ, minX,minY,maxZ, r,g,b,a);
        line(vc, mat, normal, minX,minY,maxZ, minX,minY,minZ, r,g,b,a);
        line(vc, mat, normal, minX,maxY,minZ, maxX,maxY,minZ, r,g,b,a);
        line(vc, mat, normal, maxX,maxY,minZ, maxX,maxY,maxZ, r,g,b,a);
        line(vc, mat, normal, maxX,maxY,maxZ, minX,maxY,maxZ, r,g,b,a);
        line(vc, mat, normal, minX,maxY,maxZ, minX,maxY,minZ, r,g,b,a);
        line(vc, mat, normal, minX,minY,minZ, minX,maxY,minZ, r,g,b,a);
        line(vc, mat, normal, maxX,minY,minZ, maxX,maxY,minZ, r,g,b,a);
        line(vc, mat, normal, maxX,minY,maxZ, maxX,maxY,maxZ, r,g,b,a);
        line(vc, mat, normal, minX,minY,maxZ, minX,maxY,maxZ, r,g,b,a);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, Matrix3f normal,
            float x1,float y1,float z1, float x2,float y2,float z2, float x3,float y3,float z3, float x4,float y4,float z4,
            float nx,float ny,float nz, int r,int g,int b,int a, int light,int overlay) {
        vc.vertex(mat, x1,y1,z1).color(r,g,b,a).uv(0,0).overlayCoords(overlay).uv2(light).normal(normal, nx,ny,nz).endVertex();
        vc.vertex(mat, x2,y2,z2).color(r,g,b,a).uv(1,0).overlayCoords(overlay).uv2(light).normal(normal, nx,ny,nz).endVertex();
        vc.vertex(mat, x3,y3,z3).color(r,g,b,a).uv(1,1).overlayCoords(overlay).uv2(light).normal(normal, nx,ny,nz).endVertex();
        vc.vertex(mat, x4,y4,z4).color(r,g,b,a).uv(0,1).overlayCoords(overlay).uv2(light).normal(normal, nx,ny,nz).endVertex();
    }

    private static void line(VertexConsumer vc, Matrix4f mat, Matrix3f normal,
            float x1,float y1,float z1, float x2,float y2,float z2, float r,float g,float b,float a) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if (len > 1e-6) { nx/=len; ny/=len; nz/=len; }
        vc.vertex(mat, x1,y1,z1).color(r,g,b,a).normal(normal, nx,ny,nz).endVertex();
        vc.vertex(mat, x2,y2,z2).color(r,g,b,a).normal(normal, nx,ny,nz).endVertex();
    }
}
