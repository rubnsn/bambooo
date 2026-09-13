package ruby.bamboo.core.init;

import com.mojang.serialization.Codec;

import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.loot.AddItemModifier;

/**
 * GlobalLootModifier の codec 登録。SSOT は {@link BambooMod#LOOT_MODIFIER_SERIALIZERS}。
 * <p>
 * 1.20.1では組込の {@code forge:add_item} が無いため {@code bamboomod:add_item} を登録する。
 */
public final class BambooLootModifiers {
    private BambooLootModifiers() {
    }

    public static final RegistryObject<Codec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>> ADD_ITEM = BambooMod.LOOT_MODIFIER_SERIALIZERS
            .register("add_item", AddItemModifier.CODEC);

    public static void init() {
        // codec 登録は静的初期化で完結する。参照解決のためのダミー呼び出し。
        ADD_ITEM.getId();
    }
}
