package ruby.bamboo.item;

import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.entity.ZabutonColor;
import ruby.bamboo.entity.ZabutonEntity;

/**
 * 座布団アイテム (旧 ItemZabuton の1.20.1移植)。
 * <p>
 * 旧は damage値16色の単一アイテムだったが、1.20.1では色ごとに登録
 * ({@code zabuton_<色>})。ブロック上面の右クリックで設置、
 * それ以外 (側面・空中) は投擲する。シフト+上面でブロック中央に整列設置。
 */
public class ZabutonItem extends Item {

    private final ZabutonColor color;

    public ZabutonItem(ZabutonColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    public ZabutonColor getColor() {
        return color;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        // 上面以外 (側面・下面) は投擲動作
        if (ctx.getClickedFace() != Direction.UP) {
            Player thrower = ctx.getPlayer();
            if (thrower == null) {
                return InteractionResult.FAIL;
            }
            return this.throwZabuton(ctx.getLevel(), thrower, ctx.getHand()).getResult();
        }
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        double x;
        double y;
        double z;
        float yaw;
        if (player != null && player.isShiftKeyDown()) {
            // シフト+上面: ブロック中央に角度0固定で綺麗に置く
            x = ctx.getClickedPos().getX() + 0.5D;
            y = ctx.getClickedPos().getY() + 1.0D + 0.02D;
            z = ctx.getClickedPos().getZ() + 0.5D;
            yaw = 0.0F;
        } else {
            Vec3 click = ctx.getClickLocation();
            x = click.x;
            y = click.y + 0.02D;
            z = click.z;
            yaw = player != null ? player.getYRot() : 0.0F;
        }
        ZabutonEntity entity = new ZabutonEntity(BambooEntities.ZABUTON.get(), level);
        entity.setPos(x, y, z);
        entity.setYRot(yaw);
        entity.setColor(this.color);
        // 旧は無チェックだったが、壁内設置を避けるため衝突チェックを追加
        if (!level.noCollision(entity.getBoundingBox())) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            level.addFreshEntity(entity);
        }
        if (player != null) {
            if (!player.getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 空中右クリックは投擲 (旧 onItemRightClick 相当。スニーク条件は撤廃)
        return this.throwZabuton(level, player, hand);
    }

    private InteractionResultHolder<ItemStack> throwZabuton(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F,
                0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            ZabutonEntity entity = new ZabutonEntity(BambooEntities.ZABUTON.get(), level);
            entity.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
            Vec3 look = player.getLookAngle();
            entity.setDeltaMovement(look.x * 1.2D, look.y * 1.2D + 0.2D, look.z * 1.2D);
            entity.setThrower(player);
            entity.setColor(this.color);
            level.addFreshEntity(entity);
            player.getCooldowns().addCooldown(this, 10);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
