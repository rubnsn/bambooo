package ruby.bamboo.core.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import ruby.bamboo.BambooMod;
import java.util.function.Consumer;

/**
 * 温泉流体定義 — 1.20.1 ForgeFlowingFluid。
 * テクスチャはバニラ水流用、Tint はバイオーム水色×config乗算 (森林等の通常水色が初期値)。
 * QA柔軟構造: BEカスタム色があればそれを優先し、隣接ブレンドは BlockColor 側で実現。
 */
public final class SpringFluids {

    private SpringFluids() {}

    public static BaseFlowingFluid.Properties props() {
        return new BaseFlowingFluid.Properties(
                () -> BambooMod.HOT_SPRING_TYPE.get(),
                () -> BambooMod.SPRING_WATER_SOURCE.get(),
                () -> BambooMod.SPRING_WATER_FLOWING.get())
                .block(() -> (net.minecraft.world.level.block.LiquidBlock) BambooBlocks.SPRING_WATER.get())
                .bucket(() -> Items.WATER_BUCKET)
                .slopeFindDistance(1)
                .levelDecreasePerBlock(1)
                .tickRate(20);
    }

    /** バイオーム水色×tint の乗算 (BlockColor と同ロジック) — 戻りは ARGB(FF固定)で不透明 */
    public static int multiplyColor(int biome, int tint) {
        int r1 = (biome >> 16) & 0xFF, g1 = (biome >> 8) & 0xFF, b1 = biome & 0xFF;
        int r2 = (tint >> 16) & 0xFF, g2 = (tint >> 8) & 0xFF, b2 = tint & 0xFF;
        int r = r1 * r2 / 255;
        int g = g1 * g2 / 255;
        int b = b1 * b2 / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
                        return ResourceLocation.withDefaultNamespace("block/water_still");
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return ResourceLocation.withDefaultNamespace("block/water_flow");
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
                        if (getter != null && pos != null) {
                            try {
                                // sakura 1.16.5 HotSpring.HotWater.getColor 相当: biomeBlendRadius で周囲を平均
                                int blend = 2;
                                try { blend = Math.min(net.minecraft.client.Minecraft.getInstance().options.biomeBlendRadius().get(), 2); } catch (Exception e) { blend = 1; }
                                int rSum = 0, gSum = 0, bSum = 0, cnt = 0;
                                net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
                                for (int dx = -blend; dx <= blend; dx++) {
                                    for (int dz = -blend; dz <= blend; dz++) {
                                        m.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
                                        var f = getter.getFluidState(m);
                                        boolean isSpring = f.getType() == BambooMod.SPRING_WATER_SOURCE.get() || f.getType() == BambooMod.SPRING_WATER_FLOWING.get();
                                        // 水没の場合は block が spring_water でなくても fluid が spring なら対象
                                        if (!isSpring) continue;
                                        var st = getter.getBlockState(m);
                                        int col;
                                        if (st.getBlock() instanceof ruby.bamboo.block.SpringWaterBlock) {
                                            var src = ruby.bamboo.block.SpringWaterBlock.findSource(getter, m, st, 32);
                                            int base = ruby.bamboo.block.SpringColor.DEFAULT.color;
                                            if (src != null) {
                                                var srcSt = getter.getBlockState(src);
                                                if (srcSt.getBlock() instanceof ruby.bamboo.block.SpringBlock) base = srcSt.getValue(ruby.bamboo.block.SpringBlock.COLOR).color;
                                            }
                                            // 染料色は tint で薄めず鮮やかに、DEFAULT のみ tint 乗算
                                            if (base != ruby.bamboo.block.SpringColor.DEFAULT.color && base != ruby.bamboo.block.SpringColor.VANILLA.color) {
                                                col = 0xFF000000 | base;
                                            } else {
                                                int tint;
                                                try { tint = ruby.bamboo.core.config.SpringConfig.COMMON.tintColor.get(); } catch (Exception ex){ tint = 0xE0F8FF; }
                                                col = multiplyColor(base, tint);
                                            }
                                        } else {
                                            // 水没など block が spring_water でない場合は近傍の spring_water ブロックから色を借用
                                            col = resolveWaterloggedColor(getter, m);
                                            if (col == 0) continue;
                                        }
                                        rSum += (col >> 16) & 0xFF;
                                        gSum += (col >> 8) & 0xFF;
                                        bSum += col & 0xFF;
                                        cnt++;
                                    }
                                }
                                if (cnt > 0) {
                                    return 0xFF000000 | ((rSum / cnt) << 16) | ((gSum / cnt) << 8) | (bSum / cnt);
                                }
                                // フォールバック: 単一pos
                                var st0 = getter.getBlockState(pos);
                                if (st0.getBlock() instanceof ruby.bamboo.block.SpringWaterBlock) {
                                    var src0 = ruby.bamboo.block.SpringWaterBlock.findSource(getter, pos, st0, 32);
                                    int base0 = ruby.bamboo.block.SpringColor.DEFAULT.color;
                                    if (src0 != null) {
                                        var srcSt0 = getter.getBlockState(src0);
                                        if (srcSt0.getBlock() instanceof ruby.bamboo.block.SpringBlock) base0 = srcSt0.getValue(ruby.bamboo.block.SpringBlock.COLOR).color;
                                    }
                                    if (base0 != ruby.bamboo.block.SpringColor.DEFAULT.color && base0 != ruby.bamboo.block.SpringColor.VANILLA.color) return 0xFF000000 | base0;
                                    int tint0;
                                    try { tint0 = ruby.bamboo.core.config.SpringConfig.COMMON.tintColor.get(); } catch (Exception e){ tint0 = 0xE0F8FF; }
                                    return multiplyColor(base0, tint0);
                                }
                                // 水没フォールバック
                                int wl = resolveWaterloggedColor(getter, pos);
                                if (wl != 0) return wl;
                            } catch (Exception e) {}
                        }
                        return getTintColor();
                    }

                    private int resolveWaterloggedColor(net.minecraft.world.level.BlockAndTintGetter getter, net.minecraft.core.BlockPos p) {
                        // 周囲6方向の spring_water ブロックから最も近い源泉色を借用
                        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                            var n = p.relative(d);
                            var ns = getter.getBlockState(n);
                            if (ns.getBlock() instanceof ruby.bamboo.block.SpringWaterBlock) {
                                var src = ruby.bamboo.block.SpringWaterBlock.findSource(getter, n, ns, 32);
                                int base = ruby.bamboo.block.SpringColor.DEFAULT.color;
                                if (src != null) {
                                    var srcSt = getter.getBlockState(src);
                                    if (srcSt.getBlock() instanceof ruby.bamboo.block.SpringBlock) base = srcSt.getValue(ruby.bamboo.block.SpringBlock.COLOR).color;
                                }
                                if (base != ruby.bamboo.block.SpringColor.DEFAULT.color && base != ruby.bamboo.block.SpringColor.VANILLA.color) return 0xFF000000 | base;
                                int tint;
                                try { tint = ruby.bamboo.core.config.SpringConfig.COMMON.tintColor.get(); } catch (Exception e){ tint = 0xE0F8FF; }
                                return multiplyColor(base, tint);
                            }
                        }
                        return 0;
                    }
                });
            }
        };
    }
}
