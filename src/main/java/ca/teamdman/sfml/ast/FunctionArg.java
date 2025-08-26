package ca.teamdman.sfml.ast;

public final class FunctionArg {
    private final Kind kind;
    private final Object value;
    private FunctionArg(Kind kind, Object value) {
        this.kind = kind;
        this.value = value;
    }

    public static FunctionArg ofLabel(LabelAccess labelAccess) {
        return new FunctionArg(Kind.LABEL_ACCESS, labelAccess);
    }

    public static FunctionArg ofResourceIds(ResourceIdSet resourceIdSet) {
        return new FunctionArg(Kind.RESOURCE_IDS, resourceIdSet);
    }

    public static FunctionArg ofNumExpr(NumExpr numExpr) {
        return new FunctionArg(Kind.NUM, numExpr);
    }

    public static FunctionArg ofString(StringHolder str) {
        return new FunctionArg(Kind.STRING, str);
    }

    public Kind kind() {
        return kind;
    }

    public LabelAccess asLabelAccess() {
        return (LabelAccess) value;
    }

    public ResourceIdSet asResourceIds() {
        return (ResourceIdSet) value;
    }

    public NumExpr asNumExpr() {
        return (NumExpr) value;
    }

    public StringHolder asString() {
        return (StringHolder) value;
    }

    public enum Kind {LABEL_ACCESS, RESOURCE_IDS, NUM, STRING}
}
