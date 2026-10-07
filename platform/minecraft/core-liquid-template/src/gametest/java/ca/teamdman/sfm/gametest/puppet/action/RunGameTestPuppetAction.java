package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
public final class RunGameTestPuppetAction implements SFMPuppetAction {
    private final String testName;
    private boolean prepared;

    public RunGameTestPuppetAction(String testName) {
        this.testName = testName;
    }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
public record RunGameTestPuppetAction(String testName) implements SFMPuppetAction {
{% endcase %}
    @Override
    public String description() {

        return "run GameTest " + testName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (!prepared) {
            runtime.prepareGameTest();
            prepared = true;
        }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}

{% endcase %}
        return runtime.runGameTest(testName);
    }

}
