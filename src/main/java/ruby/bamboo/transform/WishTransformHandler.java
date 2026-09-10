package ruby.bamboo.transform;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * transform / untransform effect の適用。
 * 願い文言の一致ルールは datapack の bamboo_wish/transform.json が担い、
 * リザルトのセリフは lang の bamboomod.wish.result.transform.* が担う。
 * ここでは Cap への適用のみ行い、メッセージは送らない
 * (メッセージは WishManager 側で entry.message / messageFor から送る)。
 */
public final class WishTransformHandler {

    private WishTransformHandler() {
    }

    public static Component displayName(String entityId) {
        try {
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityId));
            if (type != null) {
                return type.getDescription();
            }
        } catch (Exception ignored) {
        }
        return Component.literal(entityId);
    }

    /**
     * Cap 適用のみ。成功で true。
     * 空文字は人間戻し(常に成功)。不正 id は人間へフォールバックして false。
     */
    public static boolean applyStatic(ServerPlayer player, String entityId) {
        if (entityId == null || entityId.isEmpty()) {
            TransformHelper.clearTransform(player);
            return true;
        }
        String id = entityId.trim();
        if (!TransformRegistry.isLivingId(id, player.serverLevel())) {
            TransformHelper.clearTransform(player);
            return false;
        }
        TransformHelper.setTransform(player, id);
        return true;
    }

    /** 採用種は lang の種族別セリフ、非採用種は汎用メッセージ。 */
    public static MutableComponent messageFor(String entityId) {
        if (TransformRegistry.isAdopted(entityId)) {
            try {
                var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityId));
                ResourceLocation key = type != null ? ForgeRegistries.ENTITY_TYPES.getKey(type) : null;
                if (key != null) {
                    return Component.translatable("bamboomod.wish.result.transform." + key.getPath(),
                            displayName(entityId));
                }
            } catch (Exception ignored) {
            }
        }
        return Component.translatable("bamboomod.wish.result.transform", displayName(entityId));
    }
}
