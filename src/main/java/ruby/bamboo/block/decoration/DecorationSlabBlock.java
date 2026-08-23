package ruby.bamboo.block.decoration;

import net.minecraft.world.level.block.SlabBlock;

/**
 * デコレーション用ハーフブロック。旧 DecorationSlab (1.10.2) の移植。
 * <p>
 * 1.20.1ではダブルスラブは SlabBlock の type=double 状態で表現されるため、
 * 旧版のような別ブロック(DecorationDoubleSlab)は不要。
 */
public class DecorationSlabBlock extends SlabBlock {

    private final EnumDecoration deco;

    public DecorationSlabBlock(EnumDecoration deco) {
        super(DecorationBlocks.props(deco));
        this.deco = deco;
    }

    public EnumDecoration getDeco() {
        return this.deco;
    }
}
