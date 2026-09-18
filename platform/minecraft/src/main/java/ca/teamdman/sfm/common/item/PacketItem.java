package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import ca.teamdman.sfm.common.value.SFMPacketTooltipFormatter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** A stackable item carrier for one generic SFM value per item unit. */
public class PacketItem extends Item {
    public static final int MAX_STACK_SIZE = 64;
    public static final String CODEC_VERSION_TAG = "sfm:packet_codec";
    public static final String VALUE_JSON_TAG = "sfm:packet_value";

    @SFMLocalizationDatagen
    public static final LocalizationEntry PACKET_ITEM = new LocalizationEntry(
            () -> SFMItems.PACKET.get().getDescriptionId(),
            () -> "Data Packet"
    );

    public PacketItem() {
        super(new Item.Properties().stacksTo(MAX_STACK_SIZE));
    }

    public static ItemStack create(SFMValue value) {
        ItemStack stack = new ItemStack(SFMItems.PACKET.get());
        setValue(stack, value);
        return stack;
    }

    /**
     * Stores canonical copied data. Before data components this is represented
     * by versioned NBT fields on the stack.
     */
    @MCVersionDependentBehaviour
    public static void setValue(ItemStack stack, SFMValue value) {
        requirePacketStack(stack);
        String encoded = SFMValueJsonCodec.encode(value);
        var tag = stack.getOrCreateTag();
        tag.putInt(CODEC_VERSION_TAG, SFMValueJsonCodec.VERSION);
        tag.putString(VALUE_JSON_TAG, encoded);
    }

    /** Reads and decodes a copied value without creating NBT on a blank item. */
    @MCVersionDependentBehaviour
    public static Optional<SFMValue> getValue(ItemStack stack) {
        if (!(stack.getItem() instanceof PacketItem)) {
            return Optional.empty();
        }
        var tag = stack.getTag();
        if (tag == null
            || !tag.contains(CODEC_VERSION_TAG, Tag.TAG_INT)
            || !tag.contains(VALUE_JSON_TAG, Tag.TAG_STRING)
            || !SFMValueJsonCodec.isReadableVersion(tag.getInt(CODEC_VERSION_TAG))) {
            return Optional.empty();
        }
        try {
            return Optional.of(SFMValueJsonCodec.decode(
                    tag.getString(VALUE_JSON_TAG),
                    tag.getInt(CODEC_VERSION_TAG)
            ));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> lines,
            TooltipFlag detail
    ) {
        appendTooltipLines(stack, lines, SFMItemUtils.isClientAndMoreInfoKeyPressed());
    }

    /** Renders the tooltip for a supplied more-info state without polling physical keyboard input. */
    public static void appendTooltipLines(ItemStack stack, List<Component> lines, boolean expanded) {
        Optional<SFMValue> value = getValue(stack);
        lines.addAll(SFMPacketTooltipFormatter.describe(value, expanded, getNameLength(stack)));
        if (!expanded && value.isPresent()) {
            SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(lines);
        }
    }

    private static int getNameLength(ItemStack stack) {
        return stack.getHoverName().getString().length();
    }

    private static void requirePacketStack(ItemStack stack) {
        if (!(stack.getItem() instanceof PacketItem)) {
            throw new IllegalArgumentException("Packet values can only be stored on packet items");
        }
    }
}
