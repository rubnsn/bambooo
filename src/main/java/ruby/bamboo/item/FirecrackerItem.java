package ruby.bamboo.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ruby.bamboo.entity.FirecrackerEntity;

/**
 * かんしゃく玉 (旧 ItemFirecracker の 1.20.1 移植)。
 * <p>
 * 旧版は meta0-2 の単一アイテムだったが、1.20.1 では独立アイテム化
 * (firecracker_s / m / l / m_sticky / l_sticky の5種)。
 * 投げた時点で着火済み (導火線はエンティティ側で進行)。
 */
public class FirecrackerItem extends Item {

    /** かんしゃく玉の種類。導火線・威力・破壊挙動・粘着を束ねる。 */
    public enum Type {
        /** 小: 導火線20tick、威力2.0、ブロック破壊なし */
        S(0, 20, 2.0F, false, false, false, 0.5F),
        /** 中: 導火線80tick、威力4.0(TNT相当)、通常破壊 */
        M(1, 80, 4.0F, true, false, false, 0.75F),
        /** 大: 導火線80tick、威力4.0、耐爆性無視 (破壊不能は壊さない) */
        L(2, 80, 3.0F, true, true, false, 1.0F),
        /** 中・粘着: Mに固着能力を追加 */
        M_STICKY(3, 80, 4.0F, true, false, true, 0.75F),
        /** 大・粘着: Lに固着能力を追加 */
        L_STICKY(4, 80, 3.0F, true, true, true, 1.0F);

        public final int id;
        /** 投擲から爆発までのtick数 */
        public final int fuseTicks;
        /** 爆発威力 (TNT=4.0) */
        public final float power;
        /** ブロックを破壊するか (false=S: エフェクトと音だけ) */
        public final boolean breaksBlocks;
        /** ブロックの耐爆性を無視するか (L系のみ) */
        public final boolean ignoreResistance;
        /** ブロック・エンティティに固着するか */
        public final boolean sticky;
        /** ワールド描画時のスケール (旧RenderSphereのLVスケール相当) */
        public final float renderScale;

        Type(int id, int fuseTicks, float power, boolean breaksBlocks, boolean ignoreResistance, boolean sticky, float renderScale) {
            this.id = id;
            this.fuseTicks = fuseTicks;
            this.power = power;
            this.breaksBlocks = breaksBlocks;
            this.ignoreResistance = ignoreResistance;
            this.sticky = sticky;
            this.renderScale = renderScale;
        }

        public static Type fromId(int id) {
            for (Type t : values()) {
                if (t.id == id) {
                    return t;
                }
            }
            return S;
        }

        public static Type fromStack(ItemStack stack) {
            if (stack.getItem() instanceof FirecrackerItem item) {
                return item.type;
            }
            return S;
        }
    }

    private final Type type;

    public FirecrackerItem(Type type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F,
                0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            FirecrackerEntity entity = new FirecrackerEntity(level, player, stack);
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(entity);
            // 着火済みを示す TNT 点火音
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
