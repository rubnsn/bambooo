package ruby.bamboo.block;

import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 桜の原木。旧 SakuraLog (1.10.2) の移植。
 * <p>
 * 1.20.1では RotatedPillarBlock (axis property) をそのまま利用。
 */
public class SakuraLogBlock extends RotatedPillarBlock {

    public SakuraLogBlock(BlockBehaviour.Properties props) {
        super(props);
    }
}
