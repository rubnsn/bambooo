package ruby.bamboo.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.BambooMod;

/**
 * 桜の樹木生成用 ConfiguredFeature キー。
 * <p>
 * 旧 GenSakuraTree / GenSakuraBigTree (1.10.2 WorldGenAbstractTree) の移植。
 * <p>
 * 1.20.1の {@code TreeGrower#getConfiguredFeature} は
 * {@link ResourceKey} を返す設計のため、実体は動的レジストリ
 * (data/bamboomod/worldgen/configured_feature/) の JSON で定義する。
 * <ul>
 * <li>sakura: 標準 (幹4+1、葉球 半径2)</li>
 * <li>sakura_big: 大木 (幹6+2、葉球 半径3)</li>
 * </ul>
 */
public final class SakuraTreeFeatures {

    /** 標準の桜 */
    public static final ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> SAKURA = create("sakura");
    /** 大木の桜 */
    public static final ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> SAKURA_BIG = create("sakura_big");

    private SakuraTreeFeatures() {
    }

    private static ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> create(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, name));
    }
}
