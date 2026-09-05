package ruby.bamboo.core.init;

import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import ruby.bamboo.BambooMod;
import ruby.bamboo.capability.ColoredLightStorage;

/**
 * Phase B: ColoredLight Attachment 登録 + BEホッパー連携 (RegisterCapabilitiesEvent)。
 *
 * <p>1.21.1 NeoForge: 旧 Forge Capability / LazyOptional / ICapabilitySerializable /
 * AttachCapabilitiesEvent を削除。ColoredLight は非永続化 (serialize無し・メモリ保持のみ)
 * の Chunk Attachment へ移行 (再起動後は再スキャンで再構築)。
 * BE の ITEM_HANDLER (ホッパー連携) は BlockCapability へ移行し、本クラスで一括登録する。
 */
public final class BambooCapabilities {

    /** AttachmentType 用 DeferredRegister。BambooMod 側で {@link #init(IEventBus)} を呼ぶこと。 */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BambooMod.MODID);

    /** Chunk 付随の発光色 sparse storage。serialize無し (メモリ保持のみ・非永続化)。 */
    public static final Supplier<AttachmentType<ColoredLightStorage>> COLORED_LIGHT =
            ATTACHMENTS.register("colored_light",
                    () -> AttachmentType.builder(() -> new ColoredLightStorage()).build());

    /** 旧 capability ID 互換用に維持 (Attachment 登録名と同一)。 */
    public static final ResourceLocation COLORED_LIGHT_ID =
            ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "colored_light");

    private BambooCapabilities() {
    }

    /**
     * modBus への登録。BambooMod コンストラクタから呼ぶこと
     * ({@code BambooCapabilities.init(modEventBus)})。
     */
    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(RegisterCapabilitiesEvent.class, BambooCapabilities::registerCaps);
    }

    /** BEホッパー連携 (旧 getCapability/ForgeCapabilities.ITEM_HANDLER 相当)。 */
    private static void registerCaps(RegisterCapabilitiesEvent event) {
        // 囲炉裏・石臼: WorldlyContainer + SidedInvWrapper。旧仕様通り side==null には公開しない。
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BambooBlockEntities.CAMPFIRE_BE.get(),
                (be, side) -> side == null ? null : new SidedInvWrapper(be, side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BambooBlockEntities.MILL_STONE_BE.get(),
                (be, side) -> side == null ? null : new SidedInvWrapper(be, side));
        // 壁棚: 旧仕様では side==null に handlers[0](DOWN) を返していたため維持。
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BambooBlockEntities.WALL_SHELF_BE.get(),
                (be, side) -> new SidedInvWrapper(be, side == null ? Direction.DOWN : side));
        // 和風チェスト: 旧仕様通り 全side (+null) に InvWrapper を公開。
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BambooBlockEntities.JP_CHEST_BE.get(),
                (be, side) -> new InvWrapper(be));
        // 竹鉢はホッパー無効のため登録しない (旧 getCapability は super 委譲のみ)。
    }
}
