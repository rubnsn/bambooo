package ruby.bamboo.block;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 狐火 (旧 1.10.2 Kitunebi / 参考: sakura (rubnsn) Kitsunebi) の移植。
 * <p>
 * 不可視の光源ブロック (光レベル15)。
 * プレイヤーがこのブロックの BlockItem を手に持っている間だけ可視化される
 * (設置位置確認用ギミック)。それ以外は完全に見えない。
 * <ul>
 *   <li>VISIBLE ("flg") - クライアント側のみで切り替わる表示フラグ</li>
 *   <li>当たり判定: 可視時のみフルブロック相当の選択枠。衝突は常に無し</li>
 *   <li>サーバーには影響を与えない (visible 切替は setBlock flags=3 だが
 *       サーバー側 state は変えないため、ワールドデータには反映されない)</li>
 * </ul>
 */
public class KitunebiBlock extends Block {

    public static final BooleanProperty VISIBLE = BooleanProperty.create("flg");

    /** 選択枠表示用 (旧 FULL_BLOCK_AABB 相当) */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public KitunebiBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(VISIBLE, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VISIBLE);
    }

    /**
     * 手持ち判定 + 表示切替。
     * 旧 randomDisplayTick → setVisibleFlg 相当。クライアント専用。
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        boolean visible = isHoldingThis(player);
        if (visible != state.getValue(VISIBLE)) {
            // 旧 world.setBlockState(pos, state.withProperty(VISIBLE, isVisible), 3) 相当。
            // flags=3 でも再描画は走るが、サーバー同期を防ぐため CLIENT 側 setBlock を使う。
            level.setBlock(pos, state.setValue(VISIBLE, visible), 3);
        }
    }

    private boolean isHoldingThis(Player player) {
        for (ItemStack stack : new ItemStack[] { player.getMainHandItem(), player.getOffhandItem() }) {
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() == this) {
                return true;
            }
        }
        return false;
    }

    /**
     * 可視時のみ選択可能 (sakura Kitsunebi#getShape 相当)。
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(VISIBLE) ? SHAPE : Shapes.empty();
    }

    /** 衝突判定なし (旧 getCollisionBoundingBox NULL_AABB 相当) */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        // 何もしない (隣接更新による連鎖破壊防止)
    }

    /** 旧 MapColor.AIR 相当 */
    @Override
    public MapColor defaultMapColor() {
        return MapColor.NONE;
    }
}
