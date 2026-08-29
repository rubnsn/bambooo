package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.api.ILightColor;
import ruby.bamboo.core.init.BambooCapabilities;

/**
 * 間接照明。旧 IndLight (1.10.2) の移植。
 * <p>
 * 設置面の逆向きに張り付いた小さな発光チップ(2x2x1)。
 * 隣接する同色・同向の indlight と接続して帯状に伸びる。
 * <p>
 * NORTH/EAST/SOUTH/WEST の各プロパティは「面に対する局所方向」を示す
 * (NORTH=面上方向, SOUTH=面下方向, EAST=面向かって右, WEST=左。旧 getActualState 相当)。
 * 光源レベル15。描画は cutout (旧 BlockRenderLayer.TRANSLUCENT 相当処理)。
 */
public class IndLightBlock extends Block implements ILightColor {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** 隣接接続 (旧 getActualState の north/east/south/west 相当) */
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");

    private static final VoxelShape UP_AABB = Block.box(0, 14, 0, 16, 16, 16);
    private static final VoxelShape DOWN_AABB = Block.box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape NORTH_AABB = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape SOUTH_AABB = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape WEST_AABB = Block.box(0, 0, 0, 2, 16, 16);
    private static final VoxelShape EAST_AABB = Block.box(14, 0, 0, 16, 16, 16);

    public final DyeColor color;

    public enum DyeColor {
        WHITE("white", 0xEDEDED), ORANGE("orange", 0xDB7D3E), MAGENTA("magenta", 0xB350BC),
        LIGHT_BLUE("light_blue", 0x6B8AC9), YELLOW("yellow", 0xC1B324), LIME("lime", 0x41AE38),
        PINK("pink", 0xD08499), GRAY("gray", 0x404040),
        SILVER("silver", 0x9AA1A1), CYAN("cyan", 0x2E6E89), PURPLE("purple", 0x7E3DB5),
        BLUE("blue", 0x2E409A), BROWN("brown", 0x5C4428), GREEN("green", 0x4C7F39),
        RED("red", 0xAE3936), BLACK("black", 0x181414);

        public final String name;
        /** 旧 EnumDyeColor#getMapColor().colorValue 相当の乗算用カラー (ItemColor 用) */
        public final int mapColor;

        DyeColor(String name, int mapColor) {
            this.name = name;
            this.mapColor = mapColor;
        }
    }

