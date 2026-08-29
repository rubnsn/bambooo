package ruby.bamboo.core.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.BroadLeaveBlock;
import ruby.bamboo.block.IndLightBlock;
import ruby.bamboo.block.SlideDoorBlock;
import ruby.bamboo.item.BambooBowItem;

/**
 * クライアントサイドの描画設定。
 * <p>
 * 旧 1.10.2 版の setRenderLayer / BlockRenderLayer 相当。
 * 植物(crossモデル)系は cutout、葉は cutout_mipped を指定する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BambooClientSetup {

    private BambooClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // cross モデルの植物系 -> cutout
            cutout(BambooBlocks.BAMBOO.get());
            cutout(BambooBlocks.BAMBOO_SHOOT.get());
            cutout(BambooBlocks.RICE_PLANT.get());
            cutout(BambooBlocks.SAKURA_SAPLING.get());
            cutout(BambooBlocks.MAPLE_SAPLING.get());
            cutout(BambooBlocks.GINKGO_SAPLING.get());
            cutout(BambooBlocks.HINOKI_SAPLING.get());
            for (var indlight : BambooBlocks.INDLIGHTS) {
                cutout(indlight.get());
            }
            // 行灯 -> cutout (旧 BlockRenderLayer.CUTOUT 相当)
            cutout(BambooBlocks.ANDON.get());
            // 竹柵/欄間 -> cutout (透過テクスチャ)
            cutout(BambooBlocks.BAMBOO_PANE.get());
            cutout(BambooBlocks.BAMBOO_PANE2.get());
            cutout(BambooBlocks.BAMBOO_PANE3.get());
            cutout(BambooBlocks.RANMA.get());
            // sakura deco: すだれ/のれん -> cutout
            cutout(BambooBlocks.BLIND.get());
            cutout(BambooBlocks.NOREN_BLUE.get());
            cutout(BambooBlocks.NOREN_PURPLE.get());
            // 狐火 -> cutout (cross モデル、透過テクスチャ。旧 BlockRenderLayer.CUTOUT 相当)
            cutout(BambooBlocks.KITSUNEBI.get());
            // 葉 -> cutout_mipped
            ItemBlockRenderTypes.setRenderLayer(BambooBlocks.SAKURA_LEAVES.get(), RenderType.cutoutMipped());
            for (var broad : BambooBlocks.BROAD_LEAVES) {
                ItemBlockRenderTypes.setRenderLayer(broad.get(), RenderType.cutoutMipped());
            }
            ItemBlockRenderTypes.setRenderLayer(BambooBlocks.MAPLE_LEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(BambooBlocks.GINKGO_LEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(BambooBlocks.HINOKI_LEAVES.get(), RenderType.cutoutMipped());

            // 引き戸 (sakura-master準拠): 透過3種は translucent、それ以外は cutout
            for (var slide : BambooBlocks.SLIDE_DOORS) {
                SlideDoorBlock b = slide.get();
                if (b.isTranslucent()) {
                    ItemBlockRenderTypes.setRenderLayer(b, RenderType.translucent());
                } else {
                    ItemBlockRenderTypes.setRenderLayer(b, RenderType.cutout());
                }
            }

            // 壁棚 -> cutout (薄板モデル)
            cutout(BambooBlocks.WALL_SHELF.get());

            // 田んぼ -> 半ブロック(高さ8) + 水張り時は水色上面。water_stillテクスチャの半透明を正しく描画するため translucent
            ItemBlockRenderTypes.setRenderLayer(BambooBlocks.PADDY_FIELD.get(), RenderType.translucent());

            // 石臼の BER 登録 (旧 TESR 相当)
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                    BambooBlockEntities.MILL_STONE_BE.get(),
                    ruby.bamboo.block.entity.MillStoneBlockRenderer::new);

            // 石臼 GUI の Screen 登録 (未登録だと「Failed to create screen」でGUIが開かない)
            net.minecraft.client.gui.screens.MenuScreens.register(BambooMenus.MILL_STONE.get(),
                    ruby.bamboo.gui.MillStoneScreen::new);

            // 囲炉裏の BER 登録 (旧 TESR 相当)
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                    BambooBlockEntities.CAMPFIRE_BE.get(),
                    ruby.bamboo.block.entity.CampfireBlockRenderer::new);

            // 囲炉裏 GUI の Screen 登録
            net.minecraft.client.gui.screens.MenuScreens.register(BambooMenus.CAMPFIRE.get(),
                    ruby.bamboo.gui.CampfireScreen::new);

            // 袋 GUI の Screen 登録
            net.minecraft.client.gui.screens.MenuScreens.register(BambooMenus.SACK.get(),
                    ruby.bamboo.gui.SackScreen::new);

            // 壁棚の BER 登録 (sakura-master WallShelfItemRender 相当)
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                    BambooBlockEntities.WALL_SHELF_BE.get(),
                    ruby.bamboo.block.entity.WallShelfBlockRenderer::new);

            // 引き戸の BER 登録 (sakura-master SlideDoorRender 相当)
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                    BambooBlockEntities.SLIDE_DOOR_BE.get(),
                    ruby.bamboo.block.entity.SlideDoorBlockRenderer::new);

            // ミニチュア (箱庭) の BER 登録
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                    BambooBlockEntities.MINIATURE_BE.get(),
                    ruby.bamboo.client.renderer.MiniatureBlockRenderer::new);
            // ミニチュアはスケール描画のため translucent/cutout を混在させるが、BERで処理するため
            // ブロック側の RenderType は設定不要。ただし念のため cutout を指定しておく
            ItemBlockRenderTypes.setRenderLayer(BambooBlocks.MINIATURE.get(), RenderType.cutout());

            // カットブロックの BER 登録
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                    BambooBlockEntities.CUT_BLOCK_BE.get(),
                    ruby.bamboo.client.renderer.CutBlockRenderer::new);
            ItemBlockRenderTypes.setRenderLayer(BambooBlocks.CUT_BLOCK.get(), RenderType.cutout());

            // 温泉水 — 半透明 (Phase B) — ブロックと流体両方をtranslucentに (バニラ水と同様)
            ItemBlockRenderTypes.setRenderLayer(BambooBlocks.SPRING_WATER.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(BambooMod.SPRING_WATER_SOURCE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(BambooMod.SPRING_WATER_FLOWING.get(), RenderType.translucent());
            // 布団用椅子エンティティ (huton_chair) — 不可視レンダラ。未登録だと shouldRender で NPE
            net.minecraft.client.renderer.entity.EntityRenderers.register(BambooEntities.HUTON_CHAIR.get(),
                    ruby.bamboo.client.renderer.ChairRenderer::new);

            // 鈎縄フック (刀右クリック) — 太線quad帯レンダラ
            net.minecraft.client.renderer.entity.EntityRenderers.register(BambooEntities.KAGINAWA_HOOK.get(),
                    ruby.bamboo.client.renderer.KaginawaHookRenderer::new);

            // 風 (扇子) — 不可視 (パーティクルで演出)
            net.minecraft.client.renderer.entity.EntityRenderers.register(BambooEntities.WIND.get(),
                    ruby.bamboo.client.renderer.WindRenderer::new);

            // 竹弓の各種矢 (竹/松明/光/爆発) — 共通 bamboospear テクスチャ
            net.minecraft.client.renderer.entity.EntityRenderers.register(BambooEntities.BAMBOO_ARROW.get(),
                    ruby.bamboo.client.renderer.BambooArrowRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(BambooEntities.TORCH_ARROW.get(),
                    ruby.bamboo.client.renderer.BambooArrowRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(BambooEntities.LIGHT_ARROW.get(),
                    ruby.bamboo.client.renderer.BambooArrowRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(BambooEntities.EXPLODE_ARROW.get(),
                    ruby.bamboo.client.renderer.BambooArrowRenderer::new);

            // 手裏剣 — アイテム回転描画
            net.minecraft.client.renderer.entity.EntityRenderers.register(BambooEntities.SHURIKEN.get(),
                    ruby.bamboo.client.renderer.ShurikenRenderer::new);

            // 竹弓の引き絞りモデル (pull/pulling override)。バニラは Items.BOW にしか
            // 登録されないため、独自 BowItem 継承クラスには自前で登録が必要。
            registerBambooBowModelProperties();
        });
    }

    /**
     * 竹弓の pull / pulling アイテムモデルプロパティを登録 (旧 getBowModel 相当)。
     * バニラ弓と同じ閾値 (pull 0.65 / 0.9) でモデル override が発火する。
     */
    private static void registerBambooBowModelProperties() {
        net.minecraft.resources.ResourceLocation pulling = new net.minecraft.resources.ResourceLocation("minecraft", "pulling");
        net.minecraft.resources.ResourceLocation pull = new net.minecraft.resources.ResourceLocation("minecraft", "pull");
        net.minecraft.client.renderer.item.ItemProperties.register(BambooItems.BAMBOO_BOW.get(),
                pulling, (stack, level, entity, seed) -> {
                    return entity != null && entity.isUsingItem() ? 1.0F : 0.0F;
                });
        net.minecraft.client.renderer.item.ItemProperties.register(BambooItems.BAMBOO_BOW.get(),
                pull, (stack, level, entity, seed) -> {
                    if (entity == null) {
                        return 0.0F;
                    }
                    if (!entity.isUsingItem()) {
                        return 0.0F;
                    }
                    int charge = stack.getUseDuration() - entity.getUseItemRemainingTicks();
                    return net.minecraft.util.Mth.clamp(charge / 20.0F, 0.0F, 1.0F);
                });
    }

    private static void cutout(Block block) {
        ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout());
    }

    /**
     * 広葉の色乗算 (旧 BroadLeave#colorMultiplier 相当)。
     * 共通テクスチャ broadleaf.png に対しバリアント色を tintIndex=0 に乗算する。
     */
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        for (var broad : BambooBlocks.BROAD_LEAVES) {
            BroadLeaveBlock block = broad.get();
            int color = block.variant.color;
            event.register((state, level, pos, tintIndex) -> color, block);
        }
        // 新葉の tint (broadleaf.png を色乗算)
        event.register((state, level, pos, tintIndex) -> ruby.bamboo.block.MapleLeaveBlock.PETAL_COLOR, BambooBlocks.MAPLE_LEAVES.get());
        event.register((state, level, pos, tintIndex) -> ruby.bamboo.block.GinkgoLeaveBlock.PETAL_COLOR, BambooBlocks.GINKGO_LEAVES.get());
        event.register((state, level, pos, tintIndex) -> ruby.bamboo.block.HinokiLeaveBlock.PETAL_COLOR, BambooBlocks.HINOKI_LEAVES.get());

        // 温泉水 — PARENT_DIR で源泉の COLOR を辿り、染料色は鮮やかに、DEFAULTのみtint乗算 + 3-4ブロック馴染み
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) return 0xFFFFFF;
            if (level == null || pos == null) {
                int tint2;
                try { tint2 = ruby.bamboo.core.config.SpringConfig.COMMON.tintColor.get(); } catch (Exception e) { tint2 = 0xE0F8FF; }
                return ruby.bamboo.block.SpringWaterBlock.multiplyColor(ruby.bamboo.block.SpringColor.DEFAULT.color, tint2);
            }
            // 源泉の色を PARENT_DIR 鎖で辿る（ChunkRenderCache でも動作する BlockAndTintGetter 版）
            var srcPos = ruby.bamboo.block.SpringWaterBlock.findSource(level, pos, state, 32);
            int baseColor;
            if (srcPos != null) {
                var srcState = level.getBlockState(srcPos);
                if (srcState.getBlock() instanceof ruby.bamboo.block.SpringBlock) {
                    baseColor = srcState.getValue(ruby.bamboo.block.SpringBlock.COLOR).color;
                } else baseColor = ruby.bamboo.block.SpringColor.DEFAULT.color;
            } else baseColor = ruby.bamboo.block.SpringColor.DEFAULT.color;
            // 染料色は tint で薄めず純色で、DEFAULT/VANILLA のみ Forest×tint
            int tint;
            try { tint = ruby.bamboo.core.config.SpringConfig.COMMON.tintColor.get(); } catch (Exception e) { tint = 0xE0F8FF; }
            int selfCol;
            if (baseColor != ruby.bamboo.block.SpringColor.DEFAULT.color && baseColor != ruby.bamboo.block.SpringColor.VANILLA.color) {
                selfCol = 0xFF000000 | baseColor;
            } else {
                selfCol = ruby.bamboo.block.SpringWaterBlock.multiplyColor(baseColor, tint);
            }
            // 3x3 サンプリングで平均して馴染ませる（sakura 流のブレンドを簡略化）
            int rSum = (selfCol >> 16) & 0xFF, gSum = (selfCol >> 8) & 0xFF, bSum = selfCol & 0xFF, cnt = 1;
            for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var off = pos.relative(dir);
                var offState = level.getBlockState(off);
                if (!offState.is(BambooBlocks.SPRING_WATER.get())) continue;
                var offSrc = ruby.bamboo.block.SpringWaterBlock.findSource(level, off, offState, 32);
                int offBase = ruby.bamboo.block.SpringColor.DEFAULT.color;
                if (offSrc != null) {
                    var offSrcState = level.getBlockState(offSrc);
                    if (offSrcState.getBlock() instanceof ruby.bamboo.block.SpringBlock) offBase = offSrcState.getValue(ruby.bamboo.block.SpringBlock.COLOR).color;
                }
                int offCol;
                if (offBase != ruby.bamboo.block.SpringColor.DEFAULT.color && offBase != ruby.bamboo.block.SpringColor.VANILLA.color) offCol = 0xFF000000 | offBase;
                else offCol = ruby.bamboo.block.SpringWaterBlock.multiplyColor(offBase, tint);
                rSum += (offCol >> 16) & 0xFF; gSum += (offCol >> 8) & 0xFF; bSum += offCol & 0xFF; cnt++;
            }
            if (cnt == 1) return selfCol;
            return 0xFF000000 | ((rSum / cnt) << 16) | ((gSum / cnt) << 8) | (bSum / cnt);
        }, BambooBlocks.SPRING_WATER.get());
    }

    /**
     * 花びらパーティクルのプロバイダ登録 (旧 SakuraPetal エンティティの後継)。
     * texNum 1-3 の3種をそれぞれ登録する。
     */
    @SubscribeEvent
    public static void onRegisterParticleProviders(
            net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BambooParticles.PETAL_1.get(),
                ruby.bamboo.client.particle.PetalParticle.Provider::new);
        event.registerSpriteSet(BambooParticles.PETAL_2.get(),
                ruby.bamboo.client.particle.PetalParticle.Provider::new);
        event.registerSpriteSet(BambooParticles.PETAL_3.get(),
                ruby.bamboo.client.particle.PetalParticle.Provider::new);
    }

    /**
     * 間接照明(indlight)アイコンの色乗算。
     * <p>
     * 旧 1.10.2 版 ItemIndLight#getColorFromItemstack 相当。
     * フラットな item/generated アイコン (layer0: item/indlight.png, 白の斜線) に対し、
     * 16 色 (EnumDyeColor 相当) のマップカラーを tintIndex=0 に乗算する。
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        for (var indlight : BambooBlocks.INDLIGHTS) {
            IndLightBlock block = (IndLightBlock) indlight.get();
            int color = block.color.mapColor;
            event.register((stack, tintIndex) -> color, new Item[] { block.asItem() });
        }
        // 広葉のインベントリアイコンにも色乗算 (ブロックと同じバリアント色)
        for (var broad : BambooBlocks.BROAD_LEAVES) {
            BroadLeaveBlock block = broad.get();
            int color = block.variant.color;
            event.register((stack, tintIndex) -> color, new Item[] { block.asItem() });
        }
        event.register((stack, tintIndex) -> ruby.bamboo.block.MapleLeaveBlock.PETAL_COLOR, new Item[] { BambooBlocks.MAPLE_LEAVES.get().asItem() });
        event.register((stack, tintIndex) -> ruby.bamboo.block.GinkgoLeaveBlock.PETAL_COLOR, new Item[] { BambooBlocks.GINKGO_LEAVES.get().asItem() });
        event.register((stack, tintIndex) -> ruby.bamboo.block.HinokiLeaveBlock.PETAL_COLOR, new Item[] { BambooBlocks.HINOKI_LEAVES.get().asItem() });
    }
}
