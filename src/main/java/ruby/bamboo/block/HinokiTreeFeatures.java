package ruby.bamboo.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;

/**
 * ヒノキ樹木用 ConfiguredFeature キー。
 * 形状はヒノキ (HinokiTreeFeature) 円錐に近い pine/sprite 形状。
 */
public final class HinokiTreeFeatures {

    public static final ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> HINOKI = create("hinoki");
    public static final ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> HINOKI_BIG = create("hinoki_big");

    private HinokiTreeFeatures() {
    }

    private static ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> create(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, name));
    }
}
