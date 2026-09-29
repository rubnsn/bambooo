package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * ガチャポンの BlockEntity。
 * <p>
 * 回転・レバーはクライアント演出のみで NBT 同期なし。
 * コイン投入状態 ({@code hasCoin}) のみサーバー権威で保持・同期する
 * (分離式: コイン投入 → 素手でハンドル操作)。
 */
public class GachaBlockEntity extends BlockEntity {
    /** 回転台の角度 (度) */
    public float rotor;
    public float prevRotor;
    /** レバーの引き角 (度、0=待機)。blockEvent(id=1) で90に立つ */
    public float lever;
    public float prevLever;

    /** コイン投入済みか (サーバー権威・永続化あり) */
    private boolean hasCoin;

    public GachaBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.GACHA_BE.get(), pos, state);
    }

    public boolean hasCoin() {
        return this.hasCoin;
    }

    /** サーバー側のみで呼ぶこと。変更後は setChanged + sendBlockUpdated を行う。 */
    public void setHasCoin(boolean hasCoin) {
        this.hasCoin = hasCoin;
        this.setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GachaBlockEntity be) {
        be.prevRotor = be.rotor;
        be.prevLever = be.lever;
        // 待機時はゆっくり回転 (1.0度/tick)。360で折り返さない:
        // 折り返すと描画のlerpが周回ごとに逆戻りして1フレームだけ跳ぶ
        be.rotor += 1.0F;
        if (be.lever > 0.0F) {
            be.lever = Math.max(0.0F, be.lever - 6.0F);
        }
    }

    /** レバーを引く (クライアント演出用)。 */
    public void pullLever() {
        this.lever = 90.0F;
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        // サーバー use からの blockEvent(id=1) でハンドル演出
        if (id == 1) {
            this.lever = 90.0F;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("HasCoin", this.hasCoin);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.hasCoin = tag.getBoolean("HasCoin");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("HasCoin", this.hasCoin);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.hasCoin = tag.getBoolean("HasCoin");
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }
}
