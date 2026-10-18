package ruby.bamboo.mixin.vanilla;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.core.init.BambooVillagers;

/**
 * 遊び人・魚コレクターへの就職確定時にメイド服を自動装備する。
 * <p>
 * Forge に職業変更イベントが無いため Villager#setVillagerData の TAIL に注入する。
 * 就職時 (AssignProfessionFromJobSite)・レベルアップ時に発火する。
 * 既にメイド服着用中なら何もしない (脱がせた後のレベルアップで復活させないため)。
 */
@Mixin(Villager.class)
public abstract class VillagerProfessionMixin {

    @Inject(method = "setVillagerData(Lnet/minecraft/world/entity/npc/VillagerData;)V", at = @At("TAIL"))
    private void bamboomod$autoMaidOnAsobinin(VillagerData data, CallbackInfo ci) {
        try {
            Villager self = (Villager) (Object) this;
            if (self.level().isClientSide) return;
            if (!BambooVillagers.ASOBININ.isPresent() || !BambooItems.MAID_CLOTHES.isPresent()) return;
            boolean maidJob = data.getProfession() == BambooVillagers.ASOBININ.get()
                    || (BambooVillagers.FISH_COLLECTOR.isPresent()
                            && data.getProfession() == BambooVillagers.FISH_COLLECTOR.get());
            if (!maidJob) return;
            if (self.getItemBySlot(EquipmentSlot.CHEST).is(BambooItems.MAID_CLOTHES.get())) return;
            self.setItemSlot(EquipmentSlot.CHEST, new ItemStack(BambooItems.MAID_CLOTHES.get()));
            self.setDropChance(EquipmentSlot.CHEST, 0.0F);
            self.refreshDimensions();
            if (self.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF,
                        self.getX(), self.getY() + 1.0D, self.getZ(),
                        24, 0.5D, 0.7D, 0.5D, 0.02D);
            }
        } catch (Exception ignored) {
        }
    }
}
