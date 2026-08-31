package ruby.bamboo.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 色付き光API — 任意のBlockが実装可能。
 * <p>
 * 明るさはバニラ {@code lightLevel} (0-15) で別管理されるため、本APIはRGBのみを返す。
 * クライアント側の lightmap 乗算や Sodium bake 時に参照される想定。
 * Capability / 同期は Phase B 以降で提供。
 */
public interface ILightColor {

    /**
     * このブロックが発する光の色をRGBで返す。
     *
     * @param state ブロック状態
     * @param level ワールド参照 (nullableの場合あり)
     * @param pos ブロック座標
     * @return 0xRRGGBB (上位バイトは無視、アルファは含まない)。発光しない場合は 0xFFFFFF を推奨
     */
    int getLightColor(BlockState state, BlockGetter level, BlockPos pos);
}
