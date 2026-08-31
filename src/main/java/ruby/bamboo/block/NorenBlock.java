package ruby.bamboo.block;

import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * のれん (noren_blue / noren_purple)。sakuraの Noren 相当を IronBarsBlock で移植。
 * <p>
 * 透過テクスチャ (cutout) かつ doesNotBlockMovement (noCollission)。
 */
public class NorenBlock extends IronBarsBlock {

    public NorenBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.WOOL)
                .strength(0.3F)
                .noOcclusion()
                .noCollission());
    }
}
