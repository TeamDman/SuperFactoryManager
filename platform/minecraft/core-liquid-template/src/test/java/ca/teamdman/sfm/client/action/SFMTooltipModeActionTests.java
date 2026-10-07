package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.tooltip.SFMTooltipModeService;
{% if features.structured_action_results %}
import com.google.gson.JsonObject;
{% endif %}
{% if features.structured_action_results %}
import com.google.gson.JsonParser;
{% endif %}
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
{% if features.structured_action_results %}
        fixture.expectLast("expanded", true);
{% endif %}
        fixture.run(COMPACT);
        assertEquals(COMPACT, fixture.service.mode());
        assertFalse(fixture.service.isExpanded());
{% if features.structured_action_results %}
        fixture.expectLast("compact", true);
{% endif %}
        fixture.run(AUTO);
        assertEquals(AUTO, fixture.service.mode());
{% if features.structured_action_results %}
        fixture.expectLast("auto", true);
{% endif %}
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
{% if features.structured_action_results %}
            fixture.expectLast(mode.name().toLowerCase(java.util.Locale.ROOT), false);
{% endif %}
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
{% if features.structured_action_results %}
        assertTrue(fixture.results.isEmpty());
{% endif %}
        assertEquals(0, fixture.polls.get());
    }

    @Test void humanTooltipActionsNeverOptIntoProgrammaticAuthority() {
        var service = new SFMTooltipModeService(() -> { throw new AssertionError("Input reached"); });
        for (var mode : SFMTooltipModeService.Mode.values()) {
            var action = new SFMTooltipModeAction(mode, service);
{% if features.client_program_actions %}
            assertTrue(action.programmaticDescriptor().isEmpty());
{% endif %}
{% if features.client_program_actions %}
            assertTrue(action.programmaticHandler().isEmpty());
{% endif %}
{% if features.workspace_panels %}
            assertTrue(action.requirement().resolve(new SFMClientActionContext(null, () -> false, null)).isAvailable());
{% else %}
            assertTrue(action.requirement().resolve(new SFMClientActionContext(null, () -> false)).isAvailable());
{% endif %}
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
{% if features.structured_action_results %}
        final ArrayList<SFMClientActionStructuredResult> results = new ArrayList<>();
{% endif %}
        final ArrayList<String> feedback = new ArrayList<>();
{% if features.structured_action_results %}
{% if features.workspace_panels %}
        final SFMClientActionSource source = new SFMClientActionSource(new SFMClientActionContext(null, hostCurrent::get, null),
                message -> feedback.add(message.getString()), results::add);
{% else %}
        final SFMClientActionSource source = new SFMClientActionSource(new SFMClientActionContext(null, hostCurrent::get),
                message -> feedback.add(message.getString()), results::add);
{% endif %}
{% else %}
{% if features.workspace_panels %}
        final SFMClientActionSource source = new SFMClientActionSource(new SFMClientActionContext(null, hostCurrent::get, null),
                message -> feedback.add(message.getString()));
{% else %}
        final SFMClientActionSource source = new SFMClientActionSource(new SFMClientActionContext(null, hostCurrent::get),
                message -> feedback.add(message.getString()));
{% endif %}
{% endif %}
        final CommandDispatcher<SFMClientActionSource> dispatcher = SFMClientActionDispatcherCompiler.compile(
                Arrays.stream(SFMTooltipModeService.Mode.values()).map(mode -> Map.entry(SFMTooltipModeAction.idFor(mode),
                        new SFMTooltipModeAction(mode, service))).toList());
        void run(SFMTooltipModeService.Mode mode) throws CommandSyntaxException { assertEquals(1, dispatcher.execute(command(mode), source)); }
{% if features.structured_action_results %}
        void expectLast(String mode, boolean changed) {
            var result = results.get(results.size() - 1);
            assertEquals("sfm.tooltip.mode/1", result.schemaId());
            JsonObject json = JsonParser.parseString(result.json()).getAsJsonObject();
            assertEquals("ok", json.get("status").getAsString());
            assertEquals(mode, json.get("mode").getAsString());
            assertEquals(changed, json.get("changed").getAsBoolean());
        }
{% endif %}
    }
}
