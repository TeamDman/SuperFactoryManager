package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.tooltip.SFMTooltipModeService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static ca.teamdman.sfm.client.tooltip.SFMTooltipModeService.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMTooltipModeActionTests {
    @Test void completeNoArgumentCommandsChangeModeWithoutPollingAndPublishTypedResults() throws CommandSyntaxException {
        Fixture fixture = new Fixture();
        fixture.run(EXPANDED);
        assertEquals(EXPANDED, fixture.service.mode());
        assertTrue(fixture.service.isExpanded());
        fixture.expectLast("expanded", true);
        fixture.run(COMPACT);
        assertEquals(COMPACT, fixture.service.mode());
        assertFalse(fixture.service.isExpanded());
        fixture.expectLast("compact", true);
        fixture.run(AUTO);
        assertEquals(AUTO, fixture.service.mode());
        fixture.expectLast("auto", true);
        assertEquals(0, fixture.polls.get());
        assertTrue(fixture.service.isExpanded());
        assertEquals(1, fixture.polls.get());
        assertEquals(3, fixture.feedback.size());
    }

    @Test void repeatedCommandsAreSuccessfulAndIdempotentIncludingReset() throws CommandSyntaxException {
        Fixture fixture = new Fixture();
        for (var mode : List.of(EXPANDED, COMPACT, AUTO)) {
            fixture.run(mode);
            fixture.run(mode);
            fixture.expectLast(mode.name().toLowerCase(java.util.Locale.ROOT), false);
            assertEquals(mode, fixture.service.mode());
        }
        assertEquals(0, fixture.polls.get());
    }

    @Test void paletteLifetimeDoesNotOwnTheModeAndCliNeedsNoScreenHost() throws CommandSyntaxException {
        Fixture fixture = new Fixture();
        fixture.run(EXPANDED);
        fixture.hostCurrent.set(false);
        assertTrue(fixture.service.isExpanded());
        fixture.run(COMPACT);
        assertFalse(fixture.service.isExpanded());
        fixture.run(AUTO);
        assertEquals(AUTO, fixture.service.mode());
        assertEquals(0, fixture.polls.get());
    }

    @Test void parsingListingAndRejectedArgumentsDoNotChangeOrPollState() throws CommandSyntaxException {
        Fixture fixture = new Fixture();
        for (var mode : SFMTooltipModeService.Mode.values()) {
            String command = command(mode);
            assertNotNull(fixture.dispatcher.parse(command, fixture.source).getContext().getCommand());
            assertThrows(CommandSyntaxException.class, () -> fixture.dispatcher.execute(command + " extra", fixture.source));
        }
        assertEquals(3, fixture.dispatcher.execute("sfm action list", fixture.source));
        assertEquals(AUTO, fixture.service.mode());
        assertTrue(fixture.results.isEmpty());
        assertEquals(0, fixture.polls.get());
    }

    @Test void humanTooltipActionsNeverOptIntoProgrammaticAuthority() {
        var service = new SFMTooltipModeService(() -> { throw new AssertionError("Input reached"); });
        for (var mode : SFMTooltipModeService.Mode.values()) {
            var action = new SFMTooltipModeAction(mode, service);
            assertTrue(action.programmaticDescriptor().isEmpty());
            assertTrue(action.programmaticHandler().isEmpty());
            assertTrue(action.requirement().resolve(new SFMClientActionContext(null, () -> false, null)).isAvailable());
        }
        assertThrows(NullPointerException.class, () -> new SFMTooltipModeAction(null, service));
        assertThrows(NullPointerException.class, () -> new SFMTooltipModeAction(AUTO, null));
        assertThrows(NullPointerException.class, () -> SFMTooltipModeAction.idFor(null));
    }

    private static String command(SFMTooltipModeService.Mode mode) { return "sfm action invoke " + SFMTooltipModeAction.idFor(mode); }

    private static final class Fixture {
        final AtomicBoolean hostCurrent = new AtomicBoolean(true);
        final AtomicInteger polls = new AtomicInteger();
        final SFMTooltipModeService service = new SFMTooltipModeService(() -> { polls.incrementAndGet(); return true; });
        final ArrayList<SFMClientActionStructuredResult> results = new ArrayList<>();
        final ArrayList<String> feedback = new ArrayList<>();
        final SFMClientActionSource source = new SFMClientActionSource(new SFMClientActionContext(null, hostCurrent::get, null),
                message -> feedback.add(message.getString()), results::add);
        final CommandDispatcher<SFMClientActionSource> dispatcher = SFMClientActionDispatcherCompiler.compile(
                Arrays.stream(SFMTooltipModeService.Mode.values()).map(mode -> Map.entry(SFMTooltipModeAction.idFor(mode),
                        new SFMTooltipModeAction(mode, service))).toList());
        void run(SFMTooltipModeService.Mode mode) throws CommandSyntaxException { assertEquals(1, dispatcher.execute(command(mode), source)); }
        void expectLast(String mode, boolean changed) {
            var result = results.get(results.size() - 1);
            assertEquals("sfm.tooltip.mode/1", result.schemaId());
            JsonObject json = JsonParser.parseString(result.json()).getAsJsonObject();
            assertEquals("ok", json.get("status").getAsString());
            assertEquals(mode, json.get("mode").getAsString());
            assertEquals(changed, json.get("changed").getAsBoolean());
        }
    }
}
