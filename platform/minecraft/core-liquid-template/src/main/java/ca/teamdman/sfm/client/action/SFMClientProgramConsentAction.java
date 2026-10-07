package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.program.*;
{% if features.client_frame_language %}
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
{% endif %}
import ca.teamdman.sfm.common.value.*;
{% if features.structured_action_results %}
import com.google.gson.JsonParser;
{% endif %}
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
{% if features.client_frame_language %}
import net.minecraft.core.BlockPos;
{% endif %}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Supplier;

/** Program callers can inspect/request only their own declared capability; never approval or history. */
public final class SFMClientProgramConsentAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation STATUS = new ResourceLocation("sfm", "client_program/consent/status");
    public static final ResourceLocation REQUEST = new ResourceLocation("sfm", "client_program/consent/request");
    public static final SFMValueSchema INPUT = SFMValueSchema.object(Map.of(
            "capability", SFMValueSchema.Field.required(SFMValueSchema.string(1, 256))), false);
    public static final SFMValueSchema OUTPUT = SFMValueSchema.object(Map.of(
            "status", SFMValueSchema.Field.required(SFMValueSchema.string(1, 64)),
            "consent", SFMValueSchema.Field.required(SFMValueSchema.string(0, 32)),
            "effective", SFMValueSchema.Field.required(SFMValueSchema.string(0, 32)),
            "authority", SFMValueSchema.Field.required(SFMValueSchema.string(0, 32)),
            "blockers", SFMValueSchema.Field.required(SFMValueSchema.array(SFMValueSchema.string(1, 128), 0, 16)),
            "changed", SFMValueSchema.Field.required(SFMValueSchema.bool()),
            "saved", SFMValueSchema.Field.required(SFMValueSchema.bool())), false);
    private final boolean request;
    private final Supplier<ClientProgramConsentService> service;
    private final ClientProgramConsentGate.Policy policy;
    private final SFMClientActionDescriptor descriptor;

    public SFMClientProgramConsentAction(boolean request) {
        this(request, ClientProgramConsentRuntime::service, SFMClientProgramConsentAction::policyBlockers);
    }
    public SFMClientProgramConsentAction(boolean request, Supplier<ClientProgramConsentService> service,
                                         ClientProgramConsentGate.Policy policy) {
        this.request = request; this.service = Objects.requireNonNull(service); this.policy = Objects.requireNonNull(policy);
        descriptor = createDescriptor();
    }
    @Override public Component title() { return Component.literal(request ? "Request client program consent" : "Read client program consent"); }
    @Override public Component description() { return Component.literal("Inspect or request one exact capability without opening a screen"); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() { return SFMClientActionAvailability::available; }
    @Override public Optional<SFMClientActionDescriptor> programmaticDescriptor() {
        return Optional.of(descriptor);
    }
    private SFMClientActionDescriptor createDescriptor() {
        Map<String, SFMClientActionDescriptor.StatusKind> statuses = new HashMap<>();
        for (String name : List.of("ok", "no_program_caller", "missing_manager", "invalid_capability", "invalid_input",
                "store_full", "invalid_evidence", "missing_evidence", "not_pending", "persistence_failed")) {
            statuses.put(name, name.equals("ok") ? SFMClientActionDescriptor.StatusKind.ATTEMPTED : SFMClientActionDescriptor.StatusKind.REJECTED);
        }
        return new SFMClientActionDescriptor(request ? REQUEST : STATUS, INPUT, OUTPUT,
                SFMClientActionDescriptor.ExecutionSide.CLIENT, ClientProgramConsentGate.EXECUTE,
                SFMClientActionDescriptor.ScopeResolver.none(), request ? SFMClientActionDescriptor.CostClass.CLIENT_EFFECT
                : SFMClientActionDescriptor.CostClass.LOCAL_READ,
                SFMClientActionDescriptor.Acknowledgement.LOCAL_RESULT_ONLY, statuses);
    }
    @Override public Optional<SFMClientActionProgrammaticHandler> programmaticHandler() {
        return Optional.of((input, context) -> context.caller().isPresent()
                ? query(context.caller().orElseThrow(), input) : result("no_program_caller", null, false, false));
    }

    public SFMValue query(ClientProgramIdentity identity, SFMValue input) {
        if (INPUT.validate(input).isPresent()) return result("invalid_input", null, false, false);
        ResourceLocation capability = ResourceLocation.tryParse(((SFMValue.StringValue)
                ((SFMValue.ObjectValue) input).fields().get("capability")).value());
        if (capability == null || !identity.requestedCapabilities().contains(capability)) {
            return result("invalid_capability", null, false, false);
        }
        var state = service.get();
        ClientProgramConsentService.Result operation = request ? state.request(identity, capability) : null;
        var evaluation = state.gate().evaluate(identity, capability, policy);
        return result(operation == null ? "ok" : operation.status().name().toLowerCase(Locale.ROOT), evaluation,
                operation != null && operation.changed(), operation != null && operation.saved());
    }

    private static SFMValue result(String status, ClientProgramConsentGate.Evaluation evaluation, boolean changed, boolean saved) {
        return SFMValue.object(Map.of("status", SFMValue.of(status),
                "consent", SFMValue.of(evaluation == null ? "" : evaluation.consent().name().toLowerCase(Locale.ROOT)),
                "effective", SFMValue.of(evaluation == null ? "" : evaluation.effective().name().toLowerCase(Locale.ROOT)),
                "authority", SFMValue.of(evaluation == null ? "" : evaluation.authority().name().toLowerCase(Locale.ROOT)),
                "blockers", SFMValue.array(evaluation == null ? List.of() : evaluation.policyBlockers().stream().map(SFMValue::of).toList()),
                "changed", SFMValue.of(changed), "saved", SFMValue.of(saved)));
    }

    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Integer>argument("x", IntegerArgumentType.integer())
                .then(RequiredArgumentBuilder.<SFMClientActionSource, Integer>argument("y", IntegerArgumentType.integer())
                .then(RequiredArgumentBuilder.<SFMClientActionSource, Integer>argument("z", IntegerArgumentType.integer())
                .then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("capability", SFMCanonicalTokenArgument.token())
                .executes(this::invoke)))));
    }
    @Override public int execute(SFMClientActionContext ignored, CommandContext<SFMClientActionSource> context) {
{% if features.client_frame_language %}
        BlockPos pos = new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"),
                IntegerArgumentType.getInteger(context, "z"));
        var level = Minecraft.getInstance().level;
        Optional<ClientProgramIdentity> identity = level != null && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof ClientManagerBlockEntity manager
                ? ClientManagerFrameRuntime.identityFor(manager) : Optional.empty();
        SFMValue output = identity.isEmpty() ? result("missing_manager", null, false, false)
                : query(identity.orElseThrow(), SFMValue.object(Map.of("capability", SFMValue.of(StringArgumentType.getString(context, "capability")))));
{% if features.structured_action_results %}
        context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of("sfm.client-program.consent/1",
                JsonParser.parseString(SFMValueSchema.canonicalActionJson(output))));
{% endif %}
        context.getSource().sendFeedback(Component.literal(SFMValueSchema.canonicalActionJson(output)));
        return 1;
{% else %}
        context.getSource().sendFeedback(Component.literal("Client Manager frame runtime is unavailable"));
        return 0;
{% endif %}
    }

    public static List<String> policyBlockers(ClientProgramIdentity identity, ResourceLocation capability) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        return server == null || !server.isSingleplayer() || server.isPublished()
                || !identity.world().serverEndpoint().equals("integrated")
                ? List.of("private_integrated_world_required") : List.of();
    }
}
