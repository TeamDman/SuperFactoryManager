package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public interface SFMPuppetAction {
    String description();

    boolean tick(ISFMGamePuppetRuntime runtime);

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Best-effort cleanup before a failed or timed-out puppet leaves its world. */
    default void abort() { }
{% if targets.mc_1_19_2 and features.client_automation_pause_restore %}

    /** Opt in only for an action that intentionally disconnects and rejoins its owned world. */
    default boolean permitsWorldTransition() { return false; }
{% endif %}

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
}
