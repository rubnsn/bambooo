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
 * <p>
 * 1.21.1 (NeoForge): 描画パイプラインは (…, ModelData, RenderType) 付き12引数版を使うため、
 * そちらに明示記述子で注入する (10引数版は12引数版への委譲のみ)。
 * putBulkData は alpha 引数追加で記述子が変わったため新記述子を指定する。
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {

    @Unique
    private static final ThreadLocal<Vector3f> bamboomod$currentTint = ThreadLocal.withInitial(() -> new Vector3f(1, 1, 1));

    private static final String TESSELATE_12 = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V";

    @Inject(method = TESSELATE_12, at = @At("HEAD"), remap = false)
    private void bamboomod$setTint(BlockAndTintGetter level, net.minecraft.client.resources.model.BakedModel model, net.minecraft.world.level.block.state.BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack stack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int overlay, net.neoforged.neoforge.client.model.data.ModelData data, net.minecraft.client.renderer.RenderType type, CallbackInfo ci) {
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

    @Inject(method = TESSELATE_12, at = @At("RETURN"), remap = false)
    private void bamboomod$clearTint(BlockAndTintGetter level, net.minecraft.client.resources.model.BakedModel model, net.minecraft.world.level.block.state.BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack stack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int overlay, net.neoforged.neoforge.client.model.data.ModelData data, net.minecraft.client.renderer.RenderType type, CallbackInfo ci) {
        bamboomod$currentTint.remove();
    }

    /** 1.21 putBulkData 記述子 (alpha 引数あり)。r/g/b の引数位置 3/4/5 は不変。 */
    private static final String PUT_BULK_DATA = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V";

    // putQuadData 内の putBulkData 呼び出しで blockColor (r/g/b) を tint で乗算する。
    // 以前の argsOnly ModifyVariable は brightness[vertex] (per-vertex AO 0.6-1) をチャネル別に乗算していたため端が黒く、色が分離されていなかった。
    // 正しくは putBulkData の r/g/b (blockColors 由来の1,1,1や草の緑) を乗算する。
    @ModifyArg(method = "putQuadData", remap = false, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"), index = 3)
    private float bamboomod$modifyBlockR(float r) {
        Vector3f tint = bamboomod$currentTint.get();
        if (tint == null || (tint.x == 1 && tint.y == 1 && tint.z == 1)) return r;
        return r * tint.x();
    }

    @ModifyArg(method = "putQuadData", remap = false, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"), index = 4)
    private float bamboomod$modifyBlockG(float g) {
        Vector3f tint = bamboomod$currentTint.get();
        if (tint == null || (tint.x == 1 && tint.y == 1 && tint.z == 1)) return g;
        return g * tint.y();
    }

    @ModifyArg(method = "putQuadData", remap = false, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"), index = 5)
    private float bamboomod$modifyBlockB(float b) {
        Vector3f tint = bamboomod$currentTint.get();
        if (tint == null || (tint.x == 1 && tint.y == 1 && tint.z == 1)) return b;
        return b * tint.z();
    }
}
