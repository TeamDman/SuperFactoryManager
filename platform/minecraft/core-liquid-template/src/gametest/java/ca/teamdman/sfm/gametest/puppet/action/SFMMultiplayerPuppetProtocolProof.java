package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.common.net.multiplayer.ServerboundMultiplayerPacket;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.net.*;
{% if features.touch_display %}
import ca.teamdman.sfm.client.program.*;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
{% endif %}
import ca.teamdman.sfm.common.net.*;
import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketWire;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import ca.teamdman.sfm.gametest.puppet.SFMMultiplayerPuppetFixtureContract;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;
import static ca.teamdman.sfm.gametest.puppet.SFMMultiplayerPuppetFixtureContract.*;

/** Test-only adversarial frames coexist with real registered actions and actual SFML execution. */
final class SFMMultiplayerPuppetProtocolProof {
    private static final ResourceLocation SEND = new ResourceLocation("sfm", "packet/send");
{% if features.touch_display %}
    private static final Set<String> OPERATIONS = Set.of("wait_negotiation", "subscribe_denied", "subscribe", "send_action",
            "send_denied", "wait_inbox", "close_subscription", "client_program", "raw_probes", "raw_stale_claim",
            "raw_stale_session", "pressure", "assert_empty_inbox");
{% else %}
    private static final Set<String> OPERATIONS = Set.of("wait_negotiation", "subscribe_denied", "subscribe", "send_action",
            "send_denied", "wait_inbox", "close_subscription", "assert_empty_inbox");
{% endif %}
    private SFMClientInboxRuntime.Subscription subscription;
{% if features.touch_display %}
    private ClientProgramIdentity identity;
    private TouchDisplayBlockEntity display;
{% endif %}
    private ProgramClaim capturedClaim;
    private UUID capturedSession;
    private String operation;
    private int stage;
    private long minimumSequence;
    private long rawSequence;
    private long deadline;
{% if features.touch_display %}
    private long frame = 800_000;
{% endif %}
    private boolean humanRoundTrip;
    private boolean programRoundTrip;

    boolean supports(String operation) { return OPERATIONS.contains(operation); }

    void begin(String operation) {
        if (!supports(operation) || this.operation != null) throw new IllegalStateException("Unsupported or overlapping protocol proof operation");
        this.operation = operation;
        stage = 0;
        minimumSequence = nextSequence();
        deadline = System.nanoTime() + 60_000_000_000L;
    }

