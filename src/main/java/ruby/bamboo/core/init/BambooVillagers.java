package ruby.bamboo.core.init;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;

/**
 * 村人専用職業「遊び人」と職業POI (ガチャポン) の登録。
 * BambooStructures と同じ方式で mod bus に接続する。
 * <p>
 * noko のガチャを村人が取り込むと遊び人に就職する (acquirable_job_site タグ側も必要)。
 * ローブは仮置き単色 (assets/.../profession/asobinin.png)。
 */
public class BambooVillagers {
    /** PoiType 用 DeferredRegister */
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister
            .create(ForgeRegistries.POI_TYPES, BambooMod.MODID);
    /** VillagerProfession 用 DeferredRegister */
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister
            .create(ForgeRegistries.VILLAGER_PROFESSIONS, BambooMod.MODID);

    public static final ResourceKey<PoiType> ASOBININ_POI_KEY = ResourceKey
            .create(ForgeRegistries.Keys.POI_TYPES, new ResourceLocation(BambooMod.MODID, "asobinin"));

    /** 職業ブロック: ガチャポン2種の全state (FACING×HALF) */
    public static final RegistryObject<PoiType> ASOBININ_POI = POI_TYPES.register("asobinin",
            () -> new PoiType(gachaStates(), 1, 1));

    /** 遊び人。held/acquirable とも同一POI。仕事SEなし */
    public static final RegistryObject<VillagerProfession> ASOBININ = PROFESSIONS.register("asobinin",
            () -> new VillagerProfession("asobinin",
                    (Holder<PoiType> poi) -> poi.is(ASOBININ_POI_KEY),
                    (Holder<PoiType> poi) -> poi.is(ASOBININ_POI_KEY),
                    ImmutableSet.of(), ImmutableSet.of(), null));

    private static Set<BlockState> gachaStates() {
        ImmutableSet.Builder<BlockState> builder = ImmutableSet.builder();
        for (Block block : new Block[] { BambooBlocks.GACHA.get(), BambooBlocks.GACHA_BLUE.get() }) {
            builder.addAll(block.getStateDefinition().getPossibleStates());
        }
        return builder.build();
    }

    public static void init() {
    }
}
