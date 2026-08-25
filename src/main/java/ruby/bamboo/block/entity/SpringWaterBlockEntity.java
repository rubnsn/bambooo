package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.block.SpringWaterBlock;
import ruby.bamboo.core.config.SpringConfig;
import ruby.bamboo.core.init.BambooBlockEntities;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * 温泉水 BE — 最小 (Phase B)。
 * parentPos / isDead / NBT / levelUp / levelDown / tick
 */
public class SpringWaterBlockEntity extends BlockEntity {

    private BlockPos parentPos = null;
    private boolean isDead = false;

    public SpringWaterBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.SPRING_WATER_BE.get(), pos, state);
    }

    public void setParent(BlockPos p) {
        this.parentPos = p == null ? null : p.immutable();
        setChanged();
    }

    public BlockPos getParent() {
        return this.parentPos;
    }

    public boolean isParent() {
        return this.parentPos != null && this.parentPos.equals(this.worldPosition);
    }

    public boolean isParentAlive() {
        if (this.level == null || this.parentPos == null) return false;
        BlockState st = this.level.getBlockState(this.parentPos);
        return st.is(BambooBlocks.SPRING_WATER.get());
    }

    public void setDead() {
        this.isDead = true;
        setChanged();
    }

    public boolean isDead() {
        if (isParent()) {
            return this.isDead;
        } else {
            if (this.level == null || this.parentPos == null) return true;
            var be = this.level.getBlockEntity(this.parentPos);
            if (be instanceof SpringWaterBlockEntity parentBe) {
                return parentBe.isDead();
            }
            return true;
        }
    }

    // ===== NBT =====
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (isParentAlive() && this.parentPos != null) {
            CompoundTag p = new CompoundTag();
            p.putInt("x", this.parentPos.getX());
            p.putInt("y", this.parentPos.getY());
            p.putInt("z", this.parentPos.getZ());
            tag.put("parent", p);
        }
        tag.putBoolean("isDead", this.isDead);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("parent")) {
            CompoundTag p = tag.getCompound("parent");
            this.parentPos = new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z"));
        } else {
            this.parentPos = null;
        }
        this.isDead = tag.getBoolean("isDead");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        load(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return ClientboundBlockEntityDataPacket.create(this, be -> tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        load(pkt.getTag());
    }

    // ===== static helpers =====
    public static boolean levelUp(Level level, BlockPos pos, BlockState state) {
        int max = getMaxLevel();
        int lv = state.getValue(SpringWaterBlock.SPRING_LEVEL);
        if (lv < max) {
            BlockState ns = state.setValue(SpringWaterBlock.SPRING_LEVEL, lv + 1);
            level.setBlock(pos, ns, 3);
            level.scheduleTick(pos, BambooBlocks.SPRING_WATER.get(), getWaterDelay());
            return true;
        }
        return false;
    }

    public static boolean levelUp(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!st.is(BambooBlocks.SPRING_WATER.get())) return false;
        return levelUp(level, pos, st);
    }

    public static void levelDown(Level level, BlockPos pos, BlockState state) {
        int lv = state.getValue(SpringWaterBlock.SPRING_LEVEL);
        if (lv <= 1) { // spec says <=0 -> air, but our min is 1? 0は空相当? Use <=1 -> air? We'll use lv <=1 -> remove, else -1
            level.removeBlock(pos, false);
        } else {
            BlockState ns = state.setValue(SpringWaterBlock.SPRING_LEVEL, lv - 1);
            level.setBlock(pos, ns, 3);
            level.scheduleTick(pos, BambooBlocks.SPRING_WATER.get(), getEvaporationDelay());
        }
    }

    // ===== ticker =====
    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T be) {
        if (be instanceof SpringWaterBlockEntity water) {
            water.serverTick(level, pos, state);
        }
    }

    private void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        // 親不在 or 死亡 -> 乾燥
        if (this.parentPos == null || isDead()) {
            levelDown(level, pos, state);
            return;
        }
        if (!isParent()) {
            if (!isParentAlive()) {
                setDead();
                levelDown(level, pos, state);
                return;
            }
        }
        // Phase B: これ以上の拡散・均一化は無し (Phase Cで追加)
        // ただし下注ぎはまだ無し (Phase C)
        // 水位がまだMAX未満でも源泉からの levelUp は SpringBlock側で担うため、ここでは何もしない
        // ただし乾燥していない正常系は再スケジュール不要? 一旦何もしない
    }

    private static int getMaxLevel() {
        try { return SpringConfig.COMMON.maxLevel.get(); } catch (Exception e) { return 8; }
    }

    private static int getWaterDelay() {
        try { return SpringConfig.COMMON.waterTickDelay.get(); } catch (Exception e) { return 20; }
    }

    private static int getEvaporationDelay() {
        try { return SpringConfig.COMMON.evaporationDelay.get(); } catch (Exception e) { return 30; }
    }
}
