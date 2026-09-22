package ruby.bamboo.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.Blocks;
import ruby.bamboo.core.init.BambooStructures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 自作和風集落 (バニラ jigsaw 配置を使わない自前レイアウト)。
 * 道は地形に追従・建物は口合わせで厳密接続・土台と階段と初期村人を自前で面倒見る。
 * nbt は data/bamboomod/structures/ の既存物を部品として再利用する。
 */
public class VillageStructure extends Structure {
    public static final Codec<VillageStructure> CODEC = simpleCodec(VillageStructure::new);

    private static final ResourceLocation ROAD_STRAIGHT = loc("village_road_straight");
    private static final ResourceLocation ROAD_CROSS = loc("village_road_cross");
    private static final List<WeightedHouse> HOUSES = List.of(
            new WeightedHouse(loc("village_house_small"), 10, true),
            new WeightedHouse(loc("village_house_small2"), 10, false),
            new WeightedHouse(loc("village_house"), 1, true),
            new WeightedHouse(loc("village_kura"), 1, false),
            new WeightedHouse(loc("village_shrine"), 1, false),
            new WeightedHouse(loc("village_plant_field"), 5, false),
            new WeightedHouse(loc("village_rice_field"), 5, false),
            new WeightedHouse(loc("village_will"), 5, false),
            new WeightedHouse(loc("village_broom_sakura"), 5, false));

