package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMEntitySelectorResolver;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMSelectorDomains;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Copies the canonical locations of an explicit set of Explorer identities. */
public final class SFMExplorerLocationCopyAction implements SFMClientAction<SFMClientActionContext> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.explorer.location.copy.title",
            "Copy explorer location"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.explorer.location.copy.description",
            "Copy the exact canonical location of one or more Explorer panels"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry COPIED = new LocalizationEntry(
            "gui.sfm.client_action.explorer.location.copy.copied",
            "Copied %s explorer location(s)"
    );

    @Override
    public Component title() {
        return TITLE.getComponent();
    }

    @Override
    public Component description() {
        return DESCRIPTION.getComponent();
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "explorer_selector",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    builder.suggest("all");
                    SFMExplorerRuntime.get().repository().stateSnapshot().explorers().keySet().forEach(id ->
                            builder.suggest(SFMEntitySelector.exact(
                                    SFMEntitySelector.Domain.EXPLORER,
                                    id.value()
                            ).canonical()));
                    return builder.buildFuture();
                })
                .executes(this::invoke));
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        SFMEntitySelector selector;
        try {
            selector = SFMEntitySelector.parseCanonical(
                    SFMEntitySelector.Domain.EXPLORER,
                    SFMCanonicalTokenArgument.get(context, "explorer_selector"));
        } catch (RuntimeException failure) {
            throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).create();
        }
        SFMExplorerRuntime runtime = SFMExplorerRuntime.get();
        var resolution = SFMEntitySelectorResolver.resolve(
                selector,
                SFMSelectorDomains.explorers(runtime.repository()));
        if (!resolution.complete() || resolution.identities().isEmpty()) {
            String detail = resolution.diagnostics().isEmpty()
                    ? "No explorer matched " + selector.canonical()
                    : resolution.diagnostics().get(0).message();
            throw new SimpleCommandExceptionType(Component.literal(detail)).create();
        }
        List<String> locations = resolution.identities().stream()
                .sorted()
                .map(id -> runtime.repository().find(id).orElseThrow().session().snapshot().location().canonical())
                .toList();
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join(System.lineSeparator(), locations));
        context.getSource().sendFeedback(COPIED.getComponent(Component.literal(Integer.toString(locations.size()))));
        return PanelActionSupport.closePaletteAfter(Math.max(1, locations.size()));
    }
}
