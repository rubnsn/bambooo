package ruby.bamboo.mixin.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 自作バイオームの上世界配置 (TerraBlender 不使用)。
 * 上世界パラメータ表はコード生成 ({@code OverworldBiomeBuilder.addBiomes} 経由。
 * datapack の overworld.json は preset 選択のみ) のため、Forge 単体では注入口がない。
 * ここで FOREST 点の 1/3 を autumn_forest に付け替える (TerraBlender の addModifiedVanillaBiomes 相当)。
 * 実体 (autumn_forest.json) は datapack 側で解決される。
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderMixin {
    @Unique
    private static final Logger bamboomod$LOGGER = LoggerFactory.getLogger("BambooMod/BiomeMixin");
    @Unique
    private static final ResourceKey<Biome> bamboomod$AUTUMN_FOREST =
            ResourceKey.create(Registries.BIOME, new ResourceLocation("bamboomod", "autumn_forest"));
    @Unique
    private static boolean bamboomod$logged;
    @Unique
    private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> bamboomod$original;
    @Unique
    private List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> bamboomod$recorded;

    @SuppressWarnings("unchecked")
    @ModifyVariable(method = "addBiomes", at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false)
    private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> bamboomod$record(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer) {
        this.bamboomod$original = consumer;
        this.bamboomod$recorded = new ArrayList<>();
        return pair -> {
            this.bamboomod$recorded.add(pair);
            consumer.accept(pair);
        };
    }

    @Inject(method = "addBiomes", at = @At("TAIL"), remap = false)
    private void bamboomod$appendAutumn(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer, CallbackInfo ci) {
        if (!bamboomod$logged) {
            bamboomod$LOGGER.info("[BambooMod] OverworldBiomeBuilderMixin alive: remapping FOREST points to autumn_forest");
            bamboomod$logged = true;
        }
        if (this.bamboomod$recorded == null || this.bamboomod$original == null) return;
        // 飛び飛びに置換するとモザイク化するため、前方 1/3 (低温側に偏る) を面的に置換する
        int forestTotal = 0;
        for (Pair<Climate.ParameterPoint, ResourceKey<Biome>> pair : this.bamboomod$recorded) {
            if (pair.getSecond().equals(Biomes.FOREST)) forestTotal++;
        }
        int quota = forestTotal / 3;
        int forestIndex = 0;
        int remapped = 0;
        for (Pair<Climate.ParameterPoint, ResourceKey<Biome>> pair : this.bamboomod$recorded) {
            if (pair.getSecond().equals(Biomes.FOREST)) {
                if (forestIndex < quota) {
                    this.bamboomod$original.accept(Pair.of(pair.getFirst(), bamboomod$AUTUMN_FOREST));
                    remapped++;
                }
                forestIndex++;
            }
        }
        bamboomod$LOGGER.info("[BambooMod] autumn_forest remapped {}/{} FOREST points", remapped, forestIndex);
        this.bamboomod$recorded = null;
        this.bamboomod$original = null;
    }
}
