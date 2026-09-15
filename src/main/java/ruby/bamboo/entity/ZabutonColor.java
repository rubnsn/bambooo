package ruby.bamboo.entity;

/**
 * 座布団の色 (旧 EntityZabuton.EnumZabutonColor の1.20.1移植)。
 * <p>
 * 旧版は damage値 0-15 の16色。1.20.1では damage値が無いため、
 * 色ごとに独立アイテム ({@code zabuton_<registryName>}) として登録する。
 * 旧HEX値はそのまま踏襲。CACAO は brown に読み替える。
 */
public enum ZabutonColor {
    BLACK("black", 0x312935),
    RED("red", 0xA61920),
    GREEN("green", 0x669259),
    BROWN("brown", 0x6B4B41),
    BLUE("blue", 0x2A405D),
    PURPLE("purple", 0x534362),
    CYAN("cyan", 0x77B7B7),
    LIGHT_GRAY("light_gray", 0x8B8B99),
    GRAY("gray", 0x3F3F46),
    PINK("pink", 0xE18F8F),
    LIME("lime", 0x8D9734),
    YELLOW("yellow", 0xD8C90E),
    LIGHT_BLUE("light_blue", 0x17728D),
    MAGENTA("magenta", 0xA15275),
    ORANGE("orange", 0xC8870E),
    WHITE("white", 0xFFFFFF);

    private final String registryName;
    /** 描画・アイテムtint用 RGB (0xRRGGBB) */
    public final int rgb;

    ZabutonColor(String registryName, int rgb) {
        this.registryName = registryName;
        this.rgb = rgb;
    }

    /** 登録名サフィックス ({@code zabuton_<suffix>}) */
    public String registryName() {
        return registryName;
    }

    /** 対応する羊毛のアイテムID (レシピ用) */
    public String woolId() {
        return "minecraft:" + registryName + "_wool";
    }

    public float redF() {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    public float greenF() {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    public float blueF() {
        return (rgb & 0xFF) / 255.0F;
    }

    public static ZabutonColor byOrdinal(int ordinal) {
        ZabutonColor[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return WHITE;
        }
        return values[ordinal];
    }
}
