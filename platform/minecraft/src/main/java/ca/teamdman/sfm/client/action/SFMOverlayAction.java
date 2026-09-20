package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.overlay.scene.SFMClientOverlayRuntime;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.InputMode;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Placement;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.SceneState;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneController;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Registered command-palette and remote-control adapter for the typed overlay scene engine. */
public final class SFMOverlayAction implements SFMClientAction<SFMClientActionContext> {
    public enum Kind {
        VISIBILITY_SET,
        VISIBILITY_TOGGLE,
        PLACEMENT_SET,
        INPUT_MODE_SET,
        FOCUS_ACQUIRE,
        FOCUS_RELEASE,
        Z_ORDER_SET
    }

    /** Narrow seam used by unit tests; production always delegates to the singleton runtime. */
    interface RuntimeAccess {
        Iterable<String> overlayIds();

        SceneState scene();

        SFMOverlaySceneController.BatchResult execute(
                SFMEntitySelector selector,
                SFMOverlaySceneController.Operation operation
        );
    }

    private static final RuntimeAccess LIVE_RUNTIME = new RuntimeAccess() {
        @Override
        public Iterable<String> overlayIds() {
            return SFMClientOverlayRuntime.get().overlayIds();
        }

        @Override
        public SceneState scene() {
            return SFMClientOverlayRuntime.get().scene();
        }

        @Override
        public SFMOverlaySceneController.BatchResult execute(
                SFMEntitySelector selector,
                SFMOverlaySceneController.Operation operation
        ) {
            return SFMClientOverlayRuntime.get().execute(selector, operation);
        }
    };

    private final Kind kind;
    private final RuntimeAccess runtime;

    public SFMOverlayAction(Kind kind) {
        this(kind, LIVE_RUNTIME);
    }

    SFMOverlayAction(Kind kind, RuntimeAccess runtime) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public Component title() {
        return Component.literal(switch (kind) {
            case VISIBILITY_SET -> "Set overlay visibility";
            case VISIBILITY_TOGGLE -> "Toggle overlay visibility";
            case PLACEMENT_SET -> "Set overlay placement";
            case INPUT_MODE_SET -> "Set overlay input mode";
            case FOCUS_ACQUIRE -> "Focus overlay";
            case FOCUS_RELEASE -> "Release overlay focus";
            case Z_ORDER_SET -> "Set overlay z-order";
        });
    }

