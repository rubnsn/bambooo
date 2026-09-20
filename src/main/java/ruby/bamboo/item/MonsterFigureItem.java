package ruby.bamboo.item;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ruby.bamboo.capsule.CapsuleRules;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.entity.FigureEntity;

/**
 * モンスターフィギュア (docs/port-spec-capsule-ball.md §10)。
 * 捕獲成功でボールは消費され、代わりに中身入りの本アイテムが渡される。
 * 単一アイテムをNBTで種別管理する。右クリックでワールドへ設置。
 */
public class MonsterFigureItem extends Item {

    public static final String TAG_ENTITY_ID = "FigureId";
    public static final String TAG_ENTITY = "FigureData";
    public static final String TAG_SCALE = "FigureScale";
    public static final String TAG_YAW = "FigureYaw";
    public static final String TAG_POSE = "FigurePose";

    /** 最小高さ (ブロック)。最大は実物高さ */
    public static final float MIN_HEIGHT = 0.25F;
    /** 既定高さ (ブロック) */
    public static final float DEFAULT_HEIGHT = 0.5F;

    public MonsterFigureItem(Properties properties) {
        super(properties);
    }

    /** 実物高さ (ブロック)。解決不可なら1.0 */
    public static float realHeight(@Nullable String entityId) {
        if (entityId == null) return 1.0F;
        try {
            var opt = EntityType.byString(entityId);
            if (opt.isPresent()) return Math.max(0.1F, opt.get().getHeight());
        } catch (Exception e) {
        }
        return 1.0F;
    }

    /** 既定スケール (高さ0.5ブロック相当。上限=等身) */
    public static float defaultScale(String entityId) {
        float h = realHeight(entityId);
        return Math.min(1.0F, DEFAULT_HEIGHT / h);
    }

    /** 最小スケール (高さ0.25ブロック相当) */
    public static float minScale(String entityId) {
        return MIN_HEIGHT / realHeight(entityId);
    }

    public static ItemStack create(String entityId, CompoundTag entityData, float scale, float yawDeg) {
        ItemStack stack;
        try {
            stack = new ItemStack(BambooItems.MONSTER_FIGURE.get());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
        var tag = stack.getOrCreateTag();
        tag.putString(TAG_ENTITY_ID, entityId);
        tag.put(TAG_ENTITY, entityData.copy());
        tag.putFloat(TAG_SCALE, scale);
        tag.putFloat(TAG_YAW, yawDeg);
        tag.put(TAG_POSE, new CompoundTag());
        return stack;
    }

    /** 捕獲タグ (id付き) からフィギュア化する。 */
    public static ItemStack createFromCapture(CompoundTag captureTag) {
        String id = captureTag.getString("id");
        if (id.isEmpty()) return ItemStack.EMPTY;
        return create(id, captureTag.copy(), defaultScale(id), 0.0F);
    }

    public record FigureData(String entityId, CompoundTag data, float scale, float yawDeg, CompoundTag pose) {
    }

    @Nullable
    public static FigureData read(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof MonsterFigureItem)) return null;
        var tag = stack.getOrCreateTag();
        String id = tag.getString(TAG_ENTITY_ID);
        if (id.isEmpty() || !tag.contains(TAG_ENTITY)) return null;
        CompoundTag pose = tag.contains(TAG_POSE) ? tag.getCompound(TAG_POSE).copy() : new CompoundTag();
        return new FigureData(id, tag.getCompound(TAG_ENTITY).copy(),
                tag.getFloat(TAG_SCALE) <= 0.0F ? defaultScale(id) : tag.getFloat(TAG_SCALE),
                tag.getFloat(TAG_YAW), pose);
    }

    /** 0.25 単位グリッドスナップ (Shift+設置用)。 */
    private static double snapQuarter(double v) {
        return Math.round(v * 4.0D) / 4.0D;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack held = context.getItemInHand();
        FigureData figure = read(held);
        if (figure == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var clicked = context.getClickedPos().relative(context.getClickedFace());
        // 設置先が塞がっていたら失敗
        if (!level.getBlockState(clicked).canBeReplaced()) return InteractionResult.FAIL;
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }
        Vec3 hit = context.getClickLocation();
        // 基本はクリック位置そのまま (上面なら hit.y が接地、その他は空気枡の床)。
        // Shift+設置は 0.25 単位グリッドスナップ (XZのみ。Yは接地優先)
        Vec3 at;
        if (context.getClickedFace() == net.minecraft.core.Direction.UP) {
            at = new Vec3(hit.x, hit.y, hit.z);
        } else {
            at = new Vec3(hit.x, clicked.getY(), hit.z);
        }
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            at = new Vec3(snapQuarter(at.x), at.y, snapQuarter(at.z));
        }
        // 飾る向き: 設置者と正対するよう180°反転 (Mob yawと同義)
        float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() + 180.0F : 180.0F;
        FigureEntity entity = FigureEntity.spawnFromFigure(serverLevel, at, yaw, held);
        if (entity == null) return InteractionResult.FAIL;
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.ARMOR_STAND_PLACE, SoundSource.PLAYERS, 0.7F, 1.0F);
        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            held.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        FigureData figure = read(stack);
        if (figure == null) {
            tooltip.add(Component.translatable("tooltip.bamboomod.figure.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.bamboomod.figure.contains",
                CapsuleRules.describeEntityId(figure.entityId())).withStyle(ChatFormatting.AQUA));
        float height = realHeight(figure.entityId()) * figure.scale();
        tooltip.add(Component.translatable("tooltip.bamboomod.figure.size",
                String.format("%.2f", height)).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void initializeClient(Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ruby.bamboo.client.FigureItemRenderer.getInstance();
            }
        });
    }
}
