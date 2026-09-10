package ruby.bamboo.transform;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 変身状態の保存。空文字 = 人間(未変身)。
 * Entity名で引き直すため ResourceLocation 文字列のみ保持する。
 */
public class TransformStorage implements INBTSerializable<CompoundTag> {

    private String entityId = "";
    /** クリーパー自爆の fuse カウント(-1 = 待機中ではない)。 */
    private int creeperFuse = -1;
    private long creeperCd = 0L;
    private long enderCd = 0L;
    private long biteCd = 0L;
    private long splitCd = 0L;
    private int eggTimer = 0;
    private String lastFood = "";
    private long lastFoodTick = -1000L;
    private boolean sheared = false;

    public String getEntityId() {
        return entityId != null ? entityId : "";
    }

    public void setEntityId(String id) {
        this.entityId = id != null ? id : "";
        this.creeperFuse = -1;
        this.sheared = false;
    }

    public boolean isHuman() {
        return getEntityId().isEmpty();
    }

    public void clear() {
        setEntityId("");
    }

    public int getCreeperFuse() {
        return creeperFuse;
    }

    public void setCreeperFuse(int v) {
        creeperFuse = v;
    }

    public long getCreeperCd() {
        return creeperCd;
    }

    public void setCreeperCd(long v) {
        creeperCd = v;
    }

    public long getEnderCd() {
        return enderCd;
    }

    public void setEnderCd(long v) {
        enderCd = v;
    }

    public long getBiteCd() {
        return biteCd;
    }

    public void setBiteCd(long v) {
        biteCd = v;
    }

    public long getSplitCd() {
        return splitCd;
    }

    public void setSplitCd(long v) {
        splitCd = v;
    }

    public int getEggTimer() {
        return eggTimer;
    }

    public void setEggTimer(int v) {
        eggTimer = v;
    }

    public String getLastFood() {
        return lastFood;
    }

    public long getLastFoodTick() {
        return lastFoodTick;
    }

    public void setLastFood(String id, long tick) {
        lastFood = id != null ? id : "";
        lastFoodTick = tick;
    }

    public boolean isSheared() {
        return sheared;
    }

    public void setSheared(boolean v) {
        sheared = v;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("entity", getEntityId());
        tag.putInt("creeper_fuse", creeperFuse);
        tag.putLong("creeper_cd", creeperCd);
        tag.putLong("ender_cd", enderCd);
        tag.putLong("bite_cd", biteCd);
        tag.putLong("split_cd", splitCd);
        tag.putInt("egg_timer", eggTimer);
        tag.putString("last_food", lastFood);
        tag.putLong("last_food_tick", lastFoodTick);
        tag.putBoolean("sheared", sheared);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        entityId = tag.contains("entity") ? tag.getString("entity") : "";
        creeperFuse = tag.contains("creeper_fuse") ? tag.getInt("creeper_fuse") : -1;
        creeperCd = tag.contains("creeper_cd") ? tag.getLong("creeper_cd") : 0L;
        enderCd = tag.contains("ender_cd") ? tag.getLong("ender_cd") : 0L;
        biteCd = tag.contains("bite_cd") ? tag.getLong("bite_cd") : 0L;
        splitCd = tag.contains("split_cd") ? tag.getLong("split_cd") : 0L;
        eggTimer = tag.contains("egg_timer") ? tag.getInt("egg_timer") : 0;
        lastFood = tag.contains("last_food") ? tag.getString("last_food") : "";
        lastFoodTick = tag.contains("last_food_tick") ? tag.getLong("last_food_tick") : -1000L;
        sheared = tag.contains("sheared") && tag.getBoolean("sheared");
    }
}
