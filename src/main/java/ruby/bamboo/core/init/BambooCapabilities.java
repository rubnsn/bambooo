package ruby.bamboo.core.init;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.capability.ColoredLightStorage;

/**
 * Phase B: ColoredLight Capability 登録 + LevelChunk attach.
 */
public final class BambooCapabilities {

    public static final Capability<ColoredLightStorage> COLORED_LIGHT =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation COLORED_LIGHT_ID =
            new ResourceLocation(BambooMod.MODID, "colored_light");

    private BambooCapabilities() {
    }

    @Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class EventHandler {

        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<LevelChunk> event) {
            ColoredLightStorage storage = new ColoredLightStorage();
            ICapabilitySerializable<CompoundTag> provider = new ICapabilitySerializable<CompoundTag>() {
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                    if (cap == COLORED_LIGHT) {
                        return LazyOptional.of(() -> storage).cast();
                    }
                    return LazyOptional.empty();
                }

                @Override
                public CompoundTag serializeNBT() {
                    CompoundTag tag = storage.serializeNBT();
                    // CapabilityDispatcher.put は null を許容しないため、nullなら空tagを返す
                    // ChunkSerializerは空でもForgeCapsを書き込むが、sparse0件ならList空で数十byteのみ
                    return tag != null ? tag : new CompoundTag();
                }

                @Override
                public void deserializeNBT(CompoundTag nbt) {
                    storage.deserializeNBT(nbt);
                }
            };
            event.addCapability(COLORED_LIGHT_ID, provider);
        }
    }
}
