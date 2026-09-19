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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import ruby.bamboo.gacha.GachaCapsule;
import ruby.bamboo.gacha.GachaManager;
import ruby.bamboo.gacha.GachaRarity;

/**
 * ガチャカプセル (docs/port-spec-gacha.md)。
 * <p>
 * 名前は「カプセル」で統一。色は NBT {@code Capsule} (red/blue/yellow/rainbow)
 * で保持し、上半分のtintと中身の期待値を切替える。マシン排出時は未開封、
 * 手に持って右クリック長押し (1.6秒・掲げモーション) で抽選→中身を払い出し、
 * 手元のカプセルはパカッと開いた空カプセル ({@code Opened=true}) になって残る。
 * 開封時は10%でワンランク上のカプセルがもう1個入っている (当たり)。
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
        if (isOpened(held)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.bamboomod.gacha_capsule_empty"), true);
            }
            return InteractionResultHolder.success(held);
        }
        // 長押し開封 (掲げモーションで構える)。完了は finishUsingItem
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(held);
    }

    /** 開封に必要な長押し時間 (32tick=1.6秒)。 */
    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    /** 開封モーション (掲げ構え。EATは咀嚼音・パーティクルが出るため使わない)。 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && !level.isClientSide && !isOpened(stack)) {
            openCapsule(stack, level, player);
        }
        return stack;
    }

    /** 開封本体 (サーバー側のみ): 中身抽選→払い出し→空化→10%で当たり判定。 */
    private static void openCapsule(ItemStack held, Level level, Player player) {
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
        // 開封メッセージはチャットログへ (hint表示ではすぐ消えるため)
        player.displayClientMessage(
                Component.translatable("message.bamboomod.gacha_capsule_open",
                        out.getHoverName().getString()),
                false);
        // 10%で当たり: ワンランク上のカプセルがもう1個入っていた
        if (level.getRandom().nextFloat() < 0.10F) {
            ItemStack bonus = create(capsule.higher(), false);
            if (!player.getInventory().add(bonus.copy())) {
                ItemEntity e = new ItemEntity(level, player.getX(),
                        player.getY() + 0.5, player.getZ(), bonus.copy());
                e.setDefaultPickUpDelay();
                level.addFreshEntity(e);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.6F, 1.0F);
            player.displayClientMessage(
                    Component.translatable("message.bamboomod.gacha_capsule_bonus"),
                    false);
        }
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
