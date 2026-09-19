package ruby.bamboo.capsule;

/**
 * カプセルボールの Tier (docs/port-spec-capsule-ball.md §1)。
 * 捕獲値加算のみが性能差。見た目は tint 色で区別する。
 */
public enum CapsuleTier {
    N("n", "capsule_ball", 0, 0xFFD8342C),
    R("r", "capsule_ball_r", 5, 0xFF2C5CE0),
    SR("sr", "capsule_ball_sr", 10, 0xFFF2B234),
    UR("ur", "capsule_ball_ur", 100, 0xFFB14AE0);

    /** NBT {@code Tier} に保存するID */
    public final String id;
    /** レジストリ名 */
    public final String registryName;
    /** 捕獲率加算 (%) */
    public final int bonus;
    /** 上半分 tint (ARGB。URは虹循環の基調色としてのみ使用) */
    public final int tint;

    CapsuleTier(String id, String registryName, int bonus, int tint) {
        this.id = id;
        this.registryName = registryName;
        this.bonus = bonus;
        this.tint = tint;
    }

    public static CapsuleTier byId(String id) {
        for (CapsuleTier t : values()) {
            if (t.id.equals(id)) return t;
        }
        return N;
    }

    /** 一つ上の Tier (URはURのまま)。 */
    public CapsuleTier higher() {
        return switch (this) {
            case N -> R;
            case R -> SR;
            default -> UR;
        };
    }
}
