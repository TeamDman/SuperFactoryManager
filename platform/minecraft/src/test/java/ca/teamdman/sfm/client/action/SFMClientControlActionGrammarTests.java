package ca.teamdman.sfm.client.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SFMClientControlActionGrammarTests {
    @Test
    void actionListRunsWithOrWithoutAnExplicitMode() {
        var root = new SFMClientControlActionListAction().createCommandNode("sfm:action/list").build();

        assertNotNull(root.getCommand(), "action list without a mode must use its default");
        assertNotNull(root.getChild("mode").getCommand(), "explicit list mode must remain executable");
    }

    @Test
    void logsRunsWithOrWithoutExplicitArguments() {
        var root = new SFMClientControlLogsAction().createCommandNode("sfm:logs").build();

        assertNotNull(root.getCommand(), "logs without arguments must use its defaults");
        assertNotNull(root.getChild("tail").getCommand(), "explicit tail must remain executable");
        assertNotNull(root.getChild("tail").getChild("filter").getCommand(),
                "explicit filter must remain executable");
    }
}