    @Override
    public Component description() {
        return Component.literal("Apply one explicit selector-targeted overlay operation");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent()
                );
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, String> selector = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(
                        "overlay_selector",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    selectorSuggestions().forEach(builder::suggest);
                    return builder.buildFuture();
                });

        switch (kind) {
            case VISIBILITY_SET -> selector
                    .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("visible")
                            .executes(context -> invokeOperation(
                                    context,
                                    new SFMOverlaySceneController.SetVisibility(true)
                            )))
                    .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("hidden")
                            .executes(context -> invokeOperation(
                                    context,
                                    new SFMOverlaySceneController.SetVisibility(false)
                            )));
            case VISIBILITY_TOGGLE -> selector.executes(context -> invokeOperation(
                    context,
                    new SFMOverlaySceneController.ToggleVisibility()
            ));
            case PLACEMENT_SET -> selector.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument(
                            "placement",
                            SFMCanonicalTokenArgument.token()
                    )
                    .suggests((context, builder) -> {
                        placementSuggestions().forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(this::invokePlacement));
            case INPUT_MODE_SET -> selector
                    .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("passive")
                            .executes(context -> invokeOperation(
                                    context,
                                    new SFMOverlaySceneController.SetInputMode(InputMode.PASSIVE)
                            )))
                    .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("interactive")
                            .executes(context -> invokeOperation(
                                    context,
                                    new SFMOverlaySceneController.SetInputMode(InputMode.INTERACTIVE)
                            )));
            case FOCUS_ACQUIRE -> selector.executes(context -> invokeOperation(
                    context,
                    new SFMOverlaySceneController.Focus()
            ));
            case FOCUS_RELEASE -> selector.executes(context -> invokeOperation(
                    context,
                    new SFMOverlaySceneController.Release()
            ));
            case Z_ORDER_SET -> selector.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, Integer>argument(
                            "z_order",
                            IntegerArgumentType.integer(-1_000_000, 1_000_000)
                    )
                    .executes(context -> invokeOperation(
                            context,
                            new SFMOverlaySceneController.SetZOrder(
                                    IntegerArgumentType.getInteger(context, "z_order")
                            )
                    )));
        }
        node.then(selector);
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Provide an overlay selector and the required operation argument"
        )).create();
    }

    private int invokeOperation(
            CommandContext<SFMClientActionSource> context,
            SFMOverlaySceneController.Operation operation
    ) throws CommandSyntaxException {
        try {
            SFMEntitySelector selector = SFMEntitySelector.parseCanonical(
                    SFMEntitySelector.Domain.OVERLAY,
                    SFMCanonicalTokenArgument.get(context, "overlay_selector")
            );
            SFMOverlaySceneController.BatchResult result = runtime.execute(selector, operation);
            for (SFMOverlaySceneController.TargetResult target : result.targets()) {
                context.getSource().sendFeedback(Component.literal(
                        target.id().value()
                                + " ["
                                + target.status().name().toLowerCase(Locale.ROOT).replace('_', '-')
                                + "] "
                                + target.message()
                ));
            }
            for (String diagnostic : result.diagnostics()) {
                context.getSource().sendFeedback(Component.literal("overlay diagnostic: " + diagnostic));
            }

            SFMOverlaySceneController.TargetResult rejected = result.targets().stream()
                    .filter(target -> target.status() == SFMOverlaySceneController.Status.REJECTED)
                    .findFirst()
                    .orElse(null);
            if (rejected != null || result.targets().isEmpty() || !result.diagnostics().isEmpty()) {
                String detail;
                if (!result.diagnostics().isEmpty()) {
                    detail = result.diagnostics().get(0);
                } else if (rejected != null) {
                    detail = rejected.id().value() + ": " + rejected.message();
                } else {
                    detail = "selector matched no overlays";
                }
                throw new SimpleCommandExceptionType(Component.literal(
                        "Overlay operation failed: " + detail
                )).create();
            }
            return result.targets().size();
        } catch (CommandSyntaxException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new SimpleCommandExceptionType(Component.literal(
                    failure.getMessage() == null
                            ? failure.getClass().getSimpleName()
                            : failure.getMessage()
            )).create();
        }
    }

    private int invokePlacement(
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        try {
            return invokeOperation(
                    context,
                    new SFMOverlaySceneController.SetPlacement(Placement.parseCanonical(
                            SFMCanonicalTokenArgument.get(context, "placement")
                    ))
            );
        } catch (CommandSyntaxException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new SimpleCommandExceptionType(Component.literal(
                    failure.getMessage() == null
                            ? failure.getClass().getSimpleName()
                            : failure.getMessage()
            )).create();
        }
    }

    private List<String> selectorSuggestions() {
        ArrayList<String> suggestions = new ArrayList<>();
        suggestions.add("focused");
        suggestions.add("all");
        ArrayList<String> exact = new ArrayList<>();
        for (String id : runtime.overlayIds()) {
            exact.add(SFMEntitySelector.exact(
                    SFMEntitySelector.Domain.OVERLAY,
                    id
            ).canonical());
        }
        exact.stream().sorted().forEach(suggestions::add);
        return List.copyOf(new LinkedHashSet<>(suggestions));
    }

    private List<String> placementSuggestions() {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        runtime.scene().overlays().stream()
                .map(overlay -> overlay.placement().canonical())
                .sorted()
                .forEach(suggestions::add);
        return List.copyOf(suggestions);
    }
}
