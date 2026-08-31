package ruby.bamboo.mixin.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ruby.bamboo.api.ILightColor;
import ruby.bamboo.core.init.BambooCapabilities;

/**
 * マルチプレイでのクライアント同期用。
 * LevelChunk.setBlockState の onPlace/onRemove は isClientSide でガードされクライアントでは呼ばれないため、
 * Level.markAndNotifyBlock (両sideで呼ばれる onBlockStateChange 経路) で capability map を維持する。
 * サーバ/クライアント両方で走るが、tint はクライアント描画のみで使われる。
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "markAndNotifyBlock", at = @At("TAIL"), remap = false)
    private void bamboomod$onBlockStateChange(BlockPos pos, LevelChunk levelChunk, BlockState oldState, BlockState newState, int flags, int recursionLeft, CallbackInfo ci) {
        // 空気同士や同一Stateは Level 側で弾かれているが念のため
        if (oldState == newState) return;
        boolean oldIsLight = oldState.getBlock() instanceof ILightColor;
        boolean newIsLight = newState.getBlock() instanceof ILightColor;
        if (!oldIsLight && !newIsLight) return;

        Level level = (Level) (Object) this;
        if (!level.hasChunkAt(pos)) return;
        // levelChunk 引数は null の場合がある (getChunkAt で再取得)
        LevelChunk chunk = levelChunk;
        if (chunk == null) {
            if (!level.hasChunkAt(pos)) return;
            chunk = level.getChunkAt(pos);
        }
        final LevelChunk fChunk = chunk;

        // 削除: 旧が光源で新が別色/非光源なら除去
        if (oldIsLight) {
            // 新も光源で同ブロック(同色)なら map 更新不要（updateShape による NORTH 等の変化）
            boolean sameLight = newIsLight && oldState.getBlock() == newState.getBlock();
            if (!sameLight) {
                fChunk.getCapability(BambooCapabilities.COLORED_LIGHT).ifPresent(storage -> {
                    storage.getMap().remove(Long.valueOf(pos.asLong()));
                    storage.incrementVersion();
                    // 周囲 3x3 の tintCache を無効化（描画キャッシュ）
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int cx = fChunk.getPos().x + dx;
                            int cz = fChunk.getPos().z + dz;
                            if (!level.hasChunk(cx, cz)) continue;
                            LevelChunk c2 = level.getChunk(cx, cz);
                            c2.getCapability(BambooCapabilities.COLORED_LIGHT).ifPresent(s2 -> s2.invalidateTintCache());
                        }
                    }
                });
                // 新が光源でもないならここで終了（削除のみ）
                if (!newIsLight) return;
            } else {
                // 同色光源のプロパティ更新のみなら何もしない
                return;
            }
        }

        // 追加: 新が光源なら登録
        if (newIsLight) {
            ILightColor light = (ILightColor) newState.getBlock();
            int col = light.getLightColor(newState, level, pos) & 0xFFFFFF;
            fChunk.getCapability(BambooCapabilities.COLORED_LIGHT).ifPresent(storage -> {
                storage.getMap().put(Long.valueOf(pos.asLong()), col);
                storage.setScanned(true);
                storage.incrementVersion();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int cx = fChunk.getPos().x + dx;
                        int cz = fChunk.getPos().z + dz;
                        if (!level.hasChunk(cx, cz)) continue;
                        LevelChunk c2 = level.getChunk(cx, cz);
                        c2.getCapability(BambooCapabilities.COLORED_LIGHT).ifPresent(s2 -> s2.invalidateTintCache());
                    }
                }
            });
        }
    }
}
