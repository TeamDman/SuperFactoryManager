package ca.teamdman.sfm.client.ide.action;

import ca.teamdman.sfm.client.registry.SFMIdePlaygroundActions;
import ca.teamdman.sfm.client.screen.IdePlaygroundScreen;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record IdeActionDefinition(
        LocalizationEntry title,

        Consumer<IdePlaygroundScreen> executor,

        Supplier<String> keybindingHintSupplier
) {

    public Optional<String> keybindingHint() {

        if (keybindingHintSupplier == null) {
            return Optional.empty();
        }
        String value = keybindingHintSupplier.get();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public @UnknownNullability ResourceLocation id() {
        return SFMIdePlaygroundActions.registry().getId(this);
    }

}