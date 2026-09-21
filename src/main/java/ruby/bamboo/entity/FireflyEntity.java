package ruby.bamboo.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ホタル (旧構想の新規実装。Entity自体は未実装だった)。
 * <p>
 * 森系バイオームの水辺・天空光下・夜間のみ自然スポーン (スポーン判定で制限)。
 * 朝になるとデスポーンする。瓶で捕獲してホタル瓶にできる。
 */
public class FireflyEntity extends PathfinderMob {

    /** 行動の中心点。ここから離れすぎたら戻る */
    private static final double HOME_RANGE = 12.0D;

    private BlockPos homePos;

    public FireflyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isPushable() {
        // 当たり判定は残すが押さない (攻撃・捕獲の対象にはなる)
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0D)
                .add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // 朝にデスポーン (クライアントには消滅が同期される)
        if (this.level().isDay()) {
            this.discard();
            return;
        }
        if (this.homePos == null) {
            this.homePos = this.blockPosition();
        }
        // 水中に入ったら浮上 (水場の上を飛ぶが中には入らない)
        if (this.isInWaterOrBubble()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.15D, 0.0D));
        }
        // 初期地点から離れすぎたら帰還 (1秒ごとに判定)
        if (this.tickCount % 20 == 0
                && this.distanceToSqr(this.homePos.getX() + 0.5D, this.position().y,
                        this.homePos.getZ() + 0.5D) > HOME_RANGE * HOME_RANGE) {
            this.getNavigation().moveTo(this.homePos.getX() + 0.5D, this.homePos.getY() + 1.5D,
                    this.homePos.getZ() + 0.5D, 1.0D);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.homePos != null) {
            tag.putIntArray("Home", new int[] { this.homePos.getX(), this.homePos.getY(), this.homePos.getZ() });
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) {
            int[] h = tag.getIntArray("Home");
            if (h.length == 3) {
                this.homePos = new BlockPos(h[0], h[1], h[2]);
            }
        }
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // コウモリ・オウムと同型。飛行中は落下距離を蓄積しない
    }

    /**
     * スポーン条件: 夜間 + 天空光 + 近傍に水場。
     * バイオーム絞り (森系) は biome_modifier 側で行う。
     */
    public static boolean checkFireflySpawnRules(EntityType<FireflyEntity> type, ServerLevelAccessor level,
            MobSpawnType spawnType, BlockPos pos, RandomSource rand) {
        if (!level.getLevel().isNight()) {
            return false;
        }
        if (!level.canSeeSky(pos)) {
            return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-5, -2, -5), pos.offset(5, 2, 5))) {
            if (level.getBlockState(p).is(Blocks.WATER)) {
                return true;
            }
        }
        return false;
    }
}
