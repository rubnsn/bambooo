package ruby.bamboo.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * メイド服 (胴体防具・マーカー兼用)。
 * 村人の胴スロットに入れてメイド見た目の目印にする。防具値なし・実質無限耐久。
 * 描画は透明アーマーテクスチャ (maid_layer_1.png) のため不可視。
 * ゾンビ化しても HumanoidArmorLayer が透明を描くだけで見た目に出ない。
 */
public class MaidClothesItem extends ArmorItem {

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return 10000;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_LEATHER;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            // HumanoidArmorLayer が domain 分割して解決:
            // bamboomod:textures/models/armor/maid_layer_1.png
            return "bamboomod:maid";
        }

        @Override
        public float getToughness() {
            return 0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0F;
        }
    };

    public MaidClothesItem() {
        super(MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties());
    }
}
