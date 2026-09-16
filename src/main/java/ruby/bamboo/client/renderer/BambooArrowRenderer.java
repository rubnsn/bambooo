package ruby.bamboo.client.renderer;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import ruby.bamboo.BambooMod;

/**
 * 竹弓用の各種矢 (竹矢/松明矢/光矢/爆発矢) 共通レンダラ。
 * <p>
 * 旧 1.10.2 の RenderBambooArrow (textures/entitys/bamboospear.png) 相当。
 * ArrowRenderer の描画ロジックを使い、テクスチャのみ差し替える。
 */
public class BambooArrowRenderer extends ArrowRenderer<AbstractArrow> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "textures/entity/bamboospear.png");

    public BambooArrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractArrow arrow) {
        return TEXTURE;
    }
}