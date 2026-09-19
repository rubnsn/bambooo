package ruby.bamboo.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import ruby.bamboo.capsule.CapsuleRules;

/**
 * 所有者を攻撃した敵性モンスターへの反撃 (対象は Monster に限定し、
 * プレイヤー・友好Mobへの加害を起こさない)。
 */
public class CapsuleOwnerHurtTargetGoal extends TargetGoal {

    private final Mob mob;
    private LivingEntity attacker;
    private int timestamp;

    public CapsuleOwnerHurtTargetGoal(Mob mob) {
        super(mob, false);
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        Player owner = CapsuleRules.getOwnerPlayer(mob.level(), mob);
        if (owner == null) return false;
        LivingEntity hurtBy = owner.getLastHurtByMob();
        if (hurtBy == null || !hurtBy.isAlive()) return false;
        if (!(hurtBy instanceof Monster)) return false;
        if (hurtBy == mob) return false;
        if (CapsuleRules.isReleased(hurtBy)) return false;
        int ts = owner.getLastHurtByMobTimestamp();
        if (mob.level().getGameTime() - ts > 200L) return false;
        this.attacker = hurtBy;
        this.timestamp = ts;
        return true;
    }

    @Override
    public void start() {
        mob.setTarget(attacker);
        this.targetMob = attacker;
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        if (attacker == null || !attacker.isAlive()) return false;
        if (!(attacker instanceof Monster)) return false;
        return super.canContinueToUse();
    }

    public int getTimestamp() {
        return timestamp;
    }
}
