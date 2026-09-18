package ruby.bamboo.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ruby.bamboo.gacha.GachaCapsule;
import ruby.bamboo.gacha.GachaManager;
import ruby.bamboo.gacha.GachaRarity;

/**
 * ガチャカプセル (docs/port-spec-gacha.md)。
 * <p>
 * 名前は「カプセル」で統一。色は NBT {@code Capsule} (red/blue/yellow/rainbow)
 * で保持し、上半分のtintと中身の期待値を切替える。マシン排出時は未開封、
 * 手に持って右クリックで抽選→中身を払い出し、手元のカプセルは
 * パカッと開いた空カプセル ({@code Opened=true}) になって残る。
 */
public class GachaCapsuleItem extends Item {
    /** カプセル色のNBTキー */
    public static final String TAG_CAPSULE = "Capsule";
    /** 開封済みのNBTキー */
    public static final String TAG_OPENED = "Opened";

    public GachaCapsuleItem(Properties props) {
        super(props);
    }

    /** 未開封/空カプセルのスタックを生成 (クリエタブ・排出用)。 */
    public static ItemStack create(GachaCapsule capsule, boolean opened) {
        ItemStack stack = new ItemStack(
                ruby.bamboo.core.init.BambooItems.GACHA_CAPSULE.get());
        stack.getOrCreateTag().putString(TAG_CAPSULE, capsule.id);
        stack.getOrCreateTag().putBoolean(TAG_OPENED, opened);
        return stack;
    }

    public static GachaCapsule getCapsule(ItemStack stack) {
        return GachaCapsule.byId(stack.getOrCreateTag().getString(TAG_CAPSULE));
    }

    /** 開封済み (空・パカッと開いた見た目) か。モデル述語 {@code bamboomod:opened} と連動。 */
    public static boolean isOpened(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(TAG_OPENED);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
            InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(held);
        }
        if (isOpened(held)) {
            player.displayClientMessage(
                    Component.translatable("message.bamboomod.gacha_capsule_empty"), true);
            return InteractionResultHolder.success(held);
        }
        GachaCapsule capsule = getCapsule(held);
        GachaManager.SingleResult r =
                GachaManager.rollContentResult(capsule, level.getRandom());
        ItemStack out = r.stack().copy();
        if (!player.getInventory().add(out.copy())) {
            ItemEntity e = new ItemEntity(level, player.getX(),
                    player.getY() + 0.5, player.getZ(), out.copy());
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        }
        // 手元のカプセルは空になって残る。複数持ちは1個だけ開封
        if (held.getCount() == 1) {
            held.getOrCreateTag().putBoolean(TAG_OPENED, true);
        } else {
            held.shrink(1);
            ItemStack empty = create(capsule, true);
            if (!player.getInventory().add(empty)) {
                ItemEntity e = new ItemEntity(level, player.getX(),
                        player.getY() + 0.5, player.getZ(), empty);
                e.setDefaultPickUpDelay();
                level.addFreshEntity(e);
            }
        }
        SoundEvent se = switch (r.rarity()) {
            case SUPER_RARE -> SoundEvents.PLAYER_LEVELUP;
            case RARE -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            default -> SoundEvents.BUNDLE_INSERT;
        };
        level.playSound(null, player.blockPosition(), se, SoundSource.PLAYERS,
                0.6F, r.rarity() == GachaRarity.RARE ? 1.2F : 1.0F);
        // カプセルを開けるパカッと感: フェンスゲート開音を少し高めに重ねる
        level.playSound(null, player.blockPosition(), SoundEvents.FENCE_GATE_OPEN,
                SoundSource.PLAYERS, 0.5F, 1.2F);
        player.displayClientMessage(
                Component.translatable("message.bamboomod.gacha_capsule_open",
                        out.getHoverName().getString()),
                true);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        if (isOpened(stack)) {
            tooltip.add(Component.translatable("tooltip.bamboomod.gacha_capsule.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(getCapsule(stack).langKey())
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
