package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * 温泉水 BE — Phase C: 水平均一化 + 半径32ガード + reparent + 溢流 + 下注ぎ
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
        if (lv <= 1) {
            level.removeBlock(pos, false);
        } else {
            BlockState ns = state.setValue(SpringWaterBlock.SPRING_LEVEL, lv - 1);
            level.setBlock(pos, ns, 3);
            level.scheduleTick(pos, BambooBlocks.SPRING_WATER.get(), getEvaporationDelay());
        }
    }

    // ===== manhattan XZ =====
    public static int manhattanXZ(BlockPos a, BlockPos b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private int getMaxRadius() {
        try { return SpringConfig.COMMON.maxSpreadRadius.get(); } catch (Exception e) { return 32; }
    }

    // ===== reparent =====
    public void reparentToNearest() {
        if (this.level == null || this.parentPos == null) return;
        int maxR = getMaxRadius();
        BlockPos bestParent = this.parentPos;
        int bestDist = manhattanXZ(this.worldPosition, bestParent);
        for (Direction dir : Direction.values()) {
            BlockPos off = this.worldPosition.relative(dir);
            BlockState offState = this.level.getBlockState(off);
            if (!offState.is(BambooBlocks.SPRING_WATER.get())) continue;
            var be = this.level.getBlockEntity(off);
            if (!(be instanceof SpringWaterBlockEntity offWater)) continue;
            BlockPos candParent = offWater.getParent();
            if (candParent == null) continue;
            int d = manhattanXZ(this.worldPosition, candParent);
            if (d < bestDist) {
                if (maxR != 0 && d > maxR) continue;
                bestDist = d;
                bestParent = candParent;
            }
        }
        if (!bestParent.equals(this.parentPos)) {
            setParent(bestParent);
        }
    }

    // ===== 溢流 =====
    public void trySpreadHorizontally(Level level, BlockPos pos, BlockState state) {
        int lv = state.getValue(SpringWaterBlock.SPRING_LEVEL);
        if (lv != getMaxLevel()) return;
        int maxR = getMaxRadius();
        BlockPos parent = this.parentPos;
        if (parent == null) return;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos cand = pos.relative(dir);
            BlockState candState = level.getBlockState(cand);
            if (candState.is(BambooBlocks.SPRING_WATER.get())) continue;
            boolean empty = level.isEmptyBlock(cand) || candState.canBeReplaced();
            if (!empty) continue;
            if (maxR != 0 && manhattanXZ(cand, parent) > maxR) {
                // ガード外はスキップ、半径超の警告は省略
                continue;
            }
            BlockState ns = BambooBlocks.SPRING_WATER.get().defaultBlockState().setValue(SpringWaterBlock.SPRING_LEVEL, 1);
            level.setBlock(cand, ns, 3);
            var be = level.getBlockEntity(cand);
            if (be instanceof SpringWaterBlockEntity water) {
                water.setParent(parent);
            }
            level.scheduleTick(cand, BambooBlocks.SPRING_WATER.get(), getWaterDelay());
            break; // 1つだけ
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
            // reparent every 5 ticks
            int interval = getReparentInterval();
            if (level.getGameTime() % interval == 0) {
                reparentToNearest();
            }
            // 水平均一化: 最も低い兄弟を+1、ただし距離ガード
            int maxR = getMaxRadius();
            BlockPos best = null;
            int bestLv = Integer.MAX_VALUE;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos off = pos.relative(dir);
                BlockState offState = level.getBlockState(off);
                if (!offState.is(BambooBlocks.SPRING_WATER.get())) continue;
                if (maxR != 0 && manhattanXZ(off, this.parentPos) > maxR) continue;
                int offLv = offState.getValue(SpringWaterBlock.SPRING_LEVEL);
                if (offLv < bestLv) {
                    bestLv = offLv;
                    best = off;
                }
            }
            if (best != null) {
                levelUp(level, best);
            }
        }

        // 下注ぎ vs 溢流
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        int maxR = getMaxRadius();
        boolean belowIsWater = belowState.is(BambooBlocks.SPRING_WATER.get());
        boolean within = maxR == 0 || manhattanXZ(below, this.parentPos) <= maxR;
        if (belowIsWater && within) {
            levelUp(level, below, belowState);
        } else {
            trySpreadHorizontally(level, pos, state);
        }

        // 自身の再スケジュール（乾燥で消えていなければ）
        if (level.getBlockState(pos).is(BambooBlocks.SPRING_WATER.get())) {
            // 既に levelUp/Down でスケジュールされているが、拡散が無かった場合も維持のため再スケジュール
            // 重複スケジュールは Forge が無視するため安全
            // ここでは生存中は waterDelay で維持
            // level.scheduleTick(pos, BambooBlocks.SPRING_WATER.get(), getWaterDelay());
        }
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

    private static int getReparentInterval() {
        try { return SpringConfig.COMMON.reparentInterval.get(); } catch (Exception e) { return 5; }
    }
}
