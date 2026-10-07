package ca.teamdman.sfm.common.compat.computercraft;

final class SFMComputerCraftResults {
    private SFMComputerCraftResults() {

    }

    static Object[] success() {

        return new Object[]{true};
    }

    static Object[] success(String statusCode) {

        return new Object[]{true, statusCode};
    }

    static Object[] failure(String errorCode) {

        return new Object[]{false, errorCode};
    }

    static Object[] unavailable(String errorCode) {

        return new Object[]{null, errorCode};
    }
}
