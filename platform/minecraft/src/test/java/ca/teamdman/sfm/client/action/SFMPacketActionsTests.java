package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.net.SFMPacketObservationLog;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPacketActionsTests {
    @Test
    void listReturnsBoundedMachinePageAndAdvancesToLastReturnedEntry() throws Exception {
        SFMPacketObservationLog log = new SFMPacketObservationLog();
        SFMPacketObservationLog.SessionId session = log.beginSession();
        log.append(SFMValue.of("first"));
        log.append(SFMValue.object(java.util.Map.of("answer", SFMValue.of(42L))));
        SFMPacketListAction action = new SFMPacketListAction(log::currentSessionId, log::page);

        Invocation invocation = invoke(
                action,
                "sfm:packet/list after 0 limit 1 session " + session.value()
        );

        assertEquals(SFMPacketListAction.RESULT_SCHEMA, invocation.result().schemaId());
        JsonObject json = JsonParser.parseString(invocation.result().json()).getAsJsonObject();
        assertEquals("ok", json.get("status").getAsString());
        assertEquals("CONTIGUOUS", json.get("continuity").getAsString());
        assertEquals(1, json.getAsJsonArray("entries").size());
        assertEquals("first", json.getAsJsonArray("entries").get(0)
                .getAsJsonObject().get("value").getAsString());
        assertEquals(1, json.get("next_after_sequence").getAsLong());
        assertTrue(json.get("has_more").getAsBoolean());
        assertTrue(invocation.feedback().get(0).contains("Listed 1"));
    }

    @Test
    void staleListSessionIsExplicitAndDoesNotRedirectEntries() throws Exception {
        SFMPacketObservationLog log = new SFMPacketObservationLog();
        log.beginSession();
        log.append(SFMValue.of("current-world"));
        SFMPacketListAction action = new SFMPacketListAction(log::currentSessionId, log::page);

        Invocation invocation = invoke(
                action,
                "sfm:packet/list after 8 session " + UUID.randomUUID()
        );

        JsonObject json = JsonParser.parseString(invocation.result().json()).getAsJsonObject();
        assertEquals("session_changed", json.get("status").getAsString());
        assertEquals("SESSION_CHANGED", json.get("continuity").getAsString());
        assertEquals(0, json.getAsJsonArray("entries").size());
        assertEquals(0, json.get("next_after_sequence").getAsLong());
        assertFalse(json.get("has_more").getAsBoolean());
    }

    @Test
    void listWithoutAnActiveSessionReturnsACompleteEmptyMachineResult() throws Exception {
        SFMPacketObservationLog log = new SFMPacketObservationLog();
        SFMPacketListAction action = new SFMPacketListAction(log::currentSessionId, log::page);

        Invocation invocation = invoke(action, "sfm:packet/list limit 3");

        JsonObject json = JsonParser.parseString(invocation.result().json()).getAsJsonObject();
        assertEquals("no_session", json.get("status").getAsString());
        assertEquals("no_session", json.get("continuity").getAsString());
        assertFalse(json.get("session_id_present").getAsBoolean());
        assertEquals(0, json.getAsJsonArray("entries").size());
    }

    @Test
    void sendForwardsExactAddressSideAndBoundedValueWithoutDeliveryClaim() throws Exception {
        SFMPacketObservationLog.SessionId session = new SFMPacketObservationLog.SessionId(UUID.randomUUID());
        AtomicReference<SFMPacketInventoryAddress> address = new AtomicReference<>();
        AtomicReference<SFMValue> value = new AtomicReference<>();
        SFMPacketSendAction action = new SFMPacketSendAction(
                () -> Optional.of(session),
                (target, payload) -> {
                    address.set(target);
                    value.set(payload);
                    return true;
                }
        );
        String valueJson = "{\"message\":\"hello world\",\"ratio\":0.5,\"sequence\":7}";

        Invocation invocation = invoke(
                action,
                "sfm:packet/send minecraft:overworld 12 64 -7 side north session "
                + session.value() + " " + StringArgumentType.escapeIfRequired(valueJson)
        );

        assertEquals("minecraft:overworld", address.get().dimension().toString());
        assertEquals(12, address.get().position().getX());
        assertEquals(64, address.get().position().getY());
        assertEquals(-7, address.get().position().getZ());
        assertEquals("north", address.get().side().orElseThrow().getName());
        assertEquals(valueJson, SFMValueJsonCodec.encode(value.get()));
        assertEquals(
                SFMValue.of(0.5),
                ((SFMValue.ObjectValue) value.get()).fields().get("ratio")
        );
        JsonObject json = JsonParser.parseString(invocation.result().json()).getAsJsonObject();
        assertEquals("send_attempted", json.get("status").getAsString());
        assertTrue(json.get("local_transport_accepted").getAsBoolean());
        assertFalse(invocation.feedback().get(0).toLowerCase().contains("delivered"));
        assertTrue(invocation.feedback().get(0).contains("not acknowledged"));
    }

    @Test
    void staleSendSessionNeverCallsTransport() throws Exception {
        SFMPacketObservationLog.SessionId current = new SFMPacketObservationLog.SessionId(UUID.randomUUID());
        AtomicBoolean called = new AtomicBoolean();
        SFMPacketSendAction action = new SFMPacketSendAction(
                () -> Optional.of(current),
                (target, value) -> {
                    called.set(true);
                    return true;
                }
        );

        Invocation invocation = invoke(
                action,
                "sfm:packet/send minecraft:overworld 0 0 0 session " + UUID.randomUUID() + " null"
        );

        assertFalse(called.get());
        JsonObject json = JsonParser.parseString(invocation.result().json()).getAsJsonObject();
        assertEquals("session_changed", json.get("status").getAsString());
        assertFalse(json.get("local_transport_accepted").getAsBoolean());
    }

    @Test
    void sendReportsEffectsDisabledWithoutClaimingTransportAcceptance() throws Exception {
        SFMPacketObservationLog.SessionId current = new SFMPacketObservationLog.SessionId(UUID.randomUUID());
        SFMPacketSendAction action = new SFMPacketSendAction(
                () -> Optional.of(current),
                (target, value) -> false
        );

        Invocation invocation = invoke(
                action,
                "sfm:packet/send minecraft:overworld 0 64 0 null"
        );

        JsonObject json = JsonParser.parseString(invocation.result().json()).getAsJsonObject();
        assertEquals("effects_disabled", json.get("status").getAsString());
        assertFalse(json.get("local_transport_accepted").getAsBoolean());
        assertTrue(invocation.feedback().get(0).contains("not attempted"));
    }

    @Test
    void sendRejectsValuesOutsideTheSfmValueContractBeforeTransport() {
        SFMPacketObservationLog.SessionId session = new SFMPacketObservationLog.SessionId(UUID.randomUUID());
        AtomicBoolean called = new AtomicBoolean();
        SFMPacketSendAction action = new SFMPacketSendAction(
                () -> Optional.of(session),
                (target, value) -> {
                    called.set(true);
                    return true;
                }
        );

        assertThrows(CommandSyntaxException.class,
                () -> invoke(action, "sfm:packet/send minecraft:overworld 0 0 0 1e309"));
        assertThrows(CommandSyntaxException.class,
                () -> invoke(action, "sfm:packet/send overworld 0 0 0 null"));
        assertThrows(CommandSyntaxException.class,
                () -> invoke(action, "sfm:packet/send minecraft:overworld 0 0 0 session 1-1-1-1-1 null"));
        assertFalse(called.get());
    }

    private static Invocation invoke(SFMClientAction<?> action, String command) throws CommandSyntaxException {
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(action.createCommandNode(command.substring(0, command.indexOf(' '))));
        ArrayList<String> feedback = new ArrayList<>();
        AtomicReference<SFMClientActionStructuredResult> result = new AtomicReference<>();
        dispatcher.execute(
                command,
                new SFMClientActionSource(
                        SFMClientActionContext.create(null, () -> true),
                        component -> feedback.add(component.getString()),
                        value -> {
                            if (!result.compareAndSet(null, value)) {
                                throw new AssertionError("multiple structured results");
                            }
                        }
                )
        );
        return new Invocation(result.get(), feedback);
    }

    private record Invocation(
            SFMClientActionStructuredResult result,
            ArrayList<String> feedback
    ) {
    }
}
