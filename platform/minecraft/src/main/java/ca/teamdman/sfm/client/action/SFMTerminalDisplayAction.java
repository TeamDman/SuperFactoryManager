package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.program.ClientProgramInboxReadSurface;
import ca.teamdman.sfm.client.terminal.*;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Pure status/declaration actions plus a descriptor-only, opaque-lease input admission boundary. */
public final class SFMTerminalDisplayAction implements SFMClientAction<SFMClientActionContext> {
    public enum Kind { DISPLAY, INPUT_STATUS, INPUT_EFFECT }
    public static final ResourceLocation DISPLAY = new ResourceLocation("sfm", "terminal/display");
    public static final ResourceLocation INPUT_STATUS = new ResourceLocation("sfm", "terminal/input/status");
    public static final ResourceLocation INPUT_EFFECT = new ResourceLocation("sfm", "terminal/input");
    public static final SFMValueSchema EFFECT_INPUT = SFMValueSchema.object(Map.of(
            "binding", required(TouchDisplayTerminalBinding.SCHEMA), "u", required(SFMValueSchema.floating(0, 1)),
            "v", required(SFMValueSchema.floating(0, 1))), false);
    public static final SFMValueSchema OUTPUT = SFMValueSchema.object(Map.of(
            "status", required(SFMValueSchema.string(1, 64)), "session", required(SFMValueSchema.string(0, 36)),
            "ready", required(SFMValueSchema.bool()), "input", required(SFMValueSchema.bool()),
            "attempted", required(SFMValueSchema.integer(0, Long.MAX_VALUE)), "rejected", required(SFMValueSchema.integer(0, Long.MAX_VALUE)),
            "acknowledged", required(SFMValueSchema.integer(0, Long.MAX_VALUE)), "inputState", required(SFMValueSchema.string(1, 64))), false);
    private final Kind kind;
    private final SFMClientActionDescriptor descriptor;

    public SFMTerminalDisplayAction(Kind kind) {
        this.kind = kind;
        ResourceLocation id = switch (kind) { case DISPLAY -> DISPLAY; case INPUT_STATUS -> INPUT_STATUS; case INPUT_EFFECT -> INPUT_EFFECT; };
        descriptor = new SFMClientActionDescriptor(id, kind == Kind.INPUT_EFFECT ? EFFECT_INPUT : TouchDisplayTerminalBinding.SCHEMA,
                OUTPUT, SFMClientActionDescriptor.ExecutionSide.CLIENT,
                kind == Kind.DISPLAY ? TouchDisplayTerminalBroker.SESSION : TouchDisplayTerminalBroker.INPUT,
                value -> {
                    SFMValue binding = kind == Kind.INPUT_EFFECT ? ((SFMValue.ObjectValue) value).fields().get("binding") : value;
                    var fields = ((SFMValue.ObjectValue) binding).fields();
                    ResourceLocation channel = new ResourceLocation(((SFMValue.StringValue) fields.get("channel")).value());
                    List<SFMClientActionDescriptor.DataScope> scopes = new ArrayList<>();
                    scopes.add(new SFMClientActionDescriptor.DataScope(TouchDisplayTerminalBroker.READ, binding));
                    if (kind != Kind.DISPLAY) {
                        if (!((SFMValue.BooleanValue) fields.get("input")).value()) throw new IllegalArgumentException("Input is not declared");
                        scopes.add(new SFMClientActionDescriptor.DataScope(TouchDisplayTerminalBroker.SESSION, binding));
                        scopes.add(new SFMClientActionDescriptor.DataScope(ClientProgramInboxReadSurface.READ, SFMValue.of(channel.toString())));
                    }
                    return new SFMClientActionDescriptor.InputCheck.Accepted(scopes);
                }, kind == Kind.INPUT_EFFECT ? SFMClientActionDescriptor.CostClass.CLIENT_EFFECT : SFMClientActionDescriptor.CostClass.LOCAL_READ,
                kind == Kind.INPUT_EFFECT ? SFMClientActionDescriptor.Acknowledgement.LOCAL_TRANSPORT_ATTEMPT_ONLY
                        : SFMClientActionDescriptor.Acknowledgement.LOCAL_RESULT_ONLY,
                Map.of("not_mounted", SFMClientActionDescriptor.StatusKind.REJECTED, "mounted", SFMClientActionDescriptor.StatusKind.ATTEMPTED,
                        "waiting", SFMClientActionDescriptor.StatusKind.ATTEMPTED));
    }

    @Override public Component title() { return Component.literal("Touch Display terminal " + kind.name().toLowerCase(Locale.ROOT)); }
    @Override public Component description() { return Component.literal("Exact local terminal binding; creation and input leases require explicit user controls"); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return ignored -> SFMClientActionAvailability.unavailable(Component.literal("Use the terminal mounts panel or an approved Client Manager"));
    }
    @Override public Optional<SFMClientActionDescriptor> programmaticDescriptor() { return Optional.of(descriptor); }
    @Override public Optional<SFMClientActionProgrammaticHandler> programmaticHandler() {
        if (kind == Kind.INPUT_EFFECT) return Optional.empty(); // No script or command can manufacture an input lease.
        return Optional.of((input, context) -> TouchDisplayTerminalRuntime.status(TouchDisplayTerminalBinding.parse(
                context.caller().orElseThrow(), input)));
    }
    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        throw new IllegalStateException("Terminal binding has no human-authority command fallback");
    }
    private static SFMValueSchema.Field required(SFMValueSchema value) { return SFMValueSchema.Field.required(value); }
}
