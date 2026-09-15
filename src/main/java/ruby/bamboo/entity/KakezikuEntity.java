package ruby.bamboo.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ruby.bamboo.core.init.BambooItems;

/**
 * 掛け軸 (旧 EntityKakeziku の1.20.1移植)。
 * <p>
 * 旧は Entity 直継承の自前壁掛け実装だったが、1.20.1では
 * バニラ絵画と同型の {@link HangingEntity} を継承し、壁裏判定・
 * ピストン破壊・落下物化をバニラ機構に委譲する。
 * 柄は旧24柄 ({@link KakezikuMotive}) から設置面に収まるものをランダム選択。
 */
public class KakezikuEntity extends HangingEntity {

    private static final EntityDataAccessor<Integer> DATA_MOTIVE = SynchedEntityData.defineId(KakezikuEntity.class,
            EntityDataSerializers.INT);

    public KakezikuEntity(EntityType<? extends KakezikuEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_MOTIVE, KakezikuMotive.TATU.ordinal());
    }

    public KakezikuMotive getMotive() {
        return KakezikuMotive.byOrdinal(this.entityData.get(DATA_MOTIVE));
    }

    public void setMotive(KakezikuMotive motive) {
        this.entityData.set(DATA_MOTIVE, motive.ordinal());
    }

    /** 壁位置と向きを設定する (motive 設定後に呼ぶこと) */
    public void setHangPosition(BlockPos pos, Direction direction) {
        this.pos = pos;
        this.setDirection(direction);
    }

    /**
     * 設置候補を生成する。クリックしたブロックを頂点とし、下方向の壁の長さで柄を絞る:
     * 下に3マスあれば3マス柄 (16x48) + 2マス柄 (16x32) からランダム、
     * 2マスしかなければ2マス柄のみ。1マス以下は設置不可。
     * (旧はクリック位置中心に収まる柄からランダムだったが、接地のクセが強いため頂点基準に変更)
     */
    public static Optional<KakezikuEntity> create(Level level, BlockPos clickedPos, Direction face) {
        if (face.getAxis().isVertical()) {
            return Optional.empty();
        }
        int topY = clickedPos.getY();
        // 背後の壁列: クリック位置を頂点に下へ3マス、頑丈面の連続を調べる
        int depth = 0;
        for (int i = 0; i < 3; i++) {
            BlockPos wallPos = new BlockPos(clickedPos.getX(), topY - i, clickedPos.getZ());
            if (level.getBlockState(wallPos).isFaceSturdy(level, wallPos, face)) {
                depth++;
            } else {
                break;
            }
        }
        if (depth < 2) {
            return Optional.empty();
        }
        // HangingEntity の中心計算 (高さ32は+1.0、高さ48は+0.5) では
        // どちらの柄も上端 = pos.y+2 になるため、pos.y = topY-1 で頂点揃えになる
        BlockPos base = new BlockPos(clickedPos.getX() + face.getStepX(), topY - 1,
                clickedPos.getZ() + face.getStepZ());
        final int wallDepth = depth;
        List<KakezikuMotive> fitting = new ArrayList<>();
        for (KakezikuMotive motive : KakezikuMotive.values()) {
            if (motive.sizeY / 16 > wallDepth) {
                continue;
            }
            KakezikuEntity probe = new KakezikuEntity(
                    ruby.bamboo.core.init.BambooEntities.KAKEZIKU.get(), level);
            probe.setMotive(motive);
            probe.setHangPosition(base, face);
            if (probe.survives() && level.noCollision(probe)) {
                fitting.add(motive);
            }
            probe.discard();
        }
        if (fitting.isEmpty()) {
            return Optional.empty();
        }
        KakezikuMotive picked = fitting.get(level.random.nextInt(fitting.size()));
        KakezikuEntity entity = new KakezikuEntity(
                ruby.bamboo.core.init.BambooEntities.KAKEZIKU.get(), level);
        entity.setMotive(picked);
        entity.setHangPosition(base, face);
        return Optional.of(entity);
    }

    @Override
    public int getWidth() {
        return this.getMotive().sizeX;
    }
    @Override
    public int getHeight() {
        return this.getMotive().sizeY;
    }

    /**
     * 壁裏チェックを無効化する。裏のブロックを壊しても落下・アイテム化しない。
     * 掛け軸同士の重なりチェックのみ維持する (設置時の fitting 判定用)。
     */
    @Override
    public boolean survives() {
        if (this.isRemoved()) {
            return false;
        }
        return this.level().getEntities(this, this.getBoundingBox(), HANGING_ENTITY).isEmpty();
    }

    @Override
    public void dropItem(Entity brokenBy) {
        this.spawnAtLocation(new ItemStack(BambooItems.KAKEZIKU.get()));
    }

    @Override
    public void playPlacementSound() {
        this.level().playSound(null, this.getPos(), SoundEvents.PAINTING_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Facing", (byte) this.direction.get2DDataValue());
        tag.putString("Motive", this.getMotive().title);
        tag.putInt("TileX", this.getPos().getX());
        tag.putInt("TileY", this.getPos().getY());
        tag.putInt("TileZ", this.getPos().getZ());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setMotive(KakezikuMotive.byTitle(tag.getString("Motive")));
        this.pos = new BlockPos(tag.getInt("TileX"), tag.getInt("TileY"), tag.getInt("TileZ"));
        this.setDirection(Direction.from2DDataValue(tag.getByte("Facing")));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        // 向き (下位3bit) + 柄ordinal (上位) を同梱する。
        // 柄がスポーン確定しないとクライアントは既定柄で描画→同期後に高さがスナップするため
        int data = this.direction.get3DDataValue() | (this.getMotive().ordinal() << 3);
        return new ClientboundAddEntityPacket(this, data, this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        // バニラ絵画と同型。super → setPos override 経由で整数アンカーが正確に入るため、
        // ここで blockPosition() を取り直してはならない (2マス柄はEntity Yが整数のため+1ズレる)。
        // 柄は bbox 計算に使うため setDirection より先に確定させる
        super.recreateFromPacket(packet);
        int data = packet.getData();
        this.setMotive(KakezikuMotive.byOrdinal(data >> 3));
        this.setDirection(Direction.from3DDataValue(data & 7));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // バニラ絵画と同型。同期ズレ時のbbox追従用
        if (key.equals(DATA_MOTIVE)) {
            this.recalculateBoundingBox();
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(BambooItems.KAKEZIKU.get());
    }
}
