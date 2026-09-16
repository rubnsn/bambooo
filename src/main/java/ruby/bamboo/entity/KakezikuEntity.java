package ruby.bamboo.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ruby.bamboo.BambooMod;

/**
 * 掛け軸 (旧 EntityKakeziku の1.20.1移植)。
 * <p>
 * 旧は Entity 直継承の自前壁掛け実装だったが、1.20.1では
 * バニラ絵画と同型の {@link HangingEntity} を継承し、壁裏判定・
 * ピストン破壊・落下物化をバニラ機構に委譲する。
 * 柄は旧24柄 ({@link KakezikuMotive}) から設置面に収まるものをランダム選択。
 * <p>
 * 1.21 相違点 (§5):
 * <ul>
 * <li>1.21 の {@code HangingEntity} は {@code BlockAttachedEntity} 継承に変更。
 * {@code getWidth/getHeight} は廃止され、抽象 {@code calculateBoundingBox} を実装する
 * (バニラ {@code Painting} と同型。厚み 1px・頂点揃えは 1.20.1 と同一計算)。</li>
 * <li>{@code defineSynchedData(SynchedEntityData.Builder)} 形式。</li>
 * <li>向きはスポーンパケットの data 欄 (3D値) で同期する (バニラ絵画と同型)。
 * 柄は EntityData で同期し、{@code onSyncedDataUpdated} で bbox 追従する。</li>
 * <li>{@code BambooEntities}/{@code BambooItems} 直接参照をやめ、
 * {@code BuiltInRegistries} 参照に変更 (登録は親が配線)。</li>
 * </ul>
 */
public class KakezikuEntity extends HangingEntity {

    private static final EntityDataAccessor<Integer> DATA_MOTIVE = SynchedEntityData.defineId(KakezikuEntity.class,
            EntityDataSerializers.INT);

    public KakezikuEntity(EntityType<? extends KakezikuEntity> type, Level level) {
        super(type, level);
    }

    /**
     * 登録済み EntityType をレジストリから解決する。
     * 親が {@code BambooEntities.KAKEZIKU} ("kakeziku") を登録後に有効になる。
     */
    @SuppressWarnings("unchecked")
    public static EntityType<KakezikuEntity> registryType() {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .get(ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "kakeziku"));
        return (EntityType<KakezikuEntity>) type;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MOTIVE, KakezikuMotive.TATU.ordinal());
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
        // 頂点揃え: 2マス柄・3マス柄とも上端 = pos.y+2 (calculateBoundingBox の中心計算による)。
        // よって pos.y = topY-1 でどちらの柄も頂点が揃う (1.20.1 と同一)。
        BlockPos base = new BlockPos(clickedPos.getX() + face.getStepX(), topY - 1,
                clickedPos.getZ() + face.getStepZ());
        final int wallDepth = depth;
        List<KakezikuMotive> fitting = new ArrayList<>();
        for (KakezikuMotive motive : KakezikuMotive.values()) {
            if (motive.sizeY / 16 > wallDepth) {
                continue;
            }
            KakezikuEntity probe = new KakezikuEntity(registryType(), level);
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
        KakezikuEntity entity = new KakezikuEntity(registryType(), level);
        entity.setMotive(picked);
        entity.setHangPosition(base, face);
        return Optional.of(entity);
    }

    /**
     * バウンディングボックス計算 (バニラ Painting と同型)。
     * 幅1・高さ2/3・厚み1px。偶数高さは+0.5補正のため、
     * 2マス柄・3マス柄とも上端 = pos.y+2 になる。
     */
    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction dir) {
        KakezikuMotive motive = this.getMotive();
        double w = motive.sizeX / 16.0D;
        double h = motive.sizeY / 16.0D;
        Vec3 center = Vec3.atCenterOf(pos).relative(dir, -0.46875D);
        double dx = w % 2.0D == 0.0D ? 0.5D : 0.0D;
        double dy = h % 2.0D == 0.0D ? 0.5D : 0.0D;
        Vec3 c = center.relative(dir.getCounterClockWise(), dx).relative(Direction.UP, dy);
        Direction.Axis axis = dir.getAxis();
        double sx = axis == Direction.Axis.X ? 0.0625D : w;
        double sz = axis == Direction.Axis.Z ? 0.0625D : w;
        return AABB.ofSize(c, sx, h, sz);
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
    public void dropItem(@Nullable Entity brokenBy) {
        ItemStack drop = getItemStack();
        if (!drop.isEmpty()) {
            this.spawnAtLocation(drop);
        }
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
        // バニラ絵画と同型: 柄→向きの順で確定させ、pos 読込後に setDirection で bbox 再計算
        this.setMotive(KakezikuMotive.byTitle(tag.getString("Motive")));
        this.direction = Direction.from2DDataValue(tag.getByte("Facing"));
        super.readAdditionalSaveData(tag);
        this.setDirection(this.direction);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        // 向きを同梱する (バニラ絵画と同型)。柄は EntityData 同期で届く。
        return new ClientboundAddEntityPacket(this, this.direction.get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // バニラ絵画と同型。同期ズレ時のbbox追従用
        if (DATA_MOTIVE.equals(key)) {
            this.recalculateBoundingBox();
        }
    }

    @Override
    public ItemStack getPickResult() {
        return getItemStack();
    }

    /**
     * 掛け軸アイテムをレジストリから解決する。
     * 親が {@code BambooItems} に {@code kakeziku} を登録後に有効になる。
     * 未登録時は EMPTY (破壊ドロップなし・PickBlock なしとして振る舞う)。
     */
    private ItemStack getItemStack() {
        Item item = BuiltInRegistries.ITEM
                .get(ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "kakeziku"));
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }
}
