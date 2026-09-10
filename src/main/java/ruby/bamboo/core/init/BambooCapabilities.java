package ruby.bamboo.core.init;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
import ruby.bamboo.skill.SkillStorage;
import ruby.bamboo.transform.TransformStorage;

/**
 * Phase B: ColoredLight Capability 登録 + LevelChunk attach.
 * feat-skill: Skill Capability 登録 + Player attach.
 */
public final class BambooCapabilities {

    public static final Capability<ColoredLightStorage> COLORED_LIGHT =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation COLORED_LIGHT_ID =
            new ResourceLocation(BambooMod.MODID, "colored_light");

    public static final Capability<SkillStorage> SKILL =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation SKILL_ID =
            new ResourceLocation(BambooMod.MODID, "skill");

    public static final Capability<TransformStorage> TRANSFORM =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation TRANSFORM_ID =
            new ResourceLocation(BambooMod.MODID, "transform");

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

        @SubscribeEvent
        public static void onAttachPlayerCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (!(event.getObject() instanceof Player)) {
                return;
            }
            attachTransform(event);
            SkillStorage storage = new SkillStorage();
            LazyOptional<SkillStorage> lazy = LazyOptional.of(() -> storage);
            ICapabilitySerializable<CompoundTag> provider = new ICapabilitySerializable<CompoundTag>() {
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                    if (cap == SKILL) {
                        return lazy.cast();
                    }
                    return LazyOptional.empty();
                }

                @Override
                public CompoundTag serializeNBT() {
                    return storage.serializeNBT();
                }

                @Override
                public void deserializeNBT(CompoundTag nbt) {
                    storage.deserializeNBT(nbt);
                }
            };
            event.addCapability(SKILL_ID, provider);
        }

        private static void attachTransform(AttachCapabilitiesEvent<Entity> event) {
            TransformStorage storage = new TransformStorage();
            LazyOptional<TransformStorage> lazy = LazyOptional.of(() -> storage);
            ICapabilitySerializable<CompoundTag> provider = new ICapabilitySerializable<CompoundTag>() {
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                    if (cap == TRANSFORM) {
                        return lazy.cast();
                    }
                    return LazyOptional.empty();
                }

                @Override
                public CompoundTag serializeNBT() {
                    return storage.serializeNBT();
                }

                @Override
                public void deserializeNBT(CompoundTag nbt) {
                    storage.deserializeNBT(nbt);
                }
            };
            event.addCapability(TRANSFORM_ID, provider);
        }
    }
}
