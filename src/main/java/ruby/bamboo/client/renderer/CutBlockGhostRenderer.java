package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
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
 * カットブロック設置時のゴースト表示 (3軸絶対, テクスチャなし)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CutBlockGhostRenderer {

    private static final RenderType GHOST_FILL = RenderType.create(
            "cut_ghost_fill",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("translucent_transparency",
                            () -> {
                                RenderSystem.enableBlend();
                                RenderSystem.defaultBlendFunc();
                            },
                            () -> {
                                RenderSystem.disableBlend();
                                RenderSystem.defaultBlendFunc();
                            }))
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("lequal", 515))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setLightmapState(new RenderStateShard.LightmapStateShard(false))
                    .setOverlayState(new RenderStateShard.OverlayStateShard(false))
                    .createCompositeState(false));

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Player player = mc.player;
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
        net.minecraft.world.phys.Vec3 hitVec = bhr.getLocation();
        CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(held);
        if (data.state().isAir()) return;

        CutBlockEntity.Tier tier = CutBlockEntity.getTierFromLevels(data.xLevel(), data.yLevel(), data.zLevel());
        if (tier == CutBlockEntity.Tier.OTHER) return;
        BlockPos ghostPos = null;
        int[] bounds = null;
        // 既存BEへの吸着表示は RAY HIT面で前面(同一座標内空隙)かを判定。全Tier共通
        if (mc.level.getBlockEntity(hitPos) instanceof CutBlockEntity existingBe && !existingBe.isEmpty()) {
            int[] cand = CutBlockEntity.getInsideCandidate(existingBe, hitPos, hitVec, face, tier);
            if (cand != null) {
                ghostPos = hitPos;
                bounds = cand;
            }
        }
        if (ghostPos == null) {
            BlockPos placePosTmp = hitPos.relative(face);
            BlockState placeState = mc.level.getBlockState(placePosTmp);
            boolean canReplace = placeState.canBeReplaced();
            if (mc.level.getBlockEntity(placePosTmp) instanceof CutBlockEntity placeBe && !placeBe.isEmpty()) {
                int[] adjCand = CutBlockEntity.getAdjacentCandidate(placeBe, placePosTmp, hitVec, face, tier);
                if (adjCand != null) {
                    ghostPos = placePosTmp;
                    bounds = adjCand;
                } else {
                    return;
                }
            } else if (mc.level.getBlockEntity(placePosTmp) instanceof CutBlockEntity placeBeEmpty) {
                // 空のcut_blockは新規配置として扱う
                ghostPos = placePosTmp;
                if (tier == CutBlockEntity.Tier.HALF) bounds = CutBlockEntity.computeHalfBounds(hitVec, hitPos, face);
                else { int s = tier == CutBlockEntity.Tier.EIGHT ? 8 : 4; bounds = CutBlockEntity.computeCubeBoundsForNewPlacement(hitVec, hitPos, face, s); }
            } else {
                if (!canReplace) return;
                ghostPos = placePosTmp;
                if (tier == CutBlockEntity.Tier.HALF) bounds = CutBlockEntity.computeHalfBounds(hitVec, hitPos, face);
                else { int s = tier == CutBlockEntity.Tier.EIGHT ? 8 : 4; bounds = CutBlockEntity.computeCubeBoundsForNewPlacement(hitVec, hitPos, face, s); }
            }
        }
        if (ghostPos == null || bounds == null) return;
        BlockPos placePos = ghostPos;
        float minX = bounds[0] / 16f;
        float minY = bounds[1] / 16f;
        float minZ = bounds[2] / 16f;
        float maxX = bounds[3] / 16f;
        float maxY = bounds[4] / 16f;
        float maxZ = bounds[5] / 16f;

        PoseStack poseStack = event.getPoseStack();
        net.minecraft.world.phys.Vec3 cam = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(placePos.getX() - cam.x, placePos.getY() - cam.y, placePos.getZ() - cam.z);

        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        renderTranslucentBox(poseStack, buffer, minX, minY, minZ, maxX, maxY, maxZ);
        renderWireframeBox(poseStack, buffer, minX, minY, minZ, maxX, maxY, maxZ);
        buffer.endBatch(GHOST_FILL);
        buffer.endBatch(RenderType.lines());

        poseStack.popPose();
    }

    private static boolean isCutBlock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            String key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            return key.equals("bamboomod:cut_block");
        } catch (Exception e) {
            return false;
        }
    }

    private static void renderTranslucentBox(PoseStack poseStack, MultiBufferSource buffer,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        VertexConsumer vc = buffer.getBuffer(GHOST_FILL);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normal = pose.normal();
        int r = (int)(0.4f * 255), g = (int)(1.0f * 255), b = (int)(0.4f * 255), a = (int)(0.25f * 255);
        quad(vc, mat, normal, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, 0,-1,0, r,g,b,a);
        quad(vc, mat, normal, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, 0,1,0, r,g,b,a);
        quad(vc, mat, normal, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, 0,0,-1, r,g,b,a);
        quad(vc, mat, normal, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0,0,1, r,g,b,a);
        quad(vc, mat, normal, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, -1,0,0, r,g,b,a);
        quad(vc, mat, normal, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, 1,0,0, r,g,b,a);
    }

    private static void renderWireframeBox(PoseStack poseStack, MultiBufferSource buffer,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normal = pose.normal();
        float r=0.4f,g=1.0f,b=0.4f,a=1.0f;
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
            float nx,float ny,float nz, int r,int g,int b,int a) {
        vc.vertex(mat, x1,y1,z1).color(r,g,b,a).normal(normal, nx,ny,nz).endVertex();
        vc.vertex(mat, x2,y2,z2).color(r,g,b,a).normal(normal, nx,ny,nz).endVertex();
        vc.vertex(mat, x3,y3,z3).color(r,g,b,a).normal(normal, nx,ny,nz).endVertex();
        vc.vertex(mat, x4,y4,z4).color(r,g,b,a).normal(normal, nx,ny,nz).endVertex();
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
