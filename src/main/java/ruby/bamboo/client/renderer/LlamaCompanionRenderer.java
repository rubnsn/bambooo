package ruby.bamboo.client.renderer;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LlamaRenderer;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.entity.companion.LlamaCompanionEntity;

/**
 * ラマ仲間レンダラー - バニラ LlamaRenderer を流用。
 */
public class LlamaCompanionRenderer extends LlamaRenderer {

    public LlamaCompanionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, ModelLayers.LLAMA);
    }

    @Override
    public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.horse.Llama entity) {
        return super.getTextureLocation(entity);
    }
}