    public VillageStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return BambooStructures.VILLAGE_TYPE.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        return onTopOfChunkCenter(ctx, Heightmap.Types.WORLD_SURFACE_WG,
                builder -> generateVillage(ctx, builder));
    }

    private static ResourceLocation loc(String path) {
        return new ResourceLocation("bamboomod", path);
    }

    private void generateVillage(GenerationContext ctx, StructurePiecesBuilder builder) {
        RandomSource rand = ctx.random();
        StructureTemplateManager manager = ctx.structureTemplateManager();
        List<BoundingBox> placed = new ArrayList<>();
        List<PlacedRoad> roads = new ArrayList<>();
        List<Connection> links = new ArrayList<>();
        PlacedRoad first = placeRoad(manager, ctx, builder, placed, roads, ROAD_STRAIGHT, null, Rotation.NONE);
        if (first == null) return;
        // 南へ 2-4 ピース延長
        PlacedRoad cursor = first;
        int southRun = 2 + rand.nextInt(3);
        boolean branched = false;
        for (int i = 0; i < 4 && southRun > 0; i++) {
            ResourceLocation nextId = (!branched && rand.nextBoolean()) ? ROAD_CROSS : ROAD_STRAIGHT;
            PlacedRoad next = extendSouth(manager, ctx, builder, placed, roads, links, cursor, nextId, rand);
            if (next == null) break;
            cursor = next;
            southRun--;
            if (nextId.equals(ROAD_CROSS)) {
                branched = true;
                branchEastWest(manager, ctx, builder, placed, roads, links, next, rand);
            }
        }
        // 北へ 1-2 ピース
        PlacedRoad north = first;
        for (int i = 0; i < 2; i++) {
            PlacedRoad prev = extendNorth(manager, ctx, builder, placed, roads, links, north, ROAD_STRAIGHT, rand);
            if (prev == null) break;
            north = prev;
        }
        // 家を建物口に付ける
        attachHouses(manager, builder, placed, roads, rand);
        // 継ぎ目パッチ (最後に配置しテンプレートを上書きする)
        emitGhost(manager, builder, placed, links);
    }

    // ---- 配置ヘルパー ----

    private record Mouth(BlockPos pos, Direction front, Direction top) {
    }

    private record Connection(BlockPos mouthPos, Direction dir, int fromY, int nextY) {
    }

    private record PlacedRoad(BlockPos origin, List<Mouth> streetMouths, List<Mouth> buildingMouths) {
    }

    private static List<Mouth> readMouths(StructureTemplate template, Rotation rot, boolean street) {
        List<Mouth> out = new ArrayList<>();
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rot);
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW, true)) {
            if (info.nbt() == null) continue;
            String name = info.nbt().getString("name");
            boolean isStreet = name.contains("streets");
            if (isStreet != street) continue;
            out.add(new Mouth(info.pos(), JigsawBlock.getFrontFacing(info.state()), JigsawBlock.getTopFacing(info.state())));
        }
        return out;
    }

    private int surfaceY(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                ctx.heightAccessor(), ctx.randomState());
    }

    private PlacedRoad placeRoad(StructureTemplateManager manager, GenerationContext ctx,
            StructurePiecesBuilder builder, List<BoundingBox> placed, List<PlacedRoad> roads,
            ResourceLocation id, BlockPos originHint, Rotation rot) {
        StructureTemplate template = manager.getOrCreate(id);
        BlockPos origin;
        if (originHint == null) {
            ChunkPos chunk = ctx.chunkPos();
            int cx = chunk.getMiddleBlockX();
            int cz = chunk.getMiddleBlockZ();
            origin = new BlockPos(cx - 3, surfaceY(ctx, cx, cz), cz - 4);
        } else {
            origin = originHint;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rot);
        BoundingBox box = template.getBoundingBox(settings, origin);
        // 中心の地形に合わせる
        int cx = (box.minX() + box.maxX()) / 2;
        int cz = (box.minZ() + box.maxZ()) / 2;
        origin = new BlockPos(origin.getX(), surfaceY(ctx, cx, cz), origin.getZ());
        box = template.getBoundingBox(settings, origin);
        if (collides(placed, box)) return null;
        List<Mouth> streets = readMouths(template, rot, true);
        List<Mouth> buildings = readMouths(template, rot, false);
        builder.addPiece(new VillagePiece(manager, id, origin, rot, false, 0, false));
        placed.add(box);
        List<Mouth> worldStreets = new ArrayList<>();
        for (Mouth m : streets) worldStreets.add(new Mouth(origin.offset(m.pos()), m.front(), m.top()));
        List<Mouth> worldBuildings = new ArrayList<>();
        for (Mouth m : buildings) worldBuildings.add(new Mouth(origin.offset(m.pos()), m.front(), m.top()));
        PlacedRoad road = new PlacedRoad(origin, worldStreets, worldBuildings);
        roads.add(road);
        return road;
    }

    private Mouth findMouth(List<Mouth> mouths, Direction front) {
        for (Mouth m : mouths) {
            if (m.front() == front) return m;
        }
        return null;
    }

    private boolean collides(List<BoundingBox> placed, BoundingBox candidate) {
        for (BoundingBox b : placed) {
            boolean xOver = candidate.minX() + 1 <= b.maxX() && candidate.maxX() - 1 >= b.minX();
            boolean zOver = candidate.minZ() + 1 <= b.maxZ() && candidate.maxZ() - 1 >= b.minZ();
            boolean yOver = candidate.minY() <= b.maxY() && candidate.maxY() >= b.minY();
            if (xOver && zOver && yOver) return true;
        }
        return false;
    }

    private PlacedRoad extendSouth(StructureTemplateManager manager, GenerationContext ctx,
            StructurePiecesBuilder builder, List<BoundingBox> placed, List<PlacedRoad> roads,
            List<Connection> links, PlacedRoad from, ResourceLocation id, RandomSource rand) {
        Mouth south = findMouth(from.streetMouths(), Direction.SOUTH);
        if (south == null) return null;
        BlockPos anchor = south.pos().relative(Direction.SOUTH);
        List<Rotation> rots = new ArrayList<>(List.of(Rotation.values()));
        Collections.shuffle(rots, new Random(rand.nextLong()));
        for (Rotation rot : rots) {
            StructureTemplate template = manager.getOrCreate(id);
            Mouth north = findMouth(readMouths(template, rot, true), Direction.NORTH);
            if (north == null) continue;
            BlockPos o = anchor.offset(-north.pos().getX(), -north.pos().getY(), -north.pos().getZ());
            PlacedRoad placed2 = placeRoad(manager, ctx, builder, placed, roads, id, o, rot);
            if (placed2 != null) {
                links.add(new Connection(south.pos(), Direction.SOUTH, from.origin().getY(),
                        placed2.origin().getY()));
                return placed2;
            }
        }
        return null;
    }

    private PlacedRoad extendNorth(StructureTemplateManager manager, GenerationContext ctx,
            StructurePiecesBuilder builder, List<BoundingBox> placed, List<PlacedRoad> roads,
            List<Connection> links, PlacedRoad from, ResourceLocation id, RandomSource rand) {
        Mouth north = findMouth(from.streetMouths(), Direction.NORTH);
        if (north == null) return null;
        BlockPos anchor = north.pos().relative(Direction.NORTH);
        for (Rotation rot : Rotation.values()) {
            StructureTemplate template = manager.getOrCreate(id);
            Mouth south = findMouth(readMouths(template, rot, true), Direction.SOUTH);
            if (south == null) continue;
            BlockPos o = anchor.offset(-south.pos().getX(), -south.pos().getY(), -south.pos().getZ());
            PlacedRoad placed2 = placeRoad(manager, ctx, builder, placed, roads, id, o, rot);
            if (placed2 != null) {
                links.add(new Connection(north.pos(), Direction.NORTH, from.origin().getY(),
                        placed2.origin().getY()));
                return placed2;
            }
        }
        return null;
    }

    private void branchEastWest(StructureTemplateManager manager, GenerationContext ctx,
            StructurePiecesBuilder builder, List<BoundingBox> placed, List<PlacedRoad> roads,
            List<Connection> links, PlacedRoad cross, RandomSource rand) {
        for (Direction dir : new Direction[] { Direction.EAST, Direction.WEST }) {
            Mouth mouth = findMouth(cross.streetMouths(), dir);
            if (mouth == null) continue;
            if (rand.nextBoolean()) continue;
            BlockPos anchor = mouth.pos().relative(dir);
            StructureTemplate template = manager.getOrCreate(ROAD_STRAIGHT);
            for (Rotation rot : Rotation.values()) {
                Mouth end = findMouth(readMouths(template, rot, true),
                        dir == Direction.EAST ? Direction.WEST : Direction.EAST);
                if (end == null) continue;
                BlockPos o = anchor.offset(-end.pos().getX(), -end.pos().getY(), -end.pos().getZ());
                PlacedRoad placed2 = placeRoad(manager, ctx, builder, placed, roads, ROAD_STRAIGHT, o, rot);
                if (placed2 != null) {
                    links.add(new Connection(mouth.pos(), dir, cross.origin().getY(), placed2.origin().getY()));
                    break;
                }
            }
        }
    }

    private void emitGhost(StructureTemplateManager manager, StructurePiecesBuilder builder,
            List<BoundingBox> placed, List<Connection> links) {
        if (placed.isEmpty()) return;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BoundingBox b : placed) {
            minX = Math.min(minX, b.minX());
            minY = Math.min(minY, b.minY());
            minZ = Math.min(minZ, b.minZ());
            maxX = Math.max(maxX, b.maxX());
            maxY = Math.max(maxY, b.maxY());
            maxZ = Math.max(maxZ, b.maxZ());
        }
        VillagePiece ghost = new VillagePiece(manager, ROAD_STRAIGHT,
                new BlockPos(minX, minY, minZ), Rotation.NONE, false, 0, true);
        ghost.setVillageBox(new BoundingBox(minX - 2, minY - 6, minZ - 2, maxX + 2, maxY + 6, maxZ + 2));
        for (Connection link : links) {
            emitSteps(ghost, link);
        }
        builder.addPiece(ghost);
    }

    private void emitSteps(VillagePiece ghost, Connection link) {
        int diff = link.fromY() - link.nextY();
        if (diff == 0) return;
        // 下り方向 (高い側から低い側へ)
        Direction dir = link.dir();
        Direction d = diff > 0 ? dir : dir.getOpposite();
        BlockPos mouth = link.mouthPos();
        int highY = Math.max(link.fromY(), link.nextY());
        int lowY = Math.min(link.fromY(), link.nextY());
        // 高い側の端列: 下りなら mouth 列、登りなら anchor (mouth+dir) 列。既存面の置換なので隙間が出ない
        BlockPos highCenter = diff > 0
                ? new BlockPos(mouth.getX(), highY, mouth.getZ())
                : new BlockPos(mouth.getX() + dir.getStepX(), highY, mouth.getZ() + dir.getStepZ());
        Direction across = d.getClockWise();
        int steps = Math.min(Math.abs(diff), 4);
        for (int k = 0; k < steps; k++) {
            int y = highY - k;
            if (y <= lowY) break;
            for (int w = -2; w <= 2; w++) {
                BlockPos rel = highCenter.relative(d, k).relative(across, w);
                BlockPos at = new BlockPos(rel.getX(), y, rel.getZ());
                ghost.addPatch(at, (byte) 1, d.getOpposite());
                // 階段の下が空洞なら支える (空気・置換可のみ、既存面は不変)
                for (int yy = y - 1; yy >= lowY; yy--) {
                    ghost.addPatch(new BlockPos(at.getX(), yy, at.getZ()), (byte) 0, d);
                }
            }
        }
    }

    private void attachHouses(StructureTemplateManager manager, StructurePiecesBuilder builder,
            List<BoundingBox> placed, List<PlacedRoad> roads, RandomSource rand) {
        int houses = 0;
        int villagersLeft = 3;
        List<Mouth> mouths = new ArrayList<>();
        for (PlacedRoad road : roads) mouths.addAll(road.buildingMouths());
        Collections.shuffle(mouths, new Random(rand.nextLong()));
        for (Mouth mouth : mouths) {
            if (houses >= 6) break;
            int placedVillagers = tryAttachHouse(manager, builder, placed, mouth, rand, villagersLeft > 0);
            if (placedVillagers >= 0) {
                houses++;
                villagersLeft -= placedVillagers;
            }
        }
    }

    /** @return 配置した村人数。配置なしは -1 */
    private int tryAttachHouse(StructureTemplateManager manager, StructurePiecesBuilder builder,
            List<BoundingBox> placed, Mouth mouth, RandomSource rand, boolean allowVillager) {
        List<WeightedHouse> candidates = new ArrayList<>();
        for (WeightedHouse h : HOUSES) {
            for (int i = 0; i < h.weight(); i++) candidates.add(h);
        }
        Collections.shuffle(candidates, new Random(rand.nextLong()));
        for (WeightedHouse house : candidates) {
            StructureTemplate template = manager.getOrCreate(house.id());
            List<Rotation> rots = new ArrayList<>(List.of(Rotation.values()));
            Collections.shuffle(rots, new Random(rand.nextLong()));
            for (Rotation rot : rots) {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rot);
                for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings,
                        Blocks.JIGSAW, true)) {
                    if (info.nbt() == null || !info.nbt().getString("name").contains("entrance")) continue;
                    Direction front = JigsawBlock.getFrontFacing(info.state());
                    Direction top = JigsawBlock.getTopFacing(info.state());
                    if (front != mouth.front().getOpposite() || top != mouth.top()) continue;
                    BlockPos origin = mouth.pos().relative(mouth.front()).offset(-info.pos().getX(),
                            -info.pos().getY(), -info.pos().getZ());
                    // 建物口は厳密合わせ: 道口Yに建物口を合わせる
                    origin = new BlockPos(origin.getX(), mouth.pos().getY() - info.pos().getY(), origin.getZ());
                    BoundingBox box = template.getBoundingBox(settings, origin);
                    if (collides(placed, box)) continue;
                    int villagers = (house.hasBed() && allowVillager) ? 1 : 0;
                    builder.addPiece(new VillagePiece(manager, house.id(), origin, rot, true, villagers,
                            false));
                    placed.add(box);
                    return villagers;
                }
            }
        }
        return -1;
    }

    private record WeightedHouse(ResourceLocation id, int weight, boolean hasBed) {
    }
}
