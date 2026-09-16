package ruby.bamboo.client.gui.trump;

import net.minecraft.network.chat.Component;

/**
 * ソリティアの難易度。めくり数と山札回収の上限を定める。
 */
public enum SolitaireDifficulty {
    /** 山札1枚めくり・回収無制限。 */
    EASY(1, -1, "screen.bamboomod.solitaire_easy", "screen.bamboomod.solitaire_easy_desc"),
    /** 山札1枚めくり・回収3回まで。 */
    NORMAL(1, 3, "screen.bamboomod.solitaire_normal", "screen.bamboomod.solitaire_normal_desc"),
    /** 山札3枚めくり・回収3回まで。 */
    HARD(3, 3, "screen.bamboomod.solitaire_hard", "screen.bamboomod.solitaire_hard_desc"),
    /** 山札3枚めくり・回収1回のみ。 */
    HELL(3, 1, "screen.bamboomod.solitaire_hell", "screen.bamboomod.solitaire_hell_desc");

    private final int drawCount;
    private final int maxRedeals;
    private final String labelKey;
    private final String descKey;

    SolitaireDifficulty(int drawCount, int maxRedeals, String labelKey, String descKey) {
        this.drawCount = drawCount;
        this.maxRedeals = maxRedeals;
        this.labelKey = labelKey;
        this.descKey = descKey;
    }

    public int drawCount() {
        return drawCount;
    }

    public int maxRedeals() {
        return maxRedeals;
    }

    public Component label() {
        return Component.translatable(labelKey);
    }

    public Component desc() {
        return Component.translatable(descKey);
    }
}
