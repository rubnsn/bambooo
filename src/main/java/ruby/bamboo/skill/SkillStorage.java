package ruby.bamboo.skill;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * プレイヤーごとのスキル保存 (feat-spec-skill §1)。
 * level 0 = 未所持。未所持スキルへの xp 加算は行わない。
 * 上昇時の超過 xp は切捨て・0戻し (自動・読書とも共通)。
 */
public class SkillStorage implements INBTSerializable<CompoundTag> {

    private static final String KEY_LEVEL_SUFFIX = "_lv";
    private static final String KEY_XP_SUFFIX = "_xp";
    private static final String KEY_MAX_SUFFIX = "_max";

    private final Map<SkillType, Integer> level = new EnumMap<>(SkillType.class);
    private final Map<SkillType, Integer> xp = new EnumMap<>(SkillType.class);
    private final Map<SkillType, Integer> max = new EnumMap<>(SkillType.class);
    /** 移動統計の前回値と積算 (cm)。Cap保持のためログアウト掃除不要。 */
    private int walkBase;
    private long walkAcc;
    private int swimBase;
    private long swimAcc;

    public SkillStorage() {
        for (SkillType t : SkillType.values()) {
            level.put(t, 0);
            xp.put(t, 0);
            max.put(t, SkillType.DEFAULT_MAX_LEVEL);
        }
    }

    public int getLevel(SkillType type) {
        return level.getOrDefault(type, 0);
    }

    public int getXp(SkillType type) {
        return xp.getOrDefault(type, 0);
    }

    public int getMaxLevel(SkillType type) {
        return max.getOrDefault(type, SkillType.DEFAULT_MAX_LEVEL);
    }

    public int getNext(SkillType type) {
        return SkillType.requiredFor(getLevel(type));
    }

    public boolean isAcquired(SkillType type) {
        return getLevel(type) > 0;
    }

    public boolean isMaxed(SkillType type) {
        return getLevel(type) >= getMaxLevel(type);
    }

    public void setLevel(SkillType type, int lv) {
        level.put(type, Math.max(0, Math.min(lv, getMaxLevel(type))));
        xp.put(type, 0);
    }

    /**
     * xp 加算。未所持・カンスト時は無視。
     * `xp >= next` で自動上昇し、超過は切捨て・0戻し。
     *
     * @return 上昇したら true
     */
    public boolean addXp(SkillType type, int amount) {
        if (amount <= 0 || !isAcquired(type) || isMaxed(type)) {
            return false;
        }
        int cur = getXp(type) + amount;
        if (cur >= getNext(type)) {
            level.put(type, getLevel(type) + 1);
            xp.put(type, 0);
            return true;
        }
        xp.put(type, cur);
        return false;
    }

    /** 読書成功時: Lv+1・xp=0 (貯蓄の切捨ては代償)。 */
    public boolean grantByBook(SkillType type) {
        if (isMaxed(type)) {
            return false;
        }
        level.put(type, getLevel(type) + 1);
        xp.put(type, 0);
        return true;
    }

    public int getWalkBase() {
        return walkBase;
    }

    public void setWalkBase(int v) {
        walkBase = v;
    }

    public long getWalkAcc() {
        return walkAcc;
    }

    public void setWalkAcc(long v) {
        walkAcc = Math.max(0, v);
    }

    public int getSwimBase() {
        return swimBase;
    }

    public void setSwimBase(int v) {
        swimBase = v;
    }

    public long getSwimAcc() {
        return swimAcc;
    }

    public void setSwimAcc(long v) {
        swimAcc = Math.max(0, v);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        for (SkillType t : SkillType.values()) {
            tag.putInt(t.getId() + KEY_LEVEL_SUFFIX, getLevel(t));
            tag.putInt(t.getId() + KEY_XP_SUFFIX, getXp(t));
            tag.putInt(t.getId() + KEY_MAX_SUFFIX, getMaxLevel(t));
        }
        tag.putInt("walk_base", walkBase);
        tag.putLong("walk_acc", walkAcc);
        tag.putInt("swim_base", swimBase);
        tag.putLong("swim_acc", swimAcc);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        for (SkillType t : SkillType.values()) {
            String lvKey = t.getId() + KEY_LEVEL_SUFFIX;
            String xpKey = t.getId() + KEY_XP_SUFFIX;
            String maxKey = t.getId() + KEY_MAX_SUFFIX;
            if (tag.contains(lvKey)) {
                level.put(t, Math.max(0, tag.getInt(lvKey)));
            }
            if (tag.contains(xpKey)) {
                xp.put(t, Math.max(0, tag.getInt(xpKey)));
            }
            if (tag.contains(maxKey)) {
                int m = tag.getInt(maxKey);
                max.put(t, m > 0 ? m : SkillType.DEFAULT_MAX_LEVEL);
            }
        }
        if (tag.contains("walk_base")) {
            walkBase = tag.getInt("walk_base");
        }
        if (tag.contains("walk_acc")) {
            walkAcc = Math.max(0, tag.getLong("walk_acc"));
        }
        if (tag.contains("swim_base")) {
            swimBase = tag.getInt("swim_base");
        }
        if (tag.contains("swim_acc")) {
            swimAcc = Math.max(0, tag.getLong("swim_acc"));
        }
    }
}
