package ruby.bamboo.core.init;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.entity.CampfireBlockEntity;
import ruby.bamboo.block.entity.CutBlockEntity;
import ruby.bamboo.block.entity.JPChestBlockEntity;
import ruby.bamboo.block.entity.MillStoneBlockEntity;
import ruby.bamboo.block.entity.MiniatureBlockEntity;
import ruby.bamboo.block.entity.SlideDoorBlockEntity;
import ruby.bamboo.block.entity.WallShelfBlockEntity;

/**
 * BlockEntityType 登録。JPChest 用に新規追加 (旧 1.10.2 版は TileEntity 登録を
 * GameRegistry.registerTileEntity で行っていたが、1.20.1 では DeferredRegister 方式)。
 * <p>
 * DeferredRegister 本体は {@link ruby.bamboo.BambooMod#BLOCK_ENTITIES} を使用する。
 */
public final class BambooBlockEntities {

    public static final RegistryObject<BlockEntityType<JPChestBlockEntity>> JP_CHEST_BE = BambooMod.BLOCK_ENTITIES
            .register(
                    "jp_chest",
                    () -> BlockEntityType.Builder.of(JPChestBlockEntity::new, BambooBlocks.JP_CHEST.get()).build(null));

    /** 石臼 (旧 TileMillStone) */
    public static final RegistryObject<BlockEntityType<MillStoneBlockEntity>> MILL_STONE_BE = BambooMod.BLOCK_ENTITIES
            .register(
                    "mill_stone",
                    () -> BlockEntityType.Builder.of(MillStoneBlockEntity::new, BambooBlocks.MILLSTONE.get())
                            .build(null));

    /** 囲炉裏 (旧 TileCampfire) */
    public static final RegistryObject<BlockEntityType<CampfireBlockEntity>> CAMPFIRE_BE = BambooMod.BLOCK_ENTITIES
            .register(
                    "campfire",
                    () -> BlockEntityType.Builder.of(CampfireBlockEntity::new, BambooBlocks.CAMPFIRE.get())
                            .build(null));

    /** 引き戸 (sakura-master SlideDoor) */
    public static final RegistryObject<BlockEntityType<SlideDoorBlockEntity>> SLIDE_DOOR_BE = BambooMod.BLOCK_ENTITIES
            .register(
                    "slide_door",
                    () -> BlockEntityType.Builder.of(SlideDoorBlockEntity::new,
                            BambooBlocks.SHOJI.get(), BambooBlocks.SHOJI_YOKOGUMI.get(), BambooBlocks.SHOJI_TATEGUMI.get(),
                            BambooBlocks.SHOJI_YUKIMI.get(), BambooBlocks.HUSUMA.get(), BambooBlocks.GLASS_DOOR.get(),
                            BambooBlocks.GLASS_DOOR_GRID.get())
                            .build(null));

    /** 壁棚 (sakura-master WallShelf) */
    public static final RegistryObject<BlockEntityType<WallShelfBlockEntity>> WALL_SHELF_BE = BambooMod.BLOCK_ENTITIES
            .register(
                    "wall_shelf",
                    () -> BlockEntityType.Builder.of(WallShelfBlockEntity::new, BambooBlocks.WALL_SHELF.get())
                            .build(null));

    /** ミニチュア (箱庭) — Phase A データ層 */
    public static final RegistryObject<BlockEntityType<MiniatureBlockEntity>> MINIATURE_BE = BambooMod.BLOCK_ENTITIES
            .register(
                    "miniature",
                    () -> BlockEntityType.Builder.of(MiniatureBlockEntity::new, BambooBlocks.MINIATURE.get())
                            .build(null));

    /** カットブロック */
    public static final RegistryObject<BlockEntityType<CutBlockEntity>> CUT_BLOCK_BE = BambooMod.BLOCK_ENTITIES
            .register(
                    "cut_block",
                    () -> BlockEntityType.Builder.of(CutBlockEntity::new, BambooBlocks.CUT_BLOCK.get())
                            .build(null));

    /**
     * 静的初期化順序の保証用ダミー。BambooMod コンストラクタから呼ばれる。
     */
    public static void init() {
        // static フィールド初期化は本クラスがロードされた時点で完了している
    }
}
