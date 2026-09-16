package ruby.bamboo.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.CampfireBlock;
import ruby.bamboo.block.MillStoneBlock;
import ruby.bamboo.block.WallShelfBlock;
import ruby.bamboo.block.entity.CampfireBlockEntity;
import ruby.bamboo.block.entity.MillStoneBlockEntity;
import ruby.bamboo.block.entity.WallShelfBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade との任意連携。JEIプラグインと同方式で、Jade不在時はこのクラス自体が
 * ロードされないため Bamboo単体でも動作する (mods.toml の任意依存と対)。
 * <p>
 * 表示内容: 囲炉裏の燃料・調理進捗 / 石臼の粉砕進捗 / 壁棚の中身。
 * 進捗値はサーバー側で NBT に詰めて送り、クライアント側で読む。
 */
@WailaPlugin
public class BambooJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new CampfireProvider(), CampfireBlockEntity.class);
        registration.registerBlockDataProvider(new MillstoneProvider(), MillStoneBlockEntity.class);
        registration.registerBlockDataProvider(new WallShelfProvider(), WallShelfBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new CampfireProvider(), CampfireBlock.class);
        registration.registerBlockComponent(new MillstoneProvider(), MillStoneBlock.class);
        registration.registerBlockComponent(new WallShelfProvider(), WallShelfBlock.class);
    }

    // ===== 囲炉裏: 燃料残量 + 調理進捗 =====

    static class CampfireProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "campfire");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof CampfireBlockEntity be) {
                data.putInt("Fuel", be.getFuelAmount());
                data.putBoolean("Burning", be.isBurning());
                data.putInt("Cook", be.getCookProgress());
                ItemStack result = be.getCookingResult();
                if (!result.isEmpty()) {
                    data.put("Result", result.save(new CompoundTag()));
                }
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Fuel")) return;
            tooltip.add(Component.translatable("tooltip.bamboomod.jade.fuel", data.getInt("Fuel")));
            if (data.getBoolean("Burning")) {
                ItemStack result = data.contains("Result") ? ItemStack.of(data.getCompound("Result")) : ItemStack.EMPTY;
                if (!result.isEmpty()) {
                    tooltip.add(Component.translatable("tooltip.bamboomod.jade.cooking", result.getHoverName(), data.getInt("Cook")));
                } else {
                    tooltip.add(Component.translatable("tooltip.bamboomod.jade.cooking_progress", data.getInt("Cook")));
                }
            }
        }
    }

    // ===== 石臼: 粉砕進捗 =====

    static class MillstoneProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "millstone");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof MillStoneBlockEntity be) {
                data.putInt("Grind", be.getGrindTime());
                ItemStack input = be.getItem(0);
                if (!input.isEmpty()) {
                    // 品名表示用に1個分だけ送る
                    ItemStack one = input.copy();
                    one.setCount(1);
                    data.put("Input", one.save(new CompoundTag()));
                }
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Grind")) return;
            int grind = data.getInt("Grind");
            if (grind <= 0) return;
            int pct = Math.round((float) grind / MillStoneBlockEntity.MAX_GRINDTIME * 100);
            ItemStack input = data.contains("Input") ? ItemStack.of(data.getCompound("Input")) : ItemStack.EMPTY;
            if (!input.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.bamboomod.jade.grinding", input.getHoverName(), pct));
            } else {
                tooltip.add(Component.translatable("tooltip.bamboomod.jade.grinding_progress", pct));
            }
        }
    }

    // ===== 壁棚: 左右の中身 =====

    static class WallShelfProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "wall_shelf");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof WallShelfBlockEntity be) {
                ItemStack right = be.getItem(WallShelfBlockEntity.SLOT_RIGHT);
                ItemStack left = be.getItem(WallShelfBlockEntity.SLOT_LEFT);
                if (!right.isEmpty()) {
                    data.put("Right", right.save(new CompoundTag()));
                }
                if (!left.isEmpty()) {
                    data.put("Left", left.save(new CompoundTag()));
                }
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data.contains("Right")) {
                appendStack(tooltip, ItemStack.of(data.getCompound("Right")));
            }
            if (data.contains("Left")) {
                appendStack(tooltip, ItemStack.of(data.getCompound("Left")));
            }
        }

        private static void appendStack(ITooltip tooltip, ItemStack stack) {
            if (stack.isEmpty()) return;
            if (stack.getCount() > 1) {
                tooltip.add(Component.literal(stack.getCount() + "x ").append(stack.getHoverName()));
            } else {
                tooltip.add(stack.getHoverName());
            }
        }
    }
}
