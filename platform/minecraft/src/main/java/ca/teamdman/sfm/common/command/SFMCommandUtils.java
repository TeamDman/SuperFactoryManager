package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public final class SFMCommandUtils {
    private SFMCommandUtils() {
    }

    @MCVersionDependentBehaviour
    public static void sendSuccess(CommandSourceStack source, Supplier<Component> componentSupplier) {
        source.sendSuccess(componentSupplier.get(), true);
    }

    public static void sendFailure(CommandSourceStack source, Supplier<Component> componentSupplier) {
        source.sendFailure(componentSupplier.get());
    }
}