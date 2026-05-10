package ca.teamdman.sfm.common.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.function.Consumer;

public record SFMTooltipProvider(SFMTooltipType type) implements TooltipProvider {
    public static final Codec<SFMTooltipProvider> CODEC = SFMTooltipType.CODEC.xmap(SFMTooltipProvider::new, SFMTooltipProvider::type);
    public static final StreamCodec<ByteBuf, SFMTooltipProvider> STREAM_CODEC = ByteBufCodecs.idMapper(
            i -> SFMTooltipType.values()[i],
            SFMTooltipType::ordinal
    ).map(SFMTooltipProvider::new, SFMTooltipProvider::type);

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        type.addToTooltip(context, consumer, flag, components);
    }
}
