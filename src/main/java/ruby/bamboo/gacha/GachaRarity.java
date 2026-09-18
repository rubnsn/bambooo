package ruby.bamboo.gacha;

/**
 * ガチャのレアリティ。テスト用3段階。
 * <p>
 * 排出カプセルの色と開封演出の強さを切替える。確率は {@link GachaManager} の定数。
 */
public enum GachaRarity {
    /** コモン: 白カプセル、演出なし */
    COMMON(0xF2F2F2, "screen.bamboomod.gacha_rarity_common"),
    /** レア: 青カプセル、白フラッシュ+小演出 */
    RARE(0x3FA9F5, "screen.bamboomod.gacha_rarity_rare"),
    /** スーパーレア: 赤紫カプセル、金フラッシュ+拡大+SE */
    SUPER_RARE(0xC93FC9, "screen.bamboomod.gacha_rarity_sr");

    /** カプセル基調色 (RGB) */
    public final int capsuleColor;
    /** 表示用langキー */
    public final String langKey;

    GachaRarity(int capsuleColor, String langKey) {
        this.capsuleColor = capsuleColor;
        this.langKey = langKey;
    }

    public static GachaRarity byOrdinal(int o) {
        GachaRarity[] v = values();
        if (o < 0 || o >= v.length) {
            return COMMON;
        }
        return v[o];
    }
}
