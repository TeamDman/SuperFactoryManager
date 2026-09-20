package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProgramOperations;
import ca.teamdman.sfml.ast.FrameTrigger;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMMultiplayerPacketProgramTests {
    private static final ResourceLocation DIM = new ResourceLocation("minecraft", "overworld");
    private static final String SEND_JSON = "{\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":-2,\"side\":\"north\",\"value\":{\"hello\":1}}";
    private static final String READ_JSON = "{\"channel\":\"sfm:dashboard\",\"mode\":\"latest\"}";

    @Test
    void literalInputsExtractExactInventoryAndCurrentManagerDimensionInboxScopes() {
        var operations = extract(json("send_args", SEND_JSON) + invoke("sent", "sfm:packet/send", "send_args")
                + json("read_args", READ_JSON) + invoke("inbox", "sfm:client_inbox/read", "read_args"));
        assertEquals(Set.of(sendTarget(1, Optional.of(Direction.NORTH)), inbox(DIM, "sfm:dashboard")), operations);
        assertThrows(UnsupportedOperationException.class, operations::clear);
        ResourceLocation otherDimension = new ResourceLocation("minecraft", "the_nether");
        assertEquals(Set.of(inbox(otherDimension, "sfm:dashboard")), SFMMultiplayerPacketProgramOperations.extract(
                program(json("args", READ_JSON) + invoke("result", "sfm:client_inbox/read", "args")), otherDimension));
    }

    @Test
    void nestedLiteralFieldBindingsResolveWithoutExecutingAnyAction() {
        String nested = "{\"packet\":" + SEND_JSON + ",\"read\":" + READ_JSON + "}";
        var operations = extract(json("Bundle", nested)
                + "LET request BE FIELD \"packet\" OF bundle\n"
                + invoke("sent", "sfm:packet/send", "REQUEST")
                + "LET request BE FIELD \"read\" OF BUNDLE\n"
                + invoke("read", "sfm:client_inbox/read", "request"));
        assertEquals(Set.of(sendTarget(1, Optional.of(Direction.NORTH)), inbox(DIM, "sfm:dashboard")), operations);
    }

    @Test
    void bothBranchesAreCollectedEvenWhenConditionIsConstantAndBindingsDoNotEscape() {
        String body = json("args", SEND_JSON) + "IF FALSE THEN\n"
                + invoke("result", "sfm:packet/send", "args")
                + "ELSE\n" + json("args", SEND_JSON.replace("\"x\":1", "\"x\":2"))
                + invoke("result", "sfm:packet/send", "args") + "END\n"
                + invoke("after", "sfm:packet/send", "args");
        assertEquals(Set.of(sendTarget(1, Optional.of(Direction.NORTH)), sendTarget(2, Optional.of(Direction.NORTH))), extract(body));
        assertThrows(IllegalArgumentException.class, () -> extract("IF TRUE THEN\n" + json("hidden", SEND_JSON)
                + "END\n" + invoke("result", "sfm:packet/send", "hidden")));
    }

    @Test
    void omittedAndNullSideMeanOnlyUnsidedAndBareChannelMatchesExistingCanonicalization() {
        String absent = SEND_JSON.replace(",\"side\":\"north\"", "");
        String nullSide = SEND_JSON.replace("\"north\"", "null");
        assertEquals(Set.of(sendTarget(1, Optional.empty())), extract(json("a", absent) + invoke("b", "sfm:packet/send", "a")));
        assertEquals(Set.of(sendTarget(1, Optional.empty())), extract(json("a", nullSide) + invoke("b", "sfm:packet/send", "a")));
        assertEquals(Set.of(inbox(DIM, "minecraft:dashboard")), extract(json("a", READ_JSON.replace("sfm:dashboard", "dashboard"))
                + invoke("b", "sfm:client_inbox/read", "a")));
    }

    @Test
    void unknownOrSimilarlyNamedActionsNeverGrantNetworkAuthorityOrConstantResults() {
        assertTrue(extract(json("args", SEND_JSON) + invoke("result", "sfm:packet/send_extra", "args")).isEmpty());
        assertTrue(extract(json("args", SEND_JSON) + invoke("result", "other:packet/send", "args")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> extract(json("args", SEND_JSON)
                + invoke("result", "sfm:fixture/read", "args")
                + invoke("sent", "sfm:packet/send", "result")));
        assertThrows(IllegalArgumentException.class, () -> extract(json("args", READ_JSON)
                + invoke("result", "sfm:client_inbox/read", "args")
                + "LET value BE FIELD \"value\" OF result\n"
                + invoke("sent", "sfm:packet/send", "value")));
    }

    @Test
    void invalidNetworkArgumentsFailTheWholeExtractionIncludingInactiveBranches() {
        for (String invalid : List.of(
                SEND_JSON.replace("\"x\":1", "\"x\":1.0"),
                SEND_JSON.replace("\"x\":1", "\"x\":2147483648"),
                SEND_JSON.replace("minecraft:overworld", "overworld"),
                SEND_JSON.replace("minecraft:overworld", "BAD:dimension"),
                SEND_JSON.replace("\"north\"", "\"NORTH\""),
                SEND_JSON.replace("\"north\"", "true"),
                SEND_JSON.replace("\"value\":{\"hello\":1}", "\"extra\":1"),
                SEND_JSON.replace("\"hello\":1", "\"hello\":\"" + "x".repeat(3100) + "\""))) {
            assertThrows(IllegalArgumentException.class, () -> extract("IF FALSE THEN\n" + json("args", invalid)
                    + invoke("result", "sfm:packet/send", "args") + "END"), invalid);
        }
        for (String invalid : List.of("{}", READ_JSON.replace("latest", "all"),
                READ_JSON.replace("sfm:dashboard", "BAD:channel"),
                READ_JSON.replace("\"mode\":\"latest\"", "\"mode\":\"latest\",\"recipient\":\"someone_else\""))) {
            assertThrows(IllegalArgumentException.class, () -> extract(json("args", invalid)
                    + invoke("result", "sfm:client_inbox/read", "args")));
        }
    }

    @Test
    void invalidOrUnboundFieldAndConditionReferencesDoNotProducePartialAuthority() {
        assertThrows(IllegalArgumentException.class, () -> extract("LET args BE FIELD \"x\" OF missing"));
        assertThrows(IllegalArgumentException.class, () -> extract(json("value", "{}") + "LET args BE FIELD \"x\" OF value"));
        assertThrows(IllegalArgumentException.class, () -> extract("IF missing EQ JSON \"true\" THEN\n"
                + json("args", SEND_JSON) + invoke("result", "sfm:packet/send", "args") + "END"));
    }

    @Test
    void onlyClientFrameProgramsAreAcceptedAndTriggersHaveSeparateBindingEnvironments() {
        String frame = program(json("args", READ_JSON) + invoke("result", "sfm:client_inbox/read", "args"));
        assertEquals(Set.of(inbox(DIM, "sfm:dashboard")), SFMMultiplayerPacketProgramOperations.extract(frame.replace("CLIENT BTW\n", ""), DIM));
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketProgramOperations.extract(frame.replace("CLIENT BTW", "SERVER BTW"), DIM));
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketProgramOperations.extract("EVERY 20 TICKS DO END", DIM));
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketProgramOperations.extract("CLIENT BTW", DIM));
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketProgramOperations.extract(frame
                + "EVERY FRAME FOR panels AS panel DO LET result BE INVOKE sfm:client_inbox/read WITH args END", DIM));
    }

    @Test
    void sourceBytesUnicodeTokensAndNestingAreBoundedBeforeRecursiveCompilation() {
        assertFalse(SFMMultiplayerPacketProgramOperations.permitsSource("x".repeat(Program.MAX_PROGRAM_LENGTH + 1)));
        assertFalse(SFMMultiplayerPacketProgramOperations.permitsSource("雪".repeat(30000)));
        assertFalse(SFMMultiplayerPacketProgramOperations.permitsSource("\ud800"));
        assertFalse(SFMMultiplayerPacketProgramOperations.permitsSource("x ".repeat(SFMMultiplayerPacketProgramOperations.MAX_TOKENS + 1)));
        assertFalse(SFMMultiplayerPacketProgramOperations.permitsSource(program("IF TRUE THEN ".repeat(FrameTrigger.MAX_NESTING + 1)
                + "END ".repeat(FrameTrigger.MAX_NESTING + 1))));
        assertFalse(SFMMultiplayerPacketProgramOperations.permitsSource(program("IF " + "NOT ".repeat(FrameTrigger.MAX_NESTING + 1) + "TRUE THEN END")));
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketProgramOperations.extract("@ INVALID", DIM));
    }

    @Test
    void variableScopeAndTriggerBudgetsFailClosed() {
        StringBuilder variables = new StringBuilder();
        for (int i = 0; i <= SFMMultiplayerPacketProgramOperations.MAX_VARIABLES; i++) variables.append(json("v" + i, "{}"));
        assertThrows(IllegalArgumentException.class, () -> extract(variables.toString()));
        String triggers = "EVERY FRAME FOR displays AS display DO END\n".repeat(SFMMultiplayerPacketProgramOperations.MAX_TRIGGERS + 1);
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketProgramOperations.extract("CLIENT BTW\n" + triggers, DIM));
        StringBuilder scopes = new StringBuilder();
        for (int i = 0; i <= MAX_PROGRAM_SCOPES; i++) {
            scopes.append(json("args", READ_JSON.replace("sfm:dashboard", "sfm:channel_" + i)));
            scopes.append(invoke("result", "sfm:client_inbox/read", "args"));
        }
        assertThrows(IllegalArgumentException.class, () -> extract(scopes.toString()));
    }

    @Test
    void commentsAndRenderStatementsCannotBeMistakenForNetworkInvocation() {
        assertTrue(extract("-- LET result BE INVOKE sfm:packet/send WITH invented\n"
                + "RENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO display").isEmpty());
    }

    private static ProgramOperation sendTarget(int x, Optional<Direction> side) {
        return new ProgramOperation(Action.PACKET_SEND, new InventoryScope(new SFMPacketInventoryAddress(DIM, new BlockPos(x, 64, -2), side)));
    }
    private static ProgramOperation inbox(ResourceLocation dimension, String channel) {
        return new ProgramOperation(Action.INBOX_SUBSCRIBE, new InboxScope(dimension, new ResourceLocation(channel)));
    }
    private static String json(String name, String json) { return "LET " + name + " BE JSON \"" + json.replace("\"", "\\\"") + "\"\n"; }
    private static String invoke(String name, String action, String argument) { return "LET " + name + " BE INVOKE " + action + " WITH " + argument + "\n"; }
    private static String program(String body) { return "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n" + body + "\nEND\n"; }
    private static Set<ProgramOperation> extract(String body) { return SFMMultiplayerPacketProgramOperations.extract(program(body), DIM); }
}
