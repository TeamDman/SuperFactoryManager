package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;

/** Machine-readable listing of the action registry used by the command palette. */
public final class SFMClientControlActionListAction implements SFMClientAction<SFMClientActionContext> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.action_list.title", "List client actions");
    @SFMLocalizationDatagen
    public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.action_list.description", "List registered SFM client actions");

    @Override public Component title() { return TITLE.getComponent(); }
    @Override public Component description() { return DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public void configureCommandNode(com.mojang.brigadier.builder.LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "mode", StringArgumentType.word()).executes(this::invoke));
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        // StringArgumentType is intentionally read defensively: Brigadier's
        // literal continuation can invoke this action without a mode in older
        // command histories.
        String mode;
        try {
            mode = StringArgumentType.getString(context, "mode");
        } catch (IllegalArgumentException ignored) {
            mode = "available";
        }
        if (!mode.equals("available") && !mode.equals("all")) {
            throw new IllegalArgumentException("action list mode must be available or all");
        }
        JsonArray actions = new JsonArray();
        ArrayList<ResourceLocation> ids = new ArrayList<>(SFMClientActions.registry().keys());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : ids) {
            SFMClientAction<?> action = SFMClientActions.registry().get(id);
            if (action == null) continue;
            if ("available".equals(mode) && !action.requirement().resolve(context.getSource().context()).isAvailable()) continue;
            JsonObject value = new JsonObject();
            value.addProperty("id", id.toString());
            value.addProperty("title", action.title().getString());
            value.addProperty("description", action.description().getString());
{% if features.client_program_actions %}
            value.addProperty("programmatic", action.programmaticDescriptor().isPresent());
{% else %}
            value.addProperty("programmatic", false);
{% endif %}
            actions.add(value);
        }
        JsonObject result = new JsonObject();
        result.addProperty("schema", "sfm.action-list/1");
        result.addProperty("mode", mode);
        result.add("actions", actions);
        context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of(
                "sfm.action-list/1", result));
        context.getSource().sendFeedback(Component.literal("actions: " + actions.size()));
        return 1;
    }
}
