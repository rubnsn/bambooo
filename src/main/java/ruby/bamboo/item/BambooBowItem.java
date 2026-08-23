package ruby.bamboo.item;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.entity.arrow.BambooArrowEntity;
import ruby.bamboo.entity.arrow.TorchArrowEntity;
import ruby.bamboo.item.arrow.ArrowBase;
import ruby.bamboo.item.arrow.BambooArrowItem;

/**
 * 竹弓 (旧 BambooBow の 1.20.1 移植)。
 * <p>
 * バニラ矢ではなくインベントリ内の {@link ArrowBase} 系アイテムを弾として使用。
 * 選択中の矢種は弓の NBT ({@code Ammo}/{@code Slot}) に保持され、
 * Vキー (汎用キー) で循環切替できる。
 */
public class BambooBowItem extends BowItem {

    public static final String TAG_AMMO = "Ammo";
    public static final String AMMO_SLOT = "Slot";

    public BambooBowItem(Properties properties) {
        super(properties);
    }

    // ===== 矢種検索 =====

    /**
     * インベントリ内の一意な ArrowBase 種リスト (旧 getArrowTypes 相当)。
     */
    public List<ItemStack> getArrowTypes(Player player) {
        List<ItemStack> result = new java.util.ArrayList<>();
        List<ItemStack> all = new java.util.ArrayList<>();
        all.addAll(player.getInventory().items);
        all.addAll(player.getInventory().offhand);
        for (ItemStack stack : all) {
            if (!stack.isEmpty() && stack.getItem() instanceof ArrowBase
                    && result.stream().noneMatch(s -> ItemStack.isSameItem(s, stack))) {
                result.add(stack);
            }
        }
        return result;
    }

    /** 選択中スロット取得 (旧 getArrowSlot 相当) */
    public byte getArrowSlot(ItemStack bow) {
        CompoundTag tag = bow.getTag();
        if (tag == null || !tag.contains(TAG_AMMO)) {
            return 0;
        }
        return tag.getCompound(TAG_AMMO).getByte(AMMO_SLOT);
    }

    /** 選択中スロット設定 (V キーパケット側から呼ばれる) */
    public void setArrowSlot(ItemStack bow, byte slot) {
        bow.getOrCreateTagElement(TAG_AMMO).putByte(AMMO_SLOT, slot);
    }

    /** 選択中の矢スタックを取得 (無ければ null)。スロットが範囲外なら丸め */
    public ItemStack findSelectedArrow(Player player, ItemStack bow) {
        List<ItemStack> types = getArrowTypes(player);
        if (types.isEmpty()) {
            return null;
        }
        int slot = getArrowSlot(bow);
        if (slot >= types.size()) {
            slot = 0;
            setArrowSlot(bow, (byte) 0);
        }
        return types.get(slot);
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        // バニラ矢ではなく ArrowBase 系のみ対応
        return stack -> stack.getItem() instanceof ArrowBase;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bow = player.getItemInHand(hand);
        boolean hasArrow = findSelectedArrow(player, bow) != null;
        if (!player.getAbilities().instabuild && !hasArrow) {
            return InteractionResultHolder.fail(bow);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(bow);
    }

    @Override
    public void releaseUsing(ItemStack bow, Level level, LivingEntity shooter, int timeLeft) {
        if (!(shooter instanceof Player player)) {
            return;
        }
        ItemStack arrowStack = findSelectedArrow(player, bow);
        if (arrowStack == null && !player.getAbilities().instabuild) {
            return;
        }
        if (arrowStack == null) {
            // creative で矢を持っていない場合はデフォルトで竹矢
            arrowStack = new ItemStack(BambooItems.BAMBOO_ARROW.get());
        }
        if (!(arrowStack.getItem() instanceof ArrowBase arrowItem)) {
            return;
        }

        int charge = this.getUseDuration(bow) - timeLeft;
        charge = ForgeEventFactory.onArrowLoose(bow, level, player, charge, true);
        if (charge < 0) {
            return;
        }

        float power = calcOldPower(charge); // 旧式 (p²+2p)/3、cap1.0
        if (!(power < 0.1F)) {
            if (!level.isClientSide) {
                fireArrow(level, player, bow, arrowStack, arrowItem, power, charge);
                bow.hurtAndBreak(1, shooter, e -> e.broadcastBreakEvent(shooter.getUsedItemHand()));
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
                    1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }

    private void fireArrow(Level level, Player player, ItemStack bow, ItemStack arrowStack,
            ArrowBase arrowItem, float power, int chargeFrame) {
        boolean noResource = player.getAbilities().instabuild
                || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, bow) > 0;
        float velocity = power * 2.0F;

        AbstractArrow arrow = createTypedArrow(level, player, arrowStack, velocity);

        applyEnchantments(arrow, bow);

        // 竹矢: barrage 設定
        if (arrow instanceof BambooArrowEntity bambooArrow) {
            int owned = countStack(player, arrowStack.getItem());
            int attackCount = BambooArrowItem.calcBarrage(
                    chargeFrame,
                    player.getAbilities().instabuild && player.getAbilities().flying,
                    owned);
            bambooArrow.setBarrage(Math.max(0, attackCount - 1), power); // 初弾含め attackCount 本
            if (power >= 1.0F) {
                arrow.setCritArrow(true);
            }
        } else if (arrow instanceof TorchArrowEntity) {
            // 松明矢: 10% 自然着火 (旧 world.rand.nextFloat() < 0.1)
            if (level.getRandom().nextFloat() < 0.1F) {
                arrow.setSecondsOnFire(100);
            }
        }

        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                velocity, 1.0F);
        level.addFreshEntity(arrow);

        // 消費処理 (旧 isNoResources 分岐相当)
        if (!noResource) {
            consumeFromInventory(player, arrowStack.getItem(), 1);
        } else {
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }
    }

    /**
     * 弓発射時の矢エンティティ生成。
     */
    private AbstractArrow createTypedArrow(Level level, Player player, ItemStack arrowStack, float velocity) {
        return ((ArrowBase) arrowStack.getItem()).createArrowForBow(level, player, velocity);
    }

    private void applyEnchantments(AbstractArrow arrow, ItemStack bow) {
        int powerLvl = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (powerLvl > 0) {
            double base = arrow.getBaseDamage();
            // 竹矢は旧式 (+j×0.15)、その他は (+j×0.5+0.5)
            if (arrow instanceof BambooArrowEntity) {
                arrow.setBaseDamage(base + powerLvl * 0.15D);
            } else {
                arrow.setBaseDamage(base + powerLvl * 0.5D + 0.5D);
            }
        }
        int punchLvl = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (punchLvl > 0) {
            arrow.setKnockback(punchLvl);
        }
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) {
            arrow.setSecondsOnFire(100);
        }
    }

    /** インベントリ全体から指定アイテム数を消費 */
    private void consumeFromInventory(Player player, net.minecraft.world.item.Item item, int amount) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(item)) {
                int take = Math.min(amount, s.getCount());
                s.shrink(take);
                amount -= take;
            }
        }
    }

    private static int countStack(Player player, net.minecraft.world.item.Item item) {
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(item)) {
                total += s.getCount();
            }
        }
        return total;
    }

    /** 旧 power 式 ((p²+2p)/3、cap1.0) — p=charge/10 */
    public static float calcOldPower(int chargeFrame) {
        float p = chargeFrame / 10.0F;
        float f = (p * p + p * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }
        return f;
    }
}
