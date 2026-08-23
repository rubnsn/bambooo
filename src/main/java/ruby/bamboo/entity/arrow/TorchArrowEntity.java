package ruby.bamboo.entity.arrow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.core.init.BambooItems;

/**
 * 松明矢 (旧 EntityTorchArrow)。
 * <p>
 * 常時クリティカル (発射側で setCritArrow(true))。
 * ブロック命中で面の外側が AIR なら松明を設置して消滅 (旧 onGroundHit 相当)。
 * 飛行中は FLAME パーティクル。
 */
public class TorchArrowEntity extends AbstractArrow {

    public TorchArrowEntity(EntityType<? extends TorchArrowEntity> type, Level level) {
        super(type, level);
    }

    public TorchArrowEntity(EntityType<? extends TorchArrowEntity> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    public TorchArrowEntity(Level level, LivingEntity shooter) {
        this(BambooEntities.TORCH_ARROW.get(), shooter, level);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        Level level = this.level();
        BlockPos hitPos = result.getBlockPos();
        Direction face = result.getDirection();
        BlockPos targetPos = hitPos.relative(face);

        BlockState state = level.getBlockState(targetPos);
        if (state.isAir()) {
            BlockState torch = Blocks.TORCH.defaultBlockState();
            // 壁付き松明への配置判定 (旧 Blocks.TORCH.onBlockPlaced 相当)
            if (face != Direction.UP && face != Direction.DOWN) {
                torch = Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, face);
            }
            if (torch.canSurvive(level, targetPos)) {
                level.setBlock(targetPos, torch, 3);
                this.discard();
                return;
            }
        }

        super.onHitBlock(result);
    }

    @Override
    public void tick() {
        super.tick();
        // FLAME パーティクル (旧 spawnCritParticle 相当)
        if (this.level().isClientSide && !this.inGround) {
            for (int k = 0; k < 4; ++k) {
                this.level().addParticle(ParticleTypes.FLAME,
                        this.getX() + this.getDeltaMovement().x * k / 4.0D,
                        this.getY() + this.getDeltaMovement().y * k / 4.0D,
                        this.getZ() + this.getDeltaMovement().z * k / 4.0D,
                        0.0D, -0.025D, 0.0D);
            }
        }
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(BambooItems.TORCH_ARROW.get());
    }
}
