package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.program.LabelPositionHolder;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    public static final Supplier<DataComponentType<ItemStack>> FORM_REFERENCE = DATA_COMPONENT_TYPES.register(
            "form_reference",
            () -> DataComponentType
                    .<ItemStack>builder()
                    .persistent(ItemStack.CODEC)
                    .networkSynchronized(ItemStack.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    public static final Supplier<DataComponentType<Set<BlockPos>>> CABLE_POSITIONS = DATA_COMPONENT_TYPES.register(
            "cable_positions",
            () -> DataComponentType.<Set<BlockPos>>builder()
                    .networkSynchronized(BlockPos.STREAM_CODEC
                                                 .apply(ByteBufCodecs.list())
                                                 .map(HashSet::new, ArrayList::new)).build()
    );
    public static final Supplier<DataComponentType<Set<BlockPos>>> CAPABILITY_POSITIONS = DATA_COMPONENT_TYPES.register(
            "cable_positions",
            () -> DataComponentType.<Set<BlockPos>>builder()
                    .networkSynchronized(BlockPos.STREAM_CODEC
                                                 .apply(ByteBufCodecs.list())
                                                 .map(HashSet::new, ArrayList::new)).build()
    );


    public static void register(IEventBus bus) {
        DATA_COMPONENT_TYPES.register(bus);
    }
}
