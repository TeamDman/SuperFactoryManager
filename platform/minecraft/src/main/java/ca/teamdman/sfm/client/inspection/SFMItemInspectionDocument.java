package ca.teamdman.sfm.client.inspection;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Creates a stable, read-only snapshot of the item under a container cursor. */
public final class SFMItemInspectionDocument {
    private static final int MAX_PRETTY_NBT_DEPTH = 64;
    @Nullable
    private static volatile Supplier<Optional<ItemStack>> hoveredIngredientSource;

    private SFMItemInspectionDocument() {
    }

    /** JEI registers this client-only source while its runtime is available. */
    public static void setHoveredIngredientSource(@Nullable Supplier<Optional<ItemStack>> source) {
        hoveredIngredientSource = source;
    }

    public static Optional<ItemStack> hoveredStack(@Nullable Object host) {
        if (host instanceof AbstractContainerScreen<?> screen) {
            Slot slot = screen.hoveredSlot;
            if (slot != null && !slot.getItem().isEmpty()) return Optional.of(slot.getItem().copy());
        }
        // Optional overlays are registered at runtime. This class must not
        // link JEI classes when a player opens the inspector without JEI.
        return hoveredIngredient(hoveredIngredientSource);
    }

    static Optional<ItemStack> hoveredIngredient(@Nullable Supplier<Optional<ItemStack>> source) {
        if (source == null) return Optional.empty();
        return source.get().filter(stack -> !stack.isEmpty()).map(ItemStack::copy);
    }

    public static Optional<Captured> captureHovered(@Nullable Object host) {
        return hoveredStack(host).map(stack -> capture(stack, Minecraft.getInstance().player));
    }

    public static Captured capture(ItemStack stack, @Nullable Player player) {
        ItemStack snapshot = stack.copy();
        ResourceLocation registeredId = ForgeRegistries.ITEMS.getKey(snapshot.getItem());
        String itemId = registeredId == null ? "unregistered" : registeredId.toString();
        List<Component> tooltipComponents;
        try {
            tooltipComponents = snapshot.getTooltipLines(player, TooltipFlag.Default.NORMAL);
        } catch (RuntimeException tooltipFailure) {
            tooltipComponents = List.of(Component.literal("Unavailable: " + tooltipFailure.getClass().getSimpleName()
                    + ": " + oneLine(String.valueOf(tooltipFailure.getMessage()))));
        }

        @Nullable String packetValue = null;
        if (snapshot.getItem() instanceof PacketItem) {
            packetValue = PacketItem.getValue(snapshot)
                    .map(SFMValueJsonCodec::encodePretty)
                    .orElse("unavailable: packet data is missing or invalid");
        }

        String displayName = oneLine(snapshot.getHoverName().getString());
        String stackData = prettyTag(snapshot.save(new CompoundTag()));
        List<String> localizedTooltip = tooltipComponents.stream()
                .map(Component::getString)
                .map(SFMItemInspectionDocument::oneLine)
                .toList();
        List<String> unlocalizedTooltip = tooltipComponents.stream()
                .map(component -> oneLine(Component.Serializer.toJson(component)))
                .toList();
        return new Captured(
                "Item: " + displayName,
                renderWithTooltipComponents(displayName, itemId, snapshot.getCount(), localizedTooltip,
                        unlocalizedTooltip, stackData, packetValue),
                SFMTextDocumentLanguage.itemInspection()
        );
    }

    static String renderWithTooltipComponents(
            String displayName,
            String itemId,
            int count,
            List<String> localizedTooltip,
            List<String> unlocalizedTooltip,
            String stackData,
            @Nullable String packetValue
    ) {
        StringBuilder document = new StringBuilder(render(
                displayName, itemId, count, localizedTooltip, stackData, packetValue));
        document.append("\ntooltip (unlocalized):\n");
        if (unlocalizedTooltip.isEmpty()) {
            document.append("  (empty)\n");
        } else {
            unlocalizedTooltip.forEach(line -> document.append("  - ").append(oneLine(line)).append('\n'));
        }
        document.append("\ntooltip (localized):\n");
        if (localizedTooltip.isEmpty()) {
            document.append("  (empty)\n");
        } else {
            localizedTooltip.forEach(line -> document.append("  - ").append(oneLine(line)).append('\n'));
        }
        return document.toString();
    }

    static String render(
            String displayName,
            String itemId,
            int count,
            List<String> tooltip,
            String stackData,
            @Nullable String packetValue
    ) {
        StringBuilder document = new StringBuilder();
        document.append("schema: sfm.item-inspection/1\n")
                .append("name: ").append(oneLine(displayName)).append('\n')
                .append("item-id: ").append(itemId).append('\n')
                .append("count: ").append(count).append("\n\n")
                .append("tooltip:\n");
        if (tooltip.isEmpty()) {
            document.append("  (empty)\n");
        } else {
            tooltip.forEach(line -> document.append("  - ").append(oneLine(line)).append('\n'));
        }
        if (packetValue != null) {
            document.append("\npacket-value-json:\n").append(packetValue).append('\n');
        }
        document.append("\nitem-stack-data-snbt:\n").append(stackData).append('\n');
        return document.toString();
    }

    static String prettyTag(Tag tag) {
        StringBuilder output = new StringBuilder();
        appendTag(output, tag, 0);
        return output.toString();
    }

    private static void appendTag(StringBuilder output, Tag tag, int depth) {
        if (depth >= MAX_PRETTY_NBT_DEPTH) {
            output.append(tag);
            return;
        }
        if (tag instanceof CompoundTag compound) {
            List<String> keys = compound.getAllKeys().stream().sorted(Comparator.naturalOrder()).toList();
            if (keys.isEmpty()) {
                output.append("{}");
                return;
            }
            output.append("{\n");
            for (int index = 0; index < keys.size(); index++) {
                String key = keys.get(index);
                indent(output, depth + 1);
                output.append(quote(key)).append(": ");
                appendTag(output, compound.get(key), depth + 1);
                output.append(index + 1 == keys.size() ? '\n' : ",\n");
            }
            indent(output, depth);
            output.append('}');
            return;
        }
        if (tag instanceof ListTag list) {
            if (list.isEmpty()) {
                output.append("[]");
                return;
            }
            output.append("[\n");
            for (int index = 0; index < list.size(); index++) {
                indent(output, depth + 1);
                appendTag(output, list.get(index), depth + 1);
                output.append(index + 1 == list.size() ? '\n' : ",\n");
            }
            indent(output, depth);
            output.append(']');
            return;
        }
        if (tag instanceof net.minecraft.nbt.StringTag string) {
            // StringTag#toString delegates to Mojang's SNBT escaping, which
            // has differed across supported mappings for control newlines.
            // Explicitly quote the decoded value so the editor always sees a
            // single JSON/SNBT string token.
            output.append(quote(string.getAsString()));
            return;
        }
        output.append(tag);
    }

    private static void indent(StringBuilder output, int depth) {
        output.append("  ".repeat(Math.max(0, depth)));
    }

    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> quoted.append("\\\\");
                case '"' -> quoted.append("\\\"");
                case '\b' -> quoted.append("\\b");
                case '\f' -> quoted.append("\\f");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) {
                        quoted.append("\\u00")
                                .append(Character.forDigit(character >>> 4, 16))
                                .append(Character.forDigit(character & 0xf, 16));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace("\r", "\\r").replace("\n", "\\n");
    }

    public record Captured(String title, String content, SFMTextDocumentLanguage language) {
    }
}
