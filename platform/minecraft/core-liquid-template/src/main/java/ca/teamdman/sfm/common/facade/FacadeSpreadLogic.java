package ca.teamdman.sfm.common.facade;

public enum FacadeSpreadLogic {
    SINGLE,
    NETWORK,
    NETWORK_GLOBAL_SAME_PAINT,
    NETWORK_CONTIGUOUS_SAME_PAINT;

    public static FacadeSpreadLogic fromParts(
            boolean isCtrlKeyDown,
            boolean isAltKeyDown
    ) {
        if (isCtrlKeyDown && isAltKeyDown) {
            return NETWORK;
        }
        if (isAltKeyDown) {
            return NETWORK_GLOBAL_SAME_PAINT;
        }
        if (isCtrlKeyDown) {
            return NETWORK_CONTIGUOUS_SAME_PAINT;
        }
        return SINGLE;
    }
}
