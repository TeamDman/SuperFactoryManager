package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.control.SFMClientActionRuntime;
import ca.teamdman.sfm.client.control.SFMClientCaptureService;
import ca.teamdman.sfm.client.control.SFMClientControlLifecycle;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Palette and remote callers share these exact lifecycle/capture operations. */
public final class SFMActionServicesAction implements SFMClientAction<SFMClientActionContext> {
    public enum Operation { STATUS, FILES_DISABLE, VOX_ENABLE, VOX_DISABLE, CAPTURE, CAPTURE_STATUS, PUPPET_PAUSE, PUPPET_RESUME }
    private final Operation operation;

    public SFMActionServicesAction(Operation operation) { this.operation = operation; }
    @Override public Component title() { return Component.literal(switch (operation) {
        case STATUS -> "Action services status";
        case FILES_DISABLE -> "Disable File Action Server";
        case VOX_ENABLE -> "Enable Vox Action Server";
        case VOX_DISABLE -> "Disable Vox Action Server";
        case CAPTURE -> "Capture a fresh game frame";
        case CAPTURE_STATUS -> "Capture status";
        case PUPPET_PAUSE -> "Pause an exact puppet for external control";
        case PUPPET_RESUME -> "Resume an exact puppet";
    }); }
    @Override public Component description() { return title(); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() { return SFMClientActionAvailability::available; }

    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        if (operation == Operation.CAPTURE_STATUS || operation == Operation.PUPPET_RESUME) {
            node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("id", StringArgumentType.word()).executes(this::invoke));
        } else if (operation == Operation.PUPPET_PAUSE) {
            node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("id", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<SFMClientActionSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 300))
                            .executes(this::invoke)));
        } else node.executes(this::invoke);
    }

    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        SFMClientActionRuntime runtime = SFMClientControlLifecycle.runtime();
        JsonObject result;
        try {
            switch (operation) {
                case FILES_DISABLE -> runtime.disable(SFMClientActionRuntime.Transport.FILES);
                case VOX_ENABLE -> runtime.enableVox();
                case VOX_DISABLE -> runtime.disable(SFMClientActionRuntime.Transport.VOX);
                case PUPPET_PAUSE -> runtime.pausePuppet(StringArgumentType.getString(context, "id"), IntegerArgumentType.getInteger(context, "seconds"));
                case PUPPET_RESUME -> runtime.resumePuppet(StringArgumentType.getString(context, "id"));
                default -> { }
            }
            result = switch (operation) {
                case CAPTURE -> {
                    runtime.requireCurrentTarget();
                    yield SFMClientCaptureService.request(runtime.callerId());
                }
                case CAPTURE_STATUS -> SFMClientCaptureService.status(StringArgumentType.getString(context, "id"));
                default -> runtime.status();
            };
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not enable action transport", failure);
        }
        context.getSource().sendFeedback(Component.literal(result.toString()));
        context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of(result.get("schema").getAsString(), result));
        return 1;
    }
}
