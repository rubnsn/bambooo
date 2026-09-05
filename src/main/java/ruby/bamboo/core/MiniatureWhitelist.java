package ruby.bamboo.core;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * ミニチュアの設置/操作ホワイトリスト (簡易版)。
 * <p>
 * 本来は ForgeConfigSpec で allowed/interactable を定義するが、Phase B/C ではコードベースの簡易判定で代用。
 * 要件7変更によりピストン/TNTも設置可だが内部実行は無効化されるため、canPlaceは原則true。
 */
public final class MiniatureWhitelist {

    private MiniatureWhitelist() {
    }

    /** 設置可否。現状は全ブロック許可 (空気以外)。危険ブロックも見た目のみなので許可。 */
    public static boolean canPlace(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        // 1.20.1で hasBlockEntity() == true のブロックも許可 (見た目のみ)
        // ただし barrier 等の不可視ブロックは除外してもよいが、簡易では全許可
        if (block == Blocks.BARRIER || block == Blocks.STRUCTURE_BLOCK || block == Blocks.STRUCTURE_VOID) {
            return false;
        }
        return true;
    }

    /** 右クリック操作を許可するブロックか (非TEのみ有効)。レバー/ボタン/ドア等。 */
    public static boolean canInteract(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.hasBlockEntity()) {
            return false; // TE所持は完全遮断 (要件3・8)
        }
        // プロパティで判定 (LIT / OPEN / POWERED を持つブロックは操作対象)
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.LIT) && isLampLike(state)) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            // レバー/ボタン等は POWERED を持つ
            return true;
        }
        // ブロック種別で判定
        Block block = state.getBlock();
        String id = block.toString(); // 簡易、登録名ではないが後で置換
        // lever, button, door, trapdoor, fence_gate 等
        // 名前ベースの簡易判定 (後でタグ #minecraft:doors 等に置換予定)
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
        if (name.contains("lever") || name.contains("button") || name.contains("door")
                || name.contains("trapdoor") || name.contains("fence_gate")) {
            return true;
        }
        return false;
    }

    private static boolean isLampLike(BlockState state) {
        // レッドストーンランプ等の LIT トグル対象。レッドストーントーチは除外 (常時点灯)
        Block block = state.getBlock();
        return block == Blocks.REDSTONE_LAMP;
    }

    /** トグル可能か試行し、新しい BlockState を返す。成功すれば Optional 的に非null。 */
    public static BlockState toggleInteractable(BlockState state) {
        if (!canInteract(state)) {
            return null;
        }
        try {
            if (state.hasProperty(BlockStateProperties.OPEN)) {
                boolean v = state.getValue(BlockStateProperties.OPEN);
                return state.setValue(BlockStateProperties.OPEN, !v);
            }
            if (state.hasProperty(BlockStateProperties.LIT) && state.getBlock() == Blocks.REDSTONE_LAMP) {
                boolean v = state.getValue(BlockStateProperties.LIT);
                return state.setValue(BlockStateProperties.LIT, !v);
            }
            if (state.hasProperty(BlockStateProperties.POWERED)) {
                boolean v = state.getValue(BlockStateProperties.POWERED);
                // lever/button は POWERED で状態反転、同時に OPEN 等があれば OPEN も連動させる必要があるが簡易では POWERED のみ
                return state.setValue(BlockStateProperties.POWERED, !v);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
