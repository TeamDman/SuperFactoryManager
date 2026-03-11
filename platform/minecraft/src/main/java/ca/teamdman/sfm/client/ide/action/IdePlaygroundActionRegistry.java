package ca.teamdman.sfm.client.ide.action;

import ca.teamdman.sfm.client.registry.SFMIdePlaygroundActions;
import ca.teamdman.sfm.client.screen.IdePlaygroundScreen;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class IdePlaygroundActionRegistry {
    private IdePlaygroundActionRegistry() {
    }

    public static boolean run(String actionId, IdePlaygroundScreen screen) {
        return getDefinition(actionId)
                .map(action -> {
                    action.executor().accept(screen);
                    return true;
                })
                .orElse(false);
    }

    public static List<IdePlaygroundActionDefinition> definitions() {
        return SFMIdePlaygroundActions.registry().stream().toList();
    }

    public static Optional<IdePlaygroundActionDefinition> getDefinition(String actionId) {
        String normalized = actionId.trim().toLowerCase(Locale.ROOT);
        return definitions().stream()
                .filter(definition -> definition.id().equals(normalized))
                .findFirst();
    }

    public static Optional<String> resolveActionId(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (getDefinition(normalized).isPresent()) {
            return Optional.of(normalized);
        }
        if (normalized.startsWith("panel.") || normalized.startsWith("selection.")) {
            normalized = "sfm:" + normalized;
            if (getDefinition(normalized).isPresent()) {
                return Optional.of(normalized);
            }
        }
        String searchValue = normalized;

        return definitions().stream()
                .filter(definition -> definition.aliases().stream().anyMatch(alias -> alias.toLowerCase(Locale.ROOT).equals(searchValue)))
                .map(IdePlaygroundActionDefinition::id)
                .findFirst();
    }
}