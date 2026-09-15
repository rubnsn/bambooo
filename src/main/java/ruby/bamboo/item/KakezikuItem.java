package ruby.bamboo.item;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import ruby.bamboo.entity.KakezikuEntity;

/**
 * 掛け軸アイテム (旧 ItemKakeziku の1.20.1移植)。
 * <p>
 * 旧レシピ: 棒上段 + 紙 + つづら中央。現行はつづら廃止のため
 * 囲炉裏レシピ (棒/紙/黒色染料) で代替 (data/bamboomod/recipes/campfire/kakeziku.json)。
 * 壁を右クリックすると収まる柄からランダムで掛かる (旧 onItemUse 相当)。
 */
public class KakezikuItem extends Item {

    public KakezikuItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isVertical()) {
            return InteractionResult.FAIL;
        }
        Level level = ctx.getLevel();
        BlockPos clickedPos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player != null && !player.mayUseItemAt(clickedPos.relative(face), face, stack)) {
            return InteractionResult.FAIL;
        }
        Optional<KakezikuEntity> created = KakezikuEntity.create(level, clickedPos, face);
        if (created.isEmpty()) {
            return InteractionResult.CONSUME;
        }
        KakezikuEntity entity = created.get();
        if (!level.isClientSide) {
            entity.playPlacementSound();
            level.addFreshEntity(entity);
        }
        if (player != null) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
