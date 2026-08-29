package ruby.bamboo.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 願い入力の正規化: trim・空白統一・NFKC・カタカナ→ひらがな・制御文字除去・小文字化。
 * port-spec-wish §4.1 準拠。
 */
public final class WishNormalizer {

    private WishNormalizer() {
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim().replaceAll("\\s+", " ");
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u30A1' && c <= '\u30F6') {
                c = (char) (c - 0x60);
            }
            sb.append(c);
        }
        s = sb.toString();
        s = s.replaceAll("\\p{Cntrl}", "");
        s = s.toLowerCase(Locale.ROOT);
        return s;
    }
}
