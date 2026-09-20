package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.program.*;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Program-only adapters: no implicit player/manager authority is borrowed from a human command. */
public final class SFMClientProgramReadAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation BLOCK = new ResourceLocation("sfm", "world/block_state/get");
    public static final ResourceLocation INBOX = new ResourceLocation("sfm", "client_inbox/read");
    public static final SFMValueSchema BLOCK_INPUT = SFMValueSchema.object(Map.of(
            "x", required(SFMValueSchema.integer(Integer.MIN_VALUE, Integer.MAX_VALUE)),
            "y", required(SFMValueSchema.integer(Integer.MIN_VALUE, Integer.MAX_VALUE)),
            "z", required(SFMValueSchema.integer(Integer.MIN_VALUE, Integer.MAX_VALUE)),
            "scope", required(SFMValueSchema.union(List.of(SFMValueSchema.literal(SFMValue.of("bound")),
                    SFMValueSchema.literal(SFMValue.of("loaded")))))), false);
    public static final SFMValueSchema INBOX_INPUT = SFMValueSchema.object(Map.of(
            "channel", required(SFMValueSchema.string(1, 256)),
            "mode", required(SFMValueSchema.literal(SFMValue.of("latest")))), false);
    private static final SFMValueSchema BLOCK_VALUE = SFMValueSchema.object(Map.of(
            "schema", required(SFMValueSchema.literal(SFMValue.of("sfm:block_state@1"))),
            "block", required(SFMValueSchema.string(1, 256)),
            "properties", required(SFMValueSchema.object(Map.of(), true))), false);
    public static final SFMValueSchema BLOCK_OUTPUT = SFMValueSchema.object(Map.of(
            "status", required(SFMValueSchema.string(1, 64)),
            "value", required(SFMValueSchema.optional(BLOCK_VALUE))), false);
    public static final SFMValueSchema INBOX_OUTPUT = SFMValueSchema.object(Map.of(
            "status", required(SFMValueSchema.string(1, 64)),
            "value", required(SFMValueSchema.any()), "sequence", required(SFMValueSchema.integer(0, Long.MAX_VALUE)),
            "cursor", required(SFMValueSchema.optional(SFMValueSchema.object(Map.of(
                    "session", required(SFMValueSchema.string(36, 36)), "stream", required(SFMValueSchema.string(36, 36)),
                    "after", required(SFMValueSchema.integer(0, Long.MAX_VALUE))), false))),
            "continuity", required(SFMValueSchema.optional(SFMValueSchema.string(1, 32))),
            "oldestSequence", required(SFMValueSchema.integer(0, Long.MAX_VALUE)),
            "newestSequence", required(SFMValueSchema.integer(0, Long.MAX_VALUE)), "hasMore", required(SFMValueSchema.bool())), false);

    @FunctionalInterface public interface Query { SFMValue read(ClientProgramIdentity identity, SFMValue input); }

    private final boolean inbox;
    private final Query query;
    private final SFMClientActionDescriptor descriptor;

    public SFMClientProgramReadAction(boolean inbox) {
        this(inbox, inbox ? (identity, input) -> ClientProgramReadRuntime.latest(identity, channel(input))
                : (identity, input) -> ClientProgramReadRuntime.block(identity, position(input), loaded(input)));
    }
    public SFMClientProgramReadAction(boolean inbox, Query query) {
        this.inbox = inbox; this.query = Objects.requireNonNull(query);
        Map<String, SFMClientActionDescriptor.StatusKind> statuses = new HashMap<>();
        for (String name : List.of("value", "empty", "unknown_unloaded", "unavailable_undeclared", "unavailable_awaiting_consent",
                "unavailable_denied_by_user", "unavailable_blocked_by_policy", "unavailable_out_of_scope",
                "unavailable_world_changed", "unavailable_budget", "unavailable_source", "unavailable_no_session")) {
            statuses.put(name, name.equals("value") || name.equals("empty") ? SFMClientActionDescriptor.StatusKind.ATTEMPTED
                    : SFMClientActionDescriptor.StatusKind.REJECTED);
        }
        descriptor = new SFMClientActionDescriptor(inbox ? INBOX : BLOCK, inbox ? INBOX_INPUT : BLOCK_INPUT,
                inbox ? INBOX_OUTPUT : BLOCK_OUTPUT, SFMClientActionDescriptor.ExecutionSide.CLIENT,
                ClientProgramConsentGate.EXECUTE, this::scopes, SFMClientActionDescriptor.CostClass.LOCAL_READ,
                SFMClientActionDescriptor.Acknowledgement.LOCAL_RESULT_ONLY, statuses);
    }
    private SFMClientActionDescriptor.InputCheck scopes(SFMValue input) {
        if (inbox) {
            ResourceLocation channel = channel(input);
            return new SFMClientActionDescriptor.InputCheck.Accepted(List.of(new SFMClientActionDescriptor.DataScope(
                    ClientProgramInboxReadSurface.READ, SFMValue.of(channel.toString()))));
        }
        BlockPos position = position(input);
        return new SFMClientActionDescriptor.InputCheck.Accepted(List.of(new SFMClientActionDescriptor.DataScope(
                loaded(input) ? ClientProgramBlockReadSurface.READ_LOADED : ClientProgramBlockReadSurface.READ_BOUND,
                SFMValue.object(Map.of("x", SFMValue.of(position.getX()), "y", SFMValue.of(position.getY()), "z", SFMValue.of(position.getZ()))))));
    }
    @Override public Component title() { return Component.literal(inbox ? "Read latest client inbox value" : "Read client block state"); }
    @Override public Component description() { return Component.literal("Read one exact declared target using the Client Manager's consent"); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return ignored -> SFMClientActionAvailability.unavailable(Component.literal("Requires a Client Manager program caller"));
    }
    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        throw new IllegalStateException("Client program reads have no human-authority fallback");
    }
    @Override public Optional<SFMClientActionDescriptor> programmaticDescriptor() { return Optional.of(descriptor); }
    @Override public Optional<SFMClientActionProgrammaticHandler> programmaticHandler() {
        return Optional.of((input, context) -> {
            ClientProgramIdentity caller = context.caller().orElseThrow(() -> new IllegalArgumentException("Missing Client Manager caller"));
            if (!(descriptor.checkInput(input) instanceof SFMClientActionDescriptor.InputCheck.Accepted)) {
                throw new IllegalArgumentException("Invalid client read input");
            }
            return query.read(caller, input);
        });
    }

    public static BlockPos position(SFMValue input) {
        var fields = ((SFMValue.ObjectValue) input).fields();
        return new BlockPos(Math.toIntExact(((SFMValue.LongValue) fields.get("x")).value()),
                Math.toIntExact(((SFMValue.LongValue) fields.get("y")).value()),
                Math.toIntExact(((SFMValue.LongValue) fields.get("z")).value()));
    }
    public static boolean loaded(SFMValue input) {
        return ((SFMValue.StringValue) ((SFMValue.ObjectValue) input).fields().get("scope")).value().equals("loaded");
    }
    public static ResourceLocation channel(SFMValue input) {
        String raw = ((SFMValue.StringValue) ((SFMValue.ObjectValue) input).fields().get("channel")).value();
        ResourceLocation channel = ResourceLocation.tryParse(raw);
        if (channel == null) throw new IllegalArgumentException("Invalid inbox channel");
        return channel;
    }
    private static SFMValueSchema.Field required(SFMValueSchema schema) { return SFMValueSchema.Field.required(schema); }
}
