package ruby.bamboo.item;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 鉤爪 (sakura ClimbingClaw の 1.20.1 移植)。
 * <p>
 * インベントリ所持中、任意の壁を足場相当で登れる。壁押し付けで上昇 0.2、接触中は -0.15 スロースライド。
 * スニークは解除 (自然落下) のため、ハシゴの保持とは異なり自動下降する。滑空・スペクテイタ・騎乗中は迂回し、
 * 解除後の次落下から有効。クリエイティブ飛行は考慮しない。
 */
public class ClimbingClawItem extends Item implements Accessory {

    public ClimbingClawItem(Properties properties) {
        super(properties);
    }

    @Override
    public void playerPostTick(Player player, ItemStack stack) {
        if (player.isFallFlying() || player.isSpectator() || player.isPassenger()) {
            return;
        }
        if (player.isCrouching()) {
            return;
        }
        if (player.horizontalCollision) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, 0.2D, motion.z);
            player.resetFallDistance();
        } else if (isCollideBlock(player)) {
            player.resetFallDistance();
            Vec3 motion = player.getDeltaMovement();
            double x = Mth.clamp(motion.x, -0.15D, 0.15D);
            double z = Mth.clamp(motion.z, -0.15D, 0.15D);
            double y = Math.max(motion.y, -0.15D);
            player.setDeltaMovement(x, y, z);
        }
    }

    /** 体側の当たり判定を XZ に 0.1 拡張し、固体ブロックに触れているか (流体は除外) */
    static boolean isCollideBlock(Player player) {
        Level level = player.level();
        AABB box = player.getBoundingBox().inflate(0.1D, 0.0D, 0.1D);
        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY; y < box.maxY; y++) {
            for (int x = minX; x < box.maxX; x++) {
                for (int z = minZ; z < box.maxZ; z++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
