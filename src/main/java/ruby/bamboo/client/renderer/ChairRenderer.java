package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.ChairEntity;

/**
 * Chair (huton_chair) 用レンダラ。不可視椅子のため何も描画しない。
 * レンダラ未登録だと EntityRenderDispatcher.shouldRender で NPEが発生するため
 * ダミーレンダラを必ず登録する必要がある。
 */
public class ChairRenderer extends EntityRenderer<ChairEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    public ChairRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ChairEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ChairEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        // 何も描画しない (不可視)
    }

    @Override
    public boolean shouldRender(ChairEntity entity, Frustum camera, double camX, double camY, double camZ) {
        // 表示不要だが判定自体は通す必要があるため true でも描画は render でスキップ
        // false を返すとプレイヤー乗車時の視点更新等に影響する可能性があるため
        // 親の判定を尊重しつつ、距離が極端に遠い場合のみ cull
        return super.shouldRender(entity, camera, camX, camY, camZ);
    }
}
