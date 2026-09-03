package ruby.bamboo.item;

import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * 磁石 (sakura ItemMagnet の 1.20.1 移植)。
 * <p>
 * インベントリ所持中、8tick ごとに周囲 (半径5/高さ2) のアイテム・経験値を即回収する。サーバ側のみ。
 * sakura では矢・投擲物も対象だったが、1.20.1 に相当 API がないため初版は Item/XP のみ。
 */
public class ItemMagnetItem extends Item implements Accessory {

    public ItemMagnetItem(Properties properties) {
        super(properties);
    }

    @Override
    public void playerPostTick(Player player, ItemStack stack) {
        var level = player.level();
        if (level.isClientSide) {
            return;
        }
        if ((level.getGameTime() & 7L) != 0L) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(5.0D, 2.0D, 5.0D);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive);
        for (ItemEntity e : items) {
            e.playerTouch(player);
        }
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box, Entity::isAlive);
        for (ExperienceOrb e : orbs) {
            e.playerTouch(player);
        }
    }
}
