package ruby.bamboo.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.entity.WindEntity;

/**
 * 風エンティティ用レンダラ。旧 Wind は描画なしだが、Renderer 未登録だと
 * クライアントで警告が出るため、不可視のダミーレンダラを登録する。
 */
public class WindRenderer extends EntityRenderer<WindEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    public WindRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(WindEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(WindEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        // 何も描画しない (不可視)
    }

    @Override
    public boolean shouldRender(WindEntity entity, Frustum camera, double camX, double camY, double camZ) {
        // そもそも見えないので cull してよいが、将来パーティクル等のデバッグ描画を追加する
        // 可能性があるため親の距離判定を尊重
        return false;
    }
}
