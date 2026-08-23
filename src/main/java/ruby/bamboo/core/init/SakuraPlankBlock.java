package ruby.bamboo.core.init;

import net.minecraft.world.level.block.RotatedPillarBlock;

/**
 * 桜の木材。旧 SakuraPlank (1.10.2) の移植。
 * <p>
 * 旧版は metadata(variant) を持っていたが実質 sakura のみ有効だったため、
 * 1.20.1ではバリアント property を廃止し RotatedPillarBlock (axis のみ) とした。
 * (1.20.1の StateDefinition は値が1個しかない property を登録できないため)
 * 将来バリアントを増やす場合は EnumProperty を復活させる。
 */
public class SakuraPlankBlock extends RotatedPillarBlock {

    public SakuraPlankBlock(Properties props) {
        super(props);
    }
}
