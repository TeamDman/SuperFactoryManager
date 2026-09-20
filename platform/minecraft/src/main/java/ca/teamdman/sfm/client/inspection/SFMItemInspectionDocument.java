package ca.teamdman.sfm.client.inspection;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

/** Creates a stable, read-only snapshot of the item under a container cursor. */
public final class SFMItemInspectionDocument {
    private static final int MAX_PRETTY_NBT_DEPTH = 64;

    private SFMItemInspectionDocument() {
    }

    public static Optional<ItemStack> hoveredStack(@Nullable Object host) {
        if (!(host instanceof AbstractContainerScreen<?> screen)) return Optional.empty();
        Slot slot = screen.hoveredSlot;
        if (slot == null || slot.getItem().isEmpty()) return Optional.empty();
        return Optional.of(slot.getItem().copy());
    }

    public static Optional<Captured> captureHovered(@Nullable Object host) {
        return hoveredStack(host).map(stack -> capture(stack, Minecraft.getInstance().player));
    }

    public static Captured capture(ItemStack stack, @Nullable Player player) {
        ItemStack snapshot = stack.copy();
        ResourceLocation registeredId = ForgeRegistries.ITEMS.getKey(snapshot.getItem());
        String itemId = registeredId == null ? "unregistered" : registeredId.toString();
        List<String> tooltip;
        try {
            tooltip = snapshot.getTooltipLines(player, TooltipFlag.Default.NORMAL)
                    .stream()
                    .map(line -> oneLine(line.getString()))
                    .toList();
        } catch (RuntimeException tooltipFailure) {
            tooltip = List.of("Unavailable: " + tooltipFailure.getClass().getSimpleName()
                              + ": " + oneLine(String.valueOf(tooltipFailure.getMessage())));
        }

        @Nullable String packetValue = null;
        if (snapshot.getItem() instanceof PacketItem) {
            packetValue = PacketItem.getValue(snapshot)
                    .map(SFMValueJsonCodec::encodePretty)
                    .orElse("unavailable: packet data is missing or invalid");
        }

        String displayName = oneLine(snapshot.getHoverName().getString());
        String stackData = prettyTag(snapshot.save(new CompoundTag()));
        return new Captured(
                "Item: " + displayName,
                render(displayName, itemId, snapshot.getCount(), tooltip, stackData, packetValue),
                SFMTextDocumentLanguage.plainText()
        );
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
        output.append(tag);
    }

    private static void indent(StringBuilder output, int depth) {
        output.append("  ".repeat(Math.max(0, depth)));
    }

    private static String quote(String value) {
        return '"' + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + '"';
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace("\r", "\\r").replace("\n", "\\n");
    }

    public record Captured(String title, String content, SFMTextDocumentLanguage language) {
    }
}
