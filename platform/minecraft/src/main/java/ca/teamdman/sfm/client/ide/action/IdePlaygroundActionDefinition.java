package ca.teamdman.sfm.client.ide.action;

import ca.teamdman.sfm.client.registry.SFMIdePlaygroundActions;
import ca.teamdman.sfm.client.screen.IdePlaygroundScreen;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record IdePlaygroundActionDefinition(
        LocalizationEntry title,
        Consumer<IdePlaygroundScreen> executor,
        List<String> aliases,
    Supplier<String> keybindingHintSupplier
) {
    public IdePlaygroundActionDefinition {
        aliases = List.copyOf(aliases);
    }

    public Optional<String> keybindingHint() {
        if (keybindingHintSupplier == null) {
            return Optional.empty();
        }
        String value = keybindingHintSupplier.get();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public String id() {
        return Optional.ofNullable(SFMIdePlaygroundActions.registry().getId(this))
            .map(ResourceLocation::toString)
                .orElseThrow(() -> new IllegalStateException("IDE playground action is not registered: " + title.key().get()));
    }
}