    Optional<JsonObject> tick() {
        if (operation == null) return Optional.empty();
        try {
            if (System.nanoTime() >= deadline) throw new IllegalStateException("Protocol proof timed out at " + operation + "/" + stage
                    + ": " + SFMMultiplayerClientRuntime.diagnostics());
            if (operation.equals("wait_negotiation")) {
                if (!SFMMultiplayerClientRuntime.available()) return Optional.empty();
                return complete("Versioned remote session negotiated");
            }
            require(SFMMultiplayerClientRuntime.available(), "Expected an available negotiated remote session");
            switch (operation) {
                case "subscribe", "subscribe_denied" -> {
                    if (stage == 0) {
                        require(subscription == null, "Close the earlier human subscription first");
                        subscription = SFMClientInboxTransport.subscribe(address()).orElseThrow(() -> new IllegalStateException("Subscription was not queued"));
                        stage = 1;
                    }
                    var receipt = receipt("subscribe");
                    if (receipt.isEmpty()) return Optional.empty();
                    Status expected = operation.equals("subscribe") ? Status.SUBSCRIBED : Status.AUTHORITY_DENIED;
                    require(receipt.orElseThrow().status() == expected, "Unexpected subscription acknowledgement: " + receipt.orElseThrow().status());
                    if (expected == Status.AUTHORITY_DENIED) closeSubscription();
                    return complete("Production subscription acknowledged " + expected);
                }
                case "close_subscription" -> {
                    closeSubscription();
                    if (SFMMultiplayerClientRuntime.pendingAcknowledgements() != 0) return Optional.empty();
                    return complete("Human subscription released");
                }
                case "send_action", "send_denied" -> {
                    if (stage == 0) {
                        String command = "sfm action invoke sfm:packet/send " + DIMENSION + " " + MAILBOX.getX() + " "
                                + MAILBOX.getY() + " " + MAILBOX.getZ() + " "
                                + StringArgumentType.escapeIfRequired(SFMValueJsonCodec.encode(HUMAN_VALUE));
                        var connection = Minecraft.getInstance().getConnection();
                        int accepted = SFMClientActionExecutor.execute(command,
                                SFMClientActionContext.create(null, () -> Minecraft.getInstance().getConnection() == connection),
                                ignored -> { });
                        require(accepted > 0, "Registered packet/send action was not accepted locally");
                        stage = 1;
                    }
                    var receipt = receipt("insert");
                    if (receipt.isEmpty()) return Optional.empty();
                    if (operation.equals("send_action")) requireInserted(receipt.orElseThrow());
                    else require(receipt.orElseThrow().status() == Status.AUTHORITY_DENIED, "Revoked/default-deny insert was not denied");
                    return complete("Registered packet/send received insertion-attempt-only acknowledgement");
                }
                case "wait_inbox" -> {
                    var page = SFMClientInboxRuntime.get().page(CHANNEL, Optional.empty(), 64);
                    if (page.isEmpty() || page.orElseThrow().entries().stream().noneMatch(entry -> entry.value().equals(HUMAN_VALUE))) return Optional.empty();
                    humanRoundTrip = true;
                    return complete("Actual server SFML broadcast reached the production inbox");
                }
{% if features.touch_display %}
                case "client_program" -> {
                    if (!program()) return Optional.empty();
                    programRoundTrip = true;
                    return complete("Consented Client Manager INVOKE/read completed the real server-manager round trip");
                }
{% endif %}
                case "raw_probes" -> {
                    require(capturedClaim != null && programRoundTrip, "Complete the Client Manager proof before adversarial probes");
                    require(SFMMultiplayerClientRuntime.pendingAcknowledgements() == 0, "Wait for ordinary requests to settle before raw probes");
                    capturedSession = SFMMultiplayerClientRuntime.session().orElseThrow();
                    rawSequence = nextSequence();
                    // Exact unsided authority is not authority for another position or another side.
                    raw(SFMMultiplayerPacketWire.insert(capturedSession, rawSequence++,
                            new SFMPacketInventoryAddress(DIMENSION, DENIED, Optional.empty()), Optional.empty(), HUMAN_VALUE));
                    byte[] replay = SFMMultiplayerPacketWire.insert(capturedSession, rawSequence++,
                            new SFMPacketInventoryAddress(DIMENSION, MAILBOX, Optional.of(Direction.NORTH)), Optional.empty(), HUMAN_VALUE);
                    raw(replay);
                    raw(replay);
                    ProgramClaim forged = new ProgramClaim(capturedClaim.manager(), capturedClaim.incarnation(), capturedClaim.revision(),
                            "0".repeat(64), capturedClaim.bindingsSha256());
                    raw(SFMMultiplayerPacketWire.insert(capturedSession, rawSequence++, TARGET, Optional.of(forged), PROGRAM_VALUE));
                    raw(SFMMultiplayerPacketWire.subscription(capturedSession, rawSequence++, UUID.randomUUID(),
                            new SFMClientInboxAddress(UUID.randomUUID(), DIMENSION, CHANNEL), Optional.empty(), true));
                    return complete("Sent bounded forged target, side, replay, manager source, and recipient probes; server evidence is separate");
                }
                case "raw_stale_claim" -> {
                    require(capturedClaim != null && capturedSession != null, "No captured previous program claim");
                    raw(SFMMultiplayerPacketWire.insert(capturedSession, rawSequence++, TARGET, Optional.of(capturedClaim), PROGRAM_VALUE));
                    return complete("Sent the old exact manager claim after a server-owned program edit");
                }
                case "pressure" -> {
                    require(capturedSession != null, "No adversarial probe session");
                    var denied = new SFMPacketInventoryAddress(DIMENSION, DENIED, Optional.empty());
                    for (int index = 0; index < 192; index++) {
                        raw(SFMMultiplayerPacketWire.insert(capturedSession, rawSequence++, denied, Optional.empty(), HUMAN_VALUE));
                    }
                    return complete("Sent a bounded 192-frame denied-target pressure burst");
                }
                case "raw_stale_session" -> {
                    require(capturedSession != null && !capturedSession.equals(SFMMultiplayerClientRuntime.session().orElseThrow()),
                            "Reconnect must have a different server-issued session");
                    raw(SFMMultiplayerPacketWire.insert(capturedSession, 1, TARGET, Optional.empty(), HUMAN_VALUE));
                    return complete("Sent one stale pre-reconnect session probe");
                }
                case "assert_empty_inbox" -> {
                    SFMClientInboxTransport.observeCurrentWorld();
                    require(SFMClientInboxRuntime.get().page(CHANNEL, Optional.empty(), 1).isEmpty(), "Reconnect retained an old channel subscription");
                    return complete("Reconnect cleared prior subscriptions and inbox values");
                }
                default -> throw new IllegalStateException("Unknown proof operation");
            }
        } catch (Exception failure) {
            cleanup();
            JsonObject result = observation();
            result.addProperty("success", false);
            result.addProperty("message", failure.toString());
            operation = null;
            return Optional.of(result);
        }
    }

{% if features.touch_display %}
    private boolean program() {
        var minecraft = Minecraft.getInstance();
        if (stage == 0) {
            require(subscription == null, "Close the human subscription before the program claims its channel");
            if (minecraft.level == null
                    || !(minecraft.level.getBlockEntity(CLIENT_MANAGER) instanceof ClientManagerBlockEntity manager)
                    || !(minecraft.level.getBlockEntity(DISPLAY) instanceof TouchDisplayBlockEntity surface)
                    || manager.worldId() == null || manager.signingSnapshot() == null
                    || !manager.storedSource().equals(SFMMultiplayerPuppetFixtureContract.clientSource())) return false;
            identity = ClientManagerFrameRuntime.identityFor(manager).orElseThrow(() -> new IllegalStateException(
                    ClientManagerFrameRuntime.diagnosticFor(manager).orElse("Remote client program was not compilable")));
            display = surface;
            capturedClaim = SFMMultiplayerClientSession.claimFromSnapshot(DIMENSION, CLIENT_MANAGER, manager.signingSnapshot());
            require(identity.requestedCapabilities().equals(Set.of(ClientProgramConsentGate.EXECUTE, ClientProgramInboxReadSurface.READ, SEND)),
                    "Remote fixture permission manifest is wider than expected");
            approve(ClientProgramConsentGate.EXECUTE);
            approve(ClientProgramInboxReadSurface.READ);
            ClientManagerFrameRuntime.textureForSelectedFrame(display, ++frame, true);
            stage = 1;
        }
        if (stage == 1) {
            var subscribed = receipt("subscribe");
            if (subscribed.isEmpty()) return false;
            require(subscribed.orElseThrow().status() == Status.SUBSCRIBED, "Server rejected observed Client Manager inbox scope");
            minimumSequence = nextSequence();
            approve(SEND);
            try {
                ClientManagerFrameRuntime.textureForSelectedFrame(display, ++frame, true);
                require(ClientManagerFrameRuntime.valueFor(display, identity, "sendGateway").orElseThrow().equals(SFMValue.of("ok")),
                        "Consented actual SFML INVOKE did not reach its action gateway");
            } finally {
                // Revocation occurs in this same client-thread task, before any natural BER can send a second time.
                ClientManagerFrameRuntime.consent().revoke(identity, SEND);
            }
            stage = 2;
        }
        if (stage == 2) {
            var inserted = receipt("insert");
            if (inserted.isEmpty()) return false;
            requireInserted(inserted.orElseThrow());
            ClientManagerFrameRuntime.textureForSelectedFrame(display, ++frame, true);
            if (!ClientManagerFrameRuntime.valueFor(display, identity, "receivedValue").filter(PROGRAM_VALUE::equals).isPresent()) return false;
            ClientManagerFrameRuntime.consent().store().forget(identity);
            identity = null;
            stage = 3;
        }
        return SFMClientInboxRuntime.get().page(CHANNEL, Optional.empty(), 1).isEmpty()
                && SFMMultiplayerClientRuntime.pendingAcknowledgements() == 0;
    }

