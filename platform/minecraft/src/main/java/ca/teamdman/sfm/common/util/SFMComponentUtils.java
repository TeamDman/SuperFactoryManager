package ca.teamdman.sfm.common.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Collections;
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

        // Create a new lore list
        ArrayList<Component> newLore = new ArrayList<>();

        // Re-add existing lore
        ItemLore existing = stack.get(DataComponents.LORE);
        if (existing != null) {
            newLore.addAll(existing.lines());
        }

        // Add new lore
        Collections.addAll(newLore, components);

        // Save to stack, satisfying data component immutability constraints
        stack.set(DataComponents.LORE, new ItemLore(Collections.unmodifiableList(newLore)));
    }

}
