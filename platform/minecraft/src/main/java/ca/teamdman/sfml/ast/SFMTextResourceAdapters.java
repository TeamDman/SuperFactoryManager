package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.item.DiskItem;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Read-only adapters for the initial {@code sfm:text} capability. */
public final class SFMTextResourceAdapters {
    private SFMTextResourceAdapters() {
    }

    public static boolean supports(ItemStack stack) {
        return stack.getItem() instanceof DiskItem
               || stack.is(Items.WRITABLE_BOOK)
               || stack.is(Items.WRITTEN_BOOK);
    }

    public static String read(ItemStack stack) {
        if (stack.getItem() instanceof DiskItem) {
            return DiskItem.getProgramStringReadOnly(stack);
        }
        if (!stack.is(Items.WRITABLE_BOOK) && !stack.is(Items.WRITTEN_BOOK)) {
            throw new IllegalArgumentException("Item does not provide sfm:text: " + stack);
        }
        var tag = stack.getTag();
        if (tag == null || !tag.contains("pages", Tag.TAG_LIST)) {
            return "";
        }
        ListTag pages = tag.getList("pages", Tag.TAG_STRING);
        List<String> text = new ArrayList<>(pages.size());
        for (int index = 0; index < pages.size(); index++) {
            String page = pages.getString(index);
            if (stack.is(Items.WRITABLE_BOOK)) {
                text.add(page);
                continue;
            }
            try {
                Component component = Component.Serializer.fromJson(page);
                if (component == null) {
                    throw new IllegalArgumentException("Written-book page decoded to null");
                }
                text.add(component.getString());
            } catch (RuntimeException malformedPage) {
                throw new IllegalArgumentException("Malformed written-book page " + index, malformedPage);
            }
        }
        return String.join("\n", text);
    }
}
