package ruby.bamboo.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;

/**
 * カエデ樹木用 ConfiguredFeature キー (sakura準拠 Blob)。
 */
public final class MapleTreeFeatures {

    public static final ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> MAPLE = create("maple");
    public static final ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> MAPLE_BIG = create("maple_big");

    private MapleTreeFeatures() {
    }

    private static ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> create(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, name));
    }
}
