package ruby.bamboo.entity;

/**
 * 掛け軸の柄 (旧 EnumKakeziku の1.20.1移植。タイトル・サイズ・UVオフセットを verbatim 踏襲)。
 * <p>
 * テクスチャ {@code textures/entity/kakeziku.png} (256x256) 内の配置:
 * 前半12柄は 16x32 を y=0 段に、後半12柄は 16x48 を y=32 段に並べる。
 * <p>
 * Matsu の title が旧通り "Matsh" なのは旧バグの踏襲 (NBT互換のため残す)。
 * <p>
 * 1.21: 変更なし。
 */
public enum KakezikuMotive {
    TATU("Tatu", 16, 32, 0, 0),
    WA("Wa", 16, 32, 16, 0),
    MOOOLS("Moools", 16, 32, 32, 0),
    KWA("KWa", 16, 32, 48, 0),
    BYAKKO("Byakko", 16, 32, 64, 0),
    GENBU("Genbu", 16, 32, 80, 0),
    SUZAKU("Suzaku", 16, 32, 96, 0),
    MARU("Maru", 16, 32, 112, 0),
    IKA("Ika", 16, 32, 128, 0),
    ZON("Zon", 16, 32, 144, 0),
    SUKE("Suke", 16, 32, 160, 0),
    USI("Usi", 16, 32, 176, 0),
    MATSU("Matsh", 16, 48, 0, 32),
    UME("Ume", 16, 48, 16, 32),
    SAKURA("Sakura", 16, 48, 32, 32),
    HUZI("Huzi", 16, 48, 48, 32),
    AYAME("Ayame", 16, 48, 64, 32),
    BOTAN("Botan", 16, 48, 80, 32),
    HAGI("Hagi", 16, 48, 96, 32),
    SUSUKI("Susuki", 16, 48, 112, 32),
    KIKU("Kiku", 16, 48, 128, 32),
    MOMIZI("Momizi", 16, 48, 144, 32),
    YANAGI("Yanagi", 16, 48, 160, 32),
    KIRI("Kiri", 16, 48, 176, 32);

    /** NBT "Motive" に保存するタイトル (旧 title フィールド相当) */
    public final String title;
    /** 幅・高さ (ピクセル。16px = 1ブロック) */
    public final int sizeX;
    public final int sizeY;
    /** テクスチャ内の左上オフセット (ピクセル) */
    public final int offsetX;
    public final int offsetY;

    KakezikuMotive(String title, int sizeX, int sizeY, int offsetX, int offsetY) {
        this.title = title;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public static KakezikuMotive byTitle(String title) {
        for (KakezikuMotive motive : values()) {
            if (motive.title.equals(title)) {
                return motive;
            }
        }
        return TATU;
    }

    public static KakezikuMotive byOrdinal(int ordinal) {
        KakezikuMotive[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return TATU;
        }
        return values[ordinal];
    }
}
