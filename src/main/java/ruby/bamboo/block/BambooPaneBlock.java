package ruby.bamboo.block;

import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 竹柵/欄間。旧 BambooPane (1.10.2) の移植。
 * <p>
 * バニラ鉄格子 (IronBarsBlock) 型の薄板ブロック。
 * 旧版は META 0-3 でテクスチャを切り替えていたが、1.20.1では
 * 4種を独立ブロックとして登録する (tatami と同じ手法)。
 * <p>
 * バリアント: bamboo_pane / bamboo_pane2 / bamboo_pane3 / ranma(欄間)
 */
public class BambooPaneBlock extends IronBarsBlock {

    /** 旧meta相当のバリアント */
    public enum Variant {
        /** meta=0 通常 */
        NORMAL("bamboo_pane", "bamboopane"),
        /** meta=1 日焼け1 */
        TAN1("bamboo_pane2", "bamboopane2"),
        /** meta=2 日焼け2 */
        TAN2("bamboo_pane3", "bamboopane3"),
        /** meta=3 欄間 */
        RANMA("ranma", "ranma");

        /** 登録名 / blockstate・モデル名 */
        public final String regName;
        /** テクスチャ名 (textures/block/<tex>.png) */
        public final String textureName;

        Variant(String regName, String textureName) {
            this.regName = regName;
            this.textureName = textureName;
        }
    }

    public BambooPaneBlock(Variant variant) {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.BAMBOO_WOOD)
                .strength(0.3f)
                .noOcclusion());
    }
}
