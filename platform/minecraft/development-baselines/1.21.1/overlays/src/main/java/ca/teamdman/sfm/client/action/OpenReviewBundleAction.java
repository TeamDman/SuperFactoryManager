package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.repository.SFMManagedReviewBundleRepository;
import ca.teamdman.sfm.client.review.repository.SFMRepositoryReviewException;
import ca.teamdman.sfm.client.review.repository.SFMRepositoryReviewRepository;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.review.repository.SFMRepositoryReviewWorkspace;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Opens a validated named bundle from the managed AppData inbox. */
public final class OpenReviewBundleAction implements SFMClientAction<SFMClientActionContext> {
    private static @Nullable SFMRepositoryReviewRepository automationRepository;

    public static void setRepositoryForAutomation(@Nullable SFMRepositoryReviewRepository repository) {
        automationRepository = repository;
    }

    private static SFMRepositoryReviewRepository repository() {
        return automationRepository == null ? SFMManagedReviewBundleRepository.getDefault() : automationRepository;
    }

    @Override public Component title() { return Component.literal("Open repository review bundle"); }
    @Override public Component description() {
        return Component.literal("Select a validated bundle from the managed review-bundle inbox");
    }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public void configureCommandNode(com.mojang.brigadier.builder.LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "bundle", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    try {
                        for (SFMRepositoryReviewRepository.BundleSummary summary : repository().listBundles()) {
                            builder.suggest(summary.id());
                            builder.suggest(summary.name());
                        }
                    } catch (SFMRepositoryReviewException ignored) {
                        // Execution supplies the detailed typed diagnostic.
                    }
                    return builder.buildFuture();
                })
                .executes(this::invoke));
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        String requested = StringArgumentType.getString(context, "bundle").trim();
        try {
            SFMRepositoryReviewRepository.OpenBundle opened = repository().open(requested);
            Minecraft minecraft = Minecraft.getInstance();
            SFMScreenChangeHelpers.setScreen(SFMRepositoryReviewWorkspace.create(
                    minecraft.screen, repository(), opened));
            context.getSource().sendFeedback(Component.literal(
                    (opened.restored() ? "Reopened " : "Opened ") + opened.summary().name()
                            + " · " + opened.summary().changedFileCount() + " changed files")
                    .withStyle(ChatFormatting.AQUA));
            for (String diagnostic : opened.diagnostics())
                context.getSource().sendFeedback(Component.literal(diagnostic).withStyle(ChatFormatting.YELLOW));
            return 1;
        } catch (SFMRepositoryReviewException exception) {
            String message = exception.getMessage();
            if (!exception.diagnostics().isEmpty()) message += ": " + String.join("; ", exception.diagnostics());
            throw new SimpleCommandExceptionType(Component.literal(message)).create();
        }
    }
}
