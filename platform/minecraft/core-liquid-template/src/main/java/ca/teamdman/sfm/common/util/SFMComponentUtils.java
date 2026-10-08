package ca.teamdman.sfm.common.util;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.core.component.DataComponents;
{% endcase %}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.world.item.component.ItemLore;
{% endcase %}
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import java.util.ArrayList;
import java.util.Collections;
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        // Get or create the display tag
        CompoundTag displayTag = stack.getOrCreateTag().getCompound("display");
{% when '1.21', '1.21.1', '26.1.2' %}
        // Create a new lore list
        ArrayList<Component> newLore = new ArrayList<>();
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        // Get or create the lore list
        ListTag lore;
        if (displayTag.contains("Lore", Tag.TAG_LIST)) {
            lore = displayTag.getList("Lore", Tag.TAG_STRING);
        } else {
            lore = new ListTag();
{% when '1.21', '1.21.1', '26.1.2' %}
        // Re-add existing lore
        ItemLore existing = stack.get(DataComponents.LORE);
        if (existing != null) {
            newLore.addAll(existing.lines());
{% endcase %}
        }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        // Append our lore
        for (Component component : components) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(component)));
        }
{% when '1.21', '1.21.1', '26.1.2' %}
        // Add new lore
        Collections.addAll(newLore, components);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        // Track the lore list back into the display tag
        displayTag.put("Lore", lore);

        // Track the display tag back into the stack
        stack.getOrCreateTag().put("display", displayTag);
{% when '1.21', '1.21.1', '26.1.2' %}
        // Save to stack, satisfying data component immutability constraints
        stack.set(DataComponents.LORE, new ItemLore(Collections.unmodifiableList(newLore)));
{% endcase %}
    }

}