    public IndLightBlock(DyeColor color) {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.STONE)
                .strength(0.3f, 300f)
                .lightLevel(state -> 15)
                .noOcclusion());
        this.color = color;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING).add(NORTH).add(EAST).add(SOUTH).add(WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 旧 onBlockPlaced: 設定面の逆方向に向く
        return this.defaultBlockState().setValue(FACING,
                context.getClickedFace().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case DOWN -> DOWN_AABB;
            case UP -> UP_AABB;
            case NORTH -> NORTH_AABB;
            case SOUTH -> SOUTH_AABB;
            case WEST -> WEST_AABB;
            case EAST -> EAST_AABB;
        };
    }

    /**
     * 隣接した同種 indlight への接続状態を更新 (旧 getActualState 相当)。
     * <p>
     * 接続対象は FACING に応じて変換される。例: 壁付け(FACING=SOUTH)なら
     * 面上方向 = 世界の UP、右 = 世界の EAST。
     */
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        return state
                .setValue(NORTH, connects(state, level.getBlockState(pos.relative(planeDir(facing, PlaneDir.UP)))))
                .setValue(SOUTH, connects(state, level.getBlockState(pos.relative(planeDir(facing, PlaneDir.DOWN)))))
                .setValue(EAST, connects(state, level.getBlockState(pos.relative(planeDir(facing, PlaneDir.RIGHT)))))
                .setValue(WEST, connects(state, level.getBlockState(pos.relative(planeDir(facing, PlaneDir.LEFT)))));
    }

    /** 面に対する4方向 */
    private enum PlaneDir {
        UP, DOWN, RIGHT, LEFT
    }

    /**
     * 局所方向 -> 世界方向の変換 (旧 getActualState の switch と同一の対応表)。
     */
    private static Direction planeDir(Direction facing, PlaneDir plane) {
        return switch (facing) {
            case UP -> switch (plane) {
                case UP -> Direction.NORTH;
                case DOWN -> Direction.SOUTH;
                case RIGHT -> Direction.EAST;
                case LEFT -> Direction.WEST;
            };
            case DOWN -> switch (plane) {
                case UP -> Direction.SOUTH;
                case DOWN -> Direction.NORTH;
                case RIGHT -> Direction.EAST;
                case LEFT -> Direction.WEST;
            };
            case NORTH -> switch (plane) {
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
                case RIGHT -> Direction.WEST;
                case LEFT -> Direction.EAST;
            };
            case SOUTH -> switch (plane) {
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
                case RIGHT -> Direction.EAST;
                case LEFT -> Direction.WEST;
            };
            case EAST -> switch (plane) {
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
                case RIGHT -> Direction.NORTH;
                case LEFT -> Direction.SOUTH;
            };
            case WEST -> switch (plane) {
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
                case RIGHT -> Direction.SOUTH;
                case LEFT -> Direction.NORTH;
            };
        };
    }

    /**
     * 接続判定 (旧 canChain 相当): 同ブロック種・同色・同FACING のみ。
     */
    private boolean connects(BlockState state, BlockState target) {
        if (!(target.getBlock() instanceof IndLightBlock other)) {
            return false;
        }
        return other.color == this.color && target.getValue(FACING) == state.getValue(FACING);
    }

    @Override
    public int getLightColor(BlockState state, BlockGetter level, BlockPos pos) {
        return this.color.mapColor;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (oldState.getBlock() == state.getBlock()) {
            return;
        }
        if (!level.hasChunkAt(pos)) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        chunk.getCapability(BambooCapabilities.COLORED_LIGHT).ifPresent(storage -> {
            storage.getMap().put(Long.valueOf(pos.asLong()), this.color.mapColor & 0xFFFFFF);
            storage.setScanned(true);
            storage.incrementVersion();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int cx = chunk.getPos().x + dx;
                    int cz = chunk.getPos().z + dz;
                    if (!level.hasChunk(cx, cz)) continue;
                    LevelChunk c2 = level.getChunk(cx, cz);
                    c2.getCapability(BambooCapabilities.COLORED_LIGHT).ifPresent(s2 -> s2.invalidateTintCache());
                }
            }
            com.mojang.logging.LogUtils.getLogger().info("[bamboomod] IndLight onPlace {} color {} chunk {} mapSize {} ver {}", pos, String.format("#%06X", this.color.mapColor & 0xFFFFFF), chunk.getPos(), storage.getMap().size(), storage.getVersion());
        });
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (newState.getBlock() == state.getBlock()) {
            return;
        }
        if (!level.hasChunkAt(pos)) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        chunk.getCapability(BambooCapabilities.COLORED_LIGHT).ifPresent(storage -> {
            storage.getMap().remove(Long.valueOf(pos.asLong()));
            storage.incrementVersion();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int cx = chunk.getPos().x + dx;
                    int cz = chunk.getPos().z + dz;
                    if (!level.hasChunk(cx, cz)) continue;
                    LevelChunk c2 = level.getChunk(cx, cz);
                    c2.getCapability(BambooCapabilities.COLORED_LIGHT).ifPresent(s2 -> s2.invalidateTintCache());
                }
            }
            com.mojang.logging.LogUtils.getLogger().info("[bamboomod] IndLight onRemove {} chunk {} mapSize {} ver {}", pos, chunk.getPos(), storage.getMap().size(), storage.getVersion());
        });
    }
}
