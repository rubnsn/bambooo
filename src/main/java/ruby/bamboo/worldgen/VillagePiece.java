package ruby.bamboo.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import ruby.bamboo.core.init.BambooStructures;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * 自作村の部品。配置後に建物の土台 (土)・初期村人、道継ぎ目の階段パッチ (ゴースト) を面倒見る。
 */
public class VillagePiece extends TemplateStructurePiece {
    private boolean building;
    private int villagers;
    private boolean ghost;
    private final List<BlockPos> patchPos = new ArrayList<>();
    private final List<Byte> patchKind = new ArrayList<>();
    private final List<Direction> patchDir = new ArrayList<>();

    public VillagePiece(StructureTemplateManager manager, ResourceLocation templateId, BlockPos pos, Rotation rot,
            boolean building, int villagers, boolean ghost) {
        super(BambooStructures.VILLAGE_PIECE.get(), 0, manager, templateId, templateId.toString(),
                makeSettings(rot), pos);
        this.building = building;
        this.villagers = villagers;
        this.ghost = ghost;
    }

    public VillagePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(BambooStructures.VILLAGE_PIECE.get(), tag, manager,
                rl -> makeSettings(Rotation.valueOf(tag.getString("Rot"))));
        this.building = tag.getBoolean("BambooBuilding");
        this.villagers = tag.getInt("BambooVillagers");
        this.ghost = tag.getBoolean("BambooGhost");
        ListTag ppos = tag.getList("BambooPatchPos", 3);
        ListTag pkind = tag.getList("BambooPatchKind", 3);
        ListTag pdir = tag.getList("BambooPatchDir", 3);
        for (int i = 0; i < ppos.size() && i < pkind.size() && i < pdir.size(); i++) {
            ListTag p = ppos.getList(i);
            this.patchPos.add(new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)));
            this.patchKind.add((byte) pkind.getInt(i));
            this.patchDir.add(Direction.from3DDataValue(pdir.getInt(i)));
        }
    }

    /** ゴースト用パッチ追加 (kind: 0 石レンガ / 1 石レンガ階段。階段の向きは dir) */
    public void addPatch(BlockPos pos, byte kind, Direction dir) {
        this.patchPos.add(pos);
        this.patchKind.add(kind);
        this.patchDir.add(dir);
    }

    /** ゴースト用に集落全体の箱を付け直す */
    public void setVillageBox(BoundingBox box) {
        this.boundingBox = box;
    }

    private static StructurePlaceSettings makeSettings(Rotation rot) {
        // knownShape=true: テンプレの状態を最終形として扱い、配置後の updateShapeAtEdge /
        // updateFromNeighbourShapes + blockUpdated を抑止する。nbt は手作り確定形のため接続補正は不要で、
        // 逆にチャンク跨ぎで head/foot が別 postProcess になると BedBlock.updateShape (BedBlock.java:148-150)
        // が相方不在で AIR 化してベッドだけ消える (ドア・ガチャは縦持ちのため無事)。自作土台・パッチは flag 2 のため元々更新なし。
        return new StructurePlaceSettings().setRotation(rot).setMirror(Mirror.NONE).setKnownShape(true);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super.addAdditionalSaveData(ctx, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
        tag.putBoolean("BambooBuilding", this.building);
        tag.putInt("BambooVillagers", this.villagers);
        tag.putBoolean("BambooGhost", this.ghost);
        ListTag ppos = new ListTag();
        ListTag pkind = new ListTag();
        ListTag pdir = new ListTag();
        for (int i = 0; i < this.patchPos.size(); i++) {
            BlockPos p = this.patchPos.get(i);
            ListTag pos = new ListTag();
            pos.add(net.minecraft.nbt.IntTag.valueOf(p.getX()));
            pos.add(net.minecraft.nbt.IntTag.valueOf(p.getY()));
            pos.add(net.minecraft.nbt.IntTag.valueOf(p.getZ()));
            ppos.add(pos);
            pkind.add(net.minecraft.nbt.IntTag.valueOf(this.patchKind.get(i)));
            pdir.add(net.minecraft.nbt.IntTag.valueOf(this.patchDir.get(i).get3DDataValue()));
        }
        tag.put("BambooPatchPos", ppos);
        tag.put("BambooPatchKind", pkind);
        tag.put("BambooPatchDir", pdir);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        if (this.ghost) {
            applyPatches(level, chunkBox);
            return;
        }
        super.postProcess(level, structureManager, generator, random, chunkBox, chunkPos, pivot);
        if (this.building) {
            buildFoundation(level, chunkBox);
            spawnVillagers(level);
        }
    }

    private void applyPatches(WorldGenLevel level, BoundingBox chunkBox) {
        for (int i = 0; i < this.patchPos.size(); i++) {
            BlockPos p = this.patchPos.get(i);
            if (!chunkBox.isInside(p)) continue;
            if (!level.getFluidState(p).isEmpty()) continue;
            byte kind = this.patchKind.get(i);
            BlockState current = level.getBlockState(p);
            if (kind == 1) {
                if (!isPatchable(current)) continue;
                BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, this.patchDir.get(i))
                        .setValue(StairBlock.HALF, Half.BOTTOM)
                        .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT)
                        .setValue(StairBlock.WATERLOGGED, false);
                level.setBlock(p, stair, 2);
            } else {
                if (isSoft(current)) level.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
        }
    }

    private static boolean isPatchable(BlockState state) {
        return state.is(Blocks.STONE_BRICKS) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || isSoft(state);
    }

    private void buildFoundation(WorldGenLevel level, BoundingBox chunkBox) {
        BoundingBox box = this.getBoundingBox();
        BlockState fill = Blocks.DIRT.defaultBlockState();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY() - 1; y >= Math.max(box.minY() - 10, level.getMinBuildHeight()); y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!chunkBox.isInside(p)) break;
                    BlockState current = level.getBlockState(p);
                    if (current.isAir() || current.canBeReplaced()
                            || !current.getFluidState().isEmpty() && current.getFluidState().is(Fluids.WATER)) {
                        level.setBlock(p, fill, 2);
                    } else {
                        break;
                    }
                }
            }
        }
    }

    private void spawnVillagers(WorldGenLevel level) {
        if (this.villagers <= 0) return;
        BoundingBox box = this.getBoundingBox();
        int cx = (box.minX() + box.maxX()) / 2;
        int cz = (box.minZ() + box.maxZ()) / 2;
        for (int i = 0; i < this.villagers; i++) {
            int x = cx + (i - this.villagers / 2) * 2;
            int z = cz;
            int y = box.minY() + 1;
            BlockPos pos = new BlockPos(x, y, z);
            Villager villager = new Villager(EntityType.VILLAGER, level.getLevel());
            villager.moveTo(x + 0.5D, y, z + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
            DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
            villager.finalizeSpawn(level, difficulty, MobSpawnType.STRUCTURE, null, null);
            // 求職者 (NONE) を明示: STRUCTURE生成は NONE 維持だが無職 (NITWIT) 化を防止するため固定する
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
            level.addFreshEntityWithPassengers(villager);
        }
        // 同一部品が複数チャンクで postProcess されるため二重湧き防止
        this.villagers = 0;
    }

    private static boolean isSoft(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, net.minecraft.world.level.ServerLevelAccessor level,
            RandomSource random, BoundingBox box) {
        // 自作村に data マーカーは使わない
    }

    /** StructurePieceType 登録用ファクトリ */
    public static VillagePiece load(StructureTemplateManager manager, CompoundTag tag) {
        return new VillagePiece(manager, tag);
    }
}
