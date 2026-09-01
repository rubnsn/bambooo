package ruby.bamboo.core.fishing;

/**
 * 釣りのサイズクラス。仕様書 §3 のとおり NBT はこの 3 値のみ。
 */
public enum FishSize {
    MIN("min", 0),
    NORMAL("normal", 1),
    BIG("big", 2);

    public static final String TAG_KEY = "bamboo_size_class";

    public final String tagValue;
    public final int index;

    FishSize(String tagValue, int index) {
        this.tagValue = tagValue;
        this.index = index;
    }

    public static FishSize fromTag(String value) {
        if (value == null) return NORMAL;
        for (FishSize s : values()) {
            if (s.tagValue.equals(value)) return s;
        }
        return NORMAL;
    }

    public static FishSize fromIndex(int i) {
        if (i < 0 || i >= values().length) return NORMAL;
        return values()[i];
    }

    /**
     * 代表 cm を CM 三つ組から取得する (min->最小, normal->中央, big->最大)。
     */
    public int representativeCm(int minCm, int midCm, int maxCm) {
        return switch (this) {
            case MIN -> minCm;
            case NORMAL -> midCm;
            case BIG -> maxCm;
        };
    }
}
