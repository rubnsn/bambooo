package ruby.bamboo.entity.companion;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 仲間エンティティ共通の Ride 制御。
 * 1.20.1 では Entity に共通の Ride インターフェースは無く、
 * 馬系は PlayerRideableJumping、Chair は別実装で分かれている。
 * ここで W/S 制御（前進加速/後退0.25倍）と旋回を共通化する。
 */
public final class CompanionRideUtil {

    private CompanionRideUtil() {}

    /**
     * @param mob 自身 (DolphinCompanion 等)
     * @param rider 操作プレイヤー
     * @param travelVector 本来の travel 引数 y 成分のみ使用
     * @param forwardScale 前進倍率 (dolphin 0.6, llama 0.7)
     * @return true なら制御を handle した (呼び元は super.travel を呼ばない)
     */
    public static boolean handleTravel(Mob mob, Player rider, Vec3 travelVector, float forwardScale) {
        if (!mob.isAlive() || !mob.isVehicle()) return false;
        // set rotation from rider
        mob.setYRot(rider.getYRot());
        mob.yRotO = mob.getYRot();
        mob.setXRot(rider.getXRot() * 0.5F);
        // setRot is protected - use setYRot/setXRot instead
        mob.yBodyRot = mob.getYRot();
        mob.yHeadRot = mob.yBodyRot;

        float f = rider.zza;
        float f1 = rider.xxa;
        if (f <= 0.0F) f *= 0.25F;
        else f *= forwardScale;
        f1 *= 0.5F;
        // caller handles actual movement; we just provide normalized f/f1
        // 呼び元で super.travel(new Vec3(f1, travelVector.y, f)) 等を呼ぶ
        return true;
    }

    public static Vec3 toInputVec(float strafe, float forward, float y) {
        return new Vec3(strafe, y, forward);
    }
}
