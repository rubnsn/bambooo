package ruby.bamboo.entity.ai;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import ruby.bamboo.capsule.CapsuleRules;

/**
 * 使役Mobの所有者追従 (TamableAnimalでない種にも効く汎用Goal)。
 */
public class CapsuleFollowOwnerGoal extends Goal {

    private final Mob mob;
    private final double speed;
    @Nullable
    private Player owner;
    private int recalcCooldown;

    public CapsuleFollowOwnerGoal(Mob mob) {
        this(mob, 1.1D);
    }

    public CapsuleFollowOwnerGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** AI再適用時の重複登録防止用。 */
    public boolean isFor(Mob other) {
        return this.mob == other;
    }

    @Override
    public boolean canUse() {
        Player player = CapsuleRules.getOwnerPlayer(mob.level(), mob);
        if (player == null || player.isSpectator()) return false;
        if (mob.distanceTo(player) < 6.0D) return false;
        this.owner = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (owner == null || !owner.isAlive() || owner.isSpectator()) return false;
        if (CapsuleRules.getOwnerPlayer(mob.level(), mob) != owner) return false;
        return mob.distanceTo(owner) > 4.0D;
    }

    @Override
    public void start() {
        recalcCooldown = 0;
    }

    @Override
    public void stop() {
        owner = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (owner == null) return;
        mob.getLookControl().setLookAt(owner, 10.0F, (float) mob.getMaxHeadXRot());
        double dist = mob.distanceTo(owner);
        // 離れすぎたら所有者付近へテレポート (オオカミ相当)
        if (dist > 16.0D && mob.onGround()) {
            tryTeleportNear(owner);
            return;
        }
        if (--recalcCooldown <= 0) {
            recalcCooldown = 10;
            mob.getNavigation().moveTo(owner, speed);
        }
    }

    private void tryTeleportNear(Player player) {
        var random = mob.getRandom();
        for (int i = 0; i < 10; i++) {
            int dx = random.nextInt(5) - 2;
            int dz = random.nextInt(5) - 2;
            double x = Math.floor(player.getX()) + 0.5D + dx;
            double z = Math.floor(player.getZ()) + 0.5D + dz;
            double y = player.getY();
            var pos = new net.minecraft.core.BlockPos((int) Math.floor(x), (int) Math.floor(y),
                    (int) Math.floor(z));
            var level = mob.level();
            if (!level.getBlockState(pos).isSolidRender(level, pos)
                    && level.noCollision(mob, mob.getBoundingBox().move(x - mob.getX(),
                            y - mob.getY(), z - mob.getZ()))) {
                mob.randomTeleport(x, y, z, true);
                mob.getNavigation().stop();
                return;
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
