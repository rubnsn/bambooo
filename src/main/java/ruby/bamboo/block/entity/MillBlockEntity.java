package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ruby.bamboo.block.MillBlock;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * 風車・水車の BlockEntity (旧 EntityMill / EntityWindmill / EntityWaterwheel の移植)。
 * <p>
 * 旧仕様の踏襲点:
 * <ul>
 * <li>風車は常時回転、水車は水に浸かっているときのみ回転
 * (旧 {@code handleWaterMovement} + boundingBox 水判定相当。1.20.1 では
 * 自身・真下・水平隣・その真下の流体を直接見る)</li>
 * <li>回転方向の反転 (旧水車の mirrorRoll / つづら操作相当。BlockState REVERSED)</li>
 * </ul>
 * 変更点:
 * <ul>
 * <li>風速の概念を導入: クライアント側 static フィールドで目標風速
 * (0.5-3.0 度/tick) を 100-200tick ごとに抽選し、現在風速を線形補間で追従。
 * 全風車で共有される (ユーザー指定通り static で良い)。旧版は毎tick+1固定</li>
 * <li>旧版の羽根枚数 (4-8)・サイズ (1-5)・DataWatcher 同期は廃止。
 * サイズは BlockState SIZE (S/M/L) のみ、羽根は4枚固定</li>
 * <li>roll はクライアント演出のみのため NBT 保存・サーバー同期なし。
 * SIZE/REVERSED は BlockState のため自動同期される</li>
 * </ul>
 */
public class MillBlockEntity extends BlockEntity {

    /** クライアント専用: 現在の回転角 (度)。旧 roll 相当 */
    public float roll;
    /** クライアント専用: 前tickの回転角 (partialTicks補間用) */
    public float prevRoll;

    /** クライアント専用: 現在の風速 (度/tick)。全風車で共有 */
    private static float windSpeed = 1.0F;
    /** クライアント専用: 目標風速 */
    private static float windTarget = 1.0F;
    /** クライアント専用: 次の風速抽選までの残りtick */
    private static int windTimer = 0;
    /** クライアント専用: 風速を最後に更新した gameTime (同一tickの多重更新防止) */
    private static long lastWindUpdate = -1L;

    public MillBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.MILL_BE.get(), pos, state);
    }

    /**
     * クライアントティッカー (MillBlock#getTicker から登録)。
     */
    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T be) {
        if (be instanceof MillBlockEntity mill) {
            mill.clientTick(level, state);
        }
    }

    private void clientTick(Level level, BlockState state) {
        prevRoll = roll;
        if (!(state.getBlock() instanceof MillBlock mill)) {
            return;
        }
        float dir = state.getValue(MillBlock.REVERSED) ? -1.0F : 1.0F;
        if (mill.getType().waterwheel) {
            // 旧 EntityWaterwheel#onUpdate: inWater 時のみ回転 (逆回転対応)
            if (isInWater(level, worldPosition)) {
                roll += 1.0F * dir;
            }
        } else {
            updateWind(level);
            roll += windSpeed * dir;
        }
        if (roll >= 360.0F) {
            roll -= 360.0F;
        } else if (roll < 0.0F) {
            roll += 360.0F;
        }
    }

    /**
     * 風速の更新 (同一tick内は1回のみ)。目標風速を 100-200tick ごとに
     * 0.5-3.0 度/tick の範囲で抽選し、現在風速を 2%/tick で追従させる。
     */
    private static void updateWind(Level level) {
        long time = level.getGameTime();
        if (time == lastWindUpdate) {
            return;
        }
        lastWindUpdate = time;
        if (--windTimer <= 0) {
            windTarget = 0.5F + level.random.nextFloat() * 2.5F;
            windTimer = 100 + level.random.nextInt(100);
        }
        windSpeed += (windTarget - windSpeed) * 0.02F;
    }

    /**
     * 水判定 (旧 boundingBox 水没チェック相当)。
     * 自身・真下・水平4隣・その真下に水があれば true。
     */
    public static boolean isInWater(Level level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            return true;
        }
        if (level.getFluidState(pos.below()).is(FluidTags.WATER)) {
            return true;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(pos.relative(dir)).is(FluidTags.WATER)) {
                return true;
            }
            if (level.getFluidState(pos.relative(dir).below()).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /**
     * サイズ M/L は1ブロックからはみ出して描画されるため、カリング境界を無限にする。
     * (1ブロック超え描画の要検証点への回答: BER + INFINITE で描画可能)
     */
    @Override
    public AABB getRenderBoundingBox() {
        // 1.20.1 に AABB.INFINITE は無いため無限大で構築 (フラスタムカリング無効相当)
        return new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }
}
