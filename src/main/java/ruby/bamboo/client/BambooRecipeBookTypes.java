package ruby.bamboo.client;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterRecipeBookCategoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.world.inventory.RecipeBookType;
import ruby.bamboo.BambooMod;
import ruby.bamboo.crafting.cooking.BambooCampfireRecipe;
import ruby.bamboo.gui.CampfireMenu;

/**
 * 囲炉裏レシピブックのカテゴリ登録 (CLIENT)。
 * <p>
 * vanilla炉はFURNACE_SEARCH/FOOD/BLOCKS/MISC、囲炉裏は独自Type BAMBOO_CAMPFIREで同様の4分割を行う。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BambooRecipeBookTypes {

    private BambooRecipeBookTypes() {}

    public static final RecipeBookType BAMBOO_CAMPFIRE = CampfireMenu.BAMBOO_CAMPFIRE_TYPE;

    public static final RecipeBookCategories BAMBOO_CAMPFIRE_SEARCH = RecipeBookCategories.create("BAMBOO_CAMPFIRE_SEARCH",
            new ItemStack(Items.COMPASS));
    public static final RecipeBookCategories BAMBOO_CAMPFIRE_FOOD = RecipeBookCategories.create("BAMBOO_CAMPFIRE_FOOD",
            new ItemStack(Items.PORKCHOP));
    public static final RecipeBookCategories BAMBOO_CAMPFIRE_BLOCKS = RecipeBookCategories.create("BAMBOO_CAMPFIRE_BLOCKS",
            new ItemStack(Blocks.BRICKS));
    public static final RecipeBookCategories BAMBOO_CAMPFIRE_MISC = RecipeBookCategories.create("BAMBOO_CAMPFIRE_MISC",
            new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.EMERALD));

    @SubscribeEvent
    public static void registerCategories(RegisterRecipeBookCategoriesEvent event) {
        // BookType -> Categories
        event.registerBookCategories(BAMBOO_CAMPFIRE,
                List.of(BAMBOO_CAMPFIRE_SEARCH, BAMBOO_CAMPFIRE_FOOD, BAMBOO_CAMPFIRE_BLOCKS, BAMBOO_CAMPFIRE_MISC));
        // SEARCHは他3つのaggregate
        event.registerAggregateCategory(BAMBOO_CAMPFIRE_SEARCH,
                List.of(BAMBOO_CAMPFIRE_FOOD, BAMBOO_CAMPFIRE_BLOCKS, BAMBOO_CAMPFIRE_MISC));
        // Recipe -> Category 振り分け
        event.registerRecipeCategoryFinder(BambooMod.CAMPFIRE_RECIPE_TYPE.get(), recipe -> {
            if (recipe instanceof BambooCampfireRecipe r) {
                return switch (r.category()) {
                    case FOOD -> BAMBOO_CAMPFIRE_FOOD;
                    case BLOCKS -> BAMBOO_CAMPFIRE_BLOCKS;
                    case MISC -> BAMBOO_CAMPFIRE_MISC;
                };
            }
            return BAMBOO_CAMPFIRE_MISC;
        });
    }
}
