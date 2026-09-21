package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * ホタル瓶の BlockEntity。保持データなし (BER の描画アンカー用)。
 */
public class FireflyBottleBlockEntity extends BlockEntity {

    public FireflyBottleBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.FIREFLY_BOTTLE_BE.get(), pos, state);
    }
}
