package ruby.bamboo.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import ruby.bamboo.BambooMod;

/**
 * カエデ樹木用 ConfiguredFeature キー (sakura準拠 Blob)。
 */
public final class MapleTreeFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> MAPLE = create("maple");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MAPLE_BIG = create("maple_big");

    private MapleTreeFeatures() {
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> create(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                new ResourceLocation(BambooMod.MODID, name));
    }
}
