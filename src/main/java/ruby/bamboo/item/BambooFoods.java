package ruby.bamboo.item;

import net.minecraft.world.food.FoodProperties;

/**
 * BambooFood 43種の定義。旧 ruby.bamboo.item.BambooFood.Food enum の移植。
 * <p>
 * 旧: Food(id, heal, sMod, duration, texName)
 * 新: texName を registryName (bamboofood_<texName>) とし、
 * FoodProperties の nutrition / saturationMod に変換。
 * <p>
 * 1.20.1 は食べる時間が 32tick 固定のため、旧 duration の差は仕様変更として扱う。
 * バランス調整は {@code docs/port-spec-bamboofood.md §3.3} の補正案を適用:
 * duration が短いもの(串/団子等 8-16tick)は旧より速く食べられる分 sat を -0.5、
 * 最長 ramen(128tick)は 9→8 に微減。その他 24-64tick 近傍は旧値維持。
 */
public enum BambooFoods {
    MUGIMESI(0, "mugimesi", 3, 1.5f, 32),
    GYUMESI(1, "gyumesi", 10, 5.0f, 32),
    BUTAMESI(2, "butamesi", 12, 6.0f, 48),
    KINOKOMESI(3, "kinokomesi", 6, 3.0f, 32),
    BUTAKUSI(4, "butakusi", 10, 4.5f, 16), // 旧5.0→4.5 (2倍速補正)
    GYUKUSI(5, "gyukusi", 8, 3.5f, 8), // 旧4.0→3.5 (4倍速補正)
    TAKEMESI(6, "takemesi", 5, 5.0f, 32),
    TAMAKAKE(7, "tamakake", 5, 2.0f, 24),
    OYAKO(8, "oyako", 12, 6.0f, 64),
    TEKKA(9, "tekka", 7, 3.5f, 36),
    TORIKUSI(10, "torikusi", 9, 2.5f, 12), // 旧3.0→2.5
    UMEONI(11, "umeoni", 4, 3.0f, 18),
    SAKEONI(12, "sakeoni", 7, 5.0f, 24),
    TUNAONI(13, "tunaoni", 8, 7.0f, 24),
    KINOONI(14, "kinooni", 5, 2.6f, 24),
    TAKEONI(15, "takeoni", 6, 6.0f, 24),
    WAKAMEONI(16, "wakameoni", 6, 5.0f, 24),
    DANANKO(17, "dananko", 7, 3.5f, 16), // 旧4.0→3.5
    DANKINAKO(18, "dankinako", 7, 3.5f, 16), // 旧4.0→3.5
    DANMITARASHI(19, "danmitarashi", 7, 3.5f, 16), // 旧4.0→3.5
    DANSANSYOKU(20, "dansansyoku", 7, 5.5f, 16), // 旧6.0→5.5
    DANZUNDA(21, "danzunda", 7, 4.5f, 16), // 旧5.0→4.5
    MOCHI(22, "mochi", 6, 3.0f, 72),
    COOKEDMOCHI(23, "cookedmochi", 6, 5.0f, 36),
    OHAANKO(24, "ohaanko", 7, 4.0f, 36),
    OHAKINAKO(25, "ohakinako", 7, 4.0f, 36),
    OHAZUNDA(26, "ohazunda", 7, 4.0f, 36),
    NATTO(27, "natto", 2, 0.5f, 16), // 旧1.0→0.5
    NATTOMESHI(28, "nattomeshi", 5, 2.5f, 16), // 旧3.0→2.5
    TAMANATTOMESHI(29, "tamanattomeshi", 7, 5.5f, 16), // 旧6.0→5.5
    SAKURAMOCHI(30, "sakuramochi", 7, 6.0f, 36),
    TAMAGYUMESHI(31, "tamagyumeshi", 11, 6.0f, 24),
    KATSUDON(32, "katsudon", 11, 6.0f, 24),
    SEKIHAN(33, "sekihan", 5, 4.0f, 36),
    ONISEKIHAN(34, "onisekihan", 7, 6.0f, 28),
    TOFU(35, "tofu", 1, 0.5f, 10),
    AGEDASHI(36, "agedashi", 3, 1.5f, 16), // 旧2.0→1.5
    MEN(37, "men", 1, 0.5f, 32),
    UDON(38, "udon", 10, 3.0f, 64),
    SOBA(39, "soba", 12, 3.0f, 64),
    RAMEN(40, "ramen", 18, 8.0f, 128), // 旧9.0→8.0
    PIZZA(41, "pizza", 10, 5.0f, 64),
    KAISENOYAKO(42, "kaisenoyako", 8, 4.0f, 36);

    public final int id;
    public final String texName;
    public final int hunger;
    public final float saturationMod;
    public final int duration;

    BambooFoods(int id, String texName, int hunger, float saturationMod, int duration) {
        this.id = id;
        this.texName = texName;
        this.hunger = hunger;
        this.saturationMod = saturationMod;
        this.duration = duration;
    }

    /** Registry名: bamboofood_<texName> */
    public String registryName() {
        return "bamboofood_" + texName;
    }

    /** FoodProperties 生成 */
    public FoodProperties foodProperties() {
        return new FoodProperties.Builder()
                .nutrition(hunger)
                .saturationMod(saturationMod)
                .build();
    }
}
