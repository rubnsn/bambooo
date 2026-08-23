package ruby.bamboo.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 布団用椅子エンティティ (旧 Chair 移植)。
 * <p>
 * 旧仕様: Entity直継承、当たり判定なし、NBT保存なし、乗客消失でsetDead、
 * 毎tick listener呼び出しで時間加速。
 * <p>
 * 1.20.1では tick() をoverride、discard()で削除、addAdditionalSaveData空で非永続化。
 */
public class ChairEntity extends Entity {

    private IChairUpdate listener = null;

    public ChairEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setListener(IChairUpdate listener) {
        this.listener = listener;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (this.getPassengers().isEmpty()) {
                this.discard();
            }
        }
        if (listener != null) {
            listener.apply(this.level(), this);
        }
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return super.getAddEntityPacket();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0D;
    }

    /**
     * 座った際の視点オフセット用。椅子自体は Y-1.3 に配置されるため、
     * 乗車時のプレイヤー位置は実質ブロック上面付近に来る。
     */
    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    public interface IChairUpdate {
        void apply(Level worldIn, Entity entity);
    }
}
