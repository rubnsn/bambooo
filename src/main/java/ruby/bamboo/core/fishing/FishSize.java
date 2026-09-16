package ruby.bamboo.core.fishing;

/**
 * 釣りのランク。bronze/silver/gold の3段階。
 */
public enum FishSize {
    BRONZE("bronze", 0),
    SILVER("silver", 1),
    GOLD("gold", 2);

    public static final String TAG_KEY = "bamboo_size_class";

    public final String tagValue;
    public final int index;

    FishSize(String tagValue, int index) {
        this.tagValue = tagValue;
        this.index = index;
    }

    public static FishSize fromTag(String value) {
        if (value == null) return SILVER;
        for (FishSize s : values()) {
            if (s.tagValue.equals(value)) return s;
        }
        return SILVER;
    }

    public static FishSize fromIndex(int i) {
        if (i < 0 || i >= values().length) return SILVER;
        return values()[i];
    }
}