    private void approve(ResourceLocation capability) {
        var gate = ClientManagerFrameRuntime.consent();
        gate.request(identity, capability);
        gate.decide(identity, capability, ClientProgramConsentGate.Decision.APPROVE);
    }

{% endif %}
    private Optional<SFMMultiplayerClientSession.Receipt> receipt(String operation) {
        return SFMMultiplayerClientRuntime.acknowledgements().stream()
                .filter(receipt -> receipt.sequence() >= minimumSequence && receipt.operation().equals(operation)).findFirst();
    }

    private static long nextSequence() {
        return SFMMultiplayerClientRuntime.acknowledgements().stream().mapToLong(SFMMultiplayerClientSession.Receipt::sequence).max().orElse(0) + 1;
    }

    private static void requireInserted(SFMMultiplayerClientSession.Receipt receipt) {
        require(receipt.status() == Status.INSERTION_ATTEMPTED && receipt.insertion().orElseThrow() == SFMPacketInventoryInserter.Result.INSERTED,
                "Server insertion attempt failed: " + receipt.status() + "/" + receipt.insertion());
    }

    private static SFMClientInboxAddress address() {
        return new SFMClientInboxAddress(Minecraft.getInstance().player.getUUID(), DIMENSION, CHANNEL);
    }

    private static void raw(byte[] frame) {
        SFMPackets.sendToServer(new ServerboundMultiplayerPacket(frame));
    }

