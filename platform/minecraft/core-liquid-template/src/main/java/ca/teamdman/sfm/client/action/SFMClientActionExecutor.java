package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** One contextual Brigadier execution seam shared by the palette, prompts, and dynamic bindings. */
public final class SFMClientActionExecutor {
    private SFMClientActionExecutor() {
    }

    public static boolean isExecutable(ParseResults<SFMClientActionSource> parsed) {
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }

    public static int execute(
            String command,
            SFMClientActionContext context,
            Consumer<Component> feedback
    ) throws CommandSyntaxException {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.structured_action_results %}
        return execute(SFMClientActions.commandTree(), command, context, feedback, ignored -> {
        });
{% elsif features.keyboard_profiles %}
        return execute(SFMClientActions.commandTree(), command, context, feedback);
{% else %}
        return SFMClientActions.commandTree().execute(command, new SFMClientActionSource(context, feedback));
{% endif %}
{% else %}
        return SFMClientActions.commandTree().execute(command, new SFMClientActionSource(context, feedback));
{% endcase %}
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.structured_action_results %}

    public static int execute(
            String command,
            SFMClientActionContext context,
            Consumer<Component> feedback,
            Consumer<SFMClientActionStructuredResult> structuredResult
    ) throws CommandSyntaxException {
        return execute(SFMClientActions.commandTree(), command, context, feedback, structuredResult);
    }

{% if features.keyboard_profiles %}
    /**
     * Executes against an explicit registered-action surface while preserving
     * the same provenance scope as the global dispatcher.
     */
{% else %}
    /** Executes against an explicit registered-action surface. */
{% endif %}
    public static int execute(
            SFMClientActionCommandTree commandTree,
            String command,
            SFMClientActionContext context,
            Consumer<Component> feedback
    ) throws CommandSyntaxException {
        return execute(commandTree, command, context, feedback, ignored -> {
        });
    }

    public static int execute(
            SFMClientActionCommandTree commandTree,
            String command,
            SFMClientActionContext context,
            Consumer<Component> feedback,
            Consumer<SFMClientActionStructuredResult> structuredResult
    ) throws CommandSyntaxException {
        java.util.Objects.requireNonNull(commandTree, "commandTree");
{% if features.keyboard_profiles %}
        if (SFMClientActionInvocationTrace.current().isPresent()) {
            return commandTree.execute(command, new SFMClientActionSource(context, feedback, structuredResult));
        }
        try (SFMClientActionInvocationTrace.Scope traceScope = SFMClientActionInvocationTrace.activate(
                new SFMClientActionInvocationTrace.RegisteredActionProvenance(command)
        )) {
            return commandTree.execute(command, new SFMClientActionSource(context, feedback, structuredResult));
        }
{% else %}
        return commandTree.execute(command, new SFMClientActionSource(context, feedback, structuredResult));
{% endif %}
    }
{% elsif features.keyboard_profiles %}

    /**
     * Executes against an explicit registered-action surface while preserving
     * the same provenance scope as the global dispatcher.
     */
    public static int execute(
            SFMClientActionCommandTree commandTree,
            String command,
            SFMClientActionContext context,
            Consumer<Component> feedback
    ) throws CommandSyntaxException {
        java.util.Objects.requireNonNull(commandTree, "commandTree");
        if (SFMClientActionInvocationTrace.current().isPresent()) {
            return commandTree.execute(command, new SFMClientActionSource(context, feedback));
        }
        try (SFMClientActionInvocationTrace.Scope traceScope = SFMClientActionInvocationTrace.activate(
                new SFMClientActionInvocationTrace.RegisteredActionProvenance(command)
        )) {
            return commandTree.execute(command, new SFMClientActionSource(context, feedback));
        }
    }
{% endif %}
{% endcase %}
}
