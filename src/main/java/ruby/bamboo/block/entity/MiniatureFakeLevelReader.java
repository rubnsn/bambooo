package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * ミニチュア内用 LevelReader ラッパー。
 * 内側座標 (0..size-1) は cells、境界外は実ワールド (bePos 周辺) を返す。
 * 汎用 canSurvive / isFaceSturdy 判定に用いるため、特定ブロック分岐を廃止して
 * {@code state.canSurvive(reader, pos)} に委譲する。
 */
public class MiniatureFakeLevelReader implements LevelReader {

    private final MiniatureBlockEntity be;
    private final Level outerLevel;
    private final BlockPos bePos;

    public MiniatureFakeLevelReader(MiniatureBlockEntity be, Level outerLevel, BlockPos bePos) {
        this.be = be;
        this.outerLevel = outerLevel;
        this.bePos = bePos;
    }

    // ===== BlockGetter =====

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (be.isInRange(pos.getX(), pos.getY(), pos.getZ())) {
            return be.getCell(pos);
        }
        // 境界外: 外部ワールドの隣接ブロックを返す
        int size = be.getSize();
        int dx = 0, dy = 0, dz = 0;
        if (pos.getX() < 0) dx = pos.getX();
        else if (pos.getX() >= size) dx = pos.getX() - size + 1;
        if (pos.getY() < 0) dy = pos.getY();
        else if (pos.getY() >= size) dy = pos.getY() - size + 1;
        if (pos.getZ() < 0) dz = pos.getZ();
        else if (pos.getZ() >= size) dz = pos.getZ() - size + 1;
        BlockPos outerPos = bePos.offset(dx, dy, dz);
        try {
            return outerLevel.getBlockState(outerPos);
        } catch (Exception e) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        if (be.isInRange(pos.getX(), pos.getY(), pos.getZ())) {
            // 内部 TE は保存していないため null。必要なら state から生成を試みるが現状不要。
            return null;
        }
        int size = be.getSize();
        int dx = 0, dy = 0, dz = 0;
        if (pos.getX() < 0) dx = pos.getX();
        else if (pos.getX() >= size) dx = pos.getX() - size + 1;
        if (pos.getY() < 0) dy = pos.getY();
        else if (pos.getY() >= size) dy = pos.getY() - size + 1;
        if (pos.getZ() < 0) dz = pos.getZ();
        else if (pos.getZ() >= size) dz = pos.getZ() - size + 1;
        BlockPos outerPos = bePos.offset(dx, dy, dz);
        try {
            return outerLevel.getBlockEntity(outerPos);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public float getShade(Direction dir, boolean shade) {
        return outerLevel.getShade(dir, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return outerLevel.getLightEngine();
    }

    /**
     * 光サンプリング位置の補正 (黒化対策)。
     * <p>
     * {@code tesselateBlock} は内外とも {@code LevelRenderer.getLightColor} →
     * {@code getBrightness} で光を取るが、セル座標 (0..size-1) のまま外の
     * ライトエンジンを引くと原点付近 (地下相当) の暗い値になり全面真っ黒になる。
     * ジオラマ全体は実際に照らされている {@code bePos} 直上の光で一様に照らし、
     * 方向別 shade と AO の自己遮蔽 (セル形状由来) はそのまま活かす。
     */
    @Override
    public int getBrightness(net.minecraft.world.level.LightLayer lightType, BlockPos pos) {
        try {
            return outerLevel.getBrightness(lightType, bePos.above());
        } catch (Exception e) {
            return 15;
        }
    }

    @Override
    public int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver) {
        if (be.isInRange(pos.getX(), pos.getY(), pos.getZ())) {
            // 内部では外の tint を借用
            return outerLevel.getBlockTint(bePos, resolver);
        }
        int size = be.getSize();
        int dx = 0, dy = 0, dz = 0;
        if (pos.getX() < 0) dx = pos.getX();
        else if (pos.getX() >= size) dx = pos.getX() - size + 1;
        if (pos.getY() < 0) dy = pos.getY();
        else if (pos.getY() >= size) dy = pos.getY() - size + 1;
        if (pos.getZ() < 0) dz = pos.getZ();
        else if (pos.getZ() >= size) dz = pos.getZ() - size + 1;
        return outerLevel.getBlockTint(bePos.offset(dx, dy, dz), resolver);
    }

    // ===== LevelReader =====

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean req) {
        return outerLevel.getChunk(x, z, status, req);
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return outerLevel.hasChunk(x, z);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return outerLevel.getHeight(type, x, z);
    }

    @Override
    public int getSkyDarken() {
        return outerLevel.getSkyDarken();
    }

    @Override
    public BiomeManager getBiomeManager() {
        return outerLevel.getBiomeManager();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return outerLevel.getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public boolean isClientSide() {
        return outerLevel.isClientSide();
    }

    @Override
    public int getSeaLevel() {
        return outerLevel.getSeaLevel();
    }

    @Override
    public DimensionType dimensionType() {
        return outerLevel.dimensionType();
    }

    @Override
    public RegistryAccess registryAccess() {
        return outerLevel.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return outerLevel.enabledFeatures();
    }

    // ===== LevelHeightAccessor =====

    @Override
    public int getHeight() {
        return outerLevel.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return outerLevel.getMinBuildHeight();
    }

    // ===== CollisionGetter =====

    @Override
    public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
        return outerLevel.getWorldBorder();
    }

    @Override
    public net.minecraft.world.level.BlockGetter getChunkForCollisions(int x, int z) {
        return outerLevel.getChunkForCollisions(x, z);
    }

    @Override
    public java.util.List<net.minecraft.world.phys.shapes.VoxelShape> getEntityCollisions(net.minecraft.world.entity.Entity entity, net.minecraft.world.phys.AABB box) {
        return outerLevel.getEntityCollisions(entity, box);
    }
}
