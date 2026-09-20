package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.keybinding.SFMKeyBindingListModel;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.screen.SFMKeyBindingScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Semantic controls for the active SFM Key Binds manager. */
public final class SFMKeyBindingManagerAction implements SFMClientAction<SFMKeyBindingScreen> {
    public enum Kind {
        SORT_SET,
        SCOPE_SET,
        DISPLAY_SET
    }

    private final Kind kind;

    public SFMKeyBindingManagerAction(Kind kind) {
        this.kind = Objects.requireNonNull(kind);
    }

    @Override
    public Component title() {
        return Component.literal(switch (kind) {
            case SORT_SET -> "Set key-binding sort";
            case SCOPE_SET -> "Set key-binding scope";
            case DISPLAY_SET -> "Set key-binding identity display";
        });
    }

    @Override
    public Component description() {
        return Component.literal("Change one explicit SFM Key Binds list presentation setting");
    }

    @Override
    public SFMClientActionRequirement<SFMKeyBindingScreen> requirement() {
        return context -> {
            if (context.originatingHost() instanceof SFMKeyBindingScreen captured) {
                return SFMClientActionAvailability.available(captured);
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen instanceof SFMKeyBindingScreen current) {
                return SFMClientActionAvailability.available(current);
            }
            return SFMClientActionAvailability.unavailable(Component.literal(
                    "Open SFM Key Binds before changing its list presentation"));
        };
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        switch (kind) {
            case SORT_SET -> {
                addSort(node, "name", SFMKeyBindingListModel.SortColumn.NAME);
                addSort(node, "binding-count", SFMKeyBindingListModel.SortColumn.BINDING_COUNT);
            }
            case SCOPE_SET -> {
                node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("all")
                        .executes(context -> scope(context, null)));
                node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                                "situation_id",
                                SFMCanonicalTokenArgument.token()
                        )
                        .suggests((context, suggestions) -> {
                            SFMKeyBindingService.INSTANCE.situationIds().stream()
                                    .map(ResourceLocation::toString)
                                    .sorted()
                                    .forEach(suggestions::suggest);
                            return suggestions.buildFuture();
                        })
                        .executes(context -> scope(context, new ResourceLocation(
                                SFMCanonicalTokenArgument.get(context, "situation_id")))));
            }
            case DISPLAY_SET -> {
                node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("display-name")
                        .executes(context -> display(context, SFMKeyBindingScreen.IdentityMode.DISPLAY_NAME)));
                node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("action-id")
                        .executes(context -> display(context, SFMKeyBindingScreen.IdentityMode.ACTION_ID)));
            }
        }
    }

    private void addSort(
            LiteralArgumentBuilder<SFMClientActionSource> node,
            String literal,
            SFMKeyBindingListModel.SortColumn column
    ) {
        node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal(literal)
                .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("ascending")
                        .executes(context -> sort(context, column, SFMKeyBindingListModel.Direction.ASCENDING)))
                .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("descending")
                        .executes(context -> sort(context, column, SFMKeyBindingListModel.Direction.DESCENDING))));
    }

    @Override
    public int execute(SFMKeyBindingScreen target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Provide the required key-binding presentation arguments")).create();
    }

    private int sort(
            CommandContext<SFMClientActionSource> context,
            SFMKeyBindingListModel.SortColumn column,
            SFMKeyBindingListModel.Direction direction
    ) throws CommandSyntaxException {
        SFMKeyBindingScreen target = requireTarget(context);
        target.setSort(column, direction);
        return 1;
    }

    private int scope(CommandContext<SFMClientActionSource> context, ResourceLocation scope)
            throws CommandSyntaxException {
        SFMKeyBindingScreen target = requireTarget(context);
        if (scope != null && !SFMKeyBindingService.INSTANCE.situationIds().contains(scope)) {
            throw new SimpleCommandExceptionType(Component.literal("Unknown key-binding scope: " + scope)).create();
        }
        target.setSituationFilter(scope);
        return 1;
    }

    private int display(CommandContext<SFMClientActionSource> context, SFMKeyBindingScreen.IdentityMode mode)
            throws CommandSyntaxException {
        requireTarget(context).setIdentityMode(mode);
        return 1;
    }

    private SFMKeyBindingScreen requireTarget(CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        var availability = requirement().resolve(context.getSource().context());
        if (!availability.isAvailable()) {
            throw new SimpleCommandExceptionType(availability.unavailableReason()).create();
        }
        return availability.target();
    }
}
