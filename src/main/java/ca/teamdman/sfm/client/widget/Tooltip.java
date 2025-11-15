package ca.teamdman.sfm.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Tooltip {
    private static final int MAX_WIDTH = 170;
    private final ITextComponent message;
    @Nullable
    private List<ITextComponent> cachedTooltip;

    @Nullable
    private ITextComponent narration;

    @Nullable
    public ITextComponent getNarration() {
        return narration;
    }

    public void setNarration(@Nullable ITextComponent narration) {
        this.narration = narration;
    }

    private Tooltip(ITextComponent pMessage, @Nullable ITextComponent pNarration) {
        this.message = pMessage;
        this.narration = pNarration;
    }

    public static Tooltip create(ITextComponent pMessage, @Nullable ITextComponent pNarration) {
        return new Tooltip(pMessage, pNarration);
    }

    public static Tooltip create(ITextComponent pMessage) {
        return new Tooltip(pMessage, pMessage);
    }

    public List<ITextComponent> toComponents() {
        if (this.cachedTooltip == null) {
            this.cachedTooltip = splitTooltip(Minecraft.getMinecraft(), this.message);
        }

        return this.cachedTooltip;
    }
    public List<String> toStrings() {
        if (this.cachedTooltip == null) {
            this.cachedTooltip = splitTooltip(Minecraft.getMinecraft(), this.message);
        }

        return this.cachedTooltip.stream().map(ITextComponent::getFormattedText).collect(Collectors.toList());
    }

    public static List<ITextComponent> splitTooltip(Minecraft pMinecraft, ITextComponent pMessage) {
        return split(pMessage, 170).stream().map(TextComponentString::new).collect(Collectors.toList());
    }

    public static List<String> split(ITextComponent text, int maxWidth) {
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        String formattedText = text.getFormattedText(); // Converts to legacy formatting codes (§)
        return Arrays.stream(formattedText.split("\\\\n"))
                .flatMap(s -> Stream.of(fontRenderer.listFormattedStringToWidth(s , maxWidth).toArray(new String[]{})))
                .collect(Collectors.toList());
    }
}