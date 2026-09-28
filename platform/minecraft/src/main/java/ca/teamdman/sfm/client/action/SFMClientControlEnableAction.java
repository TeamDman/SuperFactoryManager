package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.control.SFMClientControlLifecycle;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/** Opts the running client into the bounded request-file control surface. */
public final class SFMClientControlEnableAction implements SFMClientAction<SFMClientActionContext> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.control_files.title", "Enable file-driven client control");
    @SFMLocalizationDatagen
    public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.control_files.description",
            "Publish a bounded request/response directory for this running client");

    @Override public Component title() { return TITLE.getComponent(); }
    @Override public Component description() { return DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        try {
            Path directory = SFMClientControlLifecycle.enableFileControl();
            context.getSource().sendFeedback(Component.literal("file control: ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(directory.toString())));
            com.google.gson.JsonObject result = new com.google.gson.JsonObject();
            result.addProperty("schema", "sfm.client-control-files/1");
            result.addProperty("directory", directory.toString());
            context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of(
                    "sfm.client-control-files/1", result));
            return 1;
        } catch (Exception failure) {
            throw new IllegalStateException("Could not enable file-driven client control", failure);
        }
    }
}
