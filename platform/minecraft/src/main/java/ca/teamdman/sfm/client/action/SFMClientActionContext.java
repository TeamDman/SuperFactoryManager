package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public record SFMClientActionContext(
        @Nullable Object originatingHost,
        BooleanSupplier originatingHostIsCurrent
) {
    @SFMLocalizationDatagen
    public static final LocalizationEntry ORIGINATING_HOST_CHANGED = new LocalizationEntry(
            "gui.sfm.client_action.context.originating_host_changed",
            "The originating client context is no longer active"
    );

    public SFMClientActionContext {
        Objects.requireNonNull(originatingHostIsCurrent);
    }

    public static SFMClientActionContext create(
            @Nullable Object originatingHost,
            BooleanSupplier originatingHostIsCurrent
    ) {
        return new SFMClientActionContext(originatingHost, originatingHostIsCurrent);
    }

    public <T> SFMClientActionAvailability<T> requireOriginatingHost(
            Class<T> requiredType,
            Component incompatibleHostReason
    ) {
        Objects.requireNonNull(requiredType);
        Objects.requireNonNull(incompatibleHostReason);
        if (!originatingHostIsCurrent.getAsBoolean()) {
            return SFMClientActionAvailability.unavailable(
                    ORIGINATING_HOST_CHANGED.getComponent().withStyle(ChatFormatting.RED)
            );
        }
        if (!requiredType.isInstance(originatingHost)) {
            return SFMClientActionAvailability.unavailable(incompatibleHostReason);
        }
        return SFMClientActionAvailability.available(requiredType.cast(originatingHost));
    }
}
