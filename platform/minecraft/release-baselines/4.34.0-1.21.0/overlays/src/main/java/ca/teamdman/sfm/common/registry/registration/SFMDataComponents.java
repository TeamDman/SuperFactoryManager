package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.component.ItemStackBox;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.CompressedBlockPosSet;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

public class SFMDataComponents {
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = DeferredRegister.create(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            SFM.MOD_ID
    );
    public static final Supplier<DataComponentType<String>> PROGRAM_STRING = DATA_COMPONENT_TYPES.register(
            "program",
            () -> DataComponentType
                    .<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .cacheEncoding()
                    .build()
    );
    public static final Supplier<DataComponentType<String>> ACTIVE_LABEL = DATA_COMPONENT_TYPES.register(
            "active_label",
            () -> DataComponentType
                    .<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .cacheEncoding()
                    .build()
    );
    public static final Supplier<DataComponentType<LabelGunItem.LabelGunViewMode>> LABEL_GUN_VIEW_MODE = DATA_COMPONENT_TYPES.register(
            "label_gun_view_mode",
            () -> DataComponentType
                    .<LabelGunItem.LabelGunViewMode>builder()
                    .persistent(LabelGunItem.LabelGunViewMode.CODEC)
                    .networkSynchronized(LabelGunItem.LabelGunViewMode.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );
    public static final Supplier<DataComponentType<Boolean>> OVERLAY_ENABLED = DATA_COMPONENT_TYPES.register(
            "overlay_enabled",
            () -> DataComponentType
                    .<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .cacheEncoding()
                    .build()
    );
    public static final Supplier<DataComponentType<NetworkToolItem.NetworkToolOverlayMode>> NETWORK_TOOL_OVERLAY_MODE = DATA_COMPONENT_TYPES.register(
            "network_tool_overlay_mode",
            () -> DataComponentType
                    .<NetworkToolItem.NetworkToolOverlayMode>builder()
                    .persistent(NetworkToolItem.NetworkToolOverlayMode.CODEC)
                    .networkSynchronized(NetworkToolItem.NetworkToolOverlayMode.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );
    public static final Supplier<DataComponentType<BlockPos>> NETWORK_TOOL_SELECTED_BLOCK_POS = DATA_COMPONENT_TYPES.register(
            "network_tool_selected_block_pos",
            () -> DataComponentType
                    .<BlockPos>builder()
                    .persistent(BlockPos.CODEC)
                    .networkSynchronized(BlockPos.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );
    public static final Supplier<DataComponentType<List<Component>>> PROGRAM_WARNINGS = DATA_COMPONENT_TYPES.register(
            "warnings",
            () -> DataComponentType
                    .<List<Component>>builder()
                    .persistent(Codec.list(ComponentSerialization.CODEC))
                    .networkSynchronized(ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .cacheEncoding()
                    .build()
    );
    public static final Supplier<DataComponentType<List<Component>>> PROGRAM_ERRORS = DATA_COMPONENT_TYPES.register(
            "errors",
            () -> DataComponentType
                    .<List<Component>>builder()
                    .persistent(Codec.list(ComponentSerialization.CODEC))
                    .networkSynchronized(ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .cacheEncoding()
                    .build()
    );
    public static final Supplier<DataComponentType<LabelPositionHolder>> LABEL_POSITION_HOLDER = DATA_COMPONENT_TYPES.register(
            "labels",
            () -> DataComponentType
                    .<LabelPositionHolder>builder()
                    .persistent(LabelPositionHolder.CODEC.codec())
                    .networkSynchronized(LabelPositionHolder.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    public static final Supplier<DataComponentType<ItemStackBox>> FORM_REFERENCE = DATA_COMPONENT_TYPES.register(
            "form_reference",
            () -> DataComponentType
                    .<ItemStackBox>builder()
                    .persistent(ItemStackBox.CODEC)
                    .networkSynchronized(ItemStackBox.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    public static final Supplier<DataComponentType<CompressedBlockPosSet>> CABLE_POSITIONS = DATA_COMPONENT_TYPES.register(
            "cable_positions",
            () -> DataComponentType
                    .<CompressedBlockPosSet>builder()
                    .networkSynchronized(CompressedBlockPosSet.STREAM_CODEC)
                    .persistent(CompressedBlockPosSet.CODEC)
                    .cacheEncoding()
                    .build()
    );
    public static final Supplier<DataComponentType<CompressedBlockPosSet>> CAPABILITY_POSITIONS = DATA_COMPONENT_TYPES.register(
            "capability_positions",
            () -> DataComponentType
                    .<CompressedBlockPosSet>builder()
                    .networkSynchronized(CompressedBlockPosSet.STREAM_CODEC)
                    .persistent(CompressedBlockPosSet.CODEC)
                    .cacheEncoding()
                    .build()
    );


    public static void register(IEventBus bus) {
        DATA_COMPONENT_TYPES.register(bus);
    }
}
