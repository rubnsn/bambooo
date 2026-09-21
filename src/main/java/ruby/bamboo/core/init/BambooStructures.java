package ruby.bamboo.core.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.worldgen.VillagePiece;
import ruby.bamboo.worldgen.VillageStructure;

/**
 * 自作ストラクチャの型登録 (村・秋バイオーム用)。
 * BambooMod の既存 DeferredRegister 群と同じ方式で mod bus に接続する。
 */
public class BambooStructures {
    /** StructureType 用 (自作村)。新規 DeferredRegister ではなく BambooMod の流儀に従い bus 接続する */
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister
            .create(Registries.STRUCTURE_TYPE, BambooMod.MODID);
    /** StructurePieceType 用 (自作村ピース) */
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister
            .create(Registries.STRUCTURE_PIECE, BambooMod.MODID);

    /** 自作村 */
    public static final RegistryObject<StructureType<VillageStructure>> VILLAGE_TYPE = STRUCTURE_TYPES
            .register("village", () -> () -> VillageStructure.CODEC);
    /** 自作村ピース (土台・階段・初期村人付きテンプレート) */
    public static final RegistryObject<StructurePieceType> VILLAGE_PIECE = STRUCTURE_PIECES
            .register("village",
                    () -> (StructurePieceType.StructureTemplateType) VillagePiece::load);

    public static void init() {
    }
}
