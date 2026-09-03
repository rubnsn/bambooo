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
    private static final String KEY_GROWTH_SUFFIX = "_gr";

    /** 成長率の初期値・最小・最大 (100 = 経験値量の100%吸収)。 */
    public static final int GROWTH_START = 100;
    public static final int GROWTH_MIN = 10;
    public static final int GROWTH_MAX = 300;

    /**
     * xp 固定小数点スケール。内部値は百分率 (1xp = 100) で保持し、整数演算のみで扱う。
     * 互換性維持なし (旧値は百分率として読み替える)。
     */
    public static final int XP_SCALE = 100;

    private final Map<SkillType, Integer> level = new EnumMap<>(SkillType.class);
    private final Map<SkillType, Integer> xp = new EnumMap<>(SkillType.class);
    private final Map<SkillType, Integer> max = new EnumMap<>(SkillType.class);
    /** スキル別成長率 (既定100)。 */
    private final Map<SkillType, Integer> growth = new EnumMap<>(SkillType.class);
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
            growth.put(t, GROWTH_START);
        }
    }

    public int getLevel(SkillType type) {
        return level.getOrDefault(type, 0);
    }

    public int getXp(SkillType type) {
        return xp.getOrDefault(type, 0);
    }

    /** 表示用整数部 (小数点以下切捨て)。 */
    public int getXpWhole(SkillType type) {
        return getXp(type) / XP_SCALE;
    }

    /** 0.0〜1.0 の進行率。 */
    public double getProgress(SkillType type) {
        return Math.min(1.0D, (double) getXp(type) / Math.max(1, getNext(type) * XP_SCALE));
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

    /** 成長率 (100 = 全量吸収)。スキル別。 */
    public int getGrowth(SkillType type) {
        return growth.getOrDefault(type, GROWTH_START);
    }

    /** 成長率設定。10〜300にクランプ (回復手段は別途)。 */
    public void setGrowth(SkillType type, int v) {
        growth.put(type, Math.max(GROWTH_MIN, Math.min(v, GROWTH_MAX)));
    }

    public boolean isMaxed(SkillType type) {
        return getLevel(type) >= getMaxLevel(type);
    }

    public void setLevel(SkillType type, int lv) {
        level.put(type, Math.max(0, Math.min(lv, getMaxLevel(type))));
        xp.put(type, 0);
    }

    /**
     * xp 加算 (整数xp)。未所持・カンスト時は無視。
     * `xp >= next*100` で自動上昇し、超過は切捨て・0戻し。
     *
     * @return 上昇したら true
     */
    public boolean addXp(SkillType type, int amount) {
        return addXpScaled(type, (long) amount * XP_SCALE);
    }

    /**
     * xp 加算 (小数xp、0.01精度)。内部では百分率整数で蓄積する。
     *
     * @return 上昇したら true
     */
    public boolean addXpDouble(SkillType type, double amount) {
        return addXpScaled(type, Math.round(amount * XP_SCALE));
    }

    private boolean addXpScaled(SkillType type, long scaled) {
        if (scaled <= 0 || !isAcquired(type) || isMaxed(type)) {
            return false;
        }
        int g = getGrowth(type);
        // 成長率で吸収 (小数点以下切捨て)。成長率10 = 10%のみ。
        long eff = scaled * g / 100L;
        if (eff <= 0) {
            return false;
        }
        // 得るごとに減衰: 100超は2%減、100以下は1ずつ減 (切上げなし、最小10)。
        if (g > GROWTH_START) {
            setGrowth(type, (int) (g * 98L / 100L));
        } else {
            setGrowth(type, g - 1);
        }
        long cur = (long) getXp(type) + eff;
        if (cur >= (long) getNext(type) * XP_SCALE) {
            level.put(type, getLevel(type) + 1);
            xp.put(type, 0);
            return true;
        }
        xp.put(type, (int) Math.min(cur, Integer.MAX_VALUE));
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
            tag.putInt(t.getId() + KEY_GROWTH_SUFFIX, getGrowth(t));
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
            String grKey = t.getId() + KEY_GROWTH_SUFFIX;
            if (tag.contains(grKey)) {
                setGrowth(t, tag.getInt(grKey));
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
