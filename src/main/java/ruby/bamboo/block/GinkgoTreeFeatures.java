package ruby.bamboo.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;

/**
 * イチョウ樹木用 ConfiguredFeature キー。
 */
public final class GinkgoTreeFeatures {

    public static final ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> GINKGO = create("ginkgo");
    public static final ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> GINKGO_BIG = create("ginkgo_big");

    private GinkgoTreeFeatures() {
    }

    private static ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> create(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, name));
    }
}
