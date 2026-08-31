package ruby.bamboo.core.wish;

import ruby.bamboo.util.WishNormalizer;

import java.util.List;
import java.util.regex.Pattern;

/**
 * JSONから読み込んだ1件の願い定義。
 * port-spec-wish §4.1 §6.1 準拠。
 */
public class WishEntry {
    public final String id;
    public final String pattern;
    public final String match; // contains or regex
    public final boolean priority;
    public final int weight;
    public final String message;
    public final List<WishEffect> effects;
    public final Pattern compiledRegex; // nullable, for regex match

    public WishEntry(String id, String pattern, String match, boolean priority, int weight, String message, List<WishEffect> effects) {
        this.id = id;
        this.pattern = pattern;
        this.match = match;
        this.priority = priority;
        this.weight = weight <= 0 ? 1 : weight;
        this.message = message;
        this.effects = effects;
        if ("regex".equalsIgnoreCase(match)) {
            Pattern p = null;
            try {
                String normPat = WishNormalizer.normalize(pattern);
                p = Pattern.compile(normPat, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } catch (Exception e) {
                p = null;
            }
            this.compiledRegex = p;
        } else {
            this.compiledRegex = null;
        }
    }

    public boolean matches(String normalizedInput) {
        if ("regex".equalsIgnoreCase(match)) {
            if (compiledRegex == null) return false;
            return compiledRegex.matcher(normalizedInput).find();
        } else {
            // contains: pattern may have '|' separated tokens, any token matches substring
            String[] tokens = pattern.split("\\|");
            for (String tok : tokens) {
                String nt = WishNormalizer.normalize(tok);
                if (nt.isEmpty()) continue;
                if (normalizedInput.contains(nt)) return true;
            }
            return false;
        }
    }
}
