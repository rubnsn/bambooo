package ruby.bamboo.core.wish;

import com.google.gson.JsonObject;

/**
 * JSON由来の単一効果。
 */
public class WishEffect {
    public final String type;
    public final JsonObject args;

    public WishEffect(String type, JsonObject args) {
        this.type = type;
        this.args = args;
    }
}
