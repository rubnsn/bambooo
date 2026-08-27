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
 * テクスチャはバニラ水流用、Tint 0xE0F8FF で識別。
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
                        // config 依存にするとクライアント初期化時に spec 未ロードのためデフォルトを使用
                        try {
                            return ruby.bamboo.core.config.SpringConfig.COMMON.tintColor.get();
                        } catch (Exception e) {
                            return 0xE0F8FF;
                        }
                    }
                });
            }
        };
    }
}
