package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.net.SFMMultiplayerClientRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Public bounded transport diagnostics, never a grant or an assertion of downstream processing. */
public final class SFMPacketRemoteStatusAction implements SFMClientAction<SFMClientActionContext> {
    public static final String SCHEMA = "sfm.packet.remote_status/1";
    @Override public Component title() { return Component.literal("Remote packet status"); }
    @Override public Component description() { return Component.literal("Inspect negotiation and bounded server receipts; receipts are not downstream completion"); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() { return SFMClientActionAvailability::available; }
    @Override public int execute(SFMClientActionContext ignored, CommandContext<SFMClientActionSource> context) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", SCHEMA);
        result.addProperty("available", SFMMultiplayerClientRuntime.available());
        result.addProperty("session", SFMMultiplayerClientRuntime.session().map(Object::toString).orElse(""));
        result.addProperty("diagnostic", SFMMultiplayerClientRuntime.diagnostics());
        result.addProperty("pending_acknowledgements", SFMMultiplayerClientRuntime.pendingAcknowledgements());
        result.addProperty("downstream_completion_acknowledged", false);
        JsonArray receipts = new JsonArray();
        for (var receipt : SFMMultiplayerClientRuntime.acknowledgements()) {
            JsonObject item = new JsonObject();
            item.addProperty("session", receipt.session().toString());
            item.addProperty("sequence", receipt.sequence());
            item.addProperty("operation", receipt.operation());
            item.addProperty("status", receipt.status().name());
            item.addProperty("insertion", receipt.insertion().map(Enum::name).orElse(""));
            receipts.add(item);
        }
        result.add("receipts", receipts);
        context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of(SCHEMA, result));
        context.getSource().sendFeedback(Component.literal(SFMMultiplayerClientRuntime.diagnostics()));
        return 1;
    }
}