    private void closeSubscription() {
        if (subscription != null) { subscription.close(); subscription = null; }
    }

    void cleanup() {
        closeSubscription();
{% if features.touch_display %}
        if (identity != null) { ClientManagerFrameRuntime.consent().store().forget(identity); identity = null; }
{% endif %}
    }

    JsonObject observation() {
        JsonObject result = new JsonObject();
        result.addProperty("remoteAvailable", SFMMultiplayerClientRuntime.available());
        SFMMultiplayerClientRuntime.session().ifPresent(value -> result.addProperty("remoteSession", value.toString()));
        result.addProperty("diagnostic", SFMMultiplayerClientRuntime.diagnostics());
        result.addProperty("pendingAcknowledgements", SFMMultiplayerClientRuntime.pendingAcknowledgements());
        result.addProperty("humanRoundTrip", humanRoundTrip);
        result.addProperty("clientProgramRoundTrip", programRoundTrip);
        JsonArray receipts = new JsonArray();
        SFMMultiplayerClientRuntime.acknowledgements().forEach(receipt -> {
            JsonObject value = new JsonObject();
            value.addProperty("sequence", receipt.sequence());
            value.addProperty("operation", receipt.operation());
            value.addProperty("status", receipt.status().name());
            receipt.insertion().ifPresent(insertion -> value.addProperty("insertion", insertion.name()));
            receipts.add(value);
        });
        result.add("receipts", receipts);
        return result;
    }

    private Optional<JsonObject> complete(String message) {
        JsonObject result = observation();
        result.addProperty("success", true);
        result.addProperty("message", message);
        operation = null;
        return Optional.of(result);
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
