package ruby.bamboo.mixin.vanilla;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ruby.bamboo.util.ColoredLightUtil;

/**
 * B単パス: バニラ ModelBlockRenderer.putQuadData で頂点色を tint で乗算。
 * tesselateBlock で BlockPos 単位の tint を ThreadLocal に保持し、putQuadData で r/g/b に乗算する。
 * これにより松明(白)と紫床(紫)が分離される。
 * Forge 1.20.1 正規 Mixin (iTrooz準拠): remap デフォルト(true)、MixinGradle の config で refmap 生成。
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {

    @Unique
    private static final ThreadLocal<Vector3f> bamboomod$currentTint = ThreadLocal.withInitial(() -> new Vector3f(1, 1, 1));

    @Inject(method = "tesselateBlock", at = @At("HEAD"))
    private void bamboomod$setTint(BlockAndTintGetter level, net.minecraft.client.resources.model.BakedModel model, net.minecraft.world.level.block.state.BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack stack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int overlay, CallbackInfo ci) {
        if (level == null || pos == null) {
            bamboomod$currentTint.set(new Vector3f(1, 1, 1));
            return;
        }
        try {
            Vector3f tint = ColoredLightUtil.getTint(pos, level);
            bamboomod$currentTint.set(tint);
        } catch (Exception e) {
            bamboomod$currentTint.set(new Vector3f(1, 1, 1));
        }
    }

    @Inject(method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V", at = @At("HEAD"), remap = false)
    private void bamboomod$setTint2(BlockAndTintGetter level, net.minecraft.client.resources.model.BakedModel model, net.minecraft.world.level.block.state.BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack stack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int overlay, net.minecraftforge.client.model.data.ModelData data, net.minecraft.client.renderer.RenderType type, CallbackInfo ci) {
        if (level == null || pos == null) {
            bamboomod$currentTint.set(new Vector3f(1, 1, 1));
            return;
        }
        try {
            Vector3f tint = ColoredLightUtil.getTint(pos, level);
            bamboomod$currentTint.set(tint);
        } catch (Exception e) {
            bamboomod$currentTint.set(new Vector3f(1, 1, 1));
        }
    }

    @Inject(method = "tesselateBlock", at = @At("RETURN"))
    private void bamboomod$clearTint(BlockAndTintGetter level, net.minecraft.client.resources.model.BakedModel model, net.minecraft.world.level.block.state.BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack stack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int overlay, CallbackInfo ci) {
        bamboomod$currentTint.remove();
    }

    @Inject(method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V", at = @At("RETURN"), remap = false)
    private void bamboomod$clearTint2(BlockAndTintGetter level, net.minecraft.client.resources.model.BakedModel model, net.minecraft.world.level.block.state.BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack stack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int overlay, net.minecraftforge.client.model.data.ModelData data, net.minecraft.client.renderer.RenderType type, CallbackInfo ci) {
        bamboomod$currentTint.remove();
    }

    // putQuadData 内の putBulkData 呼び出しで blockColor (r/g/b) を tint で乗算する。
    // 以前の argsOnly ModifyVariable は brightness[vertex] (per-vertex AO 0.6-1) をチャネル別に乗算していたため端が黒く、色が分離されていなかった。
    // 正しくは putBulkData の r/g/b (blockColors 由来の1,1,1や草の緑) を乗算する。
    @ModifyArg(method = "putQuadData", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFF[I IZ)V"), index = 3)
    private float bamboomod$modifyBlockR(float r) {
        Vector3f tint = bamboomod$currentTint.get();
        if (tint == null || (tint.x == 1 && tint.y == 1 && tint.z == 1)) return r;
        return r * tint.x();
    }

    @ModifyArg(method = "putQuadData", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFF[I IZ)V"), index = 4)
    private float bamboomod$modifyBlockG(float g) {
        Vector3f tint = bamboomod$currentTint.get();
        if (tint == null || (tint.x == 1 && tint.y == 1 && tint.z == 1)) return g;
        return g * tint.y();
    }

    @ModifyArg(method = "putQuadData", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFF[I IZ)V"), index = 5)
    private float bamboomod$modifyBlockB(float b) {
        Vector3f tint = bamboomod$currentTint.get();
        if (tint == null || (tint.x == 1 && tint.y == 1 && tint.z == 1)) return b;
        return b * tint.z();
    }
}
