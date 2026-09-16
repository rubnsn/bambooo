package ruby.bamboo.core.init;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import ruby.bamboo.BambooMod;
import ruby.bamboo.loot.AddItemModifier;

/**
 * GlobalLootModifier の codec 登録。
 * <p>
 * 1.21.1 NeoForge では組込の {@code neoforge:add_item} を使うため、
 * JSON 側はそちらを参照する。本登録は {@code bamboomod:add_item} の予備。
 * <p>
 * 親への配線: {@code BambooMod} コンストラクタから
 * {@code BambooLootModifiers.init(modEventBus)} を呼ぶこと
 * (BambooCapabilities.init と同型)。
 */
public final class BambooLootModifiers {
    private BambooLootModifiers() {
    }

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BambooMod.MODID);

    public static final Supplier<MapCodec<? extends IGlobalLootModifier>> ADD_ITEM =
            SERIALIZERS.register("add_item", () -> AddItemModifier.CODEC.get());

    /**
     * modBus への登録。BambooMod コンストラクタから呼ぶこと。
     */
    public static void init(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
