package ruby.bamboo.handler;

import net.minecraft.resources.ResourceLocation;
import ruby.bamboo.core.fishing.FishSize;
import ruby.bamboo.core.fishing.FishingEntry;

/**
 * サーバー側 pending データ。Map&lt;UUID, FishingPending&gt; で保持。
 * 仕様書 §5 準拠。
 */
public class FishingPending {

    public final FishingEntry entry;
    public final FishSize size;
    public final int distance;
    public final int startProgress;
    public final int fishStamina;
    public final int fishPower;
    public final FishingEntry.MovePattern movePattern;
    public final int bitePower;
    public final long castTick;
    /** 餌の種類を保持 (消費時に再特定するため) */
    public final ResourceLocation baitItemId;
    /** 餌がルアー (耐久制) かどうか */
    public final boolean baitIsLure;

    public FishingPending(FishingEntry entry, FishSize size, int distance,
                          int startProgress, int fishStamina, int fishPower,
                          FishingEntry.MovePattern movePattern,
                          int bitePower, long castTick,
                          ResourceLocation baitItemId, boolean baitIsLure) {
        this.entry = entry;
        this.size = size;
        this.distance = distance;
        this.startProgress = startProgress;
        this.fishStamina = fishStamina;
        this.fishPower = fishPower;
        this.movePattern = movePattern;
        this.bitePower = bitePower;
        this.castTick = castTick;
        this.baitItemId = baitItemId;
        this.baitIsLure = baitIsLure;
    }
}
