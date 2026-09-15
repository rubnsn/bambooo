package ruby.bamboo.skill;

/**
 * スキル種別 (13種、重量挙げは廃止)。
 * id は NBT・コマンド・本アイテム名の共通キー。
 */
public enum SkillType {
    PICKAXE("pickaxe"),
    AXE("axe"),
    SHOVEL("shovel"),
    SPEED("speed"),
    LUCK("luck"),
    FISHING("fishing"),
    SWIM("swim"),
    DUAL_WIELD("dual_wield"),
    SHIELD("shield"),
    SWORD("sword"),
    SHOOTING("shooting"),
    ANATOMY("anatomy"),
    NEGOTIATION("negotiation");

    /** 新規スキルの基礎必要値 (全スキル固定、feat-spec-skill §2)。 */
    public static final int BASE_REQ = 100;
    /** 現行のレベル上限 (固定運用、将来解放に備え Storage 側に max を持つ)。 */
    public static final int DEFAULT_MAX_LEVEL = 10;

    private final String id;

    SkillType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** 次レベルに必要な累積 xp ではなく、そのレベルから次への必要量。 */
    public static int requiredFor(int level) {
        return (int) (BASE_REQ * Math.pow(level + 1, 1.5D));
    }

    public static SkillType byId(String id) {
        for (SkillType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
