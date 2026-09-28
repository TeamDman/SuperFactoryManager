package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.control.SFMClientControlLogBuffer;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.Level;

import java.util.List;

/** Returns a bounded, structured tail of the Java SFM logger. */
public final class SFMClientControlLogsAction implements SFMClientAction<SFMClientActionContext> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.logs.title", "Read SFM logs");
    @SFMLocalizationDatagen
    public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.logs.description", "Read recent Java log records with a severity filter");

    @Override public Component title() { return TITLE.getComponent(); }
    @Override public Component description() { return DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public void configureCommandNode(com.mojang.brigadier.builder.LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Integer>argument(
                        "tail", IntegerArgumentType.integer(1, 200))
                .executes(this::invoke)
                .then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                                "filter", StringArgumentType.word()).executes(this::invoke)));
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        int tail = 100;
        String filter = "INFO";
        try { tail = IntegerArgumentType.getInteger(context, "tail"); } catch (IllegalArgumentException ignored) { }
        try { filter = StringArgumentType.getString(context, "filter"); } catch (IllegalArgumentException ignored) { }
        Level level = filter.equalsIgnoreCase("all")
                ? null
                : Level.getLevel(filter.toUpperCase(java.util.Locale.ROOT));
        if (!filter.equalsIgnoreCase("all") && level == null) {
            throw new IllegalArgumentException("unknown log level: " + filter);
        }
        List<SFMClientControlLogBuffer.Entry> entries = SFMClientControlLogBuffer.snapshot(tail, level);
        JsonArray values = new JsonArray();
        for (SFMClientControlLogBuffer.Entry entry : entries) {
            JsonObject value = new JsonObject();
            value.addProperty("time", entry.epochMillis());
            value.addProperty("level", entry.level());
            value.addProperty("logger", entry.logger());
            value.addProperty("message", entry.message());
            values.add(value);
        }
        JsonObject result = new JsonObject();
        result.addProperty("schema", "sfm.logs/1");
        result.addProperty("tail", tail);
        result.addProperty("filter", level == null ? "ALL" : level.name());
        result.add("entries", values);
        context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of(
                "sfm.logs/1", result));
        context.getSource().sendFeedback(Component.literal("logs: " + entries.size()));
        return 1;
    }
}
