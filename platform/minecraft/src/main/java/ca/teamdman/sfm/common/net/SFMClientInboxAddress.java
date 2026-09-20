package ca.teamdman.sfm.common.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Stable routing address; it deliberately does not contain a program hash or consent decision. */
public record SFMClientInboxAddress(
        UUID recipient,
        ResourceLocation dimension,
        ResourceLocation channel
) {
    public static final int MAX_ID_CHARACTERS = 128;

    public SFMClientInboxAddress {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(channel, "channel");
        if (dimension.toString().length() > MAX_ID_CHARACTERS
            || channel.toString().length() > MAX_ID_CHARACTERS) {
            throw new IllegalArgumentException("Inbox identifier exceeds " + MAX_ID_CHARACTERS + " characters");
        }
    }

    public void encode(FriendlyByteBuf target) {
        target.writeUUID(recipient);
        target.writeUtf(dimension.toString(), MAX_ID_CHARACTERS);
        target.writeUtf(channel.toString(), MAX_ID_CHARACTERS);
    }

    public static SFMClientInboxAddress decode(FriendlyByteBuf source) {
        return new SFMClientInboxAddress(
                source.readUUID(),
                new ResourceLocation(source.readUtf(MAX_ID_CHARACTERS)),
                new ResourceLocation(source.readUtf(MAX_ID_CHARACTERS))
        );
    }
}
