package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * ガチャポンの BlockEntity。回転・レバーはクライアント演出のみで NBT 同期なし。
 */
public class GachaBlockEntity extends BlockEntity {
    /** 回転台の角度 (度) */
    public float rotor;
    public float prevRotor;
    /** レバーの引き角 (度、0=待機・90倒し切り)。Spin演出は Screen 側が独自に持つ */
    public float lever;
    public float prevLever;

    public GachaBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.GACHA_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GachaBlockEntity be) {
        be.prevRotor = be.rotor;
        be.prevLever = be.lever;
        // 待機時はゆっくり回転 (1.0度/tick)。レバーは元に戻る
        be.rotor += 1.0F;
        if (be.rotor >= 360.0F) {
            be.rotor -= 360.0F;
        }
        if (be.lever > 0.0F) {
            be.lever = Math.max(0.0F, be.lever - 6.0F);
        }
    }

    /** レバーを引く (右クリック演出用。ワールド側は未使用、Screen側の見た目合わせ) */
    public void pullLever() {
        this.lever = 90.0F;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }
}
