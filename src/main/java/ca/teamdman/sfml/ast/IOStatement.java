package ca.teamdman.sfml.ast;

public interface IOStatement extends Statement, ToStringPretty {
    LabelAccess labelAccess();
    ResourceLimits resourceLimits();
    boolean each();
}
