package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class SFMComponentUtils {
    public static MutableComponent substring(
            Component component,
            int start,
            int end
    ) {

        var rtn = Component.empty();
        AtomicInteger seen = new AtomicInteger(0);
        component.visit(
                (style, content) -> {
                    int contentStart = Math.max(start - seen.get(), 0);
                    int contentEnd = Math.min(end - seen.get(), content.length());

                    if (contentStart < contentEnd) {
                        rtn.append(Component.literal(content.substring(contentStart, contentEnd)).withStyle(style));
                    }
                    seen.addAndGet(content.length());
                    return Optional.empty();
                },
                Style.EMPTY
        );
        return rtn;
    }

    public static int length(
            Component component
    ) {

        AtomicInteger seen = new AtomicInteger(0);
        component.visit(content -> {
            seen.addAndGet(content.length());
            return Optional.empty();
        });
        return seen.get();
    }

    @MCVersionDependentBehaviour
    public static void appendLore(
            ItemStack stack,
            Component... components
    ) {

        // Get or create the display tag
        CompoundTag displayTag = stack.getOrCreateTag().getCompound("display");

        // Get or create the lore list
        ListTag lore;
        if (displayTag.contains("Lore", Tag.TAG_LIST)) {
            lore = displayTag.getList("Lore", Tag.TAG_STRING);
        } else {
            lore = new ListTag();
        }

        // Append our lore
        lore.add(StringTag.valueOf(Component.Serializer.toJson(LocalizationKeys.FALLING_ANVIL_JEI_CONSUMED.getComponent())));

        // Track the lore list back into the display tag
        displayTag.put("Lore", lore);

        // Track the display tag back into the stack
        stack.getOrCreateTag().put("display", displayTag);
    }

}
