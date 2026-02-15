package ca.teamdman.sfm.common.util;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.concurrent.atomic.AtomicInteger;

public class SFMComponentUtils {


    public static ITextComponent substring(
            ITextComponent component,
            int start,
            int end
    ) {
        ITextComponent rtn = new TextComponentString("");
        AtomicInteger seen = new AtomicInteger(0);

        String content = component.getUnformattedComponentText();
        int contentStart = Math.max(start - seen.get(), 0);
        int contentEnd = Math.min(end - seen.get(), content.length());

        if (contentStart < contentEnd) {
            rtn.appendSibling(new TextComponentString(content.substring(
                    contentStart,
                    contentEnd
            )).setStyle(component.getStyle()));
        }
        seen.addAndGet(content.length());

        for (ITextComponent sibling : component.getSiblings()) {
            content = sibling.getUnformattedText();
            contentStart = Math.max(start - seen.get(), 0);
            contentEnd = Math.min(end - seen.get(), content.length());

            if (contentStart < contentEnd) {
                rtn.appendSibling(new TextComponentString(content.substring(
                        contentStart,
                        contentEnd
                )).setStyle(sibling.getStyle()));
            }
            seen.addAndGet(content.length());
        }
        return rtn;
    }



//    public static ITextComponent substring(
//            ITextComponent component,
//            int start,
//            int end
//    ) {
//
//        var rtn = new TextComponentString("");
//        AtomicInteger seen = new AtomicInteger(0);
//        component.visit(
//                (style, content) -> {
//                    int contentStart = Math.max(start - seen.get(), 0);
//                    int contentEnd = Math.min(end - seen.get(), content.length());
//
//                    if (contentStart < contentEnd) {
//                        rtn.append(Component.literal(content.substring(contentStart, contentEnd)).withStyle(style));
//                    }
//                    seen.addAndGet(content.length());
//                    return Optional.empty();
//                },
//                Style.EMPTY
//        );
//        return rtn;
//    }

    public static int length(
            ITextComponent component
    ) {

        AtomicInteger seen = new AtomicInteger(component.getUnformattedComponentText().length());

        for (ITextComponent sibling : component) {
            String content = sibling.getUnformattedText();

            seen.addAndGet(content.length());
        }
        return seen.get();
    }

//    @MCVersionDependentBehaviour
//    public static void appendLore(
//            ItemStack stack,
//            Component... components
//    ) {
//
//        // Get or create the display tag
//        CompoundTag displayTag = stack.getOrCreateTag().getCompound("display");
//
//        // Get or create the lore list
//        ListTag lore;
//        if (displayTag.contains("Lore", Tag.TAG_LIST)) {
//            lore = displayTag.getList("Lore", Tag.TAG_STRING);
//        } else {
//            lore = new ListTag();
//        }
//
//        // Append our lore
//        lore.add(StringTag.valueOf(Component.Serializer.toJson(LocalizationKeys.FALLING_ANVIL_JEI_CONSUMED.getComponent())));
//
//        // Track the lore list back into the display tag
//        displayTag.put("Lore", lore);
//
//        // Track the display tag back into the stack
//        stack.getOrCreateTag().put("display", displayTag);
//    }

}
