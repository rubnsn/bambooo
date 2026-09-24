package ruby.bamboo.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.GachaBlock;
import ruby.bamboo.block.entity.GachaBlockEntity;

/**
 * ガチャブロック (構造物生成分) を村構造物内で破壊不可にする (サバイバル限定)。
 * <p>
 * 対象は datapack タグ {@code bamboomod:village_protected} で種別指定し、
 * 生成由来かは BE のプレイヤー設置マーカー ({@code GachaBlockEntity#isPlayerPlaced})
 * で判定する。構造物生成時は {@code setPlacedBy} が呼ばれないため、
 * 既存ワールドの構造物ガチャも自動で保護対象になる。
 * プレイヤー設置分はマーカーあり = 破壊可。ブロック側の硬度変更は不要。
 * クリエイティブ・スペクテイターは無条件で破壊可。爆発・ピストン等は対象外。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageProtectHandler {

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        // クリエ・スペクテイターは無条件で破壊可
        if (player.isCreative() || player.isSpectator()) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        // BE は LOWER 側。UPPER 破壊時も LOWER のマーカーで判定する
        BlockPos lowerPos = event.getState().getValue(GachaBlock.HALF) == DoubleBlockHalf.UPPER
                ? pos.below()
                : pos;
        if (level.getBlockEntity(lowerPos) instanceof GachaBlockEntity be
                && be.isPlayerPlaced()) {
            // プレイヤー設置分は破壊可
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(
                Component.translatable("message.bamboomod.village_protected"), true);
    }
}
