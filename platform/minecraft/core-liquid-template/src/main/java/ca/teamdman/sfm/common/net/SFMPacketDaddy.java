package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
{% when "1.20.2", "1.20.3" %}
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;
{% when "1.20.4" %}
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
{% when "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import java.util.function.Supplier;


{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
public interface SFMPacketDaddy<T extends SFMPacket> {
    enum PacketDirection {
        SERVERBOUND,
        CLIENTBOUND
    }

    PacketDirection getPacketDirection();

    Class<T> getPacketClass();

    void encode(
            T msg,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            FriendlyByteBuf friendlyByteBuf
{% when "1.21", "1.21.1", "26.1.2" %}
            RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    T decode(FriendlyByteBuf friendlyByteBuf);
{% when "1.21", "1.21.1", "26.1.2" %}
    T decode(RegistryFriendlyByteBuf friendlyByteBuf);
{% endcase %}

    void handle(
            T msg,
            SFMPacketHandlingContext context
    );

    default void handleOuter(
            T msg,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            Supplier<NetworkEvent.Context> contextSupplier
{% when "1.20.2", "1.20.3" %}
            NetworkEvent.Context outerContext
{% when "1.20.4" %}
            PlayPayloadContext outerContext
{% when "1.21", "1.21.1", "26.1.2" %}
            IPayloadContext outerContext
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
        SFMPacketHandlingContext context = new SFMPacketHandlingContext(contextSupplier);
{% when "1.20.2", "1.20.3" %}
        SFMPacketHandlingContext context = new SFMPacketHandlingContext(()->outerContext);
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        SFMPacketHandlingContext context = new SFMPacketHandlingContext(outerContext);
{% endcase %}
{% if features.packet_direction_validation %}
        if (!context.hasExpectedDirection(getPacketDirection())) {
            SFM.LOGGER.warn(
                    "Rejected {} received in the wrong network direction",
                    getPacketClass().getSimpleName()
            );
            context.finish();
            return;
        }
{% endif %}
        context.enqueueAndFinish(() -> {
            try {
                handle(msg, context);
            } catch (Throwable t) {
                SFM.LOGGER.warn("Encountered exception while handling packet", t);
                throw t;
            }
        });
    }

    static String truncate(
            String input,
            int maxLength
    ) {
        if (input.length() > maxLength) {
            SFM.LOGGER.warn(
                    "input too big, truncation has occurred! (len={}, max={}, over={})",
                    input.length(),
                    maxLength,
                    maxLength - input.length()
            );
            String truncationWarning = "\n...truncated";
            return input.substring(0, maxLength - truncationWarning.length()) + truncationWarning;
        }
        return input;
    }
}
