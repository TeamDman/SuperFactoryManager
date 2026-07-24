package ca.teamdman.sfm.client.theme;

import net.minecraft.network.chat.Style;

/** Immutable text presentation for a stable semantic syntax token id. */
public record SFMSyntaxStyle(int colour, boolean bold, boolean italic, boolean underlined) {
    public Style apply(Style base) {
        return base.withColor(colour).withBold(bold).withItalic(italic).withUnderlined(underlined);
    }
}
