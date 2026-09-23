package ruby.bamboo.block;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 彼岸鉱石 (旧 HiganOre)。maple/ginkgo/sakura の3種 + 深層岩変種。
 * <p>
 * 旧 1.16.5 (sakura) では採掘レベルが sakura=3/ginkgo=2/maple=1 とバラバラだったが、
 * 1.20.1 では3種ともダイヤ相当 (要鉄ピッケル、XP 3-7) に統一。
 * ドロップは loot_table 側で silk→鉱石自体 / 通常→gem (幸運 ore_drops式) とし、
 * 旧の半減処理 (count/2) は廃止。
 */
public class HiganOreBlock extends DropExperienceBlock {
    public HiganOreBlock(BlockBehaviour.Properties props) {
        super(props, UniformInt.of(3, 7));
    }
}
