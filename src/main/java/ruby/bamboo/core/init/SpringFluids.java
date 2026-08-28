package ruby.bamboo.core.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import ruby.bamboo.BambooMod;
import java.util.function.Consumer;

/**
 * 温泉流体定義 — 1.20.1 ForgeFlowingFluid。
 * テクスチャはバニラ水流用、Tint はバイオーム水色×config乗算 (森林等の通常水色が初期値)。
 * QA柔軟構造: BEカスタム色があればそれを優先し、隣接ブレンドは BlockColor 側で実現。
 */
public final class SpringFluids {

    private SpringFluids() {}

    public static ForgeFlowingFluid.Properties props() {
        return new ForgeFlowingFluid.Properties(
                () -> BambooMod.HOT_SPRING_TYPE.get(),
                () -> BambooMod.SPRING_WATER_SOURCE.get(),
                () -> BambooMod.SPRING_WATER_FLOWING.get())
                .block(() -> (net.minecraft.world.level.block.LiquidBlock) BambooBlocks.SPRING_WATER.get())
                .bucket(() -> Items.WATER_BUCKET)
                .slopeFindDistance(1)
                .levelDecreasePerBlock(1)
                .tickRate(20);
    }

    /** バイオーム水色×tint の乗算 (BlockColor と同ロジック) */
    public static int multiplyColor(int biome, int tint) {
        int r1 = (biome >> 16) & 0xFF, g1 = (biome >> 8) & 0xFF, b1 = biome & 0xFF;
        int r2 = (tint >> 16) & 0xFF, g2 = (tint >> 8) & 0xFF, b2 = tint & 0xFF;
        int r = r1 * r2 / 255;
        int g = g1 * g2 / 255;
        int b = b1 * b2 / 255;
        return (r << 16) | (g << 8) | b;
    }

    public static FluidType createHotSpringType() {
        FluidType.Properties p = FluidType.Properties.create();
        p.canSwim(true);
        p.canDrown(false);
        p.supportsBoating(true);
        p.density(1000);
        p.viscosity(1000);
        p.temperature(300);
        return new FluidType(p) {
            @Override
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return new ResourceLocation("block/water_still");
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return new ResourceLocation("block/water_flow");
                    }

                    @Override
                    public int getTintColor() {
                        int tint;
                        try { tint = ruby.bamboo.core.config.SpringConfig.COMMON.tintColor.get(); } catch (Exception e){ tint = 0xE0F8FF; }
                        return multiplyColor(ruby.bamboo.block.SpringColor.DEFAULT.color, tint);
                    }

                    @Override
                    public int getTintColor(net.minecraft.world.level.material.FluidState state,
                                            net.minecraft.world.level.BlockAndTintGetter getter,
                                            net.minecraft.core.BlockPos pos) {
                        if (getter != null && pos != null && getter instanceof net.minecraft.world.level.Level lvl) {
                            try {
                                var st = lvl.getBlockState(pos);
                                if (st.getBlock() instanceof ruby.bamboo.block.SpringWaterBlock) {
                                    var src = ruby.bamboo.block.SpringWaterBlock.findSource(lvl, pos, st, 32);
                                    int base = ruby.bamboo.block.SpringColor.DEFAULT.color;
                                    if (src != null) {
                                        var srcSt = lvl.getBlockState(src);
                                        if (srcSt.getBlock() instanceof ruby.bamboo.block.SpringBlock) base = srcSt.getValue(ruby.bamboo.block.SpringBlock.COLOR).color;
                                    }
                                    int tint;
                                    try { tint = ruby.bamboo.core.config.SpringConfig.COMMON.tintColor.get(); } catch (Exception e){ tint = 0xE0F8FF; }
                                    return multiplyColor(base, tint);
                                }
                            } catch (Exception e) {}
                        }
                        return getTintColor();
                    }
                });
            }
        };
    }
}
