package ruby.bamboo.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.network.KaginawaStateManager;

import java.util.UUID;

/**
 * 刀用鈎縄フックエンティティ (単一右クリック仕様)。
 * 飛行→固着→拘束。ロープ長は SynchedEntityData で同期、拘束はサーバtickでプレイヤーへ適用。
 */
public class KaginawaHookEntity extends Entity {

    private static final EntityDataAccessor<Boolean> DATA_ANCHORED = SynchedEntityData.defineId(KaginawaHookEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_ROPE_LENGTH = SynchedEntityData.defineId(KaginawaHookEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ANCHOR_X = SynchedEntityData.defineId(KaginawaHookEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ANCHOR_Y = SynchedEntityData.defineId(KaginawaHookEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ANCHOR_Z = SynchedEntityData.defineId(KaginawaHookEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_OWNER_ID = SynchedEntityData.defineId(KaginawaHookEntity.class, EntityDataSerializers.INT);

    private static final float MAX_LENGTH = 30.0F;
    private static final float MIN_LENGTH = 2.0F;
    private static final float INITIAL_REEL_OUT = 0.22F;
    private static final float INITIAL_REEL_IN = 0.28F;
    // 巻取り(Space)でアンカーへ引き寄せる速度
    private static final float REEL_PULL_SPEED = 0.45F;

    // owner
    private Player owner;
    private UUID ownerUUID;

    // anchor
    private Vec3 anchor = Vec3.ZERO;
    private boolean anchored = false;
    private float ropeLength = 12.0F;

    // input pending (set from packet)
    private byte pendingReelDir = 0;
    private boolean pendingPull = false;
    private float pendingForward = 0;
    private float pendingStrafe = 0;
    private boolean pendingSprint = false;

    // auto pumping
    private double prevDist = 0;
    private int ticksAnchored = 0;

    public KaginawaHookEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public KaginawaHookEntity(Level level, Player owner) {
        this(BambooEntities.KAGINAWA_HOOK.get(), level);
        this.owner = owner;
        this.ownerUUID = owner.getUUID();
        this.setOwner(owner);
    }

    // owner helper (Projectile相当)
    public void setOwner(Player player) {
        this.owner = player;
        if (player != null) {
            this.ownerUUID = player.getUUID();
            if (this.entityData != null) {
                try {
                    this.entityData.set(DATA_OWNER_ID, player.getId());
                } catch (Exception ignored) {
                }
            }
        }
    }

    public Player getOwnerPlayer() {
        if (owner != null && !owner.isRemoved()) {
            return owner;
        }
        // try via synched owner id (works on both sides)
        int id = -1;
        try {
            id = this.entityData.get(DATA_OWNER_ID);
        } catch (Exception ignored) {
        }
        if (id != -1) {
            Entity e = level().getEntity(id);
            if (e instanceof Player p) {
                owner = p;
                if (ownerUUID == null) {
                    ownerUUID = p.getUUID();
                }
                return p;
            }
        }
        if (ownerUUID != null && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            Entity e = sl.getEntity(ownerUUID);
            if (e instanceof Player p) {
                owner = p;
                return p;
            }
        }
        // client fallback: scan nearby players
        try {
            for (Player p : level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(64))) {
                if (p.getUUID().equals(ownerUUID)) {
                    owner = p;
                    return p;
                }
            }
        } catch (Exception ignored) {
        }
        return owner;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_ANCHORED, false);
        this.entityData.define(DATA_ROPE_LENGTH, 12.0F);
        this.entityData.define(DATA_ANCHOR_X, 0.0F);
        this.entityData.define(DATA_ANCHOR_Y, 0.0F);
        this.entityData.define(DATA_ANCHOR_Z, 0.0F);
        this.entityData.define(DATA_OWNER_ID, -1);
    }

    public boolean isAnchored() {
        return this.entityData.get(DATA_ANCHORED);
    }

    private void setAnchoredSynced(boolean v) {
        this.entityData.set(DATA_ANCHORED, v);
    }

    public float getRopeLength() {
        return this.entityData.get(DATA_ROPE_LENGTH);
    }

    private void setRopeLengthSynced(float v) {
        this.entityData.set(DATA_ROPE_LENGTH, v);
        this.ropeLength = v;
    }

    public Vec3 getAnchor() {
        if (level().isClientSide) {
            float x = this.entityData.get(DATA_ANCHOR_X);
            float y = this.entityData.get(DATA_ANCHOR_Y);
            float z = this.entityData.get(DATA_ANCHOR_Z);
            // 未同期なら内部anchor
            if (x == 0 && y == 0 && z == 0 && anchor != Vec3.ZERO) {
                return anchor;
            }
            return new Vec3(x, y, z);
        }
        return anchor;
    }

    private void setAnchorSynced(Vec3 v) {
        this.anchor = v;
        this.entityData.set(DATA_ANCHOR_X, (float) v.x);
        this.entityData.set(DATA_ANCHOR_Y, (float) v.y);
        this.entityData.set(DATA_ANCHOR_Z, (float) v.z);
    }

    public void handleInput(byte reelDir, boolean pull, float forward, float strafe, boolean sprint) {
        this.pendingReelDir = reelDir;
        this.pendingPull = pull;
        this.pendingForward = Mth.clamp(forward, -1, 1);
        this.pendingStrafe = Mth.clamp(strafe, -1, 1);
        this.pendingSprint = sprint;
    }

    public void shootFromPlayer(Player player, float pitch, float yaw, float velocity, float inaccuracy) {
        float f = -Mth.sin(yaw * ((float) Math.PI / 180F)) * Mth.cos(pitch * ((float) Math.PI / 180F));
        float f1 = -Mth.sin(pitch * ((float) Math.PI / 180F));
        float f2 = Mth.cos(yaw * ((float) Math.PI / 180F)) * Mth.cos(pitch * ((float) Math.PI / 180F));
        shoot(f, f1, f2, velocity, inaccuracy);
    }

    public void shoot(double dx, double dy, double dz, float velocity, float inaccuracy) {
        Vec3 vec = (new Vec3(dx, dy, dz)).normalize()
                .add(this.random.triangle(0.0D, 0.0172275D * (double) inaccuracy),
                        this.random.triangle(0.0D, 0.0172275D * (double) inaccuracy),
                        this.random.triangle(0.0D, 0.0172275D * (double) inaccuracy))
                .scale(velocity);
        this.setDeltaMovement(vec);
        double d0 = vec.horizontalDistance();
        this.setYRot((float) (Mth.atan2(vec.x, vec.z) * (double) (180F / (float) Math.PI)));
        this.setXRot((float) (Mth.atan2(vec.y, d0) * (double) (180F / (float) Math.PI)));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    public void tick() {
        super.tick();

        // owner id sync (ensure client knows owner)
        if (owner != null && this.entityData.get(DATA_OWNER_ID) == -1) {
            this.entityData.set(DATA_OWNER_ID, owner.getId());
        }

        Player player = getOwnerPlayer();

        // ownerがいなくなったら消滅
        if (!level().isClientSide) {
            if (player == null || player.isRemoved() || !player.isAlive()) {
                discardWithCleanup();
                return;
            }
            // 距離離れすぎたら回収 (64m)
            if (player.distanceTo(this) > 64.0F) {
                discardWithCleanup();
                return;
            }
            // プレイヤーが別ディメンションなら消す
            if (player.level() != this.level()) {
                discardWithCleanup();
                return;
            }
            // 手持ちが刀でなくなったら外れる (飛行・固着共)
            if (player != null && !isHoldingKatana(player)) {
                discardWithCleanup();
                return;
            }
        } else {
            // クライアント側も手持ちチェックで即時回収感を出す (固着中はtickAnchoredでもチェック)
            if (player != null && !isHoldingKatana(player) && tickCount > 5) {
                // サーバ同期待ちだが、見た目上はそのまま (discardはサーバからのパケで)
                // 何もしない — 必要なら this.discard() しても良いがサーバ権限を優先
            }
        }

        if (!isAnchored()) {
            tickFlying(player);
        } else {
            tickAnchored(player);
        }

        // 飛行寿命: 100tick超で未固着なら消滅 (30mを1.8速度で16tickで到達、余裕を持って)
        if (!isAnchored() && tickCount > 100) {
            if (!level().isClientSide) {
                discardWithCleanup();
            }
        }

        // 固着後放置: 600tick (30秒)で消滅はしない—継続。ただしプレイヤーがしゃがみ等で遠ざかっても維持
        // ただしropeLengthがMAXを超えても維持 (伸長可能)

        // クライアント側ではprevDist更新のみ
        if (level().isClientSide && isAnchored() && player != null) {
            Vec3 eye = player.getEyePosition();
            prevDist = getAnchor().distanceTo(eye);
        }
    }

    private void tickFlying(Player player) {
        Vec3 pos = this.position();
        Vec3 motion = this.getDeltaMovement();

        // 次位置のヒット判定
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        // ブロックのみ刺さる仕様なのでEntityヒットは無視 (跳ね返さない)
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            Vec3 hitPos = blockHit.getLocation();
            // 法線方向に少し浮かせて埋没防止
            Vec3 normal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            Vec3 anchorPos = hitPos.add(normal.scale(0.05));
            // 空気ブロック等は無視? 固着可能なブロックかチェック: 1.20.1では getBlockStateで isAir判定
            BlockPos bp = blockHit.getBlockPos();
            if (!level().isClientSide) {
                if (level().getBlockState(bp).isAir()) {
                    // 空気なら刺さらず継続
                } else {
                    // 固着
                    this.anchor = anchorPos;
                    this.anchored = true;
                    setAnchoredSynced(true);
                    setAnchorSynced(anchorPos);
                    this.setPos(anchorPos);
                    this.setDeltaMovement(Vec3.ZERO);
                    // 初期ロープ長は現在の距離
                    if (player != null) {
                        Vec3 eye = player.getEyePosition();
                        float len = (float) anchorPos.distanceTo(eye);
                        len = Mth.clamp(len, MIN_LENGTH, MAX_LENGTH);
                        this.ropeLength = len;
                        setRopeLengthSynced(len);
                        prevDist = len;
                        ticksAnchored = 0;
                    } else {
                        setRopeLengthSynced(12.0F);
                    }
                    level().playSound(null, anchorPos.x, anchorPos.y, anchorPos.z, SoundEvents.CROSSBOW_HIT, SoundSource.PLAYERS, 0.8F, 1.2F);
                    // 落下抑制準備
                    if (player != null) {
                        player.fallDistance = 0;
                    }
                }
            } else {
                // クライアント側でも見た目用に固着表示 (サーバ同期まで待つが、予測で固着)
                // 何もしない—サーバ同期を待つ
            }
            return;
        } else if (hitResult.getType() == HitResult.Type.ENTITY) {
            // エンティティには刺さらない—貫通
            // 何もしない、継続
        }

        // 移動
        this.setPos(pos.add(motion));
        // 重力・空気抵抗
        Vec3 newMotion = motion.scale(0.99).add(0, -0.02, 0);
        this.setDeltaMovement(newMotion);

        // 回転更新
        double d0 = newMotion.horizontalDistance();
        this.setYRot((float) (Mth.atan2(newMotion.x, newMotion.z) * (double) (180F / (float) Math.PI)));
        this.setXRot((float) (Mth.atan2(newMotion.y, d0) * (double) (180F / (float) Math.PI)));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        // 地面/水中チェックは不要
    }

    protected boolean canHitEntity(Entity entity) {
        // ownerは数tick無視 (発射直後の自傷防止)
        if (entity == getOwnerPlayer() && tickCount < 5) {
            return false;
        }
        // ブロックのみなので常にfalseでも良いが、将来的にエンティティ刺突を有効にするならここで判定
        return false;
    }

    private static boolean isHoldingKatana(Player player) {
        // メイン/オフハンドのどちらかに刀があれば保持とみなす
        var main = player.getMainHandItem();
        var off = player.getOffhandItem();
        return main.getItem() instanceof ruby.bamboo.item.CommonKatana || off.getItem() instanceof ruby.bamboo.item.CommonKatana;
    }

    private void tickAnchored(Player player) {
        if (player == null) {
            return;
        }
        // 手持ちが刀でなくなったら外れる (要望)
        if (!isHoldingKatana(player)) {
            if (!level().isClientSide) {
                discardWithCleanup();
            }
            return;
        }
        // アンカー位置を同期値で更新 (クライアント)
        Vec3 anchorPos = getAnchor();
        if (!level().isClientSide) {
            anchorPos = this.anchor;
        } else {
            // クライアントでは同期anchorを使用
            this.anchor = anchorPos;
            this.ropeLength = getRopeLength();
        }

        ticksAnchored++;
        boolean isClient = level().isClientSide;
        // クライアントも物理を予測実行してデシンクを防ぐ (サーバは同期用にsetRopeLengthSynced)
        if (isClient) {
            player.fallDistance = 0;
        }

        // 入力処理: reel (Space=-1巻取り/近づく, Shift=1伸長/遠ざかる)
        // 地面に接している時はShift(伸長)を抑止。onGround/getOnPosはnoGravityで信用できないため POS Y-1(足元-0.1)の実ブロックで判定
        net.minecraft.core.BlockPos belowPos = net.minecraft.core.BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        boolean onSolidGround = !player.level().getBlockState(belowPos).isAir();
        if (pendingReelDir < 0) {
            float newLen = Mth.clamp(ropeLength + pendingReelDir * INITIAL_REEL_IN, MIN_LENGTH, MAX_LENGTH);
            ropeLength = newLen;
            if (!isClient) {
                setRopeLengthSynced(newLen);
            }
            // 巻取り中はアンカーへ引き寄せる速度を与える (フックショットの「近づく」)
            Vec3 eye0 = player.getEyePosition();
            Vec3 toAnchor0 = anchorPos.subtract(eye0);
            if (toAnchor0.lengthSqr() > 1e-6) {
                Vec3 pullDir = toAnchor0.normalize();
                player.setDeltaMovement(player.getDeltaMovement().add(pullDir.scale(REEL_PULL_SPEED)));
                player.hasImpulse = true;
            }
        } else if (pendingReelDir > 0 && !onSolidGround) {
            // Shift: 遠ざかる (ただし地面に着いているときは伸ばさない)
            float newLen = Mth.clamp(ropeLength + pendingReelDir * INITIAL_REEL_OUT, MIN_LENGTH, MAX_LENGTH);
            ropeLength = newLen;
            if (!isClient) {
                setRopeLengthSynced(newLen);
            }
        }
        
        if(!onSolidGround){
        // WASD 推力 (フックショット): 入力方向へ自動加速。接線方向へ投影してロープに沿って進む。
            if (pendingForward != 0 || pendingStrafe != 0) {
                Vec3 look = player.getLookAngle();
                Vec3 fwd = new Vec3(look.x, 0, look.z);
                if (fwd.lengthSqr() < 1e-6) {
                    fwd = new Vec3(0, 0, 1);
                } else {
                    fwd = fwd.normalize();
                }
                Vec3 right = fwd.cross(new Vec3(0, 1, 0));
                if (right.lengthSqr() < 1e-6) {
                    right = new Vec3(1, 0, 0);
                } else {
                    right = right.normalize();
                }
                // フックショット感を出すため強めの推力 (接線方向へ投影は不要—ロープ拘束が張力を与える)
                float f = pendingSprint ? 0.09F : 0.06F;
                Vec3 impulse = fwd.scale(pendingForward * f).add(right.scale(pendingStrafe * f));
                player.setDeltaMovement(player.getDeltaMovement().add(impulse));
                player.hasImpulse = true;
            }
        }

        prevDist = anchorPos.distanceTo(player.getEyePosition());

        // === ロープ拘束 ===
        // ロープに張力が働く間、径方向外向き速度のみ除去し、位置をロープ長の円弧上へ戻す。
        // 径方向だけの矯正なので接線(振り子の運動量)は壊さない。位置矯正で実距離とロープ長が
        // 乖離するとプレイヤーが離れていくため、毎tick完全に一致させる。
        // 重力はバニラ任せ (イベントハンドラが setNoGravity(false) を維持)。
        Vec3 eye = player.getEyePosition();
        Vec3 toAnchor = anchorPos.subtract(eye);
        double dist = toAnchor.length();
        if (dist > ropeLength - 0.05 && dist > 1e-6) {
            Vec3 radial = toAnchor.normalize();
            Vec3 curVel = player.getDeltaMovement();
            // 1) 径方向外向き速度を除去 (張力で止める)。内向き・接線は保存。
            double radialVel = curVel.dot(radial);
            if (radialVel > 0) {
                Vec3 tangentVel = curVel.subtract(radial.scale(radialVel));
                player.setDeltaMovement(tangentVel);
                player.hasImpulse = true;
            }
            // 2) 位置を円弧上へ矯正 (radialは「眼→アンカー」方向なので正の向きに動かす)。
            if (dist > ropeLength) {
                Vec3 correction = radial.scale(dist - ropeLength);
                player.move(MoverType.SELF, correction); // 衝突判定付き (ブロック貫通防止)
                player.fallDistance = 0;
            }
        }
        // 最高速度クランプ: 安全上限
        Vec3 newVel = player.getDeltaMovement();
        double maxSpeed = pendingSprint ? 3.2 : 2.4;
        double speed = newVel.length();
        if (speed > maxSpeed && speed > 1e-6) {
            player.setDeltaMovement(newVel.normalize().scale(maxSpeed));
        }

        // 常に落下距離リセット
        player.fallDistance = 0;

        // 入力リセット (クライアントは毎tick再送するため、次のパケットで上書きされる)
        pendingReelDir = 0;
        pendingPull = false;
        pendingForward = 0;
        pendingStrafe = 0;
        pendingSprint = false;

        // 固着位置に自身を固定
        this.setPos(anchorPos);
        this.setDeltaMovement(Vec3.ZERO);
    }

    public void discardWithCleanup() {
        if (!level().isClientSide) {
            Player p = getOwnerPlayer();
            if (p != null) {
                KaginawaStateManager.remove(p);
            } else if (ownerUUID != null) {
                KaginawaStateManager.remove(ownerUUID);
            }
            KaginawaStateManager.removeHook(this);
        }
        this.discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        // 保存しない
        if (ownerUUID != null) {
            compound.putUUID("Owner", ownerUUID);
        }
        compound.putDouble("AnchorX", anchor.x);
        compound.putDouble("AnchorY", anchor.y);
        compound.putDouble("AnchorZ", anchor.z);
        compound.putFloat("RopeLength", ropeLength);
        compound.putBoolean("Anchored", isAnchored());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("Owner")) {
            ownerUUID = compound.getUUID("Owner");
        }
        anchor = new Vec3(compound.getDouble("AnchorX"), compound.getDouble("AnchorY"), compound.getDouble("AnchorZ"));
        ropeLength = compound.getFloat("RopeLength");
        anchored = compound.getBoolean("Anchored");
        // SyncedDataにも反映
        if (anchored) {
            setAnchoredSynced(true);
            setAnchorSynced(anchor);
            setRopeLengthSynced(ropeLength);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return super.getAddEntityPacket();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!level().isClientSide) {
            KaginawaStateManager.removeHook(this);
        }
    }
}
